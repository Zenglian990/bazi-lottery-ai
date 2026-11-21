"""Hetu numerology mapping, 5-tier lucky number pooling, and A-Res weighted lottery recommendation engine."""
from datetime import datetime, timedelta, timezone
import itertools
import math
import random
from typing import Any, Dict, List, Optional, Tuple
from lunar_python import Solar

from backend.app.db.database import get_draws
from backend.app.services.lottery_rules import get_lottery_rule
from backend.app.services.name_numerology import analyze_name_numerology
from backend.app.services.wuxing_engine import (
    BRANCH_HIDDEN_STEMS,
    ELEMENT_NAMES_ZH,
    MOTHER_ELEMENT,
    STEM_ELEMENTS,
    ZH_TO_ELEMENT,
)
from backend.app.services.yijing_rag_service import match_classics_evidence

CHINA_TZ = timezone(timedelta(hours=8))

# 中英文五行键归一化映射
ZH_TO_EN_ELEMENT: Dict[str, str] = {
    "木": "wood", "火": "fire", "土": "earth", "金": "metal", "水": "water",
    "wood": "wood", "fire": "fire", "earth": "earth", "metal": "metal", "water": "water",
}

# 经典河图五行尾数映射：1/6水、2/7火、3/8木、4/9金、5/0土
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

# 5 级八字用神梯队权重
TIER_WEIGHTS = {
    "tier1_primary": 1.00,       # 核心第一用神
    "tier2_supporting": 0.80,    # 次吉生扶用神
    "tier3_deficiency": 0.60,    # 缺失五行调候平衡（非忌神）
    "tier4_neutral": 0.40,       # 中和平和数理
    "tier5_taboo": 0.15,         # 忌神避讳偏冷
}

# 策略预设 (w_bazi, w_freq, w_omission, omission_mode)
STRATEGY_PRESETS = {
    "bazi": (0.60, 0.25, 0.15, "balanced"),
    "bazi_priority": (0.60, 0.25, 0.15, "balanced"),
    "balanced": (0.35, 0.40, 0.25, "balanced"),
    "hot_chase": (0.25, 0.55, 0.20, "hot"),
    "cold_reversal": (0.25, 0.20, 0.55, "cold"),
}

STRATEGY_ALIAS_MAP: Dict[str, str] = {
    "bazi": "bazi_priority",
    "bazi_priority": "bazi_priority",
    "bazi_pure": "bazi_priority",
    "命理吉数优先": "bazi_priority",
    "八字优先": "bazi_priority",
    "命理优先": "bazi_priority",
    "八字命理": "bazi_priority",
    "balanced": "balanced",
    "稳健大数据": "balanced",
    "八字五行平衡": "balanced",
    "五行生克平衡": "balanced",
    "五行平衡": "balanced",
    "综合平衡": "balanced",
    "hot_chase": "hot_chase",
    "冷热追号": "hot_chase",
    "热号追随": "hot_chase",
    "热号优先": "hot_chase",
    "cold_reversal": "cold_reversal",
    "极冷号反弹": "cold_reversal",
    "冷号反弹": "cold_reversal",
    "极冷反弹": "cold_reversal",
}

STRATEGY_CANONICAL_ZH: Dict[str, str] = {
    "bazi_priority": "命理吉数优先",
    "bazi": "命理吉数优先",
    "balanced": "稳健大数据",
    "hot_chase": "冷热追号",
    "cold_reversal": "极冷号反弹",
}


def get_hetu_element(number: int) -> str:
    """按河图尾数取模映射号码所属五行。"""
    tail = abs(number) % 10
    return HETU_TAIL_TO_ELEMENT[tail]


def partition_numbers_by_element(min_val: int, max_val: int) -> Dict[str, List[int]]:
    """将号码区间 [min_val, max_val] 按河图五行划分至 5 个集合。"""
    partition: Dict[str, List[int]] = {
        "wood": [], "fire": [], "earth": [], "metal": [], "water": []
    }
    for n in range(min_val, max_val + 1):
        partition[get_hetu_element(n)].append(n)
    return partition


def compute_bazi_score(
    number: int,
    xi_yong_shen: List[str],
    lacking_elements: List[str],
    ji_shen: List[str],
) -> Tuple[float, str]:
    """
    计算候选号码的 5 级八字用神得分。
    注意：忌神优先级高于缺行——忌神缺失为吉，绝不可反向升格为 Tier 3 补强。
    """
    elem = get_hetu_element(number)
    xi_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in xi_yong_shen]
    lacking_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in lacking_elements]
    ji_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in ji_shen]

    primary_xi = xi_norm[0] if xi_norm else ""
    secondary_xi = xi_norm[1:] if len(xi_norm) > 1 else []

    if elem == primary_xi:
        return TIER_WEIGHTS["tier1_primary"], "Tier 1"
    if elem in secondary_xi:
        return TIER_WEIGHTS["tier2_supporting"], "Tier 2"
    if elem in ji_norm:
        return TIER_WEIGHTS["tier5_taboo"], "Tier 5"
    if elem in lacking_norm:
        return TIER_WEIGHTS["tier3_deficiency"], "Tier 3"
    return TIER_WEIGHTS["tier4_neutral"], "Tier 4"


def a_res_weighted_sample_without_replacement(
    candidates_with_weights: List[Tuple[int, float]],
    k: int,
    rng: Optional[random.Random] = None,
) -> List[int]:
    """
    对数空间 Efraimidis-Spirakis (A-Res) 无放回加权蓄水池抽样。
    使用 key = ln(u) / weight（与 u^(1/w) 严格单调等价）彻底消除低权重下的 IEEE 754 浮点下溢。
    """
    if len(candidates_with_weights) < k:
        raise ValueError(f"Candidate pool size ({len(candidates_with_weights)}) < requested count ({k})")
    if k <= 0:
        return []

    r = rng if rng is not None else random
    keys: List[Tuple[float, int]] = []
    for item, w in candidates_with_weights:
        weight = max(float(w), 1e-6)
        # 严格限制 u ∈ (0, 1)，避免 ln(0) 负无穷或 ln(1)=0 导致平局
        u = min(max(r.random(), 1e-12), 1.0 - 1e-12)
        key = math.log(u) / weight
        keys.append((key, item))

    keys.sort(key=lambda x: x[0], reverse=True)
    selected = [item for _, item in keys[:k]]
    return sorted(selected)


def weighted_choice_with_replacement(
    candidates_with_weights: List[Tuple[int, float]],
    k: int,
    rng: Optional[random.Random] = None,
) -> List[int]:
    """支持有放回的位置独立加权抽样（适用于福彩 3D、七星彩前 6 位），保留生成顺序。"""
    if k <= 0:
        return []
    r = rng if rng is not None else random
    items = [item for item, _ in candidates_with_weights]
    weights = [max(float(w), 1e-6) for _, w in candidates_with_weights]
    return r.choices(items, weights=weights, k=k)


def resolve_strategy_weights(
    strategy: Optional[str] = "balanced",
    w_bazi: Optional[float] = None,
    w_freq: Optional[float] = None,
    w_omission: Optional[float] = None,
) -> Tuple[float, float, float, str]:
    """解析并归一化三元权重 (w_bazi, w_freq, w_omission) 与遗漏评分模式。"""
    raw_strategy = (strategy or "balanced").strip()
    canon_strategy = (
        STRATEGY_ALIAS_MAP.get(raw_strategy)
        or STRATEGY_ALIAS_MAP.get(raw_strategy.lower())
        or (raw_strategy.lower() if raw_strategy.lower() in STRATEGY_PRESETS else "balanced")
    )
    default_wb, default_wf, default_wo, om_mode = STRATEGY_PRESETS.get(
        canon_strategy, STRATEGY_PRESETS["balanced"]
    )

    has_custom = w_bazi is not None or w_freq is not None or w_omission is not None
    if has_custom:
        wb = float(w_bazi if w_bazi is not None else 0.0)
        wf = float(w_freq if w_freq is not None else 0.0)
        wo = float(w_omission if w_omission is not None else 0.0)
        total = wb + wf + wo
        if total > 1e-6:
            return wb / total, wf / total, wo / total, om_mode
        return 0.3333, 0.3333, 0.3334, om_mode

    return default_wb, default_wf, default_wo, om_mode


def get_historical_feature_maps(
    lottery_type: str,
    zone_index: int = 0,
) -> Tuple[Dict[int, float], Dict[int, float]]:
    """读取最近 100 期历史开奖，统计频次 C(n) 与当前遗漏 G(n)。"""
    freq_map: Dict[int, float] = {}
    omission_map: Dict[int, float] = {}

    try:
        draws = get_draws(lottery_type, limit=100)
    except Exception:
        draws = []

    if not draws:
        return freq_map, omission_map

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

    total_draws = len(draws)
    for num in freq_map:
        if num not in omission_map:
            omission_map[num] = total_draws

    return freq_map, omission_map


def compute_liuri_resonance(
    day_stem_elem: str,
    day_branch_elem: str,
    xi_yong_shen: List[str],
    ji_shen: List[str],
) -> Tuple[List[str], str, float]:
    """
    计算今日流日与原局喜忌的严谨共振关系：
    - 仅当流日干支五行直接命中喜用神（且非忌神）判定为「大吉」(+20%)；
    - 仅当流日干支生助喜用神且绝非原局忌神时，判定为「小吉」(+10%)；
    - 无良性交集时返回空共振列表，绝不将忌神元素强行当作 +20% 共振元素。
    """
    xi_norm = {ZH_TO_EN_ELEMENT.get(e, e) for e in xi_yong_shen}
    ji_norm = {ZH_TO_EN_ELEMENT.get(e, e) for e in ji_shen}
    active_today = [day_stem_elem, day_branch_elem]

    direct_hits: List[str] = []
    for e in active_today:
        if e in xi_norm and e not in ji_norm and e not in direct_hits:
            direct_hits.append(e)

    if direct_hits:
        return direct_hits, "大吉", 1.20

    # 次级生助：生喜用神且本身不为忌神（规避身弱时印星之母为官杀忌神的问题）
    generating_safe = {MOTHER_ELEMENT.get(e, "") for e in xi_norm} - ji_norm - {""}
    gen_hits: List[str] = []
    for e in active_today:
        if e in generating_safe and e not in gen_hits:
            gen_hits.append(e)

    if gen_hits:
        return gen_hits, "吉", 1.10

    return [], "平吉", 1.00


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
    gender: Optional[str] = "male",
    resonance_multiplier: float = 1.20,
) -> List[Tuple[int, float]]:
    """计算区间内所有候选号码的混合三元权重（八字用神 + 历史频次 + 遗漏态势 + 流日/姓名/乾坤微调）。"""
    freq_map, omission_map = get_historical_feature_maps(lottery_type, zone_index)

    pool = list(range(min_val, max_val + 1))
    freqs = [freq_map.get(n, 0.0) for n in pool]
    min_f, max_f = min(freqs), max(freqs)
    span_f = max_f - min_f

    # 历史未现号码采用合理上界（避免极小样本硬赋 100 压扁已出号归一化分布）
    max_observed_omission = max(omission_map.values()) if omission_map else 0.0
    default_omission = max(max_observed_omission + 1.0, 1.0) if omission_map else 0.0

    omissions = [omission_map.get(n, default_omission) for n in pool]
    min_o, max_o = min(omissions), max(omissions)
    span_o = max_o - min_o

    norm_harmony = [ZH_TO_EN_ELEMENT.get(e, e) for e in (harmonious_elements or [])]
    lucky_set = set(name_lucky_numbers or [])

    candidates_with_weights: List[Tuple[int, float]] = []
    for n in pool:
        bazi_score, _ = compute_bazi_score(n, xi_yong_shen, lacking_elements, ji_shen)

        c_n = freq_map.get(n, 0.0)
        freq_score = (c_n - min_f) / span_f if span_f > 1e-6 else 0.5

        g_n = omission_map.get(n, default_omission)
        if span_o <= 1e-6:
            omission_score = 0.5
        elif omission_mode == "hot":
            # 热号追随：遗漏越小分越高
            omission_score = (max_o - g_n) / span_o
        elif omission_mode == "cold":
            # 极冷号反弹：遗漏越大分越高
            omission_score = (g_n - min_o) / span_o
        else:
            # 均衡/命理模式：采用温冷适中回归钟形曲线（偏离 0.55 中位越近得分越高），让遗漏权重真正产生区分度
            norm_o = (g_n - min_o) / span_o
            omission_score = max(0.15, 1.0 - abs(norm_o - 0.55) * 1.35)

        weight = w_bazi * bazi_score + w_freq * freq_score + w_omission * omission_score
        weight = max(float(weight), 1e-4)

        # 仅当流日确实存在良性共振元素时才加权
        n_elem = get_hetu_element(n)
        if norm_harmony and n_elem in norm_harmony and resonance_multiplier > 1.0:
            weight *= resonance_multiplier

        # 姓名五格吉数微调（仅精准命中或核心尾数，避免全盘稀释）
        if lucky_set and (n in lucky_set or (n % 10 or 10) in lucky_set):
            weight *= 1.15

        # 乾坤奇偶微调
        is_male = (gender or "male").lower() in ("male", "m", "乾造", "乾", "男")
        if is_male and (n % 2 == 1):
            weight *= 1.10
        elif (not is_male) and (n % 2 == 0):
            weight *= 1.10

        candidates_with_weights.append((n, round(weight, 5)))

    return candidates_with_weights


def build_wuxing_distribution(numbers: List[int]) -> Dict[str, int]:
    """统计单注号码的河图五行分布。"""
    dist = {"wood": 0, "fire": 0, "earth": 0, "metal": 0, "water": 0}
    for n in numbers:
        dist[get_hetu_element(n)] += 1
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
    """生成中文化命理与数理加权选号依据说明。"""
    xi_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in xi_yong_shen]
    xi_zh = [ELEMENT_NAMES_ZH.get(e, e) for e in xi_norm]
    xi_tails = []
    for en_e, zh_e in zip(xi_norm[:2], xi_zh[:2]):
        tails = [str(t) for t in ELEMENT_TO_TAILS.get(en_e, [])]
        xi_tails.append(f"{zh_e}({'与'.join(tails)})")

    tails_text = "、".join(xi_tails) if xi_tails else "用神"
    matches = sum(1 for n in chosen_primary if get_hetu_element(n) in xi_norm)
    match_pct = round((matches / max(len(chosen_primary), 1)) * 100, 1)

    canon = STRATEGY_ALIAS_MAP.get(strategy, "") or STRATEGY_ALIAS_MAP.get(strategy.lower(), "")
    strategy_desc = {
        "bazi": "命理吉数优先",
        "bazi_priority": "命理吉数优先",
        "balanced": "大数据平衡稳健",
        "hot_chase": "乘胜追热连号",
        "cold_reversal": "大遗漏极冷翻转",
    }.get(canon, strategy or "综合三元混合")

    effective_name = user_name.strip() if (user_name and user_name.strip()) else "缘主"
    prefix = f"缘主【{effective_name}】"
    harmony_clause = f"，{today_harmony_text}" if today_harmony_text else ""

    return (
        f"{prefix}命中喜用神{tails_text}契合度 {match_pct}%{harmony_clause}，"
        f"结合{strategy_desc}模型(八字:{w_bazi:.2f}, 频次:{w_freq:.2f}, 遗漏:{w_omission:.2f})加权生成"
    )


def _apply_lock_exclude_filter(
    weights: List[Tuple[int, float]],
    required_count: int,
    unique: bool,
    locked: Optional[List[int]],
    excluded: Optional[List[int]],
) -> Tuple[List[int], List[Tuple[int, float]]]:
    """应用定胆（必含）与杀号（排除）约束，保证剩余候选池始终合法可抽。"""
    valid_pool = {item for item, _ in weights}
    locked_clean: List[int] = []
    for n in (locked or []):
        if n in valid_pool and (not unique or n not in locked_clean):
            locked_clean.append(n)
    locked_clean = locked_clean[:required_count]

    excluded_set = {n for n in (excluded or []) if n in valid_pool and n not in locked_clean}
    if unique:
        remaining_pool = [(item, w) for item, w in weights if item not in locked_clean and item not in excluded_set]
        need = required_count - len(locked_clean)
        if len(remaining_pool) < need:
            # 若杀号过多导致候选池不足，自动回退排除集避免报错
            remaining_pool = [(item, w) for item, w in weights if item not in locked_clean]
    else:
        remaining_pool = [(item, w) for item, w in weights if item not in excluded_set]
        if not remaining_pool:
            remaining_pool = list(weights)

    return locked_clean, remaining_pool


def _sample_zone_numbers(
    weights: List[Tuple[int, float]],
    required_count: int,
    unique: bool,
    sorted_output: bool,
    deterministic: bool,
    single_det_mode: bool,
    rng: Optional[random.Random],
    locked: Optional[List[int]] = None,
    excluded: Optional[List[int]] = None,
) -> List[int]:
    """按彩种规则与定胆/杀号条件采样单个号码区，正确支持可重复位彩种与确定性模式。"""
    locked_nums, pool_weights = _apply_lock_exclude_filter(
        weights=weights,
        required_count=required_count,
        unique=unique,
        locked=locked,
        excluded=excluded,
    )
    need = required_count - len(locked_nums)
    if need <= 0:
        chosen = list(locked_nums[:required_count])
    elif single_det_mode:
        if unique:
            sorted_w = sorted(pool_weights, key=lambda x: (-x[1], x[0]))
            chosen = locked_nums + [x[0] for x in sorted_w[:need]]
        else:
            # 可重复位置彩种：在保持完全确定性的前提下允许高权数字在多位置重复（保留组三/豹子）
            det_rng = random.Random(20260518 + required_count * 131)
            chosen = locked_nums + weighted_choice_with_replacement(pool_weights, need, rng=det_rng)
    elif deterministic:
        if unique:
            chosen = locked_nums + a_res_weighted_sample_without_replacement(pool_weights, need, rng=rng)
        else:
            chosen = locked_nums + weighted_choice_with_replacement(pool_weights, need, rng=rng)
    else:
        if unique:
            chosen = locked_nums + a_res_weighted_sample_without_replacement(pool_weights, need, rng=rng)
        else:
            chosen = locked_nums + weighted_choice_with_replacement(pool_weights, need, rng=rng)

    if sorted_output:
        chosen.sort()
    return chosen


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
    gender: Optional[str] = "male",
    locked_primary: Optional[List[int]] = None,
    excluded_primary: Optional[List[int]] = None,
    locked_secondary: Optional[List[int]] = None,
    excluded_secondary: Optional[List[int]] = None,
) -> Dict[str, Any]:
    """
    核心三元加权选号引擎：
    - 对数空间 A-Res 抽样（无下溢反转）
    - 多注跨票去重 + 退火兜底补齐（严防静默少注）
    - 支持可重复位确定性选号、严谨流日共振、姓名五格及定胆/杀号过滤
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

    # 1. 今日流日气运与严谨共振计算
    now = datetime.now(CHINA_TZ)
    solar_now = Solar.fromYmdHms(now.year, now.month, now.day, now.hour, now.minute, now.second)
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

    harmonious_elems, res_level, res_mult = compute_liuri_resonance(
        day_stem_elem=day_stem_elem,
        day_branch_elem=day_branch_elem,
        xi_yong_shen=xi_yong_shen,
        ji_shen=ji_shen,
    )

    if harmonious_elems:
        boost_pct = int(round((res_mult - 1.0) * 100))
        harm_zh = "".join(ELEMENT_NAMES_ZH.get(e, e) for e in harmonious_elems)
        today_harmony_text = (
            f"融合今日流日{ganzhi_day}({harm_zh})气运共振(+{boost_pct}%)"
        )
    else:
        today_harmony_text = (
            f"今日流日{ganzhi_day}({ELEMENT_NAMES_ZH.get(day_stem_elem)}{ELEMENT_NAMES_ZH.get(day_branch_elem)})气运平稳"
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
        "resonance_level": res_level,
        "resonance_multiplier": res_mult,
        "harmonious_elements": [ELEMENT_NAMES_ZH.get(e, e) for e in harmonious_elems],
    }

    # 2. 姓名五格吉数（过滤非中文或空返回）
    name_lucky_numbers = None
    if user_name:
        name_info = analyze_name_numerology(user_name)
        name_lucky_numbers = name_info.get("name_lucky_numbers") or None

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
        gender=gender,
        resonance_multiplier=res_mult,
    )

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
            gender=gender,
            resonance_multiplier=res_mult,
        )

    # 退火平滑权重，防止极端小样本导致去重碰撞耗尽静默截断
    p_weights_annealed = [(item, max(w, 1e-4) ** 0.25 + 0.15) for item, w in p_weights]
    s_weights_annealed = (
        [(item, max(w, 1e-4) ** 0.25 + 0.15) for item, w in s_weights]
        if s_weights is not None
        else None
    )
    p_weights_uniform = [(item, 1.0) for item, _ in p_weights]
    s_weights_uniform = [(item, 1.0) for item, _ in s_weights] if s_weights is not None else None

    seen_tickets = set()
    recommendations: List[Dict[str, Any]] = []
    primary_attempts = max(count * 40, 250)
    annealed_attempts = max(count * 40, 250)
    uniform_attempts = max(count * 60, 400)
    total_budget = primary_attempts + annealed_attempts + uniform_attempts
    attempts = 0

    def _append_ticket(chosen_p: List[int], chosen_s: List[int]) -> bool:
        p_formatted = [p_rule.format_pattern % v for v in chosen_p]
        s_formatted = [s_rule.format_pattern % v for v in chosen_s] if (s_rule and chosen_s) else None
        ticket_key = (tuple(p_formatted), tuple(s_formatted) if s_formatted else ())
        if ticket_key in seen_tickets:
            return False
        seen_tickets.add(ticket_key)

        bet_idx = len(recommendations) + 1
        formatted_ticket = " ".join(p_formatted)
        if s_formatted:
            formatted_ticket += " + " + " ".join(s_formatted)

        wuxing_dist = build_wuxing_distribution(chosen_p + chosen_s)
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
        return True

    while len(recommendations) < count and attempts < total_budget:
        attempts += 1
        if attempts <= primary_attempts:
            cur_p_w, cur_s_w = p_weights, s_weights
        elif attempts <= primary_attempts + annealed_attempts:
            cur_p_w, cur_s_w = p_weights_annealed, s_weights_annealed
        else:
            cur_p_w, cur_s_w = p_weights_uniform, s_weights_uniform

        rng_p = (
            random.Random(attempts * 10007 + int(resolved_wb * 100) + int(resolved_wf * 100))
            if deterministic
            else None
        )
        rng_s = (
            random.Random(attempts * 99991 + int(resolved_wo * 100))
            if deterministic
            else None
        )

        chosen_p = _sample_zone_numbers(
            weights=cur_p_w,
            required_count=p_rule.count,
            unique=p_rule.unique,
            sorted_output=p_rule.sorted,
            deterministic=deterministic,
            single_det_mode=bool(deterministic and count == 1 and attempts == 1),
            rng=rng_p,
            locked=locked_primary,
            excluded=excluded_primary,
        )
        chosen_s: List[int] = []
        if s_rule and cur_s_w is not None:
            chosen_s = _sample_zone_numbers(
                weights=cur_s_w,
                required_count=s_rule.count,
                unique=s_rule.unique,
                sorted_output=s_rule.sorted,
                deterministic=deterministic,
                single_det_mode=bool(deterministic and count == 1 and attempts == 1),
                rng=rng_s,
                locked=locked_secondary,
                excluded=excluded_secondary,
            )

        _append_ticket(chosen_p, chosen_s)

    # 最终确定性枚举兜底（针对极窄组合空间仍未凑齐 count 时保证不静默少注）
    if len(recommendations) < count:
        p_locked, p_rem = _apply_lock_exclude_filter(
            p_weights, p_rule.count, p_rule.unique, locked_primary, excluded_primary
        )
        p_items = [item for item, _ in sorted(p_rem, key=lambda x: -x[1])]
        p_need = p_rule.count - len(p_locked)
        p_iter = (
            itertools.combinations(p_items, p_need)
            if p_rule.unique
            else itertools.product(p_items, repeat=p_need)
        )
        s_fallback_list: List[List[int]] = [[]]
        if s_rule and s_weights:
            s_locked, s_rem = _apply_lock_exclude_filter(
                s_weights, s_rule.count, s_rule.unique, locked_secondary, excluded_secondary
            )
            s_items = [item for item, _ in sorted(s_rem, key=lambda x: -x[1])]
            s_need = s_rule.count - len(s_locked)
            s_raw = (
                itertools.combinations(s_items, s_need)
                if s_rule.unique
                else itertools.product(s_items, repeat=s_need)
            )
            s_fallback_list = []
            for comb in itertools.islice(s_raw, max(count, 20)):
                cand_s = list(s_locked) + list(comb)
                if s_rule.sorted:
                    cand_s.sort()
                s_fallback_list.append(cand_s)
            if not s_fallback_list:
                s_fallback_list = [[]]

        for p_comb in p_iter:
            if len(recommendations) >= count:
                break
            cand_p = list(p_locked) + list(p_comb)
            if p_rule.sorted:
                cand_p.sort()
            for cand_s in s_fallback_list:
                if len(recommendations) >= count:
                    break
                _append_ticket(cand_p, cand_s)

    effective_name = user_name.strip() if (user_name and user_name.strip()) else "缘主"
    lines = [f"【曾练彩票助手 - {effective_name}专属吉祥票】"]
    lines.append(f"彩种类型：{rule.chinese_name} ({len(recommendations)}注机选)")
    lines.append(f"缘主姓名：{effective_name}")
    lines.append(f"今日流日：{ganzhi_year}年 {ganzhi_month}月 {ganzhi_day}日 {ganzhi_hour}")
    lines.append(f"推演策略：{strategy or 'balanced'}")
    lines.append("-" * 38)
    for rec in recommendations:
        lines.append(f"第{rec['bet_index']:02d}注: {rec['formatted_ticket']}")
    lines.append("-" * 38)
    lines.append("祝您吉祥顺意！(彩票纯属概率游戏，请理性娱乐)")
    copy_text = "\n".join(lines)

    return {
        "lottery_type": rule.lottery_type.value,
        "strategy": strategy or "balanced",
        "recommendations": recommendations,
        "copy_text": copy_text,
        "user_name": effective_name,
        "today_liuri": today_liuri_info,
        "bazi_summary": {
            "day_master": bazi_analysis.get("day_master", ""),
            "day_master_element": bazi_analysis.get("day_master_element", ""),
            "strength": bazi_analysis.get("strength", ""),
            "strength_level": bazi_analysis.get("strength_level", bazi_analysis.get("strength", "")),
            "xi_yong_shen": bazi_analysis.get("xi_yong_shen", []),
            "ji_shen": bazi_analysis.get("ji_shen", []),
            "lacking_elements": bazi_analysis.get("lacking_elements", []),
        },
    }
