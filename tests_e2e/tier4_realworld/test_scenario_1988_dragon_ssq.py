"""Tier 4 Scenario 1: 1988 Earth Dragon SSQ Balanced Pick Full Lifecycle.
Flow: Health Check -> Bazi Destiny Analysis -> SSQ Stats/HotCold -> Balanced Recommendation -> Ticket Validation.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_scenario_1988_dragon_ssq():
    """Scenario 1: Complete end-to-end journey for 1988-05-20 user selecting SSQ."""
    client = get_test_client()

    # Step 1: Health Check
    health = client.get_health()
    assert health["status_code"] == 200

    # Step 2: Bazi Destiny Analysis
    birth_dt = "1988-05-20T10:30:00"
    destiny = client.post_destiny({
        "birth_datetime": birth_dt,
        "calendar_type": "solar",
        "gender": "male",
    })
    assert destiny["status_code"] == 200
    d_data = destiny["data"]
    assert "four_pillars" in d_data
    assert "wuxing_percentage" in d_data
    assert d_data["strength"] in ("身强", "身弱")

    # Step 3: Query SSQ Stats and Hot/Cold distribution
    stats = client.get_stats("ssq")
    assert stats["status_code"] == 200
    hot_cold = client.get_hot_cold("ssq", window=30)
    assert hot_cold["status_code"] == 200

    # Step 4: Request SSQ Balanced Pick (w_bazi=0.5, w_freq=0.3, w_omission=0.2)
    rec_resp = client.post_recommend({
        "birth_datetime": birth_dt,
        "lottery_type": "ssq",
        "count": 1,
        "strategy": "balanced",
        "w_bazi": 0.5,
        "w_freq": 0.3,
        "w_omission": 0.2,
    })
    assert rec_resp["status_code"] == 200
    recs = rec_resp["data"]["recommendations"]
    assert len(recs) == 1
    ticket = recs[0]

    # Step 5: Official Ticket Validation
    val_resp = client.validate_ticket(
        lottery_type="ssq",
        primary_numbers=ticket["primary_numbers"],
        secondary_numbers=ticket["secondary_numbers"],
    )
    assert val_resp["data"]["is_valid"] is True
    assert "|" in val_resp["data"]["formatted_string"]

    # Step 6: Oracle Ground Truth Verification
    valid, err, norm_p, norm_s, fmt = ReferenceOracle.validate_ticket(
        "ssq", ticket["primary_numbers"], ticket["secondary_numbers"]
    )
    assert valid is True
    assert len(norm_p) == 6
    assert len(norm_s) == 1
    # Red balls must be in [1, 33] and strictly sorted
    red_ints = [int(x) for x in norm_p]
    assert red_ints == sorted(red_ints)
    assert all(1 <= r <= 33 for r in red_ints)
    # Blue ball in [1, 16]
    blue_int = int(norm_s[0])
    assert 1 <= blue_int <= 16
