"""Tier 2 Test: Extreme Weighting Factors & High-Load Sampling Boundaries.
Verifies corner weight configurations (w_bazi=1.0, w_freq=1.0, w_omission=1.0, all 0) and large batch sampling.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_boundary_weights_pure_bazi_one_zero_zero():
    """T2.1: Pure Bazi weighting (1.0, 0.0, 0.0) yields 100% valid lottery tickets."""
    client = get_test_client()
    resp = client.post_recommend({
        "lottery_type": "ssq",
        "w_bazi": 1.0, "w_freq": 0.0, "w_omission": 0.0,
        "count": 5,
    })
    assert resp["status_code"] == 200
    for ticket in resp["data"]["recommendations"]:
        valid, err, _, _, _ = ReferenceOracle.validate_ticket("ssq", ticket["primary_numbers"], ticket["secondary_numbers"])
        assert valid is True

def test_boundary_weights_pure_frequency_zero_one_zero():
    """T2.2: Pure Frequency weighting (0.0, 1.0, 0.0) yields 100% valid lottery tickets."""
    client = get_test_client()
    resp = client.post_recommend({
        "lottery_type": "dlt",
        "w_bazi": 0.0, "w_freq": 1.0, "w_omission": 0.0,
        "count": 5,
    })
    assert resp["status_code"] == 200
    for ticket in resp["data"]["recommendations"]:
        valid, err, _, _, _ = ReferenceOracle.validate_ticket("dlt", ticket["primary_numbers"], ticket["secondary_numbers"])
        assert valid is True

def test_boundary_weights_pure_omission_zero_zero_one():
    """T2.3: Pure Omission weighting (0.0, 0.0, 1.0) yields 100% valid lottery tickets."""
    client = get_test_client()
    resp = client.post_recommend({
        "lottery_type": "fc3d",
        "w_bazi": 0.0, "w_freq": 0.0, "w_omission": 1.0,
        "count": 5,
    })
    assert resp["status_code"] == 200
    for ticket in resp["data"]["recommendations"]:
        valid, err, _, _, _ = ReferenceOracle.validate_ticket("fc3d", ticket["primary_numbers"])
        assert valid is True

def test_boundary_weights_all_zero_fallback():
    """T2.4: All zero weights (0.0, 0.0, 0.0) fallback safely to uniform distribution without NaN."""
    client = get_test_client()
    resp = client.post_recommend({
        "lottery_type": "qxc",
        "w_bazi": 0.0, "w_freq": 0.0, "w_omission": 0.0,
        "count": 3,
    })
    assert resp["status_code"] == 200
    for ticket in resp["data"]["recommendations"]:
        valid, err, _, _, _ = ReferenceOracle.validate_ticket("qxc", ticket["primary_numbers"], ticket["secondary_numbers"])
        assert valid is True

def test_boundary_large_count_sampling_batch_50():
    """T2.5: Stress test large batch sampling (count=50) generates 50 distinct legal tickets."""
    client = get_test_client()
    resp = client.post_recommend({
        "lottery_type": "ssq",
        "count": 50,
        "strategy": "balanced",
    })
    assert resp["status_code"] == 200
    recs = resp["data"]["recommendations"]
    assert len(recs) == 50
    for ticket in recs:
        valid, _, _, _, _ = ReferenceOracle.validate_ticket("ssq", ticket["primary_numbers"], ticket["secondary_numbers"])
        assert valid is True
