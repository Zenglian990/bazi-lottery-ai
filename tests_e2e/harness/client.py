"""HTTP API Test Client wrapper for End-to-End testing.

严格透传 FastAPI 后端真实响应（移除所有吞错假 200 Mock 回退），
并为需要 HMAC 鉴权的 AI 解盘接口自动构造合法带 Nonce 的测试签名。
"""
import hashlib
import hmac
import json
import os
import time
import uuid
from typing import Any, Dict, List, Optional

import httpx
from fastapi.testclient import TestClient

from backend.app.core.config import settings
from backend.app.main import app


class ApiClient:
    """Unified E2E HTTP client supporting in-process ASGI TestClient and live HTTP server."""

    def __init__(self, base_url: Optional[str] = None):
        self.base_url = base_url or os.getenv("TEST_API_BASE_URL", "")
        self.mode = "live" if self.base_url else "in-process"
        if self.mode == "in-process":
            # 为进程内 E2E 注入确定性测试 HMAC 密钥（不会修改生产环境配置）
            if not settings.APP_HMAC_SECRET:
                settings.APP_HMAC_SECRET = "e2e-in-process-hmac-secret-key"
            self._client = TestClient(app, raise_server_exceptions=False)
        else:
            self._client = httpx.Client(base_url=self.base_url, timeout=15.0)

    def _build_ai_hmac_headers(self, method: str, path: str, body_bytes: bytes) -> Dict[str, str]:
        secret = (settings.APP_HMAC_SECRET or os.getenv("APP_HMAC_SECRET", "")).strip()
        if not secret:
            return {"Content-Type": "application/json"}
        ts = str(int(time.time()))
        nonce = uuid.uuid4().hex
        body_sha256 = hashlib.sha256(body_bytes).hexdigest()
        canonical = f"{ts}:{nonce}:{method.upper()}:{path}:{body_sha256}"
        sig = hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()
        return {
            "Content-Type": "application/json",
            "X-App-Timestamp": ts,
            "X-App-Nonce": nonce,
            "X-App-Signature": sig,
        }

    def request(self, method: str, path: str, **kwargs) -> Any:
        return self._client.request(method, path, **kwargs)

    def _safe_json(self, resp: Any) -> Any:
        ct = resp.headers.get("content-type", "")
        if "application/json" in ct:
            try:
                return resp.json()
            except Exception:
                return resp.text
        return resp.text

    def get_health(self) -> Dict[str, Any]:
        resp = self.request("GET", "/api/health")
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def get_rules(self, lottery_type: Optional[str] = None) -> Dict[str, Any]:
        path = f"/api/lottery/rules/{lottery_type}" if lottery_type else "/api/lottery/rules"
        resp = self.request("GET", path)
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def get_history(self, lottery_type: str, limit: int = 100, page: int = 1) -> Dict[str, Any]:
        resp = self.request("GET", f"/api/lottery/history/{lottery_type}", params={"limit": limit, "page": page})
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def get_stats(self, lottery_type: str, limit: int = 100, window: int = 30) -> Dict[str, Any]:
        resp = self.request("GET", f"/api/lottery/stats/{lottery_type}", params={"limit": limit, "window": window})
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def get_hot_cold(self, lottery_type: str, window: int = 30) -> Dict[str, Any]:
        resp = self.request("GET", f"/api/lottery/hot-cold/{lottery_type}", params={"window": window})
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def get_omission(self, lottery_type: str) -> Dict[str, Any]:
        resp = self.request("GET", f"/api/lottery/omission/{lottery_type}")
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def validate_ticket(
        self,
        lottery_type: str,
        primary_numbers: List[Any],
        secondary_numbers: Optional[List[Any]] = None,
    ) -> Dict[str, Any]:
        """POST /api/lottery/validate/{lottery_type}（真实透传后端 200/400/422 响应，无假 Mock 回退）。"""
        req_body = {
            "primary_numbers": primary_numbers,
            "secondary_numbers": secondary_numbers,
        }
        resp = self.request("POST", f"/api/lottery/validate/{lottery_type}", json=req_body)
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def post_destiny(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        """POST /api/bazi/destiny（真实透传后端响应）。"""
        resp = self.request("POST", "/api/bazi/destiny", json=payload)
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def post_recommend(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        """POST /api/bazi/recommend（真实透传后端响应）。"""
        resp = self.request("POST", "/api/bazi/recommend", json=payload)
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}

    def post_divination(self, payload: Dict[str, Any], authenticated: bool = True) -> Dict[str, Any]:
        """POST /api/ai/divination（带真实 HMAC-Nonce 签名，绝不吞掉 401/422 造假 200）。"""
        body_bytes = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = (
            self._build_ai_hmac_headers("POST", "/api/ai/divination", body_bytes)
            if authenticated
            else {"Content-Type": "application/json"}
        )
        resp = self.request("POST", "/api/ai/divination", content=body_bytes, headers=headers)
        return {"status_code": resp.status_code, "data": self._safe_json(resp)}


_client_instance: Optional[ApiClient] = None


def get_test_client() -> ApiClient:
    """Singleton factory for ApiClient."""
    global _client_instance
    if _client_instance is None:
        _client_instance = ApiClient()
    return _client_instance
