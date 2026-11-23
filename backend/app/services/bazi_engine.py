"""Precision astronomical Bazi calculation engine using lunar-python."""
from datetime import datetime, timedelta, timezone
import math
import re
from typing import Any, Dict, List, Optional, Tuple, Union
from zoneinfo import ZoneInfo
from lunar_python import Lunar, LunarMonth, Solar

from backend.app.services.wuxing_engine import (
    BRANCH_HIDDEN_STEMS,
    ELEMENT_NAMES_ZH,
    STEM_ELEMENTS,
    analyze_wuxing_energy,
)

CHINA_TZ = ZoneInfo("Asia/Shanghai")
_FIXED_CST = timezone(timedelta(hours=8))

_LUNAR_STR_RE = re.compile(
    r"^\s*(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[T\s]+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?\s*$"
)


def parse_datetime_str(dt_str: Optional[str]) -> datetime:
    """将日期时间字符串解析为 Asia/Shanghai 时区 datetime；非空非法字符串明确抛出 ValueError。"""
    if dt_str is None or not str(dt_str).strip():
        return datetime.now(CHINA_TZ)

    s = str(dt_str).strip()
    # 1. 带时区偏移的 ISO-8601 字符串（先转换至固定 UTC+8，避免 1986-1991 CDT 被额外加 1 小时）
    if s.endswith(("Z", "z")) or re.search(r"[+-]\d{2}:?\d{2}$", s):
        iso_candidate = s[:-1] + "+00:00" if s.endswith(("Z", "z")) else s
        try:
            dt = datetime.fromisoformat(iso_candidate).astimezone(_FIXED_CST)
            return dt.replace(tzinfo=CHINA_TZ)
        except ValueError as exc:
            raise ValueError(f"Invalid timezone-aware datetime string: '{dt_str}'") from exc

    # 2. 常见公历格式（兼容 '-' 与 '/' 分隔）
    for fmt in (
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M",
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
        "%Y/%m/%d %H:%M:%S",
        "%Y/%m/%d %H:%M",
        "%Y/%m/%d",
    ):
        try:
            return datetime.strptime(s, fmt).replace(tzinfo=CHINA_TZ)
        except ValueError:
            continue

    # 3. fromisoformat 兜底
    try:
        dt = datetime.fromisoformat(s)
        return dt.replace(tzinfo=CHINA_TZ) if dt.tzinfo is None else dt.astimezone(CHINA_TZ)
    except ValueError as exc:
        raise ValueError(f"Invalid datetime string: '{dt_str}'") from exc


def _parse_raw_lunar_components(dt_str: str) -> Optional[Tuple[int, int, int, int, int, int]]:
    """从农历字符串（如 '2023-02-30T12:00:00'）直接提取年月日时分秒，避免被公历 strptime 误拒。"""
    m = _LUNAR_STR_RE.match(dt_str.strip())
    if not m:
        return None
    year = int(m.group(1))
    month = int(m.group(2))
    day = int(m.group(3))
    hour = int(m.group(4)) if m.group(4) is not None else 12
    minute = int(m.group(5)) if m.group(5) is not None else 0
    second = int(m.group(6)) if m.group(6) is not None else 0
    if not (1900 <= year <= 2100 and 1 <= month <= 12 and 1 <= day <= 30):
        raise ValueError(f"Lunar date out of valid range: '{dt_str}'")
    if not (0 <= hour <= 23 and 0 <= minute <= 59 and 0 <= second <= 59):
        raise ValueError(f"Lunar time out of valid range: '{dt_str}'")
    return year, month, day, hour, minute, second


# 12 时辰到标准钟表时刻 (hour, minute) 映射（不再强行覆写用户传入的 sect 流派）
SHICHEN_MAPPING: Dict[str, Tuple[int, int, int]] = {
    "早子": (0, 30, 2),
    "早子时": (0, 30, 2),
    "晨子": (0, 30, 2),
    "晚子": (23, 30, 2),
    "晚子时": (23, 30, 2),
    "夜子": (23, 30, 2),
    "夜子时": (23, 30, 2),
    "子": (0, 30, 2),
    "子时": (0, 30, 2),
    "丑": (2, 0, 2),
    "丑时": (2, 0, 2),
    "寅": (4, 0, 2),
    "寅时": (4, 0, 2),
    "卯": (6, 0, 2),
    "卯时": (6, 0, 2),
    "辰": (8, 0, 2),
    "辰时": (8, 0, 2),
    "巳": (10, 0, 2),
    "巳时": (10, 0, 2),
    "午": (12, 0, 2),
    "午时": (12, 0, 2),
    "未": (14, 0, 2),
    "未时": (14, 0, 2),
    "申": (16, 0, 2),
    "申时": (16, 0, 2),
    "酉": (18, 0, 2),
    "酉时": (18, 0, 2),
    "戌": (20, 0, 2),
    "戌时": (20, 0, 2),
    "亥": (22, 0, 2),
    "亥时": (22, 0, 2),
}


def parse_shichen(shichen_str: Optional[str]) -> Optional[Tuple[int, int, int]]:
    """解析中文十二时辰（含早/晚子时）为 (hour, minute, default_sect)。"""
    if not shichen_str:
        return None
    cleaned = shichen_str.strip()
    return SHICHEN_MAPPING.get(cleaned)


def compute_equation_of_time_minutes(dt: datetime) -> float:
    """
    计算天文均时差（Equation of Time，单位：分钟）。
    采用经典天文近似公式：EoT = 9.87*sin(2B) - 7.53*cos(B) - 1.5*sin(B)，
    其中 B = 2*pi*(doy - 81)/365，全年误差 < 30 秒。
    """
    doy = dt.timetuple().tm_yday
    b = 2.0 * math.pi * (doy - 81) / 365.0
    return 9.87 * math.sin(2.0 * b) - 7.53 * math.cos(b) - 1.5 * math.sin(b)


def adjust_true_solar_time(
    dt: datetime,
    longitude: Optional[float],
    apply_eot: Optional[bool] = None,
    is_dst: bool = False,
) -> datetime:
    """
    将东八区北京时间（120.0°E 基准）校正为真太阳时。
    - 经度平太阳时差：Delta_lon = (longitude - 120.0) * 4 分钟
    - 天文均时差 EoT：默认当显式启用 apply_eot=True 或传入非 120.0°E 城市经度时自动叠加
    - 夏令时校正：当 1986-1991 年中国夏令时出生且标记 is_dst=True 时，回退 60 分钟还原北京时间
    """
    adjusted = dt
    if is_dst:
        adjusted = adjusted - timedelta(hours=1)

    lon = 120.0 if longitude is None else float(longitude)
    delta_lon_minutes = (lon - 120.0) * 4.0

    should_apply_eot = apply_eot if apply_eot is not None else (abs(lon - 120.0) >= 1e-4)
    eot_minutes = compute_equation_of_time_minutes(adjusted) if should_apply_eot else 0.0

    total_minutes = delta_lon_minutes + eot_minutes
    if abs(total_minutes) < 1e-6:
        return adjusted
    return adjusted + timedelta(minutes=total_minutes)


def _format_actual_shichen_label(lunar: Lunar, adj_hour: int) -> str:
    """依据真太阳时校正后的实际时辰生成与四柱时支严格一致的时辰文案。"""
    zhi = lunar.getTimeZhi()
    if zhi == "子":
        return "晚子时" if adj_hour == 23 else "早子时"
    return f"{zhi}时"


def calculate_four_pillars(
    birth_datetime: Optional[Union[str, datetime]] = None,
    calendar_type: str = "solar",
    longitude: Optional[float] = 120.0,
    sect: int = 2,
    shichen: Optional[str] = None,
    lunar_year: Optional[int] = None,
    lunar_month: Optional[int] = None,
    lunar_day: Optional[int] = None,
    is_leap_month: bool = False,
    lunar_hour: Optional[str] = None,
    gender: Optional[str] = "male",
    apply_eot: Optional[bool] = None,
    is_dst: bool = False,
) -> Dict[str, Any]:
    """
    计算精准四柱八字、五行量化、起运岁数与前 8 步大运。
    - 严禁覆盖用户显式传入的 sect（1=早子时换日派，2=晚子时派）；
    - 非空非法 birth_datetime 抛出 ValueError，不再静默回退当前时间；
    - 农历 29 天小月安全钳制并记录 calendar_warnings，假闰月明确记录校验错误/提示；
    - 经度校正后 lunar_datetime 时辰严格与校正后四柱时支一致。
    """
    shichen_to_use = lunar_hour or shichen
    shichen_parsed = parse_shichen(shichen_to_use)
    effective_sect = sect if sect in (1, 2) else 2
    calendar_warnings: List[str] = []

    # 判定历法模式：显式 solar 优先尊重 birth_datetime（除非未提供 birth_datetime 而仅给了 lunar_*）
    has_lunar_parts = (
        lunar_year is not None and lunar_month is not None and lunar_day is not None
    )
    req_cal = (calendar_type or "solar").strip().lower()
    if req_cal == "solar" and birth_datetime is not None:
        cal_type = "solar"
    elif has_lunar_parts or req_cal == "lunar":
        cal_type = "lunar"
    else:
        cal_type = "solar"

    if cal_type == "lunar":
        # 解析农历年月日与时分秒（支持纯农历拆分子段，也支持 '2023-02-30T12:00:00' 农历字符串）
        if has_lunar_parts:
            y, m, d = int(lunar_year), int(lunar_month), int(lunar_day)
            if shichen_parsed:
                raw_h, raw_mi, raw_s = shichen_parsed[0], shichen_parsed[1], 0
            elif isinstance(birth_datetime, datetime):
                raw_h, raw_mi, raw_s = birth_datetime.hour, birth_datetime.minute, birth_datetime.second
            elif isinstance(birth_datetime, str) and birth_datetime.strip():
                parsed_raw = _parse_raw_lunar_components(birth_datetime)
                if parsed_raw:
                    _, _, _, raw_h, raw_mi, raw_s = parsed_raw
                else:
                    dt_tmp = parse_datetime_str(birth_datetime)
                    raw_h, raw_mi, raw_s = dt_tmp.hour, dt_tmp.minute, dt_tmp.second
            else:
                raw_h, raw_mi, raw_s = 12, 0, 0
        elif isinstance(birth_datetime, datetime):
            dt_aware = (
                birth_datetime.astimezone(CHINA_TZ)
                if birth_datetime.tzinfo is not None
                else birth_datetime.replace(tzinfo=CHINA_TZ)
            )
            y, m, d = dt_aware.year, dt_aware.month, dt_aware.day
            raw_h, raw_mi, raw_s = (
                (shichen_parsed[0], shichen_parsed[1], 0)
                if shichen_parsed
                else (dt_aware.hour, dt_aware.minute, dt_aware.second)
            )
        elif isinstance(birth_datetime, str) and birth_datetime.strip():
            parsed_lunar = _parse_raw_lunar_components(birth_datetime)
            if parsed_lunar is not None:
                y, m, d, raw_h, raw_mi, raw_s = parsed_lunar
            else:
                dt_parsed = parse_datetime_str(birth_datetime)
                y, m, d = dt_parsed.year, dt_parsed.month, dt_parsed.day
                raw_h, raw_mi, raw_s = dt_parsed.hour, dt_parsed.minute, dt_parsed.second
            if shichen_parsed:
                raw_h, raw_mi, raw_s = shichen_parsed[0], shichen_parsed[1], 0
        else:
            now_dt = datetime.now(CHINA_TZ)
            now_lunar = Solar.fromYmdHms(
                now_dt.year, now_dt.month, now_dt.day, now_dt.hour, now_dt.minute, now_dt.second
            ).getLunar()
            y, m, d = now_lunar.getYear(), abs(now_lunar.getMonth()), now_lunar.getDay()
            raw_h, raw_mi, raw_s = (
                (shichen_parsed[0], shichen_parsed[1], 0)
                if shichen_parsed
                else (now_dt.hour, now_dt.minute, now_dt.second)
            )

        if not (1900 <= y <= 2100 and 1 <= m <= 12 and 1 <= d <= 30):
            raise ValueError(f"农历日期超出支持范围: {y}-{m}-{d}")

        # 闰月严格校验：若该年无此闰月则明确抛出错误
        if is_leap_month:
            leap_lm = LunarMonth.fromYm(y, -m)
            if leap_lm is None:
                raise ValueError(f"农历{y}年不存在闰{m}月")
            actual_m = -m
            lm = leap_lm
        else:
            actual_m = m
            lm = LunarMonth.fromYm(y, actual_m)

        max_d = lm.getDayCount() if lm else 30
        if d > max_d:
            # 保留小月 30 日向 29 日安全钳制以兼容边界调用，并显式记录 calendar_warnings
            calendar_warnings.append(f"农历{y}年{m}月仅{max_d}天，已由第{d}日安全调整为第{max_d}日")
            actual_d = max_d
        else:
            actual_d = max(d, 1)

        raw_lunar = Lunar.fromYmdHms(y, actual_m, actual_d, raw_h, raw_mi, raw_s)
        raw_solar = raw_lunar.getSolar()
        raw_solar_dt = datetime(
            raw_solar.getYear(),
            raw_solar.getMonth(),
            raw_solar.getDay(),
            raw_solar.getHour(),
            raw_solar.getMinute(),
            raw_solar.getSecond(),
            tzinfo=CHINA_TZ,
        )
        adj_solar_dt = adjust_true_solar_time(
            raw_solar_dt, longitude, apply_eot=apply_eot, is_dst=is_dst
        )
        solar = Solar.fromYmdHms(
            adj_solar_dt.year,
            adj_solar_dt.month,
            adj_solar_dt.day,
            adj_solar_dt.hour,
            adj_solar_dt.minute,
            adj_solar_dt.second,
        )
        lunar = solar.getLunar()
        solar_dt_str = adj_solar_dt.strftime("%Y-%m-%d %H:%M:%S")
        actual_shichen = _format_actual_shichen_label(lunar, adj_solar_dt.hour)
        lunar_dt_str = (
            f"{lunar.getYear()}年 ({lunar.getYearInGanZhi()}{lunar.getYearShengXiao()}年) "
            f"农历{lunar.getMonthInChinese()}月{lunar.getDayInChinese()} {actual_shichen}"
        )
    else:
        if isinstance(birth_datetime, datetime):
            base_dt = (
                birth_datetime.astimezone(CHINA_TZ)
                if birth_datetime.tzinfo is not None
                else birth_datetime.replace(tzinfo=CHINA_TZ)
            )
        else:
            base_dt = parse_datetime_str(birth_datetime)

        if shichen_parsed:
            base_dt = base_dt.replace(
                hour=shichen_parsed[0], minute=shichen_parsed[1], second=0, microsecond=0
            )

        dt = adjust_true_solar_time(base_dt, longitude, apply_eot=apply_eot, is_dst=is_dst)
        solar = Solar.fromYmdHms(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
        lunar = solar.getLunar()
        solar_dt_str = dt.strftime("%Y-%m-%d %H:%M:%S")
        actual_shichen = _format_actual_shichen_label(lunar, dt.hour)
        lunar_dt_str = (
            f"{lunar.getYear()}年 ({lunar.getYearInGanZhi()}{lunar.getYearShengXiao()}年) "
            f"农历{lunar.getMonthInChinese()}月{lunar.getDayInChinese()} {actual_shichen}"
        )

    eight_char = lunar.getEightChar()
    eight_char.setSect(effective_sect)

    year_gan = eight_char.getYearGan()
    year_zhi = eight_char.getYearZhi()
    month_gan = eight_char.getMonthGan()
    month_zhi = eight_char.getMonthZhi()
    day_gan = eight_char.getDayGan()
    day_zhi = eight_char.getDayZhi()
    time_gan = eight_char.getTimeGan()
    time_zhi = eight_char.getTimeZhi()

    # 若处于春节与立春之间（农历年干支与立春八字年柱不同），显式注明八字年柱消除违和
    bazi_year_pair = f"{year_gan}{year_zhi}"
    if bazi_year_pair != lunar.getYearInGanZhi():
        lunar_dt_str = f"{lunar_dt_str} [八字{bazi_year_pair}年]"

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

    is_male = (gender or "male").lower() in ("male", "m", "乾造", "乾", "男")
    gender_int = 1 if is_male else 0
    yun = eight_char.getYun(gender_int)
    dayun_forward = yun.isForward()
    raw_dayuns = [d for d in yun.getDaYun() if d.getGanZhi()][:8]
    # 统一使用第一柱大运虚岁起运年龄，避免与 dayun_details[0].start_age 混用周岁/虚岁差 1-2 岁
    dayun_start_age = int(raw_dayuns[0].getStartAge()) if raw_dayuns else int(yun.getStartYear())
    dayun_list = [d.getGanZhi() for d in raw_dayuns]
    dayun_details = [
        {
            "gan_zhi": d.getGanZhi(),
            "start_age": int(d.getStartAge()),
            "end_age": int(d.getEndAge()),
            "start_year": int(d.getStartYear()),
            "end_year": int(d.getEndYear()),
        }
        for d in raw_dayuns
    ]

    return {
        "solar_datetime": solar_dt_str,
        "lunar_datetime": lunar_dt_str,
        "gender": "male" if is_male else "female",
        "dayun_forward": dayun_forward,
        "dayun_start_age": dayun_start_age,
        "dayun_list": dayun_list,
        "dayun_details": dayun_details,
        "four_pillars": four_pillars,
        "pillars": four_pillars,
        "wuxing_scores": wuxing_analysis["scores"],
        "wuxing_percentage": wuxing_analysis["percentages"],
        "day_master": day_gan,
        "day_master_element": wuxing_analysis["day_master_element"],
        "strength": wuxing_analysis["strength"],
        "strength_level": wuxing_analysis["strength_level"],
        "strength_ratio": wuxing_analysis["strength_ratio"],
        "lacking_elements": wuxing_analysis["lacking_elements"],
        "xi_yong_shen": wuxing_analysis["xi_yong_shen"],
        "ji_shen": wuxing_analysis["ji_shen"],
        "lacking_elements_zh": wuxing_analysis["lacking_elements_zh"],
        "xi_yong_shen_zh": wuxing_analysis["xi_yong_shen_zh"],
        "ji_shen_zh": wuxing_analysis["ji_shen_zh"],
        "lacking_elements_en": wuxing_analysis.get("lacking_elements_en", []),
        "xi_yong_shen_en": wuxing_analysis.get("xi_yong_shen_en", []),
        "ji_shen_en": wuxing_analysis.get("ji_shen_en", []),
        "day_master_element_zh": wuxing_analysis.get("day_master_element_zh", ""),
        "shichen": actual_shichen,
        "calendar_warnings": calendar_warnings,
    }
