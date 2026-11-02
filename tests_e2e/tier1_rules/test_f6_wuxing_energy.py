"""Tier 1 Test: F6 Hidden Stems Energy Weighting & Wuxing Quantification.
Verifies the 110-point positional model, branch hidden stem ratios, Day Master strength, and Xi Yong Shen logic.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_f6_110_point_weight_sum():
    """F6.1: Positional weights across the 8 positions must strictly sum to 110 points."""
    weights = ReferenceOracle.PILLAR_WEIGHTS
    total = sum(weights.values())
    assert total == 110.0
    assert weights["month_branch"] == 35.0  # Month branch holds dominant energy

def test_f6_hidden_stem_decomposition_ratios():
    """F6.2: Hidden stems across all 12 Earthly Branches must sum to exactly 1.00 ratio."""
    for branch, hidden in ReferenceOracle.BRANCH_HIDDEN_STEMS.items():
        total_ratio = sum(r for _, r in hidden)
        assert round(total_ratio, 2) == 1.00, f"Branch {branch} hidden stems ratio must sum to 1.0, got {total_ratio}"

def test_f6_wuxing_score_distribution_and_percentages():
    """F6.3: Five elements scores must sum to 110.0 and percentages must normalize to 1.0."""
    res = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    scores = res["scores"]
    percentages = res["percentages"]

    # Sum of scores == 110.0
    assert round(sum(scores.values()), 1) == 110.0

    # Percentages sum to 1.0 (+- rounding delta 0.01)
    assert 0.99 <= sum(percentages.values()) <= 1.01

    # Check all 5 elements exist in map
    for elem in ["wood", "fire", "earth", "metal", "water"]:
        assert elem in scores
        assert elem in percentages

def test_f6_day_master_strength_classification():
    """F6.4: Self-party score >= 55 points qualifies as '身强', otherwise '身弱'."""
    # Benchmark chart 1990-08-15 08:30:
    # Day master is Ren (Water). Mother element is Metal.
    res = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    scores = res["scores"]
    self_party = scores["water"] + scores["metal"]
    expected_strength = "身强" if self_party >= 55.0 else "身弱"
    assert res["strength"] == expected_strength

def test_f6_xi_yong_shen_selection_logic():
    """F6.5: Verify Xi Yong Shen and Ji Shen complement Day Master strength."""
    res = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    xi_yong = res["xi_yong_shen"]
    ji_shen = res["ji_shen"]

    assert isinstance(xi_yong, list)
    assert len(xi_yong) > 0
    assert isinstance(ji_shen, list)
    assert len(ji_shen) > 0

    # Strong charts favor draining elements (wood, fire, earth for water day master)
    if res["strength"] == "身强":
        assert "wood" in xi_yong or "fire" in xi_yong or "earth" in xi_yong
    else:
        assert "water" in xi_yong or "metal" in xi_yong

def test_f6_lacking_elements_detection():
    """F6.6: Verify identification of lacking elements with score < 5.0."""
    # Chart for a date with known missing or very low element
    res = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    scores = res["scores"]
    lacking = res["lacking_elements"]

    for elem in lacking:
        assert scores[elem] < 5.0
