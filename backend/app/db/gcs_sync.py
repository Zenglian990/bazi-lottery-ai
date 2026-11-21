"""零额外依赖的 Google Cloud Storage SQLite 快照持久化模块。

专用于 Cloud Run 单实例（maxScale=1）零成本持久化：
- 冷启动时通过 Cloud Run Metadata Server 获取默认服务账号 OAuth2 Token，从 GCS 下载历史 SQLite 库；
- 同步入库新期号后，使用 sqlite3.Connection.backup() 导出一致性单文件快照（彻底避免 WAL 碎片不一致），
  并以原子对象覆盖上传至 GCS，实现缩容至 0 后零数据丢失。
"""
import logging
import os
from pathlib import Path
import sqlite3
import tempfile
import time
from typing import Optional
from urllib.parse import quote

import httpx

from backend.app.core.config import settings

logger = logging.getLogger(__name__)

_METADATA_TOKEN_URL = (
    "http://metadata.google.internal/computeMetadata/v1/instance/"
    "service-accounts/default/token"
)
_CACHED_TOKEN: Optional[str] = None
_CACHED_TOKEN_EXPIRY: float = 0.0
_GCS_TIMEOUT = httpx.Timeout(20.0, connect=5.0, read=15.0, write=15.0)


def is_gcs_persistence_enabled() -> bool:
    """判断是否已配置 GCS 状态持久化桶。"""
    bucket = (getattr(settings, "GCS_STATE_BUCKET", "") or os.getenv("GCS_STATE_BUCKET", "")).strip()
    return bool(bucket)


def _get_gcs_target() -> tuple[str, str]:
    bucket = (getattr(settings, "GCS_STATE_BUCKET", "") or os.getenv("GCS_STATE_BUCKET", "")).strip()
    obj_name = (
        getattr(settings, "GCS_STATE_OBJECT", "")
        or os.getenv("GCS_STATE_OBJECT", "bazi-lottery/lottery.db")
    ).strip().lstrip("/")
    return bucket, obj_name


def _fetch_access_token(client: httpx.Client) -> Optional[str]:
    """优先使用环境变量静态测试令牌，否则向 GCP Metadata Server 申请短期 OAuth2 Token。"""
    global _CACHED_TOKEN, _CACHED_TOKEN_EXPIRY

    env_token = os.getenv("GCS_ACCESS_TOKEN", "").strip()
    if env_token:
        return env_token

    now = time.monotonic()
    if _CACHED_TOKEN and now < _CACHED_TOKEN_EXPIRY - 60:
        return _CACHED_TOKEN

    try:
        resp = client.get(
            _METADATA_TOKEN_URL,
            headers={"Metadata-Flavor": "Google"},
            timeout=3.0,
        )
        resp.raise_for_status()
        payload = resp.json()
        token = payload.get("access_token")
        expires_in = int(payload.get("expires_in", 1800) or 1800)
        if token:
            _CACHED_TOKEN = str(token)
            _CACHED_TOKEN_EXPIRY = now + max(120, expires_in)
            return _CACHED_TOKEN
    except Exception as exc:
        logger.warning("Unable to obtain GCP Metadata access token for GCS sync: %s", type(exc).__name__)
    return None


def download_db_from_gcs(
    db_path: Optional[Path] = None,
    client: Optional[httpx.Client] = None,
) -> bool:
    """
    容器启动时从 GCS 下载最新 SQLite 快照到本地路径。
    若未启用 GCS 或云端对象尚不存在（404），返回 False 并安全降级到本地种子初始化。
    """
    if not is_gcs_persistence_enabled():
        return False

    bucket, obj_name = _get_gcs_target()
    target_path = Path(db_path or settings.DB_PATH)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    encoded_obj = quote(obj_name, safe="")
    url = f"https://storage.googleapis.com/storage/v1/b/{bucket}/o/{encoded_obj}?alt=media"

    def _download(active_client: httpx.Client) -> bool:
        token = _fetch_access_token(active_client)
        if not token:
            return False
        resp = active_client.get(url, headers={"Authorization": f"Bearer {token}"})
        if resp.status_code == 404:
            logger.info("No existing GCS snapshot at gs://%s/%s; will initialize from seed.", bucket, obj_name)
            return False
        resp.raise_for_status()
        content = resp.content
        if len(content) < 100 or not content.startswith(b"SQLite format 3\x00"):
            logger.warning("Downloaded GCS object is not a valid SQLite database, ignoring.")
            return False

        tmp_file = target_path.with_suffix(".gcs_download.tmp")
        tmp_file.write_bytes(content)
        # 清理旧 WAL/SHM 避免与新下载的主文件页头冲突
        for suffix in ("-wal", "-shm"):
            sidecar = Path(str(target_path) + suffix)
            if sidecar.exists():
                try:
                    sidecar.unlink()
                except OSError:
                    pass
        os.replace(tmp_file, target_path)
        logger.info("Restored SQLite snapshot from gs://%s/%s (%d bytes).", bucket, obj_name, len(content))
        return True

    try:
        if client is not None:
            return _download(client)
        with httpx.Client(timeout=_GCS_TIMEOUT, follow_redirects=False) as temp_client:
            return _download(temp_client)
    except Exception as exc:
        logger.warning("Failed to download SQLite snapshot from GCS: %s", type(exc).__name__)
        return False


def _create_consistent_sqlite_snapshot(source_db: Path, snapshot_path: Path) -> None:
    """使用 SQLite 在线备份 API 生成独立、无 WAL 依赖的一致性单文件快照。"""
    src_conn = sqlite3.connect(str(source_db), timeout=30.0)
    dst_conn = sqlite3.connect(str(snapshot_path), timeout=30.0)
    try:
        src_conn.backup(dst_conn)
        dst_conn.commit()
    finally:
        dst_conn.close()
        src_conn.close()


def upload_db_to_gcs(
    db_path: Optional[Path] = None,
    client: Optional[httpx.Client] = None,
) -> bool:
    """
    将本地 SQLite 库通过一致性快照上传至 GCS（单实例写后持久落盘）。
    未启用 GCS 或上传失败时不阻断主业务，返回布尔状态。
    """
    if not is_gcs_persistence_enabled():
        return False

    bucket, obj_name = _get_gcs_target()
    source_path = Path(db_path or settings.DB_PATH)
    if not source_path.exists():
        return False

    encoded_obj = quote(obj_name, safe="")
    url = (
        f"https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o"
        f"?uploadType=media&name={encoded_obj}"
    )

    tmp_dir = Path(tempfile.mkdtemp(prefix="bazi_gcs_snap_"))
    snap_path = tmp_dir / "snapshot.db"
    try:
        _create_consistent_sqlite_snapshot(source_path, snap_path)
        payload = snap_path.read_bytes()

        def _upload(active_client: httpx.Client) -> bool:
            token = _fetch_access_token(active_client)
            if not token:
                return False
            resp = active_client.post(
                url,
                content=payload,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/octet-stream",
                },
            )
            resp.raise_for_status()
            logger.info("Uploaded SQLite snapshot to gs://%s/%s (%d bytes).", bucket, obj_name, len(payload))
            return True

        if client is not None:
            return _upload(client)
        with httpx.Client(timeout=_GCS_TIMEOUT, follow_redirects=False) as temp_client:
            return _upload(temp_client)
    except Exception as exc:
        logger.warning("Failed to upload SQLite snapshot to GCS: %s", type(exc).__name__)
        return False
    finally:
        try:
            if snap_path.exists():
                snap_path.unlink()
            tmp_dir.rmdir()
        except OSError:
            pass
