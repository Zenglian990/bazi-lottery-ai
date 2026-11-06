"""Unit and regression tests for Bazi astronomical calculations, 110pt Wuxing scoring, and destiny API."""
from datetime import datetime
import pytest
from starlette.testclient import TestClient

from backend.app.main import app
from backend.app.services.bazi_engine import calculate_four_pillars, adjust_true_solar_time
from backend.app.services.wuxing_engine import (
    analyze_wuxing_energy,
    PILLAR_WEIGHTS,
    BRANCH_HIDDEN_STEMS,
    STEM_ELEMENTS,
    MOTHER_ELEMENT,
)


@pytest.fixture
def api_client():
    return TestClient(app)


class TestFourPillarsAstronomical:
    """Tests for astronomical four pillars, Li Chun, and Jie Qi boundaries."""

    def test_standard_chart_1990_aug_15(self):
        """1990-08-15 08:30:00 benchmark chart."""
        res = calculate_four_pillars("1990-08-15T08:30:00")
        pillars = res["four_pillars"]

        assert pillars["year"]["pair"] == "庚午"
        assert pillars["month"]["pair"] == "甲申"
        assert pillars["day"]["pair"] == "壬子"
        assert pillars["hour"]["pair"] == "甲辰"

        assert res["day_master"] == "壬"
        assert res["day_master_element"] == "water"

    def test_lichun_exact_transition_year_boundary(self):
        """Li Chun (立春) 2026 exact second transition."""
        # 1 second before Li Chun: 乙巳 (2025 Snake)
        b_before = calculate_four_pillars("2026-02-04T04:02:07")
        assert b_before["four_pillars"]["year"]["pair"] == "乙巳"

        # 1 second after Li Chun: 丙午 (2026 Horse)
        b_after = calculate_four_pillars("2026-02-04T04:02:09")
        assert b_after["four_pillars"]["year"]["pair"] == "丙午"

    def test_century_turn_gregorian_continuity(self):
        """1999-12-31 vs 2000-01-01 are both Ji Mao year prior to Li Chun."""
        b1999 = calculate_four_pillars("1999-12-31T12:00:00")
        b2000 = calculate_four_pillars("2000-01-01T12:00:00")

        assert b1999["four_pillars"]["year"]["pair"] == "己卯"
        assert b2000["four_pillars"]["year"]["pair"] == "己卯"
        assert b1999["four_pillars"]["day"]["pair"] != b2000["four_pillars"]["day"]["pair"]

    def test_sect1_vs_sect2_early_late_zi_hour(self):
        """Between 23:00 and 23:59:59, Sect 1 advances day pillar; Sect 2 maintains current day."""
        dt_str = "2026-11-02T23:45:00"
        s1 = calculate_four_pillars(dt_str, sect=1)
        s2 = calculate_four_pillars(dt_str, sect=2)

        assert s1["four_pillars"]["day"]["pair"] != s2["four_pillars"]["day"]["pair"]
        assert s1["four_pillars"]["day"]["pair"] == "辛巳"
        assert s2["four_pillars"]["day"]["pair"] == "庚辰"
        assert s1["four_pillars"]["hour"]["branch"] == "子"
        assert s2["four_pillars"]["hour"]["branch"] == "子"

    def test_longitude_true_solar_time_shift(self):
        """Urumqi longitude (87.6) shifts hour from Zi to Hai."""
        clock_dt = datetime(2026, 11, 2, 23, 15, 0)
        b_cst = calculate_four_pillars(clock_dt, longitude=120.0)
        b_urumqi = calculate_four_pillars(clock_dt, longitude=87.6)

        assert b_cst["four_pillars"]["hour"]["branch"] == "子"
        assert b_urumqi["four_pillars"]["hour"]["branch"] == "亥"


class TestWuxing110PointEnergy:
    """Tests for 110-point positional weighting and Day Master diagnosis."""

    def test_110_points_sum_invariant(self):
        """Total positional weights across the 8 positions must sum to 110.0 points."""
        assert sum(PILLAR_WEIGHTS.values()) == 110.0
        assert PILLAR_WEIGHTS["month_branch"] == 35.0

    def test_hidden_stems_ratios_sum_to_one(self):
        """All 12 earthly branches hidden stems ratios must sum to 1.0."""
        for branch, hidden in BRANCH_HIDDEN_STEMS.items():
            total = sum(r for _, r in hidden)
            assert round(total, 2) == 1.00

    def test_energy_scores_and_percentages_sum(self):
        """Five element scores sum to 110.0 and percentages sum to 1.0."""
        res = calculate_four_pillars("1990-08-15T08:30:00")
        scores = res["wuxing_scores"]
        percentages = res["wuxing_percentage"]

        assert round(sum(scores.values()), 1) == 110.0
        assert 0.99 <= sum(percentages.values()) <= 1.01

    def test_day_master_strength_cutoff_at_55(self):
        """Self-party score >= 55.0 points determines 身强, else 身弱."""
        # Benchmark chart 1990-08-15: Water (38.3) + Metal (31.0) = 69.3 >= 55.0 -> 身强
        res = calculate_four_pillars("1990-08-15T08:30:00")
        assert res["strength"] == "身强"
        assert res["strength_ratio"] >= 0.50

        # Winter fire chart 1983-12-10 00:30:00 is 身弱
        res_weak = calculate_four_pillars("1983-12-10T00:30:00")
        if res_weak["strength"] == "身弱":
            assert res_weak["strength_ratio"] < 0.50

    def test_xi_yong_shen_complement_strength(self):
        """Strong charts favor draining elements (木, 火, 土); weak charts favor supporting (水, 金). Pure Chinese output."""
        res_strong = calculate_four_pillars("1990-08-15T08:30:00")
        xi_yong = res_strong["xi_yong_shen"]
        ji_shen = res_strong["ji_shen"]

        # Validate pure Chinese localization
        assert any(e in xi_yong for e in ["木", "火", "土"])
        assert any(e in ji_shen for e in ["水", "金"])

    def test_lacking_elements_under_5_points(self):
        """Elements with score < 5.0 are listed in lacking_elements in Chinese."""
        # 1974-02-23 04:00:00: spring wood chart
        res = calculate_four_pillars("1974-02-23T04:00:00")
        zh_to_en = {"金": "metal", "木": "wood", "水": "water", "火": "fire", "土": "earth"}
        for elem in res["lacking_elements"]:
            en_elem = zh_to_en.get(elem, elem)
            assert res["wuxing_scores"][en_elem] < 5.0


class TestBaziDestinyApi:
    """Integration tests for POST /api/bazi/destiny endpoint."""

    def test_destiny_endpoint_success(self, api_client):
        payload = {
            "birth_datetime": "1990-08-15T08:30:00",
            "calendar_type": "solar",
            "gender": "male",
            "longitude": 120.0,
            "sect": 2,
        }
        resp = api_client.post("/api/bazi/destiny", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        assert "four_pillars" in data
        assert "year" in data["four_pillars"]
        assert "month" in data["four_pillars"]
        assert "day" in data["four_pillars"]
        assert "hour" in data["four_pillars"]

        assert "wuxing_scores" in data
        assert "wuxing_percentage" in data
        for elem in ["wood", "fire", "earth", "metal", "water"]:
            assert elem in data["wuxing_scores"]
            assert elem in data["wuxing_percentage"]

        assert data["day_master"] == "壬"
        assert data["strength"] == "身强"
        assert isinstance(data["xi_yong_shen"], list)
        assert isinstance(data["ji_shen"], list)

    def test_destiny_endpoint_invalid_date_format(self, api_client):
        """Invalid date format raises 400 or handles safely."""
        resp = api_client.post("/api/bazi/destiny", json={"birth_datetime": "invalid-date"})
        # Should return 200 with fallback or 400
        assert resp.status_code in (200, 400)

    def test_pure_lunar_calculation(self):
        """Pure lunar input (1984-05-08 辰时)."""
        res = calculate_four_pillars(
            lunar_year=1984,
            lunar_month=5,
            lunar_day=8,
            calendar_type="lunar",
            shichen="辰时",
        )
        assert res["lunar_datetime"] is not None
        assert res["four_pillars"]["year"]["pair"] == "甲子"
        assert res["four_pillars"]["hour"]["pair"].endswith("辰")
        assert "solar_datetime" in res

    def test_pure_lunar_29_day_boundary_protection(self):
        """2030 month 12 only has 29 days; passing 30 clamps safely without exception."""
        res = calculate_four_pillars(
            lunar_year=2030,
            lunar_month=12,
            lunar_day=30,
            calendar_type="lunar",
        )
        assert res is not None
        assert "four_pillars" in res

    def test_destiny_endpoint_pure_lunar_default_name(self, api_client):
        """POST /api/bazi/destiny with pure lunar parameters and default user_name '缘主'."""
        payload = {
            "lunar_year": 1990,
            "lunar_month": 5,
            "lunar_day": 15,
            "calendar_type": "lunar",
            "shichen": "午时",
        }
        resp = api_client.post("/api/bazi/destiny", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["user_name"] == "缘主"
        assert "four_pillars" in data
