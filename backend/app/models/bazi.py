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
    birth_datetime: Optional[str] = Field(default=None, description="Birth datetime in ISO 8601 or YYYY-MM-DD HH:MM:SS format")
    calendar_type: Optional[str] = Field(default="lunar", description="'lunar' (农历) or 'solar' (公历)")
    lunar_year: Optional[int] = Field(default=None, description="农历年 (如 1990)")
    lunar_month: Optional[int] = Field(default=None, description="农历月 (1-12)")
    lunar_day: Optional[int] = Field(default=None, description="农历日 (1-30)")
    is_leap_month: bool = Field(default=False, description="是否农历闰月")
    lunar_hour: Optional[str] = Field(default=None, description="出生时辰 (如 辰时/甲子)")
    gender: Optional[str] = Field(default="male", description="'male' or 'female'")
    longitude: Optional[float] = Field(default=120.0, description="Birth location longitude (for True Solar Time)")
    sect: Optional[int] = Field(default=2, description="1: Sect 1 早子时换日派, 2: Sect 2 晚子时派")
    user_name: Optional[str] = Field(default="缘主", description="缘主姓名 (结合五格剖象与笔画数理)")
    shichen: Optional[str] = Field(default=None, description="出生时辰 (如: 早子时, 晚子时, 丑时...)")


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
    day_master_element_zh: Optional[str] = Field(default=None, description="Day Master element in Chinese, e.g. '金'")
    strength: str = Field(..., description="'身强' or '身弱'")
    strength_ratio: Optional[float] = Field(default=None, description="Ratio of self-party energy score (>= 0.5 is 身强)")
    lacking_elements: List[str] = Field(default_factory=list, description="Lacking elements (score < 5.0)")
    xi_yong_shen: List[str] = Field(default_factory=list, description="Favorable elements (喜用神)")
    ji_shen: List[str] = Field(default_factory=list, description="Unfavorable elements (忌神)")
    lacking_elements_zh: Optional[List[str]] = Field(default=None, description="Lacking elements in Chinese")
    xi_yong_shen_zh: Optional[List[str]] = Field(default=None, description="Favorable elements in Chinese")
    ji_shen_zh: Optional[List[str]] = Field(default=None, description="Unfavorable elements in Chinese")
    user_name: Optional[str] = Field(default="缘主", description="缘主姓名")
    shichen: Optional[str] = Field(default=None, description="出生时辰")
    name_numerology: Optional[Dict[str, Any]] = Field(default=None, description="姓名五格数理分析")
    gender: Optional[str] = Field(default="male", description="性别: 'male'(乾造) 或 'female'(坤造)")
    dayun_forward: Optional[bool] = Field(default=True, description="大运顺逆 (阳男阴女顺行，阴男阳女逆行)")
    dayun_list: Optional[List[str]] = Field(default_factory=list, description="前8步起运干支")

    @model_validator(mode="after")
    def populate_alias_and_chinese(self) -> "BaziDestinyResponse":
        if self.pillars is None:
            self.pillars = self.four_pillars
        elem_map = {"wood": "木", "fire": "火", "earth": "土", "metal": "金", "water": "水"}
        self.xi_yong_shen = [elem_map.get(e, e) for e in self.xi_yong_shen]
        self.ji_shen = [elem_map.get(e, e) for e in self.ji_shen]
        self.lacking_elements = [elem_map.get(e, e) for e in self.lacking_elements]
        if self.lacking_elements_zh is None:
            self.lacking_elements_zh = self.lacking_elements
        if self.xi_yong_shen_zh is None:
            self.xi_yong_shen_zh = self.xi_yong_shen
        if self.ji_shen_zh is None:
            self.ji_shen_zh = self.ji_shen
        if self.day_master_element_zh is None:
            self.day_master_element_zh = elem_map.get(self.day_master_element, self.day_master_element)
        return self


class ClassicsEvidenceModel(BaseModel):
    """Ancient classic grounding evidence for a ticket recommendation."""
    book: str = Field(..., description="典籍名称，如《周易》、《穷通宝鉴》、《奇门遁甲》")
    chapter: str = Field(..., description="篇章，如'大有卦第十四'、'论秋月庚金'")
    quote: str = Field(..., description="古籍原文引述")
    vernacular: str = Field(..., description="白话译释与易理玄机")
    auspicious_numbers: List[int] = Field(default_factory=list, description="印证神数")
    category: Optional[str] = Field(default="hexagram", description="分类: hexagram, qimen, tiao_hou, meihua")


class LotteryRecommendation(BaseModel):
    """A single lottery ticket recommendation."""
    bet_index: Optional[int] = Field(default=1, description="注号 (1, 2, 3...)")
    primary_numbers: List[str] = Field(..., description="Primary zone numbers (e.g. 6 red balls for SSQ)")
    secondary_numbers: Optional[List[str]] = Field(default=None, description="Secondary zone numbers (e.g. 1 blue ball for SSQ)")
    formatted_ticket: Optional[str] = Field(default=None, description="Formatted ticket string, e.g. '03 06 11 18 21 28 + 09'")
    wuxing_distribution: Dict[str, int] = Field(..., description="Wuxing element counts in chosen numbers")
    score_rationale: str = Field(..., description="Cultural and statistical selection rationale")
    classics_evidence: Optional[ClassicsEvidenceModel] = Field(default=None, description="易理渊源与典籍印证")



class BaziRecommendRequest(BaseModel):
    """Request model for Bazi lottery recommendation."""
    birth_datetime: Optional[str] = Field(default=None, description="Birth datetime string (optional)")
    calendar_type: Optional[str] = Field(default="lunar", description="'lunar' or 'solar'")
    lunar_year: Optional[int] = Field(default=None, description="农历年 (如 1990)")
    lunar_month: Optional[int] = Field(default=None, description="农历月 (1-12)")
    lunar_day: Optional[int] = Field(default=None, description="农历日 (1-30)")
    is_leap_month: bool = Field(default=False, description="是否农历闰月")
    lunar_hour: Optional[str] = Field(default=None, description="出生时辰 (如 辰时/甲子)")
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
    user_name: Optional[str] = Field(default="缘主", description="缘主姓名")
    shichen: Optional[str] = Field(default=None, description="出生时辰 (如: 早子时, 丑时...)")


class BaziRecommendResponse(BaseModel):
    """Response model for Bazi lottery recommendation."""
    lottery_type: str = Field(..., description="Lottery code")
    strategy: str = Field(..., description="Applied recommendation strategy")
    recommendations: List[LotteryRecommendation] = Field(..., description="List of generated tickets")
    bazi_summary: Optional[Dict[str, Any]] = Field(default=None, description="Summary of user's Bazi destiny")
    copy_text: Optional[str] = Field(default=None, description="Unified plain text representation for copying all bets")
    user_name: Optional[str] = Field(default="缘主", description="缘主姓名")
    today_liuri: Optional[Dict[str, Any]] = Field(default=None, description="今日流日干支与气运")


class TodayFortuneRequest(BaseModel):
    """Request model for Today's Fortune & LiuRi analysis."""
    birth_datetime: Optional[str] = Field(default=None, description="User's birth datetime for natal resonance")
    calendar_type: Optional[str] = Field(default="lunar", description="'lunar' or 'solar'")
    lunar_year: Optional[int] = Field(default=None, description="农历年")
    lunar_month: Optional[int] = Field(default=None, description="农历月")
    lunar_day: Optional[int] = Field(default=None, description="农历日")
    is_leap_month: bool = Field(default=False, description="是否农历闰月")
    lunar_hour: Optional[str] = Field(default=None, description="出生时辰")
    gender: Optional[str] = Field(default="male", description="'male' or 'female'")
    longitude: Optional[float] = Field(default=120.0, description="Location longitude")
    sect: Optional[int] = Field(default=2, description="Sect 1 or 2")
    user_name: Optional[str] = Field(default="缘主", description="缘主姓名")
    shichen: Optional[str] = Field(default=None, description="出生时辰")


class TodayFortuneResponse(BaseModel):
    """Response model for Today's Fortune & LiuRi analysis."""
    solar_date: str = Field(..., description="公历日期 YYYY-MM-DD")
    solar_datetime: str = Field(..., description="公历完整时间 YYYY-MM-DD HH:MM:SS")
    lunar_date: str = Field(..., description="农历日期")
    ganzhi_year: str = Field(..., description="今日流年干支")
    ganzhi_month: str = Field(..., description="今日流月干支")
    ganzhi_day: str = Field(..., description="今日流日干支")
    ganzhi_hour: str = Field(..., description="今日流时干支")
    day_nayin: str = Field(..., description="今日流日纳音五行")
    cai_shen_position: str = Field(..., description="财神方位")
    xi_shen_position: str = Field(..., description="喜神方位")
    day_yi: List[str] = Field(default_factory=list, description="今日黄历宜")
    day_ji: List[str] = Field(default_factory=list, description="今日黄历忌")
    chong_sha: str = Field(..., description="冲煞信息")
    resonance: Optional[Dict[str, Any]] = Field(default=None, description="缘主八字与今日流日天人感应共振")

