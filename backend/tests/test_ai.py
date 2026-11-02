"""Comprehensive tests for AI LLM Divination module (backend/app/api/routes_ai.py).
Verifies schema validation, non-streaming JSON, streaming SSE, authentic 5-section generator,
responsible gaming compliance keywords, and cross-lottery support.
"""
import json
import pytest
from fastapi.testclient import TestClient

from backend.app.main import app

client = TestClient(app)

SAMPLE_SSQ_TICKET = {
    "primary_numbers": ["03", "06", "11", "18", "21", "28"],
    "secondary_numbers": ["09"],
}

MANDATORY_SECTIONS = [
    "【命盘乾坤】",
    "【数理玄机】",
    "【五行合局】",
    "【灵感吉言】",
    "【理性倡议】",
]

MANDATORY_DISCLAIMER_KEYWORDS = ["理性", "娱乐", "量力而行", "概率"]


def test_ai_divination_schema_validation():
    """Verify invalid lottery types and malformed requests return proper HTTP error codes."""
    # Invalid lottery type
    resp = client.post(
        "/api/ai/divination",
        json={
            "lottery_type": "invalid_lotto",
            "recommended_numbers": SAMPLE_SSQ_TICKET,
        },
    )
    assert resp.status_code == 400
    assert "Unsupported lottery type" in resp.json()["detail"]

    # Missing recommended_numbers (Pydantic validation error)
    resp2 = client.post(
        "/api/ai/divination",
        json={
            "lottery_type": "ssq",
        },
    )
    assert resp2.status_code == 422


def test_ai_divination_fallback_non_streaming():
    """Verify non-streaming JSON response contains 5 standard sections and disclaimer."""
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "recommended_numbers": SAMPLE_SSQ_TICKET,
        "stream": False,
    }
    resp = client.post("/api/ai/divination", json=payload)
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("application/json")

    data = resp.json()
    assert data["lottery_type"] == "ssq"
    assert data["has_disclaimer"] is True
    assert data["provider"] in ("fallback", "gemini", "openai", "deepseek")

    text = data["divination_text"]
    for section in MANDATORY_SECTIONS:
        assert section in text, f"Missing required section {section} in output"

    for kw in MANDATORY_DISCLAIMER_KEYWORDS:
        assert kw in text, f"Missing required disclaimer keyword '{kw}' in output"


def test_ai_divination_deepseek_provider():
    """Verify provider='deepseek' returns compliant divination and graceful fallback."""
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "recommended_numbers": SAMPLE_SSQ_TICKET,
        "provider": "deepseek",
        "model": "deepseek-chat",
        "stream": False,
    }
    resp = client.post("/api/ai/divination", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert data["provider"] in ("deepseek", "fallback")
    assert all(sec in data["divination_text"] for sec in MANDATORY_SECTIONS)
    assert all(kw in data["divination_text"] for kw in MANDATORY_DISCLAIMER_KEYWORDS)


def test_ai_divination_fallback_streaming_sse():
    """Verify streaming SSE mode emits sequential chunks and properly formatted SSE events."""
    payload = {
        "birth_datetime": "1990-08-15T08:30:00",
        "lottery_type": "ssq",
        "recommended_numbers": SAMPLE_SSQ_TICKET,
        "stream": True,
    }
    resp = client.post("/api/ai/divination", json=payload)
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/event-stream")

    full_text = ""
    got_terminal_done = False
    event_count = 0

    for line in resp.iter_lines():
        if not line or not line.startswith("data: "):
            continue
        event_count += 1
        data_json = json.loads(line[6:].strip())
        assert "delta" in data_json
        assert "done" in data_json

        if data_json["done"] is True:
            got_terminal_done = True
        else:
            full_text += data_json["delta"]

    assert got_terminal_done, "SSE stream did not emit terminal done event"
    assert event_count >= 2, "SSE stream emitted fewer events than expected"

    for section in MANDATORY_SECTIONS:
        assert section in full_text, f"Missing required section {section} in assembled SSE stream"

    for kw in MANDATORY_DISCLAIMER_KEYWORDS:
        assert kw in full_text, f"Missing disclaimer keyword '{kw}' in assembled SSE stream"


def test_ai_divination_disclaimer_keywords_present():
    """Unconditional presence of responsible gaming disclaimer keywords."""
    resp = client.post(
        "/api/ai/divination",
        json={
            "lottery_type": "dlt",
            "recommended_numbers": {
                "primary_numbers": ["05", "10", "15", "20", "25"],
                "secondary_numbers": ["01", "06"],
            },
            "stream": False,
        },
    )
    assert resp.status_code == 200
    text = resp.json()["divination_text"]
    for kw in MANDATORY_DISCLAIMER_KEYWORDS:
        assert kw in text


def test_ai_divination_cross_lottery_types():
    """Verify divination works reliably across all 4 lottery types (ssq, dlt, fc3d, qxc)."""
    configs = [
        ("ssq", {"primary_numbers": ["01", "02", "03", "04", "05", "06"], "secondary_numbers": ["07"]}),
        ("dlt", {"primary_numbers": ["01", "02", "03", "04", "05"], "secondary_numbers": ["06", "07"]}),
        ("fc3d", {"primary_numbers": ["3", "6", "9"]}),
        ("qxc", {"primary_numbers": ["1", "2", "3", "4", "5", "6", "7"]}),
    ]

    for lt, ticket in configs:
        resp = client.post(
            "/api/ai/divination",
            json={
                "birth_datetime": "1995-10-24T14:20:00",
                "lottery_type": lt,
                "recommended_numbers": ticket,
                "stream": False,
            },
        )
        assert resp.status_code == 200, f"Failed for lottery type {lt}: {resp.text}"
        data = resp.json()
        assert data["lottery_type"] == lt
        assert all(s in data["divination_text"] for s in MANDATORY_SECTIONS)


def test_ai_divination_with_explicit_bazi_summary():
    """Verify custom bazi_summary payload interpolation."""
    payload = {
        "lottery_type": "ssq",
        "recommended_numbers": SAMPLE_SSQ_TICKET,
        "bazi_summary": {
            "day_master": "丙",
            "strength": "身弱",
            "xi_yong_shen": ["木", "火"],
            "four_pillars_str": "丙寅年 庚寅月 丙午日 戊子时",
        },
        "stream": False,
    }
    resp = client.post("/api/ai/divination", json=payload)
    assert resp.status_code == 200
    text = resp.json()["divination_text"]
    assert "丙" in text
    assert "身弱" in text
    assert all(s in text for s in MANDATORY_SECTIONS)


def test_ai_extract_ticket_numbers_none_handling():
    """Verify _extract_ticket_numbers safely handles None values without TypeError."""
    from backend.app.services.ai_service import _extract_ticket_numbers

    # None secondary
    p, s = _extract_ticket_numbers({"primary_numbers": ["01", "02"], "secondary_numbers": None})
    assert p == ["01", "02"]
    assert s == []

    # None primary
    p, s = _extract_ticket_numbers({"primary_numbers": None, "secondary_numbers": ["07"]})
    assert p == []
    assert s == ["07"]

    # Both None
    p, s = _extract_ticket_numbers({"primary_numbers": None, "secondary_numbers": None})
    assert p == []
    assert s == []

    # Lists with None
    p, s = _extract_ticket_numbers({"primary_numbers": ["01", None, "03"], "secondary_numbers": [None, "09"]})
    assert p == ["01", "03"]
    assert s == ["09"]

    # None object
    p, s = _extract_ticket_numbers(None)
    assert p == []
    assert s == []


def test_ai_divination_with_none_numbers_api():
    """Verify API endpoint succeeds with 200 when primary or secondary numbers are None."""
    payload = {
        "lottery_type": "ssq",
        "recommended_numbers": {
            "primary_numbers": ["01", "02", "03", "04", "05", "06"],
            "secondary_numbers": None,
        },
        "stream": False,
    }
    resp = client.post("/api/ai/divination", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert all(s in data["divination_text"] for s in MANDATORY_SECTIONS)

