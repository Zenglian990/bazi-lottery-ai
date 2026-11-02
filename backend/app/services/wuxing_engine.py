"""Wuxing energy quantification, 110-point positional weighting, and Xi Yong Shen diagnostic engine."""
from typing import Dict, List, Tuple, Any, Optional

# Heavenly Stems to Five Elements
STEM_ELEMENTS: Dict[str, str] = {
    "甲": "wood", "乙": "wood",
    "丙": "fire", "丁": "fire",
    "戊": "earth", "己": "earth",
    "庚": "metal", "辛": "metal",
    "壬": "water", "癸": "water",
}

# 12 Earthly Branches hidden stems (人元藏干) and energy ratios
BRANCH_HIDDEN_STEMS: Dict[str, List[Tuple[str, float]]] = {
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

# 110-point Positional Weight Matrix
# Month branch (月令) holds the dominant seasonal weight of 35 points
PILLAR_WEIGHTS: Dict[str, float] = {
    "year_stem": 10.0,
    "year_branch": 10.0,
    "month_stem": 12.0,
    "month_branch": 35.0,
    "day_stem": 12.0,
    "day_branch": 15.0,
    "hour_stem": 8.0,
    "hour_branch": 8.0,
}

# Five Elements Generating Cycle (生)
GENERATING_CYCLE: Dict[str, str] = {
    "wood": "fire",
    "fire": "earth",
    "earth": "metal",
    "metal": "water",
    "water": "wood",
}

# Five Elements Mother Element (生日主之印星)
MOTHER_ELEMENT: Dict[str, str] = {
    "wood": "water",
    "fire": "wood",
    "earth": "fire",
    "metal": "earth",
    "water": "metal",
}

# Wealth Cycle (我克为财)
WEALTH_CYCLE: Dict[str, str] = {
    "wood": "earth",
    "fire": "metal",
    "earth": "water",
    "metal": "wood",
    "water": "fire",
}

# Officer / Restraining Cycle (克我为官杀)
OFFICER_CYCLE: Dict[str, str] = {
    "wood": "metal",
    "fire": "water",
    "earth": "wood",
    "metal": "fire",
    "water": "earth",
}

# Chinese and English element mappings
ELEMENT_NAMES_ZH: Dict[str, str] = {
    "wood": "木",
    "fire": "火",
    "earth": "土",
    "metal": "金",
    "water": "水",
}

ZH_TO_ELEMENT: Dict[str, str] = {v: k for k, v in ELEMENT_NAMES_ZH.items()}


def analyze_wuxing_energy(
    year_stem: str,
    year_branch: str,
    month_stem: str,
    month_branch: str,
    day_stem: str,
    day_branch: str,
    hour_stem: str,
    hour_branch: str,
) -> Dict[str, Any]:
    """
    Quantifies the 110-point Wuxing energy distribution and diagnoses Day Master strength,
    Xi Yong Shen, Ji Shen, and lacking elements.
    """
    scores = {"wood": 0.0, "fire": 0.0, "earth": 0.0, "metal": 0.0, "water": 0.0}

    # Stems contribution
    scores[STEM_ELEMENTS[year_stem]] += PILLAR_WEIGHTS["year_stem"]
    scores[STEM_ELEMENTS[month_stem]] += PILLAR_WEIGHTS["month_stem"]
    scores[STEM_ELEMENTS[day_stem]] += PILLAR_WEIGHTS["day_stem"]
    scores[STEM_ELEMENTS[hour_stem]] += PILLAR_WEIGHTS["hour_stem"]

    # Branches contribution with 3-qi hidden stem breakdown
    for stem_name, ratio in BRANCH_HIDDEN_STEMS[year_branch]:
        scores[STEM_ELEMENTS[stem_name]] += PILLAR_WEIGHTS["year_branch"] * ratio

    for stem_name, ratio in BRANCH_HIDDEN_STEMS[month_branch]:
        scores[STEM_ELEMENTS[stem_name]] += PILLAR_WEIGHTS["month_branch"] * ratio

    for stem_name, ratio in BRANCH_HIDDEN_STEMS[day_branch]:
        scores[STEM_ELEMENTS[stem_name]] += PILLAR_WEIGHTS["day_branch"] * ratio

    for stem_name, ratio in BRANCH_HIDDEN_STEMS[hour_branch]:
        scores[STEM_ELEMENTS[stem_name]] += PILLAR_WEIGHTS["hour_branch"] * ratio

    total_score = sum(scores.values())
    percentages = {k: round(v / total_score, 4) for k, v in scores.items()}

    # Day Master attributes
    day_master_element = STEM_ELEMENTS[day_stem]
    mother_element = MOTHER_ELEMENT[day_master_element]

    # Self-party energy (同党: 比劫 + 印枭)
    self_party_score = scores[day_master_element] + scores[mother_element]
    strength_ratio = round(self_party_score / total_score, 4)
    is_strong = self_party_score >= 55.0  # 55.0 / 110.0 = 50%
    strength = "身强" if is_strong else "身弱"

    # Lacking elements: score < 5.0 points
    lacking_elements = [elem for elem, sc in scores.items() if sc < 5.0]

    # Xi Yong Shen and Ji Shen determination
    child_element = GENERATING_CYCLE[day_master_element]
    wealth_element = WEALTH_CYCLE[day_master_element]
    officer_element = OFFICER_CYCLE[day_master_element]

    if is_strong:
        # Strong day master favors draining and restraining: 食伤, 财星, 官杀
        xi_yong_shen = [child_element, wealth_element, officer_element]
        ji_shen = [mother_element, day_master_element]
    else:
        # Weak day master favors supporting and boosting: 印枭, 比劫
        xi_yong_shen = [mother_element, day_master_element]
        ji_shen = [officer_element, wealth_element, child_element]

    lacking_zh = [ELEMENT_NAMES_ZH[e] for e in lacking_elements]
    xi_yong_zh = [ELEMENT_NAMES_ZH[e] for e in xi_yong_shen]
    ji_shen_zh = [ELEMENT_NAMES_ZH[e] for e in ji_shen]

    return {
        "scores": {k: round(v, 2) for k, v in scores.items()},
        "percentages": percentages,
        "total_score": round(total_score, 2),
        "day_master": day_stem,
        "day_master_element": day_master_element,
        "strength": strength,
        "strength_ratio": strength_ratio,
        "self_party_score": round(self_party_score, 2),
        "lacking_elements": lacking_elements,
        "xi_yong_shen": xi_yong_shen,
        "ji_shen": ji_shen,
        "lacking_elements_zh": lacking_zh,
        "xi_yong_shen_zh": xi_yong_zh,
        "ji_shen_zh": ji_shen_zh,
    }
