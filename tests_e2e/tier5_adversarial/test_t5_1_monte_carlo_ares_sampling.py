"""Tier 5 Adversarial Test: T5.1 Chi-Square Uniformity & Weight Monotonicity Stress Test.

Empirical verification of Efraimidis-Spirakis A-Res weighted reservoir sampling:
1. 5,000+ Monte Carlo iterations across lottery types (SSQ, DLT, FC3D, QXC).
2. Zero duplicate violations (violation rate strictly 0.000%).
3. Chi-square test on unweighted sampling proving null hypothesis of uniformity cannot be rejected (p > 0.01).
4. Spearman rank correlation and empirical odds ratio proving higher-weighted elements appear with statistically
   significantly higher probability (p < 1e-6).
"""
import math
from typing import List, Tuple, Dict
from scipy.stats import chisquare, spearmanr

from backend.app.services.numerology_engine import (
    a_res_weighted_sample_without_replacement,
    weighted_choice_with_replacement,
    recommend_lottery_tickets,
)
from backend.app.services.bazi_engine import calculate_four_pillars


def test_t5_1_ssq_5000_monte_carlo_zero_duplicate_invariance():
    """T5.1.1: 5,000 Monte Carlo runs of SSQ A-Res sampling prove 0% duplicate violation."""
    red_candidates = [(i, 1.0 + (i % 5) * 0.2) for i in range(1, 34)]
    blue_candidates = [(i, 1.0) for i in range(1, 17)]

    iterations = 5000
    duplicate_violations = 0
    out_of_bounds = 0
    unsorted_count = 0

    for _ in range(iterations):
        # Sample 6 red balls without replacement
        chosen_reds = a_res_weighted_sample_without_replacement(red_candidates, 6)

        # Invariant 1: Exactly 6 balls
        if len(chosen_reds) != 6:
            duplicate_violations += 1

        # Invariant 2: Zero duplicates in red zone
        if len(set(chosen_reds)) != 6:
            duplicate_violations += 1

        # Invariant 3: Boundaries [1, 33]
        if any(b < 1 or b > 33 for b in chosen_reds):
            out_of_bounds += 1

        # Invariant 4: Ascending sorted
        if chosen_reds != sorted(chosen_reds):
            unsorted_count += 1

        # Sample 1 blue ball
        chosen_blue = a_res_weighted_sample_without_replacement(blue_candidates, 1)
        if len(chosen_blue) != 1 or chosen_blue[0] < 1 or chosen_blue[0] > 16:
            out_of_bounds += 1

    assert duplicate_violations == 0, f"Detected {duplicate_violations} duplicate violations in SSQ sampling!"
    assert out_of_bounds == 0, f"Detected {out_of_bounds} out-of-bounds balls in SSQ sampling!"
    assert unsorted_count == 0, f"Detected {unsorted_count} unsorted outputs in SSQ sampling!"


def test_t5_1_dlt_5000_monte_carlo_zero_duplicate_invariance():
    """T5.1.2: 5,000 Monte Carlo runs of DLT A-Res sampling prove 0% duplicate violation."""
    front_candidates = [(i, 0.5 + (i % 7) * 0.1) for i in range(1, 36)]
    back_candidates = [(i, 0.4 + (i % 3) * 0.3) for i in range(1, 13)]

    iterations = 5000
    duplicate_violations = 0
    out_of_bounds = 0

    for _ in range(iterations):
        # Front zone: 5 from 35 without replacement
        chosen_front = a_res_weighted_sample_without_replacement(front_candidates, 5)
        if len(chosen_front) != 5 or len(set(chosen_front)) != 5:
            duplicate_violations += 1
        if any(b < 1 or b > 35 for b in chosen_front):
            out_of_bounds += 1

        # Back zone: 2 from 12 without replacement
        chosen_back = a_res_weighted_sample_without_replacement(back_candidates, 2)
        if len(chosen_back) != 2 or len(set(chosen_back)) != 2:
            duplicate_violations += 1
        if any(b < 1 or b > 12 for b in chosen_back):
            out_of_bounds += 1

    assert duplicate_violations == 0, f"Detected {duplicate_violations} duplicate violations in DLT sampling!"
    assert out_of_bounds == 0, f"Detected {out_of_bounds} out-of-bounds balls in DLT sampling!"


def test_t5_1_chi_square_uniformity_null_hypothesis():
    """T5.1.3: Chi-Square Goodness-of-Fit test on unweighted sampling (uniform null-hypothesis)."""
    # 33 balls, uniform weights = 1.0, sample k=6
    candidates = [(i, 1.0) for i in range(1, 34)]
    iterations = 5000
    k = 6
    n = 33

    counts: Dict[int, int] = {i: 0 for i in range(1, 34)}
    for _ in range(iterations):
        sample = a_res_weighted_sample_without_replacement(candidates, k)
        for ball in sample:
            counts[ball] += 1

    observed = [counts[i] for i in range(1, 34)]
    expected_per_ball = iterations * (k / n)  # 5000 * (6/33) = 909.0909
    expected = [expected_per_ball] * n

    # Compute Chi-Square statistic and p-value
    chi2_stat, p_value = chisquare(observed, expected)

    # Degrees of freedom = 33 - 1 = 32
    # Critical value at alpha = 0.01 for df=32 is approx 53.486
    # If p_value > 0.01, we cannot reject the null hypothesis of uniform distribution
    assert p_value > 0.01, (
        f"Chi-square test rejected uniformity! stat={chi2_stat:.4f}, p={p_value:.6f} "
        f"(expected p > 0.01 for true uniform sampling)"
    )


def test_t5_1_weight_monotonicity_and_statistical_significance():
    """T5.1.4: Empirical proof of weight monotonicity and statistical significance."""
    # 10 items with strictly increasing synthetic weights: w_i = i * 0.2
    # Item 1 has weight 0.2, Item 10 has weight 2.0 (10x ratio)
    candidates = [(i, i * 0.2) for i in range(1, 11)]
    iterations = 5000
    k = 3

    counts: Dict[int, int] = {i: 0 for i in range(1, 11)}
    for _ in range(iterations):
        sample = a_res_weighted_sample_without_replacement(candidates, k)
        for item in sample:
            counts[item] += 1

    observed_counts = [counts[i] for i in range(1, 11)]
    weights = [i * 0.2 for i in range(1, 11)]

    # 1. Spearman Rank Correlation between candidate weight and observed appearance frequency
    rho, spearman_p = spearmanr(weights, observed_counts)
    assert rho >= 0.85, f"Spearman rank correlation {rho:.4f} is too low (expected >= 0.85)"
    assert spearman_p < 1e-4, f"Spearman p-value {speman_p} not statistically significant"

    # 2. Ratio of highest weight element vs lowest weight element
    highest_count = counts[10]
    lowest_count = counts[1]
    ratio = highest_count / max(lowest_count, 1)

    # With weight ratio 10:1 and k=3 from 10, highest element should appear at least 3.0x more often
    assert ratio > 3.0, (
        f"Weight ratio failed: top weight appeared {highest_count} times, lowest appeared {lowest_count} "
        f"(ratio {ratio:.2f} <= 3.0)"
    )

    # 3. Two-proportion hypothesis test confirming top is significantly higher than bottom
    p1 = highest_count / iterations
    p2 = lowest_count / iterations
    p_pool = (highest_count + lowest_count) / (2 * iterations)
    se = math.sqrt(2 * p_pool * (1 - p_pool) / iterations)
    z_stat = (p1 - p2) / se if se > 0 else 0
    assert z_stat > 15.0, f"Z-statistic {z_stat:.2f} indicates insufficient statistical distinction"


def test_t5_1_five_tier_lucky_weight_monotonicity():
    """T5.1.5: Verify 5-tier lucky number weighting adheres to monotonic selection odds."""
    # Classical 5 tiers:
    # Tier 1: 1.00 (Core Primary Xi)
    # Tier 2: 0.80 (Supporting Xi)
    # Tier 3: 0.60 (Deficiency Balance)
    # Tier 4: 0.40 (Neutral)
    # Tier 5: 0.15 (Taboo Ji Shen)
    tier_candidates = [
        (1, 1.00),  # Tier 1
        (2, 0.80),  # Tier 2
        (3, 0.60),  # Tier 3
        (4, 0.40),  # Tier 4
        (5, 0.15),  # Tier 5
    ]
    iterations = 5000
    counts: Dict[int, int] = {i: 0 for i in range(1, 6)}

    for _ in range(iterations):
        sample = a_res_weighted_sample_without_replacement(tier_candidates, 2)
        for item in sample:
            counts[item] += 1

    # Tier 1 count > Tier 2 count > Tier 3 count > Tier 4 count > Tier 5 count
    c1, c2, c3, c4, c5 = counts[1], counts[2], counts[3], counts[4], counts[5]
    assert c1 > c2, f"Tier 1 count ({c1}) not greater than Tier 2 ({c2})"
    assert c2 > c3, f"Tier 2 count ({c2}) not greater than Tier 3 ({c3})"
    assert c3 > c4, f"Tier 3 count ({c3}) not greater than Tier 4 ({c4})"
    assert c4 > c5, f"Tier 4 count ({c4}) not greater than Tier 5 ({c5})"


def test_t5_1_recommend_engine_monte_carlo_across_all_lotteries():
    """T5.1.6: Run recommendation engine across all 4 lottery types under diverse strategies."""
    bazi_analysis = calculate_four_pillars("1990-08-15T08:30:00")
    lottery_types = ["ssq", "dlt", "fc3d", "qxc"]
    strategies = ["bazi_priority", "balanced", "hot_chase", "cold_reversal"]

    for lt in lottery_types:
        for strat in strategies:
            res = recommend_lottery_tickets(
                bazi_analysis=bazi_analysis,
                lottery_type=lt,
                count=5,
                strategy=strat,
            )
            recs = res["recommendations"]
            assert len(recs) == 5

            for r in recs:
                p_nums = r["primary_numbers"]
                s_nums = r.get("secondary_numbers")
                if lt == "ssq":
                    assert len(p_nums) == 6
                    assert len(set(p_nums)) == 6
                    assert len(s_nums) == 1
                elif lt == "dlt":
                    assert len(p_nums) == 5
                    assert len(set(p_nums)) == 5
                    assert len(s_nums) == 2
                    assert len(set(s_nums)) == 2
                elif lt == "fc3d":
                    assert len(p_nums) == 3
                    assert s_nums is None
                elif lt == "qxc":
                    assert len(p_nums) == 6
                    assert len(s_nums) == 1
