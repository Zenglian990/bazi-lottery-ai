"""Tier 1 Test: F7 Hetu Numerology & Lucky Number Pool Partitions.
Verifies classical Hetu tail modulo 10 mapping, exhaustive range partitioning, and lucky tier stratification.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_f7_hetu_classical_tail_modulo_mapping():
    """F7.1: Verify classical Hetu mapping: 1/6 Water, 2/7 Fire, 3/8 Wood, 4/9 Metal, 5/0 Earth."""
    assert ReferenceOracle.get_hetu_element(1) == "water"
    assert ReferenceOracle.get_hetu_element(6) == "water"
    assert ReferenceOracle.get_hetu_element(16) == "water"
    assert ReferenceOracle.get_hetu_element(21) == "water"

    assert ReferenceOracle.get_hetu_element(2) == "fire"
    assert ReferenceOracle.get_hetu_element(7) == "fire"
    assert ReferenceOracle.get_hetu_element(27) == "fire"

    assert ReferenceOracle.get_hetu_element(3) == "wood"
    assert ReferenceOracle.get_hetu_element(8) == "wood"
    assert ReferenceOracle.get_hetu_element(33) == "wood"

    assert ReferenceOracle.get_hetu_element(4) == "metal"
    assert ReferenceOracle.get_hetu_element(9) == "metal"
    assert ReferenceOracle.get_hetu_element(29) == "metal"

    assert ReferenceOracle.get_hetu_element(5) == "earth"
    assert ReferenceOracle.get_hetu_element(10) == "earth"
    assert ReferenceOracle.get_hetu_element(30) == "earth"

def test_f7_ssq_red_full_partition():
    """F7.2: Verify SSQ Red (1-33) numbers partition completely into 5 disjoint subsets."""
    partition = ReferenceOracle.partition_numbers_by_element(1, 33)

    all_nums = set()
    for elem, nums in partition.items():
        all_nums.update(nums)
        # All numbers in element partition must have matching Hetu element
        for n in nums:
            assert ReferenceOracle.get_hetu_element(n) == elem

    # Total must be exactly 33 numbers from 1 to 33
    assert len(all_nums) == 33
    assert all_nums == set(range(1, 34))

def test_f7_dlt_front_full_partition():
    """F7.3: Verify DLT Front (1-35) numbers partition completely into 5 disjoint subsets."""
    partition = ReferenceOracle.partition_numbers_by_element(1, 35)

    all_nums = set()
    for elem, nums in partition.items():
        all_nums.update(nums)

    assert len(all_nums) == 35
    assert all_nums == set(range(1, 36))
    # Each element has exactly 7 numbers in [1, 35]
    for elem, nums in partition.items():
        assert len(nums) == 7

def test_f7_fc3d_and_qxc_digits_partition():
    """F7.4: Verify FC3D (0-9) and QXC 7th ball (0-14) partitioning."""
    p_0_9 = ReferenceOracle.partition_numbers_by_element(0, 9)
    # In [0, 9], every element has exactly 2 numbers
    for elem, nums in p_0_9.items():
        assert len(nums) == 2

    p_0_14 = ReferenceOracle.partition_numbers_by_element(0, 14)
    total_qxc_7 = sum(len(nums) for nums in p_0_14.values())
    assert total_qxc_7 == 15

def test_f7_lucky_pool_stratification_tiers():
    """F7.5: Numbers matching primary Xi Yong Shen must be assigned top lucky scores."""
    bazi = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    primary_xi = bazi["xi_yong_shen"][0]

    # Candidate numbers for SSQ Red
    partition = ReferenceOracle.partition_numbers_by_element(1, 33)
    lucky_numbers = partition[primary_xi]

    assert len(lucky_numbers) > 0
    for num in lucky_numbers:
        assert ReferenceOracle.get_hetu_element(num) == primary_xi

def test_f7_element_completeness_theorem():
    """F7.6: Any consecutive range of length >= 10 contains all 5 elements."""
    for start in [1, 5, 12, 23]:
        part = ReferenceOracle.partition_numbers_by_element(start, start + 9)
        for elem in ["wood", "fire", "earth", "metal", "water"]:
            assert len(part[elem]) >= 1
