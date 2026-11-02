# 曾练彩票助手 (ZengLian Lottery Assistant)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Backend](https://img.shields.io/badge/FastAPI-0.115+-009688.svg)]()
[![Android](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Compose%20M3-3DDC84.svg)]()
[![Python](https://img.shields.io/badge/Python-3.12%20%2F%203.14-3776AB.svg)]()
[![E2E Coverage](https://img.shields.io/badge/E2E%20Tests-127%2F127%20Passed%20(100%25)-success.svg)]()
[![Unit Tests](https://img.shields.io/badge/Backend%20Pytest-144%2F144%20Passed-success.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)]()

> 基于**生辰八字天干地支推算**、**河图五行数理**、**历史开奖大数据加权**并集成**国内顶级 DeepSeek (deepseek-flash / deepseek-v4-pro 1M上下文) 与 Google Gemini (gemini-3.8-flash / gemini-2.5-flash) AI 多模智能解盘**的现代化专业级彩票助手。采用前后端分离架构，由高性能云端 RESTful API 后端服务与原生 Android Jetpack Compose 客户端协同构成。搭载全新东方轻奢暗金 Taiji 乾坤 Logo。

---

## 目录 (Table of Contents)

1. [项目概述与设计理念 (Overview & Concepts)](#1-项目概述与设计理念-overview--concepts)
2. [系统架构与技术栈 (Architecture & Tech Stack)](#2-系统架构与技术栈-architecture--tech-stack)
3. [项目工程目录结构 (Directory Layout)](#3-项目工程目录结构-directory-layout)
4. [环境依赖与前置准备 (Prerequisites)](#4-环境依赖与前置准备-prerequisites)
5. [后端本地启动与运行 (Local Backend Setup)](#5-后端本地启动与运行-local-backend-setup)
6. [Docker 容器化部署指南 (Docker Deployment)](#6-docker-容器化部署指南-docker-deployment)
7. [Android 客户端构建与打包 (Android Build & Packaging)](#7-android-客户端构建与打包-android-build--packaging)
8. [AI 大模型双模配置指南 (AI Dual-Mode Configuration)](#8-ai-大模型双模配置指南-ai-dual-mode-configuration)
9. [自动化测试与完整质量验证 (Automated Verification)](#9-自动化测试与完整质量验证-automated-verification)
10. [法定理性购彩与免责声明 (Responsible Gaming Disclaimer)](#10-法定理性购彩与免责声明-responsible-gaming-disclaimer)

---

## 1. 项目概述与设计理念 (Overview & Concepts)

本系统融合东方传统命理易学精粹与现代统计学算法，彻底颠覆传统随机摇号机选模式：

- **四柱八字精准推演**：采用国际天文算法与中国农历历法引擎（`lunar-python`），精确计算生辰的年柱（立春为界）、月柱（12节气交节时刻换月）、日柱与时柱（早晚子时与五鼠遁元）。
- **110分地支藏干能量模型**：量化四柱天干地支五行（金、木、水、火、土）力量，月令独占35分高权重，解构地支本气、中气、余气，科学诊断日主身强身弱及命中「喜用神」。
- **河图洛书数理映射池**：遵循“天一生水地六成之、地二生火天七成之、天三生木地八成之、地四生金天九成之、天五生土地十成之”古训，将五行生化能量映射到各彩种号码尾数，构建5级吉祥加权池。
- **历史开奖大数据科学统计**：内建 100+ 期主流彩票真实开奖数据集，支持网络非阻塞动态增量更新。实时计算全量出现频率、30/50期滑动窗口冷热号划分（$\ge 1.25$ 为热，$\le 0.75$ 为冷）、全维度遗漏走势与欲出几率模型。
- **A-Res (Efraimidis-Spirakis) 无重复加权安全抽样**：三元线性加权（命理权重 + 历史频率权重 + 遗漏欲出权重），采用 A-Res 蓄水池算法在 $O(N)$ 时间内完成严格不放回安全抽样，绝对保障前区与后区球号不重复、区间合法、严格升序排列。
- **原生 Android 东方吉庆美学**：基于 Jetpack Compose + Material Design 3，搭载原生 Canvas 绘制的五边形命理雷达图、3D 拟真质感彩球弹跳动画、动态 Base URL 测速与三级离线回退引擎。
- **AI 易道大师双模解盘**：用户既可直连 Google Gemini v1beta 或 OpenAI 兼容端点（Android KeyStore 硬件加密存储），亦可使用后端安全中继，生成具备五段固定结构（【命盘乾坤】、【数理玄机】、【五行合局】、【灵感吉言】、【理性倡议】）的高水准易道卦辞。

---

## 2. 系统架构与技术栈 (Architecture & Tech Stack)

```
+-----------------------------------------------------------------------------------+
|                           Native Android Client (Kotlin)                          |
|  [Jetpack Compose M3]                                                             |
|   ├─ Screen 1: 生辰输入 (公历/农历精准切换、DatePicker / TimePicker)              |
|   ├─ Screen 2: 彩种选择 (双色球 SSQ / 大乐透 DLT / 福彩3D FC3D / 七星彩 QXC)       |
|   ├─ Screen 3: 五行命理雷达图 (Compose Canvas 原生五边形极坐标雷达多边形绘制)     |
|   ├─ Screen 4: 历史冷热走势大屏 (柱状图、冷热号标签、遗漏排行榜)                   |
|   └─ Screen 5: 智能机选球盘 (3D 拟真彩球径向渐变、入盘弹跳动画、一键重抽)         |
|  [AI Divination Client] ── SSE 流式读取 ──> Gemini / OpenAI (用户直连模式)         |
|  [Network Layer (Retrofit + OkHttp)] ── 动态 Base URL 拦截器                       |
|  [Offline Engine] ── 本地纯 Kotlin 八字与离线 100 期种子 (三级离线保底)            |
+------------------------------------------┬----------------------------------------+
                                           │ HTTP RESTful API (JSON)
                                           ▼
+-----------------------------------------------------------------------------------+
|                         Cloud RESTful Backend (FastAPI)                           |
|  [API Router Layer]                                                               |
|   ├─ GET  /api/health                          (系统健康状态与数据库期数监控)      |
|   ├─ GET  /api/lottery/rules                   (主流彩种规则规约与元数据)          |
|   ├─ GET  /api/lottery/stats/{type}            (全量开奖频率统计)                  |
|   ├─ GET  /api/lottery/hot-cold/{type}         (滑动窗口冷热号分类)                |
|   ├─ GET  /api/lottery/omission/{type}         (当前/最大/平均遗漏与欲出几率)      |
|   ├─ POST /api/lottery/validate/{type}         (票面规则与球号区间校验)            |
|   ├─ POST /api/bazi/destiny                    (生辰转干支/四柱/五行打分/喜用神)   |
|   ├─ POST /api/bazi/recommend                  (三元加权 + A-Res 安全机选抽样)     |
|   └─ POST /api/ai/divination                   (易道彩经大师 AI 解盘，支持 SSE)    |
|  [Core Engines]                                                                   |
|   ├─ Lottery Rule & Stats Engine (SSQ, DLT, FC3D, QXC 校验与统计计算)             |
|   ├─ Bazi & Wuxing Engine (lunar-python 节气天文算法 + 110分藏干能量模型)         |
|   ├─ Hetu Numerology & A-Res Sampling (河图五行数理池 + 无重复加权蓄水池采样)     |
|   └─ Data Storage & Sync (SQLite3 WAL 模式 + 100期内置种子库 + 官网增量同步)       |
+-----------------------------------------------------------------------------------+
```

### 核心技术栈

- **后端开发**：Python 3.12+ / 3.14, FastAPI 0.115+, Uvicorn, Pydantic v2, `lunar-python` 1.4+, `sqlite3` (WAL 高性能并发读写), `requests`, `beautifulsoup4`, `httpx`
- **移动端开发**：Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Material Design 3, Navigation Compose, AndroidX Lifecycle / ViewModel, Retrofit2 + OkHttp3, Android KeyStore (EncryptedSharedPreferences)
- **容器与交付**：Docker, Docker Compose, Multi-stage Build, curl healthcheck probe
- **质量保障**：Pytest, 5-Tier 全量 E2E 测试框架, Monte Carlo 仿真, Android JVM 单元测试

---

## 3. 项目工程目录结构 (Directory Layout)

```
blissful-archimedes/
├── backend/                                  # 云端 RESTful API 后端服务
│   ├── Dockerfile                            # 后端容器化构建定义 (Python 3.12-slim)
│   ├── requirements.txt                      # 后端 Python 依赖清单
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                           # FastAPI 应用入口与中间件配置
│   │   ├── core/
│   │   │   ├── config.py                     # 全局环境配置与跨域白名单
│   │   │   └── logging.py                    # 结构化日志配置
│   │   ├── models/
│   │   │   ├── lottery.py                    # 彩种实体与数据契约 Pydantic 模型
│   │   │   └── bazi.py                       # 八字命理与机选推荐请求/响应模型
│   │   ├── services/
│   │   │   ├── lottery_rules.py              # 彩票规则引擎 (SSQ, DLT, FC3D, QXC)
│   │   │   ├── lottery_stats.py              # 历史大数据频率/冷热/遗漏统计引擎
│   │   │   ├── bazi_engine.py                # 干支历四柱推算与天文节气交节算法
│   │   │   ├── wuxing_engine.py              # 支藏干量化打分与喜用神诊断
│   │   │   ├── numerology_engine.py          # 河图五行数理与 A-Res 加权抽样机选
│   │   │   └── ai_service.py                 # 大模型中继、提示词工程与防御加固
│   │   ├── api/
│   │   │   ├── routes_lottery.py             # 彩票数据与统计分析路由
│   │   │   ├── routes_bazi.py                # 八字推算与机选推荐路由
│   │   │   └── routes_ai.py                  # AI 易道大师解盘路由 (支持 SSE 流式)
│   │   └── db/
│   │       ├── database.py                   # SQLite WAL 连接池与自动建表
│   │       ├── sync_service.py               # 官方开奖数据联网抓取与增量同步
│   │       └── seeds/
│   │           └── lottery_seed_100.json     # 100+ 期主流彩种内置离线历史开奖数据
│   └── tests/                                # 后端 Pytest 自动化测试套件 (143项用例)
│       ├── conftest.py
│       ├── test_rules.py                     # 彩种基础校验测试
│       ├── test_stats.py                     # 统计算法与冷热遗漏测试
│       ├── test_bazi.py                      # 八字天文四柱与喜用神推算测试
│       ├── test_recommend.py                 # 加权抽样与合法性测试
│       ├── test_api.py                       # RESTful API 端点端到端测试
│       ├── test_ai.py                        # AI 解盘与空值防御测试 (DEFECT-M5-01)
│       ├── test_adversarial_rules.py         # 规则边界模糊测试与故障注入
│       └── test_empirical_challenger2.py     # 真实种子数据实证与并发测试
│
├── android/                                  # 原生 Android 客户端 (Kotlin + Compose M3)
│   ├── gradlew / gradlew.bat                 # Gradle 跨平台包装脚本
│   ├── gradle/wrapper/                       # Gradle 8.14.3 配置
│   ├── build.gradle.kts                      # 根项目构建脚本
│   ├── settings.gradle.kts                   # 模块配置
│   └── app/
│       ├── build.gradle.kts                  # 应用级构建脚本 (Compose, Retrofit, M3)
│       ├── src/
│       │   ├── main/
│       │   │   ├── AndroidManifest.xml
│       │   │   ├── assets/
│       │   │   │   └── seed_lottery_data.json # 客户端离线备用历史开奖数据
│       │   │   ├── java/com/zeng/lottery/
│       │   │   │   ├── MainActivity.kt       # 单 Activity Compose 主入口
│       │   │   │   ├── LotteryApp.kt         # 全局导航框架与脚手架
│       │   │   │   ├── data/
│       │   │   │   │   ├── api/              # Retrofit API 接口与 BaseUrl 拦截器
│       │   │   │   │   ├── model/            # 客户端数据模型契约
│       │   │   │   │   ├── local/            # 本地纯 Kotlin 八字与离线回退引擎
│       │   │   │   │   └── repository/       # 统一数据仓库
│       │   │   │   ├── ui/
│       │   │   │   │   ├── theme/            # 东方吉庆美学主题配色与字体排印
│       │   │   │   │   ├── components/       # 3D拟真彩球、Canvas 五行雷达图、卡片
│       │   │   │   │   ├── screens/          # 5 大核心界面 (输入/选种/命盘/走势/机选)
│       │   │   │   │   └── viewmodel/        # LotteryViewModel, AiDivinationViewModel
│       │   │   │   └── ai/
│       │   │   │       ├── GeminiClient.kt   # Direct Gemini v1beta SSE 客户端
│       │   │   │       ├── OpenAiClient.kt   # Direct OpenAI 兼容 SSE 客户端
│       │   │   │       └── KeyStoreManager.kt# AndroidKeyStore 硬件级密钥安全加密
│       │   └── test/                         # Android JVM 单元测试
│       │       ├── BaziEngineTest.kt         # 本地八字推算正确性测试
│       │       ├── LotteryRuleTest.kt        # 彩票离线规则测试
│       │       └── ViewModelTest.kt          # ViewModel 状态流驱动测试
│
├── tests_e2e/                                # 5-Tier 独立 E2E 测试验证轨
│   ├── harness/                              # E2E 运行引擎与客户端断言工具
│   ├── run_all_e2e.py                        # 全量 5-Tier E2E 测试一键运行器 (127 tests)
│   ├── run_tier5_adversarial.py              # Tier 5 对抗性测试套件独立运行器 (22 tests)
│   ├── tier1_rules/                          # Tier 1: 彩种规则与基础选号覆盖
│   ├── tier2_boundaries/                     # Tier 2: 生辰极端边界与天文容错测试
│   ├── tier3_interactions/                   # Tier 3: 八字命理与开奖大数据交互验证
│   ├── tier4_realworld/                      # Tier 4: 全流程端到端真实用户场景测试
│   └── tier5_adversarial/                    # Tier 5: 蒙特卡洛/天文极端节气/WAL并发/AI加固
│
├── data/                                     # 挂载宿主机持久化数据卷 (SQLite 数据库)
├── docker-compose.yml                        # Docker 编排服务配置
├── requirements.txt                          # 根级依赖配置
└── README.md                                 # 项目全流程交付与运维文档
```

---

## 4. 环境依赖与前置准备 (Prerequisites)

在开始部署或开发前，请确保您的开发环境满足以下要求：

| 组件 | 最低要求 | 推荐配置 | 用途 |
|---|---|---|---|
| **Python** | 3.12+ | 3.12 / 3.14 | 后端服务运行、数据计算与自动化测试 |
| **Java JDK** | JDK 17+ | OpenJDK 21 / Temurin 21 | Android 编译构建环境 |
| **Android SDK** | SDK 34 | SDK 35 (Build-Tools 35.0.0) | Android 客户端编译 |
| **Docker** | 24.0+ | Docker Desktop / Docker CE | 容器化部署后端服务（可选） |
| **Git** | 2.30+ | 2.40+ | 代码拉取与版本控制 |

### 环境变量准备

如需启用云端大模型解盘，可配置以下环境变量（支持写在 `.env` 文件或容器环境中）：

```bash
# 国内顶级 DeepSeek 模型配置 (推荐)
export DEEPSEEK_API_KEY="your-deepseek-api-key"
export DEEPSEEK_BASE_URL="https://api.deepseek.com/v1"
export DEEPSEEK_MODEL="deepseek-flash"

# 谷歌 Gemini 前沿模型配置 (可选)
export GEMINI_API_KEY="your-gemini-api-key"
export GEMINI_MODEL="gemini-3.8-flash"

# OpenAI 兼容接口配置 (可选)
export OPENAI_API_KEY="your-openai-api-key"
export OPENAI_BASE_URL="https://api.openai.com/v1"
export OPENAI_MODEL="gpt-4o-mini"
```

---

## 5. 后端本地启动与运行 (Local Backend Setup)

### 步骤 1：创建并激活虚拟环境

```bash
# 进入项目根目录
cd blissful-archimedes

# 创建 Python 虚拟环境
python -m venv .venv

# 激活虚拟环境 (Windows PowerShell)
.venv\Scripts\Activate.ps1

# 激活虚拟环境 (Linux / macOS)
source .venv/bin/activate
```

### 步骤 2：安装依赖

```bash
pip install --upgrade pip
pip install -r backend/requirements.txt
```

### 步骤 3：启动 FastAPI 服务

```bash
uvicorn backend.app.main:app --host 127.0.0.1 --port 8000 --reload
```

启动成功后，控制台将输出：
```
[INFO] backend.app.main: Initializing database and seeding if empty...
[INFO] backend.app.main: Database ready. Current draw counts: {'ssq': 100, 'dlt': 100, 'fc3d': 100, 'qxc': 100}
Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
```

### 步骤 4：验证服务可用性

- **健康检查探针**：`curl http://127.0.0.1:8000/api/health`
  ```json
  {
    "status": "healthy",
    "service": "Lottery Big Data & Rule Engine",
    "version": "1.0.0",
    "supported_lotteries": ["ssq", "dlt", "fc3d", "qxc"],
    "database": {
      "status": "connected",
      "total_draws": {"ssq": 100, "dlt": 100, "fc3d": 100, "qxc": 100}
    }
  }
  ```
- **交互式 API 文档 (Swagger UI)**：在浏览器打开 [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- **Redoc 文档**：在浏览器打开 [http://127.0.0.1:8000/redoc](http://127.0.0.1:8000/redoc)

---

## 6. Docker 容器化部署指南 (Docker Deployment)

系统提供工业级容器镜像定义与 `docker-compose.yml` 编排配置，支持一键在 Linux VPS、云服务器或容器平台上部署。

### 方式 A：使用 Docker Compose 一键启动（推荐）

1. 在项目根目录执行启动命令：
   ```bash
   docker-compose up -d --build
   ```

2. 检查容器运行状态与健康检查探针：
   ```bash
   docker-compose ps
   ```
   输出中状态应为 `Up (healthy)`。

3. 查看后端实时运行日志：
   ```bash
   docker-compose logs -f backend
   ```

4. 停止并释放容器：
   ```bash
   docker-compose down
   ```

### 方式 B：单独构建与运行 Docker 容器

```bash
# 1. 构建 Docker 镜像
docker build -f backend/Dockerfile -t bazi-lottery-backend:latest .

# 2. 启动容器 (映射端口并挂载持久化数据库卷)
docker run -d \
  --name bazi_lottery_backend \
  -p 8000:8000 \
  -v "$(pwd)/data:/app/data" \
  -e PORT=8000 \
  -e GEMINI_API_KEY="your-api-key" \
  --restart unless-stopped \
  bazi-lottery-backend:latest

# 3. 检查容器健康检查状态
docker inspect --format='{{json .State.Health}}' bazi_lottery_backend
```

---

## 7. Android 客户端构建与打包 (Android Build & Packaging)

原生 Android 客户端采用 Kotlin 2.0 与 Jetpack Compose M3 架构编写，使用标准的 Gradle 包装器构建。

### 步骤 1：确认环境变量设置

确保 `JAVA_HOME` 指向 JDK 21，`ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）已配置：
```powershell
# Windows PowerShell 示例
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
$env:ANDROID_HOME = "C:\Users\Asus\AppData\Local\Android\Sdk"
```

### 步骤 2：执行 Android JVM 单元测试

在 `android` 目录下执行单元测试：
```bash
cd android
.\gradlew.bat testDebugUnitTest
```
测试通过后将在 `android/app/build/reports/tests/testDebugUnitTest/` 生成 HTML 测试报告。

### 步骤 3：编译与组装 Debug APK

```bash
cd android
.\gradlew.bat assembleDebug
```
构建成功后，生成的 APK 文件位于：
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 4：安装并运行应用

可通过 ADB 命令行将 APK 安装至已连接的物理手机或模拟器：
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 5：配置网络连接与动态 Base URL

- 进入 Android 应用主界面后，点击右上角设置图标（齿轮）。
- 在「后端 API 地址」输入框中填入部署的后端地址：
  - **局域网/实体机测试**：`http://192.168.x.x:8000`
  - **Android 模拟器测试**：`http://10.0.2.2:8000`
  - **云端上线地址**：`https://your-domain.com`
- 点击「网络测速」，系统将执行 `GET /api/health` 并在 50ms 内反馈连接延迟。若网络不可达，应用将自动启用内置的纯 Kotlin 离线引擎保底。

---

## 8. AI 大模型双模配置指南 (AI Dual-Mode Configuration)

系统设计了灵活且安全的**双模 AI 解盘架构**，兼顾个人隐私与免配置体验：

```
                +-------------------------------------------------------------+
                |                Android Client AI Gateway                    |
                +------------------------------┬------------------------------+
                                               │
                        [模式切换: 设置面板 Provider 选择]
                               /               \
                              /                 \
                             ▼                   ▼
                 【模式一: 用户直连模式】     【模式二: 后端安全中继模式】
                 - Direct DeepSeek / Gemini  - 转发至 POST /api/ai/divination
                 - Direct OpenAI 兼容端点     - 前端零配置、无需填 Key
                 - EncryptedKeyStore 加密    - 服务端安全管控与频次限制
                 - OkHttp SSE 打字机流式     - 服务端 Fallback 离线算法保底
```

### 模式一：用户直连模式 (Direct Client Mode)

适用于不愿将个人 Key 上传到服务端的资深用户：
1. 打开 Android 客户端设置页。
2. 在「AI 提供商」选择 **DeepSeek 深度求索**、**Google Gemini** 或 **OpenAI 兼容端点**。支持快捷切换 `deepseek-flash`、`deepseek-v4-pro` 与 `gemini-3.8-flash`、`gemini-2.5-flash`。
3. 填入您的专属 API Key（如 `AIzaSy...` 或 `sk-...`）。
4. （若选择 OpenAI）可自定义 Base URL（例如支持 DeepSeek、Moonshot、OneAPI 等代理端点）及 Model 模型名。
5. **安全存储机制**：密钥通过 `AndroidKeyStore` 利用硬件安全模块（TEE/StrongBox）进行 AES-GCM-256 加密存储在 `EncryptedSharedPreferences` 中，任何第三方应用与日志均无法窃取。
6. 调用解盘时，客户端直接与大模型建立 OkHttp SSE 长连接，实现逐字打印流式动效。

### 模式二：后端安全中继模式 (Backend Relay Mode)

适用于大众小白用户，前端无需配置任何密钥：
1. 服务端部署时，在 `.env` 或环境变量中配置好 `GEMINI_API_KEY` 或 `OPENAI_API_KEY`。
2. Android 端选择「服务端中继模式（默认）」。
3. 客户端发起 `POST /api/ai/divination`，由后端调用云端大模型并将结果安全流式或一次性返回。
4. **高可用保底策略**：若云端大模型发生网络波动或超时，后端内置的高阶五段式易道数理算法将**无缝接管**，确保 100% 成功返回具备权威易理依据的解盘卦辞。

---

## 9. 自动化测试与完整质量验证 (Automated Verification)

项目拥有全行业最严格的 5 层自动化测试验证金字塔，确保全链路算法在极端工况下的 100% 确定性与健壮性：

```
                           ▲
                          / \
                         / 5 \   Tier 5: 对抗加固与极端压力测试 (22 tests)
                        /-----\  - 5000次蒙特卡洛/卡方检验/立春分钟级节气/WAL并发
                       /   4   \ Tier 4: 全流程真实用户生命周期集成测试 (5 tests)
                      /---------\
                     /     3     \ Tier 3: 八字命理与开奖大数据成对交互测试 (12 tests)
                    /-------------\
                   /       2       \ Tier 2: 生辰极端边界与天文容错测试 (27 tests)
                  /-----------------\
                 /         1         \ Tier 1: 彩种规则规约与基础选号测试 (61 tests)
                +---------------------+
                 + 后端 Pytest 自动化测试套件 (143 tests)
                 + 原生 Android JVM 单元测试套件
```

### 验证指令 1：运行后端全量 Pytest 测试套件

覆盖彩票校验规则、历史统计走势、天文四柱推算、A-Res 加权抽样、API 端点集成及白盒模糊故障测试：

```bash
python -m pytest backend/tests -v
```

**实测结果**：`143 passed in 2.37s`（100% 通过）。

### 验证指令 2：运行全量 5-Tier E2E 测试套件

一键执行从底层规则规约到高阶对抗加固的全量 E2E 测试套件：

```bash
python tests_e2e/run_all_e2e.py
```

**实测结果**：
```
================================================================================
  E2E TEST EXECUTION SUMMARY
================================================================================
  Total Tests Executed : 127
  Passed Tests         : 127
  Failed Tests         : 0
  Execution Time       : 6.537 seconds
--------------------------------------------------------------------------------
  Tier Breakdown:
    - Tier 1    :  61 passed,  0 failed  [100.0%]
    - Tier 2    :  27 passed,  0 failed  [100.0%]
    - Tier 3    :  12 passed,  0 failed  [100.0%]
    - Tier 4    :   5 passed,  0 failed  [100.0%]
    - Tier 5    :  22 passed,  0 failed  [100.0%]
================================================================================
  OVERALL VERDICT: ALL E2E QUALITY GATES PASSED
================================================================================
```

### 验证指令 3：运行专属 Tier 5 对抗性与极端应力测试套件

针对蒙特卡洛抽样均匀度、卡方拟合优度检验、立春跨年精确分钟截断、24节气能量守恒、SQLite WAL 24并发读写锁及 AI 畸形报文防御进行独立检验：

```bash
python tests_e2e/run_tier5_adversarial.py
```

**实测结果**：`22 passed, 0 failed [100.0%]`。

### 验证指令 4：运行 Android JVM 单元测试

验证本地纯 Kotlin 引擎在离线工况下的八字推算、规则校验与 ViewModel 状态流：

```bash
cd android
.\gradlew.bat testDebugUnitTest
```

**实测结果**：`BUILD SUCCESSFUL`（24 tasks up-to-date，全部通过）。

---

## 10. 法定理性购彩与免责声明 (Responsible Gaming Disclaimer)

### 📢 国家法定购彩提醒

> **理性购彩，量力而行，公益娱乐。**
>
> 1. 彩票属于国家公益事业，购买彩票属于**纯粹的概率游戏**，中奖结果完全随机。
> 2. 本系统所采用的生辰八字天干地支推算、河图五行数理映射、历史大数据加权以及 AI 大模型智能解盘，其核心初衷为**传承东方传统民俗文化与数理统计学术建模研究**，旨在为机选增添传统文化乐趣与民俗体验。
> 3. **严禁做出任何“包中”、“必中”、“预测稳赢”的虚假承诺**。系统给出的所有号码组合均为算法生成的机选号码，绝不代表未来真实的开奖确定性。
> 4. 请用户树立健康正确的购彩理念，未成年人严禁购彩，切勿过度沉迷或超出自身经济能力进行非理性投注！

---

## 开源许可与维护 (License & Credits)

- 本项目采用 [MIT License](LICENSE) 开源协议。
- 核心历法算法鸣谢：[`lunar-python`](https://github.com/6tail/lunar-python)
- 抽样算法鸣谢：Pavlos S. Efraimidis & Paul G. Spirakis (*Weighted random sampling with a reservoir*)
