"""Tier 1 Test: F5 Precision Solar-to-Ganzhi & Four Pillars Determination.
Verifies Li Chun year cutoff, 12 Jie month terms, Julian day mod 60, Five Rats hour rule, and Sect 1/2 Zi hours.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_f5_solar_to_lunar_and_four_pillars_known_sample():
    """F5.1: Verify verified astronomical birth date: 1990-08-15 08:30:00."""
    res = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    pillars = res["pillars"]
    assert pillars["year"]["pair"] == "庚午"
    assert pillars["month"]["pair"] == "甲申"
    assert pillars["day"]["pair"] == "壬子"
    assert pillars["hour"]["pair"] == "甲辰"
    assert res["day_master"] == "壬"
    assert res["day_master_element"] == "water"

def test_f5_lichun_year_transition():
    """F5.2: Year stem-branch strictly shifts at Li Chun (2026-02-04 04:01:51), not Jan 1 or Lunar Jan 1."""
    # Before Li Chun (2026-02-03 12:00) -> Still Yi Si (2025 year of snake)
    bazi_before = ReferenceOracle.compute_bazi(2026, 2, 3, 12, 0, 0)
    assert bazi_before["pillars"]["year"]["pair"] == "乙巳"

    # After Li Chun (2026-02-04 12:00) -> Bing Wu (2026 year of horse)
    bazi_after = ReferenceOracle.compute_bazi(2026, 2, 4, 12, 0, 0)
    assert bazi_after["pillars"]["year"]["pair"] == "丙午"

def test_f5_twelve_jie_month_transition():
    """F5.3: Month branch shifts at the 12 Jie solar terms, not calendar month boundaries."""
    # 2026-10-01 is still in You month (between Bai Lu and Han Lu)
    # Han Lu in 2026 occurs on 2026-10-08 09:40:00
    bazi_early_oct = ReferenceOracle.compute_bazi(2026, 10, 5, 12, 0, 0)
    assert bazi_early_oct["pillars"]["month"]["branch"] == "酉"

    # After Han Lu (2026-10-09), month shifts to Xu
    bazi_mid_oct = ReferenceOracle.compute_bazi(2026, 10, 9, 12, 0, 0)
    assert bazi_mid_oct["pillars"]["month"]["branch"] == "戌"

def test_f5_jdn_day_ganzhi_congruence():
    """F5.4: Day Ganzhi cycle is strictly periodic modulo 60 without leap interruptions."""
    # Consecutive days must advance Ganzhi index by exactly 1 mod 60
    stems = ["甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"]
    branches = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"]
    cycle_60 = [f"{stems[i % 10]}{branches[i % 12]}" for i in range(60)]

    bazi_day1 = ReferenceOracle.compute_bazi(2026, 11, 2, 12, 0, 0)
    bazi_day2 = ReferenceOracle.compute_bazi(2026, 11, 3, 12, 0, 0)

    p1 = bazi_day1["pillars"]["day"]["pair"]
    p2 = bazi_day2["pillars"]["day"]["pair"]

    idx1 = cycle_60.index(p1)
    idx2 = cycle_60.index(p2)
    assert (idx1 + 1) % 60 == idx2

def test_f5_five_rats_seek_hour_stem():
    """F5.5: Hour stem follows the Five Rats formula: (DayStem * 2 + BranchIdx) % 10."""
    # For a Ren (Water, index 8) day:
    # Stem of Rat (Zi, index 0) hour = (8 * 2 + 0) % 10 = 16 % 10 = 6 -> Geng (庚)
    # Stem of Chen (Dragon, index 4) hour = (8 * 2 + 4) % 10 = 20 % 10 = 0 -> Jia (甲)
    bazi_chen = ReferenceOracle.compute_bazi(1990, 8, 15, 8, 30, 0)
    assert bazi_chen["pillars"]["day"]["stem"] == "壬"
    assert bazi_chen["pillars"]["hour"]["stem"] == "甲"
    assert bazi_chen["pillars"]["hour"]["branch"] == "辰"

def test_f5_sect1_vs_sect2_zi_hour():
    """F5.6: Test Late Zi hour (23:00-24:00) behavior under Sect 1 vs Sect 2."""
    # At 2026-11-02 23:30:
    # Sect 1 switches day pillar to next day at 23:00
    # Sect 2 keeps day pillar until 00:00 midnight but sets hour stem to next day's Zi hour
    b1 = ReferenceOracle.compute_bazi(2026, 11, 2, 23, 30, 0, sect=1)
    b2 = ReferenceOracle.compute_bazi(2026, 11, 2, 23, 30, 0, sect=2)

    assert b1["pillars"]["hour"]["branch"] == "子"
    assert b2["pillars"]["hour"]["branch"] == "子"
    # Sect 1 shifts day pillar to next day (Xin Si)
    assert b1["pillars"]["day"]["pair"] == "辛巳"
    # Sect 2 keeps current day pillar (Geng Chen)
    assert b2["pillars"]["day"]["pair"] == "庚辰"
