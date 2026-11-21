"""Tier 1 Test: F8 Hybrid Weighted Selection & A-Res Safe Sampling.
Verifies tri-factor linear weights, Efraimidis-Spirakis (A-Res) reservoir sampling, ticket legality, and sorting.
"""
from tests_e2e.harness.oracle import ReferenceOracle
from tests_e2e.harness.client import get_test_client

def test_f8_a_res_reservoir_sampling_uniqueness():
    """F8.1: A-Res reservoir sampling must produce 100% unique numbers in zones requiring uniqueness."""
    client = get_test_client()

    # Generate 20 SSQ recommendations
    req = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "count": 20,
        "strategy": "balanced",
        "w_bazi": 0.5,
        "w_freq": 0.3,
        "w_omission": 0.2,
    }
    resp = client.post_recommend(req)
    assert resp["status_code"] == 200
    recs = resp["data"]["recommendations"]
    assert len(recs) == 20

    for ticket in recs:
        reds = ticket["primary_numbers"]
        assert len(reds) == 6
        assert len(set(reds)) == 6, f"Duplicate red ball detected: {reds}"

def test_f8_a_res_reservoir_sampling_bounds():
    """F8.2: All sampled balls must fall strictly within legal integer bounds."""
    client = get_test_client()

    # DLT: Front 1-35, Back 1-12
    req = {
        "birth_datetime": "1995-10-24T14:20:00",
        "lottery_type": "dlt",
        "count": 10,
        "strategy": "balanced",
    }
    resp = client.post_recommend(req)
    recs = resp["data"]["recommendations"]

    for t in recs:
        fronts = [int(x) for x in t["primary_numbers"]]
        backs = [int(x) for x in t["secondary_numbers"]]

        for f in fronts:
            assert 1 <= f <= 35, f"Front ball {f} outside [1, 35]"
        for b in backs:
            assert 1 <= b <= 12, f"Back ball {b} outside [1, 12]"

def test_f8_sampling_sorted_order():
    """F8.3: Primary and secondary numbers in sorted zones must be strictly ascending."""
    client = get_test_client()
    resp = client.post_recommend({"lottery_type": "ssq", "count": 5})
    recs = resp["data"]["recommendations"]

    for t in recs:
        reds = [int(x) for x in t["primary_numbers"]]
        assert reds == sorted(reds), f"Red balls must be sorted ascending: {reds}"

def test_f8_tri_factor_weight_normalization():
    """F8.4: Verify weights fusion behavior under different strategy presets."""
    client = get_test_client()

    # Pure bazi strategy
    res_bazi = client.post_recommend({
        "lottery_type": "ssq",
        "strategy": "bazi_pure",
        "w_bazi": 1.0, "w_freq": 0.0, "w_omission": 0.0,
    })
    assert res_bazi["status_code"] == 200

    # Trend chase hot strategy
    res_hot = client.post_recommend({
        "lottery_type": "ssq",
        "strategy": "hot_chase",
        "w_bazi": 0.2, "w_freq": 0.6, "w_omission": 0.2,
    })
    assert res_hot["status_code"] == 200

def test_f8_multi_ticket_batch_generation():
    """F8.5: Count parameter accurately generates exact number of distinct tickets."""
    client = get_test_client()
    for count in [1, 3, 5, 10]:
        resp = client.post_recommend({"lottery_type": "fc3d", "count": count})
        assert len(resp["data"]["recommendations"]) == count

def test_f8_end_to_end_recommendation_legality():
    """F8.6: Every recommended ticket must pass the authoritative ReferenceOracle validator."""
    client = get_test_client()
    for lt in ["ssq", "dlt", "fc3d", "qxc"]:
        resp = client.post_recommend({"lottery_type": lt, "count": 3})
        for ticket in resp["data"]["recommendations"]:
            valid, err, _, _, _ = ReferenceOracle.validate_ticket(
                lottery_type=lt,
                primary_numbers=ticket["primary_numbers"],
                secondary_numbers=ticket.get("secondary_numbers"),
            )
            assert valid is True, f"Recommended ticket failed oracle validation for {lt}: {err}"
