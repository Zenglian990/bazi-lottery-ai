"""Tier 5 Adversarial Test: T5.4 AI Malformed Input & Resilience Stress Test.

使用真实 HMAC-Nonce 鉴权头（同时显式验证无鉴权返回 401）对 /api/ai/divination 执行真实对抗压测。
"""
import hashlib
import hmac
import json
import time
import uuid
from typing import Any, Dict, List

import pytest
from starlette.testclient import TestClient

from backend.app.api import routes_ai
from backend.app.core.config import settings
from backend.app.core.security import clear_replay_cache
from backend.app.main import app
from backend.app.services.ai_service import RESPONSIBLE_GAMING_KEYWORDS

_TEST_HMAC_SECRET = "t54-adversarial-test-hmac-secret"


@pytest.fixture(autouse=True)
def _setup_t54_auth(monkeypatch):
    monkeypatch.setattr(settings, "APP_HMAC_SECRET", _TEST_HMAC_SECRET)
    routes_ai._AI_RATE_BUCKETS.clear()
    clear_replay_cache()
    yield
    routes_ai._AI_RATE_BUCKETS.clear()
    clear_replay_cache()


def _signed_post(client: TestClient, payload: Dict[str, Any]):
    """支持 pytest 与 run_all_e2e 直接调用双模式，使用当前测试 HMAC 密钥构造一次性防重放签名。"""
    if not settings.APP_HMAC_SECRET:
        settings.APP_HMAC_SECRET = _TEST_HMAC_SECRET
    routes_ai._AI_RATE_BUCKETS.clear()
    secret = settings.APP_HMAC_SECRET
    raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    ts = str(int(time.time()))
    nonce = uuid.uuid4().hex
    body_sha256 = hashlib.sha256(raw).hexdigest()
    canonical = f"{ts}:{nonce}:POST:/api/ai/divination:{body_sha256}"
    sig = hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()
    headers = {
        "Content-Type": "application/json",
        "X-App-Timestamp": ts,
        "X-App-Nonce": nonce,
        "X-App-Signature": sig,
    }
    return client.post("/api/ai/divination", content=raw, headers=headers)


def test_t5_4_unauthenticated_request_strictly_rejected():
    """T5.4.0: 未携带鉴权头的请求必须严格返回 401。"""
    client = TestClient(app, raise_server_exceptions=False)
    resp = client.post(
        "/api/ai/divination",
        json={"lottery_type": "ssq", "recommended_numbers": {"primary_numbers": ["01", "02", "03", "04", "05", "06"]}},
    )
    assert resp.status_code == 401


def test_t5_4_corrupted_payload_schema_rejection_without_500():
    """T5.4.1: Malformed and corrupted payloads must return 422/400 with zero unhandled 500 crashes."""
    client = TestClient(app, raise_server_exceptions=False)

    corrupted_cases: List[Dict[str, Any]] = [
        {},
        {"lottery_type": "ssq"},
        {"recommended_numbers": {"primary_numbers": ["01", "02"]}},
        {"lottery_type": "ssq", "recommended_numbers": 12345},
        {"lottery_type": "ssq", "recommended_numbers": "not_a_dict"},
        {"lottery_type": "unsupported_lottery_xyz", "recommended_numbers": {"primary_numbers": ["01"]}},
        {"lottery_type": None, "recommended_numbers": None},
        {"lottery_type": "ssq", "recommended_numbers": []},
    ]

    for idx, case in enumerate(corrupted_cases):
        resp = _signed_post(client, case)
        assert resp.status_code in (400, 422), (
            f"Case {idx} returned unexpected status {resp.status_code}: {resp.text}"
        )


def test_t5_4_negative_and_extreme_numerical_inputs_resilience():
    """T5.4.2: Negative numbers, zeroes, floats, and extreme numbers must be handled safely without 500."""
    client = TestClient(app, raise_server_exceptions=False)

    edge_number_cases = [
        {"lottery_type": "ssq", "provider": "fallback", "recommended_numbers": {"primary_numbers": [-1, -5, -12, -22, -30, -33], "secondary_numbers": ["-1"]}},
        {"lottery_type": "fc3d", "provider": "fallback", "recommended_numbers": {"primary_numbers": [0, 0, 0]}},
        {"lottery_type": "dlt", "provider": "fallback", "recommended_numbers": {"primary_numbers": [999999999, 888888888, 777777777, 666666666, 555555555], "secondary_numbers": ["1234567", "9876543"]}},
        {"lottery_type": "qxc", "provider": "fallback", "recommended_numbers": {"primary_numbers": ["3.14", "2.71", "1.41", "0.57", "9.99", "8.88"], "secondary_numbers": ["14.0"]}},
    ]

    for idx, case in enumerate(edge_number_cases):
        resp = _signed_post(client, case)
        assert resp.status_code in (200, 400, 422), f"Numerical edge case {idx} returned {resp.status_code}"
        if resp.status_code == 200:
            data = resp.json()
            assert data["has_disclaimer"] is True
            text = data["divination_text"]
            assert "【理性倡议】" in text
            for kw in RESPONSIBLE_GAMING_KEYWORDS:
                assert kw in text


def test_t5_4_gigantic_strings_and_buffer_stress():
    """T5.4.3: Gigantic strings and large nested payload stress testing must return 413 or 422 without 500."""
    client = TestClient(app, raise_server_exceptions=False)

    giant_payload = {
        "lottery_type": "ssq",
        "recommended_numbers": {
            "primary_numbers": ["A" * 1000, "B" * 1000, "03", "04", "05", "06"],
            "secondary_numbers": ["C" * 500],
        },
        "birth_datetime": "9" * 10000,
        "bazi_summary": {f"key_{i}": f"val_{i}" * 10 for i in range(200)},
    }

    resp = _signed_post(client, giant_payload)
    assert resp.status_code in (400, 413, 422), f"Expected 400/413/422, got {resp.status_code}"


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
            "provider": "fallback",
            "recommended_numbers": numbers_map[lt],
            "birth_datetime": "1995-10-20T14:30:00",
            "stream": False,
        }
        resp = _signed_post(client, payload)
        assert resp.status_code == 200, f"Divination request for {lt} failed: {resp.text}"

        data = resp.json()
        text = data["divination_text"]
        for header in ["【命盘乾坤】", "【数理玄机】", "【五行合局】", "【灵感吉言】", "【理性倡议】"]:
            assert header in text
        for kw in RESPONSIBLE_GAMING_KEYWORDS:
            assert kw in text
        assert data["has_disclaimer"] is True


def test_t5_4_streaming_sse_malformed_and_disclaimer_resilience():
    """T5.4.5: Server-Sent Events (SSE) streaming divination resilience and terminal completion."""
    client = TestClient(app)
    payload = {
        "lottery_type": "ssq",
        "provider": "fallback",
        "recommended_numbers": {"primary_numbers": ["03", "08", "13", "18", "23", "28"], "secondary_numbers": ["16"]},
        "birth_datetime": "1988-03-08T06:00:00",
        "stream": True,
    }

    resp = _signed_post(client, payload)
    assert resp.status_code == 200
    assert resp.headers.get("content-type", "").startswith("text/event-stream")

    raw_events = resp.text.strip().split("\n\n")
    assert len(raw_events) >= 2

    accumulated_text = ""
    terminal_event_found = False
    for event in raw_events:
        if not event.strip():
            continue
        assert event.startswith("data: ")
        data_json = json.loads(event[6:])
        accumulated_text += data_json.get("delta", "")
        if data_json.get("done") is True:
            terminal_event_found = True

    assert terminal_event_found
    assert "【理性倡议】" in accumulated_text
    for kw in RESPONSIBLE_GAMING_KEYWORDS:
        assert kw in accumulated_text


def test_t5_4_null_ticket_fields_adversarial_reproduction():
    """T5.4.6: Explicit null ticket fields must yield 200 without TypeError."""
    client = TestClient(app, raise_server_exceptions=False)

    payload_null_sec = {
        "lottery_type": "ssq",
        "provider": "fallback",
        "recommended_numbers": {
            "primary_numbers": ["01", "02", "03", "04", "05", "06"],
            "secondary_numbers": None,
        },
    }
    assert _signed_post(client, payload_null_sec).status_code == 200

    payload_null_pri = {
        "lottery_type": "ssq",
        "provider": "fallback",
        "recommended_numbers": {
            "primary_numbers": None,
            "secondary_numbers": ["16"],
        },
    }
    assert _signed_post(client, payload_null_pri).status_code == 200
