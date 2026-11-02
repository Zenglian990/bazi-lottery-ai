"""Hetu numerology mapping, 5-tier lucky number pooling, and A-Res weighted lottery recommendation engine."""
import math
import random
from typing import Dict, List, Tuple, Any, Optional

from backend.app.services.wuxing_engine import (
    ELEMENT_NAMES_ZH,
    ZH_TO_ELEMENT,
)
from backend.app.services.lottery_rules import get_lottery_rule
from backend.app.db.database import get_draws

# Classical Hetu (河图) Ten Numbers Mapping: Tail Modulo 10 -> Element
# 天一生水 地六成之 (1, 6 -> Water)
# 地二生火 天七成之 (2, 7 -> Fire)
# 天三生木 地八成之 (3, 8 -> Wood)
# 地四生金 天九成之 (4, 9 -> Metal)
# 天五生土 地十成之 (5, 0 -> Earth)
HETU_TAIL_TO_ELEMENT: Dict[int, str] = {
    1: "water", 6: "water",
    2: "fire", 7: "fire",
    3: "wood", 8: "wood",
    4: "metal", 9: "metal",
    5: "earth", 0: "earth",
}

ELEMENT_TO_TAILS: Dict[str, List[int]] = {
    "water": [1, 6],
    "fire": [2, 7],
    "wood": [3, 8],
    "metal": [4, 9],
    "earth": [5, 0],
}

# 5-Tier Lucky Multipliers
TIER_WEIGHTS = {
    "tier1_primary": 1.00,       # 核心第一用神
    "tier2_supporting": 0.80,    # 次吉生扶用神
    "tier3_deficiency": 0.60,    # 缺失五行调候平衡
    "tier4_neutral": 0.40,       # 中和平和数理
    "tier5_taboo": 0.15,         # 忌神避讳偏冷
}

# Strategy Presets (w_bazi, w_freq, w_omission, omission_mode)
STRATEGY_PRESETS = {
    "bazi": (0.60, 0.25, 0.15, "balanced"),
    "bazi_priority": (0.60, 0.25, 0.15, "balanced"),
    "balanced": (0.35, 0.40, 0.25, "balanced"),
    "hot_chase": (0.25, 0.55, 0.20, "hot"),
    "cold_reversal": (0.25, 0.20, 0.55, "cold"),
}


def get_hetu_element(number: int) -> str:
    """Map any integer to its Hetu Five Element via last digit (mod 10)."""
    tail = abs(number) % 10
    return HETU_TAIL_TO_ELEMENT[tail]


def partition_numbers_by_element(min_val: int, max_val: int) -> Dict[str, List[int]]:
    """Partition integer range [min_val, max_val] into 5 Wuxing subsets."""
    partition: Dict[str, List[int]] = {
        "wood": [], "fire": [], "earth": [], "metal": [], "water": []
    }
    for n in range(min_val, max_val + 1):
        elem = get_hetu_element(n)
        partition[elem].append(n)
    return partition


def compute_bazi_score(
    number: int,
    xi_yong_shen: List[str],
    lacking_elements: List[str],
    ji_shen: List[str],
) -> Tuple[float, str]:
    """
    Calculate 5-tier Bazi score and tier classification for a candidate ball number.
    """
    elem = get_hetu_element(number)
    primary_xi = xi_yong_shen[0] if xi_yong_shen else ""
    secondary_xi = xi_yong_shen[1:] if len(xi_yong_shen) > 1 else []

    if elem == primary_xi:
        return TIER_WEIGHTS["tier1_primary"], "Tier 1"
    elif elem in secondary_xi:
        return TIER_WEIGHTS["tier2_supporting"], "Tier 2"
    elif elem in lacking_elements:
        return TIER_WEIGHTS["tier3_deficiency"], "Tier 3"
    elif elem in ji_shen:
        return TIER_WEIGHTS["tier5_taboo"], "Tier 5"
    else:
        return TIER_WEIGHTS["tier4_neutral"], "Tier 4"


def a_res_weighted_sample_without_replacement(
    candidates_with_weights: List[Tuple[int, float]],
    k: int,
) -> List[int]:
    """
    Efraimidis-Spirakis (A-Res) Weighted Reservoir Sampling Without Replacement.
    Guarantees exactly k distinct elements sampled proportional to weights, returned ascending sorted.
    """
    if len(candidates_with_weights) < k:
        raise ValueError(f"Candidate pool size ({len(candidates_with_weights)}) < requested count ({k})")

    keys = []
    for item, w in candidates_with_weights:
        weight = max(float(w), 1e-6)
        u = max(random.random(), 1e-12)
        key = u ** (1.0 / weight)
        keys.append((key, item))

    keys.sort(key=lambda x: x[0], reverse=True)
    selected = [item for _, item in keys[:k]]
    return sorted(selected)


def weighted_choice_with_replacement(
    candidates_with_weights: List[Tuple[int, float]],
    k: int,
) -> List[int]:
    """
    Weighted choice with replacement for positional digit lotteries (e.g. FC3D, QXC front digits).
    Preserves generation order (not sorted).
    """
    items = [item for item, _ in candidates_with_weights]
    weights = [max(float(w), 1e-6) for _, w in candidates_with_weights]
    return random.choices(items, weights=weights, k=k)


def resolve_strategy_weights(
    strategy: Optional[str] = "balanced",
    w_bazi: Optional[float] = None,
    w_freq: Optional[float] = None,
    w_omission: Optional[float] = None,
) -> Tuple[float, float, float, str]:
    """
    Resolve and normalize tri-factor weights (w_bazi, w_freq, w_omission) and omission mode.
    """
    preset_name = (strategy or "balanced").lower()
    default_wb, default_wf, default_wo, om_mode = STRATEGY_PRESETS.get(
        preset_name, STRATEGY_PRESETS["balanced"]
    )

    has_custom = w_bazi is not None or w_freq is not None or w_omission is not None
    if has_custom:
        wb = float(w_bazi if w_bazi is not None else 0.0)
        wf = float(w_freq if w_freq is not None else 0.0)
        wo = float(w_omission if w_omission is not None else 0.0)
        total = wb + wf + wo
        if total > 1e-6:
            return wb / total, wf / total, wo / total, om_mode
        else:
            # Fallback for all-zero weights: uniform weighting
            return 0.3333, 0.3333, 0.3334, om_mode

    return default_wb, default_wf, default_wo, om_mode


def get_historical_feature_maps(
    lottery_type: str,
    zone_index: int = 0,
) -> Tuple[Dict[int, float], Dict[int, float]]:
    """
    Load recent 100 draws from SQLite database and compute:
    1. Appearance counts C(n)
    2. Current omission gaps G(n)
    """
    freq_map: Dict[int, float] = {}
    omission_map: Dict[int, float] = {}

    try:
        draws = get_draws(lottery_type, limit=100)
    except Exception:
        draws = []

    if not draws:
        return freq_map, omission_map

    # Extract zone balls per draw (newest is index 0)
    for idx, d in enumerate(draws):
        if isinstance(d, dict):
            raw_balls = d.get("primary_numbers", []) if zone_index == 0 else (d.get("secondary_numbers") or [])
        else:
            raw_balls = getattr(d, "primary_numbers", []) if zone_index == 0 else (getattr(d, "secondary_numbers", None) or [])
        if raw_balls is None:
            continue
        balls_list = [raw_balls] if isinstance(raw_balls, (str, int)) else list(raw_balls)
        for b_str in balls_list:
            try:
                num = int(str(b_str).strip())
            except ValueError:
                continue
            freq_map[num] = freq_map.get(num, 0) + 1
            if num not in omission_map:
                omission_map[num] = idx

    # If any number hasn't appeared in recent draws, current omission is at least total draws
    total_draws = len(draws)
    for num in freq_map:
        if num not in omission_map:
            omission_map[num] = total_draws

    return freq_map, omission_map


def calculate_candidate_weights(
    min_val: int,
    max_val: int,
    xi_yong_shen: List[str],
    lacking_elements: List[str],
    ji_shen: List[str],
    w_bazi: float,
    w_freq: float,
    w_omission: float,
    omission_mode: str,
    lottery_type: str,
    zone_index: int,
) -> List[Tuple[int, float]]:
    """
    Compute hybrid tri-factor weights for all numbers in [min_val, max_val].
    """
    freq_map, omission_map = get_historical_feature_maps(lottery_type, zone_index)

    # Calculate min and max for normalization
    pool = list(range(min_val, max_val + 1))
    freqs = [freq_map.get(n, 0.0) for n in pool]
    min_f, max_f = min(freqs), max(freqs)
    span_f = max(max_f - min_f, 1e-6)

    omissions = [omission_map.get(n, 10.0) for n in pool]
    min_o, max_o = min(omissions), max(omissions)
    span_o = max(max_o - min_o, 1e-6)

    candidates_with_weights: List[Tuple[int, float]] = []
    for n in pool:
        # 1. Bazi Score
        bazi_score, _ = compute_bazi_score(n, xi_yong_shen, lacking_elements, ji_shen)

        # 2. Freq Score (normalized 0.0 - 1.0)
        c_n = freq_map.get(n, 0.0)
        freq_score = (c_n - min_f) / span_f if span_f > 0 else 0.5

        # 3. Omission Score (normalized according to omission_mode)
        g_n = omission_map.get(n, 10.0)
        if omission_mode == "hot":
            # smaller omission gets higher score
            omission_score = (max_o - g_n) / span_o if span_o > 0 else 0.5
        elif omission_mode == "cold":
            # larger omission gets higher score (cold reversal)
            omission_score = (g_n - min_o) / span_o if span_o > 0 else 0.5
        else:
            omission_score = 0.5

        # Combined weight
        weight = w_bazi * bazi_score + w_freq * freq_score + w_omission * omission_score
        weight = max(float(weight), 1e-4)
        candidates_with_weights.append((n, weight))

    return candidates_with_weights


def build_wuxing_distribution(numbers: List[int]) -> Dict[str, int]:
    """Calculate counts of each of the 5 elements in the chosen numbers."""
    dist = {"wood": 0, "fire": 0, "earth": 0, "metal": 0, "water": 0}
    for n in numbers:
        elem = get_hetu_element(n)
        dist[elem] += 1
    return dist


def generate_score_rationale(
    lottery_type: str,
    strategy: str,
    chosen_primary: List[int],
    xi_yong_shen: List[str],
    w_bazi: float,
    w_freq: float,
    w_omission: float,
) -> str:
    """Generate professional Chinese astrological & statistical rationale string."""
    primary_xi = xi_yong_shen[0] if xi_yong_shen else "wood"
    xi_zh = [ELEMENT_NAMES_ZH.get(e, e) for e in xi_yong_shen]
    xi_tails = []
    for e in xi_yong_shen[:2]:
        tails = [str(t) for t in ELEMENT_TO_TAILS.get(e, [])]
        xi_tails.append(f"{ELEMENT_NAMES_ZH.get(e, e)}({'与'.join(tails)})")

    tails_text = "、".join(xi_tails)

    # Calculate match count with Xi Yong Shen
    matches = sum(1 for n in chosen_primary if get_hetu_element(n) in xi_yong_shen)
    match_pct = round((matches / max(len(chosen_primary), 1)) * 100, 1)

    strategy_desc = {
        "bazi": "命理吉数优先",
        "bazi_priority": "命理吉数优先",
        "balanced": "大数据平衡稳健",
        "hot_chase": "乘胜追热连号",
        "cold_reversal": "大遗漏极冷翻转",
    }.get(strategy.lower(), "综合三元混合")

    return f"命中喜用神{tails_text}契合度 {match_pct}%，结合{strategy_desc}模型(八字:{w_bazi:.2f}, 频次:{w_freq:.2f}, 遗漏:{w_omission:.2f})加权生成"


def recommend_lottery_tickets(
    bazi_analysis: Dict[str, Any],
    lottery_type: str = "ssq",
    count: int = 1,
    strategy: Optional[str] = "balanced",
    w_bazi: Optional[float] = None,
    w_freq: Optional[float] = None,
    w_omission: Optional[float] = None,
    deterministic: bool = False,
) -> Dict[str, Any]:
    """
    Main recommendation engine producing legal, ascending, non-duplicate ticket recommendations.
    """
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise ValueError(f"Unsupported lottery type: '{lottery_type}'")

    p_rule = rule.zones[0]
    s_rule = rule.zones[1] if len(rule.zones) > 1 else None

    xi_yong_shen = bazi_analysis.get("xi_yong_shen", ["wood"])
    lacking_elements = bazi_analysis.get("lacking_elements", [])
    ji_shen = bazi_analysis.get("ji_shen", ["metal"])

    resolved_wb, resolved_wf, resolved_wo, om_mode = resolve_strategy_weights(
        strategy=strategy,
        w_bazi=w_bazi,
        w_freq=w_freq,
        w_omission=w_omission,
    )

    # Primary zone candidates with weights
    p_weights = calculate_candidate_weights(
        min_val=p_rule.min_value,
        max_val=p_rule.max_value,
        xi_yong_shen=xi_yong_shen,
        lacking_elements=lacking_elements,
        ji_shen=ji_shen,
        w_bazi=resolved_wb,
        w_freq=resolved_wf,
        w_omission=resolved_wo,
        omission_mode=om_mode,
        lottery_type=rule.lottery_type.value,
        zone_index=0,
    )

    # Secondary zone candidates with weights if applicable
    s_weights = None
    if s_rule:
        s_weights = calculate_candidate_weights(
            min_val=s_rule.min_value,
            max_val=s_rule.max_value,
            xi_yong_shen=xi_yong_shen,
            lacking_elements=lacking_elements,
            ji_shen=ji_shen,
            w_bazi=resolved_wb,
            w_freq=resolved_wf,
            w_omission=resolved_wo,
            omission_mode=om_mode,
            lottery_type=rule.lottery_type.value,
            zone_index=1,
        )

    recommendations = []
    for _ in range(count):
        # Sample Primary Zone
        if deterministic:
            # Deterministic: sort by weight descending
            sorted_p = sorted(p_weights, key=lambda x: x[1], reverse=True)
            chosen_p = sorted([x[0] for x in sorted_p[:p_rule.count]])
        else:
            if p_rule.unique:
                chosen_p = a_res_weighted_sample_without_replacement(p_weights, p_rule.count)
            else:
                chosen_p = weighted_choice_with_replacement(p_weights, p_rule.count)

        if p_rule.sorted:
            chosen_p.sort()

        p_formatted = [p_rule.format_pattern % v for v in chosen_p]

        # Sample Secondary Zone
        s_formatted = None
        chosen_s: List[int] = []
        if s_rule and s_weights:
            if deterministic:
                sorted_s = sorted(s_weights, key=lambda x: x[1], reverse=True)
                chosen_s = sorted([x[0] for x in sorted_s[:s_rule.count]])
            else:
                if s_rule.unique:
                    chosen_s = a_res_weighted_sample_without_replacement(s_weights, s_rule.count)
                else:
                    chosen_s = weighted_choice_with_replacement(s_weights, s_rule.count)

            if s_rule.sorted:
                chosen_s.sort()

            s_formatted = [s_rule.format_pattern % v for v in chosen_s]

        # Ticket Wuxing Distribution
        all_ticket_nums = chosen_p + chosen_s
        wuxing_dist = build_wuxing_distribution(all_ticket_nums)

        # Rationale string
        rationale = generate_score_rationale(
            lottery_type=rule.lottery_type.value,
            strategy=strategy or "balanced",
            chosen_primary=chosen_p,
            xi_yong_shen=xi_yong_shen,
            w_bazi=resolved_wb,
            w_freq=resolved_wf,
            w_omission=resolved_wo,
        )

        recommendations.append({
            "primary_numbers": p_formatted,
            "secondary_numbers": s_formatted,
            "wuxing_distribution": wuxing_dist,
            "score_rationale": rationale,
        })

    return {
        "lottery_type": rule.lottery_type.value,
        "strategy": strategy or "balanced",
        "recommendations": recommendations,
        "bazi_summary": {
            "day_master": bazi_analysis.get("day_master", ""),
            "day_master_element": bazi_analysis.get("day_master_element", ""),
            "strength": bazi_analysis.get("strength", ""),
            "xi_yong_shen": bazi_analysis.get("xi_yong_shen", []),
            "ji_shen": bazi_analysis.get("ji_shen", []),
            "lacking_elements": bazi_analysis.get("lacking_elements", []),
        },
    }
