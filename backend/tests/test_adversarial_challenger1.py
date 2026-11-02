"""Empirical Adversarial Stress Test Suite - Challenger 1
Auditing Backend Algorithms, Chinese Localization, Multi-Bet Uniqueness,
Today's LiuRi Resonance, Name Numerology/12 Shichen, and Stats Schema Compatibility.
"""
from datetime import datetime, timedelta
import pytest
from starlette.testclient import TestClient

from backend.app.main import app
from backend.app.services.lottery_rules import validate_ticket, get_lottery_rule
from backend.app.services.name_numerology import analyze_name_numerology, split_chinese_name, get_character_stroke
from backend.app.services.bazi_engine import calculate_four_pillars, SHICHEN_MAPPING
from backend.app.services.numerology_engine import recommend_lottery_tickets


@pytest.fixture(scope="module")
def client():
    return TestClient(app)


# ==============================================================================
# Suite 1: Multi-Bet Uniqueness & Rule Conformance Stress Test
# ==============================================================================
class TestMultiBetUniquenessAndValidity:
    """Stress tests requesting 1, 5, 10, 20 bets across all lottery types and strategies."""

    @pytest.mark.parametrize("lottery_type", ["ssq", "dlt", "fc3d", "qxc"])
    @pytest.mark.parametrize("count", [1, 5, 10, 20])
    @pytest.mark.parametrize("strategy", ["balanced", "aggressive", "conservative"])
    @pytest.mark.parametrize("deterministic", [False, True])
    def test_multi_bet_direct_engine_uniqueness_and_rules(
        self, lottery_type, count, strategy, deterministic
    ):
        """Direct engine execution: Assert 100% ticket uniqueness and strict rule conformance."""
        bazi = calculate_four_pillars("1990-08-15 08:30:00")
        res = recommend_lottery_tickets(
            bazi_analysis=bazi,
            lottery_type=lottery_type,
            count=count,
            strategy=strategy,
            deterministic=deterministic,
            user_name="曾练",
            shichen="辰时",
        )

        recs = res["recommendations"]
        assert len(recs) == count, f"Expected {count} recommendations, got {len(recs)}"

        seen_tickets = set()
        for idx, rec in enumerate(recs):
            pri = rec["primary_numbers"]
            sec = rec.get("secondary_numbers")
            ticket_tuple = (tuple(pri), tuple(sec) if sec else ())
            
            # 1. Uniqueness within batch
            assert ticket_tuple not in seen_tickets, (
                f"Duplicate ticket detected in {lottery_type} (count={count}, strat={strategy}, "
                f"det={deterministic}) at index {idx}: {ticket_tuple}"
            )
            seen_tickets.add(ticket_tuple)

            # 2. Strict lottery rule conformance
            is_valid, err, norm_p, norm_s, formatted = validate_ticket(
                lottery_type=lottery_type,
                primary_numbers=pri,
                secondary_numbers=sec,
            )
            assert is_valid, f"Invalid ticket {ticket_tuple} for {lottery_type}: {err}"

            # 3. Rule constraints assertions
            rule = get_lottery_rule(lottery_type)
            p_rule = rule.zones[0]
            assert len(pri) == p_rule.count
            if p_rule.sorted:
                int_p = [int(x) for x in pri]
                assert int_p == sorted(int_p), f"Primary numbers not sorted in {lottery_type}: {pri}"
            if p_rule.unique:
                assert len(set(pri)) == len(pri), f"Primary numbers contain duplicates: {pri}"

            if len(rule.zones) > 1:
                s_rule = rule.zones[1]
                assert sec is not None
                assert len(sec) == s_rule.count
                if s_rule.sorted:
                    int_s = [int(x) for x in sec]
                    assert int_s == sorted(int_s), f"Secondary numbers not sorted in {lottery_type}: {sec}"
                if s_rule.unique:
                    assert len(set(sec)) == len(sec), f"Secondary numbers contain duplicates: {sec}"
            else:
                assert sec is None or len(sec) == 0

    @pytest.mark.parametrize("lottery_type", ["ssq", "dlt", "fc3d", "qxc"])
    @pytest.mark.parametrize("count", [5, 10, 20])
    def test_multi_bet_api_endpoint(self, client, lottery_type, count):
        """REST API POST /api/bazi/recommend multi-bet response verification."""
        payload = {
            "birth_datetime": "1992-05-18T14:20:00",
            "calendar_type": "solar",
            "lottery_type": lottery_type,
            "count": count,
            "strategy": "balanced",
            "user_name": "诸葛孔明",
            "shichen": "未时",
        }
        resp = client.post("/api/bazi/recommend", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        recs = data["recommendations"]
        assert len(recs) == count
        assert "copy_text" in data
        assert "诸葛孔明" in data["copy_text"]

        seen = set()
        for r in recs:
            t = (tuple(r["primary_numbers"]), tuple(r.get("secondary_numbers") or []))
            assert t not in seen, f"Duplicate found in API recommendations: {t}"
            seen.add(t)


# ==============================================================================
# Suite 2: Pure Chinese Localization Strict Assertion (50 Datetimes)
# ==============================================================================
class TestChineseLocalizationStrictAssertion:
    """Rigorous assertion of 100% pure Chinese characters and ZERO English tokens."""

    VALID_CHINESE_ELEMENTS = {"金", "木", "水", "火", "土"}
    ENGLISH_ELEMENT_TOKENS = {"wood", "fire", "earth", "metal", "water"}

    @classmethod
    def get_50_diverse_datetimes(cls):
        """Generate 50 historically, astronomically, and calendar-diverse test dates."""
        base_dates = [
            "1949-10-01T15:00:00", "1958-03-21T06:00:00", "1960-02-29T12:00:00",
            "1968-12-22T00:00:00", "1972-02-29T18:30:00", "1976-07-28T03:42:00",
            "1980-01-01T00:00:00", "1984-02-04T16:00:00", "1988-08-08T08:08:08",
            "1989-06-04T08:00:00", "1990-08-15T08:30:00", "1993-01-01T00:01:00",
            "1996-02-29T23:45:00", "1997-07-01T00:00:00", "1999-12-31T23:59:59",
            "2000-01-01T00:00:00", "2000-02-29T23:30:00", "2003-03-21T12:00:00",
            "2005-09-23T18:00:00", "2008-08-08T20:08:08", "2010-06-21T11:28:00",
            "2012-02-29T04:15:00", "2012-12-21T12:00:00", "2015-10-01T08:00:00",
            "2018-02-04T05:28:00", "2020-01-25T01:00:00", "2020-02-29T12:00:00",
            "2022-02-04T04:50:00", "2024-02-04T16:26:00", "2024-02-10T00:00:00",
            "2024-02-29T23:00:00", "2025-01-29T00:00:00", "2026-02-04T10:00:00",
            "2026-11-02T13:00:00", "2027-02-04T16:00:00", "2028-02-29T12:00:00",
            "2030-05-05T05:05:05", "2032-02-29T00:30:00", "2035-10-10T10:10:10",
        ]
        curr = datetime(1965, 5, 20, 10, 0)
        while len(base_dates) < 50:
            curr += timedelta(days=533, hours=5, minutes=17)
            base_dates.append(curr.strftime("%Y-%m-%dT%H:%M:%S"))
        return base_dates

    def test_50_datetimes_destiny_localization(self, client):
        """Assert all 50 datetimes return purely Chinese xi_yong_shen, ji_shen, and lacking_elements."""
        dates = self.get_50_diverse_datetimes()
        assert len(dates) == 50

        for dt_str in dates:
            resp = client.post("/api/bazi/destiny", json={"birth_datetime": dt_str})
            assert resp.status_code == 200, f"Destiny request failed for {dt_str}: {resp.text}"
            data = resp.json()

            xi_yong = data.get("xi_yong_shen", [])
            ji_shen = data.get("ji_shen", [])
            lacking = data.get("lacking_elements", [])

            assert len(xi_yong) > 0, f"Empty xi_yong_shen for {dt_str}"
            assert len(ji_shen) > 0, f"Empty ji_shen for {dt_str}"

            for elem in xi_yong:
                assert elem in self.VALID_CHINESE_ELEMENTS, (
                    f"Non-Chinese element '{elem}' found in xi_yong_shen for {dt_str}"
                )
                assert elem.lower() not in self.ENGLISH_ELEMENT_TOKENS

            for elem in ji_shen:
                assert elem in self.VALID_CHINESE_ELEMENTS, (
                    f"Non-Chinese element '{elem}' found in ji_shen for {dt_str}"
                )
                assert elem.lower() not in self.ENGLISH_ELEMENT_TOKENS

            for elem in lacking:
                assert elem in self.VALID_CHINESE_ELEMENTS, (
                    f"Non-Chinese element '{elem}' found in lacking_elements for {dt_str}"
                )
                assert elem.lower() not in self.ENGLISH_ELEMENT_TOKENS

    def test_50_datetimes_recommend_localization(self, client):
        """Assert all 50 datetimes recommend summary contains purely Chinese elements."""
        dates = self.get_50_diverse_datetimes()

        for dt_str in dates:
            resp = client.post(
                "/api/bazi/recommend",
                json={"birth_datetime": dt_str, "lottery_type": "ssq", "count": 1},
            )
            assert resp.status_code == 200, f"Recommend failed for {dt_str}: {resp.text}"
            data = resp.json()

            bazi_sum = data.get("bazi_summary", {})
            xi_yong = bazi_sum.get("xi_yong_shen", [])
            ji_shen = bazi_sum.get("ji_shen", [])
            lacking = bazi_sum.get("lacking_elements", [])

            for elem in xi_yong + ji_shen + lacking:
                assert elem in self.VALID_CHINESE_ELEMENTS, (
                    f"Non-Chinese element '{elem}' in recommend bazi_summary for {dt_str}"
                )
                assert elem.lower() not in self.ENGLISH_ELEMENT_TOKENS


# ==============================================================================
# Suite 3: Today's Fortune & LiuRi Resonance Stress Test
# ==============================================================================
class TestTodayFortuneAndLiuRiResonance:
    """Stress tests on /api/bazi/today-fortune across current and future dates."""

    HEAVENLY_STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"}
    EARTHLY_BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"}

    def test_today_fortune_get_endpoint(self, client):
        """GET /api/bazi/today-fortune validates Ganzhi Four Pillars and Huangli."""
        resp = client.get("/api/bazi/today-fortune")
        assert resp.status_code == 200
        data = resp.json()

        assert data["ganzhi_year"][0] in self.HEAVENLY_STEMS
        assert data["ganzhi_year"][1] in self.EARTHLY_BRANCHES
        assert data["ganzhi_month"][0] in self.HEAVENLY_STEMS
        assert data["ganzhi_month"][1] in self.EARTHLY_BRANCHES
        assert data["ganzhi_day"][0] in self.HEAVENLY_STEMS
        assert data["ganzhi_day"][1] in self.EARTHLY_BRANCHES
        assert data["ganzhi_hour"][0] in self.HEAVENLY_STEMS
        assert data["ganzhi_hour"][1] in self.EARTHLY_BRANCHES

        assert isinstance(data["day_nayin"], str) and len(data["day_nayin"]) >= 2
        assert isinstance(data["cai_shen_position"], str) and len(data["cai_shen_position"]) >= 1
        assert isinstance(data["xi_shen_position"], str) and len(data["xi_shen_position"]) >= 1
        assert isinstance(data["day_yi"], list) and len(data["day_yi"]) > 0
        assert isinstance(data["day_ji"], list) and len(data["day_ji"]) > 0

    @pytest.mark.parametrize(
        "birth_dt, user_name, shichen",
        [
            ("1984-02-04T12:00:00", "曾练", "午时"),
            ("1990-08-15T08:30:00", "欧阳修", "辰时"),
            ("2000-01-01T00:30:00", "诸葛亮", "早子时"),
            ("2026-11-03T10:00:00", "司马懿", "巳时"),
            ("2030-12-31T23:30:00", "东方朔", "晚子时"),
        ],
    )
    def test_today_fortune_post_resonance(self, client, birth_dt, user_name, shichen):
        """POST /api/bazi/today-fortune calculates valid natal chart LiuRi resonance."""
        payload = {
            "birth_datetime": birth_dt,
            "user_name": user_name,
            "shichen": shichen,
        }
        resp = client.post("/api/bazi/today-fortune", json=payload)
        assert resp.status_code == 200
        data = resp.json()

        resonance = data.get("resonance")
        assert resonance is not None, "Missing resonance object"
        assert resonance["resonance_multiplier"] >= 1.0, (
            f"Resonance multiplier {resonance['resonance_multiplier']} must be >= 1.0"
        )
        assert resonance["resonance_level"] in {"大吉", "平吉", "吉"}
        assert len(resonance["harmonious_elements"]) > 0
        for elem in resonance["harmonious_elements"]:
            assert elem in {"金", "木", "水", "火", "土"}


# ==============================================================================
# Suite 4: Name Numerology & 12 Shichen Stress Test
# ==============================================================================
class TestNameNumerologyAnd12Shichen:
    """Stress tests on Chinese Name Five Grids, compound surnames, and 12 Shichen."""

    @pytest.mark.parametrize(
        "name, expected_surname, expected_given",
        [
            ("曾练", "曾", "练"),
            ("张三", "张", "三"),
            ("王二小", "王", "二小"),
            ("欧阳修", "欧阳", "修"),
            ("诸葛孔明", "诸葛", "孔明"),
            ("司马懿", "司马", "懿"),
            ("上官婉儿", "上官", "婉儿"),
            ("皇甫端", "皇甫", "端"),
            ("犇羴鱻", "犇", "羴鱻"),
            ("仉督", "仉督", ""),
        ],
    )
    def test_name_splitting_and_five_grids(self, name, expected_surname, expected_given):
        """Verify surname/given name splitting and five grids calculation."""
        res = analyze_name_numerology(name)
        assert res["user_name"] == name
        assert res["surname"] == expected_surname
        assert res["given_name"] == expected_given

        # Five Grids assertions
        for grid_key in ["tian_ge", "ren_ge", "di_ge", "wai_ge", "zong_ge"]:
            grid = res[grid_key]
            assert grid["value"] > 0
            assert grid["element"] in {"金", "木", "水", "火", "土"}
            assert grid["fortune"] in {"大吉", "吉", "半吉", "凶", "平"}

        assert len(res["name_lucky_numbers"]) >= 2
        for num in res["name_lucky_numbers"]:
            assert 1 <= num <= 40

    def test_12_shichen_hour_pillars_and_early_late_zi_differentiation(self):
        """Verify that all 12 Shichen generate valid pillars, and 早子时 != 晚子时."""
        benchmark_date = "1990-08-15"
        shichen_list = [
            ("早子时", 0), ("丑时", 2), ("寅时", 4), ("卯时", 6),
            ("辰时", 8), ("巳时", 10), ("午时", 12), ("未时", 14),
            ("申时", 16), ("酉时", 18), ("戌时", 20), ("亥时", 22),
            ("晚子时", 23),
        ]

        hour_pillars = {}
        for sc_name, _ in shichen_list:
            res = calculate_four_pillars(benchmark_date, shichen=sc_name)
            hp = res["four_pillars"]["hour"]
            hour_pillars[sc_name] = f"{hp['stem']}{hp['branch']}"

        # Assert all branches correspond strictly
        assert hour_pillars["早子时"].endswith("子")
        assert hour_pillars["丑时"].endswith("丑")
        assert hour_pillars["寅时"].endswith("寅")
        assert hour_pillars["卯时"].endswith("卯")
        assert hour_pillars["辰时"].endswith("辰")
        assert hour_pillars["巳时"].endswith("巳")
        assert hour_pillars["午时"].endswith("午")
        assert hour_pillars["未时"].endswith("未")
        assert hour_pillars["申时"].endswith("申")
        assert hour_pillars["酉时"].endswith("酉")
        assert hour_pillars["戌时"].endswith("戌")
        assert hour_pillars["亥时"].endswith("亥")
        assert hour_pillars["晚子时"].endswith("子")

        # Early Zi vs Late Zi strict distinction under Sect 2
        early_zi = hour_pillars["早子时"]
        late_zi = hour_pillars["晚子时"]
        assert early_zi != late_zi, (
            f"Early Zi ({early_zi}) and Late Zi ({late_zi}) must have different stems!"
        )
        assert early_zi == "庚子", f"Expected 早子时 to be 庚子, got {early_zi}"
        assert late_zi == "壬子", f"Expected 晚子时 to be 壬子, got {late_zi}"


# ==============================================================================
# Suite 5: Stats Schema Compatibility Stress Test
# ==============================================================================
class TestStatsSchemaCompatibility:
    """Stress tests on /api/lottery/stats/{type} across ssq, dlt, fc3d, qxc."""

    @pytest.mark.parametrize("lottery_type", ["ssq", "dlt", "fc3d", "qxc"])
    def test_stats_schema_compat(self, client, lottery_type):
        """Assert total_draws >= 100, last_issue, and stats dictionary for all lotteries."""
        resp = client.get(f"/api/lottery/stats/{lottery_type}")
        assert resp.status_code == 200, f"Stats failed for {lottery_type}: {resp.text}"
        data = resp.json()

        assert "total_draws" in data
        assert data["total_draws"] >= 100
        assert "draws_analyzed" in data
        assert data["total_draws"] == data["draws_analyzed"]

        assert "last_issue" in data
        assert len(data["last_issue"]) > 0
        assert "latest_issue" in data
        assert data["last_issue"] == data["latest_issue"]

        assert "stats" in data
        stats = data["stats"]
        assert "primary" in stats

        # Validate primary numbers stat items
        primary_stats = stats["primary"]
        assert len(primary_stats) > 0
        for num_key, stat_obj in primary_stats.items():
            for field in [
                "frequency", "rate", "is_hot", "is_cold",
                "current_omission", "max_omission", "avg_omission",
            ]:
                assert field in stat_obj, f"Missing {field} in {lottery_type} primary stats for {num_key}"
            assert isinstance(stat_obj["frequency"], int)
            assert isinstance(stat_obj["rate"], float)
            assert isinstance(stat_obj["is_hot"], bool)
            assert isinstance(stat_obj["is_cold"], bool)

        # For dual-zone lotteries, assert secondary zone presence
        rule = get_lottery_rule(lottery_type)
        if len(rule.zones) > 1:
            assert "secondary" in stats, f"Expected 'secondary' stats for dual-zone {lottery_type}"
            secondary_stats = stats["secondary"]
            assert len(secondary_stats) > 0
            for num_key, stat_obj in secondary_stats.items():
                for field in [
                    "frequency", "rate", "is_hot", "is_cold",
                    "current_omission", "max_omission", "avg_omission",
                ]:
                    assert field in stat_obj, f"Missing {field} in {lottery_type} secondary stats for {num_key}"


# ==============================================================================
# Suite 6: Edge Cases, Extremes & Boundary Stress Test
# ==============================================================================
class TestEdgeCasesAndBoundaries:
    """Stress tests on boundary datetimes, extreme counts, and special name inputs."""

    @pytest.mark.parametrize(
        "special_name",
        ["  曾 练  ", "曾练123", "VIP曾练", "A曾练B", "   "],
    )
    def test_special_name_inputs(self, special_name):
        """Special/unusual names should not crash and should return safe structure."""
        res = analyze_name_numerology(special_name)
        if not special_name.strip():
            assert res == {}
        else:
            assert "user_name" in res
            assert res["tian_ge"]["value"] > 0
            assert res["zong_ge"]["value"] > 0

    @pytest.mark.parametrize(
        "dt_extreme",
        ["1901-01-01T00:00:00", "2099-12-31T23:59:59"],
    )
    def test_extreme_datetimes(self, dt_extreme):
        """Extreme historical or future dates calculate properly without crashing."""
        bazi = calculate_four_pillars(dt_extreme)
        assert bazi["day_master"] in {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"}
        assert len(bazi["xi_yong_shen"]) > 0

    def test_large_count_recommendations(self):
        """Requesting 50 bets produces 50 strictly unique valid tickets."""
        bazi = calculate_four_pillars("1990-08-15 08:30:00")
        res = recommend_lottery_tickets(bazi, "ssq", count=50)
        recs = res["recommendations"]
        assert len(recs) == 50
        tickets = [tuple(x["primary_numbers"] + x["secondary_numbers"]) for x in recs]
        assert len(set(tickets)) == 50

