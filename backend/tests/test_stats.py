"""Unit tests for big data analytics: frequency, moving window cold/hot, omission, and weights."""
import pytest
from backend.app.services.lottery_stats import (
    analyze_zone_stats,
    calculate_lottery_stats,
    calculate_hot_cold,
    calculate_omission,
    get_statistical_weights,
)
from backend.app.db.database import get_draws, init_db

@pytest.fixture(scope="module")
def setup_seed_db():
    """Ensure database is seeded for real dataset tests."""
    init_db(seed_if_empty=True)

def test_controlled_frequency_calculation():
    """Verify frequency and total count on controlled synthetic draws."""
    # 10 draws: index 0 (newest) to 9 (oldest)
    draws = [
        {"issue": "10", "primary_numbers": ["01", "03"]},
        {"issue": "09", "primary_numbers": ["02", "04"]},
        {"issue": "08", "primary_numbers": ["01", "05"]},
        {"issue": "07", "primary_numbers": ["06", "07"]},
        {"issue": "06", "primary_numbers": ["01", "08"]},
        {"issue": "05", "primary_numbers": ["09", "10"]},
        {"issue": "04", "primary_numbers": ["02", "03"]},
        {"issue": "03", "primary_numbers": ["04", "05"]},
        {"issue": "02", "primary_numbers": ["06", "07"]},
        {"issue": "01", "primary_numbers": ["08", "09"]},
    ]
    pool = [f"{i:02d}" for i in range(1, 11)]
    stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=pool,
        balls_per_draw=2,
        window=10,
    )
    stat_map = {s.number: s for s in stats}

    # "01" appeared at indices 0, 2, 4 -> 3 times out of 10
    assert stat_map["01"].total_count == 3
    assert stat_map["01"].frequency == 0.3
    assert stat_map["01"].recent_count == 3

    # "10" appeared at index 5 -> 1 time out of 10
    assert stat_map["10"].total_count == 1
    assert stat_map["10"].frequency == 0.1

def test_controlled_hot_cold_window_classification():
    """Verify hot, warm, cold classification based on expected value threshold."""
    # Pool of 10 balls, 2 balls per draw.
    # W = 10, theoretical prob = 2 / 10 = 0.2, expected count = 10 * 0.2 = 2.0
    # Hot threshold factor = 1.25 -> expected * 1.25 = 2.5. If count >= 2.5 (e.g. 3) -> hot.
    # Cold threshold factor = 0.75 -> expected * 0.75 = 1.5. If count <= 1.5 (e.g. 0 or 1) -> cold.
    # Warm: count == 2.
    draws = [
        {"primary_numbers": ["01", "02"]},
        {"primary_numbers": ["01", "02"]},
        {"primary_numbers": ["01", "03"]},
        {"primary_numbers": ["01", "03"]},
        {"primary_numbers": ["04", "05"]},
        {"primary_numbers": ["06", "07"]},
        {"primary_numbers": ["08", "09"]},
        {"primary_numbers": ["04", "05"]},
        {"primary_numbers": ["06", "07"]},
        {"primary_numbers": ["08", "09"]},
    ]
    pool = [f"{i:02d}" for i in range(1, 11)]
    stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=pool,
        balls_per_draw=2,
        window=10,
    )
    stat_map = {s.number: s for s in stats}

    # "01" has 4 occurrences in window 10 -> Hot (4 >= 2.5)
    assert stat_map["01"].hot_status == "hot"

    # "02" has 2 occurrences -> Warm (1.5 < 2 < 2.5)
    assert stat_map["02"].hot_status == "warm"

    # "10" has 0 occurrences -> Cold (0 <= 1.5)
    assert stat_map["10"].hot_status == "cold"

def test_controlled_omission_mathematical_precision():
    """Verify g0, max omission, avg omission (N-k)/(k+1), and desire index."""
    # 10 draws (indices 0 to 9)
    # Number "01" appears at indices [2, 5, 8]
    # N = 10, k = 3
    # Intervals:
    #   g0 = 2 (indices[0] == 2)
    #   g1 = 5 - 2 - 1 = 2
    #   g2 = 8 - 5 - 1 = 2
    #   g3 = (10 - 1) - 8 = 1
    # Total gap sum = 2 + 2 + 2 + 1 = 7 (= 10 - 3)
    # Max omission = max(2, 2, 2, 1) = 2
    # Avg omission = 7 / (3 + 1) = 1.75
    # Desire index = 2 / 1.75 = 1.14
    draws = [{"primary_numbers": []} for _ in range(10)]
    draws[2]["primary_numbers"] = ["01"]
    draws[5]["primary_numbers"] = ["01"]
    draws[8]["primary_numbers"] = ["01"]

    stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=["01", "02"],
        balls_per_draw=1,
        window=10,
    )
    stat_map = {s.number: s for s in stats}

    s01 = stat_map["01"]
    assert s01.current_omission == 2
    assert s01.max_omission == 2
    assert s01.avg_omission == 1.75
    assert s01.desire_index == 1.14

    # Ball "02" never appeared:
    # current_omission = 10, max_omission = 10, avg_omission = 10.0, desire_index = 1.0
    s02 = stat_map["02"]
    assert s02.current_omission == 10
    assert s02.max_omission == 10
    assert s02.avg_omission == 10.0
    assert s02.desire_index == 1.0

def test_omission_when_appeared_in_newest_draw():
    """Verify that when a number appears in the newest draw (index 0), current omission is 0 and desire index is 0."""
    draws = [{"primary_numbers": ["01"]} for _ in range(5)]
    stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=["01"],
        balls_per_draw=1,
        window=5,
    )
    assert stats[0].current_omission == 0
    assert stats[0].desire_index == 0.0

def test_ssq_real_seed_statistical_analytics(setup_seed_db):
    """Test full statistics calculation on the authentic 100 SSQ historical draws."""
    draws = get_draws("ssq", limit=100)
    assert len(draws) == 100

    stats = calculate_lottery_stats("ssq", draws, window=30)
    assert stats is not None
    assert stats.lottery_type == "ssq"
    assert stats.draws_analyzed == 100
    assert len(stats.primary_zone.stats) == 33  # Red balls 01-33
    assert stats.secondary_zone is not None
    assert len(stats.secondary_zone.stats) == 16  # Blue balls 01-16

    # Verify that all red ball frequencies are within reasonable historical limits [0, 1]
    for s in stats.primary_zone.stats:
        assert 0.0 <= s.frequency <= 1.0
        assert s.hot_status in ["hot", "warm", "cold"]
        assert s.current_omission >= 0
        assert s.max_omission >= s.current_omission

def test_hot_cold_partitioning(setup_seed_db):
    """Test hot/cold classification output structure on authentic draws."""
    draws = get_draws("dlt", limit=100)
    hc = calculate_hot_cold("dlt", draws, window=30)
    assert hc is not None
    assert hc.lottery_type == "dlt"
    assert hc.window == 30

    # Total front balls should sum to 35
    total_front = (
        len(hc.primary_zone.hot_numbers)
        + len(hc.primary_zone.warm_numbers)
        + len(hc.primary_zone.cold_numbers)
    )
    assert total_front == 35

    # Total back balls should sum to 12
    assert hc.secondary_zone is not None
    total_back = (
        len(hc.secondary_zone.hot_numbers)
        + len(hc.secondary_zone.warm_numbers)
        + len(hc.secondary_zone.cold_numbers)
    )
    assert total_back == 12

def test_omission_rankings(setup_seed_db):
    """Test omission rankings sorting by desire index descending."""
    draws = get_draws("fc3d", limit=100)
    om = calculate_omission("fc3d", draws)
    assert om is not None
    assert om.lottery_type == "fc3d"
    assert len(om.primary_zone) == 10

    # Check descending order by desire_index
    for i in range(len(om.primary_zone) - 1):
        assert om.primary_zone[i].desire_index >= om.primary_zone[i + 1].desire_index

def test_statistical_weights_generation(setup_seed_db):
    """Test generating positive multiplier weights for Bazi recommendation."""
    draws = get_draws("ssq", limit=100)
    weights = get_statistical_weights("ssq", draws, zone="primary", window=30)
    assert len(weights) == 33
    for ball_num, w in weights.items():
        assert w > 0.2  # positive weighting factor
        assert 0.2 <= w <= 2.5


def test_fc3d_intra_draw_duplicates_omission_regression():
    """
    Regression test for FC3D/QXC intra-draw duplicate numbers.
    Asserts that:
    1. Total occurrences count every ball in the draw.
    2. Draw appearance indices are deduplicated per draw.
    3. Every omission interval is >= 0 (no negative gaps like -1).
    4. Average omission (N-k)/(k+1) is strictly > 0 when k < N.
    5. Desire index is non-negative and mathematically valid.
    """
    # 5 draws with repeated numbers (baozi, zusan)
    # Draw 0 (newest): ['8', '8', '8'] (baozi)
    # Draw 1: ['0', '8', '8'] (zusan)
    # Draw 2: ['1', '2', '3']
    # Draw 3: ['8', '4', '5']
    # Draw 4 (oldest): ['6', '7', '9']
    draws = [
        {"issue": "005", "primary_numbers": ["8", "8", "8"]},
        {"issue": "004", "primary_numbers": ["0", "8", "8"]},
        {"issue": "003", "primary_numbers": ["1", "2", "3"]},
        {"issue": "002", "primary_numbers": ["8", "4", "5"]},
        {"issue": "001", "primary_numbers": ["6", "7", "9"]},
    ]
    pool = [str(i) for i in range(10)]
    stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=pool,
        balls_per_draw=3,
        window=5,
    )
    stat_map = {s.number: s for s in stats}

    # Number '8' appeared:
    # - Draw 0 (3 times)
    # - Draw 1 (2 times)
    # - Draw 3 (1 time)
    # Total count = 6, but distinct draws k = 3 out of N = 5
    s8 = stat_map["8"]
    assert s8.total_count == 6
    assert s8.frequency == 1.2
    # Distinct indices: [0, 1, 3]
    # Intervals:
    #   g_0 = 0 (newest draw 0)
    #   g_1 = 1 - 0 - 1 = 0 (between draw 0 and 1)
    #   g_2 = 3 - 1 - 1 = 1 (between draw 1 and 3)
    #   g_3 = (5 - 1) - 3 = 1 (after draw 3 to draw 4)
    # All intervals >= 0! Max omission = 1
    # Average omission = (5 - 3) / (3 + 1) = 2 / 4 = 0.5
    assert s8.current_omission == 0
    assert s8.max_omission == 1
    assert s8.avg_omission == 0.5
    assert s8.avg_omission > 0
    assert s8.desire_index == 0.0

    # Number '0' appeared only in Draw 1 (index 1):
    # Total count = 1, distinct k = 1 out of N = 5
    # Intervals:
    #   g_0 = 1
    #   g_1 = (5 - 1) - 1 = 3
    # Max omission = 3, Avg omission = (5 - 1) / (1 + 1) = 2.0
    s0 = stat_map["0"]
    assert s0.total_count == 1
    assert s0.current_omission == 1
    assert s0.max_omission == 3
    assert s0.avg_omission == 2.0
    assert s0.avg_omission > 0
    assert s0.desire_index == round(1 / 2.0, 2)  # 0.5

    # Verify invariants across ALL numbers in the pool:
    for num, s in stat_map.items():
        assert s.current_omission >= 0
        assert s.max_omission >= s.current_omission
        assert s.avg_omission > 0
        assert s.desire_index >= 0.0


def test_qxc_intra_draw_duplicates_omission_regression():
    """Verify QXC front-zone multi-digit repeated numbers do not corrupt omission intervals."""
    draws = [
        {"issue": f"202600{i+1}", "primary_numbers": ["5", "5", "1", "2", "3", "4"]}
        for i in range(5)
    ]
    pool = [str(i) for i in range(10)]
    stats = analyze_zone_stats(
        draws=draws,
        zone_key="primary_numbers",
        number_pool=pool,
        balls_per_draw=6,
        window=5,
    )
    s5 = [s for s in stats if s.number == "5"][0]
    # '5' appeared in all 5 draws (2 times per draw -> total 10)
    # k = 5, N = 5 -> distinct appearance in every draw
    assert s5.total_count == 10
    assert s5.current_omission == 0
    assert s5.max_omission == 0
    assert s5.avg_omission == 0.0
    assert s5.desire_index == 0.0

    # Digits not present (e.g. '0', '6', '7', '8', '9')
    s0 = [s for s in stats if s.number == "0"][0]
    assert s0.total_count == 0
    assert s0.current_omission == 5
    assert s0.max_omission == 5
    assert s0.avg_omission == 5.0
    assert s0.desire_index == 1.0


def test_sync_endpoint_lottery_type_validation():
    """Verify /api/lottery/sync returns 400 for unsupported lottery types and accepts valid types."""
    from fastapi.testclient import TestClient
    from backend.app.main import app

    client = TestClient(app)

    # Invalid lottery types must raise HTTP 400
    invalid_resp = client.post("/api/lottery/sync", json={"lottery_type": "invalid_game"})
    assert invalid_resp.status_code == 400
    assert "Unsupported lottery type: invalid_game" in invalid_resp.json()["detail"]

    empty_type_resp = client.post("/api/lottery/sync", json={"lottery_type": "unknown_lotto"})
    assert empty_type_resp.status_code == 400

    # Valid supported lottery types must return HTTP 200
    valid_resp = client.post("/api/lottery/sync", json={"lottery_type": "fc3d"})
    assert valid_resp.status_code == 200
    assert valid_resp.json()["status"] == "success"

