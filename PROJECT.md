# Project: Bazi Lottery Recommender (生辰八字彩票机选系统)

## Architecture

前后端分离架构（Cloud RESTful API 后端 + 原生 Android Jetpack Compose 客户端 + 最新 AI 大模型 API 双模集成）：

```
+-----------------------------------------------------------------------------------+
|                           Native Android Client (Kotlin)                          |
|  [Jetpack Compose M3]                                                             |
|   ├─ Screen 1: 生辰输入 (DatePicker / TimePicker / 公历农历切换)                  |
|   ├─ Screen 2: 彩种选择 (SSQ / DLT / FC3D / QXC)                                  |
|   ├─ Screen 3: 五行命理雷达图 (Compose Canvas 原生五边形极坐标渲染)               |
|   ├─ Screen 4: 历史冷热遗漏大屏 (柱状图 / 遗漏走势热力卡片)                       |
|   └─ Screen 5: 智能机选球盘 (3D 拟真彩球径向渐变 / 物理弹跳动画)                 |
|  [AI Divination Client] ── SSE 流式读取 ──> Gemini / OpenAI (用户直连模式)         |
|  [Network Layer (Retrofit + OkHttp)] ── 动态 Base URL 拦截器                       |
|  [Offline Engine] ── 本地纯 Kotlin 八字与离线 100 期种子 (三级离线回退保底)        |
+------------------------------------------┬----------------------------------------+
                                           │ HTTP / RESTful API (JSON)
                                           ▼
+-----------------------------------------------------------------------------------+
|                         Cloud RESTful Backend (FastAPI)                           |
|  [API Router Layer]                                                               |
|   ├─ /api/health                          (系统健康检查)                          |
|   ├─ /api/lottery/rules                   (主流彩票规则规约)                      |
|   ├─ /api/lottery/stats/{type}            (出现频率分析)                          |
|   ├─ /api/lottery/hot-cold/{type}         (冷热号滑动窗口分析)                    |
|   ├─ /api/lottery/omission/{type}         (当前/最大/平均遗漏与欲出几率)          |
|   ├─ /api/bazi/destiny                    (公历转干支/四柱/五行量化/喜用神)       |
|   ├─ /api/bazi/recommend                  (三元线性加权 + A-Res 安全抽样机选)     |
|   └─ /api/ai/divination                   (服务端大模型安全中继)                  |
|  [Core Engines]                                                                   |
|   ├─ Lottery Rule & Stats Engine (SSQ, DLT, FC3D, QXC 校验与统计计算)             |
|   ├─ Bazi & Wuxing Engine (`lunar-python` 节气天文算法 + 110分支藏干能量模型)    |
|   ├─ Hetu Numerology & A-Res Sampling (河图五行数理池 + 无重复加权蓄水池采样)     |
|   └─ Data Storage & Sync (SQLite3 + 100期内置种子数据 + 官网非阻塞爬虫同步)       |
+-----------------------------------------------------------------------------------+
```

---

## Feature Inventory

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | 彩种规则规约与校验引擎 | 严格实现双色球(6红1-33+1蓝1-16)、大乐透(5前1-35+2后1-12)、福彩3D(3位0-9)、七星彩(前6位0-9+第7位0-14)规则与票面校验 | M1 | ORIGINAL_REQUEST §R1 |
| F2 | 历史开奖大数据存储与同步 | 内置 100+ 期离线 JSON 种子数据集 + 官方 API / 网页非阻塞增量更新机制，SQLite UPSERT 存储 | M1 | ORIGINAL_REQUEST §R1 |
| F3 | 开奖频率与冷热走势统计分析 | 计算号码全量出现频次、滑动窗口(30/50期)冷热号划分($\ge 1.25 E_W$ 为热，$\le 0.75 E_W$ 为冷) | M1 | ORIGINAL_REQUEST §R1 |
| F4 | 多维遗漏值分析算法 | 计算当前遗漏、最大历史遗漏、平均遗漏 $\frac{N-k}{k+1}$ 及欲出几率 $g_0 / g_{avg}$ | M1 | ORIGINAL_REQUEST §R1 |
| F5 | 干支历与四柱八字精准推算 | 公历精准换算干支历、立春换年柱、12节气换月柱、儒略日模60日柱、五鼠遁元时柱，支持早晚子时与真太阳时 | M2 | ORIGINAL_REQUEST §R2 |
| F6 | 支藏干能量加权与五行量化打分 | 12地支藏干三气(本/中/余气)拆解，110分四柱位置权重模型(月令35分)，量化五行得分、判定日主强弱与喜用神 | M2 | ORIGINAL_REQUEST §R2 |
| F7 | 河图五行数理与吉祥数字池映射 | 传统河图数理(1/6水, 2/7火, 3/8木, 4/9金, 5/0土)尾数映射扩展至全彩种号码区间，生成5级吉数分层池 | M2 | ORIGINAL_REQUEST §R2 |
| F8 | 三元混合加权与A-Res安全抽样 | 结合命理吉数、出现频率、遗漏值得分进行混合加权，采用 Efraimidis-Spirakis (A-Res) 蓄水池无重复抽样，确保100%合法有效 | M2 | ORIGINAL_REQUEST §R2 |
| F9 | 原生 Android 工程脚手架与主题 | Kotlin 2.0.21 + AGP 8.13 + Gradle 8.14.3 + Material Design 3 现代化主题与响应式布局 | M3 | ORIGINAL_REQUEST §R3 |
| F10 | 生辰输入与彩种选择面板 | M3 DatePicker/TimePicker 交互、公历/农历切换、彩种 Chips/Tab 切换组件 | M3 | ORIGINAL_REQUEST §R3 |
| F11 | 五行命理 Canvas 雷达图谱组件 | 纯 Compose Canvas 绘制正五边形极坐标雷达网格、五行动画扫掠多边形与得分指标展示 | M3 | ORIGINAL_REQUEST §R3 |
| F12 | 历史冷热走势与遗漏大屏 | 历史走势柱状图、冷热号高亮标签与遗漏排行榜交互卡片 | M3 | ORIGINAL_REQUEST §R3 |
| F13 | 3D拟真彩球动效展示盘 | 径向渐变(RadialGradient)偏心光源渲染 3D 红/蓝/金质感彩球，弹跳落球入盘动画，一键智能重抽 | M3 | ORIGINAL_REQUEST §R3 |
| F14 | 客户端动态网络与三级离线回退 | 动态 Base URL 设置与延迟测速，网络超时自动回退本地内置纯 Kotlin 八字与离线 100 期统计 | M3 | ORIGINAL_REQUEST §R3 |
| F15 | 双模 AI 大模型 API 集成 | 支持前端用户在设置中自定义配置 Gemini API Key 或 OpenAI 兼容端点(加密存储)，或使用后端安全中继代理 | M4 | ORIGINAL_REQUEST §R4 |
| F16 | 易道彩经大师提示词工程与流式解盘 | 专业命盘玄机阐释、数理生成依据拆解、今日运势吉言、理性购彩合规免责声明，支持 SSE 打字机流式输出 | M4 | ORIGINAL_REQUEST §R4 |
| F17 | 100% E2E 全量测试套件通过与对抗加固 | 覆盖 Tier 1-4 需求驱动黑盒测试套件（彩种规则、八字推算、加权抽样、网络通信），并通过 Tier 5 白盒对抗测试 | M5 | Acceptance Criteria |
| F18 | 工程交付与完整部署运维文档 | 清晰后端与 Android 目录结构、Dockerfile 容器化配置、详细开发运行/测试/云端部署/打包 README 文档 | M6 | ORIGINAL_REQUEST §R5 |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | 彩票规则引擎与大数据分析后端 | F1, F2, F3, F4: 彩种定义校验、SQLite 100期内置种子库、频率/冷热/遗漏统计计算、FastAPI RESTful API 基础端点 | none | DONE |
| M2 | 生辰八字五行推算与吉祥选号算法 | F5, F6, F7, F8: 干支历四柱推算、110分支藏干能量模型、喜用神判定、河图五行数理池映射、A-Res 加权安全机选抽样接口 | M1 | DONE |
| M3 | 原生 Android 客户端与可视化图谱 | F9, F10, F11, F12, F13, F14: Gradle M3 架构、生辰与彩种面板、Canvas 五行雷达图、冷热走势卡片、3D拟真球盘、动态网络与离线保底 | M1, M2 | DONE |
| M4 | AI 大模型双模集成与智能解盘 | F15, F16: Gemini / OpenAI SSE 流式客户端、EncryptedSharedPreferences、八字易理提示词工程、后端中继与合规免责声明 | M2, M3 | DONE |
| M5 | 100% E2E 测试套件通过与对抗加固 | F17: 满足 E2E 测试套件 (Tiers 1-4) 100% 通过，白盒对抗测试 (Tier 5) 缺陷收敛 | M1, M2, M3, M4 | DONE |
| M6 | 部署工程化、容器化与交付文档 | F18: Dockerfile, docker-compose.yml, 完整 README.md 部署与打包指南，代码仓库归档交付 | M1, M2, M3, M4, M5 | DONE |


---

## Interface Contracts

### 1. Backend RESTful API Specification

#### Health Check
- `GET /api/health`
- Response:
  ```json
  {
    "status": "healthy",
    "version": "1.0.0",
    "timestamp": "2026-11-02T05:00:00Z"
  }
  ```

#### Lottery Rules
- `GET /api/lottery/rules`
- Response:
  ```json
  {
    "rules": {
      "ssq": {
        "name": "双色球",
        "red_range": [1, 33],
        "red_count": 6,
        "blue_range": [1, 16],
        "blue_count": 1
      },
      "dlt": {
        "name": "大乐透",
        "front_range": [1, 35],
        "front_count": 5,
        "back_range": [1, 12],
        "back_count": 2
      },
      "fc3d": {
        "name": "福彩3D",
        "digits_count": 3,
        "range_each": [0, 9]
      },
      "qxc": {
        "name": "七星彩",
        "front_digits_count": 6,
        "front_range": [0, 9],
        "last_range": [0, 14]
      }
    }
  }
  ```

#### Historical Stats & Cold/Hot/Omission
- `GET /api/lottery/stats/{lottery_type}`
- `GET /api/lottery/hot-cold/{lottery_type}?window=30`
- `GET /api/lottery/omission/{lottery_type}`
- Response format:
  ```json
  {
    "lottery_type": "ssq",
    "total_draws": 100,
    "last_issue": "2026125",
    "stats": {
      "red": {
        "1": {"frequency": 18, "rate": 0.18, "is_hot": true, "current_omission": 2, "max_omission": 14, "avg_omission": 5.2},
        ...
      },
      "blue": { ... }
    }
  }
  ```

#### Bazi Destiny Analysis
- `POST /api/bazi/destiny`
- Request:
  ```json
  {
    "birth_datetime": "1990-08-15T08:30:00",
    "calendar_type": "solar",
    "gender": "male",
    "longitude": 120.0
  }
  ```
- Response:
  ```json
  {
    "solar_datetime": "1990-08-15 08:30:00",
    "lunar_datetime": "一九九零年六月廿五 辰时",
    "four_pillars": {
      "year": {"stem": "庚", "branch": "午", "element": "金火", "hide_stems": ["丁", "己"]},
      "month": {"stem": "甲", "branch": "申", "element": "木金", "hide_stems": ["庚", "壬", "戊"]},
      "day": {"stem": "庚", "branch": "戌", "element": "金土", "hide_stems": ["戊", "辛", "丁"]},
      "hour": {"stem": "庚", "branch": "辰", "element": "金土", "hide_stems": ["戊", "乙", "癸"]}
    },
    "wuxing_scores": {
      "metal": 38.5,
      "wood": 10.2,
      "water": 5.4,
      "fire": 18.0,
      "earth": 37.9
    },
    "wuxing_percentage": {
      "metal": 0.35, "wood": 0.09, "water": 0.05, "fire": 0.16, "earth": 0.35
    },
    "day_master": "庚",
    "strength": "身强",
    "lacking_elements": ["水"],
    "xi_yong_shen": ["水", "木"],
    "ji_shen": ["金", "土"]
  }
  ```

#### Bazi Lottery Recommendation
- `POST /api/bazi/recommend`
- Request:
  ```json
  {
    "birth_datetime": "1990-08-15T08:30:00",
    "calendar_type": "solar",
    "lottery_type": "ssq",
    "count": 1,
    "strategy": "balanced",
    "w_bazi": 0.5,
    "w_freq": 0.3,
    "w_omission": 0.2
  }
  ```
- Response:
  ```json
  {
    "lottery_type": "ssq",
    "strategy": "balanced",
    "recommendations": [
      {
        "primary_numbers": ["03", "06", "11", "18", "21", "28"],
        "secondary_numbers": ["09"],
        "wuxing_distribution": {"wood": 2, "water": 2, "metal": 1, "fire": 1, "earth": 1},
        "score_rationale": "命中喜用神水(1/6)与木(3/8)契合度 71.4%，融合近30期大遗漏追热指数"
      }
    ],
    "bazi_summary": { ... }
  }
  ```

#### AI Divination Relay
- `POST /api/ai/divination`
- Request:
  ```json
  {
    "birth_datetime": "1990-08-15T08:30:00",
    "lottery_type": "ssq",
    "recommended_numbers": {"red": ["03", "06", "11", "18", "21", "28"], "blue": ["09"]},
    "bazi_info": { ... },
    "stream": true
  }
  ```
- Response: SSE (`text/event-stream`) streaming text response.

---

## Code Layout

```
C:\Users\Asus\Documents\antigravity\blissful-archimedes\
├── PROJECT.md                                # 全局工程总索引与里程碑规约
├── README.md                                 # 部署运行与测试完整文档
├── docker-compose.yml                        # 生产与本地容器编排
│
├── backend/                                  # 云端 RESTful API 后端服务 (Python / FastAPI)
│   ├── Dockerfile                            # 后端容器构建定义
│   ├── requirements.txt                      # 依赖项清单 (fastapi, uvicorn, lunar-python, pytest, etc.)
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                           # FastAPI 应用入口与中间件配置
│   │   ├── core/
│   │   │   ├── config.py                     # 环境配置与跨域设置
│   │   │   └── logging.py                    # 日志体系
│   │   ├── models/
│   │   │   ├── lottery.py                    # 彩种实体与 Pydantic 数据契约
│   │   │   └── bazi.py                       # 八字命理与推荐请求/响应模型
│   │   ├── services/
│   │   │   ├── lottery_rules.py              # 彩票规则与校验引擎 (SSQ, DLT, FC3D, QXC)
│   │   │   ├── lottery_stats.py              # 历史大数据频率/冷热/遗漏统计引擎
│   │   │   ├── bazi_engine.py                # 干支历四柱推算与天文节气计算
│   │   │   ├── wuxing_engine.py              # 支藏干量化打分与喜用神诊断
│   │   │   ├── numerology_engine.py          # 河图五行数理与 A-Res 加权抽样机选
│   │   │   └── ai_service.py                 # 大模型中继与提示词生成
│   │   ├── api/
│   │   │   ├── routes_lottery.py             # 彩票数据与统计路由
│   │   │   ├── routes_bazi.py                # 八字推算与机选推荐路由
│   │   │   └── routes_ai.py                  # AI 解盘路由
│   │   └── db/
│   │       ├── database.py                   # SQLite 数据连接与初始化
│   │       ├── seeds/
│   │       │   └── lottery_seed_100.json     # 100+期内置离线历史开奖数据集
│   │       └── sync_service.py               # 官方开奖数据联网抓取与同步
│   └── tests/                                # 后端自动化单元与集成测试
│       ├── test_rules.py                     # 彩种校验测试
│       ├── test_stats.py                     # 统计算法与冷热遗漏测试
│       ├── test_bazi.py                      # 八字天文四柱与喜用神推算测试
│       ├── test_recommend.py                 # 加权抽样与合法性测试
│       └── test_api.py                       # RESTful API 端点端到端测试
│
├── android/                                  # 原生 Android 客户端 (Kotlin + Jetpack Compose)
│   ├── gradlew / gradlew.bat                 # Gradle 包装脚本
│   ├── gradle/wrapper/                       # Gradle 8.14.3 配置
│   ├── build.gradle.kts                      # 根构建脚本
│   ├── settings.gradle.kts                   # 模块配置
│   └── app/
│       ├── build.gradle.kts                  # 应用级构建脚本 (Compose, Retrofit, OkHttp, M3)
│       ├── src/
│       │   ├── main/
│       │   │   ├── AndroidManifest.xml
│       │   │   ├── assets/
│       │   │   │   └── seed_lottery_data.json # 客户端离线备用历史开奖数据
│       │   │   ├── java/com/zeng/lottery/
│       │   │   │   ├── MainActivity.kt       # 单 Activity Compose 入口
│       │   │   │   ├── LotteryApp.kt         # 全局导航与脚手架
│       │   │   │   ├── data/
│       │   │   │   │   ├── api/              # Retrofit API 接口与 BaseUrl 拦截器
│       │   │   │   │   ├── model/            # 客户端数据模型
│       │   │   │   │   ├── local/            # 本地纯 Kotlin 八字与离线回退引擎
│       │   │   │   │   └── repository/       # 统一数据仓库
│       │   │   │   ├── ui/
│       │   │   │   │   ├── theme/            # Material 3 东方吉庆美学主题配色
│       │   │   │   │   ├── components/       # 3D拟真彩球、Canvas 五行雷达图、卡片
│       │   │   │   │   ├── screens/          # 5 大核心界面 (输入/选种/命盘/走势/机选)
│       │   │   │   │   └── viewmodel/        # LotteryViewModel, AiDivinationViewModel
│       │   │   │   └── ai/
│       │   │   │       ├── GeminiClient.kt   # Direct Gemini SSE 客户端
│       │   │   │       ├── OpenAiClient.kt   # Direct OpenAI 兼容 SSE 客户端
│       │   │   │       └── KeyStoreManager.kt# 密钥加密存储
│       │   └── test/                         # Android JVM 单元测试
│       │       ├── BaziEngineTest.kt
│       │       ├── LotteryRuleTest.kt
│       │       └── ViewModelTest.kt
│
└── tests_e2e/                                # E2E 测试轨独立测试套件 (需求驱动黑盒验证)
    ├── harness/                              # E2E 测试运行器与断言工具
    ├── tier1_rules/                          # Tier 1: 彩种规则与基础选号功能覆盖
    ├── tier2_boundaries/                     # Tier 2: 生辰极端边界与容错测试
    ├── tier3_interactions/                   # Tier 3: 八字命理与开奖大数据交互验证
    └── tier4_realworld/                      # Tier 4: 全流程端到端真实用户场景测试
```
