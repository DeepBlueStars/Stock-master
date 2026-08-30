## Context

StockAI 当前通过 `market_quotes`（个股最新收盘价）与 `etf_daily_quotes`（ETF 日线）提供行情，数据采集在 `etf-service`（Python/FastAPI + akshare + APScheduler）中完成，统一写入 MongoDB（`tradingagents` 库）。本 change 需补齐指数历史、个股历史K线、财务指标与行业/概念分类数据。

## Goals / Non-Goals

**Goals:**
- 建立统一的数据源抽象，先落地 akshare 实现，预留付费源扩展点
- 覆盖优质池（沪深300+中证500）与主要宽基指数的历史行情 + 财务/分类数据
- 采集任务可定时运行、失败可降级

**Non-Goals:**
- 不实现任何分析/决策逻辑（属于后续 market-regime / stock-screener / trade-plan）
- 不接付费数据源（仅预留接口）
- 不做全A股覆盖

## Decisions

### 决策1：数据采集承载位置
- **选择**：沿用 `etf-service`（Python + akshare）作为数据采集服务，新增指数/个股/财务采集模块
- **理由**：akshare 为 Python 库，Java 后端无法直接调用；复用现有 MongoDB 写入与 APScheduler 调度模式，成本最低
- **备选**：新建独立 Python 数据服务 —— 隔离更干净但增加部署复杂度，当前单机个人工具收益不明显

### 决策2：多数据源接入
- **选择**：定义数据源抽象接口（指数行情/个股K线/财务指标/行业概念），akshare 为主源，baostock（历史K线/财务）、tushare（财务/日线）、东方财富、新浪财经（行情快照）为备用源；主源失败时自动降级到备用源
- **理由**：单一免费数据源不稳定，多源备份提高数据可用性；接口抽象便于日后升级付费源
- **备选**：仅用 akshare —— 实现简单但存在单点故障风险

### 决策3：数据统一格式
- **选择**：所有数据源数据在入库前统一转换为标准格式（标的代码、交易日期、开盘价、最高价、最低价、收盘价、成交量、成交额、数据来源、采集时间等标准字段），原始字段差异在数据源适配器内做映射与清洗
- **理由**：屏蔽各数据源字段口径差异，上层分析只依赖统一格式，切换数据源不影响消费方

### 决策4：存储模型
- **选择**：新增 MongoDB 集合 `index_daily_quotes`、`stock_daily_kline`、`stock_fundamentals`、`stock_industry_concept`，以及优质池成分股集合 `index_constituents`
- **理由**：与现有 `etf_daily_quotes` 的「按日一行」模式一致，便于按代码+交易日查询与去重
- **约束**：MUST 新建集合存储，禁止改造已有集合（如 `market_quotes`、`etf_daily_quotes`、`etf_basic_info`），避免影响现有功能

### 决策5：数据源密钥配置
- **选择**：各数据源所需密钥或 token（如 tushare token）通过环境变量与 `.env` 文件配置（`os.getenv` 读取），参考 etf-service 现有配置模式
- **理由**：与 etf-service 现有配置方式一致，密钥不进版本库，便于切换与升级数据源

## Risks / Trade-offs

- [单一数据源不稳定/限流] → 多数据源备份切换 + 重试与降级，失败保留旧数据
- [不同数据源字段口径不一致] → 统一格式层在适配器内做字段映射与清洗，屏蔽上游差异
- [全市场财务数据拉取慢] → 先聚焦优质池约800只，缩小拉取范围
