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
