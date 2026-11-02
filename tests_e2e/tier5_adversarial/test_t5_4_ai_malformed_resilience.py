"""Tier 5 Adversarial Test: T5.4 AI Malformed Input & Resilience Stress Test.

Stress-tests /api/ai/divination against:
1. Corrupted, missing, and wrong-type payloads (verifies 422/400 schema validation without 500).
2. Negative ticket numbers, zero, extreme large numbers, floats.
3. Gigantic strings and buffer overflow payloads (10,000+ chars).
4. Mandatory presence of Section 5 '【理性倡议】' and statutory keywords ('理性', '娱乐', '量力而行', '概率').
5. Streaming SSE endpoint resilience and disclaimer presence.
6. Empirical vulnerability test for null ticket fields.
"""
import json
from typing import Dict, Any, List
from starlette.testclient import TestClient

from backend.app.main import app
from backend.app.services.ai_service import RESPONSIBLE_GAMING_KEYWORDS


def test_t5_4_corrupted_payload_schema_rejection_without_500():
    """T5.4.1: Malformed and corrupted payloads must return 422/400 with zero unhandled 500 crashes."""
    client = TestClient(app, raise_server_exceptions=False)

    corrupted_cases: List[Dict[str, Any]] = [
        {},  # Completely empty JSON -> 422
        {"lottery_type": "ssq"},  # Missing recommended_numbers -> 422
        {"recommended_numbers": {"primary_numbers": ["01", "02"]}},  # Missing lottery_type -> 422
        {"lottery_type": "ssq", "recommended_numbers": 12345},  # Int instead of dict/model -> 422
        {"lottery_type": "ssq", "recommended_numbers": "not_a_dict"},  # String instead of dict/model -> 422
        {"lottery_type": "unsupported_lottery_xyz", "recommended_numbers": {"primary_numbers": ["01"]}},  # Invalid type -> 400
        {"lottery_type": None, "recommended_numbers": None},  # None fields -> 422
        {"lottery_type": "ssq", "recommended_numbers": []},  # Empty list -> 422
    ]

    for idx, case in enumerate(corrupted_cases):
        resp = client.post("/api/ai/divination", json=case)
        assert resp.status_code in (400, 422), (
            f"Case {idx} returned unexpected status {resp.status_code}: {resp.text}"
        )
        assert resp.status_code != 500, f"Case {idx} triggered a 500 Internal Server Error crash!"


def test_t5_4_negative_and_extreme_numerical_inputs_resilience():
    """T5.4.2: Negative numbers, zeroes, floats, and extreme numbers must not crash the service."""
    client = TestClient(app, raise_server_exceptions=False)

    edge_number_cases = [
        # Negative numbers
        {"lottery_type": "ssq", "recommended_numbers": {"primary_numbers": [-1, -5, -12, -22, -30, -33], "secondary_numbers": ["-1"]}},
        # Zeroes
        {"lottery_type": "fc3d", "recommended_numbers": {"primary_numbers": [0, 0, 0]}},
        # Huge numbers
        {"lottery_type": "dlt", "recommended_numbers": {"primary_numbers": [999999999, 888888888, 777777777, 666666666, 555555555], "secondary_numbers": ["1234567", "9876543"]}},
        # String floats
        {"lottery_type": "qxc", "recommended_numbers": {"primary_numbers": ["3.14", "2.71", "1.41", "0.57", "9.99", "8.88"], "secondary_numbers": ["14.0"]}},
    ]

    for idx, case in enumerate(edge_number_cases):
        resp = client.post("/api/ai/divination", json=case)
        assert resp.status_code != 500, f"Numerical edge case {idx} caused a 500 crash!"

        if resp.status_code == 200:
            data = resp.json()
            assert data["has_disclaimer"] is True
            text = data["divination_text"]
            assert "【理性倡议】" in text
            for kw in RESPONSIBLE_GAMING_KEYWORDS:
                assert kw in text, f"Keyword '{kw}' missing from divination text in case {idx}"


def test_t5_4_gigantic_strings_and_buffer_stress():
    """T5.4.3: Gigantic strings and large nested payload stress testing (zero 500 / OOM)."""
    client = TestClient(app, raise_server_exceptions=False)

    giant_payload = {
        "lottery_type": "ssq",
        "recommended_numbers": {
            "primary_numbers": ["A" * 1000, "B" * 1000, "03", "04", "05", "06"],
            "secondary_numbers": ["C" * 500],
        },
        "birth_datetime": "9" * 10000,  # 10k chars invalid date
        "bazi_summary": {f"key_{i}": f"val_{i}" * 10 for i in range(200)},  # 200 extra keys
    }

    resp = client.post("/api/ai/divination", json=giant_payload)
    assert resp.status_code != 500, f"Giant payload caused 500 crash: {resp.text}"
    if resp.status_code == 200:
        data = resp.json()
        assert "【理性倡议】" in data["divination_text"]


def test_t5_4_responsible_gaming_disclaimer_mandatory_presence():
    """T5.4.4: Section 5 '【理性倡议】' and statutory keywords strictly verified across all lottery types."""
    client = TestClient(app)

    lotteries = ["ssq", "dlt", "fc3d", "qxc"]
    numbers_map = {
        "ssq": {"primary_numbers": ["02", "07", "12", "17", "22", "27"], "secondary_numbers": ["06"]},
        "dlt": {"primary_numbers": ["01", "06", "11", "16", "21"], "secondary_numbers": ["02", "07"]},
        "fc3d": {"primary_numbers": ["3", "8", "5"]},
        "qxc": {"primary_numbers": ["1", "6", "2", "7", "3", "8"], "secondary_numbers": ["10"]},
    }

    for lt in lotteries:
        payload = {
            "lottery_type": lt,
            "recommended_numbers": numbers_map[lt],
            "birth_datetime": "1995-10-20T14:30:00",
            "stream": False,
        }
        resp = client.post("/api/ai/divination", json=payload)
        assert resp.status_code == 200, f"Divination request for {lt} failed: {resp.text}"

        data = resp.json()
        text = data["divination_text"]

        # Check all 5 mandatory section headers
        mandatory_headers = [
            "【命盘乾坤】",
            "【数理玄机】",
            "【五行合局】",
            "【灵感吉言】",
            "【理性倡议】",
        ]
        for header in mandatory_headers:
            assert header in text, f"Header '{header}' missing from {lt} divination text:\n{text}"

        # Check all statutory keywords
        for kw in RESPONSIBLE_GAMING_KEYWORDS:
            assert kw in text, f"Mandatory keyword '{kw}' missing from {lt} divination text:\n{text}"

        assert data["has_disclaimer"] is True


def test_t5_4_streaming_sse_malformed_and_disclaimer_resilience():
    """T5.4.5: Server-Sent Events (SSE) streaming divination resilience and terminal completion."""
    client = TestClient(app)

    payload = {
        "lottery_type": "ssq",
        "recommended_numbers": {"primary_numbers": ["03", "08", "13", "18", "23", "28"], "secondary_numbers": ["16"]},
        "birth_datetime": "1988-03-08T06:00:00",
        "stream": True,
    }

    resp = client.post("/api/ai/divination", json=payload)
    assert resp.status_code == 200
    assert resp.headers.get("content-type", "").startswith("text/event-stream")

    raw_events = resp.text.strip().split("\n\n")
    assert len(raw_events) >= 2, "Expected multiple SSE streaming events"

    accumulated_text = ""
    terminal_event_found = False

    for event in raw_events:
        if not event.strip():
            continue
        assert event.startswith("data: ")
        data_json = json.loads(event[6:])
        delta = data_json.get("delta", "")
        accumulated_text += delta
        if data_json.get("done") is True:
            terminal_event_found = True

    assert terminal_event_found, "Terminal SSE event (done=True) was not emitted!"
    assert "【理性倡议】" in accumulated_text, "Section 5 missing from streamed text!"
    for kw in RESPONSIBLE_GAMING_KEYWORDS:
        assert kw in accumulated_text, f"Keyword '{kw}' missing from streamed text!"


def test_t5_4_null_ticket_fields_adversarial_reproduction():
    """T5.4.6: Adversarial probe reproducing TypeError in _extract_ticket_numbers when fields are None.
    Documents defect: numbers.get('secondary_numbers', []) returns None when secondary_numbers: None.
    """
    client = TestClient(app, raise_server_exceptions=False)

    # When secondary_numbers is explicitly null in JSON payload
    payload_null_sec = {
        "lottery_type": "ssq",
        "recommended_numbers": {
            "primary_numbers": ["01", "02", "03", "04", "05", "06"],
            "secondary_numbers": None,
        },
    }
    resp = client.post("/api/ai/divination", json=payload_null_sec)

    # EMPIRICAL OBSERVATION:
    # Previously unhandled 500 due to TypeError: 'NoneType' object is not iterable.
    # Now hardened, this strictly yields 200 (treating secondary_numbers as empty list).
    assert resp.status_code == 200

    # Also verify when primary_numbers is explicitly null
    payload_null_pri = {
        "lottery_type": "ssq",
        "recommended_numbers": {
            "primary_numbers": None,
            "secondary_numbers": ["16"],
        },
    }
    resp_pri = client.post("/api/ai/divination", json=payload_null_pri)
    assert resp_pri.status_code == 200
