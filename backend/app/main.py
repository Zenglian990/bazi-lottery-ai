"""FastAPI main application entrypoint."""
from contextlib import asynccontextmanager
from datetime import datetime, timezone
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.app.core.config import settings
from backend.app.models.lottery import HealthResponse
from backend.app.db.database import init_db, get_total_draws_count
from backend.app.api.routes_lottery import router as lottery_router
from backend.app.api.routes_bazi import router as bazi_router
from backend.app.api.routes_ai import router as ai_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan event handler for application startup and shutdown."""
    logger.info("Initializing database and seeding if empty...")
    init_db(seed_if_empty=True)
    counts = get_total_draws_count()
    logger.info(f"Database ready. Current draw counts: {counts}")
    yield
    logger.info("Application shutting down...")

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="RESTful API for Lottery Rules Engine, Big Data Frequency, Cold/Hot and Omission Analytics",
    lifespan=lifespan,
)

# CORS middleware configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=settings.CORS_ALLOW_CREDENTIALS,
    allow_methods=settings.CORS_ALLOW_METHODS,
    allow_headers=settings.CORS_ALLOW_HEADERS,
)

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
        },
        timestamp=datetime.now(timezone.utc).isoformat(),
    )

@app.get("/", tags=["root"])
def root_info():
    """Root info endpoint."""
    return {
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "docs_url": "/docs",
        "health_url": "/api/health",
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main.py:app", host="0.0.0.0", port=8000, reload=True)
