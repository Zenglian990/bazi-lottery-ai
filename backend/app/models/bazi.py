"""Bazi Destiny & Numerology Recommendation Pydantic v2 Models."""
from datetime import datetime
from typing import Dict, List, Optional, Any, Union
from pydantic import BaseModel, Field, model_validator


class PillarDetailModel(BaseModel):
    """Details for a single pillar (stem, branch, pair, element, hidden stems)."""
    stem: str = Field(..., description="Heavenly Stem (天干), e.g. '庚'")
    branch: str = Field(..., description="Earthly Branch (地支), e.g. '午'")
    pair: str = Field(..., description="Stem-Branch combination, e.g. '庚午'")
    element: str = Field(..., description="Element combination, e.g. '金火'")
    hide_stems: List[str] = Field(default_factory=list, description="Hidden stems (藏干), e.g. ['丁', '己']")

    def __getitem__(self, item: str) -> Any:
        return getattr(self, item)


class FourPillarsModel(BaseModel):
    """Four Pillars model (Year, Month, Day, Hour)."""
    year: PillarDetailModel
    month: PillarDetailModel
    day: PillarDetailModel
    hour: PillarDetailModel

    def __getitem__(self, item: str) -> Any:
        return getattr(self, item)

    def __contains__(self, item: str) -> bool:
        return hasattr(self, item)


class WuxingScoresModel(BaseModel):
    """110-point positional model scores for each element."""
    wood: float = Field(..., description="Wood score (木)")
    fire: float = Field(..., description="Fire score (火)")
    earth: float = Field(..., description="Earth score (土)")
    metal: float = Field(..., description="Metal score (金)")
    water: float = Field(..., description="Water score (水)")

    def __getitem__(self, item: str) -> float:
        return getattr(self, item)

    def __contains__(self, item: str) -> bool:
        return hasattr(self, item)


class WuxingPercentageModel(BaseModel):
    """Percentage distribution of the five elements (sums to ~1.0)."""
    wood: float
    fire: float
    earth: float
    metal: float
    water: float

    def __getitem__(self, item: str) -> float:
        return getattr(self, item)

    def __contains__(self, item: str) -> bool:
        return hasattr(self, item)


class BaziDestinyRequest(BaseModel):
    """Request model for Bazi destiny analysis."""
    birth_datetime: str = Field(..., description="Birth datetime in ISO 8601 or YYYY-MM-DD HH:MM:SS format")
    calendar_type: Optional[str] = Field(default="solar", description="'solar' (公历) or 'lunar' (农历)")
    gender: Optional[str] = Field(default="male", description="'male' or 'female'")
    longitude: Optional[float] = Field(default=120.0, description="Birth location longitude (for True Solar Time)")
    sect: Optional[int] = Field(default=2, description="1: Sect 1 早子时换日派, 2: Sect 2 晚子时派")


class BaziDestinyResponse(BaseModel):
    """Response model for Bazi destiny analysis."""
    solar_datetime: str = Field(..., description="Solar calendar datetime string")
    lunar_datetime: str = Field(..., description="Lunar calendar text representation")
    four_pillars: FourPillarsModel = Field(..., description="Four Pillars information")
    pillars: Optional[FourPillarsModel] = Field(default=None, description="Compatibility alias for four_pillars")
    wuxing_scores: WuxingScoresModel = Field(..., description="110-point energy scores")
    wuxing_percentage: Dict[str, float] = Field(..., description="Wuxing score percentages")
    day_master: str = Field(..., description="Day Master stem (日主天干), e.g. '庚'")
    day_master_element: str = Field(..., description="Day Master element, e.g. 'metal'")
    strength: str = Field(..., description="'身强' or '身弱'")
    strength_ratio: Optional[float] = Field(default=None, description="Ratio of self-party energy score (>= 0.5 is 身强)")
    lacking_elements: List[str] = Field(default_factory=list, description="Lacking elements (score < 5.0)")
    xi_yong_shen: List[str] = Field(default_factory=list, description="Favorable elements (喜用神)")
    ji_shen: List[str] = Field(default_factory=list, description="Unfavorable elements (忌神)")
    lacking_elements_zh: Optional[List[str]] = Field(default=None, description="Lacking elements in Chinese")
    xi_yong_shen_zh: Optional[List[str]] = Field(default=None, description="Favorable elements in Chinese")
    ji_shen_zh: Optional[List[str]] = Field(default=None, description="Unfavorable elements in Chinese")

    @model_validator(mode="after")
    def populate_alias_and_chinese(self) -> "BaziDestinyResponse":
        if self.pillars is None:
            self.pillars = self.four_pillars
        elem_map = {"wood": "木", "fire": "火", "earth": "土", "metal": "金", "water": "水"}
        if self.lacking_elements_zh is None:
            self.lacking_elements_zh = [elem_map.get(e, e) for e in self.lacking_elements]
        if self.xi_yong_shen_zh is None:
            self.xi_yong_shen_zh = [elem_map.get(e, e) for e in self.xi_yong_shen]
        if self.ji_shen_zh is None:
            self.ji_shen_zh = [elem_map.get(e, e) for e in self.ji_shen]
        return self


class LotteryRecommendation(BaseModel):
    """A single lottery ticket recommendation."""
    primary_numbers: List[str] = Field(..., description="Primary zone numbers (e.g. 6 red balls for SSQ)")
    secondary_numbers: Optional[List[str]] = Field(default=None, description="Secondary zone numbers (e.g. 1 blue ball for SSQ)")
    wuxing_distribution: Dict[str, int] = Field(..., description="Wuxing element counts in chosen numbers")
    score_rationale: str = Field(..., description="Cultural and statistical selection rationale")


class BaziRecommendRequest(BaseModel):
    """Request model for Bazi lottery recommendation."""
    birth_datetime: Optional[str] = Field(default=None, description="Birth datetime string (optional)")
    calendar_type: Optional[str] = Field(default="solar", description="'solar' or 'lunar'")
    gender: Optional[str] = Field(default="male", description="'male' or 'female'")
    longitude: Optional[float] = Field(default=120.0, description="Birth location longitude")
    sect: Optional[int] = Field(default=2, description="Sect 1 or Sect 2 for Zi hour")
    lottery_type: str = Field(default="ssq", description="Lottery type: ssq, dlt, fc3d, qxc")
    count: int = Field(default=1, ge=1, le=100, description="Number of tickets to generate (1-100)")
    strategy: Optional[str] = Field(default="balanced", description="Strategy: bazi, balanced, hot_chase, cold_reversal")
    w_bazi: Optional[float] = Field(default=None, description="Weight for Bazi lucky score")
    w_freq: Optional[float] = Field(default=None, description="Weight for historical frequency")
    w_omission: Optional[float] = Field(default=None, description="Weight for omission value")
    deterministic: Optional[bool] = Field(default=False, description="Deterministic top-ranked selection if True")


class BaziRecommendResponse(BaseModel):
    """Response model for Bazi lottery recommendation."""
    lottery_type: str = Field(..., description="Lottery code")
    strategy: str = Field(..., description="Applied recommendation strategy")
    recommendations: List[LotteryRecommendation] = Field(..., description="List of generated tickets")
    bazi_summary: Optional[Dict[str, Any]] = Field(default=None, description="Summary of user's Bazi destiny")
