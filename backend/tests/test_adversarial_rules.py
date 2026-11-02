"""Adversarial stress test suite for Lottery Rules Engine and Validation Endpoints.
Probes:
- Boundary conditions (SSQ red 0, 1, 33, 34; blue 0, 1, 16, 17; DLT front 0, 1, 35, 36; back 0, 1, 12, 13; QXC 7th pos 0, 14, 15).
- Duplicate numbers in sets that forbid duplicates.
- Order preservation vs auto-sorting (SSQ/DLT auto-sorted; FC3D/QXC order preserved).
- Type stress: negative numbers, floats, strings, nulls, empty arrays, oversized arrays.
- FC3D permutation rules (0-9 permitted duplicates, 3 digits).
- High-volume randomized combinatorial fuzzing.
- Direct service invocation and RESTful API endpoint probing.
"""
import random
import pytest
from fastapi.testclient import TestClient

from backend.app.main import app
from backend.app.services.lottery_rules import (
    validate_ticket,
    validate_zone_numbers,
    get_lottery_rule,
    LOTTERY_RULES,
)

@pytest.fixture(scope="module")
def api_client():
    """FastAPI TestClient fixture."""
    with TestClient(app) as client:
        yield client


# ============================================================================
# 1. BOUNDARY VALUE PROBES (SSQ, DLT, FC3D, QXC)
# ============================================================================

class TestBoundaryValues:
    """Rigorous boundary probes for all supported lottery types."""

    def test_ssq_red_boundaries(self, api_client):
        """SSQ red ball boundary probing: 0 (invalid), 1 (valid min), 33 (valid max), 34 (invalid)."""
        # Red = 0 (invalid < 1)
        valid, err, _, _, _ = validate_ticket("ssq", [0, 2, 3, 4, 5, 6], [1])
        assert valid is False
        assert "outside valid range" in err or "0" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [0, 2, 3, 4, 5, 6],
            "secondary_numbers": [1],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False

        # Red = 1 (valid min boundary)
        valid, err, norm_p, norm_s, fmt = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [1])
        assert valid is True
        assert norm_p[0] == "01"

        # Red = 33 (valid max boundary)
        valid, err, norm_p, norm_s, fmt = validate_ticket("ssq", [1, 2, 3, 4, 5, 33], [1])
        assert valid is True
        assert norm_p[-1] == "33"

        # Red = 34 (invalid > 33)
        valid, err, _, _, _ = validate_ticket("ssq", [1, 2, 3, 4, 5, 34], [1])
        assert valid is False
        assert "outside valid range" in err or "34" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [1, 2, 3, 4, 5, 34],
            "secondary_numbers": [1],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False

    def test_ssq_blue_boundaries(self, api_client):
        """SSQ blue ball boundary probing: 0 (invalid), 1 (valid min), 16 (valid max), 17 (invalid)."""
        # Blue = 0 (invalid < 1)
        valid, err, _, _, _ = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [0])
        assert valid is False

        # Blue = 1 (valid min)
        valid, err, _, norm_s, fmt = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [1])
        assert valid is True
        assert norm_s == ["01"]
        assert fmt.endswith("|01")

        # Blue = 16 (valid max)
        valid, err, _, norm_s, fmt = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [16])
        assert valid is True
        assert norm_s == ["16"]
        assert fmt.endswith("|16")

        # Blue = 17 (invalid > 16)
        valid, err, _, _, _ = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [17])
        assert valid is False
        assert "17" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [1, 2, 3, 4, 5, 6],
            "secondary_numbers": [17],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False

    def test_dlt_front_boundaries(self, api_client):
        """DLT front ball boundary probing: 0 (invalid), 1 (valid min), 35 (valid max), 36 (invalid)."""
        # Front = 0 (invalid)
        valid, err, _, _, _ = validate_ticket("dlt", [0, 2, 3, 4, 5], [1, 2])
        assert valid is False

        # Front = 1 (valid min)
        valid, err, norm_p, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 2])
        assert valid is True
        assert norm_p[0] == "01"

        # Front = 35 (valid max)
        valid, err, norm_p, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 35], [1, 2])
        assert valid is True
        assert norm_p[-1] == "35"

        # Front = 36 (invalid)
        valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 36], [1, 2])
        assert valid is False
        assert "36" in err

    def test_dlt_back_boundaries(self, api_client):
        """DLT back ball boundary probing: 0 (invalid), 1 (valid min), 12 (valid max), 13 (invalid)."""
        # Back = 0 (invalid)
        valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [0, 2])
        assert valid is False

        # Back = 1 (valid min)
        valid, err, _, norm_s, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 2])
        assert valid is True
        assert norm_s[0] == "01"

        # Back = 12 (valid max)
        valid, err, _, norm_s, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 12])
        assert valid is True
        assert norm_s[-1] == "12"

        # Back = 13 (invalid)
        valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 13])
        assert valid is False
        assert "13" in err

    def test_fc3d_boundaries(self, api_client):
        """FC3D boundaries: digits 0-9; -1 and 10 must be rejected."""
        # -1 (invalid)
        valid, err, _, _, _ = validate_ticket("fc3d", [-1, 5, 5])
        assert valid is False

        # 0 (valid min)
        valid, err, norm_p, _, fmt = validate_ticket("fc3d", [0, 5, 5])
        assert valid is True
        assert norm_p == ["0", "5", "5"]

        # 9 (valid max)
        valid, err, norm_p, _, fmt = validate_ticket("fc3d", [9, 5, 5])
        assert valid is True
        assert norm_p == ["9", "5", "5"]

        # 10 (invalid)
        valid, err, _, _, _ = validate_ticket("fc3d", [10, 5, 5])
        assert valid is False
        assert "10" in err

    def test_qxc_boundaries_and_modern_7th_pos(self, api_client):
        """QXC modern rules: front 0-9, 7th position 0-14 (0 valid, 14 valid, 15 invalid)."""
        # Front position boundaries: 0 and 9 valid, -1 and 10 invalid
        valid, _, _, _, _ = validate_ticket("qxc", [0, 1, 2, 3, 4, 9], [7])
        assert valid is True

        valid, _, _, _, _ = validate_ticket("qxc", [-1, 1, 2, 3, 4, 9], [7])
        assert valid is False

        valid, _, _, _, _ = validate_ticket("qxc", [0, 1, 2, 3, 4, 10], [7])
        assert valid is False

        # 7th position = 0 (valid min)
        valid, err, _, norm_s, fmt = validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [0])
        assert valid is True
        assert norm_s == ["00"]
        assert fmt.endswith("|00")

        # 7th position = 14 (valid max according to post-2020 rules)
        valid, err, _, norm_s, fmt = validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [14])
        assert valid is True
        assert norm_s == ["14"]
        assert fmt.endswith("|14")

        # 7th position = 15 (invalid > 14)
        valid, err, _, _, _ = validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [15])
        assert valid is False
        assert "15" in err

        resp = api_client.post("/api/lottery/validate/qxc", json={
            "primary_numbers": [1, 2, 3, 4, 5, 6],
            "secondary_numbers": [15],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False

        # 7th position = -1 (invalid < 0)
        valid, err, _, _, _ = validate_ticket("qxc", [1, 2, 3, 4, 5, 6], [-1])
        assert valid is False


# ============================================================================
# 2. DUPLICATE BALLS PROBES
# ============================================================================

class TestDuplicateBalls:
    """Probe duplicate ball enforcement across lotteries with distinct uniqueness constraints."""

    def test_ssq_red_duplicates_forbidden(self, api_client):
        """SSQ red balls forbid duplicates."""
        # Exact duplicate
        valid, err, _, _, _ = validate_ticket("ssq", [1, 1, 2, 3, 4, 5], [6])
        assert valid is False
        assert "duplicate" in err.lower() or "重复" in err

        # String and int mixed duplicate
        valid, err, _, _, _ = validate_ticket("ssq", ["01", 1, 2, 3, 4, 5], [6])
        assert valid is False
        assert "duplicate" in err.lower() or "重复" in err

        # Whitespace padded duplicate
        valid, err, _, _, _ = validate_ticket("ssq", [" 01 ", 1, 2, 3, 4, 5], [6])
        assert valid is False

        # All identical
        valid, err, _, _, _ = validate_ticket("ssq", [7, 7, 7, 7, 7, 7], [6])
        assert valid is False

    def test_ssq_red_and_blue_independent(self):
        """SSQ allows identical numbers in red and blue zones (independent pools)."""
        valid, err, norm_p, norm_s, fmt = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], [6])
        assert valid is True
        assert norm_p == ["01", "02", "03", "04", "05", "06"]
        assert norm_s == ["06"]

    def test_dlt_front_and_back_duplicates_forbidden_internally(self):
        """DLT front and back zones forbid internal duplicates, but permit cross-zone overlap."""
        # Front duplicate
        valid, err, _, _, _ = validate_ticket("dlt", [5, 5, 6, 7, 8], [1, 2])
        assert valid is False
        assert "duplicate" in err.lower()

        # Back duplicate
        valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [2, 2])
        assert valid is False
        assert "duplicate" in err.lower()

        # Cross-zone overlap is 100% legal
        valid, err, norm_p, norm_s, fmt = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 2])
        assert valid is True
        assert norm_p == ["01", "02", "03", "04", "05"]
        assert norm_s == ["01", "02"]

    def test_fc3d_and_qxc_allow_duplicates(self):
        """FC3D and QXC front digits permit duplicate numbers (permutations)."""
        # FC3D 豹子号 (3 identical)
        valid, err, norm_p, _, fmt = validate_ticket("fc3d", [8, 8, 8])
        assert valid is True
        assert norm_p == ["8", "8", "8"]

        # FC3D 组三 (2 identical)
        valid, err, norm_p, _, fmt = validate_ticket("fc3d", [1, 2, 1])
        assert valid is True
        assert norm_p == ["1", "2", "1"]

        # QXC all 6 digits identical
        valid, err, norm_p, norm_s, fmt = validate_ticket("qxc", [6, 6, 6, 6, 6, 6], [6])
        assert valid is True
        assert norm_p == ["6", "6", "6", "6", "6", "6"]
        assert norm_s == ["06"]


# ============================================================================
# 3. UNSORTED NUMBERS & ORDER PRESERVATION
# ============================================================================

class TestSortingAndOrdering:
    """Ensure auto-sorting for SSQ/DLT and strict order preservation for FC3D/QXC."""

    def test_ssq_and_dlt_auto_sorting(self):
        """SSQ red and DLT front/back zones must auto-sort in ascending order."""
        # SSQ reversed
        valid, err, norm_p, norm_s, fmt = validate_ticket("ssq", [33, 25, 20, 15, 10, 5], [16])
        assert valid is True
        assert norm_p == ["05", "10", "15", "20", "25", "33"]
        assert fmt == "05,10,15,20,25,33|16"

        # DLT scrambled
        valid, err, norm_p, norm_s, fmt = validate_ticket("dlt", [35, 1, 20, 5, 10], [12, 1])
        assert valid is True
        assert norm_p == ["01", "05", "10", "20", "35"]
        assert norm_s == ["01", "12"]
        assert fmt == "01,05,10,20,35|01,12"

    def test_fc3d_and_qxc_order_preservation(self):
        """FC3D and QXC must preserve user digit order and NOT auto-sort."""
        # FC3D: [8, 0, 6] must NOT become [0, 6, 8]
        valid, err, norm_p, _, fmt = validate_ticket("fc3d", [8, 0, 6])
        assert valid is True
        assert norm_p == ["8", "0", "6"]
        assert fmt == "8,0,6"

        # QXC: [9, 8, 7, 6, 5, 4] must NOT be sorted
        valid, err, norm_p, norm_s, fmt = validate_ticket("qxc", [9, 8, 7, 6, 5, 4], [3])
        assert valid is True
        assert norm_p == ["9", "8", "7", "6", "5", "4"]
        assert fmt == "9,8,7,6,5,4|03"


# ============================================================================
# 4. NEGATIVE NUMBERS, FLOATS, STRINGS, NULLS, EMPTY & OVERSIZED ARRAYS
# ============================================================================

class TestMalformedAndTypeStress:
    """Stress test engine and API against adversarial malformed payloads."""

    def test_negative_numbers(self, api_client):
        """Negative numbers must be cleanly rejected across all lotteries."""
        for lt in ["ssq", "dlt", "fc3d", "qxc"]:
            rule = LOTTERY_RULES[lt]
            p_count = rule.zones[0].count
            bad_primary = [-5] + [1] * (p_count - 1)
            valid, err, _, _, _ = validate_ticket(lt, bad_primary, [1] if len(rule.zones) > 1 else None)
            assert valid is False
            assert "outside valid range" in err or "-5" in err

            resp = api_client.post(f"/api/lottery/validate/{lt}", json={
                "primary_numbers": bad_primary,
                "secondary_numbers": [1] if len(rule.zones) > 1 else None,
            })
            assert resp.status_code == 200
            assert resp.json()["is_valid"] is False

    def test_float_rejection(self, api_client):
        """Floats (e.g. 1.5, 3.14) must be rejected."""
        # Service level rejection
        valid, err, _, _, _ = validate_ticket("ssq", [1.5, 2, 3, 4, 5, 6], [1])
        assert valid is False
        assert "non-integer" in err or "1.5" in err

        valid, err, _, _, _ = validate_ticket("ssq", [1.0, 2, 3, 4, 5, 6], [1])
        assert valid is False
        assert "non-integer" in err

        # API level rejection (Pydantic validates Union[str, int])
        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [1.5, 2, 3, 4, 5, 6],
            "secondary_numbers": [1],
        })
        assert resp.status_code in (400, 422)

    def test_string_inputs_valid_and_invalid(self, api_client):
        """Valid numeric strings should parse; garbage strings must reject."""
        # Valid string integers with padding / whitespace
        valid, err, norm_p, norm_s, fmt = validate_ticket("ssq", [" 01 ", "02", "3", " 4 ", "05", "+6"], ["07"])
        assert valid is True
        assert norm_p == ["01", "02", "03", "04", "05", "06"]
        assert norm_s == ["07"]

        # Non-numeric string
        valid, err, _, _, _ = validate_ticket("ssq", ["abc", 2, 3, 4, 5, 6], [1])
        assert valid is False
        assert "non-integer" in err or "abc" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": ["abc", 2, 3, 4, 5, 6],
            "secondary_numbers": [1],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False
        assert "abc" in resp.json()["error_message"]

        # Hex and float strings
        valid, err, _, _, _ = validate_ticket("ssq", ["0x10", 2, 3, 4, 5, 6], [1])
        assert valid is False

        valid, err, _, _, _ = validate_ticket("ssq", ["1.5", 2, 3, 4, 5, 6], [1])
        assert valid is False

        # Empty string
        valid, err, _, _, _ = validate_ticket("ssq", ["", 2, 3, 4, 5, 6], [1])
        assert valid is False

    def test_nulls_and_nones(self, api_client):
        """None inside list or missing required zones must reject."""
        # None inside primary list
        valid, err, _, _, _ = validate_ticket("ssq", [1, 2, 3, 4, 5, None], [1])
        assert valid is False
        assert "non-integer" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [1, 2, 3, 4, 5, None],
            "secondary_numbers": [1],
        })
        assert resp.status_code == 422

        # Null secondary when required (SSQ requires secondary)
        valid, err, _, _, _ = validate_ticket("ssq", [1, 2, 3, 4, 5, 6], None)
        assert valid is False
        assert "requires exactly 1" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [1, 2, 3, 4, 5, 6],
            "secondary_numbers": None,
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False

    def test_empty_and_oversized_arrays(self, api_client):
        """Empty and oversized arrays must be rejected."""
        # Empty array
        valid, err, _, _, _ = validate_ticket("ssq", [], [1])
        assert valid is False
        assert "requires exactly 6" in err

        resp = api_client.post("/api/lottery/validate/ssq", json={
            "primary_numbers": [],
            "secondary_numbers": [1],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False

        # Oversized SSQ primary (10 balls instead of 6)
        valid, err, _, _, _ = validate_ticket("ssq", list(range(1, 11)), [1])
        assert valid is False
        assert "requires exactly 6" in err

        # Undersized SSQ primary (5 balls instead of 6)
        valid, err, _, _, _ = validate_ticket("ssq", [1, 2, 3, 4, 5], [1])
        assert valid is False
        assert "requires exactly 6" in err

        # Oversized DLT secondary (3 balls instead of 2)
        valid, err, _, _, _ = validate_ticket("dlt", [1, 2, 3, 4, 5], [1, 2, 3])
        assert valid is False
        assert "requires exactly 2" in err

    def test_fc3d_unexpected_secondary_rejected(self, api_client):
        """FC3D is single-zone; providing secondary numbers must reject."""
        valid, err, _, _, _ = validate_ticket("fc3d", [1, 2, 3], secondary_numbers=[4])
        assert valid is False
        assert "single zone" in err

        resp = api_client.post("/api/lottery/validate/fc3d", json={
            "primary_numbers": [1, 2, 3],
            "secondary_numbers": [4],
        })
        assert resp.status_code == 200
        assert resp.json()["is_valid"] is False
        assert "single zone" in resp.json()["error_message"]

    def test_unsupported_lottery_type(self, api_client):
        """Unsupported lottery type must return clean rejection / 404."""
        valid, err, _, _, _ = validate_ticket("mega_millions", [1, 2, 3, 4, 5], [1])
        assert valid is False
        assert "Unsupported" in err

        resp = api_client.post("/api/lottery/validate/mega_millions", json={
            "primary_numbers": [1, 2, 3, 4, 5],
            "secondary_numbers": [1],
        })
        assert resp.status_code == 404


# ============================================================================
# 5. HIGH-VOLUME FUZZING & STRESS HARNESS GENERATOR
# ============================================================================

class TestHighVolumeStressHarness:
    """Fuzzing and stress testing across thousands of generated combinations."""

    def test_random_valid_ticket_generation_fuzz(self):
        """Generate 1,000 valid tickets per lottery type; all must pass validation."""
        for lt, rule in LOTTERY_RULES.items():
            for _ in range(250):
                z1 = rule.zones[0]
                pool1 = list(range(z1.min_value, z1.max_value + 1))
                if z1.unique:
                    p_nums = random.sample(pool1, z1.count)
                else:
                    p_nums = [random.choice(pool1) for _ in range(z1.count)]

                s_nums = None
                if len(rule.zones) > 1:
                    z2 = rule.zones[1]
                    pool2 = list(range(z2.min_value, z2.max_value + 1))
                    if z2.unique:
                        s_nums = random.sample(pool2, z2.count)
                    else:
                        s_nums = [random.choice(pool2) for _ in range(z2.count)]

                valid, err, norm_p, norm_s, fmt = validate_ticket(lt, p_nums, s_nums)
                assert valid is True, f"Failed on valid {lt}: {p_nums}, {s_nums}, err: {err}"
                assert len(norm_p) == z1.count
                if s_nums is not None:
                    assert len(norm_s) == len(s_nums)
                assert fmt is not None

    def test_random_mutated_fault_injection_fuzz(self):
        """Inject faults (out-of-bounds, duplicates, wrong lengths, non-numeric) into 1,000 tickets.
        All must be rejected safely with is_valid=False and no unhandled exceptions.
        """
        for lt, rule in LOTTERY_RULES.items():
            z1 = rule.zones[0]
            pool1 = list(range(z1.min_value, z1.max_value + 1))
            has_sec = len(rule.zones) > 1
            z2 = rule.zones[1] if has_sec else None

            for i in range(250):
                # Pick mutation strategy
                strategy = i % 5
                p_nums = random.sample(pool1, min(z1.count, len(pool1))) if z1.unique else [random.choice(pool1) for _ in range(z1.count)]
                s_nums = [random.choice(range(z2.min_value, z2.max_value + 1)) for _ in range(z2.count)] if has_sec else None

                if strategy == 0:
                    # Out of bounds above max
                    p_nums[0] = z1.max_value + random.randint(1, 100)
                elif strategy == 1:
                    # Out of bounds below min
                    p_nums[0] = z1.min_value - random.randint(1, 100)
                elif strategy == 2:
                    if z1.unique and len(p_nums) > 1:
                        # Force duplicate in unique zone
                        p_nums[1] = p_nums[0]
                    else:
                        # Non-unique zone: mutate count by adding extra number
                        p_nums.append(z1.min_value)
                elif strategy == 3:
                    # Count error (too short or too long)
                    if len(p_nums) > 1:
                        p_nums = p_nums[:-1]
                    else:
                        p_nums = p_nums + [z1.min_value]
                elif strategy == 4:
                    # Inject non-numeric or float item
                    p_nums[0] = random.choice(["NaN", "undefined", 3.14, -999, "bad"])

                valid, err, norm_p, norm_s, fmt = validate_ticket(lt, p_nums, s_nums)
                # If mutation made it invalid, must return valid=False
                assert valid is False, f"Mutated ticket illegally passed! Type: {lt}, p: {p_nums}, s: {s_nums}"
                assert err is not None
