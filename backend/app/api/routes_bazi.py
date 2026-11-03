"""FastAPI router for Bazi Destiny Analysis and Numerology Lottery Recommendations."""
from datetime import datetime
import logging
from typing import Dict, Any, Optional
from fastapi import APIRouter, HTTPException, status
from lunar_python import Solar, Lunar

from backend.app.models.bazi import (
    BaziDestinyRequest,
    BaziDestinyResponse,
    BaziRecommendRequest,
    BaziRecommendResponse,
    TodayFortuneRequest,
    TodayFortuneResponse,
    FourPillarsModel,
    PillarDetailModel,
    WuxingScoresModel,
    LotteryRecommendation,
)
from backend.app.services.bazi_engine import calculate_four_pillars
from backend.app.services.numerology_engine import recommend_lottery_tickets, ZH_TO_EN_ELEMENT
from backend.app.services.wuxing_engine import STEM_ELEMENTS, BRANCH_HIDDEN_STEMS, ELEMENT_NAMES_ZH, MOTHER_ELEMENT
from backend.app.services.name_numerology import analyze_name_numerology
from backend.app.services.lottery_rules import get_lottery_rule

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/bazi", tags=["bazi"])


def compute_today_fortune_data(
    birth_datetime: Optional[str] = None,
    calendar_type: Optional[str] = "solar",
    longitude: Optional[float] = 120.0,
    sect: Optional[int] = 2,
    user_name: Optional[str] = None,
    shichen: Optional[str] = None,
) -> TodayFortuneResponse:
    """Compute today's real-time LiuRi, Huangli directions, and natal chart resonance."""
    now = datetime.now()
    solar_now = Solar.fromDate(now)
    lunar_now = solar_now.getLunar()

    ganzhi_year = lunar_now.getYearInGanZhi()
    ganzhi_month = lunar_now.getMonthInGanZhi()
    ganzhi_day = lunar_now.getDayInGanZhi()
    ganzhi_hour = lunar_now.getTimeInGanZhi()

    day_stem = lunar_now.getDayGan()
    day_branch = lunar_now.getDayZhi()
    stem_elem = STEM_ELEMENTS.get(day_stem, "metal")
    branch_hiddens = BRANCH_HIDDEN_STEMS.get(day_branch, [("戊", 1.0)])
    branch_elem = STEM_ELEMENTS.get(branch_hiddens[0][0], "earth")

    cai_pos = lunar_now.getDayPositionCai()
    xi_pos = lunar_now.getDayPositionXi()
    day_yi = lunar_now.getDayYi()
    day_ji = lunar_now.getDayJi()
    chong_sha = lunar_now.getDayChongDesc()

    resonance = None
    if birth_datetime:
        try:
            user_bazi = calculate_four_pillars(
                birth_datetime=birth_datetime,
                calendar_type=calendar_type or "solar",
                longitude=longitude or 120.0,
                sect=sect or 2,
                shichen=shichen,
            )
            user_xi_norm = [ZH_TO_EN_ELEMENT.get(e, e) for e in user_bazi["xi_yong_shen"]]
            today_elems = {stem_elem, branch_elem}
            generating_elems = {MOTHER_ELEMENT.get(e, "") for e in user_xi_norm}

            harmonious = list(today_elems.intersection(user_xi_norm).union(today_elems.intersection(generating_elems)))
            has_harmony = len(harmonious) > 0

            name_tag = f"【{user_name}】" if user_name else "缘主"
            if has_harmony:
                res_score = 0.88
                res_level = "大吉"
                multiplier = 1.20
                desc = (
                    f"今日流日{ganzhi_day}({ELEMENT_NAMES_ZH.get(stem_elem)}{ELEMENT_NAMES_ZH.get(branch_elem)})，"
                    f"与{name_tag}喜用神【{'/'.join(user_bazi['xi_yong_shen'])}】相生共振(+20%)，财气充沛，灵感汇聚。"
                )
            else:
                res_score = 0.70
                res_level = "平吉"
                multiplier = 1.05
                desc = (
                    f"今日流日{ganzhi_day}气运平稳中和，利于遵照五行数理平衡选号，静心生慧。"
                )

            resonance = {
                "today_ganzhi": f"{ganzhi_day} ({ELEMENT_NAMES_ZH.get(stem_elem)}{ELEMENT_NAMES_ZH.get(branch_elem)})",
                "user_day_master": user_bazi["day_master"],
                "user_xi_yong": user_bazi["xi_yong_shen"],
                "resonance_score": res_score,
                "resonance_level": res_level,
                "resonance_multiplier": multiplier,
                "harmonious_elements": [ELEMENT_NAMES_ZH.get(e, e) for e in (harmonious or [stem_elem, branch_elem])],
                "description": desc,
            }
        except Exception as e:
            logger.warning(f"Failed to calculate natal resonance: {e}")

    return TodayFortuneResponse(
        solar_date=solar_now.toYmd(),
        solar_datetime=now.strftime("%Y-%m-%d %H:%M:%S"),
        lunar_date=lunar_now.toString(),
        ganzhi_year=ganzhi_year,
        ganzhi_month=ganzhi_month,
        ganzhi_day=ganzhi_day,
        ganzhi_hour=ganzhi_hour,
        day_nayin=lunar_now.getDayNaYin(),
        cai_shen_position=cai_pos,
        xi_shen_position=xi_pos,
        day_yi=day_yi[:8],
        day_ji=day_ji[:8],
        chong_sha=chong_sha,
        resonance=resonance,
    )


@router.get("/today-fortune", response_model=TodayFortuneResponse, summary="Get today's real-time LiuRi & Huangli directions")
def get_today_fortune(
    birth_datetime: Optional[str] = None,
    calendar_type: Optional[str] = "solar",
    longitude: Optional[float] = 120.0,
    sect: Optional[int] = 2,
    user_name: Optional[str] = None,
    shichen: Optional[str] = None,
) -> TodayFortuneResponse:
    """Retrieve real-time calendar date, LiuRi Four Pillars, Huangli, and optional natal resonance."""
    return compute_today_fortune_data(
        birth_datetime=birth_datetime,
        calendar_type=calendar_type,
        longitude=longitude,
        sect=sect,
        user_name=user_name,
        shichen=shichen,
    )


@router.post("/today-fortune", response_model=TodayFortuneResponse, summary="Analyze today's LiuRi with user's natal chart resonance")
def post_today_fortune(request: Optional[TodayFortuneRequest] = None) -> TodayFortuneResponse:
    """Analyze today's LiuRi resonance with user natal chart via POST body."""
    req = request or TodayFortuneRequest()
    return compute_today_fortune_data(
        birth_datetime=req.birth_datetime,
        calendar_type=req.calendar_type,
        longitude=req.longitude,
        sect=req.sect,
        user_name=req.user_name,
        shichen=req.shichen,
    )


@router.post("/destiny", response_model=BaziDestinyResponse, summary="Analyze Bazi destiny and Wuxing distribution")
def analyze_destiny(request: BaziDestinyRequest) -> BaziDestinyResponse:
    """
    Calculate Four Pillars, 110-point Wuxing scores, Day Master strength,
    Xi Yong Shen, Ji Shen, lacking elements, Chinese name numerology, and 12 Shichen.
    """
    try:
        raw_bazi = calculate_four_pillars(
            birth_datetime=request.birth_datetime,
            calendar_type=request.calendar_type or "solar",
            longitude=request.longitude if request.longitude is not None else 120.0,
            sect=request.sect if request.sect is not None else 2,
            shichen=request.shichen,
        )

        four_pillars = FourPillarsModel(
            year=PillarDetailModel(**raw_bazi["four_pillars"]["year"]),
            month=PillarDetailModel(**raw_bazi["four_pillars"]["month"]),
            day=PillarDetailModel(**raw_bazi["four_pillars"]["day"]),
            hour=PillarDetailModel(**raw_bazi["four_pillars"]["hour"]),
        )

        wuxing_scores = WuxingScoresModel(**raw_bazi["wuxing_scores"])

        name_num = None
        if request.user_name:
            name_num = analyze_name_numerology(request.user_name)

        return BaziDestinyResponse(
            solar_datetime=raw_bazi["solar_datetime"],
            lunar_datetime=raw_bazi["lunar_datetime"],
            four_pillars=four_pillars,
            pillars=four_pillars,
            wuxing_scores=wuxing_scores,
            wuxing_percentage=raw_bazi["wuxing_percentage"],
            day_master=raw_bazi["day_master"],
            day_master_element=raw_bazi["day_master_element"],
            day_master_element_zh=raw_bazi.get("day_master_element_zh"),
            strength=raw_bazi["strength"],
            strength_ratio=raw_bazi.get("strength_ratio"),
            lacking_elements=raw_bazi["lacking_elements"],
            xi_yong_shen=raw_bazi["xi_yong_shen"],
            ji_shen=raw_bazi["ji_shen"],
            lacking_elements_zh=raw_bazi.get("lacking_elements_zh"),
            xi_yong_shen_zh=raw_bazi.get("xi_yong_shen_zh"),
            ji_shen_zh=raw_bazi.get("ji_shen_zh"),
            user_name=request.user_name,
            shichen=request.shichen or raw_bazi.get("shichen"),
            name_numerology=name_num,
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
    historical frequency and omission weights, multi-bet cross-ticket deduplication,
    today's LiuRi cosmic resonance, and Chinese name numerology.
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
            shichen=request.shichen,
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
            user_name=request.user_name,
            shichen=request.shichen,
        )

        recs = [
            LotteryRecommendation(
                bet_index=r.get("bet_index", idx + 1),
                primary_numbers=r["primary_numbers"],
                secondary_numbers=r.get("secondary_numbers"),
                formatted_ticket=r.get("formatted_ticket"),
                wuxing_distribution=r["wuxing_distribution"],
                score_rationale=r["score_rationale"],
                classics_evidence=r.get("classics_evidence"),
            )
            for idx, r in enumerate(rec_data["recommendations"])
        ]

        return BaziRecommendResponse(
            lottery_type=lottery_type_norm,
            strategy=rec_data["strategy"],
            recommendations=recs,
            bazi_summary=rec_data.get("bazi_summary"),
            copy_text=rec_data.get("copy_text"),
            user_name=request.user_name,
            today_liuri=rec_data.get("today_liuri"),
        )
    except Exception as e:
        logger.error(f"Error in recommendation generation: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Recommendation failed: {str(e)}",
        )
