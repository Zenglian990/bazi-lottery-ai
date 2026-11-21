"""统一鉴权、防重放 HMAC 校验与可信客户端 IP 提取模块。"""
import hashlib
import hmac
import ipaddress
import os
import re
import time
from fastapi import HTTPException, Request, status

from backend.app.core.config import settings

_HEX64_RE = re.compile(r"^[0-9a-f]{64}$")


def _get_admin_token() -> str:
    """动态读取管理员 Bearer Token（兼容单测 monkeypatch）。"""
    return (settings.ADMIN_TOKEN or os.getenv("ADMIN_TOKEN", "")).strip()


def _get_app_hmac_secret() -> str:
    """动态读取客户端应用 HMAC 密钥（兼容单测 monkeypatch）。"""
    return (settings.APP_HMAC_SECRET or os.getenv("APP_HMAC_SECRET", "")).strip()


def get_trusted_client_ip(req: Request) -> str:
    """
    提取可信客户端 IP，严禁对本地回环或 testclient 豁免。
    从 X-Forwarded-For 右侧取倒数第 TRUSTED_PROXY_COUNT 个合法 IP；无合法 XFF 时回退 req.client.host。
    """
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
    AI 解盘端点双模鉴权（用于 POST /api/ai/divination）：
    (a) Authorization: Bearer <ADMIN_TOKEN>
    (b) 客户端 HMAC-SHA256 防重放签名（X-App-Timestamp + X-App-Signature）
        Canonical String: "{ts}:{METHOD}:{path}:{body_sha256}"
    未配置密钥或校验失败统一返回 401。
    """
    expected_admin = _get_admin_token()
    token = _extract_bearer_token(req)
    if token and expected_admin:
        if hmac.compare_digest(token.encode("utf-8"), expected_admin.encode("utf-8")):
            return "admin"

    hmac_secret = _get_app_hmac_secret()
    ts_raw = req.headers.get("x-app-timestamp", "").strip()
    sig_raw = req.headers.get("x-app-signature", "").strip().lower()

    if hmac_secret and ts_raw and sig_raw and _HEX64_RE.fullmatch(sig_raw):
        try:
            ts = int(ts_raw)
        except ValueError:
            ts = None

        tolerance = int(getattr(settings, "APP_HMAC_TOLERANCE_SECONDS", 300) or 300)
        if ts is not None and abs(int(time.time()) - ts) <= tolerance:
            raw_body = await req.body()
            body_sha256 = hashlib.sha256(raw_body).hexdigest()
            canonical = f"{ts}:{req.method.upper()}:{req.url.path}:{body_sha256}"
            expected_sig = hmac.new(
                hmac_secret.encode("utf-8"),
                canonical.encode("utf-8"),
                hashlib.sha256,
            ).hexdigest()
            if hmac.compare_digest(sig_raw.encode("utf-8"), expected_sig.encode("utf-8")):
                return "app_hmac"

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Authentication required",
        headers={"WWW-Authenticate": "Bearer"},
    )
