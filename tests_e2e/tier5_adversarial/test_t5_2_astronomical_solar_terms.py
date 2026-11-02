"""Tier 5 Adversarial Test: T5.2 Astronomical Edge & Solar Term Boundary Stress Test.

Empirical verification of astronomical Bazi engine and 110-point Wuxing energy conservation:
1. Extreme Gregorian dates (1901-01-01, 2099-12-31, 2000-02-29 leap day, 1900-02-28).
2. Exact minute transitions across the 24 solar terms, particularly Lichun (立春) astrological year boundary.
3. Late-night Zi-hour (早晚子时) under Sect 1 vs Sect 2.
4. Lunar leap months (2020 闰四月, 2023 闰二月).
5. 100% invariant of 110-point energy conservation (sum == 110.0) across all boundary evaluations.
"""
from datetime import datetime, timedelta
import random
from lunar_python import Solar, Lunar

from backend.app.services.bazi_engine import calculate_four_pillars


def test_t5_2_extreme_date_boundaries_and_century_continuity():
    """T5.2.1: Test extreme dates spanning 1901 through 2099 and century turns."""
    extreme_dates = [
        "1901-01-01T00:00:00",
        "1901-12-31T23:59:59",
        "1900-02-28T23:59:59",
        "2000-02-29T12:00:00",  # Century leap year day
        "2000-01-01T00:00:00",  # Y2K boundary
        "2024-02-29T23:59:59",  # Modern leap day
        "2099-01-01T00:00:00",
        "2099-12-31T23:59:59",  # Upper ephemeris boundary
    ]

    for dt_str in extreme_dates:
        res = calculate_four_pillars(dt_str)
        pillars = res["four_pillars"]

        # Invariant 1: All 4 pillars have non-empty stem, branch, pair, element
        for p_name in ("year", "month", "day", "hour"):
            p = pillars[p_name]
            assert p["stem"], f"Empty stem in {p_name} for {dt_str}"
            assert p["branch"], f"Empty branch in {p_name} for {dt_str}"
            assert len(p["pair"]) == 2, f"Invalid pair in {p_name} for {dt_str}"

        # Invariant 2: Total Wuxing energy strictly conserved at 110.0
        scores = res["wuxing_scores"]
        total_energy = sum(scores.values())
        assert abs(total_energy - 110.0) < 1e-4, f"Energy conservation violated at {dt_str}: {total_energy}"


def test_t5_2_lichun_exact_minute_year_boundary_transitions():
    """T5.2.2: Test minute-level year pillar transition across Lichun (立春) boundaries."""
    test_years = [2024, 2025, 2026]

    for yr in test_years:
        s = Solar.fromYmd(yr, 2, 4)
        l = s.getLunar()
        jq_table = l.getJieQiTable()

        # Locate exact Lichun transition time
        lichun_solar = None
        for k, v in jq_table.items():
            if "LI_CHUN" in k or "立春" in k:
                lichun_solar = v
                break

        assert lichun_solar is not None, f"Could not find Lichun for year {yr}"
        transition_dt = datetime(
            lichun_solar.getYear(),
            lichun_solar.getMonth(),
            lichun_solar.getDay(),
            lichun_solar.getHour(),
            lichun_solar.getMinute(),
            lichun_solar.getSecond(),
        )

        t_before = transition_dt - timedelta(minutes=1)
        t_after = transition_dt + timedelta(minutes=1)

        res_before = calculate_four_pillars(t_before)
        res_after = calculate_four_pillars(t_after)

        y_before = res_before["four_pillars"]["year"]["pair"]
        y_after = res_after["four_pillars"]["year"]["pair"]

        # Astrological year boundary: year pillar MUST change across Lichun
        assert y_before != y_after, (
            f"Year {yr} Lichun transition failed: before={y_before}, after={y_after} at {transition_dt}"
        )

        # Invariant: 110.0 energy conserved in both
        assert abs(sum(res_before["wuxing_scores"].values()) - 110.0) < 1e-4
        assert abs(sum(res_after["wuxing_scores"].values()) - 110.0) < 1e-4


def test_t5_2_24_solar_terms_exact_boundary_transitions():
    """T5.2.3: Test all 24 solar terms exact transitions across a full solar year."""
    # Sample mid-year 2024 to cover all 24 terms
    s = Solar.fromYmd(2024, 6, 1)
    l = s.getLunar()
    jq_table = l.getJieQiTable()

    assert len(jq_table) >= 24, f"Expected at least 24 solar terms, found {len(jq_table)}"

    for term_name, jq_solar in jq_table.items():
        dt = datetime(
            jq_solar.getYear(),
            jq_solar.getMonth(),
            jq_solar.getDay(),
            jq_solar.getHour(),
            jq_solar.getMinute(),
            jq_solar.getSecond(),
        )
        t_before = dt - timedelta(minutes=1)
        t_after = dt + timedelta(minutes=1)

        res_b = calculate_four_pillars(t_before)
        res_a = calculate_four_pillars(t_after)

        # 110-point conservation invariant
        assert abs(sum(res_b["wuxing_scores"].values()) - 110.0) < 1e-4, f"Failed at {term_name} before"
        assert abs(sum(res_a["wuxing_scores"].values()) - 110.0) < 1e-4, f"Failed at {term_name} after"


def test_t5_2_late_night_zi_hour_sect_variations():
    """T5.2.4: Test early vs late Zi-hour (早子时 vs 晚子时/夜子时) and sect differences."""
    # 23:30 (Late Zi / 夜子时) vs 00:30 (Early Zi / 早子时)
    late_zi_str = "2024-08-15T23:30:00"
    early_zi_str = "2024-08-16T00:30:00"

    res_late_sect2 = calculate_four_pillars(late_zi_str, sect=2)
    res_late_sect1 = calculate_four_pillars(late_zi_str, sect=1)
    res_early_sect2 = calculate_four_pillars(early_zi_str, sect=2)

    # In Sect 2: 23:30 day pillar retains Aug 15 day stem
    # In Sect 1: 23:30 transitions day pillar to Aug 16 day stem
    p_late2 = res_late_sect2["four_pillars"]
    p_late1 = res_late_sect1["four_pillars"]

    assert p_late2["day"]["pair"] != p_late1["day"]["pair"], "Sect 1 vs Sect 2 should yield different day pillars"

    # Energy conservation across all variations
    for res in (res_late_sect2, res_late_sect1, res_early_sect2):
        assert abs(sum(res["wuxing_scores"].values()) - 110.0) < 1e-4


def test_t5_2_leap_months_ephemeris_stress():
    """T5.2.5: Stress-test lunar leap months (2020 leap 4th month, 2023 leap 2nd month)."""
    leap_cases = [
        # 2020 leap 4th month (solar dates ~May/June 2020)
        "2020-05-25T12:00:00",
        "2020-06-15T18:30:00",
        # 2023 leap 2nd month (solar dates ~March/April 2023)
        "2023-04-01T08:15:00",
        "2023-04-10T14:45:00",
    ]

    for dt_str in leap_cases:
        res = calculate_four_pillars(dt_str)
        assert res["lunar_datetime"], f"Empty lunar datetime for leap case {dt_str}"
        assert abs(sum(res["wuxing_scores"].values()) - 110.0) < 1e-4


def test_t5_2_100_percent_wuxing_energy_conservation_invariant():
    """T5.2.6: High-volume Monte Carlo check: 500 pseudo-random astronomical timestamps."""
    random.seed(42)
    for _ in range(500):
        # Generate random date between 1902 and 2098
        year = random.randint(1902, 2098)
        month = random.randint(1, 12)
        day = random.randint(1, 28)
        hour = random.randint(0, 23)
        minute = random.randint(0, 59)
        second = random.randint(0, 59)

        dt = datetime(year, month, day, hour, minute, second)
        res = calculate_four_pillars(dt)
        tot = sum(res["wuxing_scores"].values())
        assert abs(tot - 110.0) < 1e-4, f"Energy conservation failed at {dt}: {tot}"
