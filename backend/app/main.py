"""FastAPI main application entrypoint."""
import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, timezone
import json
import logging
import os
import threading
import time
from typing import Optional
import uuid

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.datastructures import MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from backend.app.api.routes_ai import router as ai_router
from backend.app.api.routes_bazi import router as bazi_router
from backend.app.api.routes_lottery import router as lottery_router
from backend.app.core.config import settings
from backend.app.core.security import get_trusted_client_ip
from backend.app.db.database import get_latest_draw_summary, get_total_draws_count, init_db
from backend.app.db.gcs_sync import (
    download_db_from_gcs,
    is_gcs_persistence_enabled,
    upload_db_to_gcs,
)
from backend.app.db.sync_service import sync_lottery_data_detailed_async
from backend.app.models.lottery import HealthResponse
from backend.app.services import ai_service


class _JsonLogFormatter(logging.Formatter):
    """生产环境 GCP Cloud Logging 兼容结构化 JSON 日志格式化器。"""

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "severity": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def _configure_logging() -> None:
    root = logging.getLogger()
    if root.handlers:
        return
    handler = logging.StreamHandler()
    if settings.IS_PRODUCTION:
        handler.setFormatter(_JsonLogFormatter())
    else:
        handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
    root.setLevel(logging.INFO)
    root.addHandler(handler)


_configure_logging()
logger = logging.getLogger(__name__)


class SecurityHeadersMiddleware:
    """纯 ASGI 安全响应头与 Request-ID 追踪中间件（零缓冲兼容 SSE 流）。"""

    _SECURITY_HEADERS = (
        ("Strict-Transport-Security", "max-age=31536000; includeSubDomains"),
        ("X-Content-Type-Options", "nosniff"),
        ("X-Frame-Options", "DENY"),
        ("Referrer-Policy", "no-referrer"),
    )

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        req_headers = dict(scope.get("headers") or [])
        req_id = (
            req_headers.get(b"x-request-id", b"").decode("latin1").strip()
            or req_headers.get(b"x-cloud-trace-context", b"").decode("latin1").split("/", 1)[0].strip()
            or uuid.uuid4().hex[:16]
        )
        start_ts = time.monotonic()

        async def send_with_security_headers(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = MutableHeaders(scope=message)
                for key, value in self._SECURITY_HEADERS:
                    if key not in headers:
                        headers[key] = value
                if "X-Request-ID" not in headers:
                    headers["X-Request-ID"] = req_id
                elapsed_ms = int((time.monotonic() - start_ts) * 1000)
                if "X-Response-Time-Ms" not in headers:
                    headers["X-Response-Time-Ms"] = str(elapsed_ms)
            await send(message)

        await self.app(scope, receive, send_with_security_headers)


class GlobalRateLimitMiddleware:
    """纯 ASGI 单实例全局防刷限流中间件（按可信客户端 IP 与 RATE_LIMIT_DEFAULT_PER_MIN 滑动窗口限流）。"""

    _EXEMPT_PATHS = {"/", "/api/health", "/api/health/detailed"}
    _WINDOW_SECONDS = 60.0
    _MAX_BUCKETS = 4000

    def __init__(self, app: ASGIApp, max_requests_per_min: Optional[int] = None, force_enable: bool = False) -> None:
        self.app = app
        self._limit = max_requests_per_min
        self._force_enable = force_enable
        self._buckets: dict[str, list[float]] = {}
        self._lock = threading.Lock()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        if not (self._force_enable or settings.ENABLE_GLOBAL_RATE_LIMIT):
            await self.app(scope, receive, send)
            return

        method = str(scope.get("method") or "").upper()
        path = str(scope.get("path") or "")
        if method == "OPTIONS" or path in self._EXEMPT_PATHS:
            await self.app(scope, receive, send)
            return

        limit = self._limit if self._limit is not None else settings.RATE_LIMIT_DEFAULT_PER_MIN
        client_ip = get_trusted_client_ip(Request(scope))
        # Starlette 进程内 TestClient 默认使用虚拟主机名 "testclient"，非显式压测时不误伤自动化测试套
        if client_ip == "testclient" and not self._force_enable:
            await self.app(scope, receive, send)
            return
        now = time.monotonic()

        allowed = True
        with self._lock:
            if len(self._buckets) > 1000:
                stale = [
                    k for k, v in self._buckets.items()
                    if not v or (now - v[-1] > self._WINDOW_SECONDS * 2)
                ]
                for k in stale:
                    self._buckets.pop(k, None)
                if len(self._buckets) >= self._MAX_BUCKETS:
                    oldest = sorted(
                        self._buckets.items(),
                        key=lambda item: item[1][-1] if item[1] else 0.0,
                    )[: self._MAX_BUCKETS // 4]
                    for k, _ in oldest:
                        self._buckets.pop(k, None)

            bucket = [t for t in self._buckets.get(client_ip, []) if now - t < self._WINDOW_SECONDS]
            if len(bucket) >= limit:
                allowed = False
            else:
                bucket.append(now)
                self._buckets[client_ip] = bucket

        if not allowed:
            resp = JSONResponse(
                status_code=429,
                content={"detail": "当前访问过于频繁，请稍候片刻再试。"},
                headers={"Retry-After": "15"},
            )
            await resp(scope, receive, send)
            return

        await self.app(scope, receive, send)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：优先从 GCS 恢复 SQLite 快照，按需轻量检查滞后并安全回收后台任务。"""
    if settings.IS_PRODUCTION and not (settings.ADMIN_TOKEN or settings.APP_HMAC_SECRET):
        logger.warning(
            "Production fail-closed warning: ADMIN_TOKEN and APP_HMAC_SECRET are empty; "
            "authenticated endpoints (/api/lottery/sync, /api/ai/divination) will return 401."
        )

    restored_from_gcs = False
    if is_gcs_persistence_enabled():
        restored_from_gcs = await asyncio.to_thread(download_db_from_gcs)

    logger.info("Initializing database (restored_from_gcs=%s)...", restored_from_gcs)
    init_db(seed_if_empty=True)
    counts = get_total_draws_count()
    latest = get_latest_draw_summary()
    logger.info("Database ready. counts=%s latest=%s", counts, latest)

    if is_gcs_persistence_enabled() and not restored_from_gcs:
        await asyncio.to_thread(upload_db_to_gcs)

    await ai_service.init_http_client()

    bg_task = None
    # 生产环境默认交由 Cloud Scheduler 单点定时触发 /api/lottery/sync，避免冷启动爬虫放大；
    # 若显式开启 AUTO_SYNC_ON_STARTUP=true，或所有彩种滞后超阈值且处于非测试环境时才轻量补拉一次。
    max_stale = max(
        (info.get("stale_days") or 0 for info in latest.values()),
        default=0,
    )
    should_startup_sync = settings.AUTO_SYNC_ON_STARTUP or (
        is_gcs_persistence_enabled() and max_stale >= settings.STALE_DATA_THRESHOLD_DAYS
    )
    if should_startup_sync:
        async def _auto_sync_background():
            try:
                await asyncio.sleep(1.0)
                logger.info("Triggering non-blocking background startup sync (max_stale=%dd)...", max_stale)
                synced, failed, uploaded = await sync_lottery_data_detailed_async("all", 50)
                logger.info(
                    "Background startup sync finished: synced=%s failed=%s gcs_uploaded=%s",
                    synced, failed, uploaded,
                )
            except asyncio.CancelledError:
                pass
            except Exception as exc:
                logger.warning("Background startup sync exception: %s", type(exc).__name__)

        bg_task = asyncio.create_task(_auto_sync_background())

    try:
        yield
    finally:
        if bg_task is not None and not bg_task.done():
            bg_task.cancel()
            await asyncio.gather(bg_task, return_exceptions=True)
        await ai_service.close_http_client()
        logger.info("Application shutting down...")


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="RESTful API for Lottery Rules Engine, Big Data Frequency, Cold/Hot and Omission Analytics",
    docs_url=None if settings.IS_PRODUCTION else "/docs",
    redoc_url=None if settings.IS_PRODUCTION else "/redoc",
    openapi_url=None if settings.IS_PRODUCTION else "/openapi.json",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=settings.CORS_ALLOW_CREDENTIALS,
    allow_methods=settings.CORS_ALLOW_METHODS,
    allow_headers=settings.CORS_ALLOW_HEADERS,
)
app.add_middleware(GlobalRateLimitMiddleware)
app.add_middleware(SecurityHeadersMiddleware)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """全局兜底异常处理器，防止堆栈或内部错误文本泄漏至客户端。"""
    logger.error(
        "Unhandled server error on %s %s: %s",
        request.method, request.url.path, type(exc).__name__, exc_info=True,
    )
    return JSONResponse(status_code=500, content={"detail": "Internal server error"})


app.include_router(lottery_router, prefix=settings.API_V1_PREFIX)
app.include_router(bazi_router, prefix=settings.API_V1_PREFIX)
app.include_router(ai_router, prefix=settings.API_V1_PREFIX)


@app.get("/api/health", response_model=HealthResponse, tags=["health"])
def health_check() -> HealthResponse:
    """轻量存活检查端点：返回服务状态、总期数及各彩种最新期号/滞后天数。"""
    counts = get_total_draws_count()
    latest = get_latest_draw_summary()
    return HealthResponse(
        status="healthy",
        service="Lottery Big Data & Rule Engine",
        version=settings.VERSION,
        supported_lotteries=settings.SUPPORTED_LOTTERIES,
        database={
            "status": "connected",
            "total_draws": counts,
            "counts": counts,
            "latest_draws": latest,
            "gcs_persistence": is_gcs_persistence_enabled(),
        },
        total_draws=counts,
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


@app.get("/api/health/detailed", tags=["health"])
def detailed_health_check():
    """深度健康检查端点：校验数据滞后阈值与持久化状态，供定时巡检与客户端诊断。"""
    counts = get_total_draws_count()
    latest = get_latest_draw_summary()
    stale_lotteries = [
        lt for lt, info in latest.items()
        if info.get("stale_days") is not None and info["stale_days"] > settings.STALE_DATA_THRESHOLD_DAYS
    ]
    overall = "degraded" if stale_lotteries else "healthy"
    return {
        "status": overall,
        "version": settings.VERSION,
        "gcs_persistence": is_gcs_persistence_enabled(),
        "stale_threshold_days": settings.STALE_DATA_THRESHOLD_DAYS,
        "stale_lotteries": stale_lotteries,
        "total_draws": counts,
        "latest_draws": latest,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.api_route("/", methods=["GET", "HEAD"], tags=["root"])
def root_info():
    """Root info endpoint."""
    payload = {
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "health_url": "/api/health",
    }
    if not settings.IS_PRODUCTION:
        payload["docs_url"] = "/docs"
    return payload


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=int(os.getenv("PORT", "8080")), reload=True)
