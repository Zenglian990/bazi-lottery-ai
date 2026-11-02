"""Tests for enhanced features: Dual-compatible stats, Chinese localization,
Today's LiuRi & fortune endpoints, Multi-bet lottery recommendations with deduplication,
and Chinese Name Numerology with 12 Shichen.
"""
import pytest
from starlette.testclient import TestClient

from backend.app.main import app
from backend.app.services.name_numerology import (
    analyze_name_numerology,
    get_character_stroke,
    split_chinese_name,
)
from backend.app.services.bazi_engine import calculate_four_pillars, parse_shichen
from backend.app.services.numerology_engine import (
    compute_bazi_score,
    recommend_lottery_tickets,
)
from backend.app.services.lottery_rules import validate_ticket
from backend.app.services.lottery_stats import calculate_lottery_stats
from backend.app.db.database import get_draws, seed_database_if_empty, get_total_draws_count


@pytest.fixture
def client():
    return TestClient(app)


# ============================================================================
# 1. Dual-Compatible Stats Schema & Database Auto-Seed
# ============================================================================
class TestDualCompatibleStatsSchema:
    def test_stats_endpoint_contains_android_fields(self, client):
        """GET /api/lottery/stats/{type} includes total_draws, last_issue, and stats dict."""
        resp = client.get("/api/lottery/stats/ssq")
        assert resp.status_code == 200
        data = resp.json()

        assert "total_draws" in data
        assert "draws_analyzed" in data
        assert data["total_draws"] == data["draws_analyzed"]
        assert data["total_draws"] >= 100

        assert "last_issue" in data
        assert "latest_issue" in data
        assert data["last_issue"] == data["latest_issue"]

        assert "stats" in data
        assert "primary" in data["stats"]
        assert len(data["stats"]["primary"]) == 33  # SSQ 33 red balls

        sample_stat = data["stats"]["primary"]["01"]
        for key in ["frequency", "rate", "is_hot", "is_cold", "current_omission", "max_omission", "avg_omission"]:
            assert key in sample_stat

        assert isinstance(sample_stat["frequency"], int)
        assert isinstance(sample_stat["rate"], float)
        assert isinstance(sample_stat["is_hot"], bool)
        assert isinstance(sample_stat["is_cold"], bool)

        assert "secondary" in data["stats"]
        assert len(data["stats"]["secondary"]) == 16  # SSQ 16 blue balls

    def test_auto_seed_on_empty_query(self, tmp_path):
        """Querying get_draws auto-seeds database if empty."""
        temp_db = tmp_path / "test_auto_seed.db"
        # Database does not exist yet; querying draws should auto-seed
        draws = get_draws("ssq", limit=10, db_path=temp_db)
        assert len(draws) == 10
        cnt = get_total_draws_count("ssq", db_path=temp_db)
        assert cnt >= 100


# ============================================================================
# 2. Complete Chinese Localization
# ============================================================================
class TestChineseLocalization:
    def test_bazi_destiny_pure_chinese_elements(self, client):
        """POST /api/bazi/destiny returns pure Chinese characters in xi_yong_shen, ji_shen, and lacking_elements."""
        payload = {
            "birth_datetime": "1990-08-15T08:30:00",
            "calendar_type": "solar",
            "gender": "male",
        }
        resp = client.post("/api/bazi/destiny", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        valid_chinese_elements = {"金", "木", "水", "火", "土"}

        assert len(data["xi_yong_shen"]) > 0
        for elem in data["xi_yong_shen"]:
            assert elem in valid_chinese_elements, f"Found non-Chinese element in xi_yong_shen: {elem}"

        assert len(data["ji_shen"]) > 0
        for elem in data["ji_shen"]:
            assert elem in valid_chinese_elements, f"Found non-Chinese element in ji_shen: {elem}"

        for elem in data["lacking_elements"]:
            assert elem in valid_chinese_elements, f"Found non-Chinese element in lacking_elements: {elem}"

    def test_compute_bazi_score_chinese_and_english_equivalence(self):
        """compute_bazi_score yields identical scores whether given Chinese or English elements."""
        score_zh, tier_zh = compute_bazi_score(
            number=3,  # Hetu 3 -> Wood
            xi_yong_shen=["木", "火"],
            lacking_elements=["水"],
            ji_shen=["金"],
        )
        score_en, tier_en = compute_bazi_score(
            number=3,
            xi_yong_shen=["wood", "fire"],
            lacking_elements=["water"],
            ji_shen=["metal"],
        )
        assert score_zh == score_en == 1.00
        assert tier_zh == tier_en == "Tier 1"

        # Taboo element check
        score_taboo_zh, tier_taboo_zh = compute_bazi_score(
            number=4,  # Hetu 4 -> Metal
            xi_yong_shen=["木"],
            lacking_elements=["水"],
            ji_shen=["金"],
        )
        score_taboo_en, tier_taboo_en = compute_bazi_score(
            number=4,
            xi_yong_shen=["wood"],
            lacking_elements=["water"],
            ji_shen=["metal"],
        )
        assert score_taboo_zh == score_taboo_en == 0.15
        assert tier_taboo_zh == tier_taboo_en == "Tier 5"


# ============================================================================
# 3. Real-Time Date & Today's LiuRi Endpoints
# ============================================================================
class TestRealTimeLiuRiAndFortune:
    def test_get_today_fortune_endpoint(self, client):
        """GET /api/bazi/today-fortune returns valid solar/lunar calendar, Four Pillars, and Huangli."""
        resp = client.get("/api/bazi/today-fortune")
        assert resp.status_code == 200
        data = resp.json()

        assert "solar_date" in data
        assert "solar_datetime" in data
        assert "lunar_date" in data
        assert "ganzhi_year" in data
        assert "ganzhi_month" in data
        assert "ganzhi_day" in data
        assert "ganzhi_hour" in data
        assert "day_nayin" in data
        assert "cai_shen_position" in data
        assert "xi_shen_position" in data
        assert isinstance(data["day_yi"], list)
        assert isinstance(data["day_ji"], list)
        assert "chong_sha" in data

    def test_post_today_fortune_with_natal_resonance(self, client):
        """POST /api/bazi/today-fortune calculates natal resonance with user's birth datetime."""
        payload = {
            "birth_datetime": "1990-08-15T08:30:00",
            "calendar_type": "solar",
            "user_name": "曾练",
        }
        resp = client.post("/api/bazi/today-fortune", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        assert data["resonance"] is not None
        res = data["resonance"]
        assert "user_day_master" in res
        assert "user_xi_yong" in res
        assert "resonance_multiplier" in res
        assert res["resonance_multiplier"] >= 1.0
        assert "description" in res

    def test_recommendation_includes_today_liuri(self, client):
        """POST /api/bazi/recommend returns today_liuri in response."""
        payload = {
            "lottery_type": "ssq",
            "count": 1,
        }
        resp = client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        assert "today_liuri" in data
        assert data["today_liuri"] is not None
        assert "ganzhi_day" in data["today_liuri"]
        assert "harmonious_elements" in data["today_liuri"]


# ============================================================================
# 4. Multi-Bet Lottery Recommendations & Deduplication
# ============================================================================
class TestMultiBetRecommendationsAndDeduplication:
    def test_multi_bet_distinctness_5_and_10_bets(self, client):
        """Generating 5 and 10 bets produces 100% unique tickets without duplicate bets."""
        for count in [5, 10]:
            payload = {
                "lottery_type": "ssq",
                "count": count,
                "strategy": "balanced",
            }
            resp = client.post("/api/bazi/recommend", json=payload)
            assert resp.status_code == 200
            data = resp.json()

            recs = data["recommendations"]
            assert len(recs) == count

            # Verify bet_index and formatted_ticket
            for idx, r in enumerate(recs):
                assert r["bet_index"] == idx + 1
                assert r["formatted_ticket"] is not None
                assert len(r["primary_numbers"]) == 6
                assert len(r["secondary_numbers"]) == 1

            # Cross-ticket deduplication verification
            tickets = [
                (tuple(r["primary_numbers"]), tuple(r["secondary_numbers"]))
                for r in recs
            ]
            assert len(set(tickets)) == count, f"Found duplicate tickets in multi-bet batch of {count}!"

    def test_multi_bet_fc3d_and_qxc_distinctness(self, client):
        """FC3D and QXC multi-bet generation produces non-duplicate tickets."""
        for ltype in ["fc3d", "qxc"]:
            payload = {
                "lottery_type": ltype,
                "count": 5,
            }
            resp = client.post("/api/bazi/recommend", json=payload)
            assert resp.status_code == 200
            recs = resp.json()["recommendations"]
            assert len(recs) == 5

            tickets = [tuple(r["primary_numbers"]) for r in recs]
            assert len(set(tickets)) == 5

    def test_copy_text_generation(self, client):
        """Response contains unified copy_text with formatted ticket lines."""
        payload = {
            "lottery_type": "dlt",
            "count": 5,
            "user_name": "曾练",
        }
        resp = client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        assert "copy_text" in data
        assert data["copy_text"] is not None
        assert "曾练" in data["copy_text"]
        assert "大乐透" in data["copy_text"]
        assert "第01注:" in data["copy_text"]
        assert "第05注:" in data["copy_text"]


# ============================================================================
# 5. Chinese Name Numerology & 12 Shichen
# ============================================================================
class TestNameNumerologyAnd12Shichen:
    def test_analyze_name_numerology_zeng_lian(self):
        """Analyze name 曾练: accurate stroke counts, Five Grids, and lucky numbers."""
        res = analyze_name_numerology("曾练")
        assert res["user_name"] == "曾练"
        assert res["surname"] == "曾"
        assert res["given_name"] == "练"

        assert res["character_strokes"]["曾"] == 12
        assert res["character_strokes"]["练"] == 8

        # Five Grids
        assert res["tian_ge"]["value"] == 13
        assert res["tian_ge"]["fortune"] == "大吉"
        assert res["ren_ge"]["value"] == 20
        assert res["di_ge"]["value"] == 9
        assert res["zong_ge"]["value"] == 20

        assert len(res["name_lucky_numbers"]) > 0

    def test_compound_surname_numerology(self):
        """Analyze compound surname 诸葛孔明."""
        res = analyze_name_numerology("诸葛孔明")
        assert res["surname"] == "诸葛"
        assert res["given_name"] == "孔明"
        assert res["tian_ge"]["value"] > 0
        assert res["ren_ge"]["value"] > 0

    def test_destiny_endpoint_with_user_name_and_shichen(self, client):
        """POST /api/bazi/destiny with user_name and shichen populates name_numerology and shichen."""
        payload = {
            "birth_datetime": "1990-08-15",
            "calendar_type": "solar",
            "user_name": "曾练",
            "shichen": "午时",
        }
        resp = client.post("/api/bazi/destiny", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        assert data["user_name"] == "曾练"
        assert data["shichen"] == "午时"
        assert data["name_numerology"] is not None
        assert data["name_numerology"]["tian_ge"]["value"] == 13

        # Hour pillar for 午时 (12:00) on 1990-08-15
        assert data["four_pillars"]["hour"]["branch"] == "午"

    def test_early_and_late_zi_hour_precision(self):
        """早子时 (00:00-01:00) vs 晚子时 (23:00-24:00) yields distinct time pillars."""
        early_res = calculate_four_pillars(
            birth_datetime="1990-08-15",
            shichen="早子时",
        )
        late_res = calculate_four_pillars(
            birth_datetime="1990-08-15",
            shichen="晚子时",
        )

        assert early_res["four_pillars"]["hour"]["branch"] == "子"
        assert late_res["four_pillars"]["hour"]["branch"] == "子"

        # Early Zi hour is 庚子, late Zi hour under Sect 2 is 壬子
        early_time_stem = early_res["four_pillars"]["hour"]["stem"]
        late_time_stem = late_res["four_pillars"]["hour"]["stem"]
        assert early_time_stem != late_time_stem
        assert early_time_stem == "庚"
        assert late_time_stem == "壬"

    def test_shichen_mappings_all_twelve(self):
        """All 12 Shichen names parse properly to expected clock times."""
        shichen_branches = {
            "早子时": "子",
            "丑时": "丑",
            "寅时": "寅",
            "卯时": "卯",
            "辰时": "辰",
            "巳时": "巳",
            "午时": "午",
            "未时": "未",
            "申时": "申",
            "酉时": "酉",
            "戌时": "戌",
            "亥时": "亥",
            "晚子时": "子",
        }
        for name, expected_branch in shichen_branches.items():
            res = calculate_four_pillars(
                birth_datetime="1995-10-01",
                shichen=name,
            )
            assert res["four_pillars"]["hour"]["branch"] == expected_branch
