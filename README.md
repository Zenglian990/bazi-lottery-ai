# 曾练彩票助手 (Bazi Lottery AI)

[![Version](https://img.shields.io/badge/version-v1.4.0-FFB300.svg)]()
[![Backend](https://img.shields.io/badge/FastAPI-0.115+-009688.svg)]()
[![Android](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Compose%20M3-3DDC84.svg)]()
[![Python](https://img.shields.io/badge/Python-3.12%20%2F%203.14-3776AB.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> 融合**生辰八字天干地支天文排盘**、**河图五行数理**、**历史开奖大数据统计与对数空间 A-Res 加权抽样**，并集成**云端安全中继 / DeepSeek (V3 & R1) / Google Gemini (2.5 Flash & Pro) / OpenAI 兼容端点**流式 AI 解盘的原生 Android + FastAPI 全栈彩票助手。

---

## 核心特性 (v1.4.0)

1. **天文级四柱八字与五行能量模型**
   - 双端同源 6tail 农历天文历法引擎（后端 `lunar-python` + 端侧 `cn.6tail:lunar`），支持 **1900 ~ 2100** 农历/公历双向实时互转、精准闰月校验、早/晚子时切分与**出生地经度真太阳时 + 天文均时差**校正。
   - 110 分制支藏干权重模型（月令 35 分），输出细化 5 级旺衰（极旺/偏强/中和/偏弱/极弱）、动态喜用神排序、**8 步大运时间轴**与**姓名五格剖象数理**（650+ 简体笔画字典）。
2. **四大彩种大数据统计与精准选号**
   - 完整支持 **双色球 (SSQ)**、**超级大乐透 (DLT)**、**福彩 3D (FC3D)**、**七星彩 (QXC)**。
   - 对数空间 A-Res 加权无放回抽样 + 4 大选号策略（命理吉数优先 / 稳健大数据 / 冷热追号 / 极冷号反弹）+ 可视化 **定胆/杀号** 与 **自定义三元权重滑块**。
   - 端侧 `LazyColumn` 历史开奖明细 + Compose Canvas 频次分布柱状图 + 全号盘遗漏热力矩阵 + 联网开奖磁盘缓存（断网保留最新期号）。
3. **收藏夹一键自动对奖复盘**
   - 内置四大官方彩种精准规则对奖引擎（`PrizeChecker`），自动比对最新开奖高亮命中球并统计奖级与参考奖金。
4. **双模 AI 易道解盘（含完整 64 卦 RAG 典籍）**
   - 后端安全中继采用 `X-App-Timestamp` + `X-App-Nonce` + `X-App-Signature` 防重放 HMAC-SHA256 鉴权，结合 91 条《周易》64 卦 /《穷通宝鉴》/ 奇门九星典籍语料流式输出五段结构卦辞。
   - 支持直连 DeepSeek (`deepseek-chat` / `deepseek-reasoner` 折叠思考链)、Gemini 2.5 (`maxOutputTokens=4096`) 与 HTTPS OpenAI 兼容端点，端侧 API Key 由 `AndroidKeyStore` AES-256-GCM 硬件级加密保管。
5. **Cloud Run 零成本高可用持久化**
   - 单实例锁定（`maxScale: 1`）+ GCS SQLite 一致性快照备份/恢复（`backend/app/db/gcs_sync.py`）+ Cloud Scheduler 每日定时同步官方开奖。

---

## 工程目录结构

```text
bazi-lottery-ai/
├── Dockerfile                        # 统一生产容器镜像 (Python 3.12-slim + non-root)
├── docker-compose.yml                # 本地一键容器编排
├── cloudrun.yaml                     # Google Cloud Run 单实例持久化部署模板
├── .env.example                      # 环境变量配置示例
├── LICENSE                           # MIT 开源许可
├── .github/workflows/
│   ├── ci.yml                        # 全栈 CI 门禁 (后端单测 + 5-Tier E2E + Android 单测)
│   └── build-apk.yml                 # 签名 + R8 混淆 Release APK 自动构建与发布
├── backend/                          # FastAPI 云端后端服务
│   ├── requirements.txt
│   ├── scripts/generate_classics_corpus.py
│   ├── app/
│   │   ├── main.py                   # 应用入口、结构化 JSON 日志、GCS 状态恢复与优雅停机
│   │   ├── core/                     # config.py / security.py (HMAC-Nonce + 最小权限令牌)
│   │   ├── models/                   # bazi.py / lottery.py / ai.py
│   │   ├── services/                 # bazi / wuxing / numerology / name / stats / rag / ai
│   │   ├── api/                      # routes_bazi.py / routes_lottery.py / routes_ai.py
│   │   └── db/                       # database.py / sync_service.py / gcs_sync.py / seeds/
│   └── tests/                        # 349 项后端单元与对抗测试
├── android/                          # 原生 Android Kotlin + Jetpack Compose M3 客户端
│   └── app/src/
│       ├── main/java/com/zeng/lottery/
│       │   ├── ai/                   # BackendAiClient (HMAC-Nonce), OpenAiClient, GeminiClient, KeyStoreManager
│       │   ├── data/                 # api/, local/ (BaziEngine, OfflineLotteryEngine, PrizeChecker), model/, repository/
│       │   └── ui/                   # components/, screens/, theme/ (暗金 & 宣纸浅色), viewmodel/
│       └── test/                     # Android JVM 单元测试套件
└── tests_e2e/                        # 5-Tier 独立全链路 E2E 验证框架 (真实签名零 Mock)
```

---

## 快速启动

### 1. 本地后端运行

```bash
cp .env.example .env
python -m venv .venv
# Windows: .venv\Scripts\Activate.ps1 | Linux/macOS: source .venv/bin/activate
pip install -r backend/requirements.txt
uvicorn backend.app.main:app --host 127.0.0.1 --port 8000 --reload
```

### 2. Docker Compose 部署

```bash
cp .env.example .env   # 填入 ADMIN_TOKEN / APP_HMAC_SECRET / GEMINI_API_KEY
docker compose up -d --build
```

### 3. 全栈自动化测试

```bash
# 后端单元测试 (349 passed)
python -m pytest backend/tests -q

# 5-Tier 独立 E2E 质量门禁 (153 pytest / 132 gate passed)
python -m pytest tests_e2e -q
python tests_e2e/run_all_e2e.py

# Android JVM 单元测试
cd android && ./gradlew testDebugUnitTest
```

---

## 理性购彩声明

> 彩票属于国家公益事业与纯粹的概率游戏，开奖结果完全随机。本系统基于传统文化民俗研究与数理统计模型，旨在提供文化趣味、走势可视化与复盘工具，**不构成任何中奖承诺**。请理性娱乐，量力而行，未成年人禁止购彩。
