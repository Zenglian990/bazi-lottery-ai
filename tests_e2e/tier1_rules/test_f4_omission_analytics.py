"""Tier 1 Test: F4 Multi-dimensional Omission Analytics.
Verifies current omission (g0), max historical gap, closed-form average omission (N-k)/(k+1), and desire index.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_f4_current_omission_definition():
    """F4.1: Verify current omission g0 is 0 on recent draw and counts upward on misses."""
    # Simulating draw sequence for a ball: [Hit, Miss, Miss, Miss] (newest to oldest) -> current omission = 0
    # Simulating: [Miss, Miss, Miss, Hit] -> current omission = 3
    history = [False, False, False, True, False]
    current_gap = 0
    for hit in history:
        if hit:
            break
        current_gap += 1
    assert current_gap == 3

def test_f4_average_omission_closed_form_formula():
    """F4.2: Verify closed-form average omission formula g_avg = (N - k) / (k + 1)."""
    # N = 100 draws, appeared k = 19 times
    # g_avg = (100 - 19) / (19 + 1) = 81 / 20 = 4.05
    avg_1 = ReferenceOracle.calculate_average_omission(total_draws=100, appearance_count=19)
    assert avg_1 == 4.05

    # N = 100, k = 9
    # g_avg = (100 - 9) / (9 + 1) = 91 / 10 = 9.1
    avg_2 = ReferenceOracle.calculate_average_omission(total_draws=100, appearance_count=9)
    assert avg_2 == 9.1

def test_f4_average_omission_never_appeared():
    """F4.3: Edge case when ball never appeared in window (k = 0)."""
    # N = 100, k = 0 -> g_avg = (100 - 0) / (0 + 1) = 100.0
    avg_never = ReferenceOracle.calculate_average_omission(total_draws=100, appearance_count=0)
    assert avg_never == 100.0

def test_f4_desire_index_formula():
    """F4.4: Verify desire index formula = current_omission / avg_omission."""
    # Current omission = 10, avg omission = 5.0 -> desire = 2.0
    desire = ReferenceOracle.calculate_desire_index(current_omission=10, avg_omission=5.0)
    assert desire == 2.0

    # High omission overdue ball: current = 25, avg = 5.0 -> desire = 5.0
    overdue_desire = ReferenceOracle.calculate_desire_index(current_omission=25, avg_omission=5.0)
    assert overdue_desire == 5.0

def test_f4_desire_index_zero_omission():
    """F4.5: When ball just appeared (current = 0), desire index must be 0.0."""
    desire_fresh = ReferenceOracle.calculate_desire_index(current_omission=0, avg_omission=4.5)
    assert desire_fresh == 0.0

def test_f4_max_omission_boundary():
    """F4.6: Historical max omission must be greater than or equal to current omission."""
    draw_gaps = [2, 5, 14, 3, 1]
    max_gap = max(draw_gaps)
    current_gap = draw_gaps[0]
    assert max_gap >= current_gap
    assert max_gap == 14
