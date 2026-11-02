"""Unit tests for lottery rule engine, parameter bounds, and ticket validation."""
import pytest
from backend.app.services.lottery_rules import (
    get_lottery_rule,
    get_all_rules,
    validate_ticket,
    validate_zone_numbers,
)
from backend.app.models.lottery import LotteryType

def test_all_lottery_rules_exist():
    """Verify that all four core lottery rules are properly registered."""
    rules = get_all_rules()
    for lt in ["ssq", "dlt", "fc3d", "qxc"]:
        assert lt in rules
        rule = get_lottery_rule(lt)
        assert rule is not None
        assert rule.lottery_type == lt

def test_ssq_rule_specifications():
    """Verify official specifications for ShuangSeQiu (SSQ)."""
    rule = get_lottery_rule(LotteryType.SSQ)
    assert rule.chinese_name == "双色球"
    assert len(rule.zones) == 2
    red, blue = rule.zones[0], rule.zones[1]
    assert red.count == 6
    assert red.min_value == 1 and red.max_value == 33
    assert red.unique is True
    assert red.sorted is True
    assert blue.count == 1
    assert blue.min_value == 1 and blue.max_value == 16

def test_ssq_ticket_validation_valid():
    """Test valid SSQ tickets with normalization and auto-sorting."""
    # Unsorted and unpadded input
    primary = ["27", 4, "26", "6", 15, "10"]
    secondary = ["11"]
    is_valid, err, norm_p, norm_s, formatted = validate_ticket("ssq", primary, secondary)
    assert is_valid is True
    assert err is None
    assert norm_p == ["04", "06", "10", "15", "26", "27"]
    assert norm_s == ["11"]
    assert formatted == "04,06,10,15,26,27|11"

def test_ssq_ticket_validation_invalid_red_duplicate():
    """Test SSQ validation rejection on duplicate red balls."""
    primary = ["04", "06", "10", "15", "26", "26"]
    secondary = ["11"]
    is_valid, err, _, _, _ = validate_ticket("ssq", primary, secondary)
    assert is_valid is False
    assert "duplicate" in err.lower() or "重复" in err

def test_ssq_ticket_validation_invalid_bounds():
    """Test SSQ validation rejection when numbers exceed allowed ranges."""
    # Red ball 34 > 33
    is_valid, err, _, _, _ = validate_ticket("ssq", ["01", "02", "03", "04", "05", "34"], ["01"])
    assert is_valid is False
    assert "outside valid range" in err or "34" in err

    # Blue ball 17 > 16
    is_valid, err, _, _, _ = validate_ticket("ssq", ["01", "02", "03", "04", "05", "06"], ["17"])
    assert is_valid is False
    assert "17" in err

    # Red ball 0 < 1
    is_valid, err, _, _, _ = validate_ticket("ssq", ["00", "02", "03", "04", "05", "06"], ["01"])
    assert is_valid is False

def test_ssq_ticket_validation_invalid_counts():
    """Test SSQ validation rejection when ball counts mismatch."""
    # Only 5 red balls
    is_valid, err, _, _, _ = validate_ticket("ssq", ["01", "02", "03", "04", "05"], ["01"])
    assert is_valid is False
    assert "requires exactly 6" in err

    # 2 blue balls
    is_valid, err, _, _, _ = validate_ticket("ssq", ["01", "02", "03", "04", "05", "06"], ["01", "02"])
    assert is_valid is False
    assert "requires exactly 1" in err

def test_dlt_ticket_validation_valid():
    """Test valid DaLeTou (DLT) tickets."""
    primary = [35, 1, 12, 28, 7]
    secondary = [12, 1]
    is_valid, err, norm_p, norm_s, formatted = validate_ticket("dlt", primary, secondary)
    assert is_valid is True
    assert norm_p == ["01", "07", "12", "28", "35"]
    assert norm_s == ["01", "12"]
    assert formatted == "01,07,12,28,35|01,12"

def test_dlt_ticket_validation_invalid():
    """Test DLT invalid boundaries and duplicate back zone balls."""
    # Duplicate in back zone
    is_valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [6, 6])
    assert is_valid is False
    assert "duplicate" in err.lower()

    # Front zone ball 36 > 35
    is_valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 36], [1, 2])
    assert is_valid is False
    assert "36" in err

    # Back zone ball 13 > 12
    is_valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 13])
    assert is_valid is False
    assert "13" in err

def test_fc3d_ticket_validation():
    """Test FuCai 3D (FC3D) single zone and duplicate acceptance."""
    # Distinct digits
    is_valid, err, norm_p, norm_s, formatted = validate_ticket("fc3d", [0, 8, 6])
    assert is_valid is True
    assert norm_p == ["0", "8", "6"]
    assert norm_s is None
    assert formatted == "0,8,6"

    # All identical digits (豹子号: duplicates explicitly allowed)
    is_valid, err, norm_p, _, formatted = validate_ticket("fc3d", [8, 8, 8])
    assert is_valid is True
    assert norm_p == ["8", "8", "8"]

    # Out of range digit 10 > 9
    is_valid, err, _, _, _ = validate_ticket("fc3d", [0, 8, 10])
    assert is_valid is False
    assert "10" in err

    # Reject secondary zone for FC3D
    is_valid, err, _, _, _ = validate_ticket("fc3d", [1, 2, 3], secondary_numbers=[4])
    assert is_valid is False
    assert "single zone" in err

def test_qxc_modern_rule_validation():
    """Test QiXingCai modern post-2020 rule (7th ball 0-14)."""
    # 7th ball = 14 (valid upper bound)
    is_valid, err, norm_p, norm_s, formatted = validate_ticket("qxc", [2, 9, 4, 5, 6, 3], [14])
    assert is_valid is True
    assert norm_p == ["2", "9", "4", "5", "6", "3"]
    assert norm_s == ["14"]
    assert formatted == "2,9,4,5,6,3|14"

    # 7th ball = 0 (valid lower bound)
    is_valid, err, norm_p, norm_s, formatted = validate_ticket("qxc", [0, 1, 2, 3, 4, 5], [0])
    assert is_valid is True
    assert norm_s == ["00"]

    # 7th ball = 15 (invalid: exceeds modern 0-14 limit)
    is_valid, err, _, _, _ = validate_ticket("qxc", [2, 9, 4, 5, 6, 3], [15])
    assert is_valid is False
    assert "15" in err

    # 7th ball = -1 (invalid)
    is_valid, err, _, _, _ = validate_ticket("qxc", [2, 9, 4, 5, 6, 3], [-1])
    assert is_valid is False

    # Front digit out of range (10 > 9)
    is_valid, err, _, _, _ = validate_ticket("qxc", [2, 9, 4, 5, 6, 10], [5])
    assert is_valid is False
    assert "10" in err

    # Wrong front count (5 instead of 6)
    is_valid, err, _, _, _ = validate_ticket("qxc", [2, 9, 4, 5, 6], [5])
    assert is_valid is False
    assert "requires exactly 6" in err
