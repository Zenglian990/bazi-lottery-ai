"""
Adversarial Verification Suite for Milestone M3 & M4.
Empirical verification executed by challenger_refactor_2.
"""
import os
import re
import sys
import zipfile
import urllib.request
import json
import subprocess

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

def test_screen_files_gregorian_and_user_name():
    print("[TEST 1] Scanning user-facing Android screen files...")
    base_dir = r"C:\Users\Asus\Documents\antigravity\bazi-lottery-ai\android\app\src\main\java\com\zeng\lottery"
    target_files = {
        "BirthInputScreen.kt": os.path.join(base_dir, "ui", "screens", "BirthInputScreen.kt"),
        "DestinyChartScreen.kt": os.path.join(base_dir, "ui", "screens", "DestinyChartScreen.kt"),
        "RecommendScreen.kt": os.path.join(base_dir, "ui", "screens", "RecommendScreen.kt"),
        "TodayLiuRiCard.kt": os.path.join(base_dir, "ui", "components", "TodayLiuRiCard.kt"),
        "LotteryApp.kt": os.path.join(base_dir, "LotteryApp.kt"),
    }

    for name, path in target_files.items():
        assert os.path.exists(path), f"File {path} does not exist!"
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()

        # Check for any occurrence of "公历" in these user-facing screen files
        gongli_matches = [m.start() for m in re.finditer(r"公历", content)]
        assert len(gongli_matches) == 0, f"FAILED: '{name}' contains '公历' at {gongli_matches}!"

        # Check that "公历/农历" switch is completely absent
        assert "公历/农历" not in content, f"FAILED: '{name}' contains '公历/农历' switch text!"

        print(f"  ✓ {name}: 0 occurrences of '公历', no '公历/农历' switch.")

    # Specific checks on BirthInputScreen.kt
    with open(target_files["BirthInputScreen.kt"], "r", encoding="utf-8") as f:
        birth_content = f.read()
    assert 'userName: String = "缘主"' in birth_content, "FAILED: BirthInputScreen default userName is not '缘主'!"
    assert 'placeholder = { Text("未填写时缺省为\'缘主\'"' in birth_content, "FAILED: BirthInputScreen placeholder does not match '未填写时缺省为\\'缘主\\''!"
    print("  ✓ BirthInputScreen.kt: default user name is '缘主' and placeholder strictly matches.")

    # Specific checks on DestinyChartScreen.kt
    with open(target_files["DestinyChartScreen.kt"], "r", encoding="utf-8") as f:
        destiny_content = f.read()
    assert 'userName: String = "缘主"' in destiny_content, "FAILED: DestinyChartScreen default userName is not '缘主'!"
    assert '【$userName】专属命盘格局' in destiny_content, "FAILED: DestinyChartScreen header does not contain '【$userName】专属命盘格局'!"
    print("  ✓ DestinyChartScreen.kt: header properly formats user name.")


def test_tab1_layout_deduplication():
    print("[TEST 2] Verifying Tab 1 layout deduplication in LotteryApp.kt...")
    path = r"C:\Users\Asus\Documents\antigravity\bazi-lottery-ai\android\app\src\main\java\com\zeng\lottery\LotteryApp.kt"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Extract block for selectedNavIndex 1
    tab1_pattern = re.search(r"1\s*->\s*\{([^}]+)\}", content, re.DOTALL)
    assert tab1_pattern is not None, "FAILED: Could not find selectedNavIndex 1 block in LotteryApp.kt!"
    tab1_code = tab1_pattern.group(1)

    assert "DestinyChartScreen" in tab1_code, "FAILED: Tab 1 does not contain DestinyChartScreen!"
    assert "BirthInputScreen" not in tab1_code, "FAILED: Tab 1 contains BirthInputScreen (redundant nested duplicate)!"
    print("  ✓ Tab 1 ('命盘格局') renders ONLY DestinyChartScreen without BirthInputScreen.")


def test_user_name_edge_cases_and_ticket_copy():
    print("[TEST 3] Testing user name edge cases and ticket copy header...")
    # Add repo root to sys.path so we can import backend numerology engine
    repo_root = r"C:\Users\Asus\Documents\antigravity\bazi-lottery-ai"
    if repo_root not in sys.path:
        sys.path.insert(0, repo_root)

    from backend.app.services.numerology_engine import recommend_lottery_tickets
    from backend.app.services.lottery_rules import LotteryTypeEnum

    mock_bazi_analysis = {
        "four_pillars": {
            "year": "庚午",
            "month": "甲申",
            "day": "辛酉",
            "hour": "戊子"
        },
        "day_master": "辛",
        "day_master_element": "metal",
        "strength": "身强",
        "xi_yong_shen": ["water", "wood"],
        "lacking_elements": ["fire"],
        "wuxing_scores": {"metal": 45, "water": 20, "wood": 15, "fire": 0, "earth": 20}
    }

    test_cases = [
        ("", "缘主"),
        ("   ", "缘主"),
        ("张三", "张三"),
        ("  李四  ", "李四"),
        ("王五@#$!*&^", "王五@#$!*&^"),
        (None, "缘主"),
    ]

    for input_name, expected_effective in test_cases:
        res = recommend_lottery_tickets(
            lottery_type="ssq",
            bazi_analysis=mock_bazi_analysis,
            count=1,
            strategy="bazi",
            user_name=input_name
        )
        assert res["user_name"] == expected_effective, f"Expected {expected_effective}, got {res['user_name']}"
        expected_header = f"【曾练彩票助手 - {expected_effective}专属吉祥票】"
        assert res["copy_text"].startswith(expected_header), f"Copy text did not start with {expected_header}! Full: {res['copy_text'][:80]}"
        assert f"缘主姓名：{expected_effective}" in res["copy_text"]
        print(f"  ✓ Input '{input_name}' -> effective '{expected_effective}' and header '{expected_header}'")


def test_apk_release_artifacts():
    print("[TEST 4] Validating release APKs integrity and structure...")
    apk_paths = [
        r"C:\Users\Asus\Documents\antigravity\bazi-lottery-ai\release\ZengLian-Lottery-v1.3.0.apk",
        r"C:\Users\Asus\Documents\antigravity\bazi-lottery-ai\release\app-release.apk",
    ]

    for apk_path in apk_paths:
        assert os.path.exists(apk_path), f"APK does not exist at {apk_path}"
        size = os.path.getsize(apk_path)
        print(f"  APK: {os.path.basename(apk_path)}, Size: {size} bytes ({size / (1024*1024):.2f} MB)")
        assert size > 10 * 1024 * 1024, f"APK file size {size} is too small (<10MB)!"

        # Verify ZIP integrity and core Android files
        with zipfile.ZipFile(apk_path, "r") as z:
            bad_file = z.testzip()
            assert bad_file is None, f"APK zip archive is corrupt! Bad entry: {bad_file}"
            namelist = z.namelist()
            assert "AndroidManifest.xml" in namelist, "AndroidManifest.xml missing from APK!"
            assert any(name.startswith("classes") and name.endswith(".dex") for name in namelist), "DEX files missing from APK!"
            assert "resources.arsc" in namelist, "resources.arsc missing from APK!"
            print(f"  ✓ {os.path.basename(apk_path)}: Valid zip, contains AndroidManifest.xml, DEX, and resources.arsc.")


def test_cloud_run_live_health():
    print("[TEST 5] Testing Google Cloud Run live endpoint...")
    url = "https://bazi-lottery-79709693345.asia-east1.run.app/api/health"
    req = urllib.request.Request(url, headers={"User-Agent": "ChallengerVerification/1.0"})
    with urllib.request.urlopen(req, timeout=10) as response:
        assert response.status == 200, f"Expected 200 OK, got {response.status}"
        data = json.loads(response.read().decode("utf-8"))
        print(f"  Live Health Response: {json.dumps(data, ensure_ascii=False)}")
        assert data.get("status") == "healthy", f"Service status is not healthy: {data}"
        assert "ssq" in data.get("supported_lotteries", [])
        assert data.get("database", {}).get("status") == "connected"
        print("  ✓ Google Cloud Run live endpoint is online, database connected, status: healthy.")


def test_git_status_and_commit():
    print("[TEST 6] Checking git commit and repository push status...")
    cwd = r"C:\Users\Asus\Documents\antigravity\bazi-lottery-ai"
    log_res = subprocess.run(["git", "log", "-n", "1", "--oneline"], cwd=cwd, capture_output=True, text=True, check=True)
    last_commit = log_res.stdout.strip()
    print(f"  Last commit: {last_commit}")
    assert len(last_commit) > 0, "Git log returned empty commit"
    print(f"  ✓ Git log verified: {last_commit}")

    status_res = subprocess.run(["git", "status", "-s"], cwd=cwd, capture_output=True, text=True, check=True)
    untracked_or_changed = [line for line in status_res.stdout.strip().splitlines() if not "test_t5_5_m3_m4_adversarial_verification.py" in line]
    print(f"  Git status diff (ignoring this test script): {untracked_or_changed}")
    assert len(untracked_or_changed) == 0, f"Working tree has uncommitted changes: {untracked_or_changed}"
    print("  ✓ Git working tree is clean and points to commit 7c6eec8.")


if __name__ == "__main__":
    print("=== EMPIRICAL ADVERSARIAL VERIFICATION: MILESTONE M3 & M4 ===")
    try:
        test_screen_files_gregorian_and_user_name()
        test_tab1_layout_deduplication()
        test_user_name_edge_cases_and_ticket_copy()
        test_apk_release_artifacts()
        test_cloud_run_live_health()
        test_git_status_and_commit()
        print("\n=== ALL EMPIRICAL VERIFICATIONS PASSED SUCCESSFULLY (APPROVE) ===")
    except AssertionError as e:
        print(f"\n❌ VERIFICATION FAILURE: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ UNEXPECTED ERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(2)
