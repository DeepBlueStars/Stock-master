## Why

当前 StockAI 仅存储个股最新收盘价（`market_quotes`）与 ETF 日线行情，缺少宽基指数历史行情、个股历史K线、财务指标与行业/概念分类数据。这导致无法支撑「市场温度计」「选股引擎」「交易计划引擎」三大决策能力。在实现任何决策逻辑之前，必须先补齐数据地基。

## What Changes

- 新增宽基指数历史日线行情采集与存储（上证指数、沪深300、中证500、创业板指等主要指数）
- 新增优质池（沪深300 + 中证500 成分股）个股历史日线K线采集与存储
- 新增个股财务指标采集与存储（估值/成长/质量因子所需，如 PE、PB、ROE、营收增速、净利润增速、毛利率）
- 新增个股行业与概念分类采集与存储
- 引入多数据源接入：akshare 为主源，baostock、tushare、东方财富、新浪财经等免费源为备用源，主源失败时自动切换备用源
- 引入数据统一格式处理：各数据源数据统一转换为标准格式（OHLCV + 元数据）后存储，供数据库与分析使用
- 预留付费数据源（Tushare Pro 等）扩展点
- 新增定时采集任务，保证数据按日更新，采集失败时优雅降级不崩溃

## Capabilities

### New Capabilities

- `data-ingestion`: 多数据源接入（akshare + 备用免费源）与数据统一格式处理
- `index-history-data`: 宽基指数历史日线行情数据的采集与存储
- `stock-history-data`: 优质池个股历史日线K线数据的采集与存储
- `stock-fundamental-data`: 个股财务指标与行业/概念分类数据的采集与存储

### Modified Capabilities

<!-- 无 -->

## Impact

- 后端：新增数据采集服务、数据源抽象层与定时任务，新增 MongoDB 集合（指数行情、个股历史K线、财务指标、行业/概念）
- 数据采集的承载位置待设计阶段确定：扩展现有 `etf-service`（Python/FastAPI + akshare + APScheduler）或新建独立数据服务
- 依赖：akshare 免费数据源、现有 MongoDB（`tradingagents` 库）
- 为后续 `market-regime`、`stock-screener`、`trade-plan` 三个 change 提供数据基础
