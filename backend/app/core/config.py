"""Core application configuration."""
import json
import os
from pathlib import Path
from typing import List, Optional

BASE_DIR = Path(__file__).resolve().parent.parent.parent
APP_DIR = BASE_DIR / "app"
DB_DIR = APP_DIR / "db"
SEEDS_DIR = DB_DIR / "seeds"


def _parse_origins(raw: Optional[str]) -> List[str]:
    """解析 CORS_ORIGINS 环境变量（支持 JSON 数组或逗号分隔），并过滤通配符 '*'。"""
    if not raw or not raw.strip():
        return []
    text = raw.strip()
    candidates: List[str] = []
    if text.startswith("["):
        try:
            parsed = json.loads(text)
            if isinstance(parsed, list):
                candidates = [str(item).strip() for item in parsed if isinstance(item, str)]
        except (ValueError, TypeError):
            candidates = []
    else:
        candidates = [item.strip() for item in text.split(",")]
    return [origin for origin in candidates if origin and origin != "*"]


def _parse_bool(raw: Optional[str], default: bool = False) -> bool:
    if raw is None or not str(raw).strip():
        return default
    return str(raw).strip().lower() in ("1", "true", "yes", "on")


class Settings:
    PROJECT_NAME: str = "曾练彩票助手 - 易道乾坤与大数据分析中枢"
    VERSION: str = "1.4.0"
    API_V1_PREFIX: str = "/api"

    # 运行环境配置（默认生产模式 fail-closed）
    ENV: str = os.getenv("ENV", "production").strip().lower()
    IS_PRODUCTION: bool = ENV in ("prod", "production")

    # 安全与鉴权配置（支持将写库 ADMIN_TOKEN 与 AI_SERVICE_TOKEN 最小权限分离）
    ADMIN_TOKEN: str = os.getenv("ADMIN_TOKEN", "").strip()
    AI_SERVICE_TOKEN: str = os.getenv("AI_SERVICE_TOKEN", "").strip()
    APP_HMAC_SECRET: str = os.getenv("APP_HMAC_SECRET", "").strip()
    APP_HMAC_TOLERANCE_SECONDS: int = int(os.getenv("APP_HMAC_TOLERANCE_SECONDS", "300"))
    TRUSTED_PROXY_COUNT: int = max(1, int(os.getenv("TRUSTED_PROXY_COUNT", "1")))
    TRUST_X_FORWARDED_FOR: bool = _parse_bool(os.getenv("TRUST_X_FORWARDED_FOR"), default=True)
    MAX_AI_BODY_BYTES: int = int(os.getenv("MAX_AI_BODY_BYTES", "65536"))

    # 数据库与 GCS 零成本快照持久化配置
    DB_PATH: Path = Path(os.getenv("LOTTERY_DB_PATH", str(DB_DIR / "lottery.db")))
    SEED_FILE_PATH: Path = Path(os.getenv("LOTTERY_SEED_PATH", str(SEEDS_DIR / "lottery_seed_100.json")))
    GCS_STATE_BUCKET: str = os.getenv("GCS_STATE_BUCKET", "").strip()
    GCS_STATE_OBJECT: str = os.getenv("GCS_STATE_OBJECT", "bazi-lottery/lottery.db").strip()
    AUTO_SYNC_ON_STARTUP: bool = _parse_bool(os.getenv("AUTO_SYNC_ON_STARTUP"), default=False)
    STALE_DATA_THRESHOLD_DAYS: int = int(os.getenv("STALE_DATA_THRESHOLD_DAYS", "5"))

    # CORS 设置（禁用通配符与凭据）
    CORS_ORIGINS: List[str] = _parse_origins(os.getenv("CORS_ORIGINS", ""))
    CORS_ALLOW_CREDENTIALS: bool = False
    CORS_ALLOW_METHODS: List[str] = ["GET", "POST", "OPTIONS"]
    CORS_ALLOW_HEADERS: List[str] = [
        "Content-Type",
        "Authorization",
        "Accept",
        "X-App-Timestamp",
        "X-App-Nonce",
        "X-App-Signature",
        "X-Request-ID",
    ]

    # 统计参数
    DEFAULT_ANALYSIS_WINDOW: int = 100
    DEFAULT_HOT_COLD_WINDOW: int = 30
    HOT_THRESHOLD_FACTOR: float = 1.25
    COLD_THRESHOLD_FACTOR: float = 0.75
    SUPPORTED_LOTTERIES: List[str] = ["ssq", "dlt", "fc3d", "qxc"]

    # AI LLM Provider 配置
    DEEPSEEK_API_KEY: str = os.getenv("DEEPSEEK_API_KEY", "")
    DEEPSEEK_BASE_URL: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
    DEEPSEEK_MODEL: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    GEMINI_MODEL: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    OPENAI_BASE_URL: str = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    OPENAI_MODEL: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    AI_DEFAULT_PROVIDER: str = os.getenv("AI_DEFAULT_PROVIDER", "auto")


settings = Settings()
