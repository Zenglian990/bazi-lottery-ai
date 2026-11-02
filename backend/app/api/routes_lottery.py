"""FastAPI router for lottery rules, historical data, statistics, and validation."""
from datetime import datetime, timezone
from typing import Optional
from fastapi import APIRouter, HTTPException, Query, Path

from backend.app.core.config import settings
from backend.app.models.lottery import (
    LotteryRule,
    AllRulesResponse,
    HistoryResponse,
    DrawRecord,
    LotteryStatsResponse,
    HotColdResponse,
    OmissionResponse,
    TicketValidateRequest,
    TicketValidateResponse,
    SyncRequest,
    SyncResponse,
    HealthResponse,
)
from backend.app.services.lottery_rules import (
    get_lottery_rule,
    get_all_rules,
    validate_ticket,
)
from backend.app.services.lottery_stats import (
    calculate_lottery_stats,
    calculate_hot_cold,
    calculate_omission,
)
from backend.app.db.database import (
    get_draws,
    get_total_draws_count,
)
from backend.app.db.sync_service import sync_lottery_data

router = APIRouter(prefix="/lottery", tags=["lottery"])

@router.get("/rules", response_model=AllRulesResponse)
def list_rules() -> AllRulesResponse:
    """Retrieve rules for all supported lottery types."""
    return AllRulesResponse(rules=get_all_rules())

@router.get("/rules/{lottery_type}", response_model=LotteryRule)
def get_rule(lottery_type: str = Path(..., description="Lottery code: ssq, dlt, fc3d, qxc")) -> LotteryRule:
    """Retrieve rule specification for a specific lottery type."""
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise HTTPException(
            status_code=404,
            detail=f"Lottery type '{lottery_type}' not found. Supported types: {settings.SUPPORTED_LOTTERIES}",
        )
    return rule

@router.get("/history/{lottery_type}", response_model=HistoryResponse)
def get_history(
    lottery_type: str = Path(..., description="Lottery code: ssq, dlt, fc3d, qxc"),
    limit: int = Query(default=100, ge=1, le=500, description="Number of draws to return"),
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
) -> HistoryResponse:
    """Retrieve paginated historical draw results."""
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise HTTPException(status_code=404, detail=f"Unsupported lottery type: '{lottery_type}'")

    offset = (page - 1) * limit
    draws = get_draws(lottery_type, limit=limit, offset=offset)
    total = get_total_draws_count(lottery_type)

    records = [
        DrawRecord(
            issue=d["issue"],
            draw_date=d["draw_date"],
            primary_numbers=d["primary_numbers"],
            secondary_numbers=d.get("secondary_numbers"),
            sales_amount=d.get("sales_amount", 0),
            pool_amount=d.get("pool_amount", 0),
        )
        for d in draws
    ]

    return HistoryResponse(
        lottery_type=lottery_type.lower(),
        page=page,
        limit=limit,
        total=total if isinstance(total, int) else 0,
        records=records,
    )

@router.get("/stats/{lottery_type}", response_model=LotteryStatsResponse)
def get_stats(
    lottery_type: str = Path(..., description="Lottery code: ssq, dlt, fc3d, qxc"),
    limit: int = Query(default=100, ge=10, le=500, description="Total history draws to analyze"),
    window: int = Query(default=30, ge=5, le=100, description="Sliding window size for hot/cold analysis"),
) -> LotteryStatsResponse:
    """Retrieve comprehensive frequency and omission statistics for a lottery type."""
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise HTTPException(status_code=404, detail=f"Unsupported lottery type: '{lottery_type}'")

    draws = get_draws(lottery_type, limit=limit)
    if not draws:
        raise HTTPException(status_code=404, detail=f"No historical draw data found for '{lottery_type}'")

    stats = calculate_lottery_stats(lottery_type, draws, window=window)
    if not stats:
        raise HTTPException(status_code=500, detail="Failed to calculate statistics")
    return stats

@router.get("/hot-cold/{lottery_type}", response_model=HotColdResponse)
def get_hot_cold(
    lottery_type: str = Path(..., description="Lottery code: ssq, dlt, fc3d, qxc"),
    window: int = Query(default=30, ge=5, le=100, description="Moving window length for hot/cold classification"),
) -> HotColdResponse:
    """Retrieve sorted hot, warm, and cold numbers for each zone."""
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise HTTPException(status_code=404, detail=f"Unsupported lottery type: '{lottery_type}'")

    draws = get_draws(lottery_type, limit=max(window, 100))
    if not draws:
        raise HTTPException(status_code=404, detail=f"No historical draw data found for '{lottery_type}'")

    hc = calculate_hot_cold(lottery_type, draws, window=window)
    if not hc:
        raise HTTPException(status_code=500, detail="Failed to calculate hot/cold numbers")
    return hc

@router.get("/omission/{lottery_type}", response_model=OmissionResponse)
def get_omission(
    lottery_type: str = Path(..., description="Lottery code: ssq, dlt, fc3d, qxc"),
    limit: int = Query(default=100, ge=10, le=500, description="Draw sample size to evaluate omissions"),
) -> OmissionResponse:
    """Retrieve current, peak, and average omission statistics along with desire index."""
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise HTTPException(status_code=404, detail=f"Unsupported lottery type: '{lottery_type}'")

    draws = get_draws(lottery_type, limit=limit)
    if not draws:
        raise HTTPException(status_code=404, detail=f"No historical draw data found for '{lottery_type}'")

    om = calculate_omission(lottery_type, draws)
    if not om:
        raise HTTPException(status_code=500, detail="Failed to calculate omission statistics")
    return om

@router.post("/validate/{lottery_type}", response_model=TicketValidateResponse)
def validate_ticket_endpoint(
    lottery_type: str = Path(..., description="Lottery code: ssq, dlt, fc3d, qxc"),
    request: TicketValidateRequest = ...,
) -> TicketValidateResponse:
    """Validate and format a user-provided or generated lottery ticket."""
    rule = get_lottery_rule(lottery_type)
    if not rule:
        raise HTTPException(status_code=404, detail=f"Unsupported lottery type: '{lottery_type}'")

    is_valid, err, norm_pri, norm_sec, formatted = validate_ticket(
        lottery_type=lottery_type,
        primary_numbers=request.primary_numbers,
        secondary_numbers=request.secondary_numbers,
    )
    return TicketValidateResponse(
        is_valid=is_valid,
        error_message=err,
        normalized_primary=norm_pri,
        normalized_secondary=norm_sec,
        formatted_string=formatted,
    )

@router.post("/sync", response_model=SyncResponse)
def sync_data(request: SyncRequest = SyncRequest()) -> SyncResponse:
    """Trigger background or immediate sync of fresh lottery draws from CWL and 500.com."""
    raw_type = request.lottery_type if request.lottery_type is not None else "all"
    l_type = raw_type.lower()
    if l_type not in ["all", "ssq", "dlt", "fc3d", "qxc"]:
        raise HTTPException(status_code=400, detail=f"Unsupported lottery type: {request.lottery_type}")
    counts = sync_lottery_data(lottery_type=l_type, limit=100)
    total_synced = sum(counts.values())
    return SyncResponse(
        status="success",
        synced_counts=counts,
        message=f"Sync completed successfully. Total records updated: {total_synced}",
    )
