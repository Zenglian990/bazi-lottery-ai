"""Integration tests for FastAPI RESTful API endpoints."""
import pytest
from fastapi.testclient import TestClient

from backend.app.main import app
from backend.app.db.database import init_db

@pytest.fixture(scope="module")
def client():
    """Create test client with database initialized."""
    init_db(seed_if_empty=True)
    with TestClient(app) as c:
        yield c

def test_api_health_check(client):
    """Test GET /api/health endpoint returns 200 and correct structure."""
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "Lottery Big Data & Rule Engine"
    assert "ssq" in data["supported_lotteries"]
    assert "dlt" in data["supported_lotteries"]
    assert data["database"]["status"] == "connected"
    assert data["database"]["total_draws"]["ssq"] >= 100

def test_root_endpoint(client):
    """Test GET / endpoint returns service meta."""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert "service" in data
    assert data["docs_url"] == "/docs"

def test_get_all_rules(client):
    """Test GET /api/lottery/rules endpoint."""
    response = client.get("/api/lottery/rules")
    assert response.status_code == 200
    data = response.json()
    rules = data["rules"]
    assert "ssq" in rules
    assert "dlt" in rules
    assert "fc3d" in rules
    assert "qxc" in rules

def test_get_single_rule_success(client):
    """Test GET /api/lottery/rules/{lottery_type} for SSQ and QXC."""
    resp_ssq = client.get("/api/lottery/rules/ssq")
    assert resp_ssq.status_code == 200
    assert resp_ssq.json()["chinese_name"] == "双色球"

    resp_qxc = client.get("/api/lottery/rules/qxc")
    assert resp_qxc.status_code == 200
    assert resp_qxc.json()["zones"][1]["max_value"] == 14  # Modern rule

def test_get_single_rule_not_found(client):
    """Test GET /api/lottery/rules/unknown returns 404."""
    response = client.get("/api/lottery/rules/invalid_code")
    assert response.status_code == 404

def test_get_history_pagination(client):
    """Test GET /api/lottery/history/{lottery_type} pagination."""
    response = client.get("/api/lottery/history/ssq?limit=10&page=1")
    assert response.status_code == 200
    data = response.json()
    assert data["lottery_type"] == "ssq"
    assert data["limit"] == 10
    assert data["page"] == 1
    assert data["total"] >= 100
    assert len(data["records"]) == 10
    # First record should have primary_numbers and secondary_numbers
    rec = data["records"][0]
    assert len(rec["primary_numbers"]) == 6
    assert len(rec["secondary_numbers"]) == 1

def test_get_stats_endpoint(client):
    """Test GET /api/lottery/stats/{lottery_type} returns full stats."""
    response = client.get("/api/lottery/stats/ssq?limit=50&window=20")
    assert response.status_code == 200
    data = response.json()
    assert data["lottery_type"] == "ssq"
    assert data["draws_analyzed"] == 50
    assert len(data["primary_zone"]["stats"]) == 33
    assert len(data["secondary_zone"]["stats"]) == 16

def test_get_hot_cold_endpoint(client):
    """Test GET /api/lottery/hot-cold/{lottery_type} returns partitioned numbers."""
    response = client.get("/api/lottery/hot-cold/dlt?window=30")
    assert response.status_code == 200
    data = response.json()
    assert data["lottery_type"] == "dlt"
    assert data["window"] == 30
    assert "hot_numbers" in data["primary_zone"]
    assert "warm_numbers" in data["primary_zone"]
    assert "cold_numbers" in data["primary_zone"]

def test_get_omission_endpoint(client):
    """Test GET /api/lottery/omission/{lottery_type} returns ranked omissions."""
    response = client.get("/api/lottery/omission/fc3d")
    assert response.status_code == 200
    data = response.json()
    assert data["lottery_type"] == "fc3d"
    assert len(data["primary_zone"]) == 10
    item = data["primary_zone"][0]
    assert "number" in item
    assert "current_omission" in item
    assert "desire_index" in item

def test_validate_ticket_endpoint_valid(client):
    """Test POST /api/lottery/validate/{lottery_type} with valid tickets."""
    # SSQ valid
    payload_ssq = {
        "primary_numbers": ["15", "4", "27", "6", "10", "26"],
        "secondary_numbers": ["11"],
    }
    resp = client.post("/api/lottery/validate/ssq", json=payload_ssq)
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_valid"] is True
    assert data["error_message"] is None
    assert data["normalized_primary"] == ["04", "06", "10", "15", "26", "27"]
    assert data["normalized_secondary"] == ["11"]
    assert data["formatted_string"] == "04,06,10,15,26,27|11"

    # QXC valid with modern 7th ball = 14
    payload_qxc = {
        "primary_numbers": [2, 9, 4, 5, 6, 3],
        "secondary_numbers": [14],
    }
    resp = client.post("/api/lottery/validate/qxc", json=payload_qxc)
    assert resp.status_code == 200
    assert resp.json()["is_valid"] is True
    assert resp.json()["normalized_secondary"] == ["14"]

def test_validate_ticket_endpoint_invalid(client):
    """Test POST /api/lottery/validate/{lottery_type} with invalid tickets."""
    # SSQ with ball out of range
    payload = {
        "primary_numbers": ["01", "02", "03", "04", "05", "34"],
        "secondary_numbers": ["01"],
    }
    resp = client.post("/api/lottery/validate/ssq", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_valid"] is False
    assert "outside valid range" in data["error_message"]

    # QXC with 7th ball = 15 (invalid)
    payload_qxc = {
        "primary_numbers": [2, 9, 4, 5, 6, 3],
        "secondary_numbers": [15],
    }
    resp = client.post("/api/lottery/validate/qxc", json=payload_qxc)
    assert resp.status_code == 200
    assert resp.json()["is_valid"] is False

def test_sync_endpoint(client):
    """Test POST /api/lottery/sync triggers sync response structure."""
    response = client.post("/api/lottery/sync", json={"lottery_type": "ssq"})
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert "synced_counts" in data
    assert "ssq" in data["synced_counts"]
