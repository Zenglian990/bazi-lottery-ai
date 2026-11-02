"""Unit and integration tests for Hetu numerology, A-Res sampling, lottery recommendations, and API endpoint."""
import pytest
from starlette.testclient import TestClient

from backend.app.main import app
from backend.app.services.numerology_engine import (
    get_hetu_element,
    partition_numbers_by_element,
    compute_bazi_score,
    a_res_weighted_sample_without_replacement,
    weighted_choice_with_replacement,
    recommend_lottery_tickets,
    resolve_strategy_weights,
)
from backend.app.services.bazi_engine import calculate_four_pillars
from backend.app.services.lottery_rules import validate_ticket


@pytest.fixture
def api_client():
    return TestClient(app)


class TestHetuNumerologyMapping:
    """Tests for classical Hetu tail-modulo 10 mapping and full range partitions."""

    def test_hetu_classical_mapping(self):
        """1/6 Water, 2/7 Fire, 3/8 Wood, 4/9 Metal, 5/0 Earth."""
        assert get_hetu_element(1) == "water"
        assert get_hetu_element(6) == "water"
        assert get_hetu_element(11) == "water"
        assert get_hetu_element(26) == "water"

        assert get_hetu_element(2) == "fire"
        assert get_hetu_element(7) == "fire"
        assert get_hetu_element(17) == "fire"

        assert get_hetu_element(3) == "wood"
        assert get_hetu_element(8) == "wood"
        assert get_hetu_element(28) == "wood"

        assert get_hetu_element(4) == "metal"
        assert get_hetu_element(9) == "metal"
        assert get_hetu_element(34) == "metal"

        assert get_hetu_element(5) == "earth"
        assert get_hetu_element(0) == "earth"
        assert get_hetu_element(20) == "earth"

    def test_ssq_red_full_partition(self):
        """All 33 red balls (1-33) partition into the 5 elements."""
        part = partition_numbers_by_element(1, 33)
        total_balls = sum(len(nums) for nums in part.values())
        assert total_balls == 33
        assert set().union(*part.values()) == set(range(1, 34))

    def test_dlt_front_full_partition(self):
        """All 35 DLT front balls (1-35) partition evenly (7 balls per element)."""
        part = partition_numbers_by_element(1, 35)
        for elem, nums in part.items():
            assert len(nums) == 7
        assert sum(len(nums) for nums in part.values()) == 35

    def test_fc3d_and_qxc_partition(self):
        """FC3D (0-9, 2 per element) and QXC 7th ball (0-14, 3 per element)."""
        p3d = partition_numbers_by_element(0, 9)
        for elem, nums in p3d.items():
            assert len(nums) == 2

        pqxc = partition_numbers_by_element(0, 14)
        for elem, nums in pqxc.items():
            assert len(nums) == 3


class TestLuckyNumberTiers:
    """Tests for 5-tier lucky number classification and score assignments."""

    def test_five_tier_scores(self):
        xi_yong = ["wood", "fire"]
        lacking = ["water"]
        ji_shen = ["metal", "earth"]

        # Wood ball (e.g. 3) -> Tier 1 (1.00)
        sc1, tier1 = compute_bazi_score(3, xi_yong, lacking, ji_shen)
        assert sc1 == 1.00
        assert tier1 == "Tier 1"

        # Fire ball (e.g. 7) -> Tier 2 (0.80)
        sc2, tier2 = compute_bazi_score(7, xi_yong, lacking, ji_shen)
        assert sc2 == 0.80
        assert tier2 == "Tier 2"

        # Water ball (e.g. 6) -> Tier 3 (0.60)
        sc3, tier3 = compute_bazi_score(6, xi_yong, lacking, ji_shen)
        assert sc3 == 0.60
        assert tier3 == "Tier 3"

        # Metal ball (e.g. 4) -> Tier 5 (0.15)
        sc5, tier5 = compute_bazi_score(4, xi_yong, lacking, ji_shen)
        assert sc5 == 0.15
        assert tier5 == "Tier 5"


class TestAResSamplingLegality:
    """Tests for A-Res sampling, no duplicate balls, ascending order, and ticket legality."""

    def test_a_res_no_duplicates_and_sorted(self):
        """A-Res sampling generates unique, sorted selections."""
        candidates = [(i, float(i)) for i in range(1, 34)]
        sampled = a_res_weighted_sample_without_replacement(candidates, 6)

        assert len(sampled) == 6
        assert len(set(sampled)) == 6
        assert sampled == sorted(sampled)
        assert all(1 <= x <= 33 for x in sampled)

    def test_ssq_recommendation_legality(self):
        """Generated SSQ recommendation validates 100% against official rules."""
        bazi = calculate_four_pillars("1990-08-15T08:30:00")
        res = recommend_lottery_tickets(bazi, lottery_type="ssq", count=10)
        recs = res["recommendations"]
        assert len(recs) == 10

        for r in recs:
            valid, err, _, _, _ = validate_ticket("ssq", r["primary_numbers"], r["secondary_numbers"])
            assert valid is True, f"Ticket validation failed: {err}"
            # Check wuxing distribution sums to total balls (6 + 1 = 7)
            assert sum(r["wuxing_distribution"].values()) == 7
            assert "score_rationale" in r

    def test_dlt_recommendation_legality(self):
        """Generated DLT recommendation validates 100% (5 front + 2 back)."""
        bazi = calculate_four_pillars("1988-05-20T10:30:00")
        res = recommend_lottery_tickets(bazi, lottery_type="dlt", count=5)
        recs = res["recommendations"]
        assert len(recs) == 5

        for r in recs:
            valid, err, _, _, _ = validate_ticket("dlt", r["primary_numbers"], r["secondary_numbers"])
            assert valid is True, f"Ticket validation failed: {err}"
            assert len(r["primary_numbers"]) == 5
            assert len(r["secondary_numbers"]) == 2

    def test_fc3d_and_qxc_recommendation_legality(self):
        """FC3D (3 digits) and QXC (6 front + 1 back) recommendations are strictly valid."""
        bazi = calculate_four_pillars("1995-10-01T15:00:00")

        # FC3D
        res_3d = recommend_lottery_tickets(bazi, lottery_type="fc3d", count=5)
        for r in res_3d["recommendations"]:
            valid, err, _, _, _ = validate_ticket("fc3d", r["primary_numbers"])
            assert valid is True
            assert len(r["primary_numbers"]) == 3

        # QXC
        res_qxc = recommend_lottery_tickets(bazi, lottery_type="qxc", count=5)
        for r in res_qxc["recommendations"]:
            valid, err, _, _, _ = validate_ticket("qxc", r["primary_numbers"], r["secondary_numbers"])
            assert valid is True
            assert len(r["primary_numbers"]) == 6
            assert len(r["secondary_numbers"]) == 1

    def test_extreme_weights_configurations(self):
        """Pure Bazi (1,0,0), Pure Freq (0,1,0), Pure Omission (0,0,1), and All-Zero (0,0,0)."""
        bazi = calculate_four_pillars("1990-08-15T08:30:00")

        for wb, wf, wo in [(1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0), (0.0, 0.0, 0.0)]:
            res = recommend_lottery_tickets(
                bazi, lottery_type="ssq", count=3, w_bazi=wb, w_freq=wf, w_omission=wo
            )
            for r in res["recommendations"]:
                valid, err, _, _, _ = validate_ticket("ssq", r["primary_numbers"], r["secondary_numbers"])
                assert valid is True

    def test_deterministic_mode_consistency(self):
        """Deterministic mode produces identical numbers for identical inputs."""
        bazi = calculate_four_pillars("1990-08-15T08:30:00")
        res1 = recommend_lottery_tickets(bazi, lottery_type="ssq", count=1, deterministic=True)
        res2 = recommend_lottery_tickets(bazi, lottery_type="ssq", count=1, deterministic=True)

        assert res1["recommendations"][0]["primary_numbers"] == res2["recommendations"][0]["primary_numbers"]
        assert res1["recommendations"][0]["secondary_numbers"] == res2["recommendations"][0]["secondary_numbers"]


class TestBaziRecommendApi:
    """Integration tests for POST /api/bazi/recommend endpoint."""

    def test_recommend_endpoint_ssq_success(self, api_client):
        payload = {
            "birth_datetime": "1990-08-15T08:30:00",
            "calendar_type": "solar",
            "lottery_type": "ssq",
            "count": 3,
            "strategy": "balanced",
            "w_bazi": 0.5,
            "w_freq": 0.3,
            "w_omission": 0.2,
        }
        resp = api_client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        assert data["lottery_type"] == "ssq"
        assert data["strategy"] == "balanced"
        assert len(data["recommendations"]) == 3

        for rec in data["recommendations"]:
            assert len(rec["primary_numbers"]) == 6
            assert len(rec["secondary_numbers"]) == 1
            assert "wuxing_distribution" in rec
            assert "score_rationale" in rec

    def test_recommend_endpoint_without_birth_datetime(self, api_client):
        """Omitting birth_datetime safely uses current time."""
        payload = {
            "lottery_type": "dlt",
            "count": 2,
        }
        resp = api_client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["recommendations"]) == 2

    def test_recommend_endpoint_unsupported_lottery(self, api_client):
        """Unsupported lottery type raises 400."""
        payload = {
            "lottery_type": "unsupported_lottery",
            "count": 1,
        }
        resp = api_client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 400
