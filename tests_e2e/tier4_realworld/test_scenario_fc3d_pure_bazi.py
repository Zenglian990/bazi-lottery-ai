"""Tier 4 Scenario 3: FC3D Pure Bazi Numerology Pick Lifecycle.
Flow: Destiny Analysis -> FC3D Rules Query -> Pure Bazi Recommendation -> Single Zone Validation.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_scenario_fc3d_pure_bazi():
    """Scenario 3: Complete end-to-end journey for FC3D user using pure Bazi numerology."""
    client = get_test_client()

    birth_dt = "2000-01-01T00:30:00"

    # Step 1: Bazi Destiny Analysis
    destiny = client.post_destiny({
        "birth_datetime": birth_dt,
        "calendar_type": "solar",
        "gender": "male",
    })
    assert destiny["status_code"] == 200
    bazi = destiny["data"]
    assert "day_master" in bazi

    # Step 2: Query FC3D Rule
    rules_resp = client.get_rules()
    assert rules_resp["status_code"] == 200
    fc3d_rule = rules_resp["data"]["rules"]["fc3d"]
    assert fc3d_rule["zones"][0]["count"] == 3
    assert fc3d_rule["zones"][0]["min_value"] == 0
    assert fc3d_rule["zones"][0]["max_value"] == 9

    # Step 3: Recommend FC3D with Pure Bazi Strategy (w_bazi=1.0)
    rec_resp = client.post_recommend({
        "birth_datetime": birth_dt,
        "lottery_type": "fc3d",
        "count": 3,
        "strategy": "bazi_pure",
        "w_bazi": 1.0,
        "w_freq": 0.0,
        "w_omission": 0.0,
    })
    assert rec_resp["status_code"] == 200
    recs = rec_resp["data"]["recommendations"]
    assert len(recs) == 3

    # Step 4: Validate each FC3D ticket
    for ticket in recs:
        val_resp = client.validate_ticket(
            lottery_type="fc3d",
            primary_numbers=ticket["primary_numbers"],
            secondary_numbers=ticket.get("secondary_numbers"),
        )
        assert val_resp["data"]["is_valid"] is True
        assert "|" not in val_resp["data"]["formatted_string"]  # Single zone

        # Oracle checks: exactly 3 digits, each in [0, 9]
        valid, _, norm_p, _, _ = ReferenceOracle.validate_ticket("fc3d", ticket["primary_numbers"])
        assert valid is True
        assert len(norm_p) == 3
        for d_str in norm_p:
            d = int(d_str)
            assert 0 <= d <= 9
