"""AI Divination Pydantic v2 Models with strict input validation."""
import re
from typing import Dict, List, Literal, Optional, Union
from pydantic import BaseModel, ConfigDict, Field, StrictInt, field_validator

_CONTROL_CHAR_RE = re.compile(r"[\x00-\x1f\x7f]")
_BIRTH_DATETIME_RE = re.compile(
    r"^\d{4}-\d{2}-\d{2}(?:[T ]\d{2}:\d{2}(?::\d{2}(?:\.\d{1,6})?)?)?(?:Z|[+-]\d{2}:?\d{2})?$"
)
_NUMBER_TOKEN_RE = re.compile(r"^-?\d{1,6}$")
_MODEL_NAME_RE = re.compile(r"^[a-zA-Z0-9._-]{1,64}$")
_USER_NAME_RE = re.compile(r"^[\u4e00-\u9fa5a-zA-Z0-9·.\s_-]{1,20}$")


def _reject_control_chars(value: str, field_name: str) -> str:
    if _CONTROL_CHAR_RE.search(value):
        raise ValueError(f"{field_name} contains forbidden control characters")
    return value


class BaziSummaryInput(BaseModel):
    """强类型八字摘要输入（禁止未知额外字段）。"""
    model_config = ConfigDict(extra="forbid")

    day_master: Optional[str] = Field(default=None, max_length=8)
    day_master_element: Optional[str] = Field(default=None, max_length=16)
    strength: Optional[str] = Field(default=None, max_length=16)
    strength_ratio: Optional[float] = Field(default=None, ge=0.0, le=100.0)
    xi_yong_shen: Optional[List[Union[str, Dict[str, str]]]] = Field(default=None, max_length=5)
    ji_shen: Optional[List[str]] = Field(default=None, max_length=5)
    lacking_elements: Optional[List[str]] = Field(default=None, max_length=5)
    four_pillars_str: Optional[str] = Field(default=None, max_length=64)

    @field_validator("day_master", "day_master_element", "strength", "four_pillars_str")
    @classmethod
    def validate_text_fields(cls, v: Optional[str], info) -> Optional[str]:
        if v is None:
            return None
        s = v.strip()
        if not s:
            return None
        _reject_control_chars(s, info.field_name)
        return s

    @field_validator("xi_yong_shen")
    @classmethod
    def validate_xi_yong_shen(
        cls, v: Optional[List[Union[str, Dict[str, str]]]]
    ) -> Optional[List[str]]:
        if v is None:
            return None
        normalized: List[str] = []
        for item in v:
            if isinstance(item, dict):
                elem = str(item.get("element", "")).strip()
            else:
                elem = str(item).strip()
            if not elem:
                continue
            if len(elem) > 16:
                raise ValueError("xi_yong_shen element too long")
            _reject_control_chars(elem, "xi_yong_shen")
            normalized.append(elem)
        return normalized

    @field_validator("ji_shen", "lacking_elements")
    @classmethod
    def validate_element_lists(cls, v: Optional[List[str]], info) -> Optional[List[str]]:
        if v is None:
            return None
        normalized: List[str] = []
        for item in v:
            s = str(item).strip()
            if not s:
                continue
            if len(s) > 16:
                raise ValueError(f"{info.field_name} element too long")
            _reject_control_chars(s, info.field_name)
            normalized.append(s)
        return normalized


class DivinationTicket(BaseModel):
    """Lottery ticket numbers for AI divination."""
    model_config = ConfigDict(extra="forbid")

    primary_numbers: Optional[List[Union[StrictInt, str]]] = Field(
        default=None,
        max_length=20,
        description="Primary numbers (e.g. 6 red balls for SSQ)",
    )
    secondary_numbers: Optional[List[Union[StrictInt, str]]] = Field(
        default=None,
        max_length=10,
        description="Secondary numbers (e.g. 1 blue ball for SSQ)",
    )

    @field_validator("primary_numbers", "secondary_numbers")
    @classmethod
    def validate_ticket_items(
        cls, v: Optional[List[Union[StrictInt, str]]], info
    ) -> Optional[List[Union[StrictInt, str]]]:
        if v is None:
            return None
        validated: List[Union[StrictInt, str]] = []
        for item in v:
            if isinstance(item, bool):
                raise ValueError(f"{info.field_name} must not contain boolean values")
            if isinstance(item, int):
                if item < -9999 or item > 999999:
                    raise ValueError(f"{info.field_name} integer out of range")
                validated.append(item)
            elif isinstance(item, str):
                s = item.strip()
                if not _NUMBER_TOKEN_RE.fullmatch(s):
                    raise ValueError(f"Invalid number token in {info.field_name}")
                validated.append(s)
            else:
                raise ValueError(f"Unsupported type in {info.field_name}")
        return validated


class BaziDivinationRequest(BaseModel):
    """Request model for AI divination."""
    birth_datetime: Optional[str] = Field(default=None, max_length=32, description="Birth datetime ISO string")
    calendar_type: Optional[Literal["solar", "lunar"]] = Field(default="solar", description="'solar' or 'lunar'")
    lottery_type: str = Field(..., min_length=1, max_length=16, description="Lottery type code: ssq, dlt, fc3d, qxc")
    recommended_numbers: DivinationTicket = Field(..., description="Selected lottery numbers")
    bazi_summary: Optional[BaziSummaryInput] = Field(default=None, description="Day master, strength, xi_yong_shen, etc.")
    stream: Optional[bool] = Field(default=False, description="Whether to return SSE stream (defaults to False for REST JSON)")
    provider: Optional[Literal["auto", "deepseek", "gemini", "openai", "fallback"]] = Field(
        default=None,
        description="'auto', 'deepseek', 'gemini', 'openai', or 'fallback'",
    )
    user_name: Optional[str] = Field(default="缘主", max_length=20, description="缘主姓名")
    user_api_key: Optional[str] = Field(default=None, max_length=256, description="Client-provided API key override")
    user_base_url: Optional[str] = Field(default=None, max_length=512, description="Client-provided base URL override")
    model: Optional[str] = Field(default=None, max_length=80, description="Model override")

    @field_validator("birth_datetime", mode="before")
    @classmethod
    def validate_birth_datetime(cls, v):
        if v is None:
            return None
        if not isinstance(v, str):
            raise ValueError("birth_datetime must be a string")
        s = v.strip()
        if not s:
            return None
        if len(s) > 32 or not _BIRTH_DATETIME_RE.fullmatch(s):
            raise ValueError("Invalid birth_datetime format")
        return s

    @field_validator("calendar_type", mode="before")
    @classmethod
    def normalize_calendar_type(cls, v):
        if v is None:
            return "solar"
        if not isinstance(v, str):
            raise ValueError("calendar_type must be a string")
        s = v.strip().lower()
        return s or "solar"

    @field_validator("lottery_type", mode="before")
    @classmethod
    def normalize_lottery_type(cls, v):
        if not isinstance(v, str):
            raise ValueError("lottery_type must be a string")
        s = v.strip().lower()
        _reject_control_chars(s, "lottery_type")
        return s

    @field_validator("provider", mode="before")
    @classmethod
    def normalize_provider(cls, v):
        if v is None:
            return None
        if not isinstance(v, str):
            raise ValueError("provider must be a string")
        s = v.strip().lower()
        return s or None

    @field_validator("user_name", mode="before")
    @classmethod
    def validate_user_name(cls, v):
        if v is None:
            return "缘主"
        if not isinstance(v, str):
            raise ValueError("user_name must be a string")
        _reject_control_chars(v, "user_name")
        s = v.strip()
        if not s:
            return "缘主"
        if len(s) > 20 or not _USER_NAME_RE.fullmatch(s):
            raise ValueError("Invalid user_name format or length")
        return s

    @field_validator("user_api_key", mode="before")
    @classmethod
    def validate_user_api_key(cls, v):
        if v is None:
            return None
        if not isinstance(v, str):
            raise ValueError("user_api_key must be a string")
        s = v.strip()
        if not s:
            return None
        if _CONTROL_CHAR_RE.search(s) or any(ch.isspace() for ch in s):
            raise ValueError("user_api_key contains invalid whitespace or control characters")
        return s

    @field_validator("user_base_url", mode="before")
    @classmethod
    def validate_user_base_url(cls, v):
        if v is None:
            return None
        if not isinstance(v, str):
            raise ValueError("user_base_url must be a string")
        s = v.strip()
        if not s:
            return None
        _reject_control_chars(s, "user_base_url")
        return s

    @field_validator("model", mode="before")
    @classmethod
    def validate_model(cls, v):
        if v is None:
            return None
        if not isinstance(v, str):
            raise ValueError("model must be a string")
        s = v.strip()
        if not s:
            return None
        _reject_control_chars(s, "model")
        if s.startswith("models/"):
            s = s[len("models/"):]
        if ".." in s or not _MODEL_NAME_RE.fullmatch(s):
            raise ValueError("Invalid model identifier")
        return s


class BaziDivinationResponse(BaseModel):
    """Response model for AI divination."""
    lottery_type: str = Field(..., description="Lottery type code")
    divination_text: str = Field(..., description="Full divination text")
    has_disclaimer: bool = Field(..., description="Whether responsible gaming disclaimer is present")
    provider: str = Field(..., description="AI provider used ('deepseek', 'gemini', 'openai', or 'fallback')")
    model: Optional[str] = Field(default=None, description="Model identifier used")
