"""FastAPI router for AI LLM Divination and Stream Interpretation."""
import json
import logging
import threading
import time
from typing import Union

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse

from backend.app.core.config import settings
from backend.app.core.security import get_trusted_client_ip, require_ai_auth
from backend.app.models.ai import BaziDivinationRequest, BaziDivinationResponse
from backend.app.services.ai_service import (
    _safe_error_summary,
    generate_divination_text,
    stream_divination_chunks,
    validate_divination_security_async,
)

logger = logging.getLogger(__name__)

_AI_RATE_BUCKETS: dict[str, list[float]] = {}
_AI_RATE_LOCK = threading.Lock()
_AI_RATE_LIMIT = 30
_AI_RATE_WINDOW = 60.0
_AI_RATE_MAX_BUCKETS = 2000


def require_ai_rate_limit(req: Request) -> None:
    """基于可信客户端 IP 的线程安全滑动窗口限流（含防伪造 IP 字典膨胀硬上限保护）。"""
    client_ip = get_trusted_client_ip(req)
    now = time.monotonic()
    with _AI_RATE_LOCK:
        if len(_AI_RATE_BUCKETS) > 500:
            stale_keys = [
                k for k, v in _AI_RATE_BUCKETS.items()
                if not v or (now - v[-1] > _AI_RATE_WINDOW * 2)
            ]
            for k in stale_keys:
                _AI_RATE_BUCKETS.pop(k, None)
            if len(_AI_RATE_BUCKETS) >= _AI_RATE_MAX_BUCKETS:
                # 仍超硬上限时按最旧活跃时间淘汰 25%，防止伪造随机 IP 打满内存
                oldest = sorted(
                    _AI_RATE_BUCKETS.items(),
                    key=lambda item: item[1][-1] if item[1] else 0.0,
                )[: _AI_RATE_MAX_BUCKETS // 4]
                for k, _ in oldest:
                    _AI_RATE_BUCKETS.pop(k, None)

        bucket = [t for t in _AI_RATE_BUCKETS.get(client_ip, []) if now - t < _AI_RATE_WINDOW]
        if len(bucket) >= _AI_RATE_LIMIT:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="AI 解盘请求过于频繁，请稍候再试。",
            )
        bucket.append(now)
        _AI_RATE_BUCKETS[client_ip] = bucket


router = APIRouter(prefix="/ai", tags=["ai"])


@router.post(
    "/divination",
    response_model=None,
    dependencies=[Depends(require_ai_auth), Depends(require_ai_rate_limit)],
    summary="Generate AI Bazi lottery divination with live streaming or non-streaming JSON",
)
async def ai_divination(
    request: BaziDivinationRequest,
    req: Request,
) -> Union[BaziDivinationResponse, StreamingResponse]:
    """
    Generate 5-part authentic '易道彩经大师' divination.
    Supports dual mode:
    - Non-streaming JSON: stream=False
    - Server-Sent Events (SSE): stream=True
    """
    lottery_type_norm = request.lottery_type.strip().lower()
    if lottery_type_norm not in settings.SUPPORTED_LOTTERIES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported lottery type '{request.lottery_type}'. Supported: {settings.SUPPORTED_LOTTERIES}",
        )

    try:
        await validate_divination_security_async(request)
    except ValueError as exc:
        logger.warning("Divination security validation rejected: %s", _safe_error_summary(exc))
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="非法的自定义接口地址或参数配置",
        )

    wants_sse = bool(request.stream) or req.headers.get("accept", "").startswith("text/event-stream")

    if not wants_sse:
        result = await generate_divination_text(request)
        return BaziDivinationResponse(
            lottery_type=lottery_type_norm,
            divination_text=result["text"],
            has_disclaimer="理性" in result["text"],
            provider=result["provider"],
            model=result.get("model"),
        )

    async def sse_generator():
        try:
            async for chunk in stream_divination_chunks(request):
                payload = json.dumps({"delta": chunk, "done": False}, ensure_ascii=False)
                yield f"data: {payload}\n\n"
        except Exception as e:
            logger.error("Error in SSE streaming generator: %s", _safe_error_summary(e))
            err_payload = json.dumps({"delta": "\n[解盘服务暂时波动，请稍后重试]", "done": False}, ensure_ascii=False)
            yield f"data: {err_payload}\n\n"

        end_payload = json.dumps({"delta": "", "done": True}, ensure_ascii=False)
        yield f"data: {end_payload}\n\n"

    return StreamingResponse(
        sse_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
