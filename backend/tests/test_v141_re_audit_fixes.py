"""v1.4.1 二次审查专项回归测试：500.com DLT/QXC 列解析、Gemini 思考块过滤、SSE 降级元事件与策略别名。"""
import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from backend.app.db.sync_service import fetch_dlt_live, fetch_qxc_live
from backend.app.models.ai import BaziDivinationRequest, DivinationTicket
from backend.app.services import ai_service
from backend.app.services.numerology_engine import STRATEGY_ALIAS_MAP


@pytest.mark.asyncio
async def test_fetch_dlt_live_parses_pool_and_sales_columns_accurately():
    html = """
    <html><body><table><tbody id="tdata">
      <tr>
        <td>26133</td><td>10</td><td>11</td><td>25</td><td>30</td><td>32</td><td>09</td><td>10</td>
        <td>766,933,719</td><td>5</td><td>7,367,247</td><td>108</td><td>140,410</td>
        <td>343,261,615</td><td>2026-11-21</td>
      </tr>
    </tbody></table></body></html>
    """
    fake_resp = MagicMock()
    fake_resp.content = html.encode("gb18030")
    fake_resp.raise_for_status = MagicMock()

    with patch("backend.app.db.sync_service._get_with_retry", new=AsyncMock(return_value=fake_resp)):
        rows = await fetch_dlt_live(MagicMock(), count=10)

    assert len(rows) == 1
    assert rows[0]["issue"] == "2026133"
    assert rows[0]["pool_amount"] == 766933719
    assert rows[0]["sales_amount"] == 343261615


@pytest.mark.asyncio
async def test_fetch_qxc_live_parses_sales_not_sum_column():
    html = """
    <html><body><table id="tablelist">
      <tr><td>26134</td><td>7 2 4 7 6 3 14</td><td>43</td><td>13,204,226</td><td>2026-11-22</td></tr>
    </table></body></html>
    """
    fake_resp = MagicMock()
    fake_resp.content = html.encode("gb18030")
    fake_resp.raise_for_status = MagicMock()

    with patch("backend.app.db.sync_service._get_with_retry", new=AsyncMock(return_value=fake_resp)):
        rows = await fetch_qxc_live(MagicMock(), count=10)

    assert len(rows) == 1
    assert rows[0]["issue"] == "2026134"
    assert rows[0]["sales_amount"] == 13204226
    assert rows[0]["pool_amount"] == 0


@pytest.mark.asyncio
async def test_gemini_call_skips_thought_parts():
    req = BaziDivinationRequest(
        lottery_type="ssq",
        recommended_numbers=DivinationTicket(primary_numbers=["01", "02", "03", "04", "05", "06"], secondary_numbers=["07"]),
        user_name="曾练",
    )
    fake_resp = MagicMock()
    fake_resp.raise_for_status = MagicMock()
    fake_resp.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": "Internal English chain-of-thought...", "thought": True},
                        {"text": "【命盘乾坤】正式中文解盘第一段。"},
                        {"text": "【理性倡议】请理性娱乐。"},
                    ]
                }
            }
        ]
    }
    fake_client = MagicMock()
    fake_client.post = AsyncMock(return_value=fake_resp)

    class _FakeCtx:
        async def __aenter__(self):
            return fake_client

        async def __aexit__(self, *args):
            return False

    with patch("backend.app.services.ai_service._acquire_http_client", return_value=_FakeCtx()):
        out = await ai_service._call_gemini_api(req, "dummy-key", "gemini-2.5-flash")

    assert "Internal English" not in out
    assert "【命盘乾坤】正式中文解盘第一段。" in out
    assert "【理性倡议】请理性娱乐。" in out


@pytest.mark.asyncio
async def test_stream_divination_events_emits_fallback_metadata():
    req = BaziDivinationRequest(
        lottery_type="ssq",
        recommended_numbers=DivinationTicket(primary_numbers=["01", "02", "03", "04", "05", "06"], secondary_numbers=["07"]),
        user_name="曾练",
        provider="fallback",
    )
    events = [evt async for evt in ai_service.stream_divination_events(req)]
    assert len(events) > 2
    first = events[0]
    assert first.get("provider") == "fallback"
    assert first.get("model") == "algorithmic-master-v1"
    assert first.get("fallback_reason")
    full_text = "".join(e.get("delta", "") for e in events)
    assert "【命盘乾坤】" in full_text
    assert "理性" in full_text


def test_strategy_alias_map_covers_short_english_aliases():
    assert STRATEGY_ALIAS_MAP["hot"] == "hot_chase"
    assert STRATEGY_ALIAS_MAP["cold"] == "cold_reversal"
    assert STRATEGY_ALIAS_MAP["bazi_heavy"] == "bazi_priority"
    assert STRATEGY_ALIAS_MAP["balanced"] == "balanced"


def test_v150_emoji_nickname_allowed_and_xss_blocked():
    from pydantic import ValidationError

    # 大众昵称含 Emoji/标点应放行，避免排盘 200 但 AI 解盘 422
    req = BaziDivinationRequest(
        lottery_type="ssq",
        recommended_numbers=DivinationTicket(primary_numbers=["01", "02", "03", "04", "05", "06"], secondary_numbers=["07"]),
        user_name="曾先生🎉·2026!",
    )
    assert req.user_name == "曾先生🎉·2026!"

    # 尖括号注入与控制字符仍须拦截
    with pytest.raises(ValidationError):
        BaziDivinationRequest(
            lottery_type="ssq",
            recommended_numbers=DivinationTicket(primary_numbers=["01", "02", "03", "04", "05", "06"], secondary_numbers=["07"]),
            user_name="<script>alert(1)</script>",
        )


def test_v150_dayun_start_age_matches_first_detail_and_lichun_note():
    from backend.app.services.bazi_engine import calculate_four_pillars

    # 立春前日（2025-02-02 农历乙巳年正月初五，但八字仍属甲辰年）
    res = calculate_four_pillars("2025-02-02T10:00:00", calendar_type="solar", gender="male")
    assert res["dayun_details"], "dayun_details should not be empty"
    assert res["dayun_start_age"] == res["dayun_details"][0]["start_age"]
    assert "[八字甲辰年]" in res["lunar_datetime"]


def test_v150_fallback_divination_respects_weak_vs_strong_strength():
    weak_req = BaziDivinationRequest(
        lottery_type="ssq",
        recommended_numbers=DivinationTicket(primary_numbers=["01", "02", "03", "04", "05", "06"], secondary_numbers=["07"]),
        user_name="曾先生",
        bazi_summary={"day_master": "辛", "strength": "身弱", "xi_yong_shen": ["土", "金"]},
    )
    weak_text = ai_service.generate_fallback_divination(weak_req)
    assert "生逢生旺之令，气盛神足" not in weak_text
    assert "气机清灵内敛" in weak_text

    strong_req = BaziDivinationRequest(
        lottery_type="ssq",
        recommended_numbers=DivinationTicket(primary_numbers=["01", "02", "03", "04", "05", "06"], secondary_numbers=["07"]),
        user_name="曾先生",
        bazi_summary={"day_master": "甲", "strength": "身强", "xi_yong_shen": ["火", "土"]},
    )
    strong_text = ai_service.generate_fallback_divination(strong_req)
    assert "生逢生旺之令，气盛神足" in strong_text


def test_v151_global_rate_limit_middleware_blocks_excess_and_exempts_health():
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    from backend.app.main import GlobalRateLimitMiddleware

    mini = FastAPI()
    mini.add_middleware(GlobalRateLimitMiddleware, max_requests_per_min=2, force_enable=True)

    @mini.get("/api/health")
    def _h():
        return {"ok": True}

    @mini.get("/api/ping")
    def _p():
        return {"pong": True}

    client = TestClient(mini)
    assert client.get("/api/ping").status_code == 200
    assert client.get("/api/ping").status_code == 200
    blocked = client.get("/api/ping")
    assert blocked.status_code == 429
    assert blocked.headers.get("Retry-After") == "15"
    # 健康检查端点必须始终豁免，防止探针误杀容器
    assert client.get("/api/health").status_code == 200


def test_v151_hot_read_cache_header_and_version_bump(tmp_path):
    from fastapi.testclient import TestClient
    from backend.app.db.database import get_data_version, init_db, upsert_draws
    from backend.app.main import app

    client = TestClient(app)
    r1 = client.get("/api/lottery/stats/ssq?limit=50")
    assert r1.status_code == 200
    assert "max-age=300" in (r1.headers.get("Cache-Control") or "")

    isolated_db = tmp_path / "cache_test.db"
    init_db(isolated_db, seed_if_empty=False)
    v_before = get_data_version()
    upsert_draws(
        "ssq",
        [
            {
                "issue": "2099999",
                "draw_date": "2099-01-01",
                "primary_numbers": ["01", "02", "03", "04", "05", "06"],
                "secondary_numbers": ["07"],
                "sales_amount": 1000,
                "pool_amount": 2000,
            }
        ],
        db_path=isolated_db,
    )
    assert get_data_version() > v_before


@pytest.mark.asyncio
async def test_v151_sporttery_backup_source_when_500_fails():
    fake_sporttery_resp = MagicMock()
    fake_sporttery_resp.json.return_value = {
        "value": {
            "list": [
                {
                    "lotteryDrawNum": "26135",
                    "lotteryDrawTime": "2026-11-23",
                    "lotteryDrawResult": "03 11 19 25 34 04 11",
                    "totalSaleAmount": "320,100,000",
                    "poolBalanceAfterdraw": "812,500,000",
                }
            ]
        }
    }

    async def _side_effect(client, url, headers, max_retries=2):
        if "500.com" in url:
            raise RuntimeError("500.com temporarily down")
        return fake_sporttery_resp

    with patch("backend.app.db.sync_service._get_with_retry", side_effect=_side_effect):
        rows = await fetch_dlt_live(MagicMock(), count=10)

    assert len(rows) == 1
    assert rows[0]["issue"] == "2026135"
    assert rows[0]["primary_numbers"] == ["03", "11", "19", "25", "34"]
    assert rows[0]["secondary_numbers"] == ["04", "11"]
    assert rows[0]["pool_amount"] == 812500000
    assert rows[0]["sales_amount"] == 320100000
