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
_CHECKED_EMPTY_PATHS: set[str] = set()
_CHECKED_PATHS_LOCK = threading.Lock()

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
CREATE INDEX IF NOT EXISTS idx_lottery_type_issue_int ON lottery_draws(lottery_type, CAST(issue AS INTEGER) DESC);
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
    conn.execute("PRAGMA busy_timeout = 30000;")
    try:
        yield conn
    finally:
        conn.close()

def init_db(db_path: Optional[Path] = None, seed_if_empty: bool = True) -> None:
    """Initialize SQLite database tables, WAL pragma (once), indexes, and initial seeds."""
    target_path = Path(db_path or settings.DB_PATH)
    target_path.parent.mkdir(parents=True, exist_ok=True)

    with _db_write_lock:
        with get_db_connection(target_path) as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA journal_mode = WAL;")
            cursor.execute("PRAGMA synchronous = NORMAL;")
            cursor.execute("PRAGMA busy_timeout = 30000;")
            cursor.execute(DDL_CREATE_DRAWS_TABLE)
            for stmt in DDL_CREATE_INDEXES.strip().split(";"):
                if stmt.strip():
                    cursor.execute(stmt)
            conn.commit()

    if seed_if_empty:
        seed_database_if_empty(target_path)
    with _CHECKED_PATHS_LOCK:
        _CHECKED_EMPTY_PATHS.add(str(target_path.resolve()))

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

def _query_draws_single_conn(
    target_path: Path,
    lottery_type: str,
    limit: int,
    offset: int,
) -> List[sqlite3.Row]:
    query = """
    SELECT lottery_type, issue, draw_date, primary_numbers, secondary_numbers, sales_amount, pool_amount
    FROM lottery_draws
    WHERE lottery_type = ?
    ORDER BY CAST(issue AS INTEGER) DESC
    LIMIT ? OFFSET ?
    """
    with get_db_connection(target_path) as conn:
        cursor = conn.cursor()
        cursor.execute(query, (lottery_type.lower(), limit, offset))
        return cursor.fetchall()


def get_draws(
    lottery_type: str,
    limit: int = 100,
    offset: int = 0,
    db_path: Optional[Path] = None,
) -> List[Dict[str, Any]]:
    """
    Retrieve historical draws for specified lottery type ordered from newest to oldest.
    Uses a single connection on normal requests; auto-initializes/seeds on missing table or first empty query.
    """
    target_path = Path(db_path or settings.DB_PATH)
    resolved_key = str(target_path.resolve())

    try:
        rows = _query_draws_single_conn(target_path, lottery_type, limit, offset)
    except sqlite3.OperationalError as exc:
        if "no such table" in str(exc).lower():
            logger.info(f"Table missing at {target_path}, initializing database and seeding...")
            init_db(target_path, seed_if_empty=True)
            rows = _query_draws_single_conn(target_path, lottery_type, limit, offset)
        else:
            raise

    if not rows and offset == 0:
        with _CHECKED_PATHS_LOCK:
            already_checked = resolved_key in _CHECKED_EMPTY_PATHS
        if not already_checked:
            logger.info(f"No draws found on first query for '{lottery_type}' at {target_path}, checking seed...")
            inserted = seed_database_if_empty(target_path)
            with _CHECKED_PATHS_LOCK:
                _CHECKED_EMPTY_PATHS.add(resolved_key)
            if inserted > 0:
                rows = _query_draws_single_conn(target_path, lottery_type, limit, offset)

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

def _query_total_draws_count(
    target_path: Path,
    lottery_type: Optional[str] = None,
) -> Union[int, Dict[str, int]]:
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


def get_total_draws_count(
    lottery_type: Optional[str] = None,
    db_path: Optional[Path] = None,
) -> Union[int, Dict[str, int]]:
    """Return count of draws for a specific lottery or all supported types."""
    target_path = Path(db_path or settings.DB_PATH)
    try:
        return _query_total_draws_count(target_path, lottery_type)
    except sqlite3.OperationalError as exc:
        if "no such table" in str(exc).lower():
            logger.info(f"Table missing at {target_path}, initializing database and seeding...")
            init_db(target_path, seed_if_empty=True)
            return _query_total_draws_count(target_path, lottery_type)
        raise


def get_draws_with_total(
    lottery_type: str,
    limit: int = 100,
    offset: int = 0,
    db_path: Optional[Path] = None,
) -> tuple[List[Dict[str, Any]], int]:
    """在单次 SQLite 连接内同时读取分页开奖记录与总期数，减少连接开销。"""
    target_path = Path(db_path or settings.DB_PATH)
    resolved_key = str(target_path.resolve())
    lt = lottery_type.lower()

    def _run_once():
        with get_db_connection(target_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT lottery_type, issue, draw_date, primary_numbers, secondary_numbers, sales_amount, pool_amount
                FROM lottery_draws
                WHERE lottery_type = ?
                ORDER BY CAST(issue AS INTEGER) DESC
                LIMIT ? OFFSET ?
                """,
                (lt, limit, offset),
            )
            rows = cursor.fetchall()
            cursor.execute(
                "SELECT COUNT(*) as cnt FROM lottery_draws WHERE lottery_type = ?",
                (lt,),
            )
            total = int(cursor.fetchone()["cnt"])
            return rows, total

    try:
        rows, total = _run_once()
    except sqlite3.OperationalError as exc:
        if "no such table" in str(exc).lower():
            init_db(target_path, seed_if_empty=True)
            rows, total = _run_once()
        else:
            raise

    if not rows and offset == 0:
        with _CHECKED_PATHS_LOCK:
            already_checked = resolved_key in _CHECKED_EMPTY_PATHS
        if not already_checked:
            inserted = seed_database_if_empty(target_path)
            with _CHECKED_PATHS_LOCK:
                _CHECKED_EMPTY_PATHS.add(resolved_key)
            if inserted > 0:
                rows, total = _run_once()

    results = []
    for r in rows:
        results.append({
            "lottery_type": r["lottery_type"],
            "issue": r["issue"],
            "draw_date": r["draw_date"],
            "primary_numbers": json.loads(r["primary_numbers"]) if r["primary_numbers"] else [],
            "secondary_numbers": json.loads(r["secondary_numbers"]) if r["secondary_numbers"] else None,
            "sales_amount": r["sales_amount"],
            "pool_amount": r["pool_amount"],
        })
    return results, total


def get_latest_draw_summary(db_path: Optional[Path] = None) -> Dict[str, Dict[str, Any]]:
    """查询各彩种最新期号、最新开奖日期与滞后天数（用于健康检查与滞后告警）。"""
    from datetime import date, datetime, timedelta, timezone

    target_path = Path(db_path or settings.DB_PATH)
    china_today = datetime.now(timezone(timedelta(hours=8))).date()
    summary: Dict[str, Dict[str, Any]] = {}

    def _query():
        with get_db_connection(target_path) as conn:
            cursor = conn.cursor()
            for lt in settings.SUPPORTED_LOTTERIES:
                cursor.execute(
                    """
                    SELECT issue, draw_date
                    FROM lottery_draws
                    WHERE lottery_type = ?
                    ORDER BY CAST(issue AS INTEGER) DESC
                    LIMIT 1
                    """,
                    (lt,),
                )
                row = cursor.fetchone()
                if row:
                    draw_date_str = str(row["draw_date"] or "")[:10]
                    stale_days: Optional[int] = None
                    if len(draw_date_str) == 10:
                        try:
                            d_obj = date.fromisoformat(draw_date_str)
                            stale_days = max(0, (china_today - d_obj).days)
                        except ValueError:
                            stale_days = None
                    summary[lt] = {
                        "latest_issue": row["issue"],
                        "latest_draw_date": draw_date_str,
                        "stale_days": stale_days,
                    }
                else:
                    summary[lt] = {
                        "latest_issue": None,
                        "latest_draw_date": None,
                        "stale_days": None,
                    }

    try:
        _query()
    except sqlite3.OperationalError as exc:
        if "no such table" in str(exc).lower():
            init_db(target_path, seed_if_empty=True)
            _query()
        else:
            raise

    return summary
