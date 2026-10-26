---
comet_change: stock-screener
role: technical-design
canonical_spec: openspec
archived-with: 2026-10-26-stock-screener
status: final
---

# stock-screener 选股引擎技术设计

## Context

data-foundation（已归档 change）已由 etf-service 将优质池（沪深300+中证500，约 800 只）数据写入 MongoDB `tradingagents` 库：

- `stock_fundamentals`（symbol + report_date 去重）：pe、pb、roe、revenue_yoy、profit_yoy、gross_margin
- `stock_industry_concept`（symbol 去重）：industry、concepts(List)
- `stock_daily_kline`（symbol + trade_date 去重）：OHLC、volume、amount
- `index_constituents`（index_code + symbol 去重）：优质池成分股

Java 侧（StockAI）目前无上述四个集合的 PO/Repository，选股/热点链路为零。本设计基于 open 阶段 design.md 的三项决策（四因子加权模型、量价聚合+质量过滤区分炒作、复用 ChatClient 做总结）做深度技术细化。

## Goals / Non-Goals

**Goals：**
- 优质池内可复现的多因子打分（估值/成长/质量/动量）
- 多风格候选榜（价值型/成长型/均衡型各 Top 20）+ 长期价值评级
- 热点方向识别（行业+概念，单日+5日双窗口）+ 炒作风险标注
- AI 综合报告（失败降级纯量化文本）
- 每交易日定时更新 + 前端展示页

**Non-Goals：**（继承 open 阶段）
- 不做全 A 股覆盖、盘中实时选股、买卖时点推荐
- 不做权重自动优化（仅提供全量快照供后续离线回测）

## 架构与模块划分

### 后端 StockAI（Java/Spring Boot）

```
com.xian.stockai
├── po/
│   ├── StockFundamentals.java        # 只读映射 stock_fundamentals
│   ├── StockIndustryConcept.java     # 只读映射 stock_industry_concept
│   ├── StockDailyKline.java          # 只读映射 stock_daily_kline
│   ├── IndexConstituents.java        # 只读映射 index_constituents
│   ├── ScreeningScoreRecord.java     # 新集合 screening_scores（全量快照）
│   ├── ScreeningResult.java          # 新集合 screening_results（榜单+AI报告）
│   └── HotspotResult.java            # 新集合 hotspot_results
├── dao/
│   ├── StockFundamentalsRepository / StockIndustryConceptRepository
│   ├── StockDailyKlineRepository / IndexConstituentsRepository
│   ├── ScreeningScoreRecordRepository / ScreeningResultRepository
│   └── HotspotResultRepository
├── service/
│   ├── ScreeningService.java         # 因子计算、风格榜单、长期评级、快照与榜单入库
│   ├── HotspotService.java           # 行业/概念聚合、热度得分、炒作标注
│   └── ScreeningReportService.java   # AI 综合报告 + 降级
├── task/
│   └── ScreeningTask.java            # cron 编排：因子→榜单→热点→AI 报告→入库
└── controller/
    ├── ScreeningController.java      # /screening/latest|history|run
    └── HotspotController.java        # /hotspot/latest
```

### 前端 StockAI-Console（Vue 3）

- 新增 `src/components/StockScreener.vue`（参照 `MarketRegime.vue` 风格）
- `src/router/index.js` 新增 `/screener` 路由；`src/App.vue` 左侧菜单新增「选股」

## 数据流

```
etf-service 采集（已有）
   ↓ MongoDB: stock_fundamentals / stock_industry_concept / stock_daily_kline / index_constituents
ScreeningTask（交易日 18:00）
   ↓ 1) 读取优质池 + 财务 + K线 → 因子百分位计算 → 全量快照 screening_scores
   ↓ 2) 三套风格权重派生 Top 20 榜单 + 长期评级
   ↓ 3) HotspotService 聚合行业/概念量价 → 热度得分 + 炒作标注 → hotspot_results
   ↓ 4) ScreeningReportService 注入榜单+热点 → ChatClient 生成报告（失败降级）
   ↓ screening_results（榜单 + ai_report）
前端 StockScreener.vue ← /screening/latest、/hotspot/latest
```

## 因子算法

### 因子定义与合成

| 因子 | 子指标（来源） | 方向 | 子权重 |
|---|---|---|---|
| 估值 valuation | PE、PB（stock_fundamentals） | 越低越好（反向百分位）；PE≤0 得 0 分 | 各 50% |
| 成长 growth | revenue_yoy、profit_yoy | 正向百分位 | 各 50% |
| 质量 quality | roe、gross_margin | 正向百分位 | 60% / 40% |
| 动量 momentum | 20 日收益率（stock_daily_kline 收盘价） | 正向百分位 | 100% |

- 归一化：池内横截面百分位排名，映射到 0~100（整数），完全由当日数据决定，可复现
- 财务指标取该股最近可用 report_date（限 1 年内），结果标注 `data_date`；无可用数据 → 对应因子 null
- K 线不足 20 个交易日 → 动量因子 null

### 综合评分与风格权重

权重外置 `application.yml`（`screening.weights.*`），默认：

| 风格 | 估值 | 成长 | 质量 | 动量 |
|---|---|---|---|---|
| 价值型 value | 40% | 20% | 30% | 10% |
| 成长型 growth | 10% | 40% | 20% | 30% |
| 均衡型 balanced | 25% | 25% | 30% | 20% |

- 某因子 null → 综合分按剩余因子权重重新归一化
- 缺失因子 ≥2 → 综合分无效，不入榜（快照中保留并标记 data_incomplete）

### 长期价值评级

质量分 × 60% + 成长分 × 40%（任一缺失则用另一方 100%）：

- ≥65 → 高；45~65 → 中；<45 → 低；两者均缺失 → 未知

### 入选理由

模板化量化文本，例如：「ROE 池内前 15%，PE 低于 82% 成分股，20 日涨幅池内前 30%」。不使用 AI 生成，保证可复现。

## 热点识别算法

- 维度：行业（industry）与概念（concepts 展开）分别聚合；成分股 <3 只的方向过滤
- 每个方向聚合池内成分股：单日平均涨幅、单日成交额占全池比例、涨停家数（单日涨幅 ≥9.9%，仅优质池口径，结果标注）、5 日平均累计涨幅、5 日平均成交额占比
- 热度得分（0~100）= 5日热度 60% + 单日热度 40%；窗口内 = 涨幅分 40% + 成交额占比分 40% + 涨停家数分 20%（各指标跨方向百分位归一）
- 输出：行业 Top 10 + 概念 Top 10，按热度降序，附代表股（热度贡献最大的 3 只）

### 炒作风险标注

对每个热点方向取成分股质量因子得分中位数：

- 中位数 <40 且热度 ≥60 → 「纯炒作风险高」
- 中位数 ≥60 → 「有基本面支撑」
- 其余 → 「中性」

标注同时输出依据（质量中位数、热度得分），满足 spec「标注炒作风险及其依据」。

## 数据模型

均每日一文档，`trade_date` 唯一索引（upsert 去重）。

### screening_scores（全量快照）

| 字段 | 说明 |
|---|---|
| trade_date / generated_at | 交易日 / 生成时间 |
| stocks[] | 全池数组：symbol、name、valuation/growth/quality/momentum 因子分、pe/pb/roe 等子指标原始值、momentum_return、long_term_rating、data_date、data_incomplete |

### screening_results（榜单 + AI 报告）

| 字段 | 说明 |
|---|---|
| trade_date / generated_at | 交易日 / 生成时间 |
| boards | value/growth/balanced 三个数组，各 Top 20：symbol、name、total_score、四因子分、long_term_rating、reason |
| ai_report / ai_report_status | 报告文本 / success 或 fallback |

### hotspot_results

| 字段 | 说明 |
|---|---|
| trade_date / generated_at | 交易日 / 生成时间 |
| industries[] / concepts[] | 各 Top 10：name、hot_score、单日/5日指标、stock_count、limit_up_count、hype_label（SUPPORTED/NEUTRAL/HYPE_RISK）、hype_basis、top_stocks[] |

## REST API（POST + Result\<T\>）

| 接口 | 说明 |
|---|---|
| /screening/latest | 最新榜单 + AI 报告；无数据时 Result.fail |
| /screening/history | 请求体 page/size，按 trade_date 倒序分页返回榜单摘要 |
| /screening/run | 手动触发一次全流程（补数/验证用），幂等 upsert 当日结果 |
| /hotspot/latest | 最新热点结果 |

## AI 总结

- 复用 `CommonConfiguration` 的 ChatClient，`.prompt().user(prompt).call().content()` 同步调用（同 `MarketRegimeService` 范式）
- prompt 注入：三风格榜 Top 20 精简表、热点 Top 10 及炒作标注、评分统计（池均分等）
- 失败 catch → `buildFallbackReport()` 纯量化文本，`ai_report_status=fallback`；榜单与热点不受影响

## 定时任务

- `ScreeningTask`：`@Scheduled(cron = "${screening.cron:0 0 18 * * MON-FRI}")`
- 前置校验：当日 `stock_daily_kline` 有数据才执行，否则记日志跳过
- 任务类只做编排，顺序：因子快照 → 榜单 → 热点 → AI 报告 → 入库；任一步骤异常记日志，不阻断已完成的步骤产物入库

## 错误处理与降级

- 财务数据缺失/滞后：最近 report_date + data_date 标注；因子 null 重归一化
- AI 失败：降级纯量化文本
- 数据未就绪：任务跳过并记日志，保留旧数据
- API 无数据：`Result.fail("暂无选股结果")`

## 测试策略

- **单元测试**（JUnit + Mockito，`mvn test`）：
  - 百分位方向（高 ROE 得高分、低 PE 得高分）、PE≤0 得 0 分
  - 因子缺失重归一化、缺失 ≥2 不入榜
  - 三风格权重合成、长期评级阈值边界（65/45）
  - 热点方向聚合、成分股 <3 过滤、炒作标注三档阈值
  - AI 调用失败走 fallback
- **集成验证**：`mvn test` 全绿；前端 `npm run build` 通过；手动触发 `/screening/run` 端到端核验

## 风险与权衡

- [因子权重主观] → 权重外置 yml；全量快照支持离线回测调参（后续可加回测工具）
- [财务数据滞后/缺失] → 最近可用数据 + data_date 标注；因子级降级
- [涨停家数仅优质池口径] → 与全市场热点存在偏差，结果中标注口径
- [概念股重叠导致方向重复] → 行业/概念分榜展示，接受重叠
- [快照存储增长] → 每日 3 个文档（快照约 200KB），可接受；后续可加 TTL 归档
- [AI 不可用] → 降级纯量化文本，核心量化结果不受影响
