"""Tier 4 Scenario 4: QXC Complete AI Divination Streaming Workflow Lifecycle.
Flow: Destiny Analysis -> QXC Recommendation -> AI Divination Streaming Relay -> Responsible Gaming Compliance.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_scenario_qxc_ai_divination():
    """Scenario 4: Complete end-to-end journey for QXC generation with AI Divination Master."""
    client = get_test_client()

    birth_dt = "1992-07-07T21:30:00"

    # Step 1: Bazi Destiny Analysis
    destiny = client.post_destiny({
        "birth_datetime": birth_dt,
        "calendar_type": "solar",
        "gender": "male",
    })
    assert destiny["status_code"] == 200
    bazi = destiny["data"]

    # Step 2: Generate QXC Ticket Recommendation
    rec_resp = client.post_recommend({
        "birth_datetime": birth_dt,
        "lottery_type": "qxc",
        "count": 1,
        "strategy": "balanced",
    })
    assert rec_resp["status_code"] == 200
    ticket = rec_resp["data"]["recommendations"][0]

    # Step 3: Validate Ticket Validity
    valid, _, norm_p, norm_s, fmt = ReferenceOracle.validate_ticket(
        "qxc", ticket["primary_numbers"], ticket["secondary_numbers"]
    )
    assert valid is True
    assert len(norm_p) == 6
    assert len(norm_s) == 1

    # Step 4: Dispatch to AI Divination Service
    ai_resp = client.post_divination({
        "birth_datetime": birth_dt,
        "lottery_type": "qxc",
        "recommended_numbers": {
            "front_six": norm_p,
            "seventh_ball": norm_s,
        },
        "bazi_summary": {
            "day_master": bazi["day_master"],
            "strength": bazi["strength"],
            "xi_yong_shen": bazi["xi_yong_shen"],
        },
    })
    assert ai_resp["status_code"] == 200
    divination_data = ai_resp["data"]
    divination_text = divination_data["divination_text"]

    # Step 5: Verify Structured 5-Part Template Output
    assert "命盘" in divination_text
    assert "数理" in divination_text
    assert "五行" in divination_text
    assert "吉言" in divination_text or "灵感" in divination_text
    assert "理性" in divination_text

    # Step 6: Verify Responsible Gaming Disclaimer Enforcement
    assert any(term in divination_text for term in ["理性", "娱乐", "量力而行", "概率"])
