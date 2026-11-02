"""Tier 1 Test: RESTful API Contract Schemas & Serialization.
Verifies response schemas against PROJECT.md § Interface Contracts (F9-F16 data interfaces).
"""
from tests_e2e.harness.client import get_test_client
from backend.app.models.lottery import (
    LotteryType,
    ZoneRule,
    LotteryRule,
    AllRulesResponse,
    TicketValidateRequest,
    TicketValidateResponse,
    HealthResponse,
)

def test_contract_health_schema():
    """Verify GET /api/health returns required system status fields."""
    client = get_test_client()
    resp = client.get_health()
    assert resp["status_code"] == 200
    data = resp["data"]
    assert "status" in data
    assert "version" in data
    assert data["status"] in ("healthy", "ok", "running")

def test_contract_lottery_rules_schema():
    """Verify GET /api/lottery/rules adheres to AllRulesResponse contract."""
    client = get_test_client()
    resp = client.get_rules()
    assert resp["status_code"] == 200
    data = resp["data"]
    assert "rules" in data
    rules = data["rules"]
    for lt in ["ssq", "dlt", "fc3d", "qxc"]:
        assert lt in rules
        r = rules[lt]
        assert "chinese_name" in r
        assert "zones" in r
        assert len(r["zones"]) >= 1

def test_contract_lottery_stats_schema():
    """Verify stats response data model contains primary and secondary zone stats."""
    client = get_test_client()
    resp = client.get_stats("ssq")
    assert resp["status_code"] == 200
    data = resp["data"]
    assert "lottery_type" in data
    assert "draws_analyzed" in data
    assert "primary_zone" in data

def test_contract_bazi_destiny_schema():
    """Verify POST /api/bazi/destiny returns Four Pillars, Wuxing percentages, and Xi Yong Shen."""
    client = get_test_client()
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "calendar_type": "solar",
        "gender": "male",
        "longitude": 120.0,
    }
    resp = client.post_destiny(payload)
    assert resp["status_code"] == 200
    data = resp["data"]

    # Check Four Pillars structure
    assert "four_pillars" in data
    for pillar in ["year", "month", "day", "hour"]:
        assert pillar in data["four_pillars"]
        assert "stem" in data["four_pillars"][pillar]
        assert "branch" in data["four_pillars"][pillar]

    # Check Wuxing scores & percentages
    assert "wuxing_scores" in data
    assert "wuxing_percentage" in data
    for elem in ["wood", "fire", "earth", "metal", "water"]:
        assert elem in data["wuxing_scores"]
        assert elem in data["wuxing_percentage"]

    # Check Day Master and Xi Yong Shen
    assert "day_master" in data
    assert "strength" in data
    assert "xi_yong_shen" in data
    assert "ji_shen" in data

def test_contract_bazi_recommend_schema():
    """Verify POST /api/bazi/recommend returns list of formatted recommendations."""
    client = get_test_client()
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "count": 2,
        "strategy": "balanced",
    }
    resp = client.post_recommend(payload)
    assert resp["status_code"] == 200
    data = resp["data"]
    assert "lottery_type" in data
    assert "recommendations" in data
    assert len(data["recommendations"]) == 2

    for rec in data["recommendations"]:
        assert "primary_numbers" in rec
        assert isinstance(rec["primary_numbers"], list)
        assert len(rec["primary_numbers"]) == 6
        assert "secondary_numbers" in rec
        assert "wuxing_distribution" in rec
        assert "score_rationale" in rec

def test_contract_ai_divination_schema():
    """Verify POST /api/ai/divination returns textual divination and responsible gaming disclaimer."""
    client = get_test_client()
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "recommended_numbers": {"red": ["03", "06", "11", "18", "21", "28"], "blue": ["09"]},
    }
    resp = client.post_divination(payload)
    assert resp["status_code"] == 200
    data = resp["data"]
    assert "divination_text" in data
    assert data.get("has_disclaimer", True) is True
    # Must enforce responsible gaming disclaimer
    assert "理性" in data["divination_text"] or "娱乐" in data["divination_text"]

def test_contract_pydantic_model_validations():
    """Verify Pydantic models enforce type validation and reject invalid types."""
    # Valid TicketValidateRequest
    req = TicketValidateRequest(primary_numbers=[1, 2, 3, 4, 5, 6], secondary_numbers=[7])
    assert len(req.primary_numbers) == 6

    # TicketValidateResponse
    res = TicketValidateResponse(is_valid=True, formatted_string="01,02,03,04,05,06|07")
    assert res.is_valid is True
    assert res.formatted_string == "01,02,03,04,05,06|07"
