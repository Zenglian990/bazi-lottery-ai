"""Precision astronomical Bazi calculation engine using lunar-python."""
from datetime import datetime, timedelta
import re
from typing import Dict, Any, Optional, Tuple, Union
from lunar_python import Solar, Lunar

from backend.app.services.wuxing_engine import (
    analyze_wuxing_energy,
    STEM_ELEMENTS,
    BRANCH_HIDDEN_STEMS,
    ELEMENT_NAMES_ZH,
)


def parse_datetime_str(dt_str: Optional[str]) -> datetime:
    """Parse flexible datetime strings into a Python datetime object."""
    if not dt_str:
        return datetime.now()

    cleaned = dt_str.strip().replace("Z", "").replace("+00:00", "")
    # Try ISO formats
    for fmt in (
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M",
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
    ):
        try:
            return datetime.strptime(cleaned, fmt)
        except ValueError:
            continue

    # Fallback with fromisoformat
    try:
        return datetime.fromisoformat(cleaned)
    except Exception:
        return datetime.now()


def adjust_true_solar_time(dt: datetime, longitude: Optional[float]) -> datetime:
    """
    Adjust clock time (UTC+8 / CST 120.0°E baseline) to True Solar Time based on longitude.
    Formula: Delta_t = (longitude - 120.0) * 4 minutes.
    """
    if longitude is None or abs(longitude - 120.0) < 1e-4:
        return dt
    delta_minutes = (longitude - 120.0) * 4.0
    return dt + timedelta(minutes=delta_minutes)


def calculate_four_pillars(
    birth_datetime: Optional[Union[str, datetime]] = None,
    calendar_type: str = "solar",
    longitude: Optional[float] = 120.0,
    sect: int = 2,
) -> Dict[str, Any]:
    """
    Calculate Four Pillars (Year, Month, Day, Hour) and astronomical ephemeris.
    """
    if isinstance(birth_datetime, datetime):
        base_dt = birth_datetime
    else:
        base_dt = parse_datetime_str(birth_datetime)

    # True Solar Time adjustment
    dt = adjust_true_solar_time(base_dt, longitude)

    # Solar / Lunar ephemeris resolution
    if calendar_type.lower() == "lunar":
        # Parse as lunar calendar
        lunar = Lunar.fromYmdHms(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
        solar = lunar.getSolar()
    else:
        solar = Solar.fromYmdHms(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
        lunar = solar.getLunar()

    eight_char = lunar.getEightChar()
    eight_char.setSect(sect)

    year_gan = eight_char.getYearGan()
    year_zhi = eight_char.getYearZhi()
    month_gan = eight_char.getMonthGan()
    month_zhi = eight_char.getMonthZhi()
    day_gan = eight_char.getDayGan()
    day_zhi = eight_char.getDayZhi()
    time_gan = eight_char.getTimeGan()
    time_zhi = eight_char.getTimeZhi()

    # Build pillar details with element combinations and hidden stems
    def make_pillar_detail(stem: str, branch: str) -> Dict[str, Any]:
        stem_elem = STEM_ELEMENTS[stem]
        branch_hiddens = BRANCH_HIDDEN_STEMS[branch]
        main_hidden_stem = branch_hiddens[0][0]
        branch_elem = STEM_ELEMENTS[main_hidden_stem]
        elem_combo = f"{ELEMENT_NAMES_ZH[stem_elem]}{ELEMENT_NAMES_ZH[branch_elem]}"
        hide_stems = [s for s, _ in branch_hiddens]
        return {
            "stem": stem,
            "branch": branch,
            "pair": f"{stem}{branch}",
            "element": elem_combo,
            "hide_stems": hide_stems,
        }

    four_pillars = {
        "year": make_pillar_detail(year_gan, year_zhi),
        "month": make_pillar_detail(month_gan, month_zhi),
        "day": make_pillar_detail(day_gan, day_zhi),
        "hour": make_pillar_detail(time_gan, time_zhi),
    }

    # Wuxing energy quantification
    wuxing_analysis = analyze_wuxing_energy(
        year_stem=year_gan,
        year_branch=year_zhi,
        month_stem=month_gan,
        month_branch=month_zhi,
        day_stem=day_gan,
        day_branch=day_zhi,
        hour_stem=time_gan,
        hour_branch=time_zhi,
    )

    solar_dt_str = dt.strftime("%Y-%m-%d %H:%M:%S")
    lunar_dt_str = lunar.toString()

    return {
        "solar_datetime": solar_dt_str,
        "lunar_datetime": lunar_dt_str,
        "four_pillars": four_pillars,
        "pillars": four_pillars,
        "wuxing_scores": wuxing_analysis["scores"],
        "wuxing_percentage": wuxing_analysis["percentages"],
        "day_master": day_gan,
        "day_master_element": wuxing_analysis["day_master_element"],
        "strength": wuxing_analysis["strength"],
        "strength_ratio": wuxing_analysis["strength_ratio"],
        "lacking_elements": wuxing_analysis["lacking_elements"],
        "xi_yong_shen": wuxing_analysis["xi_yong_shen"],
        "ji_shen": wuxing_analysis["ji_shen"],
        "lacking_elements_zh": wuxing_analysis["lacking_elements_zh"],
        "xi_yong_shen_zh": wuxing_analysis["xi_yong_shen_zh"],
        "ji_shen_zh": wuxing_analysis["ji_shen_zh"],
    }
