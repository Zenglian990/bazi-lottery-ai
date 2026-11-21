"""Tier 2 Test: Adversarial Inputs, Malformed Data & Boundary Injections.
Verifies rejection of out-of-bounds numbers, duplicate balls, count mismatches, and injection attacks.
"""
from tests_e2e.harness.client import get_test_client

def test_adversarial_out_of_bounds_balls():
    """T2.1: Numbers outside designated [min, max] range must be rejected."""
    client = get_test_client()

    # SSQ Red max is 33
    r1 = client.validate_ticket("ssq", [1, 2, 3, 4, 5, 34], [1])
    assert r1["data"]["is_valid"] is False

    # SSQ Red min is 1 (0 is invalid)
    r2 = client.validate_ticket("ssq", [0, 2, 3, 4, 5, 6], [1])
    assert r2["data"]["is_valid"] is False

    # DLT Back max is 12 (13 is invalid)
    r3 = client.validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 13])
    assert r3["data"]["is_valid"] is False

    # QXC 7th ball max is 14 (15 is invalid)
    r4 = client.validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [15])
    assert r4["data"]["is_valid"] is False

def test_adversarial_duplicate_balls_in_unique_zones():
    """T2.2: Duplicate numbers in zones with unique=True must be strictly rejected."""
    client = get_test_client()

    # SSQ Red duplicates
    r1 = client.validate_ticket("ssq", [7, 7, 12, 15, 20, 28], [5])
    assert r1["data"]["is_valid"] is False

    # DLT Front duplicates
    r2 = client.validate_ticket("dlt", [1, 2, 3, 4, 4], [1, 2])
    assert r2["data"]["is_valid"] is False

    # DLT Back duplicates
    r3 = client.validate_ticket("dlt", [1, 2, 3, 4, 5], [6, 6])
    assert r3["data"]["is_valid"] is False

def test_adversarial_zone_count_mismatch():
    """T2.3: Supplying too few or too many balls in a zone must be rejected."""
    client = get_test_client()

    # SSQ Red with 5 balls (needs 6)
    r1 = client.validate_ticket("ssq", [1, 2, 3, 4, 5], [6])
    assert r1["data"]["is_valid"] is False

    # SSQ Red with 7 balls (needs 6)
    r2 = client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6, 7], [8])
    assert r2["data"]["is_valid"] is False

    # FC3D with 2 digits (needs 3)
    r3 = client.validate_ticket("fc3d", [1, 2])
    assert r3["data"]["is_valid"] is False

    # FC3D with 4 digits (needs 3)
    r4 = client.validate_ticket("fc3d", [1, 2, 3, 4])
    assert r4["data"]["is_valid"] is False

def test_adversarial_non_numeric_and_float_types():
    """T2.4: Non-integer string tokens and invalid types must be safely rejected."""
    client = get_test_client()

    # Alphabetical strings
    r1 = client.validate_ticket("ssq", ["a", "b", "c", "d", "e", "f"], [1])
    assert r1["data"]["is_valid"] is False

    # Empty strings
    r2 = client.validate_ticket("fc3d", ["", "1", "2"])
    assert r2["data"]["is_valid"] is False

def test_adversarial_injection_payloads_safely_handled():
    """T2.5: SQL injection and XSS payloads in ticket parameters must be rejected without 500 error."""
    client = get_test_client()

    sql_payload = ["' OR 1=1; --", "2", "3"]
    r_sql = client.validate_ticket("fc3d", sql_payload)
    assert r_sql["data"]["is_valid"] is False

    xss_payload = ["<script>alert(1)</script>", "1", "2"]
    r_xss = client.validate_ticket("fc3d", xss_payload)
    assert r_xss["data"]["is_valid"] is False

def test_adversarial_empty_and_null_payloads():
    """T2.6: Completely empty ball lists must be handled cleanly with validation failure."""
    client = get_test_client()

    r_empty = client.validate_ticket("ssq", [], [])
    assert r_empty["data"]["is_valid"] is False
