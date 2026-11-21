"""Tier 3 Test: Bazi Lucky Numbers × QXC Special Rules (Front Duplicate Permutations & 7th Ball 0-14).
Verifies interaction between Hetu digit mapping and QXC's unique dual-zone format.
"""
from tests_e2e.harness.oracle import ReferenceOracle
from tests_e2e.harness.client import get_test_client

def test_interaction_qxc_front_duplicate_digits_legality():
    """T3.1: QXC front 6 positions permit duplicate lucky digits, maintaining ticket validity."""
    client = get_test_client()

    # Front 6 digits containing repeating lucky digits (e.g. four 8s for Wood)
    ticket_req = {
        "primary_numbers": [8, 8, 3, 8, 8, 3],
        "secondary_numbers": [12],
    }
    res = client.validate_ticket("qxc", ticket_req["primary_numbers"], ticket_req["secondary_numbers"])
    assert res["data"]["is_valid"] is True
    assert res["data"]["formatted_string"] == "8,8,3,8,8,3|12"

def test_interaction_qxc_7th_ball_extended_hetu_mapping():
    """T3.2: 7th ball range [0, 14] maps cleanly to Hetu Five Elements across boundary numbers 10-14."""
    # 10 mod 10 = 0 -> Earth
    assert ReferenceOracle.get_hetu_element(10) == "earth"
    # 11 mod 10 = 1 -> Water
    assert ReferenceOracle.get_hetu_element(11) == "water"
    # 12 mod 10 = 2 -> Fire
    assert ReferenceOracle.get_hetu_element(12) == "fire"
    # 13 mod 10 = 3 -> Wood
    assert ReferenceOracle.get_hetu_element(13) == "wood"
    # 14 mod 10 = 4 -> Metal
    assert ReferenceOracle.get_hetu_element(14) == "metal"

    # All numbers in [0, 14] are accounted for
    partition = ReferenceOracle.partition_numbers_by_element(0, 14)
    assert len(partition["earth"]) == 3  # 0, 5, 10
    assert len(partition["water"]) == 3  # 1, 6, 11
    assert len(partition["fire"]) == 3   # 2, 7, 12
    assert len(partition["wood"]) == 3   # 3, 8, 13
    assert len(partition["metal"]) == 3  # 4, 9, 14

def test_interaction_qxc_recommendation_with_bazi_favored_digits():
    """T3.3: Recommending QXC tickets based on Bazi input produces valid format tickets."""
    client = get_test_client()
    req = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "qxc",
        "count": 3,
        "strategy": "balanced",
    }
    resp = client.post_recommend(req)
    assert resp["status_code"] == 200
    for ticket in resp["data"]["recommendations"]:
        valid, err, _, _, _ = ReferenceOracle.validate_ticket(
            "qxc", ticket["primary_numbers"], ticket["secondary_numbers"]
        )
        assert valid is True
        # Front has 6 digits, secondary has 1 digit
        assert len(ticket["primary_numbers"]) == 6
        assert len(ticket["secondary_numbers"]) == 1
        s_val = int(ticket["secondary_numbers"][0])
        assert 0 <= s_val <= 14

def test_interaction_qxc_all_same_digit_boundary():
    """T3.4: All six front digits identical (e.g. 6,6,6,6,6,6) is a valid permutation."""
    client = get_test_client()
    res = client.validate_ticket("qxc", [6, 6, 6, 6, 6, 6], [6])
    assert res["data"]["is_valid"] is True
    assert res["data"]["formatted_string"] == "6,6,6,6,6,6|06"
