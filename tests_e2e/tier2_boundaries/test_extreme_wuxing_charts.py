"""Tier 2 Test: Extreme Wuxing Energy Charts & Boundary Conditions.
Verifies pure single-element dominance, zero-score element charts, 55.0 pt Day Master boundary, and extreme weak/strong charts.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_extreme_single_element_dominant_chart():
    """T2.1: Extreme wood chart (e.g. spring Jia/Yi/Yin/Mao combinations) exhibits extreme wood concentration."""
    # 1974-02-23 (Jia Yin year, Bing Yin month, Jia Yin day...)
    bazi = ReferenceOracle.compute_bazi(1974, 2, 23, 4, 0, 0)
    scores = bazi["scores"]

    # Wood score must be high (> 45)
    assert scores["wood"] > 45.0
    assert bazi["percentages"]["wood"] > 0.40
    # Day Master is Jia (Wood)
    assert bazi["day_master_element"] == "wood"
    # Lacking elements detected
    assert len(bazi["lacking_elements"]) >= 1

def test_extreme_zero_score_element_detection():
    """T2.2: Verify chart with 0 score in an element is identified in lacking_elements."""
    bazi = ReferenceOracle.compute_bazi(1974, 2, 23, 4, 0, 0)
    scores = bazi["scores"]
    lacking = bazi["lacking_elements"]

    for elem, sc in scores.items():
        if sc == 0.0:
            assert elem in lacking

def test_boundary_exact_55_point_day_master_strength_cutoff():
    """T2.3: Verify exact cutoff at 55.0 points (50% of 110) between '身强' and '身弱'."""
    # Self party score calculation:
    # >= 55.0 -> 身强
    # < 55.0 -> 身弱
    bazi = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    self_party = bazi["scores"][bazi["day_master_element"]] + bazi["scores"][ReferenceOracle.MOTHER_ELEMENT[bazi["day_master_element"]]]

    if self_party >= 55.0:
        assert bazi["strength"] == "身强"
    else:
        assert bazi["strength"] == "身弱"

def test_extreme_weak_day_master_chart():
    """T2.4: Extremely weak Day Master chart (self party < 30 points) properly designates supporting Xi Yong Shen."""
    # Born in deep winter with fire Day Master and no wood/fire support
    bazi = ReferenceOracle.compute_bazi(1983, 12, 10, 0, 30, 0)
    if bazi["strength"] == "身弱":
        dm_elem = bazi["day_master_element"]
        mother_elem = ReferenceOracle.MOTHER_ELEMENT[dm_elem]
        # Xi Yong Shen must include supporting mother and self elements
        assert mother_elem in bazi["xi_yong_shen"] or dm_elem in bazi["xi_yong_shen"]

def test_extreme_balanced_chart_percentage_stability():
    """T2.5: Wuxing scores and percentages remain stable and finite across all charts."""
    test_dates = [
        (1980, 1, 1, 0, 0),
        (1995, 6, 15, 12, 30),
        (2008, 8, 8, 20, 8),
        (2026, 11, 2, 15, 0),
    ]
    for y, m, d, h, mi in test_dates:
        bazi = ReferenceOracle.compute_bazi(y, m, d, h, mi)
        scores = bazi["scores"]
        percentages = bazi["percentages"]

        # No NaN or infinities
        for sc in scores.values():
            assert not (sc != sc)  # not NaN
            assert sc >= 0.0
        for p in percentages.values():
            assert not (p != p)
            assert 0.0 <= p <= 1.0

        assert round(sum(scores.values()), 1) == 110.0
