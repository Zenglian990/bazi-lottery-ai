"""Lottery live sync service for CWL and 500.com data sources."""
import urllib.request
import json
import logging
from typing import List, Dict, Any, Optional
from bs4 import BeautifulSoup

from backend.app.db.database import upsert_draws

logger = logging.getLogger(__name__)

DEFAULT_TIMEOUT = 10
HEADERS_CWL = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "http://www.cwl.gov.cn/",
}
HEADERS_500 = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
}

def fetch_ssq_live(count: int = 100) -> List[Dict[str, Any]]:
    """Fetch recent SSQ draws from official China Welfare Lottery API."""
    url = f"http://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&issueCount={count}"
    req = urllib.request.Request(url, headers=HEADERS_CWL)
    with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT) as r:
        raw = json.loads(r.read().decode("utf-8")).get("result", [])

    records = []
    for item in raw:
        reds = sorted([f"{int(x):02d}" for x in item["red"].split(",")])
        blue = [f"{int(item['blue']):02d}"]
        sales = int(float(item.get("sales", 0) or 0))
        pool = int(float(item.get("poolmoney", 0) or 0))
        records.append({
            "issue": str(item["code"]),
            "draw_date": item.get("date", "")[:10],
            "primary_numbers": reds,
            "secondary_numbers": blue,
            "sales_amount": sales,
            "pool_amount": pool,
        })
    return records

def fetch_fc3d_live(count: int = 100) -> List[Dict[str, Any]]:
    """Fetch recent FC3D draws from official China Welfare Lottery API."""
    url = f"http://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=3d&issueCount={count}"
    req = urllib.request.Request(url, headers=HEADERS_CWL)
    with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT) as r:
        raw = json.loads(r.read().decode("utf-8")).get("result", [])

    records = []
    for item in raw:
        digits = [str(int(x)) for x in item["red"].split(",")]
        sales = int(float(item.get("sales", 0) or 0))
        pool = int(float(item.get("poolmoney", 0) or 0))
        records.append({
            "issue": str(item["code"]),
            "draw_date": item.get("date", "")[:10],
            "primary_numbers": digits,
            "secondary_numbers": None,
            "sales_amount": sales,
            "pool_amount": pool,
        })
    return records

def fetch_dlt_live(count: int = 100) -> List[Dict[str, Any]]:
    """Fetch recent DLT draws from 500.com datachart table."""
    url = f"https://datachart.500.com/dlt/history/newinc/history.php?limit={count}"
    req = urllib.request.Request(url, headers=HEADERS_500)
    with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT) as r:
        soup = BeautifulSoup(r.read().decode("gb2312", errors="ignore"), "html.parser")

    records = []
    tbody = soup.find("tbody", id="tdata")
    if tbody:
        for tr in tbody.find_all("tr"):
            tds = [td.get_text(strip=True) for td in tr.find_all("td")]
            if len(tds) >= 8:
                raw_issue = tds[0]
                issue = "20" + raw_issue if len(raw_issue) == 5 else raw_issue
                front = sorted([f"{int(x):02d}" for x in tds[1:6]])
                back = sorted([f"{int(x):02d}" for x in tds[6:8]])
                sales_str = tds[8].replace(",", "") if len(tds) > 8 else "0"
                sales = int(sales_str) if sales_str.isdigit() else 0
                pool_str = tds[9].replace(",", "") if len(tds) > 9 else "0"
                pool = int(pool_str) if pool_str.isdigit() else 0
                draw_date = tds[14] if len(tds) > 14 and len(tds[14]) == 10 else ""
                records.append({
                    "issue": issue,
                    "draw_date": draw_date,
                    "primary_numbers": front,
                    "secondary_numbers": back,
                    "sales_amount": sales,
                    "pool_amount": pool,
                })
    return records[:count]

def fetch_qxc_live(count: int = 100) -> List[Dict[str, Any]]:
    """Fetch recent QXC draws (modern rule) from 500.com datachart table."""
    url = f"https://datachart.500.com/qxc/history/inc/history.php?limit={max(count, 120)}"
    req = urllib.request.Request(url, headers=HEADERS_500)
    with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT) as r:
        soup = BeautifulSoup(r.read().decode("gb2312", errors="ignore"), "html.parser")

    records = []
    trs = [tr for tr in soup.find_all("tr") if len(tr.find_all("td")) >= 5 and tr.find_all("td")[0].get_text(strip=True).isdigit()]
    for tr in trs:
        tds = [td.get_text(strip=True) for td in tr.find_all("td")]
        raw_issue = tds[0]
        issue = "20" + raw_issue if len(raw_issue) == 5 else raw_issue
        nums = tds[1].split()
        if len(nums) == 7:
            front = [str(int(x)) for x in nums[:6]]
            back = [f"{int(nums[6]):02d}"]
            sales_str = tds[2].replace(",", "") if len(tds) > 2 else "0"
            sales = int(sales_str) if sales_str.isdigit() else 0
            pool_str = tds[3].replace(",", "") if len(tds) > 3 else "0"
            pool = int(pool_str) if pool_str.isdigit() else 0
            draw_date = tds[4] if len(tds) > 4 and len(tds[4]) == 10 else ""
            records.append({
                "issue": issue,
                "draw_date": draw_date,
                "primary_numbers": front,
                "secondary_numbers": back,
                "sales_amount": sales,
                "pool_amount": pool,
            })
    return records[:count]

def sync_lottery_data(lottery_type: str = "all", limit: int = 100) -> Dict[str, int]:
    """
    Synchronize lottery draws from official endpoints and save to SQLite.
    Returns dictionary with number of draws updated per lottery type.
    """
    targets = ["ssq", "dlt", "fc3d", "qxc"] if lottery_type == "all" else [lottery_type.lower()]
    synced_counts: Dict[str, int] = {t: 0 for t in targets}

    for lt in targets:
        try:
            records: List[Dict[str, Any]] = []
            if lt == "ssq":
                records = fetch_ssq_live(limit)
            elif lt == "fc3d":
                records = fetch_fc3d_live(limit)
            elif lt == "dlt":
                records = fetch_dlt_live(limit)
            elif lt == "qxc":
                records = fetch_qxc_live(limit)

            if records:
                upserted = upsert_draws(lt, records)
                synced_counts[lt] = upserted
                logger.info(f"Successfully synced {upserted} draws for {lt}")
        except Exception as e:
            logger.warning(f"Sync failed for {lt}: {e}. Local seed remains intact.")
            synced_counts[lt] = 0

    return synced_counts
