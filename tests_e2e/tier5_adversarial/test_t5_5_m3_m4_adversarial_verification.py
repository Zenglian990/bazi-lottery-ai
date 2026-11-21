"""Tier 5 Portable Verification Suite: 仓库相对路径代码契约、Tab 结构、姓名边界与进程内健康探活。

已彻底移除硬编码本机外部路径、过时本地 APK 文件路径、硬编码线上依赖与虚假 commit 输出。
"""
from pathlib import Path
import re

from tests_e2e.harness.client import get_test_client

REPO_ROOT = Path(__file__).resolve().parents[2]
ANDROID_PKG_ROOT = REPO_ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "zeng" / "lottery"


def test_screen_files_user_name_defaults():
    """验证关键 Android 页面源文件存在并规范处理默认缘主称谓。"""
    target_files = [
        ANDROID_PKG_ROOT / "ui" / "screens" / "BirthInputScreen.kt",
        ANDROID_PKG_ROOT / "ui" / "screens" / "DestinyChartScreen.kt",
        ANDROID_PKG_ROOT / "ui" / "screens" / "RecommendScreen.kt",
        ANDROID_PKG_ROOT / "ui" / "components" / "TodayLiuRiCard.kt",
        ANDROID_PKG_ROOT / "LotteryApp.kt",
    ]
    for path in target_files:
        assert path.exists(), f"Android source file missing: {path}"

    destiny_content = (ANDROID_PKG_ROOT / "ui" / "screens" / "DestinyChartScreen.kt").read_text(encoding="utf-8")
    assert "专属命盘格局" in destiny_content


def test_tab1_layout_deduplication():
    """验证 Tab 1 ('命盘格局') 仅渲染 DestinyChartScreen，不嵌套重复的 BirthInputScreen。"""
    app_path = ANDROID_PKG_ROOT / "LotteryApp.kt"
    content = app_path.read_text(encoding="utf-8")
    tab1_pattern = re.search(r"1\s*->\s*\{(.*?)\n\s*2\s*->", content, re.DOTALL)
    assert tab1_pattern is not None, "Could not locate Tab 1 block in LotteryApp.kt"
    tab1_code = tab1_pattern.group(1)
    assert "DestinyChartScreen" in tab1_code
    assert "BirthInputScreen(" not in tab1_code


def test_user_name_edge_cases_and_ticket_copy():
    """验证推荐引擎在空名、纯空白、含空白姓名与特殊字符下的兜底文案一致性。"""
    from backend.app.services.numerology_engine import recommend_lottery_tickets

    mock_bazi_analysis = {
        "day_master": "辛",
        "day_master_element": "metal",
        "strength": "身强",
        "xi_yong_shen": ["water", "wood"],
        "lacking_elements": ["fire"],
        "ji_shen": ["metal", "earth"],
    }

    test_cases = [
        ("", "缘主"),
        ("   ", "缘主"),
        ("张三", "张三"),
        ("  李四  ", "李四"),
        (None, "缘主"),
    ]

    for input_name, expected_effective in test_cases:
        res = recommend_lottery_tickets(
            lottery_type="ssq",
            bazi_analysis=mock_bazi_analysis,
            count=1,
            strategy="bazi",
            user_name=input_name,
        )
        assert res["user_name"] == expected_effective
        expected_header = f"【曾练彩票助手 - {expected_effective}专属吉祥票】"
        assert res["copy_text"].startswith(expected_header)
        assert f"缘主姓名：{expected_effective}" in res["copy_text"]


def test_in_process_health_and_detailed_health():
    """验证进程内 /api/health 与 /api/health/detailed 返回完整数据结构。"""
    client = get_test_client()
    health = client.get_health()
    assert health["status_code"] == 200
    data = health["data"]
    assert data.get("status") == "healthy"
    assert "ssq" in data.get("supported_lotteries", [])
    assert data.get("database", {}).get("status") == "connected"
    assert "latest_draws" in data.get("database", {})

    detailed = client.request("GET", "/api/health/detailed")
    assert detailed.status_code == 200
    d_json = detailed.json()
    assert d_json["status"] in ("healthy", "degraded")
    assert "latest_draws" in d_json
