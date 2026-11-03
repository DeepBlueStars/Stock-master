# AI Berkshire 融合方案（StockAI 工作区）

> 目标：把 `https://github.com/xbtlin/ai-berkshire` 的投研方法论、Skill 提示词和金融计算工具，融合进当前 StockAI 工作区，形成「候选池 → 去劣筛选 → 深度研究 → 论文跟踪 → 组合复盘」的闭环。
>
> 分析基准：2026-10-31 拉取的 ai-berkshire 仓库（247 MB，其中 `reports/` 约 136 MB、3630 个文件）。
> 本文是实施方案，不包含代码改动；阶段划分可直接拆成 OpenSpec change 或开发任务。

---

## 一、结论先行

### 1.1 ai-berkshire 不是可运行的应用

它是一套 **面向 Claude Code / Codex 的提示词工作流集合**：

| 组成 | 内容 | 能否直接融合 |
|---|---|---|
| `skills/*.md`（21 个） | 投研流程、评分标准、输出模板 | ✅ 可作为提示词资产移植 |
| `codex-skills/*/SKILL.md`（22 个） | 由 `skills/*.md` 生成的 Codex 版本 | ⚠️ 与 `skills/` 重复，二选一 |
| `codex-prompts/*.md` | Codex slash prompt 兼容层 | ❌ 不需要 |
| `tools/*.py`（14 个） | 金融验算、估值、筛选、爬虫、报告索引 | ✅ 部分移植，部分保留 |
| `reports/`（2274 份报告） | 示例与历史产物 | ❌ 不复制（体积大且与产品无关） |
| `data/`、`assets/`、`reader/` | 示例数据、图片、阅读器 | ❌ 不复制 |
| `scripts/` | 安装到 `~/.claude/commands` / `~/.codex/skills` | ❌ 本项目不需要 |

**关键判断**：它真正的价值是 **方法论 + 输出契约 + 反幻觉纪律 + 可复算工具**，而不是代码库。
直接 `git clone` 后把目录塞进 StockAI，不会让 StockAI 多出任何功能；必须把 Skill 改造成 **Spring AI 可执行的提示词 + 数据注入 + 工具调用 + 报告落库 + 前端展示**。

### 1.2 推荐路线

**推荐：以 Java 原生「Research Center」为主，etf-service 补齐 A 股财务历史数据，Claude Code/Codex 保留为“重型离线研究”入口。**

原因：

1. StockAI 后端已经具备 Spring AI `ChatClient`、DeepSeek 配置、MongoDB、定时任务、通知、`Result<T>` 响应规范，新增研究模块的接入成本最低。
2. ai-berkshire 的 Skill 本质是“结构化提示词”，Java 完全可以执行；真正的缺口是 **数据** 和 **Agent 工具**，不是语言。
3. 把 Python 工具全部搬进 Java 不现实，但 `financial_rigor.py`（382 行）核心计算可低成本移植；`akshare` 数据采集继续放在 etf-service（已有 Python + akshare + Mongo + 调度器）。
4. 不新增第 5 个常驻服务，部署拓扑保持 `Console → StockAI → MongoDB` + `etf-service → MongoDB`。
5. Claude Code/Codex 仍然适合一次性、需要大量联网检索的深度报告；产物按统一格式导入即可，和产品功能不冲突。

### 1.3 一句话落地路径

> 先做一个 **单只股票的「巴菲特 Checklist + 去劣筛选」最小闭环**（P0），验证提示词、数据、落库、前端四件事；
> 再加 **论文跟踪 + 组合复盘 + 异动归因**（P1）；
> 最后做 **四大师深度研究 + 多 Agent 团队**（P2）。

---

## 二、ai-berkshire 能力盘点

### 2.1 22 个 Skill 的真实依赖

统计 `skills/*.md` 中对 Claude Code 专属能力的引用：

| Skill | WebSearch | Task（子 Agent） | Team/TaskCreate | 说明 |
|---|---:|---:|---:|---|
| `investment-team` | 10 | 4 | 4 | 强依赖多 Agent |
| `news-pulse` | 1 | 3 | 4 | 强依赖多 Agent + 联网 |
| `private-company-research` | 1 | 1 | 4 | 强依赖多 Agent + 联网 |
| `investment-research` | 0 | 1 | 0 | 依赖联网取数 |
| `investment-checklist` | 0 | 1 | 0 | 可改为“数据注入” |
| `quality-screen` | 2 | 0 | 0 | 需要 10 年财务数据 |
| `thesis-tracker` | 2 | 0 | 0 | 需要行情 + 新闻 |
| `portfolio-review` | 1 | 1 | 0 | 可用现有持仓数据 |
| `earnings-review` | 0 | 1 | 0 | 需要财报原文/公告 |
| `bottleneck-hunter` | 4 | 0 | 0 | 强依赖联网 |
| `management-deep-dive` | 1 | 1 | 0 | 联网 + 推理 |
| `industry-research` | 0 | 1 | 0 | 联网 + 行业数据 |
| `industry-funnel` | 0 | 0 | 0 | 需要全市场股票池 |
| `financial-data` | 0 | 0 | 0 | 数据规范，可直接复用 |
| `thesis-drift` | 0 | 0 | 0 | 纯文本对比，易落地 |
| `dyp-ask` | 0 | 0 | 0 | 纯思维问答，易落地 |
| `era-alpha` | 0 | 0 | 0 | 偏主题判断，需联网 |
| `income-investment` | 0 | 0 | 0 | 收益型分析，需财务数据 |
| `deep-company-series` | 0 | 0 | 0 | 长文生成，可离线 |
| `wechat-article` | 0 | 0 | 0 | 内容生成，可离线 |
| `earnings-team` | 0 | 0 | 0 | 多角色写作，可并行 |

**结论**：

- **不依赖联网/多 Agent 的 Skill**（`investment-checklist`、`quality-screen`、`portfolio-review`、`thesis-tracker`、`thesis-drift`、`dyp-ask`、`income-investment`、`deep-company-series`、`wechat-article`、`financial-data`）最适合先做成产品功能。
- **强依赖 `Task/WebSearch/TeamCreate` 的 Skill**（`investment-team`、`news-pulse`、`private-company-research`、`bottleneck-hunter`）不能原样搬；必须用“并行 `ChatClient` 调用 + 数据检索层”替代。

### 2.2 可直接复用的“硬资产”

| 资产 | 位置 | 价值 | 处理方式 |
|---|---|---|---|
| 四大师评分框架 | `skills/investment-checklist.md`、`investment-research.md`、`investment-team.md` | 六关 Checklist、镜子测试、硬否决清单、四维评分 | 移植为 Prompt + 结构化输出 |
| 去劣 7 指标 + 3 豁免 | `skills/quality-screen.md` | 可确定性实现，最适合工程化 | 规则引擎 + Prompt 解释 |
| 数据交叉验证规范 | `skills/financial-data.md` | 两源校验、误差阈值、单位/口径说明 | 合并为 `common-rules.md` |
| 金融精确计算 | `tools/financial_rigor.py`（382 行，零依赖 `decimal`） | 市值验算、PE/PB/ROE、三情景、交叉验证、Benford | 核心函数移植 Java；或保留 Python CLI |
| 终值/IRR 工具 | `tools/terminal_value.py`（672 行） | 永续增长 PE、10 年 IRR、敏感性 | P2 移植为 Java 计算服务 |
| 报告抽检 | `tools/report_audit.py`（465 行） | 15% 数据点抽检、准出/打回 | P2 做简化版 |
| 报告索引/命名规范 | `tools/reports_index.py`、`CLAUDE.md` | 公司/专题/日期目录规范 | 用于报告归档与前端索引 |
| 反偏见机制 | 信息丰富度 A/B/C、芒格逆向、快速否决、留白原则 | 提升报告可信度 | 写进公共 Prompt 和输出校验 |

### 2.3 不能直接搬的部分

1. **Claude Code 的 `Task` / `WebSearch` / `TeamCreate` / `TaskCreate`**：Spring AI 没有这些内置工具。需要自建检索/并行编排。
2. **`$ARGUMENTS` 占位符**：需要模板渲染。
3. **面向 GitHub 的报告目录/公众号流程**：与 StockAI 产品无关，最多做导出。
4. **美股/港股/台股数据源**（macrotrends、aastocks、FinMind）：当前 StockAI 以 A 股为主，先不做；需要时单独接。
5. **雪球爬虫**（Playwright + 登录态）：合规和维护成本高，P3 再评估。

---

## 三、当前项目现状与差距

### 3.1 现状（已核对代码）

| 能力 | 位置 | 现状 |
|---|---|---|
| LLM 调用 | `CommonConfiguration#chatClient`、`ChatController` | Spring AI 1.0.3 + OpenAI 兼容 DeepSeek，已有流式接口 |
| Prompt 资源 | `StockAI/src/main/resources/prompts/` | 已有 `system.md`、交易计划周报 Prompt + 模板 |
| AI 报告模式 | `ScreeningReportService`、`TradePlanSummaryService` | 已有“生成 → 结构校验 → 失败降级 → 落库”的成熟范式 |
| 选股 | `ScreeningService`、`HotspotService` | 四因子、三风格 Top20、热点行业/概念 |
| 持仓/交易 | `StockService`、`Position`、`StockTrade` | 买卖、成本、收益、现金 |
| 交易计划 | `TradePlanService`、`TradePlanSummaryService` | ATR、支撑阻力、周报 |
| 行情/财务 | `MarketQuotes`、`StockDailyKline`、`StockFundamentals` | 日线、PE/PB/ROE/营收增速/利润增速/毛利率 |
| 数据采集 | `etf-service`（Python FastAPI + akshare + Mongo） | ETF 行情、指数、个股财务摘要、行业概念、估值 |
| 通知 | `NotificationService`、Feishu sender | 站内 + 飞书 |
| 前端 | `StockAI-Console`（Vue 3 + Element Plus） | 总览、股票、配置、市场、交易计划 |
| 测试 | JUnit 5 + Mockito | 已有 `ChatClient` mock 范式 |

### 3.2 与 ai-berkshire 的差距

| ai-berkshire 需要 | StockAI 现状 | 差距等级 | 建议 |
|---|---|---|---|
| 10 年 ROE、毛利率、净利率 | 只有单期 `roe`、`gross_margin` | 🔴 高 | 新增 `stock_financial_history` |
| 5 年经营现金流、资本开支、FCF | 无 | 🔴 高 | akshare 采集 + 新集合 |
| EBIT、利息支出、有息负债 | 无 | 🔴 高 | 新增字段 |
| 5 年总股本变化 | 无 | 🟡 中 | 新增字段 |
| EPS、BVPS、每股股息、FCF/share | 无 | 🟡 中 | 新增字段 |
| 财报原文/公告/电话会 | 无 | 🟡 中 | akshare 公告 + 手动上传 |
| 公司新闻/行业新闻 | 只有热点板块 | 🟡 中 | `stock_news` + akshare `stock_news_em` |
| 联网搜索/多源交叉验证 | DeepSeek 无联网 | 🔴 高 | 先用 akshare/公告，后续接搜索 API |
| 多 Agent 并行 | 无 | 🟡 中 | `CompletableFuture` + 多 `ChatClient` |
| 报告存储/版本/审计 | `analysis_reports` 字段极少 | 🟡 中 | 新建 `research_reports`，不要改旧集合 |
| 报告前端 | 无 | 🟡 中 | 新增 `/research` 页面 |
| 组合层面分析 | 有持仓/行业，无组合报告 | 🟢 低 | 复用现有数据 |
| 论文跟踪 | 无 | 🟡 中 | 新建 `research_thesis` |

> **最大的坑不是模型，是数据。** 没有 5–10 年财务历史，`quality-screen` 和 `investment-checklist` 的关键指标只能填“数据不足”，报告会退化成泛泛而谈。因此 P0 必须同时做数据补齐。

---

## 四、推荐架构

### 4.1 总体架构

```
┌──────────────────────────────────────────────────────────────┐
│ StockAI-Console (Vue 3)                                      │
│  /research  研究中心                                         │
│   ├─ 个股 Checklist / 去劣筛选                               │
│   ├─ 深度研究报告列表 / 详情（Markdown）                      │
│   ├─ 投资论文（假设 / 红线 / 季度检查）                       │
│   ├─ 组合复盘                                                 │
│   └─ 异动归因（News Pulse）                                   │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTP (axios, 现有 /srv 反代)
┌───────────────────────────▼──────────────────────────────────┐
│ StockAI 后端 (Java 21 / Spring Boot 3.5 / Spring AI 1.0.3)   │
│                                                              │
│  research 包                                                 │
│   ├─ ResearchController          REST API                    │
│   ├─ ResearchRunService          异步任务 / 进度 / SSE        │
│   ├─ ResearchOrchestrator        单 Agent / 多 Agent 编排     │
│   ├─ ResearchSkillRegistry       加载 skills/*.md 模板        │
│   ├─ ResearchContextAssembler    数据装配（Mongo + etf-service）│
│   ├─ FinancialRigorService       精确计算（BigDecimal）        │
│   ├─ ResearchReportValidator     结构校验 / 降级              │
│   ├─ ResearchAuditService        数据抽检（P2）               │
│   ├─ ThesisService               论文建档 / 季度检查           │
│   ├─ PortfolioReviewService      组合分析                     │
│   ├─ NewsPulseService            异动归因                     │
│   └─ ResearchTask                收盘/季度定时任务             │
│                                                              │
│  复用：ChatClient、MongoTemplate、NotificationService、Feishu │
└───────────────┬───────────────────────────────┬──────────────┘
                │ Mongo                         │ HTTP
┌───────────────▼──────────────┐   ┌────────────▼──────────────┐
│ MongoDB (tradingagents)      │   │ etf-service (FastAPI)      │
│  research_reports            │   │  /research/fundamentals    │
│  research_runs               │   │  /research/news            │
│  research_thesis             │   │  /research/announcements   │
│  research_thesis_checks      │   │  /research/quotes          │
│  stock_financial_history     │   │  /research/industry        │
│  stock_news                  │   │  复用 akshare + Mongo      │
│  research_sources            │   └────────────────────────────┘
└──────────────────────────────┘
```

### 4.2 职责边界

| 层 | 负责 | 不负责 |
|---|---|---|
| StockAI-Console | 发起研究、查看报告、论文管理、组合复盘 | 直接调用 LLM |
| StockAI 后端 | Prompt 编排、LLM 调用、报告落库、校验、定时任务、通知 | 原始数据爬取 |
| etf-service | A 股财务历史、公告、新闻、行情、行业数据采集与标准化 | LLM 调用、报告生成 |
| MongoDB | 报告、运行状态、论文、财务历史、新闻、来源 | — |
| Claude Code/Codex | 需要大量联网检索的离线深度报告 | 产品内自动流程 |

### 4.3 为什么不用“独立 Python 研究服务”

备选方案：新建 `research-service`（FastAPI + OpenAI SDK + 直接复用 `tools/*.py`）。

- 优点：Python 工具零改动、多 Agent 编排容易、Prompt 可直接读文件。
- 缺点：多一个常驻服务、重复 LLM/Mongo 配置、部署与监控成本上升、与现有 Java 业务割裂。

**结论**：除非你确定要把“多 Agent 深度研究”做成独立产品，否则不新增服务。P0/P1 用 Java 足够；`financial_rigor.py` 核心函数移植成本约 1–2 天。

---

## 五、数据模型设计

> 原则：**不改旧集合**，新增集合，避免影响现有选股、交易计划、前端。

### 5.1 `research_reports`（报告主表）

```json
{
  "_id": "uuid",
  "user_id": "6928fca14f206ada1b7a9269",
  "symbol": "600519",
  "stock_name": "贵州茅台",
  "market": "A",
  "skill": "investment-checklist",
  "skill_version": "ai-berkshire@2026-10-31",
  "prompt_version": "v1",
  "status": "SUCCEEDED",
  "recommendation": "PASS",
  "total_score": 4.7,
  "scores": {
    "circle_of_competence": 5,
    "business_quality": 5,
    "moat": 5,
    "management": 3,
    "margin_of_safety": 4,
    "discipline": 5
  },
  "confidence_level": "A",
  "conclusion": "通过，但管理层维度扣分，需跟踪...",
  "content_md": "# ...",
  "data_cutoff": "2026-10-30",
  "data_snapshot": { "pe": 21.3, "roe_10y_avg": 0.31, "...": "..." },
  "missing_fields": ["interest_coverage"],
  "sources": [
    {"field": "revenue", "name": "东方财富", "url": "...", "value": 1239, "confidence": "high"}
  ],
  "audit": {"status": "PASS", "sampled": 12, "failed": 0},
  "model": "deepseek-flash",
  "prompt_tokens": 12000,
  "completion_tokens": 3000,
  "cost_estimate": 0.02,
  "run_id": "uuid",
  "created_at": "2026-10-31 10:00:00",
  "updated_at": "2026-10-31 10:02:00"
}
```

索引：`(user_id, symbol, created_at desc)`、`(symbol, skill, created_at desc)`、`(status)`。

### 5.2 `research_runs`（异步运行状态）

```json
{
  "_id": "uuid",
  "report_id": "uuid",
  "user_id": "...",
  "skill": "investment-research",
  "params": {"symbol": "600519", "market": "A", "depth": "standard"},
  "status": "RUNNING",
  "stage": "master_buffett",
  "progress": 45,
  "stages": [
    {"name": "data_collect", "status": "DONE", "duration_ms": 1200},
    {"name": "master_duan", "status": "RUNNING", "duration_ms": null}
  ],
  "error": null,
  "started_at": "...",
  "finished_at": null
}
```

### 5.3 `research_thesis` / `research_thesis_checks`

```json
// research_thesis
{
  "_id": "uuid",
  "user_id": "...",
  "symbol": "600519",
  "stock_name": "贵州茅台",
  "version": 1,
  "source_report_id": "uuid",
  "core_thesis": "我以 1500 元买入...（5 句话）",
  "assumptions": [
    {"no": 1, "text": "收入增速维持 10%+", "verify_by": "季报", "frequency": "quarterly", "status": "HOLD"}
  ],
  "red_lines": [
    {"no": 1, "text": "管理层诚信问题", "severity": "FATAL", "action": "清仓"}
  ],
  "valuation_anchors": {"buy_price": 1500, "neutral_target": 1900, "bear_case": 1100},
  "status": "ACTIVE",
  "next_check_at": "2026-11-15",
  "created_at": "...",
  "updated_at": "..."
}

// research_thesis_checks
{
  "_id": "uuid",
  "thesis_id": "uuid",
  "check_date": "2026-11-15",
  "status": "HOLD",
  "findings": [{"assumption_no": 1, "result": "HOLD", "evidence": "..."}],
  "red_line_triggered": null,
  "report_id": "uuid",
  "created_at": "..."
}
```

### 5.4 `stock_financial_history`（补齐 ai-berkshire 的核心输入）

```json
{
  "_id": "uuid",
  "symbol": "600519",
  "report_date": "2025-12-31",
  "period_type": "annual",          // annual | q1 | h1 | q3
  "revenue": 1239.0,
  "gross_profit": 1100.0,
  "net_profit": 600.0,
  "operating_cash_flow": 650.0,
  "capex": 50.0,
  "free_cash_flow": 600.0,
  "ebit": 800.0,
  "interest_expense": 2.0,
  "total_assets": 2500.0,
  "total_equity": 2000.0,
  "total_debt": 50.0,
  "cash": 1500.0,
  "shares_outstanding": 1.256e9,
  "eps": 47.0,
  "bvps": 160.0,
  "dividend_per_share": 30.0,
  "roe": 0.31,
  "gross_margin": 0.91,
  "net_margin": 0.52,
  "data_source": "akshare/eastmoney",
  "source_url": "...",
  "fetched_at": "..."
}
```

唯一索引：`(symbol, report_date, period_type)`。

### 5.5 `stock_news`

```json
{
  "_id": "uuid",
  "symbol": "600519",
  "published_at": "2026-10-30 09:00:00",
  "title": "...",
  "summary": "...",
  "url": "...",
  "source": "eastmoney",
  "category": "company|industry|policy|market",
  "sentiment": "positive|neutral|negative",
  "fetched_at": "..."
}
```

### 5.6 `research_sources`

用于交叉验证和审计，记录“报告里的每个关键数字从哪来”：

```json
{
  "_id": "uuid",
  "report_id": "uuid",
  "field": "revenue_2025",
  "source_name": "东方财富",
  "source_url": "...",
  "value": 1239.0,
  "unit": "亿元",
  "confidence": "high",
  "fetched_at": "..."
}
```

---

## 六、API 设计

统一使用现有 `Result<T>` 包装（`state=0` 成功，`state=1` 失败）。

| 方法 | 路径 | 说明 | 阶段 |
|---|---|---|---|
| POST | `/research/run` | 发起研究（异步） | P0 |
| GET | `/research/run/{runId}` | 查询运行状态/进度 | P0 |
| GET | `/research/run/{runId}/stream` | SSE 流式输出 | P2 |
| POST | `/research/reports` | 报告列表（分页/筛选） | P0 |
| GET | `/research/report/{id}` | 报告详情 | P0 |
| DELETE | `/research/report/{id}` | 删除报告 | P1 |
| POST | `/research/checklist` | 单股/多股 Checklist（快捷入口） | P0 |
| POST | `/research/quality-screen` | 去劣筛选（单股/股票池） | P0 |
| POST | `/research/thesis/create` | 建立投资论文 | P1 |
| POST | `/research/thesis/check` | 执行论文检查 | P1 |
| GET | `/research/thesis/list` | 论文列表 | P1 |
| GET | `/research/thesis/{id}` | 论文详情 | P1 |
| POST | `/research/portfolio-review` | 组合复盘 | P1 |
| POST | `/research/news-pulse` | 异动归因 | P1 |
| GET | `/research/companies/{symbol}/timeline` | 公司报告时间线 | P2 |
| POST | `/research/export/{id}` | 导出 Markdown/PDF | P3 |

请求示例：

```json
POST /research/run
{
  "userId": "6928fca14f206ada1b7a9269",
  "symbol": "600519",
  "market": "A",
  "skill": "investment-checklist",
  "options": {
    "depth": "standard",
    "includeNews": true,
    "maxReportChars": 12000
  }
}
```

响应：

```json
{
  "state": 0,
  "message": null,
  "data": {
    "runId": "uuid",
    "reportId": "uuid",
    "status": "QUEUED"
  }
}
```

---

## 七、功能映射与优先级

### 7.1 P0（1–2 周）：最小可用闭环

| 功能 | 对应 Skill | 数据来源 | 交付物 |
|---|---|---|---|
| 个股六关 Checklist | `investment-checklist` | 行情 + 财务历史 + 行业 + 新闻 | `/research` 页面 + 报告详情 |
| 去劣 7 指标筛选 | `quality-screen` | 财务历史（10 年/可得年份） | 汇总表 + 豁免说明 |
| 数据交叉验证 | `financial-data` | 两源/缺失标注 | 报告附录“数据核验记录” |
| 金融精确计算 | `financial_rigor.py` | 输入参数 | `FinancialRigorService` + 单测 |

**验收标准**：

1. 输入 `600519`，30 秒内生成六关表格，总分、结论、镜子测试齐全。
2. 每个财务数字能在 `research_sources` 找到来源；缺失字段明确标注“数据不足”。
3. 报告 Markdown 落库，前端列表/详情可读。
4. 模型失败时回退为确定性表格，`status=fallback`，不丢数据。
5. `FinancialRigorService` 对 `0.1 + 0.2`、市值单位、三情景计算的单测通过。

### 7.2 P1（1–2 周）：持仓闭环

| 功能 | 对应 Skill | 数据来源 | 交付物 |
|---|---|---|---|
| 投资论文建档 | `thesis-tracker` | 最新报告 + 持仓成本 + 估值 | `research_thesis` + 页面 |
| 季度论文检查 | `thesis-tracker` | 最新财报 + 新闻 + 行情 | `research_thesis_checks` + 通知 |
| 组合复盘 | `portfolio-review` | 持仓 + 行情 + 行业 + 现金 | 组合报告 + 集中度/相关性表 |
| 异动归因 | `news-pulse` | 日涨跌 + 新闻 + 公告 + 行业 | 归因报告 + 通知 |
| 论文漂移 | `thesis-drift` | 两份报告 | 差异报告 |

**验收标准**：

1. 持仓股可一键建档，自动填充 5 句话论文、3–7 个假设、红线清单。
2. 论文检查结果分 `HOLD / WATCH / BREAK`，触发红线时发站内/飞书通知。
3. 组合复盘能读取真实持仓，输出第一大持仓占比、前三大占比、行业集中度、相关性检查。
4. 单日跌幅超过阈值（默认 5%）的持仓自动触发 News Pulse，生成“真因不明/情绪/价值事件”判断。

### 7.3 P2（2–4 周）：深度研究

| 功能 | 对应 Skill | 关键实现 |
|---|---|---|
| 四大师单公司深度研究 | `investment-research` | 7 模块顺序生成 + 三情景估值 + 数据审计 |
| 四大师并行团队 | `investment-team` | 4 个并行 `ChatClient` + Team Lead 综合 |
| 财报精读 | `earnings-review` | 公告/财报原文 + 现金流/资产负债表专项 |
| 管理层纵深 | `management-deep-dive` | 管理层专题 Prompt |
| 行业全景/漏斗 | `industry-research`、`industry-funnel` | 复用 `IndexConstituents` + 行业概念 + 财务历史 |
| 报告抽检 | `report_audit.py` | 15% 数据点抽检 + 准出/打回 |

**验收标准**：

1. 深度报告包含：信息丰富度评级、生意本质、护城河、逆向思考、管理层、文明趋势、估值、反面论据、数据来源。
2. 三情景目标价由 `FinancialRigorService` 计算，Prompt 只做解释，不做心算。
3. `investment-team` 4 个视角独立生成，综合报告能体现分歧（不是四份报告拼接）。
4. 报告审计抽检 ≥10 个数据点，失败项必须回退修正或标记。

### 7.4 P3（可选）：内容与站点

- `deep-company-series`：8 篇长文系列。
- `wechat-article` / `earnings-team`：公众号文章。
- `bottleneck-hunter` / `era-alpha` / `private-company-research`：强联网，建议继续在 Claude Code/Codex 离线执行，产物导入。
- 报告静态站点（参考 `tools/build_site.py`）与全文搜索。

---

## 八、代码级改造清单

### 8.1 StockAI 后端新增

```
StockAI/src/main/java/com/xian/stockai/research/
├── config/ResearchProperties.java
├── controller/ResearchController.java
├── dao/
│   ├── ResearchReportRepository.java
│   ├── ResearchRunRepository.java
│   ├── ResearchThesisRepository.java
│   ├── ResearchThesisCheckRepository.java
│   ├── StockFinancialHistoryRepository.java
│   ├── StockNewsRepository.java
│   └── ResearchSourceRepository.java
├── po/
│   ├── ResearchReport.java
│   ├── ResearchRun.java
│   ├── ResearchThesis.java
│   ├── ResearchThesisCheck.java
│   ├── StockFinancialHistory.java
│   ├── StockNews.java
│   └── ResearchSource.java
├── service/
│   ├── ResearchSkillRegistry.java
│   ├── ResearchContextAssembler.java
│   ├── ResearchOrchestrator.java
│   ├── ResearchRunService.java
│   ├── ResearchReportService.java
│   ├── ResearchReportValidator.java
│   ├── FinancialRigorService.java
│   ├── ThesisService.java
│   ├── PortfolioReviewService.java
│   ├── NewsPulseService.java
│   └── ResearchAuditService.java
├── task/ResearchTask.java
└── vo/
    ├── ResearchRunRequest.java
    ├── ResearchRunRes.java
    ├── ResearchReportBriefRes.java
    ├── ResearchReportDetailRes.java
    └── ThesisCreateRequest.java
```

资源：

```
StockAI/src/main/resources/prompts/research/
├── common-rules.md                 # 数据纪律、反幻觉、输出规范、免责声明
├── investment-checklist.md         # 六关 + 镜子测试 + 快速否决
├── quality-screen.md               # 7 指标 + 3 豁免
├── thesis-tracker.md               # 论文建档/检查
├── portfolio-review.md             # 组合复盘
├── news-pulse.md                   # 异动归因
├── thesis-drift.md                 # 论文漂移
├── dyp-ask.md                      # 段永平问答（轻量）
├── investment-research.md          # 深度研究主流程
├── role-duan.md                    # 段永平视角
├── role-buffett.md                 # 巴菲特视角
├── role-munger.md                  # 芒格视角
├── role-lile.md                    # 李录视角
└── team-lead.md                    # 综合研判
```

配置（`application.yml` 新增，建议敏感项用环境变量）：

```yaml
research:
  enabled: true
  default-model: ${RESEARCH_MODEL:deepseek-flash}
  max-concurrency: 2
  timeout-seconds: 180
  max-input-chars: 16000
  max-output-chars: 20000
  max-history-years: 10
  data-cutoff-lookback-days: 450
  etf-service-base-url: ${ETF_SERVICE_BASE_URL:http://localhost:8887}
  news:
    enabled: true
    provider: akshare
  audit:
    enabled: false
    sample-rate: 0.15
  disclaimer: "本报告由 AI 与公开数据生成，仅作研究辅助，不构成投资建议。"
```

> 安全提醒：当前 `application.yml` 中存在硬编码模型 Key。融合前务必改为环境变量，并轮换已暴露的 Key；不要把 Key 写进任何 Prompt、报告或前端。

### 8.2 etf-service 新增

```
etf-service/app/
├── api/research.py                 # APIRouter(prefix="/research")
├── services/research_data_service.py
├── datasource/financial_history_source.py
└── tasks/research_task.py
```

在 `app/api/__init__.py` 注册：

```python
from .research import router as research_router
api_router.include_router(research_router)
```

接口建议：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/research/fundamentals/{symbol}?years=10` | 年度/季度财务历史 |
| GET | `/research/quotes/{symbol}?days=250` | 行情序列 |
| GET | `/research/news/{symbol}?days=30` | 公司新闻 |
| GET | `/research/announcements/{symbol}?days=90` | 公告 |
| GET | `/research/industry/{symbol}` | 行业/概念 |
| GET | `/research/universe` | 指数成分/候选池 |
| GET | `/research/health` | 数据完整性检查 |

采集建议（以实际 akshare 版本为准，需做列名防御映射）：

- `stock_financial_abstract` / `stock_financial_analysis_indicator`：核心财务指标。
- `stock_financial_report_sina` / `stock_profit_sheet_by_report_em`：利润表、现金流、资产负债表。
- `stock_individual_info_em`：公司概况、行业、总股本。
- `stock_a_indicator_lg`：PE/PB/股息率历史。
- `stock_news_em`：个股新闻。
- `stock_notice_report` / 巨潮公告：公告。
- `stock_zh_a_hist`：行情。
- `stock_board_concept_cons_em`：概念成分（已有）。

> 注意：akshare 接口列名和可用性随版本变化；所有采集必须做“字段候选匹配 + 失败降级 + 数据不足标注”，不能假设一次成功。

### 8.3 StockAI-Console 新增

```
StockAI-Console/src/components/Research/
├── ResearchCenter.vue        # 主页面（Tab）
├── ChecklistPanel.vue        # 六关 Checklist
├── QualityScreenPanel.vue    # 去劣筛选
├── ReportList.vue            # 报告列表
├── ReportViewer.vue          # Markdown 渲染
├── ThesisPanel.vue           # 论文列表/详情
├── PortfolioReviewPanel.vue  # 组合复盘
└── NewsPulsePanel.vue        # 异动归因
```

路由：

```js
{
  path: '/research',
  name: '投研中心',
  component: ResearchCenter
}
```

依赖新增：`markdown-it`（Markdown 渲染）+ `dompurify`（XSS 清洗）。
样式沿用现有 Element Plus 与 CSS 变量；评分用 ★ 或进度条；报告详情用 Markdown 渲染 + 目录。

### 8.4 现有代码的改动点

| 文件 | 改动 |
|---|---|
| `StockAI/pom.xml` | 无需新增依赖（Spring AI/Mongo 已够）；如需 Markdown 导出可加 `commonmark` |
| `application.yml` | 新增 `research` 配置；模型 Key 改环境变量 |
| `App.vue` | 左侧菜单增加“投研中心” |
| `router/index.js` | 增加 `/research` 路由 |
| `package.json` | 增加 `markdown-it`、`dompurify` |
| `MonitorService` | 增加“异动触发 News Pulse”钩子（P1） |
| `NotificationService` | 复用；新增研究通知类型 |
| `HotspotService` / `ScreeningService` | 提供候选池、行业热度给研究模块 |
| `AnalysisReports` | **不改**；新报告写 `research_reports` |
| `ChatController` | 保留；可选改为调用 Research Orchestrator |

---

## 九、Prompt 迁移规范（最关键）

### 9.1 原 Skill 不能直接作为系统提示词

原 `skills/*.md` 是“给一个拥有 Task/WebSearch/Team 工具的 Agent 的指令”。直接丢给 Spring AI 会导致：

- 模型试图“使用 Task 工具”，但工具不存在 → 幻觉或空转。
- `$ARGUMENTS` 未替换 → 把占位符当公司名。
- 要求联网搜索 → 模型编造来源。

### 9.2 模板化改造规则

| 原内容 | 改造 |
|---|---|
| `对 $ARGUMENTS 执行...` | `对 ${symbol}（${stockName}）执行...` |
| `使用 Task 工具启动后台 Agent` | 删除；改为“以下数据由系统提供” |
| `使用 WebSearch 搜索` | 删除；改为“以下新闻/公告由系统提供；未提供则标注数据不足” |
| `TeamCreate / TaskCreate` | 删除；改为“四个视角分别输出，最后由 Team Lead 综合” |
| `python3 tools/financial_rigor.py ...` | 改为“系统已用工具计算，结果见 `computed` 字段；禁止自行计算” |
| `输出示例` | 保留；作为输出格式约束 |
| `评分标准` | 保留；作为确定性维度 |
| 免责声明 | 统一追加 `common-rules.md` 的固定文本 |

### 9.3 推荐输入结构

把数据装配成紧凑 JSON，放在 user 消息中：

```json
{
  "meta": {
    "symbol": "600519",
    "name": "贵州茅台",
    "market": "A",
    "as_of": "2026-10-30",
    "currency": "CNY",
    "data_confidence": "A",
    "missing_fields": ["interest_coverage"]
  },
  "quote": {"price": 1500, "market_cap": 18800, "pe_ttm": 21.3, "pb": 7.8, "dividend_yield": 0.02},
  "financials": [
    {"year": 2025, "revenue": 1239, "net_profit": 600, "ocf": 650, "capex": 50, "fcf": 600, "roe": 0.31, "gross_margin": 0.91, "net_margin": 0.52, "debt": 50, "cash": 1500, "shares": 1256000000}
  ],
  "industry": {"name": "白酒", "concepts": ["消费", "高端白酒"], "peers": ["000858", "000568"]},
  "news": [{"date": "2026-10-28", "title": "...", "source": "东方财富", "url": "..."}],
  "computed": {
    "pe_ttm": 21.3,
    "roe_10y_avg": 0.31,
    "fcf_5y_sum": 2800,
    "ocf_ni_5y_avg": 1.08,
    "share_dilution_5y": 0.0,
    "three_scenario": {"bull": 2200, "base": 1900, "bear": 1100}
  },
  "sources": [
    {"field": "revenue_2025", "source": "东方财富", "url": "...", "confidence": "high"},
    {"field": "revenue_2025", "source": "巨潮资讯", "url": "...", "confidence": "high"}
  ]
}
```

### 9.4 输出契约

不要只要求 Markdown 自由发挥，至少加三层校验：

1. **必选章节**：如 `## 六关评分`、`## 镜子测试`、`## 快速否决`、`## 数据核验记录`。
2. **必选字段**：每关分数、结论、证据、反面论据。
3. **禁止项**：不得出现未在输入中的数字；不得给出“必涨/稳赚”；不得省略“数据不足”。

建议让模型输出 `Markdown + 末尾 JSON 摘要`，Java 解析 JSON 摘要做落库/展示，Markdown 做正文：

````markdown
## 结论
...

```json
{
  "recommendation": "PASS",
  "total_score": 4.6,
  "scores": {...},
  "missing_fields": [...],
  "citations": [...]
}
```
````

> 注意：Spring AI 的结构化输出（`entity()`）适合纯 JSON 场景；这里正文长、格式自由，推荐“Markdown 正文 + 末尾 JSON 块”，Java 用正则/JSON 解析，失败则降级。

### 9.5 反幻觉规则（写进 `common-rules.md`）

```markdown
1. 只使用输入 JSON 中的数字；输入没有的数字不得生成。
2. 每个关键数字必须给出 source 字段；没有来源的标“数据不足”。
3. 区分“事实”和“观点”；观点必须标注。
4. 不得用“最近”“大约”“可能”替代精确值。
5. 所有计算必须使用 computed 字段；禁止心算。
6. 输出必须包含免责声明。
7. 信息丰富度 A/B/C 评级必须写在开头。
8. 必须给出至少 2 条反面论据/失败情景。
```

---

## 十、金融计算工具移植清单

### 10.1 建议移植到 Java（P0–P2）

| Python 函数 | Java 方法 | 输入 | 输出 | 优先级 |
|---|---|---|---|---|
| `verify_market_cap` | `verifyMarketCap` | price, shares, reported, currency | calculated, deviation, pass | P0 |
| `verify_valuation` | `verifyValuation` | price, eps, bvps, fcf/share, dividend | PE/PB/ROE/FCF Yield | P0 |
| `cross_validate` | `crossValidate` | field, source values, tolerance | per-source deviation, pass/warn/fail | P0 |
| `three_scenario` | `threeScenario` | price, eps, shares, growth/PE scenarios | target prices | P1 |
| `calc` | `calculate` | expression | BigDecimal result | P1 |
| `terminal_value.py` 核心 | `terminalValue` | roic, g, r, payout | PE, IRR | P2 |
| `report_audit.py` 简化 | `auditReport` | report markdown, sample rate | sampled points, verdict | P2 |

实现要点：

- 使用 `BigDecimal` + `MathContext.DECIMAL128`；禁止 `double`。
- 除零、负值、单位不一致要返回结构化告警，不要抛异常中断流程。
- 输出对象而不是控制台文本，便于报告注入和前端展示。
- 单测参考 `tests/test_financial_rigor.py` 的用例（GBK 控制台问题在 Java 不存在，但精度/边界用例可复用）。

### 10.2 继续留在 Python / 不移植

| 工具 | 原因 |
|---|---|
| `ashare_data.py` | 已有 etf-service 数据源，避免重复 |
| `twstock_data.py` | 台股，当前不需要 |
| `xueqiu_scraper.py` | Playwright + 登录态，合规/维护成本高 |
| `stock_screener.py` / `momentum_backtest*.py` | 美股动量策略，与当前 A 股选股框架不同 |
| `reports_index.py` / `build_site.py` | 报告站点，P3 可选 |
| `wechat_archive.py` | 公众号归档，与产品无关 |
| `morningstar_fair_value.py` | 依赖特定数据源，P3 可选 |

---

## 十一、数据采集补齐方案

### 11.1 采集优先级

| 数据 | 用途 | 优先级 | 采集方 |
|---|---|---|---|
| 10 年 ROE、毛利率、净利率 | 去劣筛选、好生意 | P0 | etf-service |
| 5 年 OCF、资本开支、FCF | 去劣筛选、现金流质量 | P0 | etf-service |
| 利息支出、EBIT、有息负债 | 利息覆盖倍数 | P0 | etf-service |
| 5 年总股本 | 稀释检查 | P0 | etf-service |
| EPS、BVPS、股息 | 估值验算 | P0 | etf-service |
| 公告/财报原文 | 财报精读 | P1 | etf-service + 手动上传 |
| 个股新闻 | 异动归因 | P1 | etf-service |
| 行业/政策新闻 | 行业研究 | P2 | 搜索 API/手动 |
| 美股/港股数据 | 跨市场 | P3 | 独立数据源 |

### 11.2 数据质量规则

1. **两源交叉验证**：至少 2 个独立来源；误差 ≤1% 通过，1–5% 警告，>5% 失败。
2. **单位显式**：元/亿元/万亿、CNY/HKD/USD 必须写入字段/注释。
3. **缺失不猜**：采集失败写 `null` + `missing_fields`，Prompt 明确“数据不足”。
4. **数据截止日**：每份报告必须写 `data_cutoff`。
5. **可追溯**：每个关键数字写 `research_sources`。

### 11.3 调度建议

| 任务 | 时间 | 说明 |
|---|---|---|
| 财务历史全量/增量 | 每日 02:00 | 年报/季报更新后补齐 |
| 公告/新闻 | 每日 08:00、16:00 | 盘前/盘后 |
| 行情 | 现有任务 | 复用 |
| 论文检查 | 每周一 09:00 | 或季报发布后触发 |
| 组合复盘 | 每周五 18:00 | 复用周报链路 |
| 异动归因 | 收盘后 | 由 `MonitorService` 触发 |

---

## 十二、验证与验收

### 12.1 单元测试

| 测试 | 覆盖 |
|---|---|
| `FinancialRigorServiceTest` | 市值、估值、交叉验证、三情景、边界值、单位 |
| `ResearchSkillRegistryTest` | 模板加载、占位符替换、版本号 |
| `ResearchReportValidatorTest` | 必选章节、JSON 摘要、降级 |
| `ThesisServiceTest` | 建档、假设、红线、检查状态 |
| `PortfolioReviewServiceTest` | 集中度、行业占比、相关性 |
| `NewsPulseServiceTest` | 异动阈值、归因类型、通知 |

沿用现有 `Mockito` mock `ChatClient` 的测试范式，避免测试依赖真实模型。

### 12.2 集成/端到端

1. 启动 MongoDB + etf-service + StockAI + Console。
2. `POST /research/run` 研究 `600519`，轮询状态到 `SUCCEEDED`。
3. 前端 `/research` 看到报告，详情 Markdown 正常渲染。
4. 检查 `research_reports`、`research_sources`、`stock_financial_history` 数据。
5. 断网/模型失败场景：报告 `status=FALLBACK`，仍有确定性表格。
6. 持仓股触发 `news-pulse`，站内通知出现。

### 12.3 质量指标

| 指标 | 目标 |
|---|---|
| 单股 Checklist 耗时 | < 30s（P95） |
| 深度研究报告耗时 | < 5min（P95） |
| 数字来源覆盖率 | ≥ 90% |
| 报告结构校验通过率 | ≥ 95% |
| 单份报告成本 | 可配置上限，超限中止 |
| 数据缺失标注率 | 100%（缺失必须标注） |

---

## 十三、风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 数据不足导致报告空泛 | 高 | P0 先补财务历史；缺失明确标注；A/B/C 信息丰富度 |
| 模型编造数字/来源 | 高 | 只喂输入 JSON + computed；来源字段校验；抽检 |
| Token 成本/延迟 | 中 | 异步任务、缓存、按深度分级、预算上限 |
| DeepSeek 工具调用不稳定 | 中 | 优先“预取数据 + 单次生成”，不依赖 tool calling |
| 多 Agent 结果拼接感 | 中 | 角色 Prompt 独立 + Team Lead 强制写分歧 |
| akshare 接口变动 | 中 | 字段候选映射 + 失败降级 + 健康检查 |
| 报告合规风险 | 中 | 固定免责声明；不提供买卖指令；标注研究用途 |
| 凭据泄露 | 高 | Key 改环境变量；轮换；不写日志/报告 |
| License | 低 | MIT，保留版权声明；不复制报告/图片 |

---

## 十四、需要确认的 5 个决策

1. **市场范围**：先做 A 股，还是同时要港股/美股？（建议先 A 股，当前数据与 UI 都围绕 A 股）
2. **联网检索**：是否接受接入搜索 API（Tavily/Bocha/Serper）？还是先用 akshare 新闻/公告 + 手动粘贴？（建议先 akshare，P2 再评估付费搜索）
3. **深度研究入口**：产品内跑，还是继续用 Claude Code/Codex 离线跑后导入？（建议 P0/P1 产品内，P2 重型报告可离线）
4. **是否新增 Python 服务**：接受“Java 原生 + etf-service 扩展”，还是更愿意独立 `research-service`？（建议前者）
5. **单用户/多用户**：研究模块是否按 `userId` 隔离？现有 API 都有 `userId`，建议直接隔离。

---

## 十五、两周 P0 backlog（可直接执行）

### 第 1 周：数据与计算

- [ ] 复制 `skills/investment-checklist.md`、`quality-screen.md`、`financial-data.md` 到 `StockAI/src/main/resources/prompts/research/`，保留 MIT 版权说明。
- [ ] 新增 `ResearchProperties` 与 `application.yml` 配置；模型 Key 改环境变量。
- [ ] 新增 `StockFinancialHistory` PO + Repository + 唯一索引。
- [ ] etf-service 新增 `/research/fundamentals/{symbol}`，从 akshare 采集 10 年/可得年度数据，写入 `stock_financial_history`。
- [ ] 新增 `FinancialRigorService`：市值、估值、交叉验证、三情景、精确计算 + 单测。
- [ ] 新增 `ResearchContextAssembler`：把行情、财务历史、行业、新闻装配为紧凑 JSON。
- [ ] 新增 `ResearchSkillRegistry`：加载模板、替换 `$ARGUMENTS`、附公共规则。

### 第 2 周：闭环与前端

- [ ] 新增 `ResearchReport`、`ResearchRun` PO/Repository。
- [ ] 新增 `ResearchOrchestrator`：单 Agent 生成 Checklist / Quality Screen。
- [ ] 新增 `ResearchReportValidator`：章节/字段/JSON 摘要校验 + 降级。
- [ ] 新增 `ResearchController`：`POST /research/run`、`GET /research/run/{id}`、`POST /research/reports`、`GET /research/report/{id}`。
- [ ] 新增 `ResearchTask`：可选定时检查数据完整性。
- [ ] 前端新增 `/research` 路由与 `ResearchCenter.vue`、`ReportList.vue`、`ReportViewer.vue`。
- [ ] 新增 `markdown-it` + `dompurify`，报告正文渲染。
- [ ] 用 `600519`、`000858`、`601318` 跑通端到端，检查来源/缺失标注/降级。
- [ ] 补充 `README` 或 `docs` 使用说明。

### P0 完成定义（DoD）

1. 三个标的至少各生成 1 份 Checklist 和 1 份 Quality Screen 报告。
2. 每份报告可追溯到数据来源，缺失字段明确标注。
3. 前端可发起、查看、筛选报告；模型失败有降级。
4. 单测通过，`./mvnw test` 不因新增模块失败。
5. 配置中无明文密钥。

---

## 附录 A：与现有代码的复用点

| 复用点 | 位置 | 用法 |
|---|---|---|
| `ChatClient` | `CommonConfiguration` | 注入 ResearchOrchestrator |
| Prompt 资源加载 | `@Value("classpath:/prompts/...") Resource` | 与交易计划一致 |
| 结构校验 + 降级 | `TradePlanSummaryService` | 复制模式 |
| `Result<T>` | `vo/Result` | 所有 Research API |
| MongoTemplate | 现有 | 聚合/分页 |
| 通知 | `NotificationService`、`FeishuNotificationSender` | 论文红线/异动 |
| 定时任务 | `@Scheduled` | 研究任务 |
| 测试 mock | `ScreeningReportServiceTest` | ChatClient mock |
| 行业/候选池 | `ScreeningService`、`HotspotService` | 行业研究/漏斗 |

## 附录 B：参考文件（ai-berkshire）

| 文件 | 用途 |
|---|---|
| `skills/investment-checklist.md` | P0 六关 Checklist |
| `skills/quality-screen.md` | P0 去劣筛选 |
| `skills/financial-data.md` | 数据规范 |
| `skills/thesis-tracker.md` | P1 论文跟踪 |
| `skills/portfolio-review.md` | P1 组合复盘 |
| `skills/news-pulse.md` | P1 异动归因 |
| `skills/investment-research.md` | P2 深度研究 |
| `skills/investment-team.md` | P2 多 Agent |
| `skills/earnings-review.md` | P2 财报精读 |
| `tools/financial_rigor.py` | P0 精确计算 |
| `tools/terminal_value.py` | P2 终值/IRR |
| `tools/report_audit.py` | P2 报告抽检 |
| `LICENSE` | MIT，保留版权声明 |

## 附录 C：常用验证命令

```bash
# 后端测试
cd StockAI
./mvnw test -Dtest=FinancialRigorServiceTest
./mvnw test

# etf-service 数据接口
curl "http://localhost:8887/research/fundamentals/600519?years=10"
curl "http://localhost:8887/research/health"

# 发起研究
curl -X POST http://localhost:8888/research/run \
  -H 'Content-Type: application/json' \
  -d '{"userId":"6928fca14f206ada1b7a9269","symbol":"600519","skill":"investment-checklist"}'

# 前端
cd StockAI-Console
npm install
npm run dev
```

---

## 结语

这套融合的本质不是“把 ai-berkshire 的代码搬过来”，而是把它的 **投研纪律** 变成 StockAI 的 **产品能力**：

- 用 `quality-screen` 把候选池变干净；
- 用 `investment-checklist` 把“能不能买”变成可复用的六关表；
- 用 `thesis-tracker` 把“买入后祈祷”变成可跟踪的论文；
- 用 `portfolio-review` 把“看个股”升级为“看组合”；
- 用 `financial_rigor` 把“AI 心算”变成可复算的数字。

先做 P0 的一个标的闭环，再逐步扩展。这样每一步都有可验证的产物，不会陷入“接了一大堆 Prompt 却用不起来”的常见陷阱。
