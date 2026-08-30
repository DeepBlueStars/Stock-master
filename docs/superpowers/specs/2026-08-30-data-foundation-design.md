---
comet_change: data-foundation
role: technical-design
canonical_spec: openspec
archived-with: 2026-08-30-data-foundation
status: final
---

# data-foundation 技术设计

## Context

StockAI 目前通过 `market_quotes`（个股最新收盘价快照）与 `etf_daily_quotes`（ETF 日线）提供行情，数据采集在 `etf-service`（Python/FastAPI + akshare + APScheduler）中完成，统一写入 MongoDB（`tradingagents` 库）。本 change 补齐指数历史、个股历史K线、财务指标与行业/概念分类，作为 market-regime / stock-screener / trade-plan 的数据地基。

## Goals / Non-Goals

**Goals:**
- 建立多数据源接入（akshare 主源 + 备用免费源）与主备切换
- 各数据源数据统一转换为标准格式后存储
- 新建集合存储，禁止改造已有集合

**Non-Goals:**
- 不实现任何分析/决策逻辑
- 不接付费数据源（仅预留扩展点）
- 不做全A股覆盖

## 架构

```
etf-service/app/
├── datasource/              # 数据源适配器层
│   ├── base.py              # DataSource 抽象接口
│   ├── akshare_source.py    # 主源
│   ├── baostock_source.py   # 备用（历史K线/财务）
│   ├── tushare_source.py    # 备用（财务/日线，token 从 .env 读）
│   └── eastmoney_sina.py    # 备用（行情快照）
├── normalize/               # 统一格式层
│   └── schema.py            # 标准数据模型 + 字段映射/清洗
├── services/                # 采集服务（指数/个股K线/财务/行业概念）
├── tasks/                   # 定时任务（APScheduler）
└── database/mongo.py        # 新增集合引用
```

## 数据流

```
定时任务 → 采集服务 → 数据源适配器(主源→备用源链) → 统一格式(schema) → MongoDB(upsert 去重)
```

## 关键决策

### 决策1：数据源适配器接口（adapter 模式）

每个数据源实现统一 `DataSource` 接口，返回统一标准格式：

- `fetch_index_daily(code)` / `fetch_stock_daily(code)` → 行情
- `fetch_fundamentals(code)` → 财务
- `fetch_industry_concept(code)` → 行业/概念

主备切换顺序：`akshare → baostock → tushare → 东财/新浪`，全部失败记录日志并保留旧数据。

**备选**：单一 fetch 函数内 if-else 分支 —— 实现简单但扩展性与可测试性差，故弃用。

### 决策2：统一标准格式

所有数据源数据在适配器内做字段映射与清洗后，输出统一标准格式：

- 行情：`symbol, trade_date, open, high, low, close, volume, amount, data_source, created_at, updated_at`
- 财务：`symbol, report_date, pe, pb, roe, revenue_yoy, profit_yoy, gross_margin, data_source, updated_at`
- 行业/概念：`symbol, industry, concepts[], data_source, updated_at`

### 决策3：存储模型（新建集合）

| 集合 | 去重键 |
|------|--------|
| `index_daily_quotes` | index_code + trade_date |
| `stock_daily_kline` | symbol + trade_date |
| `stock_fundamentals` | symbol + report_date |
| `stock_industry_concept` | symbol |
| `index_constituents` | index_code + symbol |

约束：MUST 新建集合，禁止改造 `market_quotes`、`etf_daily_quotes`、`etf_basic_info` 等已有集合。

### 决策4：密钥配置

数据源密钥/token（如 tushare token）通过环境变量 + `.env` 文件用 `os.getenv` 读取，参考 etf-service 现有模式，不硬编码。

## 调度策略

- 指数/个股日线：交易日 15:30 后增量更新
- 成分股列表：每月更新（跟随指数成分股调整周期）
- 财务指标：财报季（4/8/10 月底）更新，检测到新财报则拉取
- 行业/概念：每日更新

## 错误处理与降级

- 单数据源失败 → 按顺序切换备用源
- 全部失败 → 记录日志、保留旧数据、不影响其他采集任务
- 数据缺失 → 字段降级为默认值并标注 data_source

## 测试策略

- 单元测试：字段映射/清洗、统一格式转换、去重逻辑
- 集成测试：mock 各数据源返回，验证主备自动切换与格式统一
- 冒烟测试：真实拉取单只股票/指数验证全链路

## 风险与权衡

- [免费数据源不稳定/限流] → 多源备份 + 重试 + 降级
- [字段口径不一致] → 统一格式层屏蔽上游差异
- [财务数据拉取慢] → 聚焦优质池约 800 只，缩小范围
