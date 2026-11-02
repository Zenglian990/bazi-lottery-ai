"""Empirical Challenger 2 Adversarial Stress Test Suite.

Probes Big Data Statistics invariants, SQLite thread safety / WAL mode,
and the embedded 100-draw lottery seed dataset.
"""
import concurrent.futures
import json
import math
from pathlib import Path
import random
import sqlite3
import time
from typing import List, Dict, Any
import pytest

from backend.app.core.config import settings
from backend.app.db.database import (
    get_db_connection,
    get_draws,
    get_total_draws_count,
    upsert_draws,
)
from backend.app.services.lottery_rules import (
    LOTTERY_RULES,
    get_lottery_rule,
    validate_ticket,
)
from backend.app.services.lottery_stats import (
    analyze_zone_stats,
    calculate_hot_cold,
    calculate_lottery_stats,
    calculate_omission,
    get_statistical_weights,
    get_zone_number_pool,
)


# ============================================================================
# 1. Embedded 100-Draw Seed Dataset Verification
# ============================================================================
class TestSeedDatasetIntegrity:
    """Adversarial verification of backend/app/db/seeds/lottery_seed_100.json."""

    @pytest.fixture(autouse=True)
    def setup_seed_data(self):
        seed_path = Path(settings.SEED_FILE_PATH)
        assert seed_path.exists(), f"Seed file not found at {seed_path}"
        with open(seed_path, "r", encoding="utf-8") as f:
            self.seed_data = json.load(f)

    def test_seed_contains_all_four_lottery_types(self):
        """Must contain exact entries for ssq, dlt, fc3d, qxc."""
        expected_types = {"ssq", "dlt", "fc3d", "qxc"}
        actual_types = set(self.seed_data.keys())
        assert expected_types.issubset(actual_types), (
            f"Missing lottery types in seed: {expected_types - actual_types}"
        )

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    def test_seed_has_exactly_100_draws(self, ltype):
        """Each lottery must have exactly 100 historical draws."""
        draws = self.seed_data[ltype]
        assert len(draws) == 100, f"{ltype} draw count is {len(draws)}, expected 100"

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    def test_seed_issues_strictly_sequential(self, ltype):
        """All issues must be strictly sequential (consecutive step == 1, no gaps, no duplicates)."""
        draws = self.seed_data[ltype]
        issues = [int(d["issue"]) for d in draws]

        # Verify descending order (newest to oldest)
        for i in range(len(issues) - 1):
            diff = issues[i] - issues[i + 1]
            assert diff == 1, (
                f"{ltype} issues are not strictly sequential at index {i}: "
                f"issues[{i}]={issues[i]}, issues[{i+1}]={issues[i+1]}, diff={diff}"
            )

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    def test_seed_all_draws_valid_under_lottery_rules(self, ltype):
        """Every single draw in the seed dataset must pass official lottery rule validation."""
        draws = self.seed_data[ltype]
        invalid_draws = []
        for d in draws:
            is_valid, err, _, _, _ = validate_ticket(
                ltype,
                d.get("primary_numbers", []),
                d.get("secondary_numbers"),
            )
            if not is_valid:
                invalid_draws.append({"issue": d.get("issue"), "error": err})

        assert len(invalid_draws) == 0, (
            f"{ltype} has {len(invalid_draws)} invalid draws in seed! "
            f"Samples: {invalid_draws[:3]}"
        )

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    def test_seed_metadata_and_dates(self, ltype):
        """Draw dates must be well-formed YYYY-MM-DD and non-increasing chronologically."""
        draws = self.seed_data[ltype]
        for i in range(len(draws) - 1):
            date_curr = draws[i]["draw_date"]
            date_prev = draws[i + 1]["draw_date"]
            assert len(date_curr) == 10 and date_curr[4] == "-" and date_curr[7] == "-", (
                f"Invalid date format in {ltype} issue {draws[i]['issue']}: {date_curr}"
            )
            assert date_curr >= date_prev, (
                f"Draw dates out of chronological order in {ltype}: "
                f"{draws[i]['issue']}({date_curr}) vs {draws[i+1]['issue']}({date_prev})"
            )


# ============================================================================
# 2. Statistical Invariance Generator & Verification
# ============================================================================
class TestStatisticalInvariance:
    """
    Statistical Invariance:
    For any valid draw sequence of length N, the sum of total ball counts across
    all numbers in a zone MUST EQUAL N * balls_drawn_per_draw.
    """

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    def test_seed_statistical_invariance(self, ltype):
        """Verify statistical invariance on the 100-draw seed dataset."""
        seed_path = Path(settings.SEED_FILE_PATH)
        with open(seed_path, "r", encoding="utf-8") as f:
            seeds = json.load(f)

        draws = seeds[ltype]
        N = len(draws)
        rule = get_lottery_rule(ltype)
        res = calculate_lottery_stats(ltype, draws)

        # Primary zone
        pri_rule = rule.zones[0]
        pri_counts = sum(s.total_count for s in res.primary_zone.stats)
        expected_pri = N * pri_rule.count
        assert pri_counts == expected_pri, (
            f"{ltype} primary: sum(total_count) = {pri_counts}, expected {expected_pri}"
        )

        # Sum of frequencies approx equal to balls_drawn_per_draw
        pri_freq_sum = sum(s.frequency for s in res.primary_zone.stats)
        assert abs(pri_freq_sum - pri_rule.count) < 0.05, (
            f"{ltype} primary: sum(frequency) = {pri_freq_sum}, expected approx {pri_rule.count}"
        )

        # Secondary zone if present
        if len(rule.zones) > 1:
            sec_rule = rule.zones[1]
            assert res.secondary_zone is not None
            sec_counts = sum(s.total_count for s in res.secondary_zone.stats)
            expected_sec = N * sec_rule.count
            assert sec_counts == expected_sec, (
                f"{ltype} secondary: sum(total_count) = {sec_counts}, expected {expected_sec}"
            )
            sec_freq_sum = sum(s.frequency for s in res.secondary_zone.stats)
            assert abs(sec_freq_sum - sec_rule.count) < 0.05, (
                f"{ltype} secondary: sum(frequency) = {sec_freq_sum}, expected approx {sec_rule.count}"
            )

    @pytest.mark.parametrize("draw_count", [1, 5, 23, 77, 250])
    def test_synthetic_draws_statistical_invariance_generator(self, draw_count):
        """Adversarial random generator: test statistical invariance across varying draw counts."""
        rng = random.Random(42 + draw_count)
        
        # Test SSQ synthetic draws
        ssq_draws = []
        for i in range(draw_count):
            reds = sorted(rng.sample(range(1, 34), 6))
            blues = rng.sample(range(1, 17), 1)
            ssq_draws.append({
                "issue": f"2026{i+1:03d}",
                "draw_date": "2026-01-01",
                "primary_numbers": [f"{r:02d}" for r in reds],
                "secondary_numbers": [f"{b:02d}" for b in blues],
            })

        res = calculate_lottery_stats("ssq", ssq_draws)
        total_red = sum(s.total_count for s in res.primary_zone.stats)
        total_blue = sum(s.total_count for s in res.secondary_zone.stats)
        assert total_red == draw_count * 6, f"Expected {draw_count * 6}, got {total_red}"
        assert total_blue == draw_count * 1, f"Expected {draw_count * 1}, got {total_blue}"

        # Test FC3D synthetic draws (with allowed duplicates 0-9)
        fc3d_draws = []
        for i in range(draw_count):
            digits = [str(rng.randint(0, 9)) for _ in range(3)]
            fc3d_draws.append({
                "issue": f"2026{i+1:03d}",
                "draw_date": "2026-01-01",
                "primary_numbers": digits,
            })
        res_3d = calculate_lottery_stats("fc3d", fc3d_draws)
        total_digits = sum(s.total_count for s in res_3d.primary_zone.stats)
        assert total_digits == draw_count * 3, f"FC3D expected {draw_count * 3}, got {total_digits}"


# ============================================================================
# 3. Omission Invariance & Edge Case Stress Testing
# ============================================================================
class TestOmissionInvariance:
    """
    Omission Invariants:
    1. current omission g_0 >= 0
    2. max omission >= g_0
    3. If number appeared k > 0 times:
       intervals: g_0, g_1, ..., g_k
       sum(g_i) == N - k
       avg_omission == (N - k) / (k + 1)
    4. If k < N, avg_omission > 0.
    5. If k == 0 (never appeared), g_0 == N, max == N, avg == N, desire == 1.0.
    """

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    def test_seed_omission_invariants(self, ltype):
        """Verify omission invariants across all numbers on the 100-draw seed dataset."""
        seed_path = Path(settings.SEED_FILE_PATH)
        with open(seed_path, "r", encoding="utf-8") as f:
            seeds = json.load(f)

        draws = seeds[ltype]
        N = len(draws)
        res = calculate_lottery_stats(ltype, draws)

        zones = [res.primary_zone]
        if res.secondary_zone:
            zones.append(res.secondary_zone)

        for zone in zones:
            for s in zone.stats:
                # Invariance 1: current omission >= 0
                assert s.current_omission >= 0, (
                    f"{ltype} {zone.zone_code} {s.number}: current_omission < 0: {s.current_omission}"
                )
                # Invariance 2: max omission >= current omission
                assert s.max_omission >= s.current_omission, (
                    f"{ltype} {zone.zone_code} {s.number}: max ({s.max_omission}) < curr ({s.current_omission})"
                )
                # Invariance 3: avg omission > 0
                assert s.avg_omission > 0, (
                    f"{ltype} {zone.zone_code} {s.number}: avg_omission <= 0: {s.avg_omission}"
                )
                # Invariance 4: max omission >= avg omission (max of a set >= mean)
                assert s.max_omission >= s.avg_omission, (
                    f"{ltype} {zone.zone_code} {s.number}: max ({s.max_omission}) < avg ({s.avg_omission})"
                )

    def test_omission_mathematical_sum_of_intervals(self):
        """Verify that the sum of all omission intervals equals N - k."""
        # 10 draws: number appears at index 1 and 6 (0-indexed from newest)
        draws = []
        for i in range(10):
            # Draw 1 and 6 contain '01'
            if i in (1, 6):
                nums = ["01", "02", "03", "04", "05", "06"]
            else:
                nums = ["10", "11", "12", "13", "14", "15"]
            draws.append({"issue": str(10 - i), "primary_numbers": nums})

        stats = analyze_zone_stats(
            draws=draws,
            zone_key="primary_numbers",
            number_pool=["01"],
            balls_per_draw=6,
        )
        s = stats[0]
        # k = 2 occurrences: indices [1, 6]
        # intervals:
        # g_0 = 1 (before first appearance)
        # g_1 = 6 - 1 - 1 = 4 (between 1 and 6)
        # g_2 = 9 - 6 = 3 (after index 6 to index 9)
        # sum = 1 + 4 + 3 = 8 == N - k (10 - 2 = 8)
        assert s.current_omission == 1
        assert s.max_omission == 4  # max(1, 4, 3)
        assert s.avg_omission == round(8 / 3, 2)  # 2.67
        assert s.desire_index == round(1 / 2.67, 2)

    def test_adversarial_omission_extreme_never_appeared(self):
        """Adversarial case: number never appears across all draws."""
        draws = [{"issue": str(i), "primary_numbers": ["02", "03", "04", "05", "06", "07"]} for i in range(50)]
        stats = analyze_zone_stats(
            draws=draws,
            zone_key="primary_numbers",
            number_pool=["01"],
            balls_per_draw=6,
        )
        s = stats[0]
        assert s.total_count == 0
        assert s.current_omission == 50
        assert s.max_omission == 50
        assert s.avg_omission == 50.0
        assert s.desire_index == 1.0

    def test_adversarial_omission_extreme_always_appeared(self):
        """Adversarial case: number appears in every single draw (k == N)."""
        draws = [{"issue": str(i), "primary_numbers": ["01", "02", "03", "04", "05", "06"]} for i in range(30)]
        stats = analyze_zone_stats(
            draws=draws,
            zone_key="primary_numbers",
            number_pool=["01"],
            balls_per_draw=6,
        )
        s = stats[0]
        assert s.total_count == 30
        assert s.current_omission == 0
        assert s.max_omission == 0
        assert s.avg_omission == 0.0
        assert s.desire_index == 0.0

    def test_adversarial_omission_appeared_only_at_extremes(self):
        """Adversarial case: appeared only in newest draw (index 0) or oldest draw (index N-1)."""
        N = 20
        # Case A: appeared only in newest draw (index 0)
        draws_newest = [{"issue": str(i), "primary_numbers": ["01"] if i == 0 else ["02"]} for i in range(N)]
        stats_a = analyze_zone_stats(draws_newest, "primary_numbers", ["01"], 1)
        # k = 1, index = [0]. Intervals: g_0 = 0, g_1 = 19 - 0 = 19. Sum = 19 == 20 - 1.
        assert stats_a[0].current_omission == 0
        assert stats_a[0].max_omission == 19
        assert stats_a[0].avg_omission == 9.5  # (20 - 1) / 2 = 9.5
        assert stats_a[0].desire_index == 0.0  # 0 / 9.5 = 0.0

        # Case B: appeared only in oldest draw (index N-1)
        draws_oldest = [{"issue": str(i), "primary_numbers": ["01"] if i == N - 1 else ["02"]} for i in range(N)]
        stats_b = analyze_zone_stats(draws_oldest, "primary_numbers", ["01"], 1)
        # k = 1, index = [19]. Intervals: g_0 = 19, g_1 = 19 - 19 = 0. Sum = 19.
        assert stats_b[0].current_omission == 19
        assert stats_b[0].max_omission == 19
        assert stats_b[0].avg_omission == 9.5
        assert stats_b[0].desire_index == 2.0  # 19 / 9.5 = 2.0


# ============================================================================
# 4. Cold / Hot Partitioning Verification
# ============================================================================
class TestColdHotPartition:
    """
    Cold/Hot Partition Invariants:
    1. A number CANNOT be simultaneously cold and hot.
    2. hot, warm, cold sets must be pairwise disjoint (H ∩ C = ∅, H ∩ W = ∅, C ∩ W = ∅).
    3. H ∪ C ∪ W must equal the entire number pool for that zone.
    """

    @pytest.mark.parametrize("ltype", ["ssq", "dlt", "fc3d", "qxc"])
    @pytest.mark.parametrize("window", [10, 30, 50, 100])
    def test_seed_cold_hot_partition_disjoint_and_complete(self, ltype, window):
        """Verify disjointness and completeness of hot/cold partitions across various windows."""
        seed_path = Path(settings.SEED_FILE_PATH)
        with open(seed_path, "r", encoding="utf-8") as f:
            seeds = json.load(f)

        hc = calculate_hot_cold(ltype, seeds[ltype], window=window)
        rule = get_lottery_rule(ltype)

        # Primary zone
        pri_pool = set(get_zone_number_pool(rule.zones[0]))
        h_pri = set(hc.primary_zone.hot_numbers)
        c_pri = set(hc.primary_zone.cold_numbers)
        w_pri = set(hc.primary_zone.warm_numbers)

        # Invariant 1 & 2: Pairwise disjoint
        assert len(h_pri & c_pri) == 0, f"{ltype} hot & cold overlap in primary zone!"
        assert len(h_pri & w_pri) == 0, f"{ltype} hot & warm overlap in primary zone!"
        assert len(c_pri & w_pri) == 0, f"{ltype} cold & warm overlap in primary zone!"

        # Invariant 3: Complete cover of zone pool
        assert (h_pri | c_pri | w_pri) == pri_pool, (
            f"{ltype} primary: partition does not match full pool!"
        )

        # Secondary zone if present
        if len(rule.zones) > 1:
            sec_pool = set(get_zone_number_pool(rule.zones[1]))
            h_sec = set(hc.secondary_zone.hot_numbers)
            c_sec = set(hc.secondary_zone.cold_numbers)
            w_sec = set(hc.secondary_zone.warm_numbers)

            assert len(h_sec & c_sec) == 0, f"{ltype} hot & cold overlap in secondary zone!"
            assert len(h_sec & w_sec) == 0, f"{ltype} hot & warm overlap in secondary zone!"
            assert len(c_sec & w_sec) == 0, f"{ltype} cold & warm overlap in secondary zone!"
            assert (h_sec | c_sec | w_sec) == sec_pool, (
                f"{ltype} secondary: partition does not match full pool!"
            )

    def test_synthetic_skewed_cold_hot_classification(self):
        """Adversarial extreme skew: only number '01' is drawn repeatedly."""
        # 30 draws where '01' appears every time, other numbers never appear
        draws = [{"issue": str(i), "primary_numbers": ["01", "02", "03", "04", "05", "06"]} for i in range(30)]
        hc = calculate_hot_cold("ssq", draws, window=30)
        h = set(hc.primary_zone.hot_numbers)
        c = set(hc.primary_zone.cold_numbers)
        w = set(hc.primary_zone.warm_numbers)

        assert len(h & c) == 0
        # '01' through '06' have 30 appearances. Theoretical expectation = 30 * (6/33) = 5.45.
        # 30 >= 5.45 * 1.25 (6.81) -> hot!
        assert "01" in h
        # '07' through '33' have 0 appearances. 0 <= 5.45 * 0.75 (4.09) -> cold!
        assert "07" in c
        assert "33" in c


# ============================================================================
# 5. Database Concurrency & Journal Mode Verification
# ============================================================================
class TestDatabaseConcurrencyAndThreadSafety:
    """
    Adversarial verification of SQLite database:
    1. Probe active journal_mode (WAL vs DELETE).
    2. Concurrent read stress test (50 worker threads executing 500 requests).
    3. Concurrent read/write stress test.
    """

    def test_database_journal_mode(self):
        """Inspect and report the current SQLite journal_mode."""
        with get_db_connection() as conn:
            mode = conn.execute("PRAGMA journal_mode;").fetchone()[0]
        # Record finding: is it WAL or DELETE?
        # While DELETE is safe with python threading lock, WAL is optimal for high concurrency
        print(f"\n[CHALLENGER_2 FINDING] SQLite PRAGMA journal_mode: {mode}")
        assert mode.lower() in ("wal", "delete"), f"Unexpected journal_mode: {mode}"

    def test_concurrent_read_stress(self):
        """50 worker threads issuing concurrent read requests across lottery types."""
        errors = []
        total_queries = 200

        def read_worker(worker_id):
            try:
                ltype = ["ssq", "dlt", "fc3d", "qxc"][worker_id % 4]
                draws = get_draws(ltype, limit=25)
                cnt = get_total_draws_count(ltype)
                assert len(draws) == 25, f"Expected 25 draws, got {len(draws)}"
                assert cnt == 100, f"Expected 100 count, got {cnt}"
                return True
            except Exception as e:
                return f"Worker {worker_id}: {type(e).__name__}: {e}"

        start_time = time.time()
        with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
            futures = [executor.submit(read_worker, i) for i in range(total_queries)]
            for f in concurrent.futures.as_completed(futures):
                res = f.result()
                if res is not True:
                    errors.append(res)

        elapsed = time.time() - start_time
        print(f"\n[CHALLENGER_2 CONCURRENCY] {total_queries} concurrent reads executed in {elapsed:.3f}s. Errors: {len(errors)}")
        assert len(errors) == 0, f"Encountered {len(errors)} errors during concurrent reads: {errors[:3]}"

    def test_concurrent_read_write_stress(self):
        """20 reader threads running continuously while 5 writer threads upsert draws."""
        stop_flag = False
        read_errors = []
        write_errors = []
        read_count = [0]
        write_count = [0]

        def reader_task(tid):
            while not stop_flag:
                try:
                    draws = get_draws("ssq", limit=10)
                    assert len(draws) > 0
                    read_count[0] += 1
                    time.sleep(0.001)
                except Exception as e:
                    read_errors.append(f"Reader {tid}: {type(e).__name__}: {e}")

        def writer_task(wid):
            for i in range(5):
                try:
                    temp_draw = [{
                        "issue": f"2099{wid:02d}{i:02d}",
                        "draw_date": "2099-01-01",
                        "primary_numbers": ["01", "02", "03", "04", "05", "06"],
                        "secondary_numbers": ["07"],
                        "sales_amount": 50000,
                        "pool_amount": 100000,
                    }]
                    upsert_draws("ssq", temp_draw)
                    write_count[0] += 1
                    time.sleep(0.002)
                except Exception as e:
                    write_errors.append(f"Writer {wid}: {type(e).__name__}: {e}")

        start_time = time.time()
        with concurrent.futures.ThreadPoolExecutor(max_workers=25) as executor:
            readers = [executor.submit(reader_task, i) for i in range(20)]
            writers = [executor.submit(writer_task, i) for i in range(5)]
            for w in writers:
                w.result()
            stop_flag = True
            for r in readers:
                r.result()

        elapsed = time.time() - start_time
        print(
            f"\n[CHALLENGER_2 RW CONCURRENCY] Finished in {elapsed:.3f}s. "
            f"Reads: {read_count[0]}, Writes: {write_count[0]}, "
            f"Read errors: {len(read_errors)}, Write errors: {len(write_errors)}"
        )

        # Cleanup inserted test draws
        with get_db_connection() as conn:
            conn.execute("DELETE FROM lottery_draws WHERE issue LIKE '2099%'")
            conn.commit()

        assert len(read_errors) == 0, f"Read errors during write contention: {read_errors[:3]}"
        assert len(write_errors) == 0, f"Write errors during write contention: {write_errors[:3]}"
