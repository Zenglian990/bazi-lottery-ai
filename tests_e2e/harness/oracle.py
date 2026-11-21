"""Authoritative Reference Oracle for E2E tests.
Provides independent mathematical and astronomical ground truth for:
1. Official Lottery Rules & Ticket Validation
2. Bazi Solar-Lunar, Four Pillars & 110-point Wuxing Quantification
3. Hetu Numerology & Lucky Number Pool Partitions
4. Statistical Calculations (Frequency, Cold/Hot thresholds, Omission values)
"""
from typing import Dict, List, Tuple, Any, Optional, Set
import math
from lunar_python import Solar, Lunar

class ReferenceOracle:
    """Authoritative reference implementation against official specifications."""

    # Official Lottery Rule Constants
    LOTTERY_SPECS = {
        "ssq": {
            "name": "双色球",
            "primary": {"name": "红球", "count": 6, "min": 1, "max": 33, "unique": True, "sorted": True, "fmt": "%02d"},
            "secondary": {"name": "蓝球", "count": 1, "min": 1, "max": 16, "unique": False, "sorted": False, "fmt": "%02d"},
            "combinations": 17721088,
        },
        "dlt": {
            "name": "超级大乐透",
            "primary": {"name": "前区", "count": 5, "min": 1, "max": 35, "unique": True, "sorted": True, "fmt": "%02d"},
            "secondary": {"name": "后区", "count": 2, "min": 1, "max": 12, "unique": True, "sorted": True, "fmt": "%02d"},
            "combinations": 21425712,
        },
        "fc3d": {
            "name": "福彩3D",
            "primary": {"name": "百十个位", "count": 3, "min": 0, "max": 9, "unique": False, "sorted": False, "fmt": "%d"},
            "secondary": None,
            "combinations": 1000,
        },
        "qxc": {
            "name": "七星彩",
            "primary": {"name": "前六位", "count": 6, "min": 0, "max": 9, "unique": False, "sorted": False, "fmt": "%d"},
            "secondary": {"name": "第七位", "count": 1, "min": 0, "max": 14, "unique": False, "sorted": False, "fmt": "%02d"},
            "combinations": 15000000,
        },
    }

    # Heavenly Stems & Earthly Branches Element Mappings
    STEM_ELEMENTS = {
        "甲": "wood", "乙": "wood",
        "丙": "fire", "丁": "fire",
        "戊": "earth", "己": "earth",
        "庚": "metal", "辛": "metal",
        "壬": "water", "癸": "water",
    }

    BRANCH_HIDDEN_STEMS = {
        "子": [("癸", 1.00)],
        "丑": [("己", 0.60), ("癸", 0.30), ("辛", 0.10)],
        "寅": [("甲", 0.60), ("丙", 0.30), ("戊", 0.10)],
        "卯": [("乙", 1.00)],
        "辰": [("戊", 0.60), ("乙", 0.30), ("癸", 0.10)],
        "巳": [("丙", 0.60), ("戊", 0.30), ("庚", 0.10)],
        "午": [("丁", 0.70), ("己", 0.30)],
        "未": [("己", 0.60), ("丁", 0.30), ("乙", 0.10)],
        "申": [("庚", 0.60), ("壬", 0.30), ("戊", 0.10)],
        "酉": [("辛", 1.00)],
        "戌": [("戊", 0.60), ("辛", 0.30), ("丁", 0.10)],
        "亥": [("壬", 0.70), ("甲", 0.30)],
    }

    # 110-point positional weights
    PILLAR_WEIGHTS = {
        "year_stem": 10.0,
        "year_branch": 10.0,
        "month_stem": 12.0,
        "month_branch": 35.0,
        "day_stem": 12.0,
        "day_branch": 15.0,
        "hour_stem": 8.0,
        "hour_branch": 8.0,
    }

    # Five Elements Generation & Destruction Cycles
    # generating: wood -> fire -> earth -> metal -> water -> wood
    GENERATING_CYCLE = {
        "wood": "fire",
        "fire": "earth",
        "earth": "metal",
        "metal": "water",
        "water": "wood",
    }
    # reverse generating (mother element)
    MOTHER_ELEMENT = {
        "wood": "water",
        "fire": "wood",
        "earth": "fire",
        "metal": "earth",
        "water": "metal",
    }

    # Hetu Numerology: tail mod 10 -> element
    HETU_TAIL_TO_ELEMENT = {
        1: "water", 6: "water",
        2: "fire", 7: "fire",
        3: "wood", 8: "wood",
        4: "metal", 9: "metal",
        5: "earth", 0: "earth",
    }

    ELEMENT_NAMES_ZH = {
        "wood": "木",
        "fire": "火",
        "earth": "土",
        "metal": "金",
        "water": "水",
    }

    ZH_TO_ELEMENT = {v: k for k, v in ELEMENT_NAMES_ZH.items()}

    # --- 1. Lottery Rule Reference Validation ---

    @classmethod
    def validate_ticket(
        cls,
        lottery_type: str,
        primary_numbers: List[Any],
        secondary_numbers: Optional[List[Any]] = None,
    ) -> Tuple[bool, Optional[str], Optional[List[str]], Optional[List[str]], Optional[str]]:
        """Authoritative ticket validator."""
        l_type = lottery_type.lower()
        if l_type not in cls.LOTTERY_SPECS:
            return False, f"Unsupported lottery type: '{lottery_type}'", None, None, None

        spec = cls.LOTTERY_SPECS[l_type]
        p_spec = spec["primary"]
        s_spec = spec["secondary"]

        # Validate primary
        p_valid, p_err, p_norm = cls._validate_zone(p_spec, primary_numbers)
        if not p_valid:
            return False, p_err, None, None, None

        s_norm = None
        if s_spec:
            s_numbers = secondary_numbers or []
            s_valid, s_err, s_norm = cls._validate_zone(s_spec, s_numbers)
            if not s_valid:
                return False, s_err, None, None, None
        else:
            if secondary_numbers and len(secondary_numbers) > 0:
                return False, f"Lottery '{lottery_type}' does not accept secondary zone numbers", None, None, None

        # Build formatted ticket string
        pri_str = ",".join(p_norm)
        if s_norm:
            sec_str = ",".join(s_norm)
            ticket_str = f"{pri_str}|{sec_str}"
        else:
            ticket_str = pri_str

        return True, None, p_norm, s_norm, ticket_str

    @classmethod
    def _validate_zone(cls, zone_spec: Dict[str, Any], numbers: List[Any]) -> Tuple[bool, Optional[str], Optional[List[str]]]:
        count = zone_spec["count"]
        min_v = zone_spec["min"]
        max_v = zone_spec["max"]
        is_unique = zone_spec["unique"]
        is_sorted = zone_spec["sorted"]
        fmt = zone_spec["fmt"]

        if len(numbers) != count:
            return False, f"Requires exactly {count} numbers, but got {len(numbers)}", None

        parsed: List[int] = []
        for x in numbers:
            try:
                v = int(str(x).strip())
            except (ValueError, TypeError):
                return False, f"Invalid non-integer item '{x}'", None
            if v < min_v or v > max_v:
                return False, f"Number {v} outside valid range [{min_v}, {max_v}]", None
            parsed.append(v)

        if is_unique and len(set(parsed)) != len(parsed):
            return False, "Duplicate numbers are not allowed", None

        if is_sorted:
            parsed.sort()

        normalized = [fmt % v for v in parsed]
        return True, None, normalized

    # --- 2. Astronomical & Bazi Reference Calculation ---

    @classmethod
    def compute_bazi(
        cls,
        year: int,
        month: int,
        day: int,
        hour: int,
        minute: int = 0,
        second: int = 0,
        sect: int = 2,
    ) -> Dict[str, Any]:
        """Compute exact Four Pillars using lunar-python ephemeris."""
        solar = Solar.fromYmdHms(year, month, day, hour, minute, second)
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

        pillars = {
            "year": {"stem": year_gan, "branch": year_zhi, "pair": f"{year_gan}{year_zhi}"},
            "month": {"stem": month_gan, "branch": month_zhi, "pair": f"{month_gan}{month_zhi}"},
            "day": {"stem": day_gan, "branch": day_zhi, "pair": f"{day_gan}{day_zhi}"},
            "hour": {"stem": time_gan, "branch": time_zhi, "pair": f"{time_gan}{time_zhi}"},
        }

        # 110-point Wuxing calculation
        scores = {"wood": 0.0, "fire": 0.0, "earth": 0.0, "metal": 0.0, "water": 0.0}

        # Year Stem (10)
        scores[cls.STEM_ELEMENTS[year_gan]] += cls.PILLAR_WEIGHTS["year_stem"]
        # Month Stem (12)
        scores[cls.STEM_ELEMENTS[month_gan]] += cls.PILLAR_WEIGHTS["month_stem"]
        # Day Stem (12 - Day Master)
        scores[cls.STEM_ELEMENTS[day_gan]] += cls.PILLAR_WEIGHTS["day_stem"]
        # Hour Stem (8)
        scores[cls.STEM_ELEMENTS[time_gan]] += cls.PILLAR_WEIGHTS["hour_stem"]

        # Branches with hidden stems
        for stem_name, ratio in cls.BRANCH_HIDDEN_STEMS[year_zhi]:
            scores[cls.STEM_ELEMENTS[stem_name]] += cls.PILLAR_WEIGHTS["year_branch"] * ratio

        for stem_name, ratio in cls.BRANCH_HIDDEN_STEMS[month_zhi]:
            scores[cls.STEM_ELEMENTS[stem_name]] += cls.PILLAR_WEIGHTS["month_branch"] * ratio

        for stem_name, ratio in cls.BRANCH_HIDDEN_STEMS[day_zhi]:
            scores[cls.STEM_ELEMENTS[stem_name]] += cls.PILLAR_WEIGHTS["day_branch"] * ratio

        for stem_name, ratio in cls.BRANCH_HIDDEN_STEMS[time_zhi]:
            scores[cls.STEM_ELEMENTS[stem_name]] += cls.PILLAR_WEIGHTS["hour_branch"] * ratio

        total_score = sum(scores.values())
        percentages = {k: round(v / total_score, 4) for k, v in scores.items()}

        day_master_element = cls.STEM_ELEMENTS[day_gan]
        mother_element = cls.MOTHER_ELEMENT[day_master_element]

        # Self party = Day master element score + mother element score
        self_party_score = scores[day_master_element] + scores[mother_element]
        is_strong = self_party_score >= 55.0  # 55 / 110 = 50%

        strength = "身强" if is_strong else "身弱"

        # Xi Yong Shen determination
        lacking = [elem for elem, sc in scores.items() if sc < 5.0]

        if is_strong:
            # Strong day master needs draining (Child/Output, Wealth, Officer/Control)
            child_element = cls.GENERATING_CYCLE[day_master_element]
            # Wealth is what Day Master controls: Wood controls Earth, Fire controls Metal, Earth controls Water, Metal controls Wood, Water controls Fire
            wealth_cycle = {"wood": "earth", "fire": "metal", "earth": "water", "metal": "wood", "water": "fire"}
            wealth_element = wealth_cycle[day_master_element]
            officer_cycle = {"wood": "metal", "fire": "water", "earth": "wood", "metal": "fire", "water": "earth"}
            officer_element = officer_cycle[day_master_element]
            xi_yong_shen = [child_element, wealth_element, officer_element]
            ji_shen = [mother_element, day_master_element]
        else:
            # Weak day master needs supporting (Mother/Resource, Self/Sibling)
            xi_yong_shen = [mother_element, day_master_element]
            wealth_cycle = {"wood": "earth", "fire": "metal", "earth": "water", "metal": "wood", "water": "fire"}
            officer_cycle = {"wood": "metal", "fire": "water", "earth": "wood", "metal": "fire", "water": "earth"}
            child_element = cls.GENERATING_CYCLE[day_master_element]
            ji_shen = [officer_cycle[day_master_element], wealth_cycle[day_master_element], child_element]

        return {
            "pillars": pillars,
            "day_master": day_gan,
            "day_master_element": day_master_element,
            "strength": strength,
            "scores": {k: round(v, 2) for k, v in scores.items()},
            "percentages": percentages,
            "lacking_elements": lacking,
            "xi_yong_shen": xi_yong_shen,
            "ji_shen": ji_shen,
            "lunar_text": lunar.toString(),
        }

    # --- 3. Hetu Numerology Reference ---

    @classmethod
    def get_hetu_element(cls, number: int) -> str:
        """Map any integer to its Hetu Five Element via last digit (mod 10)."""
        tail = abs(number) % 10
        return cls.HETU_TAIL_TO_ELEMENT[tail]

    @classmethod
    def partition_numbers_by_element(cls, min_val: int, max_val: int) -> Dict[str, List[int]]:
        """Partition integer range into 5 Wuxing subsets."""
        partition: Dict[str, List[int]] = {
            "wood": [], "fire": [], "earth": [], "metal": [], "water": []
        }
        for n in range(min_val, max_val + 1):
            elem = cls.get_hetu_element(n)
            partition[elem].append(n)
        return partition

    # --- 4. Big Data Statistics Oracle ---

    @classmethod
    def calculate_number_frequency(cls, count_in_draws: int, total_draws: int) -> float:
        """Theoretical occurrence frequency."""
        if total_draws <= 0:
            return 0.0
        return round(count_in_draws / total_draws, 4)

    @classmethod
    def classify_hot_cold(
        cls,
        window_count: int,
        window_size: int,
        zone_pick_count: int,
        zone_pool_size: int,
        hot_threshold_factor: float = 1.25,
        cold_threshold_factor: float = 0.75,
    ) -> str:
        """
        Classify a ball as 'hot', 'warm', or 'cold' within a sliding window.
        Theoretical expected count E_W = W * (zone_pick_count / zone_pool_size).
        """
        expected = window_size * (zone_pick_count / zone_pool_size)
        if window_count >= hot_threshold_factor * expected:
            return "hot"
        elif window_count <= cold_threshold_factor * expected:
            return "cold"
        return "warm"

    @classmethod
    def calculate_average_omission(cls, total_draws: int, appearance_count: int) -> float:
        """
        Closed-form formula for average omission:
        g_avg = (N - k) / (k + 1)
        where N = total draws, k = total appearance count.
        """
        if appearance_count < 0:
            return 0.0
        return round((total_draws - appearance_count) / (appearance_count + 1), 2)

    @classmethod
    def calculate_desire_index(cls, current_omission: int, avg_omission: float) -> float:
        """Desire index = current_omission / avg_omission."""
        if avg_omission <= 0:
            return 0.0
        return round(current_omission / avg_omission, 2)
