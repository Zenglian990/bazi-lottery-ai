"""Core application configuration."""
from pathlib import Path
import os
from typing import List

# Base directories
BASE_DIR = Path(__file__).resolve().parent.parent.parent
APP_DIR = BASE_DIR / "app"
DB_DIR = APP_DIR / "db"
SEEDS_DIR = DB_DIR / "seeds"

class Settings:
    PROJECT_NAME: str = "Bazi Lottery System - Rules Engine & Big Data Analytics"
    VERSION: str = "1.0.0"
    API_V1_PREFIX: str = "/api"

    # Database settings
    DB_PATH: Path = Path(os.getenv("LOTTERY_DB_PATH", str(DB_DIR / "lottery.db")))
    SEED_FILE_PATH: Path = Path(os.getenv("LOTTERY_SEED_PATH", str(SEEDS_DIR / "lottery_seed_100.json")))

    # CORS settings
    CORS_ORIGINS: List[str] = ["*"]
    CORS_ALLOW_CREDENTIALS: bool = True
    CORS_ALLOW_METHODS: List[str] = ["*"]
    CORS_ALLOW_HEADERS: List[str] = ["*"]

    # Lottery statistical parameters
    DEFAULT_ANALYSIS_WINDOW: int = 100
    DEFAULT_HOT_COLD_WINDOW: int = 30
    HOT_THRESHOLD_FACTOR: float = 1.25
    COLD_THRESHOLD_FACTOR: float = 0.75

    # Lottery Types
    SUPPORTED_LOTTERIES: List[str] = ["ssq", "dlt", "fc3d", "qxc"]

    # AI LLM Provider Configuration
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    OPENAI_BASE_URL: str = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    GEMINI_MODEL: str = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    OPENAI_MODEL: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    AI_DEFAULT_PROVIDER: str = os.getenv("AI_DEFAULT_PROVIDER", "auto")

settings = Settings()
