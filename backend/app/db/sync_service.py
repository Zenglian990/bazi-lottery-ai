"""开奖数据实时异步同步服务（福彩官网 CWL + 500.com，含退避重试、表容器校验与 GCS 自动持久化）。"""
import asyncio
import concurrent.futures
import logging
import re
from typing import Any, Dict, List, Optional, Tuple

import httpx
from bs4 import BeautifulSoup

from backend.app.db.database import upsert_draws
from backend.app.db.gcs_sync import upload_db_to_gcs
from backend.app.services.lottery_rules import validate_ticket

logger = logging.getLogger(__name__)

SYNC_TIMEOUT = httpx.Timeout(15.0, connect=5.0, read=10.0, write=5.0)
HEADERS_CWL = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Referer": "https://www.cwl.gov.cn/",
    "Accept": "application/json, text/javascript, */*; q=0.01",
}
HEADERS_500 = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Referer": "https://datachart.500.com/",
}

_ISSUE_RE = re.compile(r"^\d{5,10}$")
_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def _parse_money(val: Any) -> int:
    """安全解析销售额或奖池金额字符串。"""
    if val is None:
        return 0
    s = str(val).replace(",", "").strip()
    if not s or s in ("_", "--", "-", "null", "None"):
        return 0
    try:
        return int(float(s))
    except (ValueError, TypeError):
        return 0


def _sanitize_draw_record(
    lottery_type: str,
    issue: str,
    draw_date: str,
    primary_numbers: List[str],
    secondary_numbers: Optional[List[str]],
    sales_amount: int,
    pool_amount: int,
) -> Optional[Dict[str, Any]]:
    """校验单条开奖记录期号、日期与号码规则，不合法时返回 None。"""
    issue_clean = str(issue).strip()
    if not _ISSUE_RE.fullmatch(issue_clean):
        return None
    date_clean = str(draw_date or "").strip()[:10]
    if date_clean and not _DATE_RE.fullmatch(date_clean):
        date_clean = ""

    is_valid, _, norm_pri, norm_sec, _ = validate_ticket(
        lottery_type=lottery_type,
        primary_numbers=primary_numbers,
        secondary_numbers=secondary_numbers,
    )
    if not is_valid or not norm_pri:
        return None

    return {
        "issue": issue_clean,
        "draw_date": date_clean,
        "primary_numbers": norm_pri,
        "secondary_numbers": norm_sec,
        "sales_amount": max(0, int(sales_amount or 0)),
        "pool_amount": max(0, int(pool_amount or 0)),
    }


async def _get_with_retry(
    client: httpx.AsyncClient,
    url: str,
    headers: Dict[str, str],
    max_retries: int = 2,
) -> httpx.Response:
    """带指数退避的异步 GET 请求（防御临时跨境网络抖动）。"""
    last_exc: Optional[Exception] = None
    for attempt in range(max_retries + 1):
        try:
            resp = await client.get(url, headers=headers)
            resp.raise_for_status()
            return resp
        except Exception as exc:
            last_exc = exc
            if attempt < max_retries:
                await asyncio.sleep(0.5 * (2 ** attempt))
    assert last_exc is not None
    raise last_exc


async def fetch_ssq_live(client: httpx.AsyncClient, count: int = 100) -> List[Dict[str, Any]]:
    """抓取中国福彩官网双色球开奖数据（支持 500.com 备用源降级）。"""
    url = f"https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&issueCount={count}"
    records: List[Dict[str, Any]] = []
    try:
        resp = await _get_with_retry(client, url, HEADERS_CWL)
        payload = resp.json()
        if isinstance(payload, dict) and payload.get("state") == 0 and isinstance(payload.get("result"), list):
            for item in payload["result"]:
                try:
                    reds = [f"{int(x):02d}" for x in str(item["red"]).split(",")]
                    blue = [f"{int(item['blue']):02d}"]
                    rec = _sanitize_draw_record(
                        lottery_type="ssq",
                        issue=str(item["code"]),
                        draw_date=str(item.get("date", "")),
                        primary_numbers=reds,
                        secondary_numbers=blue,
                        sales_amount=_parse_money(item.get("sales")),
                        pool_amount=_parse_money(item.get("poolmoney")),
                    )
                    if rec:
                        records.append(rec)
                except (ValueError, TypeError, KeyError, IndexError):
                    continue
    except Exception as exc:
        logger.warning("CWL SSQ primary source failed (%s), trying 500.com fallback.", type(exc).__name__)

    if records:
        return records[:count]

    # 500.com 备用源（防止海外 Cloud Run 访问 cwl.gov.cn 遭遇拦截时数据停滞）
    fallback_url = f"https://datachart.500.com/ssq/history/newinc/history.php?limit={count}"
    resp = await _get_with_retry(client, fallback_url, HEADERS_500)
    html_text = resp.content.decode("gb18030", errors="replace")
    soup = BeautifulSoup(html_text, "html.parser")
    tbody = soup.find("tbody", id="tdata")
    if tbody:
        for tr in tbody.find_all("tr"):
            try:
                tds = [td.get_text(strip=True) for td in tr.find_all("td")]
                if len(tds) < 8:
                    continue
                raw_issue = tds[0]
                issue = "20" + raw_issue if len(raw_issue) == 5 else raw_issue
                reds = [f"{int(x):02d}" for x in tds[1:7]]
                blue = [f"{int(tds[7]):02d}"]
                pool = _parse_money(tds[9]) if len(tds) > 9 else 0
                sales = _parse_money(tds[14]) if len(tds) > 14 else 0
                draw_date = tds[15] if len(tds) > 15 else ""
                rec = _sanitize_draw_record(
                    lottery_type="ssq",
                    issue=issue,
                    draw_date=draw_date,
                    primary_numbers=reds,
                    secondary_numbers=blue,
                    sales_amount=sales,
                    pool_amount=pool,
                )
                if rec:
                    records.append(rec)
            except (ValueError, TypeError, KeyError, IndexError):
                continue
    return records[:count]


async def fetch_fc3d_live(client: httpx.AsyncClient, count: int = 100) -> List[Dict[str, Any]]:
    """抓取中国福彩官网福彩 3D 开奖数据（支持 500.com 备用源降级）。"""
    url = f"https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=3d&issueCount={count}"
    records: List[Dict[str, Any]] = []
    try:
        resp = await _get_with_retry(client, url, HEADERS_CWL)
        payload = resp.json()
        if isinstance(payload, dict) and payload.get("state") == 0 and isinstance(payload.get("result"), list):
            for item in payload["result"]:
                try:
                    digits = [str(int(x)) for x in str(item["red"]).split(",")]
                    rec = _sanitize_draw_record(
                        lottery_type="fc3d",
                        issue=str(item["code"]),
                        draw_date=str(item.get("date", "")),
                        primary_numbers=digits,
                        secondary_numbers=None,
                        sales_amount=_parse_money(item.get("sales")),
                        pool_amount=_parse_money(item.get("poolmoney")),
                    )
                    if rec:
                        records.append(rec)
                except (ValueError, TypeError, KeyError, IndexError):
                    continue
    except Exception as exc:
        logger.warning("CWL FC3D primary source failed (%s), trying 500.com fallback.", type(exc).__name__)

    if records:
        return records[:count]

    fallback_url = f"https://datachart.500.com/sd/history/inc/history.php?limit={count}"
    resp = await _get_with_retry(client, fallback_url, HEADERS_500)
    html_text = resp.content.decode("gb18030", errors="replace")
    soup = BeautifulSoup(html_text, "html.parser")
    table = soup.find("table", id="tablelist") or soup
    for tr in table.find_all("tr"):
        try:
            tds = [td.get_text(strip=True) for td in tr.find_all("td")]
            if len(tds) < 2 or not tds[0].isdigit():
                continue
            raw_issue = tds[0]
            issue = "20" + raw_issue if len(raw_issue) == 5 else raw_issue
            nums = tds[1].split()
            if len(nums) != 3:
                continue
            digits = [str(int(x)) for x in nums]
            draw_date = next((cell for cell in tds if _DATE_RE.fullmatch(cell[:10])), "")
            rec = _sanitize_draw_record(
                lottery_type="fc3d",
                issue=issue,
                draw_date=draw_date,
                primary_numbers=digits,
                secondary_numbers=None,
                sales_amount=0,
                pool_amount=0,
            )
            if rec:
                records.append(rec)
        except (ValueError, TypeError, KeyError, IndexError):
            continue
    return records[:count]


async def fetch_dlt_live(client: httpx.AsyncClient, count: int = 100) -> List[Dict[str, Any]]:
    """抓取 500.com 大乐透开奖走势表。"""
    url = f"https://datachart.500.com/dlt/history/newinc/history.php?limit={count}"
    resp = await _get_with_retry(client, url, HEADERS_500)
    html_text = resp.content.decode("gb18030", errors="replace")
    soup = BeautifulSoup(html_text, "html.parser")

    records: List[Dict[str, Any]] = []
    tbody = soup.find("tbody", id="tdata")
    if tbody:
        for tr in tbody.find_all("tr"):
            try:
                tds = [td.get_text(strip=True) for td in tr.find_all("td")]
                if len(tds) < 8:
                    continue
                raw_issue = tds[0]
                issue = "20" + raw_issue if len(raw_issue) == 5 else raw_issue
                front = [f"{int(x):02d}" for x in tds[1:6]]
                back = [f"{int(x):02d}" for x in tds[6:8]]
                sales = _parse_money(tds[8]) if len(tds) > 8 else 0
                pool = _parse_money(tds[9]) if len(tds) > 9 else 0
                draw_date = tds[14] if len(tds) > 14 else ""
                rec = _sanitize_draw_record(
                    lottery_type="dlt",
                    issue=issue,
                    draw_date=draw_date,
                    primary_numbers=front,
                    secondary_numbers=back,
                    sales_amount=sales,
                    pool_amount=pool,
                )
                if rec:
                    records.append(rec)
            except (ValueError, TypeError, KeyError, IndexError):
                continue
    return records[:count]


async def fetch_qxc_live(client: httpx.AsyncClient, count: int = 100) -> List[Dict[str, Any]]:
    """抓取 500.com 七星彩开奖表（严格限定数据表容器，防止误扫全页无关 tr）。"""
    url = f"https://datachart.500.com/qxc/history/inc/history.php?limit={max(count, 120)}"
    resp = await _get_with_retry(client, url, HEADERS_500)
    html_text = resp.content.decode("gb18030", errors="replace")
    soup = BeautifulSoup(html_text, "html.parser")

    records: List[Dict[str, Any]] = []
    container = soup.find("tbody", id="tdata") or soup.find("table", id="tablelist") or soup.find("table", class_="t_tr1") or soup
    for tr in container.find_all("tr"):
        try:
            tds = [td.get_text(strip=True) for td in tr.find_all("td")]
            if len(tds) < 5 or not _ISSUE_RE.fullmatch(tds[0]):
                continue
            raw_issue = tds[0]
            issue = "20" + raw_issue if len(raw_issue) == 5 else raw_issue
            nums = tds[1].split()
            if len(nums) != 7:
                continue
            front = [str(int(x)) for x in nums[:6]]
            back = [f"{int(nums[6]):02d}"]
            sales = _parse_money(tds[2]) if len(tds) > 2 else 0
            pool = _parse_money(tds[3]) if len(tds) > 3 else 0
            draw_date = tds[4] if len(tds) > 4 else ""
            rec = _sanitize_draw_record(
                lottery_type="qxc",
                issue=issue,
                draw_date=draw_date,
                primary_numbers=front,
                secondary_numbers=back,
                sales_amount=sales,
                pool_amount=pool,
            )
            if rec:
                records.append(rec)
        except (ValueError, TypeError, KeyError, IndexError):
            continue
    return records[:count]


async def _fetch_single_lottery(client: httpx.AsyncClient, lt: str, limit: int) -> List[Dict[str, Any]]:
    if lt == "ssq":
        return await fetch_ssq_live(client, limit)
    if lt == "fc3d":
        return await fetch_fc3d_live(client, limit)
    if lt == "dlt":
        return await fetch_dlt_live(client, limit)
    if lt == "qxc":
        return await fetch_qxc_live(client, limit)
    return []


async def sync_lottery_data_detailed_async(
    lottery_type: str = "all",
    limit: int = 100,
    client: Optional[httpx.AsyncClient] = None,
) -> Tuple[Dict[str, int], Dict[str, str], bool]:
    """
    异步并发同步开奖数据并自动持久化到 GCS 快照。
    返回 (synced_counts, failed_details, gcs_uploaded)。
    """
    targets = ["ssq", "dlt", "fc3d", "qxc"] if lottery_type == "all" else [lottery_type.lower()]
    synced_counts: Dict[str, int] = {t: 0 for t in targets}
    failed_details: Dict[str, str] = {}

    async def _run_with_client(active_client: httpx.AsyncClient) -> None:
        tasks = [_fetch_single_lottery(active_client, lt, limit) for lt in targets]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        for lt, res in zip(targets, results):
            if isinstance(res, BaseException):
                logger.warning("Sync failed for %s: %s. Local data remains intact.", lt, type(res).__name__)
                failed_details[lt] = type(res).__name__
                synced_counts[lt] = 0
                continue
            if res:
                try:
                    upserted = await asyncio.to_thread(upsert_draws, lt, res)
                    synced_counts[lt] = upserted
                    logger.info("Successfully synced %d draws for %s", upserted, lt)
                except Exception as exc:
                    logger.warning("Database upsert failed for %s: %s", lt, type(exc).__name__)
                    failed_details[lt] = f"db_{type(exc).__name__}"
                    synced_counts[lt] = 0
            else:
                failed_details[lt] = "empty_response"

    if client is not None:
        await _run_with_client(client)
    else:
        async with httpx.AsyncClient(timeout=SYNC_TIMEOUT, follow_redirects=False) as temp_client:
            await _run_with_client(temp_client)

    gcs_uploaded = False
    if sum(synced_counts.values()) > 0:
        gcs_uploaded = await asyncio.to_thread(upload_db_to_gcs)

    return synced_counts, failed_details, gcs_uploaded


async def sync_lottery_data_async(
    lottery_type: str = "all",
    limit: int = 100,
    client: Optional[httpx.AsyncClient] = None,
) -> Dict[str, int]:
    """兼容旧签名的异步同步函数，仅返回各彩种入库条数。"""
    counts, _, _ = await sync_lottery_data_detailed_async(
        lottery_type=lottery_type, limit=limit, client=client
    )
    return counts


def sync_lottery_data(lottery_type: str = "all", limit: int = 100) -> Dict[str, int]:
    """同步兼容封装。"""
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(sync_lottery_data_async(lottery_type=lottery_type, limit=limit))

    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        return pool.submit(asyncio.run, sync_lottery_data_async(lottery_type=lottery_type, limit=limit)).result()
