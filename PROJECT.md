# Project Architecture: 曾练彩票助手 (Bazi Lottery AI v1.4.0)

## 1. 系统总览

采用前后端分离双端同源引擎架构：
- **云端后端**：Python 3.12+ / FastAPI + SQLite3 (WAL) + GCS 单文件快照持久化 + 6tail `lunar-python` + 91 条易学典籍 RAG + Gemini / DeepSeek / OpenAI SSE 中继。
- **原生客户端**：Android Kotlin 2.0 + Jetpack Compose M3（玄墨暗金 & 宣纸浅色双主题）+ 6tail `cn.6tail:lunar` + 端侧离线对等选号引擎 + `PrizeChecker` 自动对奖引擎 + `AndroidKeyStore` AES-256-GCM 硬件密钥保护。

## 2. 核心 API 契约

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `GET` | `/api/health` | 公开 | 探针状态、版本号、各彩种库内条数与最新开奖期号 |
| `GET` | `/api/health/detailed` | 公开 | 各彩种开奖滞后天数监控与 GCS 同步状态 |
| `GET` | `/api/lottery/rules` | 公开 | SSQ / DLT / FC3D / QXC 规则元数据 |
| `GET` | `/api/lottery/stats/{type}` | 公开 | 全量频次、命中率、冷热分级、遗漏与欲出度 |
| `GET` | `/api/lottery/history/{type}` | 公开 | 分页历史开奖明细 |
| `POST` | `/api/lottery/validate/{type}` | 公开 | 票面合法性校验与规范格式化 |
| `POST` | `/api/lottery/sync` | `Bearer ADMIN_TOKEN` | 多源容错抓取官网开奖并自动上传 GCS 快照 |
| `POST` | `/api/bazi/destiny` | 公开 | 四柱排盘、110分五行、旺衰分级、大运详表与姓名五格数理 |
| `POST` | `/api/bazi/today-fortune` | 公开 | 今日黄历宜忌、财喜神方位、冲煞与个人流日共振 |
| `POST` | `/api/bazi/recommend` | 公开 | 对数空间 A-Res 加权抽样，支持自定义权重与定胆/杀号 |
| `POST` | `/api/ai/divination` | `HMAC-Nonce` / `AI_SERVICE_TOKEN` | 易道大师 SSE 流式解盘（带防注入隔离与违规承诺过滤） |

## 3. 质量门禁基线

- **后端单元测试**：`backend/tests/` — **349 条通过**
- **5-Tier 独立 E2E 测试**：`tests_e2e/` — **153 条 Pytest / 132 条门禁 100% 通过（零 Mock 鉴权回退）**
- **Android JVM 单测**：`android/app/src/test/` — 含对奖引擎、HMAC 签名对齐、HTTPS 校验、离线算法与状态恢复全量验证
- **CI/CD**：`.github/workflows/ci.yml` + `.github/workflows/build-apk.yml`（动态版本号 Release + 正式 Keystore 签名 + R8 混淆）
