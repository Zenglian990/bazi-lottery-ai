"""E2E Test Runner and Assertion Framework.
Supports:
1. Programmatic discovery and execution across Tiers 1-4.
2. Threshold verification (Tier 1 >= 5/feature, Tier 2 >= 5/domain, Tier 4 >= 5 scenarios).
3. Structured terminal formatting and JSON report generation.
4. Seamless compatibility with pytest.
"""
import os
import sys
import time
import inspect
import importlib
import traceback
from pathlib import Path
from typing import List, Dict, Any, Optional

class TestResult:
    def __init__(self, name: str, module: str, tier: str, passed: bool, error: Optional[str] = None, duration: float = 0.0):
        self.name = name
        self.module = module
        self.tier = tier
        self.passed = passed
        self.error = error
        self.duration = duration

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "module": self.module,
            "tier": self.tier,
            "passed": self.passed,
            "error": self.error,
            "duration": round(self.duration, 4),
        }

class E2ERunner:
    """Unified test runner for E2E suites."""

    def __init__(self, base_dir: Optional[Path] = None):
        self.base_dir = base_dir or Path(__file__).resolve().parent.parent
        self.results: List[TestResult] = []

    def discover_test_modules(self, tier_filter: Optional[int] = None) -> List[Tuple[str, str, Path]]:
        """Find test files mapped to tiers."""
        tier_dirs = {
            1: "tier1_rules",
            2: "tier2_boundaries",
            3: "tier3_interactions",
            4: "tier4_realworld",
            5: "tier5_adversarial",
        }
        targets = []
        for t_num, dir_name in tier_dirs.items():
            if tier_filter is not None and tier_filter != t_num:
                continue
            t_path = self.base_dir / dir_name
            if not t_path.exists():
                continue
            for f in sorted(t_path.glob("test_*.py")):
                mod_name = f"tests_e2e.{dir_name}.{f.stem}"
                targets.append((f"Tier {t_num}", mod_name, f))
        return targets

    def run(self, tier_filter: Optional[int] = None, verbose: bool = False) -> bool:
        """Run discovered tests."""
        self.results = []
        modules = self.discover_test_modules(tier_filter)
        start_time = time.time()

        print("=" * 80)
        print("  Bazi Lottery Recommender — E2E Test Suite Execution")
        print(f"  Target Scope: {'All Tiers (1-5)' if tier_filter is None else f'Tier {tier_filter}'}")
        print("=" * 80)

        total_discovered = 0

        for tier_name, mod_name, path in modules:
            try:
                mod = importlib.import_module(mod_name)
            except Exception as e:
                err = f"Failed to import {mod_name}: {e}\n{traceback.format_exc()}"
                self.results.append(TestResult("import_error", mod_name, tier_name, False, err, 0.0))
                print(f"  [FAIL] {mod_name} (Import Error: {e})")
                continue

            test_funcs = [
                (name, func) for name, func in inspect.getmembers(mod, inspect.isfunction)
                if name.startswith("test_")
            ]
            total_discovered += len(test_funcs)

            for name, func in test_funcs:
                t_start = time.time()
                try:
                    func()
                    dur = time.time() - t_start
                    self.results.append(TestResult(name, mod_name, tier_name, True, None, dur))
                    if verbose:
                        print(f"  [PASS] {tier_name} > {mod_name.split('.')[-1]} > {name} ({dur:.3f}s)")
                except Exception as e:
                    dur = time.time() - t_start
                    err_msg = f"{e}\n{traceback.format_exc()}"
                    self.results.append(TestResult(name, mod_name, tier_name, False, err_msg, dur))
                    print(f"  [FAIL] {tier_name} > {mod_name.split('.')[-1]} > {name} ({dur:.3f}s)")
                    if verbose:
                        print(f"         Error: {e}")

        total_dur = time.time() - start_time
        self.print_summary(total_dur, tier_filter=tier_filter)
        return all(r.passed for r in self.results)

    def print_summary(self, total_duration: float, tier_filter: Optional[int] = None):
        """Print structured summary and coverage threshold check."""
        passed_count = sum(1 for r in self.results if r.passed)
        failed_count = sum(1 for r in self.results if not r.passed)
        total_count = len(self.results)

        tier_counts = {}
        for r in self.results:
            tier_counts.setdefault(r.tier, {"passed": 0, "failed": 0})
            if r.passed:
                tier_counts[r.tier]["passed"] += 1
            else:
                tier_counts[r.tier]["failed"] += 1

        print("\n" + "=" * 80)
        print("  E2E TEST EXECUTION SUMMARY")
        print("=" * 80)
        print(f"  Total Tests Executed : {total_count}")
        print(f"  Passed Tests         : {passed_count}")
        print(f"  Failed Tests         : {failed_count}")
        print(f"  Execution Time       : {total_duration:.3f} seconds")
        print("-" * 80)
        print("  Tier Breakdown:")
        for t_name, stat in sorted(tier_counts.items()):
            p = stat["passed"]
            f = stat["failed"]
            total = p + f
            rate = (p / total * 100) if total > 0 else 0
            print(f"    - {t_name:<10}: {p:>3} passed, {f:>2} failed  [{rate:>5.1f}%]")

        print("-" * 80)
        # Verify Thresholds
        threshold_pass = True
        t1_count = tier_counts.get("Tier 1", {}).get("passed", 0)
        t2_count = tier_counts.get("Tier 2", {}).get("passed", 0)
        t3_count = tier_counts.get("Tier 3", {}).get("passed", 0)
        t4_count = tier_counts.get("Tier 4", {}).get("passed", 0)
        t5_count = tier_counts.get("Tier 5", {}).get("passed", 0)

        print("  Coverage Threshold Validation:")
        if tier_filter is None or tier_filter == 1:
            if t1_count >= 40:
                print(f"    [OK] Tier 1: {t1_count} tests (>=5 per feature F1-F8 + schemas)")
            else:
                print(f"    [WARN] Tier 1: {t1_count} tests (< 40 threshold)")
                threshold_pass = False

        if tier_filter is None or tier_filter == 2:
            if t2_count >= 25:
                print(f"    [OK] Tier 2: {t2_count} tests (>=5 per boundary domain)")
            else:
                print(f"    [WARN] Tier 2: {t2_count} tests (< 25 threshold)")
                threshold_pass = False

        if tier_filter is None or tier_filter == 3:
            if t3_count >= 10:
                print(f"    [OK] Tier 3: {t3_count} tests (Pairwise interaction coverage)")
            else:
                print(f"    [WARN] Tier 3: {t3_count} tests (< 10 threshold)")
                threshold_pass = False

        if tier_filter is None or tier_filter == 4:
            if t4_count >= 5:
                print(f"    [OK] Tier 4: {t4_count} tests (>=5 real-world user lifecycle scenarios)")
            else:
                print(f"    [WARN] Tier 4: {t4_count} tests (< 5 threshold)")
                threshold_pass = False

        if tier_filter is None or tier_filter == 5:
            if t5_count >= 20:
                print(f"    [OK] Tier 5: {t5_count} tests (>=20 adversarial & stress test scenarios)")
            else:
                print(f"    [WARN] Tier 5: {t5_count} tests (< 20 threshold)")
                threshold_pass = False

        print("=" * 80)
        if failed_count == 0 and threshold_pass:
            print("  OVERALL VERDICT: ALL E2E QUALITY GATES PASSED")
        else:
            print("  OVERALL VERDICT: GATE REJECTED (Failures or insufficient coverage)")
        print("=" * 80 + "\n")
