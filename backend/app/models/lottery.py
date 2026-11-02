"""Lottery Pydantic v2 data models, rules, and API contracts."""
from enum import Enum
from typing import List, Optional, Dict, Any, Union
from pydantic import BaseModel, Field

class LotteryType(str, Enum):
    SSQ = "ssq"
    DLT = "dlt"
    FC3D = "fc3d"
    QXC = "qxc"

class ZoneRule(BaseModel):
    zone_name: str = Field(..., description="Chinese name of zone, e.g. 红球区")
    zone_code: str = Field(..., description="Machine code, e.g. red, blue, front, back, digits")
    count: int = Field(..., description="Exact number of balls required")
    min_value: int = Field(..., description="Minimum allowed integer value")
    max_value: int = Field(..., description="Maximum allowed integer value")
    unique: bool = Field(..., description="Whether balls in this zone must be mutually unique")
    sorted: bool = Field(..., description="Whether balls in this zone are sorted ascending")
    format_pattern: str = Field(..., description="String format pattern, e.g. %02d or %d")

class LotteryRule(BaseModel):
    lottery_type: LotteryType = Field(..., description="Lottery code")
    chinese_name: str = Field(..., description="Official Chinese name")
    issuing_body: str = Field(..., description="Issuing administration")
    draw_schedule: str = Field(..., description="Draw schedule in Chinese")
    total_combinations: int = Field(..., description="Total possible combinations")
    zones: List[ZoneRule] = Field(..., description="Rules per zone")
    sample_format: str = Field(..., description="Sample ticket string")

class AllRulesResponse(BaseModel):
    rules: Dict[str, LotteryRule]

class DrawRecord(BaseModel):
    issue: str = Field(..., description="Draw issue number, e.g. 2026125")
    draw_date: str = Field(..., description="Draw date YYYY-MM-DD")
    primary_numbers: List[str] = Field(..., description="Primary zone numbers")
    secondary_numbers: Optional[List[str]] = Field(default=None, description="Secondary zone numbers if applicable")
    sales_amount: Optional[int] = Field(default=0, description="Total sales in Yuan")
    pool_amount: Optional[int] = Field(default=0, description="Jackpot pool in Yuan")

class HistoryResponse(BaseModel):
    lottery_type: str
    page: int
    limit: int
    total: int
    records: List[DrawRecord]

class NumberStat(BaseModel):
    number: str = Field(..., description="Formatted ball number string")
    total_count: int = Field(..., description="Total occurrences across all analyzed draws")
    frequency: float = Field(..., description="Occurrence rate (total_count / N)")
    recent_count: int = Field(..., description="Occurrences within recent sliding window")
    expected_count: float = Field(..., description="Theoretical expected count in window")
    hot_status: str = Field(..., description="Classification: hot, warm, cold")
    current_omission: int = Field(..., description="Draws elapsed since last occurrence (g0)")
    max_omission: int = Field(..., description="Historical peak gap in analyzed window")
    avg_omission: float = Field(..., description="Average omission gap (N-k)/(k+1)")
    desire_index: float = Field(..., description="Desire/potential index (current / avg)")

class ZoneStat(BaseModel):
    zone_name: str
    zone_code: str
    stats: List[NumberStat]

class LotteryStatsResponse(BaseModel):
    lottery_type: str
    draws_analyzed: int
    latest_issue: Optional[str] = None
    latest_draw_date: Optional[str] = None
    primary_zone: ZoneStat
    secondary_zone: Optional[ZoneStat] = None

class HotColdNumbers(BaseModel):
    hot_numbers: List[str]
    warm_numbers: List[str]
    cold_numbers: List[str]

class HotColdResponse(BaseModel):
    lottery_type: str
    window: int
    primary_zone: HotColdNumbers
    secondary_zone: Optional[HotColdNumbers] = None

class OmissionItem(BaseModel):
    number: str
    current_omission: int
    max_omission: int
    avg_omission: float
    desire_index: float

class OmissionResponse(BaseModel):
    lottery_type: str
    total_draws: int
    primary_zone: List[OmissionItem]
    secondary_zone: Optional[List[OmissionItem]] = None

class TicketValidateRequest(BaseModel):
    primary_numbers: List[Union[str, int]] = Field(..., description="Primary zone numbers")
    secondary_numbers: Optional[List[Union[str, int]]] = Field(default=None, description="Secondary zone numbers")

class TicketValidateResponse(BaseModel):
    is_valid: bool
    error_message: Optional[str] = None
    normalized_primary: Optional[List[str]] = None
    normalized_secondary: Optional[List[str]] = None
    formatted_string: Optional[str] = None

class SyncRequest(BaseModel):
    lottery_type: Optional[str] = "all"
    force_reload: Optional[bool] = False

class SyncResponse(BaseModel):
    status: str
    synced_counts: Dict[str, int]
    message: str

class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    supported_lotteries: List[str]
    database: Dict[str, Any]
    timestamp: str
