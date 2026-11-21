"""Core application configuration."""
import json
from pathlib import Path
import os
from typing import List, Optional

# Base directories
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


class Settings:
    PROJECT_NAME: str = "曾练彩票助手 - 易道乾坤与大数据分析中枢"
    VERSION: str = "1.3.6"
    API_V1_PREFIX: str = "/api"

    # 运行环境配置（默认生产模式 fail-closed）
    ENV: str = os.getenv("ENV", "production").strip().lower()
    IS_PRODUCTION: bool = ENV in ("prod", "production")

    # 安全与鉴权配置（未配置时默认拒绝）
    ADMIN_TOKEN: str = os.getenv("ADMIN_TOKEN", "").strip()
    APP_HMAC_SECRET: str = os.getenv("APP_HMAC_SECRET", "").strip()
    APP_HMAC_TOLERANCE_SECONDS: int = int(os.getenv("APP_HMAC_TOLERANCE_SECONDS", "300"))
    TRUSTED_PROXY_COUNT: int = max(1, int(os.getenv("TRUSTED_PROXY_COUNT", "1")))

    # Database settings
    DB_PATH: Path = Path(os.getenv("LOTTERY_DB_PATH", str(DB_DIR / "lottery.db")))
    SEED_FILE_PATH: Path = Path(os.getenv("LOTTERY_SEED_PATH", str(SEEDS_DIR / "lottery_seed_100.json")))

    # CORS settings（收紧默认策略，禁用通配符与跨域凭据）
    CORS_ORIGINS: List[str] = _parse_origins(os.getenv("CORS_ORIGINS", ""))
    CORS_ALLOW_CREDENTIALS: bool = False
    CORS_ALLOW_METHODS: List[str] = ["GET", "POST", "OPTIONS"]
    CORS_ALLOW_HEADERS: List[str] = [
        "Content-Type",
        "Authorization",
        "Accept",
        "X-App-Timestamp",
        "X-App-Signature",
    ]

    # Lottery statistical parameters
    DEFAULT_ANALYSIS_WINDOW: int = 100
    DEFAULT_HOT_COLD_WINDOW: int = 30
    HOT_THRESHOLD_FACTOR: float = 1.25
    COLD_THRESHOLD_FACTOR: float = 0.75

    # Lottery Types
    SUPPORTED_LOTTERIES: List[str] = ["ssq", "dlt", "fc3d", "qxc"]

    # AI LLM Provider Configuration
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
