"""FastAPI router for AI LLM Divination and Stream Interpretation."""
import json
import logging
from typing import Union
from fastapi import APIRouter, HTTPException, status, Request
from fastapi.responses import StreamingResponse

from backend.app.core.config import settings
from backend.app.models.ai import BaziDivinationRequest, BaziDivinationResponse
from backend.app.services.ai_service import (
    generate_divination_text,
    stream_divination_chunks,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["ai"])


@router.post(
    "/divination",
    response_model=None,
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
    if request.lottery_type not in settings.SUPPORTED_LOTTERIES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported lottery type '{request.lottery_type}'. Supported: {settings.SUPPORTED_LOTTERIES}",
        )

    # Determine whether to stream based on request.stream or Accept header
    wants_sse = bool(request.stream) or req.headers.get("accept", "").startswith("text/event-stream")

    if not wants_sse:
        result = await generate_divination_text(request)
        return BaziDivinationResponse(
            lottery_type=request.lottery_type,
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
            logger.error(f"Error in SSE streaming generator: {e}")
            err_payload = json.dumps({"delta": f"\n[解盘发生波动: {str(e)}]", "done": False}, ensure_ascii=False)
            yield f"data: {err_payload}\n\n"

        # Final terminal SSE event
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
