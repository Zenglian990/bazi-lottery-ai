"""Lottery rule definitions, constraints, and ticket validation engine."""
from typing import Dict, List, Tuple, Optional, Any, Union
from backend.app.models.lottery import LotteryType, ZoneRule, LotteryRule

LOTTERY_RULES: Dict[str, LotteryRule] = {
    LotteryType.SSQ.value: LotteryRule(
        lottery_type=LotteryType.SSQ,
        chinese_name="双色球",
        issuing_body="中国福利彩票发行管理中心",
        draw_schedule="每周二、四、日 21:15",
        total_combinations=17721088,
        zones=[
            ZoneRule(
                zone_name="红球区",
                zone_code="red",
                count=6,
                min_value=1,
                max_value=33,
                unique=True,
                sorted=True,
                format_pattern="%02d",
            ),
            ZoneRule(
                zone_name="蓝球区",
                zone_code="blue",
                count=1,
                min_value=1,
                max_value=16,
                unique=False,
                sorted=False,
                format_pattern="%02d",
            ),
        ],
        sample_format="04,06,10,15,26,27|11",
    ),
    LotteryType.DLT.value: LotteryRule(
        lottery_type=LotteryType.DLT,
        chinese_name="超级大乐透",
        issuing_body="国家体育总局体育彩票管理中心",
        draw_schedule="每周一、三、六 21:25",
        total_combinations=21425712,
        zones=[
            ZoneRule(
                zone_name="前区",
                zone_code="front",
                count=5,
                min_value=1,
                max_value=35,
                unique=True,
                sorted=True,
                format_pattern="%02d",
            ),
            ZoneRule(
                zone_name="后区",
                zone_code="back",
                count=2,
                min_value=1,
                max_value=12,
                unique=True,
                sorted=True,
                format_pattern="%02d",
            ),
        ],
        sample_format="12,14,18,22,27|01,09",
    ),
    LotteryType.FC3D.value: LotteryRule(
        lottery_type=LotteryType.FC3D,
        chinese_name="福彩3D",
        issuing_body="中国福利彩票发行管理中心",
        draw_schedule="每日 21:15",
        total_combinations=1000,
        zones=[
            ZoneRule(
                zone_name="百十个位",
                zone_code="digits",
                count=3,
                min_value=0,
                max_value=9,
                unique=False,
                sorted=False,
                format_pattern="%d",
            ),
        ],
        sample_format="0,8,6",
    ),
    LotteryType.QXC.value: LotteryRule(
        lottery_type=LotteryType.QXC,
        chinese_name="七星彩",
        issuing_body="国家体育总局体育彩票管理中心",
        draw_schedule="每周二、五、日 21:25",
        total_combinations=15000000,
        zones=[
            ZoneRule(
                zone_name="前六位",
                zone_code="front",
                count=6,
                min_value=0,
                max_value=9,
                unique=False,
                sorted=False,
                format_pattern="%d",
            ),
            ZoneRule(
                zone_name="第七位",
                zone_code="back",
                count=1,
                min_value=0,
                max_value=14,
                unique=False,
                sorted=False,
                format_pattern="%02d",
            ),
        ],
        sample_format="2,9,4,5,6,3|02",
    ),
}

def get_lottery_rule(lottery_type: Union[str, LotteryType]) -> Optional[LotteryRule]:
    """Retrieve rule definition for specified lottery type."""
    key = lottery_type.value if isinstance(lottery_type, LotteryType) else str(lottery_type).lower()
    return LOTTERY_RULES.get(key)

def get_all_rules() -> Dict[str, LotteryRule]:
    """Retrieve all supported lottery rules."""
    return LOTTERY_RULES

def parse_number_item(val: Any) -> Optional[int]:
    """Parse string or int into integer value."""
    if isinstance(val, int):
        return val
    try:
        s = str(val).strip()
        return int(s)
    except (ValueError, TypeError):
        return None

def validate_zone_numbers(
    zone_rule: ZoneRule,
    numbers: List[Any],
) -> Tuple[bool, Optional[str], Optional[List[str]]]:
    """Validate and normalize numbers for a single zone according to ZoneRule."""
    if len(numbers) != zone_rule.count:
        return (
            False,
            f"Zone '{zone_rule.zone_name}' requires exactly {zone_rule.count} numbers, but got {len(numbers)}",
            None,
        )

    int_values: List[int] = []
    for item in numbers:
        parsed = parse_number_item(item)
        if parsed is None:
            return False, f"Invalid non-integer item '{item}' in zone '{zone_rule.zone_name}'", None
        if parsed < zone_rule.min_value or parsed > zone_rule.max_value:
            return (
                False,
                f"Number {parsed} in zone '{zone_rule.zone_name}' is outside valid range [{zone_rule.min_value}, {zone_rule.max_value}]",
                None,
            )
        int_values.append(parsed)

    if zone_rule.unique:
        if len(set(int_values)) != len(int_values):
            return False, f"Zone '{zone_rule.zone_name}' does not allow duplicate numbers", None

    if zone_rule.sorted:
        int_values.sort()

    # Format numbers according to pattern
    formatted = [zone_rule.format_pattern % v for v in int_values]
    return True, None, formatted

def format_ticket_string(
    primary: List[str],
    secondary: Optional[List[str]] = None,
) -> str:
    """Combine primary and secondary zones into standard ticket string."""
    pri_str = ",".join(primary)
    if secondary and len(secondary) > 0:
        sec_str = ",".join(secondary)
        return f"{pri_str}|{sec_str}"
    return pri_str

def validate_ticket(
    lottery_type: Union[str, LotteryType],
    primary_numbers: List[Any],
    secondary_numbers: Optional[List[Any]] = None,
) -> Tuple[bool, Optional[str], Optional[List[str]], Optional[List[str]], Optional[str]]:
    """
    Validate and format a complete lottery ticket.
    Returns: (is_valid, error_message, normalized_primary, normalized_secondary, formatted_string)
    """
    rule = get_lottery_rule(lottery_type)
    if not rule:
        return False, f"Unsupported lottery type: '{lottery_type}'", None, None, None

    primary_rule = rule.zones[0]
    p_valid, p_err, norm_primary = validate_zone_numbers(primary_rule, primary_numbers)
    if not p_valid:
        return False, p_err, None, None, None

    norm_secondary: Optional[List[str]] = None
    if len(rule.zones) > 1:
        secondary_rule = rule.zones[1]
        sec_input = secondary_numbers or []
        s_valid, s_err, norm_secondary = validate_zone_numbers(secondary_rule, sec_input)
        if not s_valid:
            return False, s_err, None, None, None
    else:
        # Single zone lottery (e.g. FC3D) should not provide secondary numbers
        if secondary_numbers and len(secondary_numbers) > 0:
            return (
                False,
                f"Lottery type '{lottery_type}' only has a single zone; unexpected secondary numbers provided",
                None,
                None,
                None,
            )

    formatted = format_ticket_string(norm_primary, norm_secondary)
    return True, None, norm_primary, norm_secondary, formatted
