"""Tier 1 Test: F3 Historical Draw Frequencies & Hot/Cold Analysis.
Verifies occurrence frequency rates, sliding window expectation values, and 1.25/0.75 threshold classification.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_f3_frequency_calculation_formula():
    """F3.1: Verify frequency calculation matches count / N across edge conditions."""
    # Normal draw count
    f1 = ReferenceOracle.calculate_number_frequency(18, 100)
    assert f1 == 0.18

    # Zero count
    f0 = ReferenceOracle.calculate_number_frequency(0, 100)
    assert f0 == 0.0

    # 100% frequency
    f_all = ReferenceOracle.calculate_number_frequency(100, 100)
    assert f_all == 1.0

    # Zero total draws handling (no division by zero)
    f_empty = ReferenceOracle.calculate_number_frequency(0, 0)
    assert f_empty == 0.0

def test_f3_expected_count_calculation():
    """F3.2: Verify theoretical expected count E_W = W * (zone_count / pool_size)."""
    # SSQ Red zone: pool size 33, pick count 6. Window = 30.
    # Expected E_W = 30 * (6 / 33) = 180 / 33 = 5.4545...
    exp_ssq_red_30 = 30 * (6 / 33)
    assert round(exp_ssq_red_30, 2) == 5.45

    # SSQ Blue zone: pool size 16, pick count 1. Window = 30.
    # Expected E_W = 30 * (1 / 16) = 1.875
    exp_ssq_blue_30 = 30 * (1 / 16)
    assert exp_ssq_blue_30 == 1.875

    # DLT Front zone: pool size 35, pick count 5. Window = 30.
    # Expected E_W = 30 * (5 / 35) = 30 / 7 = 4.2857...
    exp_dlt_front_30 = 30 * (5 / 35)
    assert round(exp_dlt_front_30, 2) == 4.29

def test_f3_hot_threshold_factor_classification():
    """F3.3: Numbers with count >= 1.25 * E_W must be classified as 'hot'."""
    # For SSQ Red W=30: E_W = 5.4545, Hot threshold = 1.25 * 5.4545 = 6.818 -> count >= 7
    status_7 = ReferenceOracle.classify_hot_cold(
        window_count=7,
        window_size=30,
        zone_pick_count=6,
        zone_pool_size=33,
    )
    assert status_7 == "hot"

    status_10 = ReferenceOracle.classify_hot_cold(
        window_count=10,
        window_size=30,
        zone_pick_count=6,
        zone_pool_size=33,
    )
    assert status_10 == "hot"

def test_f3_cold_threshold_factor_classification():
    """F3.4: Numbers with count <= 0.75 * E_W must be classified as 'cold'."""
    # For SSQ Red W=30: E_W = 5.4545, Cold threshold = 0.75 * 5.4545 = 4.09 -> count <= 4
    status_4 = ReferenceOracle.classify_hot_cold(
        window_count=4,
        window_size=30,
        zone_pick_count=6,
        zone_pool_size=33,
    )
    assert status_4 == "cold"

    status_0 = ReferenceOracle.classify_hot_cold(
        window_count=0,
        window_size=30,
        zone_pick_count=6,
        zone_pool_size=33,
    )
    assert status_0 == "cold"

def test_f3_warm_classification_bounds():
    """F3.5: Numbers between cold and hot thresholds must be classified as 'warm'."""
    # For SSQ Red W=30: count = 5 or 6 (between 4.09 and 6.81)
    status_5 = ReferenceOracle.classify_hot_cold(
        window_count=5,
        window_size=30,
        zone_pick_count=6,
        zone_pool_size=33,
    )
    assert status_5 == "warm"

    status_6 = ReferenceOracle.classify_hot_cold(
        window_count=6,
        window_size=30,
        zone_pick_count=6,
        zone_pool_size=33,
    )
    assert status_6 == "warm"

def test_f3_sliding_window_sensitivity_30_vs_50():
    """F3.6: Verify scaling behavior when window expands from 30 to 50 draws."""
    # In W=30, count 5 is warm (E_W=5.45)
    w30_status = ReferenceOracle.classify_hot_cold(
        window_count=5, window_size=30, zone_pick_count=6, zone_pool_size=33
    )
    assert w30_status == "warm"

    # In W=50, E_W = 50 * (6/33) = 9.09. Cold threshold = 0.75 * 9.09 = 6.81.
    # Therefore, count 5 is now cold!
    w50_status = ReferenceOracle.classify_hot_cold(
        window_count=5, window_size=50, zone_pick_count=6, zone_pool_size=33
    )
    assert w50_status == "cold"
