"""Tier 4 Scenario 2: 1995 Wood Pig DLT Cold Reversal Strategy Lifecycle.
Flow: Destiny Analysis -> DLT Omission Query -> Cold Reversal Recommendation -> Ticket Validation.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_scenario_1995_pig_dlt():
    """Scenario 2: Complete end-to-end journey for 1995-10-24 user with DLT cold reversal strategy."""
    client = get_test_client()

    birth_dt = "1995-10-24T16:45:00"

    # Step 1: Bazi Destiny Analysis
    destiny = client.post_destiny({
        "birth_datetime": birth_dt,
        "calendar_type": "solar",
        "gender": "female",
    })
    assert destiny["status_code"] == 200
    bazi = destiny["data"]
    assert bazi["four_pillars"]["year"]["stem"] == "乙"
    assert bazi["four_pillars"]["year"]["branch"] == "亥"

    # Step 2: Query DLT Omission Analytics
    omission = client.get_omission("dlt")
    assert omission["status_code"] == 200

    # Step 3: Request DLT Cold Reversal Recommendation
    rec_resp = client.post_recommend({
        "birth_datetime": birth_dt,
        "lottery_type": "dlt",
        "count": 2,
        "strategy": "cold_reversal",
        "w_bazi": 0.3,
        "w_freq": 0.1,
        "w_omission": 0.6,
    })
    assert rec_resp["status_code"] == 200
    recs = rec_resp["data"]["recommendations"]
    assert len(recs) == 2

    # Step 4: Validate both tickets against official DLT rules
    for ticket in recs:
        val_resp = client.validate_ticket(
            lottery_type="dlt",
            primary_numbers=ticket["primary_numbers"],
            secondary_numbers=ticket["secondary_numbers"],
        )
        assert val_resp["data"]["is_valid"] is True

        # Oracle checks: 5 front (1-35, unique, sorted) + 2 back (1-12, unique, sorted)
        valid, _, norm_p, norm_s, fmt = ReferenceOracle.validate_ticket(
            "dlt", ticket["primary_numbers"], ticket["secondary_numbers"]
        )
        assert valid is True
        assert len(norm_p) == 5
        assert len(norm_s) == 2

        front_ints = [int(x) for x in norm_p]
        back_ints = [int(x) for x in norm_s]

        assert front_ints == sorted(front_ints)
        assert len(set(front_ints)) == 5
        assert all(1 <= f <= 35 for f in front_ints)

        assert back_ints == sorted(back_ints)
        assert len(set(back_ints)) == 2
        assert all(1 <= b <= 12 for b in back_ints)
