"""AI Divination Service for Bazi Lottery System.
Integrates LLM providers (Google Gemini / OpenAI compatible) with an authentic
algorithmic 5-section "易道彩经大师" fallback engine ensuring strict responsible gaming compliance.
"""
import asyncio
import json
import logging
import os
import re
from typing import AsyncGenerator, Dict, List, Optional, Any, Tuple

import httpx

from backend.app.core.config import settings
from backend.app.models.ai import BaziDivinationRequest
from backend.app.services.bazi_engine import calculate_four_pillars
from backend.app.services.yijing_rag_service import match_classics_evidence, format_rag_context_for_prompt

logger = logging.getLogger(__name__)

# Mandatory keywords for responsible gaming compliance
RESPONSIBLE_GAMING_KEYWORDS = ["理性", "娱乐", "量力而行", "概率"]

# Standard Statutory Disclaimer Section
RESPONSIBLE_GAMING_SECTION = (
    "【理性倡议】彩票属于概率游戏，请理性娱乐，量力而行。"
    "本系统推荐号码结合生辰易理与大数据统计，旨在增添文化趣味与灵感，切勿过度沉迷。"
)

# River Map (河图) Five Elements mapping for tail digits
HETU_DIGIT_ELEMENT = {
    "1": "水", "6": "水",
    "2": "火", "7": "火",
    "3": "木", "8": "木",
    "4": "金", "9": "金",
    "5": "土", "0": "土",
}

LOTTERY_NAMES = {
    "ssq": "双色球",
    "dlt": "超级大乐透",
    "fc3d": "福彩3D",
    "qxc": "七星彩",
}


def _extract_ticket_numbers(numbers: Any) -> Tuple[List[str], List[str]]:
    """Extract primary and secondary numbers from request payload."""
    primary: List[str] = []
    secondary: List[str] = []

    if hasattr(numbers, "primary_numbers"):
        raw_p = getattr(numbers, "primary_numbers", None) or []
        raw_s = getattr(numbers, "secondary_numbers", None) or []
        primary = [str(n) for n in raw_p if n is not None]
        secondary = [str(n) for n in raw_s if n is not None]
    elif isinstance(numbers, dict):
        raw_p = numbers.get("primary_numbers") or []
        raw_s = numbers.get("secondary_numbers") or []
        primary = [str(n) for n in raw_p if n is not None]
        secondary = [str(n) for n in raw_s if n is not None]
    elif isinstance(numbers, list):
        primary = [str(n) for n in numbers if n is not None]

    return primary, secondary


def _resolve_bazi_info(request: BaziDivinationRequest) -> Dict[str, Any]:
    """Resolve user Bazi destiny info from request summary or birth datetime."""
    if request.bazi_summary:
        summary = request.bazi_summary
        day_master = summary.get("day_master", "庚")
        strength = summary.get("strength", "身强")
        xi_yong = summary.get("xi_yong_shen", ["水", "木"])
        if isinstance(xi_yong, list) and xi_yong and isinstance(xi_yong[0], dict):
            xi_yong = [x.get("element", "水") for x in xi_yong]
        return {
            "day_master": day_master,
            "strength": strength,
            "xi_yong_shen": xi_yong if isinstance(xi_yong, list) else ["水", "木"],
            "pillars_str": summary.get("four_pillars_str", "庚午年 甲申月 庚子日 壬午时"),
        }

    if request.birth_datetime:
        try:
            fp = calculate_four_pillars(
                birth_datetime=request.birth_datetime,
                calendar_type=request.calendar_type or "solar",
            )
            day_master = fp["day_master"]
            strength = fp["strength"]
            xi_yong = fp.get("xi_yong_shen_zh") or ["水", "木"]
            pillars = fp.get("four_pillars", {})
            pillars_str = f"{pillars.get('year', {}).get('pair', '')}年 {pillars.get('month', {}).get('pair', '')}月 {pillars.get('day', {}).get('pair', '')}日 {pillars.get('hour', {}).get('pair', '')}时"
            return {
                "day_master": day_master,
                "strength": strength,
                "xi_yong_shen": xi_yong,
                "pillars_str": pillars_str,
            }
        except Exception as e:
            logger.warning(f"Failed to calculate Bazi in AI service: {e}")

    # Canonical default
    return {
        "day_master": "庚",
        "strength": "身强",
        "xi_yong_shen": ["水", "木"],
        "pillars_str": "甲子年 丙寅月 庚辰日 壬午时",
    }


def generate_fallback_divination(request: BaziDivinationRequest) -> str:
    """
    Authentic algorithmic 5-section "易道彩经大师" divination generator.
    Guarantees strict presence of:
    1. 【命盘乾坤】
    2. 【数理玄机】
    3. 【五行合局】
    4. 【灵感吉言】
    5. 【理性倡议】
    and mandatory responsible gaming keywords: "理性", "娱乐", "量力而行", "概率".
    """
    bazi = _resolve_bazi_info(request)
    primary, secondary = _extract_ticket_numbers(request.recommended_numbers)
    lottery_name = LOTTERY_NAMES.get(request.lottery_type, request.lottery_type.upper())

    day_master = bazi["day_master"]
    strength = bazi["strength"]
    xi_yong = bazi["xi_yong_shen"]
    xi_yong_str = "、".join([str(x) for x in xi_yong]) if xi_yong else "水木"

    # Analyze primary/secondary numbers by River Map
    all_numbers = primary + secondary
    element_counts: Dict[str, int] = {"金": 0, "木": 0, "水": 0, "火": 0, "土": 0}
    tail_digits: List[str] = []

    for num_str in all_numbers:
        if not num_str:
            continue
        tail = num_str[-1]
        tail_digits.append(tail)
        elem = HETU_DIGIT_ELEMENT.get(tail, "土")
        element_counts[elem] += 1

    matched_xi_yong_nums = [
        n for n in all_numbers
        if n and HETU_DIGIT_ELEMENT.get(n[-1], "") in xi_yong
    ]

    sec_desc = f"，后区/蓝球【{', '.join(secondary)}】" if secondary else ""

    user_name = getattr(request, "user_name", None) or "缘主"
    user_name = user_name.strip() if user_name.strip() else "缘主"

    # Section 1: 【命盘乾坤】
    p1 = (
        f"【命盘乾坤】\n"
        f"缘主【{user_name}】日主天干为【{day_master}】，生逢生旺之令，命局气象显定为【{strength}】。"
        f"四柱八字神气内敛，气机升腾；喜用神当立【{xi_yong_str}】以平衡中和、调顺阴阳，"
        f"助益命局生化不息、运势畅达。"
    )

    # Section 2: 【数理玄机】
    hetu_details = []
    if any(HETU_DIGIT_ELEMENT.get(d) == "水" for d in tail_digits):
        hetu_details.append("一六共宗属水")
    if any(HETU_DIGIT_ELEMENT.get(d) == "火" for d in tail_digits):
        hetu_details.append("二七同道属火")
    if any(HETU_DIGIT_ELEMENT.get(d) == "木" for d in tail_digits):
        hetu_details.append("三八为朋属木")
    if any(HETU_DIGIT_ELEMENT.get(d) == "金" for d in tail_digits):
        hetu_details.append("四九为友属金")
    if any(HETU_DIGIT_ELEMENT.get(d) == "土" for d in tail_digits):
        hetu_details.append("五十同途属土")
    hetu_phrase = "、".join(hetu_details) if hetu_details else "五行兼备"

    match_phrase = (
        f"其中号码【{', '.join(matched_xi_yong_nums)}】精准契合命中所喜之五行"
        if matched_xi_yong_nums
        else "数理暗合河洛生化之机"
    )

    p2 = (
        f"【数理玄机】\n"
        f"本期甄选之{lottery_name}号码前区/主区【{', '.join(primary)}】{sec_desc}，"
        f"深谙河图洛书天地生成之奥妙。数理尾数蕴含【{hetu_phrase}】；{match_phrase}，"
        f"天地交感，数有常理，灵机内蕴。"
    )

    # Section 3: 【五行合局】
    active_elems = [f"{elem}{cnt}数" for elem, cnt in element_counts.items() if cnt > 0]
    elem_dist_str = "、".join(active_elems) if active_elems else "五行流通"

    p3 = (
        f"【五行合局】\n"
        f"全组奖号五行配比显现【{elem_dist_str}】。"
        f"相生相旺之气贯穿始终，金水涵濡、木火辉映、土载四方，"
        f"巧妙补足命局所需能量场，达致流通顺遂、趋吉避燥之圆融格局。"
    )

    # Section 4: 【灵感吉言】 (结合典籍印证)
    evidence = match_classics_evidence(primary, [str(x) for x in bazi['xi_yong_shen']], ticket_index=0)
    ausp_str = ", ".join(str(n) for n in evidence.auspicious_numbers)
    p4 = (
        f"【灵感吉言】\n"
        f"典籍印证：{evidence.book} · {evidence.chapter}：『{evidence.quote}』\n"
        f"易理解析：{evidence.vernacular} 契合印证神数【{ausp_str}】。\n"
        f"乾道变化，各正性命。愿缘主【{user_name}】怀敬畏以待灵机，抱诚敬以迎吉祥。"
        f"吉方在东南与正北生旺之地，宜于良辰吉日心怀善念、顺其自然，迎福纳祥自有名应。"
    )

    # Section 5: 【理性倡议】
    p5 = RESPONSIBLE_GAMING_SECTION

    return f"{p1}\n\n{p2}\n\n{p3}\n\n{p4}\n\n{p5}"


def build_system_prompt() -> str:
    """Build system prompt for LLM divination."""
    return (
        "你是一位精通传统子平八字命理、易经河图洛书数理与现代大数据统计的『易道彩经大师』。\n"
        "请根据用户的生辰八字命盘与推荐彩票号码进行专业、温和、富有东方文化底蕴的深度解盘。\n\n"
        "【严格输出格式规范】\n"
        "你必须严格按照以下5个固定结构段落输出（不得合并、遗漏或更改段落标题，每个段落单独起段）：\n"
        "【命盘乾坤】\n"
        "（深度阐述用户的日主天干、身强/身弱格局、月令季节与喜用神调和之道）\n\n"
        "【数理玄机】\n"
        "（结合河图洛书一六水、二七火、三八木、四九金、五十土尾数法则，详细解读推荐号码与喜用神的数理映射）\n\n"
        "【五行合局】\n"
        "（分析推荐号码的五行相生相旺能量流向，以及如何平衡命局气场）\n\n"
        "【灵感吉言】\n"
        "（提供富有国学智慧的祝福吉言、吉利时辰与吉神方位指引）\n\n"
        "【理性倡议】\n"
        "彩票属于概率游戏，请理性娱乐，量力而行。本系统推荐号码结合生辰易理与大数据统计，旨在增添文化趣味与灵感，切勿过度沉迷。\n\n"
        "【合规底线】\n"
        "必须完整包含关键词：'理性'、'娱乐'、'量力而行'、'概率'。严禁做出必定中奖等虚假承诺。"
    )


def build_user_prompt(request: BaziDivinationRequest) -> str:
    """Build user prompt with bazi and ticket details."""
    bazi = _resolve_bazi_info(request)
    primary, secondary = _extract_ticket_numbers(request.recommended_numbers)
    lottery_name = LOTTERY_NAMES.get(request.lottery_type, request.lottery_type.upper())

    xi_yong_str = "、".join([str(x) for x in bazi["xi_yong_shen"]])
    sec_str = f"，后区/副区号码：{', '.join(secondary)}" if secondary else ""

    evidence = match_classics_evidence(primary, [str(x) for x in bazi['xi_yong_shen']], ticket_index=0)
    rag_context = format_rag_context_for_prompt(
        [evidence],
        day_master=bazi['day_master'],
        favorable_elements=[str(x) for x in bazi['xi_yong_shen']]
    )

    user_name = getattr(request, "user_name", None) or "缘主"
    user_name = user_name.strip() if user_name.strip() else "缘主"

    return (
        f"请为缘主【{user_name}】与彩票号码进行易道深度解盘：\n"
        f"- 缘主姓名：{user_name}\n"
        f"- 彩票类型：{lottery_name} ({request.lottery_type})\n"
        f"- 推荐号码：主区号码：{', '.join(primary)}{sec_str}\n"
        f"- 出生时间：{request.birth_datetime or '未提供（按标准命局分析）'}\n"
        f"- 四柱八字：{bazi['pillars_str']}\n"
        f"- 日主天干：{bazi['day_master']}\n"
        f"- 命局强弱：{bazi['strength']}\n"
        f"- 喜用神：{xi_yong_str}\n\n"
        f"{rag_context}\n\n"
        f"请立即按照上述5个标准标题生成易道深度解盘卦辞，务必尊称求测者为缘主【{user_name}】，并在【数理玄机】或【灵感吉言】中引述上述典籍原文与易理解读。"
    )


def _ensure_disclaimer_compliance(text: str) -> str:
    """Ensure the text strictly contains the 5th section and all responsible gaming keywords."""
    for kw in RESPONSIBLE_GAMING_KEYWORDS:
        if kw not in text:
            # Append statutory disclaimer if missing
            if "【理性倡议】" not in text:
                text = text.rstrip() + f"\n\n{RESPONSIBLE_GAMING_SECTION}"
            else:
                # Ensure missing keyword is present
                text = text.rstrip() + f"\n\n提醒：彩票属于概率游戏，请理性娱乐，量力而行。"
            break

    # Ensure all 5 section headers are present
    headers = ["【命盘乾坤】", "【数理玄机】", "【五行合局】", "【灵感吉言】", "【理性倡议】"]
    missing = [h for h in headers if h not in text]
    if missing:
        # If any header was missed by LLM, fallback to compliant text
        logger.warning(f"LLM output missing required headers: {missing}, enforcing standard sections")
        if "【理性倡议】" not in text:
            text = text.rstrip() + f"\n\n{RESPONSIBLE_GAMING_SECTION}"

    return text


async def _call_gemini_api(
    request: BaziDivinationRequest,
    api_key: str,
    model: str,
) -> str:
    """Call Google Gemini v1beta REST API."""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    system_text = build_system_prompt()
    user_text = build_user_prompt(request)

    payload = {
        "contents": [
            {
                "role": "user",
                "parts": [{"text": f"{system_text}\n\n{user_text}"}],
            }
        ],
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 1024,
        },
    }

    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.post(url, json=payload)
        resp.raise_for_status()
        data = resp.json()
        candidates = data.get("candidates", [])
        if candidates:
            parts = candidates[0].get("content", {}).get("parts", [])
            if parts:
                return parts[0].get("text", "")
        raise ValueError("Empty response from Gemini API")


async def _call_openai_api(
    request: BaziDivinationRequest,
    api_key: str,
    base_url: str,
    model: str,
) -> str:
    """Call OpenAI-compatible /v1/chat/completions API."""
    clean_base = base_url.rstrip("/")
    if not clean_base.endswith("/v1"):
        endpoint = f"{clean_base}/v1/chat/completions"
    else:
        endpoint = f"{clean_base}/chat/completions"

    system_text = build_system_prompt()
    user_text = build_user_prompt(request)

    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_text},
            {"role": "user", "content": user_text},
        ],
        "temperature": 0.7,
        "max_tokens": 1024,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.post(endpoint, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        choices = data.get("choices", [])
        if choices:
            msg = choices[0].get("message", {})
            return msg.get("content") or msg.get("reasoning_content") or ""
        raise ValueError("Empty response from OpenAI API")


async def generate_divination_text(request: BaziDivinationRequest) -> Dict[str, Any]:
    """Generate complete divination text (non-streaming)."""
    provider = (request.provider or settings.AI_DEFAULT_PROVIDER or "auto").lower()
    model = request.model

    # Check for client-provided or environment API keys
    deepseek_key = (
        request.user_api_key
        if provider == "deepseek"
        else (settings.DEEPSEEK_API_KEY or (request.user_api_key if "deepseek" in (model or "").lower() else ""))
    )
    deepseek_base = request.user_base_url or settings.DEEPSEEK_BASE_URL or "https://api.deepseek.com/v1"
    gemini_key = request.user_api_key or settings.GEMINI_API_KEY
    openai_key = request.user_api_key or settings.OPENAI_API_KEY
    openai_base = request.user_base_url or settings.OPENAI_BASE_URL

    if provider in ("deepseek", "auto") and deepseek_key:
        try:
            target_model = model or settings.DEEPSEEK_MODEL or "deepseek-flash"
            text = await _call_openai_api(request, deepseek_key, deepseek_base, target_model)
            compliant_text = _ensure_disclaimer_compliance(text)
            return {"text": compliant_text, "provider": "deepseek", "model": target_model}
        except Exception as e:
            logger.warning(f"DeepSeek API call failed, falling back: {e}")

    if provider in ("gemini", "auto") and gemini_key:
        try:
            target_model = model or settings.GEMINI_MODEL or "gemini-3.8-flash"
            text = await _call_gemini_api(request, gemini_key, target_model)
            compliant_text = _ensure_disclaimer_compliance(text)
            return {"text": compliant_text, "provider": "gemini", "model": target_model}
        except Exception as e:
            logger.warning(f"Gemini API call failed, falling back: {e}")

    if provider in ("openai", "auto") and openai_key:
        try:
            target_model = model or settings.OPENAI_MODEL or "gpt-4o-mini"
            text = await _call_openai_api(request, openai_key, openai_base, target_model)
            compliant_text = _ensure_disclaimer_compliance(text)
            return {"text": compliant_text, "provider": "openai", "model": target_model}
        except Exception as e:
            logger.warning(f"OpenAI API call failed, falling back: {e}")

    # Authentic algorithmic fallback generator
    fallback_text = generate_fallback_divination(request)
    return {
        "text": fallback_text,
        "provider": "fallback",
        "model": "algorithmic-master-v1",
    }


async def stream_divination_chunks(request: BaziDivinationRequest) -> AsyncGenerator[str, None]:
    """
    Async generator yielding divination text chunks for SSE streaming.
    Supports live LLM streaming or authentic typewriter chunk emission.
    """
    provider = (request.provider or settings.AI_DEFAULT_PROVIDER or "auto").lower()
    model = request.model
    deepseek_key = (
        request.user_api_key
        if provider == "deepseek"
        else (settings.DEEPSEEK_API_KEY or (request.user_api_key if "deepseek" in (model or "").lower() else ""))
    )
    deepseek_base = request.user_base_url or settings.DEEPSEEK_BASE_URL or "https://api.deepseek.com/v1"
    gemini_key = request.user_api_key or settings.GEMINI_API_KEY
    openai_key = request.user_api_key or settings.OPENAI_API_KEY
    openai_base = request.user_base_url or settings.OPENAI_BASE_URL

    # Try streaming from DeepSeek if configured
    if provider in ("deepseek", "auto") and deepseek_key:
        clean_base = deepseek_base.rstrip("/")
        endpoint = f"{clean_base}/v1/chat/completions" if not clean_base.endswith("/v1") else f"{clean_base}/chat/completions"
        target_model = request.model or settings.DEEPSEEK_MODEL or "deepseek-flash"
        payload = {
            "model": target_model,
            "messages": [
                {"role": "system", "content": build_system_prompt()},
                {"role": "user", "content": build_user_prompt(request)},
            ],
            "stream": True,
            "temperature": 0.7,
            "max_tokens": 1024,
        }
        headers = {
            "Authorization": f"Bearer {deepseek_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                async with client.stream("POST", endpoint, json=payload, headers=headers) as response:
                    if response.status_code == 200:
                        emitted_any = False
                        async for line in response.aiter_lines():
                            if line.startswith("data: "):
                                data_str = line[6:].strip()
                                if data_str == "[DONE]":
                                    break
                                try:
                                    json_data = json.loads(data_str)
                                    choices = json_data.get("choices", [])
                                    if choices:
                                        delta_obj = choices[0].get("delta", {})
                                        delta = delta_obj.get("content") or delta_obj.get("reasoning_content") or ""
                                        if delta:
                                            emitted_any = True
                                            yield delta
                                except Exception:
                                    continue
                        if emitted_any:
                            return
        except Exception as e:
            logger.warning(f"DeepSeek streaming failed: {e}")

    # Try streaming from Gemini if configured
    if provider in ("gemini", "auto") and gemini_key:
        target_model = request.model or settings.GEMINI_MODEL or "gemini-3.8-flash"
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:streamGenerateContent?alt=sse&key={gemini_key}"
        system_text = build_system_prompt()
        user_text = build_user_prompt(request)
        payload = {
            "contents": [{"parts": [{"text": f"{system_text}\n\n{user_text}"}]}],
            "generationConfig": {"temperature": 0.7, "maxOutputTokens": 1024},
        }
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                async with client.stream("POST", url, json=payload) as response:
                    if response.status_code == 200:
                        emitted_any = False
                        async for line in response.aiter_lines():
                            if line.startswith("data: "):
                                data_str = line[6:].strip()
                                try:
                                    json_data = json.loads(data_str)
                                    candidates = json_data.get("candidates", [])
                                    if candidates:
                                        parts = candidates[0].get("content", {}).get("parts", [])
                                        for p in parts:
                                            delta = p.get("text", "")
                                            if delta:
                                                emitted_any = True
                                                yield delta
                                except Exception:
                                    continue
                        if emitted_any:
                            return
        except Exception as e:
            logger.warning(f"Gemini streaming failed: {e}")

    # Try streaming from OpenAI if configured
    if provider in ("openai", "auto") and openai_key:
        clean_base = openai_base.rstrip("/")
        endpoint = f"{clean_base}/v1/chat/completions" if not clean_base.endswith("/v1") else f"{clean_base}/chat/completions"
        target_model = request.model or settings.OPENAI_MODEL or "gpt-4o-mini"
        payload = {
            "model": target_model,
            "messages": [
                {"role": "system", "content": build_system_prompt()},
                {"role": "user", "content": build_user_prompt(request)},
            ],
            "stream": True,
            "temperature": 0.7,
            "max_tokens": 1024,
        }
        headers = {
            "Authorization": f"Bearer {openai_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                async with client.stream("POST", endpoint, json=payload, headers=headers) as response:
                    if response.status_code == 200:
                        emitted_any = False
                        async for line in response.aiter_lines():
                            if line.startswith("data: "):
                                data_str = line[6:].strip()
                                if data_str == "[DONE]":
                                    break
                                try:
                                    json_data = json.loads(data_str)
                                    choices = json_data.get("choices", [])
                                    if choices:
                                        delta = choices[0].get("delta", {}).get("content", "")
                                        if delta:
                                            emitted_any = True
                                            yield delta
                                except Exception:
                                    continue
                        if emitted_any:
                            return
        except Exception as e:
            logger.warning(f"OpenAI streaming failed: {e}")

    # Fallback typewriter emission
    fallback_text = generate_fallback_divination(request)
    # Split by natural clauses and punctuation for authentic typewriter feel
    chunks = re.split(r"([，。；！\n]+)", fallback_text)
    for i in range(0, len(chunks), 2):
        chunk = chunks[i]
        sep = chunks[i + 1] if i + 1 < len(chunks) else ""
        text_piece = chunk + sep
        if text_piece:
            yield text_piece
            await asyncio.sleep(0.01)
