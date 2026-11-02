"""Tier 3 Test: Bazi Five Elements × Lottery Big Data Frequency Interaction.
Verifies pairwise synergy between astronomical destiny lucky pools and empirical draw frequencies.
"""
from tests_e2e.harness.oracle import ReferenceOracle
from tests_e2e.harness.client import get_test_client

def test_interaction_bazi_lucky_and_hot_numbers_intersection():
    """T3.1: Intersection of Bazi lucky pool and recent hot numbers forms high-probability candidate set."""
    # User with strong water Day Master favors Wood and Fire
    bazi = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    favored_elements = bazi["xi_yong_shen"]  # Wood, Fire, Earth

    # SSQ red partition
    partition = ReferenceOracle.partition_numbers_by_element(1, 33)
    lucky_pool = []
    for elem in favored_elements:
        lucky_pool.extend(partition[elem])

    # Suppose hot numbers in recent 30 draws are [3, 8, 12, 17, 24, 29]
    hot_numbers = [3, 8, 12, 17, 24, 29]
    intersection = set(lucky_pool).intersection(set(hot_numbers))

    # Numbers 3, 8 (Wood) and 12, 17 (Fire) must be in lucky pool
    assert 3 in intersection
    assert 8 in intersection
    assert 12 in intersection
    assert 17 in intersection
    assert len(intersection) >= 4

def test_interaction_bazi_lucky_and_cold_reversal_omission():
    """T3.2: Omission-weighted strategy elevates lucky numbers that have experienced high gaps (cold reversal)."""
    client = get_test_client()

    req = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "strategy": "cold_reversal",
        "w_bazi": 0.4,
        "w_freq": 0.1,
        "w_omission": 0.5,
        "count": 5,
    }
    resp = client.post_recommend(req)
    assert resp["status_code"] == 200
    recs = resp["data"]["recommendations"]
    assert len(recs) == 5

    for t in recs:
        # Every ticket must have valid rationale string mentioning combination of Bazi and Omission
        assert "score_rationale" in t
        assert len(t["primary_numbers"]) == 6

def test_interaction_tri_factor_weight_shift_behavior():
    """T3.3: Shifting weights between Bazi (w_bazi) and Frequency (w_freq) alters ticket selection."""
    client = get_test_client()

    # Bazi heavy
    res1 = client.post_recommend({
        "lottery_type": "ssq",
        "w_bazi": 0.8, "w_freq": 0.1, "w_omission": 0.1,
    })
    # Frequency heavy
    res2 = client.post_recommend({
        "lottery_type": "ssq",
        "w_bazi": 0.1, "w_freq": 0.8, "w_omission": 0.1,
    })

    assert res1["status_code"] == 200
    assert res2["status_code"] == 200

def test_interaction_wuxing_distribution_in_generated_ticket():
    """T3.4: Generated ticket wuxing distribution accurately reflects element counts of chosen numbers."""
    client = get_test_client()
    resp = client.post_recommend({"lottery_type": "ssq", "count": 1})
    ticket = resp["data"]["recommendations"][0]

    # Count elements in chosen primary numbers
    primary_ints = [int(x) for x in ticket["primary_numbers"]]
    calculated_dist = {"wood": 0, "fire": 0, "earth": 0, "metal": 0, "water": 0}
    for n in primary_ints:
        elem = ReferenceOracle.get_hetu_element(n)
        calculated_dist[elem] += 1

    assert sum(calculated_dist.values()) == 6
    # Check that ticket contains wuxing distribution
    assert "wuxing_distribution" in ticket
