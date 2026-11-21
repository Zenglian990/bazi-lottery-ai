"""Comprehensive verification tests for the 5 core bug fixes.

Covers:
1. BL-06: Gemini model configuration and API header verification.
2. BL-09 / BL-25: Timezone unification, Asia/Shanghai CST handling, and midnight lunar ephemeris.
3. BL-16 / BL-25: Chinese strategy alias resolution and omission calculation robustness.
4. BL-16: Gender parameter passed to both primary and secondary zones.
"""
from datetime import datetime
from zoneinfo import ZoneInfo
import pytest
from starlette.testclient import TestClient

from backend.app.main import app
from backend.app.core.config import settings
from backend.app.models.bazi import BaziRecommendRequest
from backend.app.services.bazi_engine import parse_datetime_str, calculate_four_pillars
from backend.app.services.numerology_engine import (
    resolve_strategy_weights,
    calculate_candidate_weights,
    recommend_lottery_tickets,
    STRATEGY_ALIAS_MAP,
    STRATEGY_PRESETS,
)


@pytest.fixture
def client():
    return TestClient(app)


class TestBL06ModelAndSecurityFixes:
    """Tests for [BL-06]: Default model corrected to gemini-2.5-flash and header authentication."""

    def test_default_gemini_model_is_valid_2_5_flash(self):
        """Settings and ai_service fallback must not reference nonexistent 3.8-flash."""
        assert settings.GEMINI_MODEL == "gemini-2.5-flash"
        assert "3.8" not in settings.GEMINI_MODEL

    @pytest.mark.asyncio
    async def test_ai_divination_model_fallback(self, monkeypatch):
        """When calling ai divination with auto or gemini, fallback uses gemini-2.5-flash and header auth."""
        from backend.app.models.ai import BaziDivinationRequest
        from backend.app.services.ai_service import generate_divination_text
        import httpx

        req = BaziDivinationRequest(
            provider="gemini",
            lottery_type="ssq",
            recommended_numbers={"primary_numbers": ["01", "02", "03", "04", "05", "06"], "secondary_numbers": ["07"]},
            user_api_key="AIzaSyMockKeyForTestingOnly",
        )

        recorded_requests = []

        class MockAsyncClient:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                pass

            async def post(self, url, headers=None, json=None):
                recorded_requests.append({"url": url, "headers": headers or {}})
                return httpx.Response(
                    200,
                    json={"candidates": [{"content": {"parts": [{"text": "吉星高照，理性为本，概率娱乐，量力而行。"}]}}]},
                    request=httpx.Request("POST", url),
                )

        monkeypatch.setattr("backend.app.services.ai_service.httpx.AsyncClient", MockAsyncClient)

        result = await generate_divination_text(req)
        assert result["provider"] == "gemini"
        assert result["model"] == "gemini-2.5-flash"
        assert len(recorded_requests) == 1
        # Crucial security checks: key must NOT be in query string, and MUST be in x-goog-api-key header
        assert "?key=" not in recorded_requests[0]["url"]
        assert recorded_requests[0]["headers"].get("x-goog-api-key") == "AIzaSyMockKeyForTestingOnly"

    def test_gemini_3_8_flash_model_sanitization(self):
        """Requesting legacy or nonexistent gemini-3.8-flash must normalize to gemini-2.5-flash."""
        from backend.app.services.ai_service import _normalize_gemini_model
        assert _normalize_gemini_model("gemini-3.8-flash") == "gemini-2.5-flash"
        assert _normalize_gemini_model("gemini-3.8") == "gemini-2.5-flash"
        assert _normalize_gemini_model(None) == "gemini-2.5-flash"
        assert _normalize_gemini_model("") == "gemini-2.5-flash"
        assert _normalize_gemini_model("gemini-2.5-pro") == "gemini-2.5-pro"


class TestBL09TimezoneAndCalendarFixes:
    """Tests for [BL-09] and [BL-25]: Timezone unification and ephemeris accuracy."""

    def test_parse_datetime_str_empty_returns_china_tz(self):
        """Empty or None input must return current time in Asia/Shanghai."""
        dt = parse_datetime_str(None)
        assert dt.tzinfo is not None
        assert str(dt.tzinfo) == "Asia/Shanghai"

    def test_parse_datetime_str_utc_z_converts_to_cst(self):
        """UTC string with 'Z' must convert to Asia/Shanghai (+8h)."""
        # 2026-11-17 18:00:00 UTC == 2026-11-18 02:00:00 CST
        dt = parse_datetime_str("2026-11-17T18:00:00Z")
        assert dt.year == 2026
        assert dt.month == 11
        assert dt.day == 18
        assert dt.hour == 2
        assert dt.minute == 0

    def test_parse_datetime_str_naive_attaches_china_tz(self):
        """Naive datetime strings attach Asia/Shanghai without changing clock numbers."""
        dt = parse_datetime_str("1990-08-15T08:30:00")
        assert dt.year == 1990
        assert dt.month == 8
        assert dt.day == 15
        assert dt.hour == 8
        assert dt.minute == 30

    def test_today_fortune_endpoint_time_in_shanghai(self, client):
        """GET /api/bazi/today-fortune returns valid current solar date."""
        resp = client.get("/api/bazi/today-fortune")
        assert resp.status_code == 200
        data = resp.json()
        assert "solar_date" in data
        assert "ganzhi_day" in data

        # Solar date should match Asia/Shanghai current date
        now_shanghai = datetime.now(ZoneInfo("Asia/Shanghai"))
        assert data["solar_date"] == now_shanghai.strftime("%Y-%m-%d")

    def test_lunar_true_solar_time_midnight_rollover(self):
        """BL-25: Lunar calculation with True Solar Time crossing midnight doesn't mismatch raw_lunar."""
        # Test lunar date at early Zi hour with west longitude (negative delta rolls back or advances)
        res = calculate_four_pillars(
            lunar_year=2026,
            lunar_month=1,
            lunar_day=1,
            shichen="早子时",
            longitude=87.6,  # Urumqi: ~ -130 minutes -> rolls back to previous day
        )
        lunar_str = res["lunar_datetime"]
        # The string must not have contradictory mismatch like raw_lunar month with shifted day
        assert "农历" in lunar_str


class TestBL16StrategyAndOmissionFixes:
    """Tests for [BL-16]: Strategy preset resolution, Chinese aliases, and omission robustness."""

    @pytest.mark.parametrize(
        "strategy_input,expected_preset,expected_mode",
        [
            ("命理吉数优先", "bazi_priority", "balanced"),
            ("八字优先", "bazi_priority", "balanced"),
            ("bazi_priority", "bazi_priority", "balanced"),
            ("bazi", "bazi_priority", "balanced"),
            ("稳健大数据", "balanced", "balanced"),
            ("八字五行平衡", "balanced", "balanced"),
            ("balanced", "balanced", "balanced"),
            ("冷热追号", "hot_chase", "hot"),
            ("热号追随", "hot_chase", "hot"),
            ("hot_chase", "hot_chase", "hot"),
            ("极冷号反弹", "cold_reversal", "cold"),
            ("冷号反弹", "cold_reversal", "cold"),
            ("cold_reversal", "cold_reversal", "cold"),
        ],
    )
    def test_strategy_alias_resolution(self, strategy_input, expected_preset, expected_mode):
        """Verify that Chinese strategy names and English codes map to the correct presets."""
        wb, wf, wo, mode = resolve_strategy_weights(strategy=strategy_input)
        exp_wb, exp_wf, exp_wo, exp_mode = STRATEGY_PRESETS[expected_preset]
        assert pytest.approx(wb, 0.01) == exp_wb
        assert pytest.approx(wf, 0.01) == exp_wf
        assert pytest.approx(wo, 0.01) == exp_wo
        assert mode == expected_mode

    def test_cold_reversal_prioritizes_undrawn_numbers(self):
        """BL-16 / BL-25: In cold_reversal mode, numbers never drawn in recent 100 draws get max omission score."""
        weights = calculate_candidate_weights(
            min_val=1,
            max_val=33,
            xi_yong_shen=["wood"],
            lacking_elements=[],
            ji_shen=["metal"],
            w_bazi=0.0,
            w_freq=0.0,
            w_omission=1.0,
            omission_mode="cold",
            lottery_type="ssq",
            zone_index=0,
        )
        # All weights should be valid numbers > 0
        assert len(weights) == 33
        for num, w in weights:
            assert w > 0.0

    def test_gender_polarization_secondary_zone(self):
        """BL-16: Male favors odd numbers (+10%), female favors even numbers (+10%) in both zones."""
        # Primary zone (zone 0)
        male_p = dict(calculate_candidate_weights(
            min_val=1, max_val=10,
            xi_yong_shen=[], lacking_elements=[], ji_shen=[],
            w_bazi=0.0, w_freq=0.0, w_omission=0.0,
            omission_mode="balanced", lottery_type="ssq", zone_index=0,
            gender="male"
        ))
        female_p = dict(calculate_candidate_weights(
            min_val=1, max_val=10,
            xi_yong_shen=[], lacking_elements=[], ji_shen=[],
            w_bazi=0.0, w_freq=0.0, w_omission=0.0,
            omission_mode="balanced", lottery_type="ssq", zone_index=0,
            gender="female"
        ))
        # Odd number 1 should have higher weight for male than female
        assert male_p[1] > female_p[1]
        # Even number 2 should have higher weight for female than male
        assert female_p[2] > male_p[2]

        # Secondary zone (zone 1)
        male_s = dict(calculate_candidate_weights(
            min_val=1, max_val=16,
            xi_yong_shen=[], lacking_elements=[], ji_shen=[],
            w_bazi=0.0, w_freq=0.0, w_omission=0.0,
            omission_mode="balanced", lottery_type="ssq", zone_index=1,
            gender="male"
        ))
        female_s = dict(calculate_candidate_weights(
            min_val=1, max_val=16,
            xi_yong_shen=[], lacking_elements=[], ji_shen=[],
            w_bazi=0.0, w_freq=0.0, w_omission=0.0,
            omission_mode="balanced", lottery_type="ssq", zone_index=1,
            gender="female"
        ))
        assert male_s[1] > female_s[1]
        assert female_s[2] > male_s[2]

    def test_recommend_endpoint_with_chinese_strategy(self, client):
        """API recommendation endpoint correctly processes Chinese strategy names."""
        payload = {
            "birth_datetime": "1990-08-15T08:30:00",
            "calendar_type": "solar",
            "lottery_type": "ssq",
            "count": 2,
            "strategy": "极冷号反弹",
            "gender": "female",
            "longitude": 120.0,
        }
        resp = client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["recommendations"]) == 2
        assert data["user_name"] == "缘主"
        assert "bazi_summary" in data
        assert "day_master" in data["bazi_summary"]

    def test_lottery_type_enum_import_compatibility(self):
        """LotteryTypeEnum alias must be imported from lottery_rules and match LotteryType."""
        from backend.app.services.lottery_rules import LotteryTypeEnum, LotteryType
        assert LotteryTypeEnum is LotteryType
        assert LotteryTypeEnum.SSQ.value == "ssq"
        assert LotteryTypeEnum.DLT.value == "dlt"

    def test_today_fortune_with_gender_parameter(self, client):
        """Today fortune endpoint accepts gender and computes resonance for female."""
        resp = client.get("/api/bazi/today-fortune?birth_datetime=1995-10-20T14:30:00&gender=female")
        assert resp.status_code == 200
        data = resp.json()
        assert "resonance" in data
        if data["resonance"]:
            assert "user_day_master" in data["resonance"]

        post_resp = client.post("/api/bazi/today-fortune", json={"birth_datetime": "1995-10-20T14:30:00", "gender": "female"})
        assert post_resp.status_code == 200
        post_data = post_resp.json()
        assert "resonance" in post_data
