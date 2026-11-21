"""FastAPI main application entrypoint."""
from contextlib import asynccontextmanager
from datetime import datetime, timezone
import asyncio
import logging
import os
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.datastructures import MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from backend.app.core.config import settings
from backend.app.models.lottery import HealthResponse
from backend.app.db.database import init_db, get_total_draws_count
from backend.app.db.sync_service import sync_lottery_data_async
from backend.app.services import ai_service
from backend.app.api.routes_lottery import router as lottery_router
from backend.app.api.routes_bazi import router as bazi_router
from backend.app.api.routes_ai import router as ai_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


class SecurityHeadersMiddleware:
    """纯 ASGI 安全响应头中间件（不缓冲 SSE 流，同时覆盖 CORS 预检与异常响应）。"""

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

        async def send_with_security_headers(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = MutableHeaders(scope=message)
                for key, value in self._SECURITY_HEADERS:
                    if key not in headers:
                        headers[key] = value
            await send(message)

        await self.app(scope, receive, send_with_security_headers)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan event handler for application startup and shutdown."""
    logger.info("Initializing database and seeding if empty...")
    init_db(seed_if_empty=True)
    counts = get_total_draws_count()
    logger.info(f"Database ready. Current draw counts: {counts}")

    await ai_service.init_http_client()

    # Non-blocking background sync on container startup to keep draws fresh
    async def _auto_sync_background():
        try:
            await asyncio.sleep(1.0)
            logger.info("Triggering non-blocking background startup sync...")
            synced = await sync_lottery_data_async("all", 50)
            logger.info(f"Background startup sync completed: {synced}")
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.warning(f"Background startup sync encountered exception: {type(e).__name__}")

    bg_task = asyncio.create_task(_auto_sync_background())
    try:
        yield
    finally:
        if not bg_task.done():
            bg_task.cancel()
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

# CORS middleware configuration (inner layer)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=settings.CORS_ALLOW_CREDENTIALS,
    allow_methods=settings.CORS_ALLOW_METHODS,
    allow_headers=settings.CORS_ALLOW_HEADERS,
)

# Security headers middleware (registered after CORSMiddleware so it wraps outermost)
app.add_middleware(SecurityHeadersMiddleware)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """全局兜底异常处理器，防止堆栈或内部错误文本泄漏至客户端。"""
    logger.error("Unhandled server error on %s %s: %s", request.method, request.url.path, type(exc).__name__, exc_info=True)
    return JSONResponse(status_code=500, content={"detail": "Internal server error"})


# Include lottery API router
app.include_router(lottery_router, prefix=settings.API_V1_PREFIX)
# Include bazi API router
app.include_router(bazi_router, prefix=settings.API_V1_PREFIX)
# Include AI divination API router
app.include_router(ai_router, prefix=settings.API_V1_PREFIX)


@app.get("/api/health", response_model=HealthResponse, tags=["health"])
def health_check() -> HealthResponse:
    """System health check endpoint returning service status and database draw counts."""
    counts = get_total_draws_count()
    return HealthResponse(
        status="healthy",
        service="Lottery Big Data & Rule Engine",
        version=settings.VERSION,
        supported_lotteries=settings.SUPPORTED_LOTTERIES,
        database={
            "status": "connected",
            "total_draws": counts,
            "counts": counts,
        },
        total_draws=counts,
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


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
