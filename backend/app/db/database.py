"""SQLite thread-safe database manager, migrations, and seeding."""
import sqlite3
import json
import logging
from pathlib import Path
from typing import List, Dict, Any, Optional, Union
from contextlib import contextmanager
import threading

from backend.app.core.config import settings

logger = logging.getLogger(__name__)

# Global lock for write transactions to prevent SQLite database lock collisions
_db_write_lock = threading.Lock()

DDL_CREATE_DRAWS_TABLE = """
CREATE TABLE IF NOT EXISTS lottery_draws (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lottery_type VARCHAR(10) NOT NULL,
    issue VARCHAR(20) NOT NULL,
    draw_date VARCHAR(20) NOT NULL,
    primary_numbers TEXT NOT NULL,
    secondary_numbers TEXT,
    sales_amount BIGINT DEFAULT 0,
    pool_amount BIGINT DEFAULT 0,
    raw_metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(lottery_type, issue)
);
"""

DDL_CREATE_INDEXES = """
CREATE INDEX IF NOT EXISTS idx_lottery_type_issue ON lottery_draws(lottery_type, issue DESC);
CREATE INDEX IF NOT EXISTS idx_lottery_type_date ON lottery_draws(lottery_type, draw_date DESC);
"""

UPSERT_DRAW_SQL = """
INSERT INTO lottery_draws (
    lottery_type, issue, draw_date, primary_numbers, secondary_numbers, sales_amount, pool_amount, raw_metadata
) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(lottery_type, issue) DO UPDATE SET
    draw_date = excluded.draw_date,
    primary_numbers = excluded.primary_numbers,
    secondary_numbers = excluded.secondary_numbers,
    sales_amount = excluded.sales_amount,
    pool_amount = excluded.pool_amount,
    raw_metadata = excluded.raw_metadata,
    updated_at = CURRENT_TIMESTAMP;
"""

@contextmanager
def get_db_connection(db_path: Optional[Path] = None):
    """Context manager for thread-safe SQLite connection."""
    target_path = Path(db_path or settings.DB_PATH)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(target_path), timeout=30.0, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL;")
    conn.execute("PRAGMA synchronous = NORMAL;")
    try:
        yield conn
    finally:
        conn.close()

def init_db(db_path: Optional[Path] = None, seed_if_empty: bool = True) -> None:
    """Initialize SQLite database tables, indexes, and initial seeds."""
    target_path = Path(db_path or settings.DB_PATH)
    target_path.parent.mkdir(parents=True, exist_ok=True)

    with _db_write_lock:
        with get_db_connection(target_path) as conn:
            cursor = conn.cursor()
            cursor.execute(DDL_CREATE_DRAWS_TABLE)
            for stmt in DDL_CREATE_INDEXES.strip().split(";"):
                if stmt.strip():
                    cursor.execute(stmt)
            conn.commit()

    if seed_if_empty:
        seed_database_if_empty(target_path)

def seed_database_if_empty(db_path: Optional[Path] = None) -> int:
    """Load pre-bundled 100-draw JSON seed dataset into SQLite if empty."""
    target_path = Path(db_path or settings.DB_PATH)
    seed_path = Path(settings.SEED_FILE_PATH)
    if not seed_path.exists():
        logger.warning(f"Seed file not found at {seed_path}, skipping initial seeding.")
        return 0

    try:
        with open(seed_path, "r", encoding="utf-8") as f:
            seed_data = json.load(f)
    except Exception as e:
        logger.error(f"Failed to read seed file {seed_path}: {e}")
        return 0

    total_inserted = 0
    with _db_write_lock:
        with get_db_connection(target_path) as conn:
            cursor = conn.cursor()
            for l_type, draws in seed_data.items():
                cursor.execute(
                    "SELECT COUNT(*) as cnt FROM lottery_draws WHERE lottery_type = ?",
                    (l_type,),
                )
                cnt = cursor.fetchone()["cnt"]
                if cnt == 0 and draws:
                    params_list = []
                    for d in draws:
                        primary_json = json.dumps(d.get("primary_numbers", []))
                        sec = d.get("secondary_numbers")
                        secondary_json = json.dumps(sec) if sec is not None else None
                        params_list.append((
                            l_type,
                            str(d.get("issue")),
                            str(d.get("draw_date", "")),
                            primary_json,
                            secondary_json,
                            int(d.get("sales_amount", 0) or 0),
                            int(d.get("pool_amount", 0) or 0),
                            None,
                        ))
                    cursor.executemany(UPSERT_DRAW_SQL, params_list)
                    conn.commit()
                    total_inserted += len(params_list)
                    logger.info(f"Seeded {len(params_list)} historical draws for lottery '{l_type}'")

    return total_inserted

def upsert_draws(lottery_type: str, draws: List[Dict[str, Any]], db_path: Optional[Path] = None) -> int:
    """Upsert lottery draw records into SQLite."""
    if not draws:
        return 0
    target_path = Path(db_path or settings.DB_PATH)
    params_list = []
    for d in draws:
        primary_json = json.dumps(d.get("primary_numbers", []))
        sec = d.get("secondary_numbers")
        secondary_json = json.dumps(sec) if sec is not None else None
        params_list.append((
            lottery_type.lower(),
            str(d.get("issue")),
            str(d.get("draw_date", "")),
            primary_json,
            secondary_json,
            int(d.get("sales_amount", 0) or 0),
            int(d.get("pool_amount", 0) or 0),
            json.dumps(d.get("raw_metadata")) if d.get("raw_metadata") else None,
        ))

    with _db_write_lock:
        with get_db_connection(target_path) as conn:
            cursor = conn.cursor()
            cursor.executemany(UPSERT_DRAW_SQL, params_list)
            conn.commit()
    return len(params_list)

def get_draws(
    lottery_type: str,
    limit: int = 100,
    offset: int = 0,
    db_path: Optional[Path] = None,
) -> List[Dict[str, Any]]:
    """
    Retrieve historical draws for specified lottery type ordered from newest to oldest.
    """
    target_path = Path(db_path or settings.DB_PATH)
    query = """
    SELECT lottery_type, issue, draw_date, primary_numbers, secondary_numbers, sales_amount, pool_amount
    FROM lottery_draws
    WHERE lottery_type = ?
    ORDER BY issue DESC
    LIMIT ? OFFSET ?
    """
    with get_db_connection(target_path) as conn:
        cursor = conn.cursor()
        cursor.execute(query, (lottery_type.lower(), limit, offset))
        rows = cursor.fetchall()

    results = []
    for r in rows:
        primary = json.loads(r["primary_numbers"]) if r["primary_numbers"] else []
        sec = json.loads(r["secondary_numbers"]) if r["secondary_numbers"] else None
        results.append({
            "lottery_type": r["lottery_type"],
            "issue": r["issue"],
            "draw_date": r["draw_date"],
            "primary_numbers": primary,
            "secondary_numbers": sec,
            "sales_amount": r["sales_amount"],
            "pool_amount": r["pool_amount"],
        })
    return results

def get_total_draws_count(
    lottery_type: Optional[str] = None,
    db_path: Optional[Path] = None,
) -> Union[int, Dict[str, int]]:
    """Return count of draws for a specific lottery or all supported types."""
    target_path = Path(db_path or settings.DB_PATH)
    with get_db_connection(target_path) as conn:
        cursor = conn.cursor()
        if lottery_type:
            cursor.execute(
                "SELECT COUNT(*) as cnt FROM lottery_draws WHERE lottery_type = ?",
                (lottery_type.lower(),),
            )
            return cursor.fetchone()["cnt"]
        else:
            cursor.execute(
                "SELECT lottery_type, COUNT(*) as cnt FROM lottery_draws GROUP BY lottery_type"
            )
            rows = cursor.fetchall()
            counts = {lt: 0 for lt in settings.SUPPORTED_LOTTERIES}
            for r in rows:
                counts[r["lottery_type"]] = r["cnt"]
            return counts
