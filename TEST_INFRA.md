# Test Infrastructure Specification (TEST_INFRA.md)
# Bazi Lottery Recommender (生辰曾练彩票助手系统)

## 1. Test Philosophy & Architecture Principles

### 1.1 Opaque-Box & Requirement-Driven
The End-to-End (E2E) testing track adheres strictly to an **opaque-box, requirement-driven methodology**:
- **Zero Internal Dependency**: Test suites do not import private helper functions, database internals, or undocumented classes from implementation modules. All testing is conducted strictly against public RESTful API contracts, official lottery regulations, and documented astronomical/astrological specifications.
- **Authoritative Expected Output Derivation**: Every test case derives its ground truth directly from authoritative sources:
  1. Official lottery issuing bodies (China Welfare Lottery Center, China Sports Lottery Administration).
  2. Verified astronomical calendar algorithms (`lunar-python` astronomical ephemeris for Solar-Lunar conversion and 24 solar terms).
  3. Closed-form mathematical formulas for 110-point Wuxing energy models, Hetu numerology mappings, and Efraimidis-Spirakis (A-Res) weighted reservoir sampling.
  4. Pydantic v2 schemas and OpenAPI specifications defined in `PROJECT.md § Interface Contracts`.
- **Progressive Testability & Decoupling**: Tests are partitioned by feature tiers so that each milestone's deliverables can be validated independently without blocking on future features.
- **Dual Execution Modes**: The test harness operates both via standard `pytest` and via a standalone high-performance runner (`tests_e2e/run_all_e2e.py`), supporting live HTTP execution against a running server (`http://127.0.0.1:8000`) or in-process ASGI execution (`httpx.Client(app=app)` / `TestClient`).

---

## 2. Feature Inventory Mapping (F1 - F16)

The E2E test suite comprehensively covers features F1 through F16 as defined in `PROJECT.md`:

| Feature ID | Feature Name | Primary Tier | Target Test Module | Coverage Threshold |
| :--- | :--- | :--- | :--- | :--- |
| **F1** | 彩种规则规约与校验引擎 | Tier 1, Tier 2 | `tier1_rules/test_f1_lottery_rules.py`<br>`tier2_boundaries/test_lottery_input_adversarial.py` | $\ge 5$ tests per lottery type (SSQ, DLT, FC3D, QXC) |
| **F2** | 历史开奖大数据存储与同步 | Tier 1 | `tier1_rules/test_f2_historical_data.py` | $\ge 5$ test cases (Seeds, Upsert, Query, Pagination, DDL) |
| **F3** | 开奖频率与冷热走势统计分析 | Tier 1, Tier 3 | `tier1_rules/test_f3_frequencies_hotcold.py`<br>`tier3_interactions/test_bazi_lottery_cross.py` | $\ge 5$ test cases (Windowing, $1.25E_W$ hot, $0.75E_W$ cold) |
| **F4** | 多维遗漏值分析算法 | Tier 1 | `tier1_rules/test_f4_omission_analytics.py` | $\ge 5$ test cases ($g_0$, $\max(g_i)$, $g_{avg}=\frac{N-k}{k+1}$, desire index) |
| **F5** | 干支历与四柱八字精准推算 | Tier 1, Tier 2 | `tier1_rules/test_f5_bazi_ganzhi.py`<br>`tier2_boundaries/test_solar_terms_boundary.py`<br>`tier2_boundaries/test_midnight_zi_hour.py` | $\ge 5$ test cases (LiChun, 12 Jie terms, JDN mod 60, Five Rats) |
| **F6** | 支藏干能量加权与五行量化打分 | Tier 1, Tier 2 | `tier1_rules/test_f6_wuxing_energy.py`<br>`tier2_boundaries/test_extreme_wuxing_charts.py` | $\ge 5$ test cases (110-point model, hidden stems, Day Master, Xi Yong Shen) |
| **F7** | 河图五行数理与吉祥数字池映射 | Tier 1, Tier 3 | `tier1_rules/test_f7_hetu_numerology.py`<br>`tier3_interactions/test_bazi_lottery_cross.py` | $\ge 5$ test cases (Hetu tail mod 10, 5-tier pools for all lotteries) |
| **F8** | 三元混合加权与A-Res安全抽样 | Tier 1, Tier 2 | `tier1_rules/test_f8_hybrid_sampling.py`<br>`tier2_boundaries/test_extreme_weights_sampling.py` | $\ge 5$ test cases (Weight fusion, A-Res reservoir, uniqueness, bounds) |
| **F9** | 原生 Android 工程脚手架与主题 | Tier 1 | `tier1_rules/test_api_schemas_contracts.py` | $\ge 5$ test cases (Client-Server contract compliance) |
| **F10** | 生辰输入与彩种选择面板 | Tier 1, Tier 4 | `tier1_rules/test_api_schemas_contracts.py`<br>`tier4_realworld/test_scenario_1988_dragon_ssq.py` | $\ge 5$ test cases (Input parsing, calendar types) |
| **F11** | 五行命理 Canvas 雷达图谱组件 | Tier 1, Tier 4 | `tier1_rules/test_api_schemas_contracts.py`<br>`tier4_realworld/test_scenario_1988_dragon_ssq.py` | $\ge 5$ test cases (Score percentage normalizations $\sum P = 1.0$) |
| **F12** | 历史冷热走势与遗漏大屏 | Tier 1, Tier 4 | `tier1_rules/test_f3_frequencies_hotcold.py`<br>`tier4_realworld/test_scenario_1995_pig_dlt.py` | $\ge 5$ test cases (Trend ordering, ranking bounds) |
| **F13** | 3D拟真彩球动效展示盘 | Tier 1, Tier 4 | `tier1_rules/test_f1_lottery_rules.py`<br>`tier4_realworld/test_scenario_1988_dragon_ssq.py` | $\ge 5$ test cases (Ticket format string, zone partition) |
| **F14** | 客户端动态网络与三级离线回退 | Tier 1, Tier 4 | `tier1_rules/test_api_schemas_contracts.py`<br>`tier4_realworld/test_scenario_multi_portfolio.py` | $\ge 5$ test cases (Health check, offline seed fallback parity) |
| **F15** | 双模 AI 大模型 API 集成 | Tier 1, Tier 4 | `tier1_rules/test_api_schemas_contracts.py`<br>`tier4_realworld/test_scenario_qxc_ai_divination.py` | $\ge 5$ test cases (Relay payload schema, streaming SSE response) |
| **F16** | 易道彩经大师提示词工程与流式解盘 | Tier 3, Tier 4 | `tier3_interactions/test_ai_prompt_parameter_binding.py`<br>`tier4_realworld/test_scenario_qxc_ai_divination.py` | $\ge 5$ test cases (5-part template, disclaimer enforcement) |

---

## 3. Directory Layout & Architecture

The E2E test suite resides exclusively in `tests_e2e/`:

```
tests_e2e/
├── __init__.py
├── run_all_e2e.py                           # Standalone unified test runner with CLI reporting
├── harness/                                 # Opaque-box execution harness & assertions
│   ├── __init__.py
│   ├── client.py                            # Opaque API client (HTTP + ASGI TestClient auto-negotiation)
│   ├── oracle.py                            # Independent reference oracle for rules, Bazi & statistics
│   └── runner.py                            # Test discovery, execution engine, and reporting framework
├── tier1_rules/                             # Tier 1: Core Functional & Rule Coverage (>=5 tests/feature)
│   ├── __init__.py
│   ├── test_f1_lottery_rules.py             # SSQ, DLT, FC3D, QXC official rule constraints
│   ├── test_f2_historical_data.py           # SQLite seed ingestion, pagination, schema stability
│   ├── test_f3_frequencies_hotcold.py       # Sliding window statistics, hot/cold threshold factors
│   ├── test_f4_omission_analytics.py        # Current, max, average omission & desire index
│   ├── test_f5_bazi_ganzhi.py               # Solar-lunar, LiChun, 12 Jie, JDN mod 60, Five Rats
│   ├── test_f6_wuxing_energy.py             # 110-point positional weights, hidden stems, Day Master
│   ├── test_f7_hetu_numerology.py           # Hetu tail mod 10 mapping & 5-tier lucky pools
│   ├── test_f8_hybrid_sampling.py           # Tri-factor linear weights & A-Res reservoir sampling
│   └── test_api_schemas_contracts.py        # OpenAPI schemas, /api/health, /api/lottery, /api/bazi, /api/ai
├── tier2_boundaries/                        # Tier 2: Boundary & Corner Conditions (>=5 tests/domain)
│   ├── __init__.py
│   ├── test_solar_terms_boundary.py         # Sub-minute transitions at Li Chun and 12 Jie terms
│   ├── test_midnight_zi_hour.py             # 23:00 / 00:00 Zi hour boundary & Sect 1 vs Sect 2
│   ├── test_extreme_wuxing_charts.py        # Pure single-element charts, 0-score elements, balanced charts
│   ├── test_extreme_weights_sampling.py     # Extreme weights (w_bazi=1, w_freq=1, w_omission=1, all 0)
│   └── test_lottery_input_adversarial.py    # Out-of-bounds balls, duplicates, type mismatches, SQL/XSS injections
├── tier3_interactions/                      # Tier 3: Pairwise Cross-Feature Interactions
│   ├── __init__.py
│   ├── test_bazi_lottery_cross.py           # Bazi Five Elements × Lottery Big Data Frequency synergy
│   ├── test_qxc_special_rules_interaction.py# Bazi Lucky Numbers × QXC 7th ball (0-14) & duplicate front digits
│   └── test_ai_prompt_parameter_binding.py  # Bazi destiny data × Recommended numbers × AI prompt bindings
└── tier4_realworld/                         # Tier 4: Real-World End-to-End User Scenarios
    ├── __init__.py
    ├── test_scenario_1988_dragon_ssq.py     # Scenario 1: 1988 Earth Dragon SSQ Balanced Pick Lifecycle
    ├── test_scenario_1995_pig_dlt.py        # Scenario 2: 1995 Wood Pig DLT Cold Reversal Strategy
    ├── test_scenario_fc3d_pure_bazi.py      # Scenario 3: FC3D Pure Bazi Numerology Pick Lifecycle
    ├── test_scenario_qxc_ai_divination.py   # Scenario 4: QXC Complete AI Divination Streaming Workflow
    └── test_scenario_multi_portfolio.py     # Scenario 5: Multi-lottery Portfolio with Responsible Gaming Validation
```

---

## 4. Test Execution Commands

### 4.1 Standalone Python Runner (Recommended)
```bash
# Run all E2E test suites (Tiers 1-4)
python tests_e2e/run_all_e2e.py

# Run specific tier
python tests_e2e/run_all_e2e.py --tier 1
python tests_e2e/run_all_e2e.py --tier 2
python tests_e2e/run_all_e2e.py --tier 3
python tests_e2e/run_all_e2e.py --tier 4

# Run with verbose output and JSON report generation
python tests_e2e/run_all_e2e.py --verbose --report report_e2e.json
```

### 4.2 Standard Pytest Execution
```bash
# Run all tests using pytest
pytest tests_e2e/ -v

# Run individual tier test file
pytest tests_e2e/tier1_rules/test_f1_lottery_rules.py -v
pytest tests_e2e/tier2_boundaries/test_solar_terms_boundary.py -v
pytest tests_e2e/tier3_interactions/test_bazi_lottery_cross.py -v
pytest tests_e2e/tier4_realworld/test_scenario_1988_dragon_ssq.py -v
```

### 4.3 Target Environment Configuration
By default, the harness connects to `http://127.0.0.1:8000`. If the live server is not running, it automatically uses the in-process FastAPI application via `httpx.Client(app=app)`.
To target a custom remote deployment (e.g. Cloud Run):
```bash
$env:API_BASE_URL="https://bazi-lottery-backend.run.app"
python tests_e2e/run_all_e2e.py
```

---

## 5. Coverage Thresholds & Quality Gates

| Gate Metric | Threshold Requirement | Enforcement Mechanism |
| :--- | :--- | :--- |
| **Tier 1 Coverage** | $\ge 5$ discrete tests per feature (F1-F8, API schemas) | Automated test count validation in `runner.py` |
| **Tier 2 Coverage** | $\ge 5$ discrete tests per boundary/corner domain | Automated test count validation in `runner.py` |
| **Tier 3 Coverage** | Full pairwise interaction matrix coverage | Cross-domain assertion checks |
| **Tier 4 Coverage** | $\ge 5$ complete real-world user lifecycle journeys | Scenario step-by-step verification |
| **Test Pass Rate** | 100% Pass (Zero unhandled failures or regressions) | CI exit code 0 |
| **Execution Speed** | Full suite execution under 30 seconds | In-memory SQLite & cached ephemeris |
