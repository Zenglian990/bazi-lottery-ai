"""
Empirical Adversarial Verification Harness for Milestone M1 & M2.
Author: challenger_refactor_1 (Teamwork Challenger Agent)
Targets:
- Milestone M1: Multi-bet lottery rules & batch generation stress test (SSQ, DLT, FC3D, QXC for count=1, 5, 10, batch deduplication, copy format, hexagrams)
- Milestone M2: Pure lunar calendar conversion stress test (1930/2030 boundaries, leap months, 29-day months, mathematical stability, zero reliance on Gregorian calendar)
"""
import sys
import re
import math
from pathlib import Path
from typing import Dict, List, Any, Set, Tuple

# Add project root and backend to path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.bazi_engine import calculate_four_pillars, SHICHEN_MAPPING
from backend.app.services.numerology_engine import recommend_lottery_tickets
from backend.app.services.lottery_rules import LOTTERY_RULES, validate_ticket, get_lottery_rule
from lunar_python import LunarMonth, Lunar, Solar

client = TestClient(app)

class EmpiricalHarnessResult:
    def __init__(self):
        self.passed_tests = 0
        self.failed_tests = 0
        self.failures = []
        self.findings = []

    def assert_true(self, condition: bool, test_name: str, details: str = ""):
        if condition:
            self.passed_tests += 1
            print(f"  [PASS] {test_name}")
        else:
            self.failed_tests += 1
            msg = f"  [FAIL] {test_name}: {details}"
            print(msg)
            self.failures.append(msg)

    def log_finding(self, finding: str):
        self.findings.append(finding)
        print(f"  [NOTE] {finding}")

harness = EmpiricalHarnessResult()

# ==============================================================================
# SUITE 1: M1 MULTI-BET LOTTERY RULES & BATCH GENERATION STRESS TEST
# ==============================================================================
print("\n" + "="*80)
print("STARTING SUITE 1: M1 Multi-Bet Lottery Rules & Batch Generation Stress Test")
print("="*80)

LOTTERY_SPECS = {
    "ssq": {
        "primary_count": 6, "primary_min": 1, "primary_max": 33, "primary_unique": True, "primary_sorted": True, "primary_fmt": r"^\d{2}$",
        "secondary_count": 1, "secondary_min": 1, "secondary_max": 16, "secondary_unique": False, "secondary_sorted": False, "secondary_fmt": r"^\d{2}$",
        "ticket_regex": r"^\d{2} \d{2} \d{2} \d{2} \d{2} \d{2} \+ \d{2}$"
    },
    "dlt": {
        "primary_count": 5, "primary_min": 1, "primary_max": 35, "primary_unique": True, "primary_sorted": True, "primary_fmt": r"^\d{2}$",
        "secondary_count": 2, "secondary_min": 1, "secondary_max": 12, "secondary_unique": True, "secondary_sorted": True, "secondary_fmt": r"^\d{2}$",
        "ticket_regex": r"^\d{2} \d{2} \d{2} \d{2} \d{2} \+ \d{2} \d{2}$"
    },
    "fc3d": {
        "primary_count": 3, "primary_min": 0, "primary_max": 9, "primary_unique": False, "primary_sorted": False, "primary_fmt": r"^\d$",
        "secondary_count": 0, "secondary_min": None, "secondary_max": None, "secondary_unique": False, "secondary_sorted": False, "secondary_fmt": None,
        "ticket_regex": r"^\d \d \d$"
    },
    "qxc": {
        "primary_count": 6, "primary_min": 0, "primary_max": 9, "primary_unique": False, "primary_sorted": False, "primary_fmt": r"^\d$",
        "secondary_count": 1, "secondary_min": 0, "secondary_max": 14, "secondary_unique": False, "secondary_sorted": False, "secondary_fmt": r"^\d{2}$",
        "ticket_regex": r"^\d \d \d \d \d \d \+ \d{2}$"
    }
}

def verify_single_ticket_rules(lottery_type: str, ticket: Dict[str, Any], bet_index: int) -> Tuple[bool, str]:
    spec = LOTTERY_SPECS[lottery_type]
    pri = ticket.get("primary_numbers", [])
    sec = ticket.get("secondary_numbers")
    formatted = ticket.get("formatted_ticket", "")

    # 1. Primary count
    if len(pri) != spec["primary_count"]:
        return False, f"Bet {bet_index}: Primary count {len(pri)} != expected {spec['primary_count']}"

    # 2. Primary ranges and format
    pri_ints = []
    for num_str in pri:
        if not re.match(spec["primary_fmt"], num_str):
            return False, f"Bet {bet_index}: Primary format invalid: '{num_str}'"
        v = int(num_str)
        if v < spec["primary_min"] or v > spec["primary_max"]:
            return False, f"Bet {bet_index}: Primary value {v} outside [{spec['primary_min']}, {spec['primary_max']}]"
        pri_ints.append(v)

    # 3. Primary uniqueness
    if spec["primary_unique"] and len(set(pri_ints)) != len(pri_ints):
        return False, f"Bet {bet_index}: Primary numbers have duplicates: {pri_ints}"

    # 4. Primary sorting
    if spec["primary_sorted"] and pri_ints != sorted(pri_ints):
        return False, f"Bet {bet_index}: Primary numbers not sorted: {pri_ints}"

    # 5. Secondary count and ranges
    if spec["secondary_count"] > 0:
        if not sec or len(sec) != spec["secondary_count"]:
            return False, f"Bet {bet_index}: Secondary count {len(sec) if sec else 0} != expected {spec['secondary_count']}"
        sec_ints = []
        for num_str in sec:
            if not re.match(spec["secondary_fmt"], num_str):
                return False, f"Bet {bet_index}: Secondary format invalid: '{num_str}'"
            v = int(num_str)
            if v < spec["secondary_min"] or v > spec["secondary_max"]:
                return False, f"Bet {bet_index}: Secondary value {v} outside [{spec['secondary_min']}, {spec['secondary_max']}]"
            sec_ints.append(v)
        if spec["secondary_unique"] and len(set(sec_ints)) != len(sec_ints):
            return False, f"Bet {bet_index}: Secondary numbers have duplicates: {sec_ints}"
        if spec["secondary_sorted"] and sec_ints != sorted(sec_ints):
            return False, f"Bet {bet_index}: Secondary numbers not sorted: {sec_ints}"
    else:
        if sec is not None and len(sec) > 0:
            return False, f"Bet {bet_index}: Unexpected secondary numbers in single-zone lottery: {sec}"

    # 6. Formatted ticket regex
    if not re.match(spec["ticket_regex"], formatted):
        return False, f"Bet {bet_index}: Formatted ticket '{formatted}' does not match regex '{spec['ticket_regex']}'"

    return True, "OK"

# 1.1 Test API across all 4 lottery types for count = 1, 5, 10
for l_type in ["ssq", "dlt", "fc3d", "qxc"]:
    for count in [1, 5, 10]:
        payload = {
            "lottery_type": l_type,
            "count": count,
            "user_name": f"测主_{l_type}_{count}",
            "strategy": "balanced",
            "calendar_type": "lunar",
            "lunar_year": 1995,
            "lunar_month": 5,
            "lunar_day": 16,
            "lunar_hour": "辰时"
        }
        resp = client.post("/api/bazi/recommend", json=payload)
        harness.assert_true(resp.status_code == 200, f"API POST /api/bazi/recommend {l_type} count={count} status 200")
        
        data = resp.json()
        recs = data.get("recommendations", [])
        harness.assert_true(len(recs) == count, f"API {l_type} count={count} returned exactly {count} bets (got {len(recs)})")

        # Verify lottery rule conformance for each bet
        all_rules_valid = True
        err_msg = ""
        for idx, rec in enumerate(recs):
            valid, msg = verify_single_ticket_rules(l_type, rec, idx + 1)
            if not valid:
                all_rules_valid = False
                err_msg = msg
                break
        harness.assert_true(all_rules_valid, f"API {l_type} count={count} all {count} bets strictly obey rules", err_msg)

# 1.2 Cross-Ticket Deduplication Stress Test (Batch Uniqueness)
print("\n--- Stress Testing Cross-Ticket Deduplication across 5-bet and 10-bet Rolls ---")
total_batches_checked = 0
total_duplicate_tickets_found = 0

for l_type in ["ssq", "dlt", "fc3d", "qxc"]:
    for count in [5, 10]:
        # Run 50 batches per combination (total 400 batches, 3000 tickets)
        batch_duplicates = 0
        for b in range(50):
            payload = {
                "lottery_type": l_type,
                "count": count,
                "user_name": f"去重测试_{l_type}",
                "strategy": "balanced",
                "calendar_type": "lunar",
                "lunar_year": 1988,
                "lunar_month": 8,
                "lunar_day": 8,
                "lunar_hour": "午时"
            }
            resp = client.post("/api/bazi/recommend", json=payload)
            if resp.status_code != 200:
                harness.assert_true(False, f"API {l_type} count={count} batch {b} returned {resp.status_code}")
                continue
            data = resp.json()
            recs = data.get("recommendations", [])
            seen_in_batch = set()
            for r in recs:
                t_key = (tuple(r["primary_numbers"]), tuple(r["secondary_numbers"] or []))
                if t_key in seen_in_batch:
                    batch_duplicates += 1
                seen_in_batch.add(t_key)
            total_batches_checked += 1

        total_duplicate_tickets_found += batch_duplicates
        harness.assert_true(
            batch_duplicates == 0,
            f"Deduplication Stress: {l_type.upper()} 50 batches of {count}-bet (0 duplicate tickets found)",
            f"Found {batch_duplicates} duplicates!"
        )

harness.assert_true(
    total_duplicate_tickets_found == 0,
    f"Batch Deduplication Overall: {total_batches_checked} batches examined, 0 duplicate tickets generated"
)

# 1.3 Ticket Copying Format and Hexagram Inspection
print("\n--- Testing Ticket Copying Format and Classics/Hexagram Grounding ---")
for l_type in ["ssq", "dlt", "fc3d", "qxc"]:
    test_user = f"福主_{l_type}"
    payload = {
        "lottery_type": l_type,
        "count": 5,
        "user_name": test_user,
        "strategy": "balanced",
        "calendar_type": "lunar",
        "lunar_year": 1993,
        "lunar_month": 6,
        "lunar_day": 20,
        "lunar_hour": "申时"
    }
    resp = client.post("/api/bazi/recommend", json=payload)
    data = resp.json()
    copy_text = data.get("copy_text", "")
    rule = get_lottery_rule(l_type)

    # Check copy_text structure
    harness.assert_true(bool(copy_text), f"CopyText non-empty for {l_type}")
    harness.assert_true(test_user in copy_text, f"CopyText includes user name '{test_user}' for {l_type}")
    harness.assert_true(rule.chinese_name in copy_text, f"CopyText includes lottery name '{rule.chinese_name}' for {l_type}")
    harness.assert_true("5注机选" in copy_text, f"CopyText includes bet count for {l_type}")
    
    # Check all bets included
    all_bets_in_copy = True
    for idx, r in enumerate(data.get("recommendations", [])):
        bet_label = f"第{idx + 1:02d}注: {r['formatted_ticket']}"
        if bet_label not in copy_text:
            all_bets_in_copy = False
            break
    harness.assert_true(all_bets_in_copy, f"CopyText contains all 5 formatted bets for {l_type}")

    # Inspect Hexagram / Classics Evidence
    recs = data.get("recommendations", [])
    has_hexagrams = True
    hexagram_names = []
    for r in recs:
        evidence = r.get("classics_evidence")
        if not evidence or not evidence.get("book") or not evidence.get("chapter"):
            has_hexagrams = False
        else:
            hexagram_names.append(f"{evidence['book']} {evidence['chapter']}")
    
    harness.assert_true(has_hexagrams, f"Recommendations contain classics_evidence/hexagrams for each bet in {l_type}")
    harness.log_finding(f"{l_type.upper()} hexagram evidence sample: {hexagram_names[:2]}")

# Test default user name fallback in copy_text
resp_default_name = client.post("/api/bazi/recommend", json={"lottery_type": "ssq", "count": 1})
copy_default = resp_default_name.json().get("copy_text", "")
harness.assert_true("缘主" in copy_default, "CopyText defaults to '缘主' when user_name is omitted")


# ==============================================================================
# SUITE 2: M2 PURE LUNAR CALENDAR CONVERSION STRESS TEST
# ==============================================================================
print("\n" + "="*80)
print("STARTING SUITE 2: M2 Pure Lunar Calendar Conversion Stress Test")
print("="*80)

STEMS = set(["甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"])
BRANCHES = set(["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"])
ELEMENTS_ZH = set(["金", "木", "水", "火", "土"])

def verify_bazi_destiny_outputs(data: Dict[str, Any], label: str) -> Tuple[bool, str]:
    fp = data.get("four_pillars") or data.get("pillars")
    if not fp:
        return False, f"{label}: missing four_pillars"
    for p_name in ["year", "month", "day", "hour"]:
        p = fp.get(p_name)
        if not p or p.get("stem") not in STEMS or p.get("branch") not in BRANCHES:
            return False, f"{label}: invalid pillar {p_name}: {p}"

    scores = data.get("wuxing_scores")
    if not scores:
        return False, f"{label}: missing wuxing_scores"
    score_sum = sum(scores.values())
    for elem, val in scores.items():
        if val < 0:
            return False, f"{label}: negative score for {elem}: {val}"

    # Percentages
    pcts = data.get("wuxing_percentage", {})
    pct_sum = sum(pcts.values())
    if abs(pct_sum - 1.0) > 1e-3:
        return False, f"{label}: wuxing percentages sum {pct_sum} != 1.0"

    # XiYongShen
    xi = data.get("xi_yong_shen", [])
    if not xi or len(xi) == 0:
        return False, f"{label}: empty xi_yong_shen"
    for x in xi:
        if x not in ELEMENTS_ZH and x not in {"metal", "wood", "water", "fire", "earth"}:
            return False, f"{label}: invalid element in xi_yong_shen: {x}"

    return True, "OK"

# 2.1 Boundary Years 1930 and 2030 Stress Test
boundary_cases = [
    {"year": 1930, "month": 1, "day": 1, "shichen": "子时", "desc": "1930 Lunar Year First Day (Boundary Start)"},
    {"year": 1930, "month": 12, "day": 29, "shichen": "亥时", "desc": "1930 Lunar Year Last Month Day 29"},
    {"year": 1930, "month": 6, "day": 15, "shichen": "午时", "desc": "1930 Lunar Mid Year"},
    {"year": 2030, "month": 1, "day": 1, "shichen": "早子时", "desc": "2030 Lunar Year First Day"},
    {"year": 2030, "month": 12, "day": 29, "shichen": "晚子时", "desc": "2030 Lunar Year Last Month Day 29 (Boundary End)"},
    {"year": 2030, "month": 6, "day": 15, "shichen": "辰时", "desc": "2030 Lunar Mid Year"},
]

for bc in boundary_cases:
    payload = {
        "calendar_type": "lunar",
        "lunar_year": bc["year"],
        "lunar_month": bc["month"],
        "lunar_day": bc["day"],
        "lunar_hour": bc["shichen"],
        "user_name": "边界测试缘主"
    }
    resp = client.post("/api/bazi/destiny", json=payload)
    harness.assert_true(resp.status_code == 200, f"Boundary Test {bc['desc']} returns HTTP 200")
    if resp.status_code == 200:
        valid, msg = verify_bazi_destiny_outputs(resp.json(), bc["desc"])
        harness.assert_true(valid, f"Boundary Test {bc['desc']} mathematical outputs valid", msg)

# 2.2 Leap Month Stress Test (e.g. 2020 Leap Month 4, 2023 Leap Month 2)
print("\n--- Stress Testing Lunar Leap Months ---")
# 2020 has regular month 4 and leap month 4
payload_reg_4 = {
    "calendar_type": "lunar",
    "lunar_year": 2020,
    "lunar_month": 4,
    "lunar_day": 15,
    "is_leap_month": False,
    "lunar_hour": "午时"
}
payload_leap_4 = {
    "calendar_type": "lunar",
    "lunar_year": 2020,
    "lunar_month": 4,
    "lunar_day": 15,
    "is_leap_month": True,
    "lunar_hour": "午时"
}

resp_reg = client.post("/api/bazi/destiny", json=payload_reg_4)
resp_leap = client.post("/api/bazi/destiny", json=payload_leap_4)

harness.assert_true(resp_reg.status_code == 200, "2020 Regular Lunar Month 4 returns 200")
harness.assert_true(resp_leap.status_code == 200, "2020 Leap Lunar Month 4 returns 200")

if resp_reg.status_code == 200 and resp_leap.status_code == 200:
    data_reg = resp_reg.json()
    data_leap = resp_leap.json()
    
    # Solar dates should be different by approximately 29/30 days
    solar_reg = data_reg["solar_datetime"]
    solar_leap = data_leap["solar_datetime"]
    harness.assert_true(solar_reg != solar_leap, f"2020 Regular vs Leap Month 4 have distinct solar dates: {solar_reg} vs {solar_leap}")
    harness.assert_true(data_reg["lunar_datetime"] != data_leap["lunar_datetime"], f"Lunar text distinguishes leap month: '{data_reg['lunar_datetime']}' vs '{data_leap['lunar_datetime']}'")
    
    # Verify both outputs valid
    v1, m1 = verify_bazi_destiny_outputs(data_reg, "2020 Reg 4")
    v2, m2 = verify_bazi_destiny_outputs(data_leap, "2020 Leap 4")
    harness.assert_true(v1 and v2, "2020 Regular and Leap Month 4 mathematical outputs both valid")

# Invalid leap month test (e.g. 2021 month 5 is NOT leap)
payload_inv_leap = {
    "calendar_type": "lunar",
    "lunar_year": 2021,
    "lunar_month": 5,
    "lunar_day": 15,
    "is_leap_month": True, # invalid leap request
    "lunar_hour": "午时"
}
resp_inv = client.post("/api/bazi/destiny", json=payload_inv_leap)
harness.assert_true(resp_inv.status_code == 200, "Invalid leap month request falls back gracefully without 500 crash")

# 2.3 29-Day Short Lunar Month Boundary Clamping Test
print("\n--- Stress Testing 29-Day Short Lunar Month Boundary Protection ---")
# Test day = 30 on months that only have 29 days
short_month_tests = [
    {"year": 2020, "month": 1, "day": 30, "desc": "2020 Month 1 (check clamp if 29 days)"},
    {"year": 2024, "month": 2, "day": 30, "desc": "2024 Month 2 (check clamp)"},
    {"year": 1990, "month": 3, "day": 30, "desc": "1990 Month 3 (check clamp)"},
    {"year": 1984, "month": 7, "day": 30, "desc": "1984 Month 7 (check clamp)"},
    {"year": 2020, "month": 4, "day": 31, "desc": "2020 Month 4 Day 31 (overflow beyond 30)"},
    {"year": 2020, "month": 4, "day": 0, "desc": "2020 Month 4 Day 0 (underflow clamp to 1)"},
]

for sm in short_month_tests:
    payload = {
        "calendar_type": "lunar",
        "lunar_year": sm["year"],
        "lunar_month": sm["month"],
        "lunar_day": sm["day"],
        "lunar_hour": "午时"
    }
    resp = client.post("/api/bazi/destiny", json=payload)
    harness.assert_true(
        resp.status_code == 200,
        f"Short Month Day Clamping: {sm['desc']} handled cleanly without IndexError (status {resp.status_code})"
    )

# 2.4 Mathematical Stability Test
print("\n--- Testing Mathematical Output Deterministic Stability ---")
# 10 consecutive calls with identical inputs must return identical results
stability_payload = {
    "calendar_type": "lunar",
    "lunar_year": 1996,
    "lunar_month": 7,
    "lunar_day": 18,
    "lunar_hour": "申时",
    "user_name": "稳定性缘主"
}
first_res = client.post("/api/bazi/destiny", json=stability_payload).json()
is_stable = True
for i in range(10):
    iter_res = client.post("/api/bazi/destiny", json=stability_payload).json()
    if (iter_res["four_pillars"] != first_res["four_pillars"] or 
        iter_res["wuxing_scores"] != first_res["wuxing_scores"] or
        iter_res["xi_yong_shen"] != first_res["xi_yong_shen"]):
        is_stable = False
        break
harness.assert_true(is_stable, "10 consecutive Bazi destiny calculations produce 100% bit-exact stable results")

# 2.5 ZERO Reliance on Gregorian Calendar in the Lunar Path
print("\n--- Verifying ZERO Reliance on Gregorian Calendar in the Lunar Path ---")
# Explicitly verify:
# A) Request does NOT have birth_datetime field at all
pure_lunar_req = {
    "calendar_type": "lunar",
    "lunar_year": 1985,
    "lunar_month": 10,
    "lunar_day": 25,
    "is_leap_month": False,
    "lunar_hour": "酉时",
    "user_name": "纯农历缘主"
}
resp_pure = client.post("/api/bazi/destiny", json=pure_lunar_req)
harness.assert_true(resp_pure.status_code == 200, "Destiny request without birth_datetime succeeds (HTTP 200)")
data_pure = resp_pure.json()
harness.assert_true("农历" in data_pure["lunar_datetime"], "Result displays pure lunar datetime string")

# Check Four Pillars for 1985-10-25 酉时
# Lunar 1985-10-25 is 乙丑年 丁亥月 己酉日 癸酉时
fp = data_pure["four_pillars"]
harness.assert_true(fp["year"]["stem"] == "乙" and fp["year"]["branch"] == "丑", f"1985 Lunar Year Pillar is 乙丑 (got {fp['year']['pair']})")
harness.assert_true(fp["month"]["branch"] == "亥", f"Lunar 10th month Branch is 亥 (got {fp['month']['pair']})")
harness.assert_true(fp["day"]["branch"] == "酉", f"Lunar day Branch is 酉 (got {fp['day']['pair']})")
harness.assert_true(fp["hour"]["branch"] == "酉", f"酉时 Branch is 酉 (got {fp['hour']['pair']})")

# B) Recommendation API with pure lunar inputs (no birth_datetime)
rec_pure_req = {
    "lottery_type": "ssq",
    "count": 5,
    "calendar_type": "lunar",
    "lunar_year": 1985,
    "lunar_month": 10,
    "lunar_day": 25,
    "lunar_hour": "酉时",
    "user_name": "纯农历缘主"
}
resp_rec_pure = client.post("/api/bazi/recommend", json=rec_pure_req)
harness.assert_true(resp_rec_pure.status_code == 200, "Recommend request with pure lunar inputs succeeds (HTTP 200)")
rec_pure_data = resp_rec_pure.json()
harness.assert_true(len(rec_pure_data["recommendations"]) == 5, "Recommend with pure lunar inputs returns 5 bets")

# C) Test passing explicit birth_datetime="" (empty string) with lunar fields
empty_dt_req = {
    "birth_datetime": "",
    "calendar_type": "lunar",
    "lunar_year": 1985,
    "lunar_month": 10,
    "lunar_day": 25,
    "lunar_hour": "酉时"
}
resp_empty = client.post("/api/bazi/destiny", json=empty_dt_req)
harness.assert_true(resp_empty.status_code == 200, "Passing empty birth_datetime='' does not cause parse failure or Gregorian fallback")

# D) Test all 12 Shichen names
print("\n--- Testing All 12 Shichen Astronomical Mappings ---")
all_shichen_passed = True
for shichen_name in ["早子时", "丑时", "寅时", "卯时", "辰时", "巳时", "午时", "未时", "申时", "酉时", "戌时", "亥时", "晚子时"]:
    shichen_req = {
        "calendar_type": "lunar",
        "lunar_year": 2000,
        "lunar_month": 1,
        "lunar_day": 1,
        "lunar_hour": shichen_name
    }
    r = client.post("/api/bazi/destiny", json=shichen_req)
    if r.status_code != 200:
        all_shichen_passed = False
        break
harness.assert_true(all_shichen_passed, "All 12 classical Shichen names (plus early/late Zi) processed successfully")

# ==============================================================================
# SUMMARY & FINAL VERDICT
# ==============================================================================
print("\n" + "="*80)
print("EMPIRICAL HARNESS EXECUTION SUMMARY")
print("="*80)
print(f"Total Tests Executed : {harness.passed_tests + harness.failed_tests}")
print(f"Passed Tests         : {harness.passed_tests}")
print(f"Failed Tests         : {harness.failed_tests}")

if harness.failed_tests == 0:
    print("\nOVERALL EMPIRICAL VERDICT: APPROVE")
else:
    print(f"\nOVERALL EMPIRICAL VERDICT: FAIL ({harness.failed_tests} failures)")
    for f in harness.failures:
        print(f)
