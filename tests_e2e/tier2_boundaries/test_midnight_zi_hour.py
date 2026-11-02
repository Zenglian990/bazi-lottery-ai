"""Tier 2 Test: Midnight Zi Hour (23:00 / 00:00) Edge Cases & True Solar Time.
Verifies Hai-to-Zi hour shift, midnight day rollover, Sect 1 vs Sect 2, and longitude solar time shifts.
"""
from tests_e2e.harness.oracle import ReferenceOracle

def test_boundary_2259_to_2300_hai_to_zi_transition():
    """T2.1: Exactly at 22:59:59 vs 23:00:00, hour branch transitions from Hai (亥) to Zi (子)."""
    b_hai = ReferenceOracle.compute_bazi(2026, 11, 2, 22, 59, 59)
    b_zi = ReferenceOracle.compute_bazi(2026, 11, 2, 23, 0, 0)

    assert b_hai["pillars"]["hour"]["branch"] == "亥"
    assert b_zi["pillars"]["hour"]["branch"] == "子"

def test_boundary_2359_to_0000_midnight_day_rollover():
    """T2.2: Midnight rollover from 23:59:59 to 00:00:00 transitions day pillar under Sect 2."""
    b_night = ReferenceOracle.compute_bazi(2026, 11, 2, 23, 59, 59, sect=2)
    b_morning = ReferenceOracle.compute_bazi(2026, 11, 3, 0, 0, 0, sect=2)

    # In Sect 2: 23:59 on Nov 2 is Geng Chen day; 00:00 on Nov 3 is Xin Si day
    assert b_night["pillars"]["day"]["pair"] == "庚辰"
    assert b_morning["pillars"]["day"]["pair"] == "辛巳"
    # Both are Zi hours
    assert b_night["pillars"]["hour"]["branch"] == "子"
    assert b_morning["pillars"]["hour"]["branch"] == "子"

def test_boundary_0059_to_0100_zi_to_chou_transition():
    """T2.3: Exactly at 00:59:59 vs 01:00:00, hour branch transitions from Zi (子) to Chou (丑)."""
    b_zi = ReferenceOracle.compute_bazi(2026, 11, 3, 0, 59, 59)
    b_chou = ReferenceOracle.compute_bazi(2026, 11, 3, 1, 0, 0)

    assert b_zi["pillars"]["hour"]["branch"] == "子"
    assert b_chou["pillars"]["hour"]["branch"] == "丑"

def test_boundary_sect1_vs_sect2_day_pillar_divergence():
    """T2.4: During 23:00 - 23:59:59, Sect 1 adopts next day's pillar, Sect 2 maintains current day."""
    # Date: 2026-11-02 23:45
    s1 = ReferenceOracle.compute_bazi(2026, 11, 2, 23, 45, 0, sect=1)
    s2 = ReferenceOracle.compute_bazi(2026, 11, 2, 23, 45, 0, sect=2)

    assert s1["pillars"]["day"]["pair"] != s2["pillars"]["day"]["pair"]
    # Sect 1 has already transitioned to Xin Si
    assert s1["pillars"]["day"]["pair"] == "辛巳"
    # Sect 2 is still Geng Chen
    assert s2["pillars"]["day"]["pair"] == "庚辰"

def test_boundary_longitude_solar_time_shift_across_zi_hour():
    """T2.5: Longitude time correction shifts the astronomical hour branch across 23:00 boundary."""
    # Clock time is 23:15 CST (UTC+8, longitude 120.0).
    # If user is in Urumqi (longitude 87.6), time delta = (87.6 - 120.0) * 4 minutes = -129.6 minutes (~ -2h 9m).
    # True solar time is approximately 21:05 (Hai hour / 亥时 or Xu hour / 戌时).
    import datetime
    clock_dt = datetime.datetime(2026, 11, 2, 23, 15, 0)

    # Local clock time computation -> Zi hour
    b_clock = ReferenceOracle.compute_bazi(clock_dt.year, clock_dt.month, clock_dt.day, clock_dt.hour, clock_dt.minute)
    assert b_clock["pillars"]["hour"]["branch"] == "子"

    # Adjust for Urumqi true solar time: -129 minutes -> 21:06
    solar_dt = clock_dt - datetime.timedelta(minutes=129)
    b_solar = ReferenceOracle.compute_bazi(solar_dt.year, solar_dt.month, solar_dt.day, solar_dt.hour, solar_dt.minute)
    assert b_solar["pillars"]["hour"]["branch"] == "亥"
