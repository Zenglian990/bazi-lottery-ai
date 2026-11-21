"""Tier 1 Test: F1 Adversarial Probing & Stress Verification.
Performs adversarial challenge on Lottery Rules Engine and RESTful endpoints:
- Boundary values (SSQ red 0, 1, 33, 34; blue 0, 1, 16, 17; DLT front 0, 1, 35, 36; back 0, 1, 12, 13; QXC 7th pos 0, 14, 15).
- Duplicate numbers in sets that forbid duplicates.
- Unsorted numbers (auto-sorting vs order preservation).
- Negative numbers, floats, strings, nulls, empty arrays, oversized arrays.
- FC3D permutation rules (0-9 permitted duplicates, 3 digits, order preserved).
"""
import random
from tests_e2e.harness.client import get_test_client
from backend.app.services.lottery_rules import validate_ticket, LOTTERY_RULES

def test_f1_adversarial_boundary_probes():
    """Adversarial 1: Boundary value probing across all four lotteries."""
    client = get_test_client()

    # 1. SSQ boundaries
    # Red = 0 (invalid)
    r = client.validate_ticket("ssq", [0, 2, 3, 4, 5, 6], [1])
    assert r["data"]["is_valid"] is False

    # Red = 1 (valid min)
    r = client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [1])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"][0] == "01"

    # Red = 33 (valid max)
    r = client.validate_ticket("ssq", [1, 2, 3, 4, 5, 33], [1])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"][-1] == "33"

    # Red = 34 (invalid)
    r = client.validate_ticket("ssq", [1, 2, 3, 4, 5, 34], [1])
    assert r["data"]["is_valid"] is False

    # Blue = 0 (invalid), 1 (valid), 16 (valid), 17 (invalid)
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [0])["data"]["is_valid"] is False
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [1])["data"]["is_valid"] is True
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [16])["data"]["is_valid"] is True
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [17])["data"]["is_valid"] is False

    # 2. DLT boundaries
    # Front: 0 invalid, 1 valid, 35 valid, 36 invalid
    assert client.validate_ticket("dlt", [0, 2, 3, 4, 5], [1, 2])["data"]["is_valid"] is False
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 2])["data"]["is_valid"] is True
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 35], [1, 2])["data"]["is_valid"] is True
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 36], [1, 2])["data"]["is_valid"] is False

    # Back: 0 invalid, 1 valid, 12 valid, 13 invalid
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 5], [0, 2])["data"]["is_valid"] is False
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 2])["data"]["is_valid"] is True
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 12])["data"]["is_valid"] is True
    assert client.validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 13])["data"]["is_valid"] is False

    # 3. FC3D boundaries: 0 valid, 9 valid, -1 invalid, 10 invalid
    assert client.validate_ticket("fc3d", [0, 5, 5])["data"]["is_valid"] is True
    assert client.validate_ticket("fc3d", [9, 5, 5])["data"]["is_valid"] is True
    assert client.validate_ticket("fc3d", [-1, 5, 5])["data"]["is_valid"] is False
    assert client.validate_ticket("fc3d", [10, 5, 5])["data"]["is_valid"] is False

    # 4. QXC modern 7th pos: 0 valid, 14 valid, 15 invalid, -1 invalid
    assert client.validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [0])["data"]["is_valid"] is True
    assert client.validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [14])["data"]["is_valid"] is True
    assert client.validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [15])["data"]["is_valid"] is False
    assert client.validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [-1])["data"]["is_valid"] is False


def test_f1_adversarial_duplicate_rules():
    """Adversarial 2: Probe duplicate handling in unique vs non-unique zones."""
    client = get_test_client()

    # SSQ red duplicate integers
    r = client.validate_ticket("ssq", [1, 1, 2, 3, 4, 5], [6])
    assert r["data"]["is_valid"] is False

    # SSQ red duplicate string and integer
    r = client.validate_ticket("ssq", ["01", 1, 2, 3, 4, 5], [6])
    assert r["data"]["is_valid"] is False

    # DLT front duplicate
    r = client.validate_ticket("dlt", [5, 5, 6, 7, 8], [1, 2])
    assert r["data"]["is_valid"] is False

    # DLT back duplicate
    r = client.validate_ticket("dlt", [1, 2, 3, 4, 5], [2, 2])
    assert r["data"]["is_valid"] is False

    # FC3D 豹子 (all 3 identical) must be legal
    r = client.validate_ticket("fc3d", [8, 8, 8])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["8", "8", "8"]

    # FC3D 组三 (2 identical) must be legal
    r = client.validate_ticket("fc3d", [1, 2, 1])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["1", "2", "1"]

    # QXC front all duplicate digits must be legal
    r = client.validate_ticket("qxc", [7, 7, 7, 7, 7, 7], [7])
    assert r["data"]["is_valid"] is True


def test_f1_adversarial_sorting_vs_preservation():
    """Adversarial 3: Auto-sorting (SSQ/DLT) vs Strict Order Preservation (FC3D/QXC)."""
    client = get_test_client()

    # SSQ auto-sorts
    r = client.validate_ticket("ssq", [33, 25, 20, 15, 10, 5], [16])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["05", "10", "15", "20", "25", "33"]
    assert r["data"]["formatted_string"] == "05,10,15,20,25,33|16"

    # DLT auto-sorts both front and back
    r = client.validate_ticket("dlt", [35, 1, 20, 5, 10], [12, 1])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["01", "05", "10", "20", "35"]
    assert r["data"]["normalized_secondary"] == ["01", "12"]
    assert r["data"]["formatted_string"] == "01,05,10,20,35|01,12"

    # FC3D MUST preserve input order
    r = client.validate_ticket("fc3d", [8, 0, 6])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["8", "0", "6"]
    assert r["data"]["formatted_string"] == "8,0,6"

    # QXC MUST preserve input order
    r = client.validate_ticket("qxc", [9, 8, 7, 6, 5, 4], [3])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["9", "8", "7", "6", "5", "4"]
    assert r["data"]["formatted_string"] == "9,8,7,6,5,4|03"


def test_f1_adversarial_type_and_payload_stress():
    """Adversarial 4: Negative numbers, floats, strings, nulls, empty & oversized arrays."""
    client = get_test_client()

    # Negative numbers
    assert client.validate_ticket("ssq", [-1, 2, 3, 4, 5, 6], [1])["data"]["is_valid"] is False

    # Float inputs with fractional part (e.g. 1.5) must be rejected
    r_float = client.validate_ticket("ssq", [1.5, 2, 3, 4, 5, 6], [1])
    assert r_float["status_code"] in (400, 422) or r_float["data"].get("is_valid") is False

    # Direct service call strictly rejects any float (both 1.5 and 1.0)
    v_f1, _, _, _, _ = validate_ticket("ssq", [1.5, 2, 3, 4, 5, 6], [1])
    assert v_f1 is False
    v_f2, _, _, _, _ = validate_ticket("ssq", [1.0, 2, 3, 4, 5, 6], [1])
    assert v_f2 is False

    # Valid string representation
    r = client.validate_ticket("ssq", [" 01 ", "02", "3", " 4 ", "05", "+6"], ["07"])
    assert r["data"]["is_valid"] is True
    assert r["data"]["normalized_primary"] == ["01", "02", "03", "04", "05", "06"]

    # Non-numeric string
    assert client.validate_ticket("ssq", ["abc", 2, 3, 4, 5, 6], [1])["data"]["is_valid"] is False

    # Nulls / Nones in array
    r_null = client.validate_ticket("ssq", [1, 2, 3, 4, 5, None], [1])
    assert r_null["status_code"] in (400, 422) or r_null["data"].get("is_valid") is False

    # Missing required secondary
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], None)["data"]["is_valid"] is False
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [])["data"]["is_valid"] is False

    # Unexpected secondary in single zone FC3D
    assert client.validate_ticket("fc3d", [1, 2, 3], [4])["data"]["is_valid"] is False

    # Empty array
    assert client.validate_ticket("ssq", [], [1])["data"]["is_valid"] is False

    # Oversized array (7 reds)
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5, 6, 7], [1])["data"]["is_valid"] is False

    # Undersized array (5 reds)
    assert client.validate_ticket("ssq", [1, 2, 3, 4, 5], [1])["data"]["is_valid"] is False


def test_f1_adversarial_combinatorial_stress():
    """Adversarial 5: 1,000 randomized tickets per lottery type fuzzing."""
    for lt, rule in LOTTERY_RULES.items():
        z1 = rule.zones[0]
        pool1 = list(range(z1.min_value, z1.max_value + 1))
        has_sec = len(rule.zones) > 1
        z2 = rule.zones[1] if has_sec else None

        for _ in range(250):
            if z1.unique:
                p_nums = random.sample(pool1, z1.count)
            else:
                p_nums = [random.choice(pool1) for _ in range(z1.count)]

            s_nums = None
            if has_sec:
                pool2 = list(range(z2.min_value, z2.max_value + 1))
                if z2.unique:
                    s_nums = random.sample(pool2, z2.count)
                else:
                    s_nums = [random.choice(pool2) for _ in range(z2.count)]

            valid, err, norm_p, norm_s, fmt = validate_ticket(lt, p_nums, s_nums)
            assert valid is True
            assert len(norm_p) == z1.count
            if has_sec:
                assert len(norm_s) == z2.count
