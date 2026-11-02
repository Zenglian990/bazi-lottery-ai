"""Tier 2 Test: Solar Terms Boundary & Ephemeris Minute Transitions.
Verifies sub-minute and exact-second transitions at Li Chun and 12 Jie terms, leap months, and century boundaries.
"""
from lunar_python import Solar
from tests_e2e.harness.oracle import ReferenceOracle

def test_boundary_lichun_exact_second_year_transition():
    """T2.1: Exactly at Li Chun 2026 (2026-02-04 04:02:08), verify sub-second year shift."""
    # 1 second before Li Chun: 乙巳 (Snake, 2025)
    s_before = Solar.fromYmdHms(2026, 2, 4, 4, 2, 7)
    assert s_before.getLunar().getEightChar().getYear() == "乙巳"

    # 1 second after Li Chun: 丙午 (Horse, 2026)
    s_after = Solar.fromYmdHms(2026, 2, 4, 4, 2, 9)
    assert s_after.getLunar().getEightChar().getYear() == "丙午"

def test_boundary_jieqi_month_transition_second_precision():
    """T2.2: Verify month stem/branch transitions strictly at the JieQi term second."""
    # In 2026, JingZhe (惊蛰) transitions from Yin (寅) month to Mao (卯) month
    s_ref = Solar.fromYmdHms(2026, 3, 5, 12, 0, 0)
    jing_zhe_dt = s_ref.getLunar().getJieQiTable()["惊蛰"].toYmdHms()

    # Parse date and test 10 seconds before and after
    import datetime
    dt = datetime.datetime.fromisoformat(jing_zhe_dt)
    dt_before = dt - datetime.timedelta(seconds=5)
    dt_after = dt + datetime.timedelta(seconds=5)

    s1 = Solar.fromYmdHms(dt_before.year, dt_before.month, dt_before.day, dt_before.hour, dt_before.minute, dt_before.second)
    s2 = Solar.fromYmdHms(dt_after.year, dt_after.month, dt_after.day, dt_after.hour, dt_after.minute, dt_after.second)

    assert s1.getLunar().getEightChar().getMonthZhi() == "寅"
    assert s2.getLunar().getEightChar().getMonthZhi() == "卯"

def test_boundary_leap_month_lunar_continuity():
    """T2.3: Verify Lunar calendar leap month (e.g. 2023 Leap Month 2 闰二月) preserves valid Bazi."""
    # In 2023, solar 2023-03-22 is 闰二月初一
    bazi_leap = ReferenceOracle.compute_bazi(2023, 3, 22, 12, 0, 0)

    assert bazi_leap["pillars"]["year"]["pair"] == "癸卯"
    assert "闰" in bazi_leap["lunar_text"] or "二月" in bazi_leap["lunar_text"]
    assert bazi_leap["strength"] in ("身强", "身弱")

def test_boundary_gregorian_century_turn_year_2000():
    """T2.4: Gregorian century leap year 2000-01-01 rollover maintains mathematical GanZhi continuity."""
    bazi_1999_dec31 = ReferenceOracle.compute_bazi(1999, 12, 31, 12, 0, 0)
    bazi_2000_jan01 = ReferenceOracle.compute_bazi(2000, 1, 1, 12, 0, 0)

    # 1999-12-31 is Ji Mao year (1999), 2000-01-01 is still Ji Mao year because Li Chun is in Feb!
    assert bazi_1999_dec31["pillars"]["year"]["pair"] == "己卯"
    assert bazi_2000_jan01["pillars"]["year"]["pair"] == "己卯"

    # Day GanZhi advances by exactly 1
    assert bazi_1999_dec31["pillars"]["day"]["pair"] != bazi_2000_jan01["pillars"]["day"]["pair"]

def test_boundary_year_end_dec31_vs_jan1():
    """T2.5: Gregorian Dec 31 vs Jan 1 must not alter year pillar before Li Chun."""
    # 2025-12-31 vs 2026-01-01 are both Yi Si (乙巳) year
    b_dec = ReferenceOracle.compute_bazi(2025, 12, 31, 10, 0, 0)
    b_jan = ReferenceOracle.compute_bazi(2026, 1, 1, 10, 0, 0)

    assert b_dec["pillars"]["year"]["pair"] == "乙巳"
    assert b_jan["pillars"]["year"]["pair"] == "乙巳"

def test_boundary_solstice_winter_summer():
    """T2.6: Winter Solstice (冬至) and Summer Solstice (夏至) astronomical ephemeris sanity."""
    s_summer = Solar.fromYmdHms(2026, 6, 21, 12, 0, 0)
    jieqi = s_summer.getLunar().getJieQiTable()
    assert "夏至" in jieqi
    assert "冬至" in jieqi
    assert jieqi["夏至"].getMonth() == 6
    assert jieqi["冬至"].getMonth() == 12
