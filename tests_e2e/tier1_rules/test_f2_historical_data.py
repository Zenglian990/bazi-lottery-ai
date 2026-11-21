"""Tier 1 Test: F2 Historical Draw Big Data Storage & Sync.
Verifies SQLite storage, 100+ seeds, UPSERT conflict resolution, pagination, and issue ordering.
"""
import tempfile
from pathlib import Path
from backend.app.db.database import (
    init_db,
    get_draws,
    upsert_draws,
    get_total_draws_count,
    seed_database_if_empty,
)

def test_f2_seed_dataset_initialization():
    """F2.1: Verify bundled seed data contains >=100 draws for each supported lottery."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        db_path = Path(tmp_dir) / "test_lottery.db"
        init_db(db_path=db_path, seed_if_empty=True)

        counts = get_total_draws_count(db_path=db_path)
        assert isinstance(counts, dict)
        for lt in ["ssq", "dlt", "fc3d", "qxc"]:
            assert lt in counts
            assert counts[lt] >= 100, f"Expected >=100 draws for {lt}, got {counts[lt]}"

def test_f2_draw_record_schema_and_json_serialization():
    """F2.2: Verify historical draw record schema fields and number lists."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        db_path = Path(tmp_dir) / "test_lottery.db"
        init_db(db_path=db_path, seed_if_empty=True)

        draws = get_draws("ssq", limit=1, db_path=db_path)
        assert len(draws) == 1
        d = draws[0]
        assert "issue" in d
        assert "draw_date" in d
        assert "primary_numbers" in d
        assert "secondary_numbers" in d
        assert isinstance(d["primary_numbers"], list)
        assert len(d["primary_numbers"]) == 6
        assert isinstance(d["secondary_numbers"], list)
        assert len(d["secondary_numbers"]) == 1

def test_f2_historical_ordering_newest_first():
    """F2.3: Verify historical queries return draws sorted by issue descending."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        db_path = Path(tmp_dir) / "test_lottery.db"
        init_db(db_path=db_path, seed_if_empty=True)

        draws = get_draws("ssq", limit=10, db_path=db_path)
        assert len(draws) >= 2
        for i in range(len(draws) - 1):
            assert int(draws[i]["issue"]) > int(draws[i + 1]["issue"]), "Issues must be sorted descending"

def test_f2_upsert_conflict_handling():
    """F2.4: Verify UPSERT updates existing issue without duplicating records."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        db_path = Path(tmp_dir) / "test_lottery.db"
        init_db(db_path=db_path, seed_if_empty=False)

        # Insert new record
        sample_draw = {
            "issue": "2026999",
            "draw_date": "2026-11-01",
            "primary_numbers": ["01", "02", "03", "04", "05", "06"],
            "secondary_numbers": ["07"],
            "sales_amount": 1000,
            "pool_amount": 5000,
        }
        upsert_draws("ssq", [sample_draw], db_path=db_path)
        assert get_total_draws_count("ssq", db_path=db_path) == 1

        # Re-insert same issue with updated sales amount
        updated_draw = dict(sample_draw)
        updated_draw["sales_amount"] = 99999
        upsert_draws("ssq", [updated_draw], db_path=db_path)

        # Count must remain 1, sales amount must be updated
        assert get_total_draws_count("ssq", db_path=db_path) == 1
        fetched = get_draws("ssq", limit=1, db_path=db_path)[0]
        assert fetched["sales_amount"] == 99999

def test_f2_pagination_offset_limit():
    """F2.5: Verify limit and offset parameters paginate accurately."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        db_path = Path(tmp_dir) / "test_lottery.db"
        init_db(db_path=db_path, seed_if_empty=True)

        page1 = get_draws("dlt", limit=5, offset=0, db_path=db_path)
        page2 = get_draws("dlt", limit=5, offset=5, db_path=db_path)

        assert len(page1) == 5
        assert len(page2) == 5

        page1_issues = [d["issue"] for d in page1]
        page2_issues = [d["issue"] for d in page2]

        # No issue overlap between page 1 and page 2
        assert len(set(page1_issues).intersection(set(page2_issues))) == 0

def test_f2_total_draws_count_aggregation():
    """F2.6: Verify get_total_draws_count returns exact scalar when lottery_type is specified."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        db_path = Path(tmp_dir) / "test_lottery.db"
        init_db(db_path=db_path, seed_if_empty=True)

        cnt = get_total_draws_count("fc3d", db_path=db_path)
        assert isinstance(cnt, int)
        assert cnt >= 100
