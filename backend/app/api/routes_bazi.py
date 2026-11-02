"""FastAPI router for Bazi Destiny Analysis and Numerology Lottery Recommendations."""
from datetime import datetime
import logging
from typing import Dict, Any, Optional
from fastapi import APIRouter, HTTPException, status

from backend.app.models.bazi import (
    BaziDestinyRequest,
    BaziDestinyResponse,
    BaziRecommendRequest,
    BaziRecommendResponse,
    FourPillarsModel,
    PillarDetailModel,
    WuxingScoresModel,
    LotteryRecommendation,
)
from backend.app.services.bazi_engine import calculate_four_pillars
from backend.app.services.numerology_engine import recommend_lottery_tickets
from backend.app.services.lottery_rules import get_lottery_rule

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/bazi", tags=["bazi"])


@router.post("/destiny", response_model=BaziDestinyResponse, summary="Analyze Bazi destiny and Wuxing distribution")
def analyze_destiny(request: BaziDestinyRequest) -> BaziDestinyResponse:
    """
    Calculate Four Pillars, 110-point Wuxing scores, Day Master strength,
    Xi Yong Shen, Ji Shen, and lacking elements.
    """
    try:
        raw_bazi = calculate_four_pillars(
            birth_datetime=request.birth_datetime,
            calendar_type=request.calendar_type or "solar",
            longitude=request.longitude if request.longitude is not None else 120.0,
            sect=request.sect if request.sect is not None else 2,
        )

        four_pillars = FourPillarsModel(
            year=PillarDetailModel(**raw_bazi["four_pillars"]["year"]),
            month=PillarDetailModel(**raw_bazi["four_pillars"]["month"]),
            day=PillarDetailModel(**raw_bazi["four_pillars"]["day"]),
            hour=PillarDetailModel(**raw_bazi["four_pillars"]["hour"]),
        )

        wuxing_scores = WuxingScoresModel(**raw_bazi["wuxing_scores"])

        return BaziDestinyResponse(
            solar_datetime=raw_bazi["solar_datetime"],
            lunar_datetime=raw_bazi["lunar_datetime"],
            four_pillars=four_pillars,
            pillars=four_pillars,
            wuxing_scores=wuxing_scores,
            wuxing_percentage=raw_bazi["wuxing_percentage"],
            day_master=raw_bazi["day_master"],
            day_master_element=raw_bazi["day_master_element"],
            strength=raw_bazi["strength"],
            strength_ratio=raw_bazi.get("strength_ratio"),
            lacking_elements=raw_bazi["lacking_elements"],
            xi_yong_shen=raw_bazi["xi_yong_shen"],
            ji_shen=raw_bazi["ji_shen"],
            lacking_elements_zh=raw_bazi.get("lacking_elements_zh"),
            xi_yong_shen_zh=raw_bazi.get("xi_yong_shen_zh"),
            ji_shen_zh=raw_bazi.get("ji_shen_zh"),
        )
    except Exception as e:
        logger.error(f"Error calculating Bazi destiny: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Bazi calculation failed: {str(e)}",
        )


@router.post("/recommend", response_model=BaziRecommendResponse, summary="Generate Bazi & big-data weighted lottery recommendations")
def recommend_tickets(request: BaziRecommendRequest) -> BaziRecommendResponse:
    """
    Recommend lottery tickets using 5-tier Hetu numerology lucky pools,
    historical frequency and omission weights, and A-Res reservoir sampling.
    """
    lottery_type_norm = request.lottery_type.lower()
    rule = get_lottery_rule(lottery_type_norm)
    if not rule:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported lottery type: '{request.lottery_type}'. Supported: ssq, dlt, fc3d, qxc",
        )

    try:
        # Calculate Bazi destiny or use current time if not provided
        bazi_analysis = calculate_four_pillars(
            birth_datetime=request.birth_datetime or datetime.now(),
            calendar_type=request.calendar_type or "solar",
            longitude=request.longitude if request.longitude is not None else 120.0,
            sect=request.sect if request.sect is not None else 2,
        )

        # Generate recommendations via A-Res sampling
        rec_data = recommend_lottery_tickets(
            bazi_analysis=bazi_analysis,
            lottery_type=lottery_type_norm,
            count=request.count,
            strategy=request.strategy or "balanced",
            w_bazi=request.w_bazi,
            w_freq=request.w_freq,
            w_omission=request.w_omission,
            deterministic=bool(request.deterministic),
        )

        recs = [
            LotteryRecommendation(
                primary_numbers=r["primary_numbers"],
                secondary_numbers=r.get("secondary_numbers"),
                wuxing_distribution=r["wuxing_distribution"],
                score_rationale=r["score_rationale"],
            )
            for r in rec_data["recommendations"]
        ]

        return BaziRecommendResponse(
            lottery_type=lottery_type_norm,
            strategy=rec_data["strategy"],
            recommendations=recs,
            bazi_summary=rec_data.get("bazi_summary"),
        )
    except Exception as e:
        logger.error(f"Error in recommendation generation: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Recommendation failed: {str(e)}",
        )
