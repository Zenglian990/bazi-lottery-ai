"""AI Divination Pydantic v2 Models."""
from typing import Dict, List, Optional, Any, Union
from pydantic import BaseModel, Field


class DivinationTicket(BaseModel):
    """Lottery ticket numbers for AI divination."""
    primary_numbers: List[str] = Field(..., description="Primary numbers (e.g. 6 red balls for SSQ)")
    secondary_numbers: Optional[List[str]] = Field(default=None, description="Secondary numbers (e.g. 1 blue ball for SSQ)")


class BaziDivinationRequest(BaseModel):
    """Request model for AI divination."""
    birth_datetime: Optional[str] = Field(default=None, description="Birth datetime ISO string")
    calendar_type: Optional[str] = Field(default="solar", description="'solar' or 'lunar'")
    lottery_type: str = Field(..., description="Lottery type code: ssq, dlt, fc3d, qxc")
    recommended_numbers: Union[Dict[str, Any], DivinationTicket] = Field(..., description="Selected lottery numbers")
    bazi_summary: Optional[Dict[str, Any]] = Field(default=None, description="Day master, strength, xi_yong_shen, etc.")
    stream: Optional[bool] = Field(default=False, description="Whether to return SSE stream (defaults to False for REST JSON)")
    provider: Optional[str] = Field(default=None, description="'auto', 'deepseek', 'gemini', 'openai', or 'fallback'")
    user_name: Optional[str] = Field(default="缘主", description="缘主姓名")
    user_api_key: Optional[str] = Field(default=None, description="Client-provided API key override")
    user_base_url: Optional[str] = Field(default=None, description="Client-provided base URL override")
    model: Optional[str] = Field(default=None, description="Model override")


class BaziDivinationResponse(BaseModel):
    """Response model for AI divination."""
    lottery_type: str = Field(..., description="Lottery type code")
    divination_text: str = Field(..., description="Full divination text")
    has_disclaimer: bool = Field(..., description="Whether responsible gaming disclaimer is present")
    provider: str = Field(..., description="AI provider used ('deepseek', 'gemini', 'openai', or 'fallback')")
    model: Optional[str] = Field(default=None, description="Model identifier used")
