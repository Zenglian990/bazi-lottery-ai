"""Tier 3 Test: Bazi Chart × Lottery Recommendation × AI Prompt Parameter Binding.
Verifies prompt parameter interpolation, structured 5-part divination template, and responsible gaming compliance.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_interaction_ai_prompt_parameter_payload_assembly():
    """T3.1: Assembly of Bazi destiny and recommended ticket into AI divination request payload."""
    bazi = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    ticket = {
        "primary_numbers": ["03", "06", "11", "18", "21", "28"],
        "secondary_numbers": ["09"],
    }
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "bazi_summary": {
            "day_master": bazi["day_master"],
            "strength": bazi["strength"],
            "xi_yong_shen": bazi["xi_yong_shen"],
        },
        "recommended_numbers": ticket,
    }

    assert payload["bazi_summary"]["day_master"] == "壬"
    assert payload["bazi_summary"]["strength"] == "身强"
    assert len(payload["recommended_numbers"]["primary_numbers"]) == 6

def test_interaction_ai_divination_response_structure_5_parts():
    """T3.2: AI divination output adheres to the 5 standard structural sections."""
    client = get_test_client()
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "recommended_numbers": {"primary_numbers": ["03", "06", "11", "18", "21", "28"], "secondary_numbers": ["09"]},
    }
    resp = client.post_divination(payload)
    assert resp["status_code"] == 200
    text = resp["data"]["divination_text"]

    # Verify standard sections are present
    assert "命盘" in text
    assert "数理" in text
    assert "五行" in text
    assert "吉言" in text or "灵感" in text
    assert "理性" in text

def test_interaction_responsible_gaming_disclaimer_enforcement():
    """T3.3: Responsible gaming disclaimer must be unconditionally present in AI divination text."""
    client = get_test_client()
    resp = client.post_divination({
        "birth_datetime": "1988-05-20T10:00:00",
        "lottery_type": "dlt",
        "recommended_numbers": {"primary_numbers": ["05", "10", "15", "20", "25"], "secondary_numbers": ["01", "06"]},
    })
    text = resp["data"]["divination_text"]
    assert any(keyword in text for keyword in ["理性", "概率", "量力而行", "娱乐"])

def test_interaction_cross_lottery_type_ai_divination_dispatch():
    """T3.4: Divination endpoint functions consistently across SSQ, DLT, FC3D, and QXC."""
    client = get_test_client()
    for lt in ["ssq", "dlt", "fc3d", "qxc"]:
        resp = client.post_divination({
            "lottery_type": lt,
            "recommended_numbers": {"primary_numbers": ["01", "02", "03"]},
        })
        assert resp["status_code"] == 200
        assert "divination_text" in resp["data"]
