"""Tier 5 Adversarial Test: T5.3 Concurrency & Database SQLite WAL Invariance.

Stress-tests concurrent access (recommendations, historical stats, AI SSE streaming, and writes)
simulating 20+ simultaneous threads against SQLite:
1. Verifies zero database lock errors (sqlite3.OperationalError: database is locked).
2. Verifies sub-50ms average database query latency.
3. Verifies SQLite WAL mode invariance (PRAGMA journal_mode == 'wal').
4. Validates thread-safe interleaved read-write transactions without deadlock or corruption.
"""
import concurrent.futures
import time
from typing import List, Dict, Any

from backend.app.db.database import (
    get_db_connection,
    get_draws,
    get_total_draws_count,
    upsert_draws,
)
from tests_e2e.harness.client import get_test_client


def test_t5_3_sqlite_journal_mode_wal_invariance():
    """T5.3.1: SQLite must strictly operate in Write-Ahead Logging (WAL) mode."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("PRAGMA journal_mode;")
        journal_mode = cursor.fetchone()[0]

        cursor.execute("PRAGMA synchronous;")
        sync_mode = cursor.fetchone()[0]

    assert str(journal_mode).lower() == "wal", f"Database journal_mode is not WAL: {journal_mode}"
    # Synchronous mode should be NORMAL (1) for high-performance WAL
    assert sync_mode in (1, 2), f"Unexpected synchronous mode: {sync_mode}"


def test_t5_3_24_threads_concurrent_read_stress():
    """T5.3.2: 24 concurrent threads executing 120 database read operations under sub-50ms SLA."""
    num_workers = 24
    num_tasks = 120

    def read_worker(idx: int) -> float:
        t0 = time.time()
        l_type = ["ssq", "dlt", "fc3d", "qxc"][idx % 4]
        draws = get_draws(l_type, limit=50)
        assert len(draws) > 0, f"Expected non-empty draws for {l_type}"
        return time.time() - t0

    with concurrent.futures.ThreadPoolExecutor(max_workers=num_workers) as executor:
        futures = [executor.submit(read_worker, i) for i in range(num_tasks)]
        latencies = [f.result() for f in futures]

    assert len(latencies) == num_tasks
    avg_latency_ms = (sum(latencies) / len(latencies)) * 1000.0

    # Sub-50ms average query latency requirement
    assert avg_latency_ms < 50.0, (
        f"Average SQLite query latency {avg_latency_ms:.2f}ms exceeded 50ms SLA under 24 threads"
    )


def test_t5_3_concurrent_interleaved_read_write_stress():
    """T5.3.3: 20 concurrent threads running mixed read & write transactions with zero lock errors."""
    num_workers = 20
    num_tasks = 100

    def mixed_worker(idx: int) -> Dict[str, Any]:
        t0 = time.time()
        l_type = "ssq" if idx % 2 == 0 else "dlt"
        if idx % 3 == 0:
            # Write transaction
            synthetic_issue = f"T5_STRESS_{idx}_{int(time.time() * 1000)}"
            draw_payload = [{
                "issue": synthetic_issue,
                "draw_date": "2026-11-02",
                "primary_numbers": ["01", "03", "05", "07", "09", "11"] if l_type == "ssq" else ["01", "02", "03", "04", "05"],
                "secondary_numbers": ["16"] if l_type == "ssq" else ["01", "02"],
                "sales_amount": 1000000,
                "pool_amount": 500000000,
            }]
            inserted = upsert_draws(l_type, draw_payload)
            return {"op": "write", "inserted": inserted, "dur": time.time() - t0}
        else:
            # Read transaction
            draws = get_draws(l_type, limit=30)
            return {"op": "read", "count": len(draws), "dur": time.time() - t0}

    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=num_workers) as executor:
            futures = [executor.submit(mixed_worker, i) for i in range(num_tasks)]
            results = [f.result() for f in futures]

        assert len(results) == num_tasks
        write_count = sum(1 for r in results if r["op"] == "write")
        read_count = sum(1 for r in results if r["op"] == "read")
        assert write_count > 0 and read_count > 0
    finally:
        with get_db_connection() as conn:
            conn.execute("DELETE FROM lottery_draws WHERE issue LIKE 'T5_STRESS_%'")
            conn.commit()


def test_t5_3_concurrent_api_endpoints_stress():
    """T5.3.4: 20 concurrent threads stressing REST endpoints (stats, recommend, AI) with zero 500 errors."""
    client = get_test_client()
    num_workers = 20
    num_tasks = 80

    def api_worker(idx: int) -> int:
        op = idx % 5
        if op == 0:
            res = client.get_stats("ssq")
        elif op == 1:
            res = client.get_hot_cold("dlt", window=30)
        elif op == 2:
            res = client.get_omission("fc3d")
        elif op == 3:
            payload = {
                "lottery_type": "ssq",
                "strategy": "balanced",
                "count": 2,
                "birth_datetime": "1990-08-15T08:30:00",
            }
            res = client.post_recommend(payload)
        else:
            payload = {
                "lottery_type": "ssq",
                "recommended_numbers": {"primary_numbers": ["01", "06", "11", "16", "21", "26"], "secondary_numbers": ["08"]},
                "birth_datetime": "1990-08-15T08:30:00",
                "stream": False,
            }
            res = client.post_divination(payload)

        return res["status_code"]

    with concurrent.futures.ThreadPoolExecutor(max_workers=num_workers) as executor:
        futures = [executor.submit(api_worker, i) for i in range(num_tasks)]
        status_codes = [f.result() for f in futures]

    # Every request must be 200 OK
    assert all(code == 200 for code in status_codes), f"Detected non-200 responses: {set(status_codes)}"
