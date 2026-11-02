"""Big data statistical analysis engine: frequencies, cold/hot sliding windows, and omissions."""
from collections import defaultdict
from typing import List, Dict, Any, Optional
import math

from backend.app.models.lottery import (
    LotteryType,
    NumberStat,
    ZoneStat,
    LotteryStatsResponse,
    HotColdNumbers,
    HotColdResponse,
    OmissionItem,
    OmissionResponse,
)
from backend.app.services.lottery_rules import get_lottery_rule
from backend.app.core.config import settings

def get_zone_number_pool(rule_zone) -> List[str]:
    """Generate the full list of formatted ball numbers for a zone."""
    return [rule_zone.format_pattern % v for v in range(rule_zone.min_value, rule_zone.max_value + 1)]

def analyze_zone_stats(
    draws: List[Dict[str, Any]],
    zone_key: str,
    number_pool: List[str],
    balls_per_draw: int,
    window: int = 30,
    hot_factor: float = 1.25,
    cold_factor: float = 0.75,
) -> List[NumberStat]:
    """
    Compute comprehensive statistics for a single zone across historical draws.
    draws: Ordered from newest (index 0) to oldest (index N-1).
    """
    N = len(draws)
    if N == 0:
        return []

    W = min(window, N)
    pool_size = len(number_pool)

    # Theoretical probability of each ball appearing in a draw
    # e.g. for SSQ red: 6/33; for FC3D: 3/10
    theoretical_prob = min(1.0, balls_per_draw / pool_size) if pool_size > 0 else 0.0
    expected_recent_count = W * theoretical_prob

    # Track occurrences and appearance indices
    counts = defaultdict(int)
    recent_counts = defaultdict(int)
    appearance_indices = defaultdict(list)

    for idx, d in enumerate(draws):
        raw_balls = d.get(zone_key)
        if raw_balls is None:
            continue
        balls = [raw_balls] if isinstance(raw_balls, str) else list(raw_balls)
        for b in balls:
            counts[b] += 1
            if idx < W:
                recent_counts[b] += 1
        for b in set(balls):
            appearance_indices[b].append(idx)

    stats_list: List[NumberStat] = []
    for num in number_pool:
        cnt = counts[num]
        freq = round(cnt / N, 4)
        rec_cnt = recent_counts[num]

        indices = appearance_indices[num]
        if not indices:
            curr_omission = N
            max_omission = N
            avg_omission = float(N)
            desire_index = 1.0
        else:
            curr_omission = indices[0]
            # Build intervals: g_0, g_1, ..., g_k
            intervals = [indices[0]]
            for i in range(len(indices) - 1):
                intervals.append(indices[i + 1] - indices[i] - 1)
            intervals.append((N - 1) - indices[-1])
            max_omission = max(intervals)
            # Mathematical average omission: (N - k) / (k + 1)
            k = len(indices)
            avg_omission = round((N - k) / (k + 1), 2)
            desire_index = round(curr_omission / avg_omission, 2) if avg_omission > 0 else 0.0

        # Classification based on sliding window expectation
        if rec_cnt >= expected_recent_count * hot_factor:
            hot_status = "hot"
        elif rec_cnt <= expected_recent_count * cold_factor:
            hot_status = "cold"
        else:
            hot_status = "warm"

        stats_list.append(NumberStat(
            number=num,
            total_count=cnt,
            frequency=freq,
            recent_count=rec_cnt,
            expected_count=round(expected_recent_count, 2),
            hot_status=hot_status,
            current_omission=curr_omission,
            max_omission=max_omission,
            avg_omission=avg_omission,
            desire_index=desire_index,
        ))

    return stats_list

def calculate_lottery_stats(
    lottery_type: str,
    draws: List[Dict[str, Any]],
    window: int = 30,
) -> Optional[LotteryStatsResponse]:
    """Compute overall stats response for given lottery and draws."""
    rule = get_lottery_rule(lottery_type)
    if not rule or not draws:
        return None

    primary_rule = rule.zones[0]
    pri_pool = get_zone_number_pool(primary_rule)
    pri_stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=pri_pool,
        balls_per_draw=primary_rule.count,
        window=window,
        hot_factor=settings.HOT_THRESHOLD_FACTOR,
        cold_factor=settings.COLD_THRESHOLD_FACTOR,
    )

    sec_stat_obj = None
    if len(rule.zones) > 1:
        secondary_rule = rule.zones[1]
        sec_pool = get_zone_number_pool(secondary_rule)
        sec_stats = analyze_zone_stats(
            draws=draws,
            zone_key="secondary_numbers",
            number_pool=sec_pool,
            balls_per_draw=secondary_rule.count,
            window=window,
            hot_factor=settings.HOT_THRESHOLD_FACTOR,
            cold_factor=settings.COLD_THRESHOLD_FACTOR,
        )
        sec_stat_obj = ZoneStat(
            zone_name=secondary_rule.zone_name,
            zone_code=secondary_rule.zone_code,
            stats=sec_stats,
        )

    latest_draw = draws[0] if draws else None
    return LotteryStatsResponse(
        lottery_type=lottery_type.lower(),
        draws_analyzed=len(draws),
        latest_issue=latest_draw.get("issue") if latest_draw else None,
        latest_draw_date=latest_draw.get("draw_date") if latest_draw else None,
        primary_zone=ZoneStat(
            zone_name=primary_rule.zone_name,
            zone_code=primary_rule.zone_code,
            stats=pri_stats,
        ),
        secondary_zone=sec_stat_obj,
    )

def calculate_hot_cold(
    lottery_type: str,
    draws: List[Dict[str, Any]],
    window: int = 30,
) -> Optional[HotColdResponse]:
    """Compute partitioned hot, warm, and cold numbers for each zone."""
    overall = calculate_lottery_stats(lottery_type, draws, window=window)
    if not overall:
        return None

    def group_numbers(stats: List[NumberStat]) -> HotColdNumbers:
        # Sort hot descending by recent_count
        hot = [s.number for s in sorted([s for s in stats if s.hot_status == "hot"], key=lambda x: x.recent_count, reverse=True)]
        # Sort cold ascending by recent_count
        cold = [s.number for s in sorted([s for s in stats if s.hot_status == "cold"], key=lambda x: x.recent_count)]
        # Warm
        warm = [s.number for s in [s for s in stats if s.hot_status == "warm"]]
        return HotColdNumbers(hot_numbers=hot, warm_numbers=warm, cold_numbers=cold)

    primary_hc = group_numbers(overall.primary_zone.stats)
    secondary_hc = group_numbers(overall.secondary_zone.stats) if overall.secondary_zone else None

    return HotColdResponse(
        lottery_type=lottery_type.lower(),
        window=min(window, len(draws)),
        primary_zone=primary_hc,
        secondary_zone=secondary_hc,
    )

def calculate_omission(
    lottery_type: str,
    draws: List[Dict[str, Any]],
) -> Optional[OmissionResponse]:
    """Compute omission rankings (sorted by desire index descending) for each zone."""
    overall = calculate_lottery_stats(lottery_type, draws, window=settings.DEFAULT_HOT_COLD_WINDOW)
    if not overall:
        return None

    def map_omissions(stats: List[NumberStat]) -> List[OmissionItem]:
        items = [
            OmissionItem(
                number=s.number,
                current_omission=s.current_omission,
                max_omission=s.max_omission,
                avg_omission=s.avg_omission,
                desire_index=s.desire_index,
            )
            for s in stats
        ]
        # Sort descending by desire index
        items.sort(key=lambda x: (x.desire_index, x.current_omission), reverse=True)
        return items

    primary_om = map_omissions(overall.primary_zone.stats)
    secondary_om = map_omissions(overall.secondary_zone.stats) if overall.secondary_zone else None

    return OmissionResponse(
        lottery_type=lottery_type.lower(),
        total_draws=len(draws),
        primary_zone=primary_om,
        secondary_zone=secondary_om,
    )

def get_statistical_weights(
    lottery_type: str,
    draws: List[Dict[str, Any]],
    zone: str = "primary",
    window: int = 30,
) -> Dict[str, float]:
    """
    Calculate normalized statistical multiplier weights for each number.
    W_stat(x) = 1.0 + 0.3 * NormFreq(x) + 0.4 * HeatScore(x) + 0.3 * DesireNorm(x)
    Used by Milestone 2 Bazi and A-Res weighted recommender.
    """
    overall = calculate_lottery_stats(lottery_type, draws, window=window)
    if not overall:
        return {}

    target_stats = overall.primary_zone.stats if zone == "primary" else (
        overall.secondary_zone.stats if overall.secondary_zone else []
    )
    if not target_stats:
        return {}

    max_freq = max((s.frequency for s in target_stats), default=1.0) or 1.0
    max_desire = max((s.desire_index for s in target_stats), default=1.0) or 1.0

    weights: Dict[str, float] = {}
    for s in target_stats:
        norm_freq = s.frequency / max_freq
        heat_boost = 0.2 if s.hot_status == "hot" else (-0.1 if s.hot_status == "cold" else 0.05)
        norm_desire = min(1.5, s.desire_index / max_desire)
        weight = 1.0 + 0.25 * norm_freq + heat_boost + 0.25 * norm_desire
        weights[s.number] = round(max(0.2, weight), 4)

    return weights
