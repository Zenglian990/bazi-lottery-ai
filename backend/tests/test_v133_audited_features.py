import pytest
from starlette.testclient import TestClient
from backend.app.main import app
from backend.app.services.ai_service import _validate_safe_url
from backend.app.services.bazi_engine import calculate_four_pillars

client = TestClient(app)

def test_ai_ssrf_rejection():
    """Verify that private IP / metadata URLs are rejected by _validate_safe_url."""
    unsafe_urls = [
        "http://169.254.169.254/computeMetadata/v1",
        "http://metadata.google.internal/computeMetadata/v1",
        "http://localhost:8000/v1",
        "http://127.0.0.1:8000/v1",
        "http://192.168.1.1:8000/v1",
        "http://10.0.0.1:8000/v1",
        "ftp://example.com/v1",
    ]
    for u in unsafe_urls:
        with pytest.raises(ValueError):
            _validate_safe_url(u)

    # Valid external HTTPS url should pass
    safe_url = "https://api.openai.com/v1"
    assert _validate_safe_url(safe_url) == safe_url

def test_gender_dayun_differentiation():
    """Verify male (乾造) vs female (坤造) dayun direction and list differ."""
    dt = "1990-08-15T08:30:00"
    # 1990 is 庚午 (庚 is Yang).
    # Yang male: dayun forward = True.
    # Yang female: dayun forward = False.
    res_m = calculate_four_pillars(dt, gender="male")
    res_f = calculate_four_pillars(dt, gender="female")

    assert res_m["gender"] == "male"
    assert res_f["gender"] == "female"
    assert res_m["dayun_forward"] is True
    assert res_f["dayun_forward"] is False
    assert res_m["dayun_list"] != res_f["dayun_list"]

def test_destiny_api_returns_gender_and_dayun():
    """POST /api/bazi/destiny returns gender and dayun details."""
    payload_m = {
        "birth_datetime": "1990-08-15T08:30:00",
        "gender": "male"
    }
    payload_f = {
        "birth_datetime": "1990-08-15T08:30:00",
        "gender": "female"
    }
    r_m = client.post("/api/bazi/destiny", json=payload_m).json()
    r_f = client.post("/api/bazi/destiny", json=payload_f).json()

    assert r_m["gender"] == "male"
    assert r_f["gender"] == "female"
    assert r_m["dayun_forward"] is True
    assert r_f["dayun_forward"] is False
