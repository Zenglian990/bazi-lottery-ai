"""Tier 1 Test: F1 Lottery Rules & Ticket Validation Engine.
Verifies SSQ, DLT, FC3D, and modern QXC official constraints, formatting, and combinations.
"""
from tests_e2e.harness.oracle import ReferenceOracle
from tests_e2e.harness.client import get_test_client

def test_f1_ssq_rule_constraints_and_validation():
    """F1.1: Verify SSQ (6 Red 1-33 unique/sorted + 1 Blue 1-16) validation."""
    client = get_test_client()

    # Valid ticket
    res = client.validate_ticket("ssq", [4, 6, 10, 15, 26, 27], [11])
    assert res["data"]["is_valid"] is True
    assert res["data"]["formatted_string"] == "04,06,10,15,26,27|11"
    assert res["data"]["normalized_primary"] == ["04", "06", "10", "15", "26", "27"]
    assert res["data"]["normalized_secondary"] == ["11"]

    # Red ball count mismatch (5 reds instead of 6)
    res_bad = client.validate_ticket("ssq", [4, 6, 10, 15, 26], [11])
    assert res_bad["data"]["is_valid"] is False

    # Red ball out of bounds (34 > 33)
    res_oob = client.validate_ticket("ssq", [4, 6, 10, 15, 26, 34], [11])
    assert res_oob["data"]["is_valid"] is False

    # Red ball duplicates (two 10s)
    res_dup = client.validate_ticket("ssq", [4, 6, 10, 10, 26, 27], [11])
    assert res_dup["data"]["is_valid"] is False

    # Blue ball out of bounds (17 > 16)
    res_blue_oob = client.validate_ticket("ssq", [4, 6, 10, 15, 26, 27], [17])
    assert res_blue_oob["data"]["is_valid"] is False

def test_f1_dlt_rule_constraints_and_validation():
    """F1.2: Verify DLT (5 Front 1-35 unique/sorted + 2 Back 1-12 unique/sorted) validation."""
    client = get_test_client()

    # Valid ticket
    res = client.validate_ticket("dlt", [12, 14, 18, 22, 27], [1, 9])
    assert res["data"]["is_valid"] is True
    assert res["data"]["formatted_string"] == "12,14,18,22,27|01,09"
    assert res["data"]["normalized_primary"] == ["12", "14", "18", "22", "27"]
    assert res["data"]["normalized_secondary"] == ["01", "09"]

    # Unsorted input should be automatically normalized and sorted
    res_unsorted = client.validate_ticket("dlt", [27, 12, 22, 14, 18], [9, 1])
    assert res_unsorted["data"]["is_valid"] is True
    assert res_unsorted["data"]["formatted_string"] == "12,14,18,22,27|01,09"

    # Front count error (6 instead of 5)
    res_bad = client.validate_ticket("dlt", [12, 14, 18, 22, 27, 30], [1, 9])
    assert res_bad["data"]["is_valid"] is False

    # Back duplicate error (two 01s)
    res_back_dup = client.validate_ticket("dlt", [12, 14, 18, 22, 27], [1, 1])
    assert res_back_dup["data"]["is_valid"] is False

def test_f1_fc3d_rule_constraints_and_validation():
    """F1.3: Verify FC3D (3 digits 0-9, duplicates allowed, order preserved, no secondary)."""
    client = get_test_client()

    # Valid ticket with duplicates (e.g. 8,8,8)
    res = client.validate_ticket("fc3d", [8, 8, 8])
    assert res["data"]["is_valid"] is True
    assert res["data"]["formatted_string"] == "8,8,8"

    # Valid ticket with 0
    res_zero = client.validate_ticket("fc3d", [0, 8, 6])
    assert res_zero["data"]["is_valid"] is True
    assert res_zero["data"]["formatted_string"] == "0,8,6"

    # Out of range (10 > 9)
    res_oob = client.validate_ticket("fc3d", [0, 8, 10])
    assert res_oob["data"]["is_valid"] is False

    # Negative value
    res_neg = client.validate_ticket("fc3d", [-1, 8, 6])
    assert res_neg["data"]["is_valid"] is False

    # Secondary zone provided when not allowed
    res_extra_sec = client.validate_ticket("fc3d", [0, 8, 6], [1])
    assert res_extra_sec["data"]["is_valid"] is False

def test_f1_qxc_modern_rule_constraints_and_validation():
    """F1.4: Verify QXC modern rule (front 6 digits 0-9 duplicates allowed + 7th digit 0-14)."""
    client = get_test_client()

    # Valid ticket (7th ball can be up to 14)
    res = client.validate_ticket("qxc", [2, 9, 4, 5, 6, 3], [14])
    assert res["data"]["is_valid"] is True
    assert res["data"]["formatted_string"] == "2,9,4,5,6,3|14"

    # 7th ball is 0
    res_zero = client.validate_ticket("qxc", [0, 0, 0, 0, 0, 0], [0])
    assert res_zero["data"]["is_valid"] is True
    assert res_zero["data"]["formatted_string"] == "0,0,0,0,0,0|00"

    # 7th ball out of bounds (15 > 14)
    res_bad_7 = client.validate_ticket("qxc", [2, 9, 4, 5, 6, 3], [15])
    assert res_bad_7["data"]["is_valid"] is False

    # Front count error (5 digits instead of 6)
    res_bad_front = client.validate_ticket("qxc", [2, 9, 4, 5, 6], [2])
    assert res_bad_front["data"]["is_valid"] is False

def test_f1_total_combination_spaces():
    """F1.5: Verify theoretical combinatorial spaces match official mathematical specs."""
    import math

    # SSQ: C(33, 6) * C(16, 1) = 1,107,568 * 16 = 17,721,088
    ssq_c = math.comb(33, 6) * math.comb(16, 1)
    assert ssq_c == 17721088
    assert ssq_c == ReferenceOracle.LOTTERY_SPECS["ssq"]["combinations"]

    # DLT: C(35, 5) * C(12, 2) = 324,632 * 66 = 21,425,712
    dlt_c = math.comb(35, 5) * math.comb(12, 2)
    assert dlt_c == 21425712
    assert dlt_c == ReferenceOracle.LOTTERY_SPECS["dlt"]["combinations"]

    # FC3D: 10^3 = 1,000
    fc3d_c = 10 ** 3
    assert fc3d_c == 1000
    assert fc3d_c == ReferenceOracle.LOTTERY_SPECS["fc3d"]["combinations"]

    # QXC: 10^6 * 15 = 15,000,000
    qxc_c = (10 ** 6) * 15
    assert qxc_c == 15000000
    assert qxc_c == ReferenceOracle.LOTTERY_SPECS["qxc"]["combinations"]

def test_f1_ticket_formatting_syntax():
    """F1.6: Verify ticket formatting syntax and delimiter conventions."""
    from backend.app.services.lottery_rules import format_ticket_string

    # Dual zone with pipe delimiter
    s1 = format_ticket_string(["01", "02", "03", "04", "05", "06"], ["07"])
    assert s1 == "01,02,03,04,05,06|07"

    # Single zone without pipe
    s2 = format_ticket_string(["1", "2", "3"])
    assert s2 == "1,2,3"

    # Empty secondary
    s3 = format_ticket_string(["1", "2", "3"], [])
    assert s3 == "1,2,3"

def test_f1_unsupported_lottery_type_rejected():
    """F1.7: Unsupported lottery types must return rejection."""
    client = get_test_client()
    res = client.validate_ticket("powerball", [1, 2, 3, 4, 5], [6])
    assert res["data"]["is_valid"] is False
    assert "Unsupported" in res["data"]["error_message"]
