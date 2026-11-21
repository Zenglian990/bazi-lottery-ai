"""FastAPI router for Bazi Destiny Analysis and Numerology Lottery Recommendations."""
from datetime import datetime, timedelta, timezone
import logging
import math
from typing import Literal, Optional
from fastapi import APIRouter, HTTPException, Query, status
from lunar_python import Solar

from backend.app.models.bazi import (
    BaziDestinyRequest,
    BaziDestinyResponse,
    BaziRecommendRequest,
    BaziRecommendResponse,
    FourPillarsModel,
    LotteryRecommendation,
    PillarDetailModel,
    TodayFortuneRequest,
    TodayFortuneResponse,
    WuxingScoresModel,
)
from backend.app.services.bazi_engine import calculate_four_pillars
from backend.app.services.lottery_rules import get_lottery_rule
from backend.app.services.name_numerology import analyze_name_numerology
from backend.app.services.numerology_engine import (
    compute_liuri_resonance,
    recommend_lottery_tickets,
)
from backend.app.services.wuxing_engine import (
    BRANCH_HIDDEN_STEMS,
    ELEMENT_NAMES_ZH,
    STEM_ELEMENTS,
)

CHINA_TZ = timezone(timedelta(hours=8))
logger = logging.getLogger(__name__)
router = APIRouter(prefix="/bazi", tags=["bazi"])


def compute_today_fortune_data(
    birth_datetime: Optional[str] = None,
    calendar_type: Optional[str] = "lunar",
    longitude: Optional[float] = 120.0,
    sect: Optional[int] = 2,
    user_name: Optional[str] = None,
    shichen: Optional[str] = None,
    lunar_year: Optional[int] = None,
    lunar_month: Optional[int] = None,
    lunar_day: Optional[int] = None,
    is_leap_month: bool = False,
    lunar_hour: Optional[str] = None,
    gender: Optional[str] = "male",
    apply_eot: Optional[bool] = None,
    is_dst: bool = False,
) -> TodayFortuneResponse:
    """计算今日实时流日、黄历财喜神方位及与用户原局的严谨气运共振。"""
    now = datetime.now(CHINA_TZ)
    solar_now = Solar.fromYmdHms(now.year, now.month, now.day, now.hour, now.minute, now.second)
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
    if birth_datetime or (lunar_year and lunar_month and lunar_day):
        try:
            user_bazi = calculate_four_pillars(
                birth_datetime=birth_datetime,
                calendar_type=calendar_type or "lunar",
                longitude=longitude if longitude is not None else 120.0,
                sect=sect if sect is not None else 2,
                shichen=shichen,
                lunar_year=lunar_year,
                lunar_month=lunar_month,
                lunar_day=lunar_day,
                is_leap_month=is_leap_month,
                lunar_hour=lunar_hour,
                gender=gender,
                apply_eot=apply_eot,
                is_dst=is_dst,
            )
            harmonious, res_level, multiplier = compute_liuri_resonance(
                day_stem_elem=stem_elem,
                day_branch_elem=branch_elem,
                xi_yong_shen=user_bazi["xi_yong_shen"],
                ji_shen=user_bazi["ji_shen"],
            )
            harm_zh = [ELEMENT_NAMES_ZH.get(e, e) for e in harmonious]
            name_tag = f"【{user_name}】" if user_name else "缘主"

            if res_level == "大吉":
                res_score = 0.88
                desc = (
                    f"今日流日{ganzhi_day}({ELEMENT_NAMES_ZH.get(stem_elem)}{ELEMENT_NAMES_ZH.get(branch_elem)})，"
                    f"直合{name_tag}喜用神【{'/'.join(harm_zh)}】气运共振(+20%)，灵感通透，宜从容布局。"
                )
            elif res_level == "吉":
                res_score = 0.78
                desc = (
                    f"今日流日{ganzhi_day}({ELEMENT_NAMES_ZH.get(stem_elem)}{ELEMENT_NAMES_ZH.get(branch_elem)})，"
                    f"流日【{'/'.join(harm_zh)}】温和生助{name_tag}喜用神(+10%)，气运顺畅，稳中见喜。"
                )
            else:
                res_score = 0.65
                desc = (
                    f"今日流日{ganzhi_day}({ELEMENT_NAMES_ZH.get(stem_elem)}{ELEMENT_NAMES_ZH.get(branch_elem)})气运平稳中和，"
                    f"未直接引动喜用神，宜依原局五行数理理性选号、量力而行。"
                )

            resonance = {
                "today_ganzhi": f"{ganzhi_day} ({ELEMENT_NAMES_ZH.get(stem_elem)}{ELEMENT_NAMES_ZH.get(branch_elem)})",
                "user_day_master": user_bazi["day_master"],
                "user_xi_yong": user_bazi["xi_yong_shen"],
                "resonance_score": res_score,
                "resonance_level": res_level,
                "resonance_multiplier": multiplier,
                "harmonious_elements": harm_zh,
                "description": desc,
            }
        except ValueError as e:
            logger.warning("Invalid natal input in today-fortune: %s", type(e).__name__)
        except Exception as e:
            logger.warning("Failed to calculate natal resonance: %s", type(e).__name__)

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
    birth_datetime: Optional[str] = Query(default=None, max_length=64),
    calendar_type: Optional[Literal["lunar", "solar"]] = Query(default="lunar"),
    longitude: Optional[float] = Query(default=120.0, ge=-180.0, le=180.0),
    sect: Optional[Literal[1, 2]] = Query(default=2),
    user_name: Optional[str] = Query(default=None, max_length=20),
    shichen: Optional[str] = Query(default=None, max_length=16),
    gender: Optional[str] = Query(default="male", max_length=16),
) -> TodayFortuneResponse:
    """获取今日黄历干支、财喜神方位与可选个人流日共振分析。"""
    if longitude is not None and not math.isfinite(longitude):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Invalid longitude parameter",
        )
    return compute_today_fortune_data(
        birth_datetime=birth_datetime,
        calendar_type=calendar_type or "lunar",
        longitude=longitude,
        sect=sect,
        user_name=user_name,
        shichen=shichen,
        gender=gender,
    )


@router.post("/today-fortune", response_model=TodayFortuneResponse, summary="Analyze today's LiuRi with user's natal chart resonance")
def post_today_fortune(request: Optional[TodayFortuneRequest] = None) -> TodayFortuneResponse:
    """以 POST 提交生辰计算今日流日共振。"""
    req = request or TodayFortuneRequest()
    return compute_today_fortune_data(
        birth_datetime=req.birth_datetime,
        calendar_type=req.calendar_type,
        longitude=req.longitude,
        sect=req.sect,
        user_name=req.user_name,
        shichen=req.shichen,
        lunar_year=req.lunar_year,
        lunar_month=req.lunar_month,
        lunar_day=req.lunar_day,
        is_leap_month=req.is_leap_month,
        lunar_hour=req.lunar_hour,
        gender=req.gender,
        apply_eot=req.apply_eot,
        is_dst=req.is_dst,
    )


@router.post("/destiny", response_model=BaziDestinyResponse, summary="Analyze Bazi destiny and Wuxing distribution")
def analyze_destiny(request: BaziDestinyRequest) -> BaziDestinyResponse:
    """计算四柱八字、110 分五行量化、喜用忌神、姓名五格与前 8 步大运详表。"""
    try:
        raw_bazi = calculate_four_pillars(
            birth_datetime=request.birth_datetime,
            calendar_type=request.calendar_type or "lunar",
            longitude=request.longitude if request.longitude is not None else 120.0,
            sect=request.sect if request.sect is not None else 2,
            shichen=request.shichen,
            lunar_year=request.lunar_year,
            lunar_month=request.lunar_month,
            lunar_day=request.lunar_day,
            is_leap_month=request.is_leap_month,
            lunar_hour=request.lunar_hour,
            gender=request.gender,
            apply_eot=request.apply_eot,
            is_dst=request.is_dst,
        )
    except ValueError as exc:
        logger.warning("Invalid Bazi destiny input parameters: %s", str(exc))
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid birth datetime or calendar parameters: {str(exc)}",
        )

    four_pillars = FourPillarsModel(
        year=PillarDetailModel(**raw_bazi["four_pillars"]["year"]),
        month=PillarDetailModel(**raw_bazi["four_pillars"]["month"]),
        day=PillarDetailModel(**raw_bazi["four_pillars"]["day"]),
        hour=PillarDetailModel(**raw_bazi["four_pillars"]["hour"]),
    )
    wuxing_scores = WuxingScoresModel(**raw_bazi["wuxing_scores"])
    effective_name = request.user_name.strip() if (request.user_name and request.user_name.strip()) else "缘主"
    name_num = analyze_name_numerology(effective_name)

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
        strength_level=raw_bazi.get("strength_level"),
        strength_ratio=raw_bazi.get("strength_ratio"),
        lacking_elements=raw_bazi["lacking_elements"],
        xi_yong_shen=raw_bazi["xi_yong_shen"],
        ji_shen=raw_bazi["ji_shen"],
        lacking_elements_zh=raw_bazi.get("lacking_elements_zh"),
        xi_yong_shen_zh=raw_bazi.get("xi_yong_shen_zh"),
        ji_shen_zh=raw_bazi.get("ji_shen_zh"),
        user_name=effective_name,
        shichen=raw_bazi.get("shichen") or request.shichen,
        name_numerology=name_num,
        gender=raw_bazi.get("gender", "male"),
        dayun_forward=raw_bazi.get("dayun_forward", True),
        dayun_start_age=raw_bazi.get("dayun_start_age"),
        dayun_list=raw_bazi.get("dayun_list", []),
        dayun_details=raw_bazi.get("dayun_details", []),
        calendar_warnings=raw_bazi.get("calendar_warnings", []),
    )


@router.post("/recommend", response_model=BaziRecommendResponse, summary="Generate Bazi & big-data weighted lottery recommendations")
def recommend_tickets(request: BaziRecommendRequest) -> BaziRecommendResponse:
    """结合生辰八字、大数据统计、流日共振及可选定胆/杀号生成合法去重推荐票。"""
    lottery_type_norm = request.lottery_type.lower()
    rule = get_lottery_rule(lottery_type_norm)
    if not rule:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported lottery type: '{request.lottery_type}'. Supported: ssq, dlt, fc3d, qxc",
        )

    try:
        bazi_analysis = calculate_four_pillars(
            birth_datetime=request.birth_datetime or datetime.now(CHINA_TZ),
            calendar_type=request.calendar_type or "lunar",
            longitude=request.longitude if request.longitude is not None else 120.0,
            sect=request.sect if request.sect is not None else 2,
            shichen=request.shichen,
            lunar_year=request.lunar_year,
            lunar_month=request.lunar_month,
            lunar_day=request.lunar_day,
            is_leap_month=request.is_leap_month,
            lunar_hour=request.lunar_hour,
            gender=request.gender,
            apply_eot=request.apply_eot,
            is_dst=request.is_dst,
        )
        effective_name = request.user_name.strip() if (request.user_name and request.user_name.strip()) else "缘主"
        rec_data = recommend_lottery_tickets(
            bazi_analysis=bazi_analysis,
            lottery_type=lottery_type_norm,
            count=request.count,
            strategy=request.strategy or "balanced",
            w_bazi=request.w_bazi,
            w_freq=request.w_freq,
            w_omission=request.w_omission,
            deterministic=bool(request.deterministic),
            user_name=effective_name,
            shichen=request.shichen,
            gender=request.gender,
            locked_primary=request.locked_primary,
            excluded_primary=request.excluded_primary,
            locked_secondary=request.locked_secondary,
            excluded_secondary=request.excluded_secondary,
        )
    except ValueError as exc:
        logger.warning("Invalid input parameters for recommendation: %s", str(exc))
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid parameters for recommendation generation: {str(exc)}",
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
        user_name=effective_name,
        today_liuri=rec_data.get("today_liuri"),
    )
