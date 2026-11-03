"""Hetu numerology mapping, 5-tier lucky number pooling, and A-Res weighted lottery recommendation engine."""
import math
import random
from datetime import datetime
from typing import Dict, List, Tuple, Any, Optional
from lunar_python import Solar, Lunar

from backend.app.services.wuxing_engine import (
    ELEMENT_NAMES_ZH,
    ZH_TO_ELEMENT,
    STEM_ELEMENTS,
    BRANCH_HIDDEN_STEMS,
    MOTHER_ELEMENT,
)
from backend.app.services.lottery_rules import get_lottery_rule
from backend.app.db.database import get_draws
from backend.app.services.name_numerology import analyze_name_numerology
from backend.app.services.yijing_rag_service import match_classics_evidence

# Element normalization mapping between Chinese and English keys
ZH_TO_EN_ELEMENT: Dict[str, str] = {
    "木": "wood", "火": "fire", "土": "earth", "金": "metal", "水": "water",
    "wood": "wood", "fire": "fire", "earth": "earth", "metal": "metal", "water": "water",
}

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
    Safely normalizes both Chinese ('金','木','水','火','土') and English ('metal','wood',...) keys.
    """
    elem = get_hetu_element(number)
    xi_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in xi_yong_shen]
    lacking_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in lacking_elements]
    ji_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in ji_shen]

    primary_xi = xi_norm[0] if xi_norm else ""
    secondary_xi = xi_norm[1:] if len(xi_norm) > 1 else []

    if elem == primary_xi:
        return TIER_WEIGHTS["tier1_primary"], "Tier 1"
    elif elem in secondary_xi:
        return TIER_WEIGHTS["tier2_supporting"], "Tier 2"
    elif elem in lacking_norm:
        return TIER_WEIGHTS["tier3_deficiency"], "Tier 3"
    elif elem in ji_norm:
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
    harmonious_elements: Optional[List[str]] = None,
    name_lucky_numbers: Optional[List[int]] = None,
) -> List[Tuple[int, float]]:
    """
    Compute hybrid tri-factor weights for all numbers in [min_val, max_val].
    Includes today's LiuRi cosmic resonance (+20%) and personal name numerology bonuses.
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

    norm_harmony = [ZH_TO_EN_ELEMENT.get(e, e) for e in (harmonious_elements or [])]
    lucky_set = set(name_lucky_numbers or [])

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

        # 4. LiuRi Celestial Energy Resonance Boost (+20% for harmonious elements)
        n_elem = get_hetu_element(n)
        if norm_harmony and n_elem in norm_harmony:
            weight *= 1.20

        # 5. Personal Name Numerology Bonus (+15% for name lucky numbers or tail matching)
        if lucky_set and (n in lucky_set or (n % 10 or 10) in lucky_set):
            weight *= 1.15

        candidates_with_weights.append((n, round(weight, 5)))

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
    user_name: Optional[str] = None,
    today_harmony_text: Optional[str] = None,
) -> str:
    """Generate professional Chinese astrological & statistical rationale string."""
    xi_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in xi_yong_shen]
    xi_zh = [ELEMENT_NAMES_ZH.get(e, e) for e in xi_norm]
    xi_tails = []
    for en_e, zh_e in zip(xi_norm[:2], xi_zh[:2]):
        tails = [str(t) for t in ELEMENT_TO_TAILS.get(en_e, [])]
        xi_tails.append(f"{zh_e}({'与'.join(tails)})")

    tails_text = "、".join(xi_tails) if xi_tails else "用神"

    # Calculate match count with Xi Yong Shen
    matches = sum(1 for n in chosen_primary if get_hetu_element(n) in xi_norm)
    match_pct = round((matches / max(len(chosen_primary), 1)) * 100, 1)

    strategy_desc = {
        "bazi": "命理吉数优先",
        "bazi_priority": "命理吉数优先",
        "balanced": "大数据平衡稳健",
        "hot_chase": "乘胜追热连号",
        "cold_reversal": "大遗漏极冷翻转",
    }.get(strategy.lower(), "综合三元混合")

    prefix = f"缘主【{user_name}】" if user_name else ""
    harmony_clause = f"，{today_harmony_text}" if today_harmony_text else ""

    return f"{prefix}命中喜用神{tails_text}契合度 {match_pct}%{harmony_clause}，结合{strategy_desc}模型(八字:{w_bazi:.2f}, 频次:{w_freq:.2f}, 遗漏:{w_omission:.2f})加权生成"


def recommend_lottery_tickets(
    bazi_analysis: Dict[str, Any],
    lottery_type: str = "ssq",
    count: int = 1,
    strategy: Optional[str] = "balanced",
    w_bazi: Optional[float] = None,
    w_freq: Optional[float] = None,
    w_omission: Optional[float] = None,
    deterministic: bool = False,
    user_name: Optional[str] = None,
    shichen: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Main recommendation engine producing legal, ascending, non-duplicate ticket recommendations.
    Supports multi-bet cross-ticket deduplication, today's LiuRi cosmic resonance (+20%),
    Chinese name numerology, and unified copy text.
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

    # 1. Today's LiuRi Celestial Energy Calculation
    now = datetime.now()
    solar_now = Solar.fromDate(now)
    lunar_now = solar_now.getLunar()
    ganzhi_year = lunar_now.getYearInGanZhi()
    ganzhi_month = lunar_now.getMonthInGanZhi()
    ganzhi_day = lunar_now.getDayInGanZhi()
    ganzhi_hour = lunar_now.getTimeInGanZhi()
    day_stem = lunar_now.getDayGan()
    day_branch = lunar_now.getDayZhi()
    day_stem_elem = STEM_ELEMENTS.get(day_stem, "metal")
    day_branch_hiddens = BRANCH_HIDDEN_STEMS.get(day_branch, [("戊", 1.0)])
    day_branch_elem = STEM_ELEMENTS.get(day_branch_hiddens[0][0], "earth")

    # Harmonious elements with user's Xi Yong Shen
    user_xi_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in xi_yong_shen]
    active_today_elems = {day_stem_elem, day_branch_elem}
    generating_elems = {MOTHER_ELEMENT.get(e, "") for e in user_xi_norm}

    harmonious_elems = list(active_today_elems.intersection(user_xi_norm).union(
        active_today_elems.intersection(generating_elems)
    ))
    if not harmonious_elems:
        harmonious_elems = [day_stem_elem, day_branch_elem]

    today_harmony_text = (
        f"融合今日流日{ganzhi_day}({ELEMENT_NAMES_ZH.get(day_stem_elem)}{ELEMENT_NAMES_ZH.get(day_branch_elem)})"
        f"气运共振(+20%)"
    )

    today_liuri_info = {
        "solar_date": solar_now.toYmd(),
        "lunar_date": lunar_now.toString(),
        "ganzhi_day": ganzhi_day,
        "day_nayin": lunar_now.getDayNaYin(),
        "cai_shen": lunar_now.getDayPositionCai(),
        "xi_shen": lunar_now.getDayPositionXi(),
        "day_yi": lunar_now.getDayYi()[:5],
        "day_ji": lunar_now.getDayJi()[:5],
        "harmonious_elements": [ELEMENT_NAMES_ZH.get(e, e) for e in harmonious_elems],
    }

    # 2. Chinese Name Numerology
    name_lucky_numbers = None
    if user_name:
        name_info = analyze_name_numerology(user_name)
        name_lucky_numbers = name_info.get("name_lucky_numbers", [])

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
        harmonious_elements=harmonious_elems,
        name_lucky_numbers=name_lucky_numbers,
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
            harmonious_elements=harmonious_elems,
            name_lucky_numbers=name_lucky_numbers,
        )

    seen_tickets = set()
    recommendations = []
    max_attempts = max(count * 60, 400)
    attempts = 0

    while len(recommendations) < count and attempts < max_attempts:
        attempts += 1

        # Sample Primary Zone
        if deterministic and count == 1:
            # Deterministic single ticket: strictly top-ranked
            sorted_p = sorted(p_weights, key=lambda x: x[1], reverse=True)
            chosen_p = sorted([x[0] for x in sorted_p[:p_rule.count]])
        elif deterministic and count > 1:
            # Deterministic multi-ticket: deterministic pseudo-random reservoir
            rng_p = random.Random(attempts * 10007 + int(resolved_wb * 100) + int(resolved_wf * 100))
            keys = []
            for item, w in p_weights:
                weight = max(float(w), 1e-6)
                u = max(rng_p.random(), 1e-12)
                key = u ** (1.0 / weight)
                keys.append((key, item))
            keys.sort(key=lambda x: x[0], reverse=True)
            if p_rule.unique:
                chosen_p = sorted([item for _, item in keys[:p_rule.count]])
            else:
                chosen_p = [keys[i % len(keys)][1] for i in range(p_rule.count)]
        else:
            # Non-deterministic A-Res reservoir sampling
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
            if deterministic and count == 1:
                sorted_s = sorted(s_weights, key=lambda x: x[1], reverse=True)
                chosen_s = sorted([x[0] for x in sorted_s[:s_rule.count]])
            elif deterministic and count > 1:
                rng_s = random.Random(attempts * 99991 + int(resolved_wo * 100))
                keys_s = []
                for item, w in s_weights:
                    weight = max(float(w), 1e-6)
                    u = max(rng_s.random(), 1e-12)
                    key = u ** (1.0 / weight)
                    keys_s.append((key, item))
                keys_s.sort(key=lambda x: x[0], reverse=True)
                if s_rule.unique:
                    chosen_s = sorted([item for _, item in keys_s[:s_rule.count]])
                else:
                    chosen_s = [keys_s[i % len(keys_s)][1] for i in range(s_rule.count)]
            else:
                if s_rule.unique:
                    chosen_s = a_res_weighted_sample_without_replacement(s_weights, s_rule.count)
                else:
                    chosen_s = weighted_choice_with_replacement(s_weights, s_rule.count)

            if s_rule.sorted:
                chosen_s.sort()

            s_formatted = [s_rule.format_pattern % v for v in chosen_s]

        # Cross-ticket deduplication
        ticket_key = (tuple(p_formatted), tuple(s_formatted) if s_formatted else ())
        if ticket_key in seen_tickets:
            continue
        seen_tickets.add(ticket_key)

        bet_idx = len(recommendations) + 1
        formatted_ticket = " ".join(p_formatted)
        if s_formatted:
            formatted_ticket += " + " + " ".join(s_formatted)

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
            user_name=user_name,
            today_harmony_text=today_harmony_text,
        )

        evidence = match_classics_evidence(
            primary_numbers=p_formatted,
            favorable_elements=xi_yong_shen,
            ticket_index=len(recommendations),
        )

        recommendations.append({
            "bet_index": bet_idx,
            "primary_numbers": p_formatted,
            "secondary_numbers": s_formatted,
            "formatted_ticket": formatted_ticket,
            "wuxing_distribution": wuxing_dist,
            "score_rationale": rationale,
            "classics_evidence": evidence.model_dump() if hasattr(evidence, "model_dump") else evidence.dict(),
        })

    # Assemble unified copy_text for one-click copying
    lines = [f"【曾练彩票助手 - {rule.chinese_name} {len(recommendations)}注机选推荐】"]
    if user_name:
        lines.append(f"缘主姓名：{user_name}")
    lines.append(f"今日流日：{ganzhi_year}年 {ganzhi_month}月 {ganzhi_day}日 {ganzhi_hour}")
    lines.append(f"推演策略：{strategy or 'balanced'}")
    lines.append("-" * 38)
    for rec in recommendations:
        lines.append(f"第{rec['bet_index']:02d}注: {rec['formatted_ticket']}")
    lines.append("-" * 38)
    lines.append("祝您财星高照，吉祥如意！(彩票纯属概率游戏，请理性购彩)")
    copy_text = "\n".join(lines)

    return {
        "lottery_type": rule.lottery_type.value,
        "strategy": strategy or "balanced",
        "recommendations": recommendations,
        "copy_text": copy_text,
        "user_name": user_name,
        "today_liuri": today_liuri_info,
        "bazi_summary": {
            "day_master": bazi_analysis.get("day_master", ""),
            "day_master_element": bazi_analysis.get("day_master_element", ""),
            "strength": bazi_analysis.get("strength", ""),
            "xi_yong_shen": bazi_analysis.get("xi_yong_shen", []),
            "ji_shen": bazi_analysis.get("ji_shen", []),
            "lacking_elements": bazi_analysis.get("lacking_elements", []),
        },
    }
