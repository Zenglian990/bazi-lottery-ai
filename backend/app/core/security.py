"""统一鉴权、防重放 Nonce HMAC 校验、请求体大小保护与可信客户端 IP 提取模块。"""
import hashlib
import hmac
import ipaddress
import os
import re
import threading
import time
from typing import Dict

from fastapi import HTTPException, Request, status

from backend.app.core.config import settings

_HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
_NONCE_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")

# 进程内防重放缓存：记录在 TTL 窗口内已使用过的 Nonce / 签名指纹（Cloud Run maxScale=1 下全局有效）
_REPLAY_CACHE: Dict[str, float] = {}
_REPLAY_LOCK = threading.Lock()
_REPLAY_MAX_ENTRIES = 10000


def clear_replay_cache() -> None:
    """清理防重放缓存（仅供测试夹具使用）。"""
    with _REPLAY_LOCK:
        _REPLAY_CACHE.clear()


def _check_and_record_replay(replay_key: str, ttl_seconds: int) -> bool:
    """检查 replay_key 是否已使用；若未使用则记录并返回 True，否则返回 False 拒绝重放。"""
    now = time.monotonic()
    with _REPLAY_LOCK:
        # 周期性清理过期条目并限制最大容量，防止内存膨胀
        if len(_REPLAY_CACHE) >= _REPLAY_MAX_ENTRIES:
            expired = [k for k, exp in _REPLAY_CACHE.items() if exp <= now]
            for k in expired:
                _REPLAY_CACHE.pop(k, None)
            if len(_REPLAY_CACHE) >= _REPLAY_MAX_ENTRIES:
                # 仍超限时淘汰最早过期的 20%
                oldest = sorted(_REPLAY_CACHE.items(), key=lambda item: item[1])[: _REPLAY_MAX_ENTRIES // 5]
                for k, _ in oldest:
                    _REPLAY_CACHE.pop(k, None)

        existing_exp = _REPLAY_CACHE.get(replay_key)
        if existing_exp is not None and existing_exp > now:
            return False
        _REPLAY_CACHE[replay_key] = now + max(60, ttl_seconds)
        return True


def _get_admin_token() -> str:
    """动态读取管理员写库 Bearer Token（仅用于 /api/lottery/sync 及管理员特权）。"""
    return (settings.ADMIN_TOKEN or os.getenv("ADMIN_TOKEN", "")).strip()


def _get_ai_service_token() -> str:
    """动态读取独立 AI 服务令牌（最小权限拆分；兼容向后 ADMIN_TOKEN）。"""
    return (getattr(settings, "AI_SERVICE_TOKEN", "") or os.getenv("AI_SERVICE_TOKEN", "")).strip()


def _get_app_hmac_secret() -> str:
    """动态读取客户端应用 HMAC 密钥（兼容单测 monkeypatch）。"""
    return (settings.APP_HMAC_SECRET or os.getenv("APP_HMAC_SECRET", "")).strip()


def get_trusted_client_ip(req: Request) -> str:
    """
    提取可信客户端 IP，严禁对本地回环或 testclient 豁免。
    仅在 TRUST_X_FORWARDED_FOR=True 时从 X-Forwarded-For 右侧取倒数第 TRUSTED_PROXY_COUNT 个合法 IP。
    """
    trust_xff = bool(getattr(settings, "TRUST_X_FORWARDED_FOR", True))
    if trust_xff:
        forwarded = req.headers.get("x-forwarded-for", "")
        if forwarded:
            parts = [p.strip() for p in forwarded.split(",") if p.strip()]
            if parts:
                proxy_count = max(1, int(getattr(settings, "TRUSTED_PROXY_COUNT", 1) or 1))
                idx = max(0, len(parts) - proxy_count)
                candidate = parts[idx]
                try:
                    return str(ipaddress.ip_address(candidate))
                except ValueError:
                    pass
    return req.client.host if (req.client and req.client.host) else "unknown"


def _extract_bearer_token(req: Request) -> str:
    """解析 Authorization: Bearer <token> 请求头。"""
    auth_header = req.headers.get("authorization", "")
    if not auth_header:
        return ""
    parts = auth_header.split(None, 1)
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1].strip()
    return ""


async def _read_bounded_body(req: Request, max_bytes: int) -> bytes:
    """在鉴权读取 Body 前校验长度上限，防止未认证超大请求体造成内存 DoS。"""
    content_length = req.headers.get("content-length")
    if content_length:
        try:
            if int(content_length) > max_bytes:
                raise HTTPException(
                    status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                    detail="Request payload too large",
                )
        except ValueError:
            pass
    raw_body = await req.body()
    if len(raw_body) > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail="Request payload too large",
        )
    return raw_body


def require_admin_auth(req: Request) -> str:
    """
    管理员 Bearer Token 鉴权（用于 POST /api/lottery/sync）。
    未配置 ADMIN_TOKEN 时遵循 fail-closed 原则返回 401。
    """
    expected = _get_admin_token()
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = _extract_bearer_token(req)
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if not hmac.compare_digest(token.encode("utf-8"), expected.encode("utf-8")):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Invalid authentication credentials",
        )
    return "admin"


async def require_ai_auth(req: Request) -> str:
    """
    AI 解盘端点多模防重放鉴权（用于 POST /api/ai/divination）：
    (a) Authorization: Bearer <AI_SERVICE_TOKEN>（推荐最小权限令牌）或 <ADMIN_TOKEN>
    (b) 客户端 HMAC-SHA256 一次性防重放签名：
        - 推荐格式（带 Nonce）：X-App-Timestamp + X-App-Nonce + X-App-Signature
          Canonical String: "{ts}:{nonce}:{METHOD}:{path}:{body_sha256}"
        - 兼容格式（无 Nonce）：X-App-Timestamp + X-App-Signature
          Canonical String: "{ts}:{METHOD}:{path}:{body_sha256}"
        两种格式均纳入防重放缓存，同一签名/Nonce 在窗口内不可重复提交。
    未配置密钥或校验失败统一返回 401；超长 Body 返回 413。
    """
    max_body = int(getattr(settings, "MAX_AI_BODY_BYTES", 65536) or 65536)
    token = _extract_bearer_token(req)
    if token:
        ai_token = _get_ai_service_token()
        if ai_token and hmac.compare_digest(token.encode("utf-8"), ai_token.encode("utf-8")):
            await _read_bounded_body(req, max_body)
            return "ai_service"
        expected_admin = _get_admin_token()
        if expected_admin and hmac.compare_digest(token.encode("utf-8"), expected_admin.encode("utf-8")):
            await _read_bounded_body(req, max_body)
            return "admin"

    hmac_secret = _get_app_hmac_secret()
    ts_raw = req.headers.get("x-app-timestamp", "").strip()
    nonce_raw = req.headers.get("x-app-nonce", "").strip()
    sig_raw = req.headers.get("x-app-signature", "").strip().lower()

    if hmac_secret and ts_raw and sig_raw and _HEX64_RE.fullmatch(sig_raw):
        try:
            ts = int(ts_raw)
        except ValueError:
            ts = None

        tolerance = int(getattr(settings, "APP_HMAC_TOLERANCE_SECONDS", 300) or 300)
        if ts is not None and abs(int(time.time()) - ts) <= tolerance:
            raw_body = await _read_bounded_body(req, max_body)
            body_sha256 = hashlib.sha256(raw_body).hexdigest()

            if nonce_raw:
                if not _NONCE_RE.fullmatch(nonce_raw):
                    raise HTTPException(
                        status_code=status.HTTP_401_UNAUTHORIZED,
                        detail="Invalid nonce format",
                        headers={"WWW-Authenticate": "Bearer"},
                    )
                canonical = f"{ts}:{nonce_raw}:{req.method.upper()}:{req.url.path}:{body_sha256}"
                replay_key = f"nonce:{nonce_raw}"
            else:
                canonical = f"{ts}:{req.method.upper()}:{req.url.path}:{body_sha256}"
                replay_key = f"sig:{ts}:{sig_raw}"

            expected_sig = hmac.new(
                hmac_secret.encode("utf-8"),
                canonical.encode("utf-8"),
                hashlib.sha256,
            ).hexdigest()
            if hmac.compare_digest(sig_raw.encode("utf-8"), expected_sig.encode("utf-8")):
                if not _check_and_record_replay(replay_key, ttl_seconds=tolerance * 2):
                    raise HTTPException(
                        status_code=status.HTTP_401_UNAUTHORIZED,
                        detail="Replay attack detected",
                        headers={"WWW-Authenticate": "Bearer"},
                    )
                return "app_hmac"

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Authentication required",
        headers={"WWW-Authenticate": "Bearer"},
    )
