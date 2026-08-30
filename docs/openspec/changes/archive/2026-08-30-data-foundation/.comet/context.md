# Comet Design Handoff

- Change: data-foundation
- Phase: design
- Mode: compact
- Context hash: 643064530631bcac1694ea85d9c13b809b034030ddc9e61abb01a44542dcdf49

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/data-foundation/proposal.md

- Source: docs/openspec/changes/data-foundation/proposal.md
- Lines: 1-34
- SHA256: ff864672d855c6fff6533408c1f7d6104ad95efeaee0caa378290fe1a9cec369

```md
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

```

## docs/openspec/changes/data-foundation/design.md

- Source: docs/openspec/changes/data-foundation/design.md
- Lines: 1-46
- SHA256: 8a29ac79fdf09d9365b672781d0ea8dc96cfdb74f37ac8bb777371a6bb12ddac

```md
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

```

## docs/openspec/changes/data-foundation/tasks.md

- Source: docs/openspec/changes/data-foundation/tasks.md
- Lines: 1-35
- SHA256: 066afe694887b1498e0b30d1b6fc7775687c0c31dbe4c2549b8fac30761896f9

```md
## 1. 数据源抽象与统一格式

- [ ] 1.1 定义数据源抽象接口（指数行情/个股K线/财务指标/行业概念）与统一标准格式模型，验证接口与格式模型定义完整
- [ ] 1.2 实现 akshare 主数据源适配器，验证输出统一标准格式数据
- [ ] 1.3 实现 baostock 备用数据源适配器（历史K线/财务），验证输出统一标准格式数据
- [ ] 1.4 实现 tushare 备用数据源适配器（财务/日线），验证 token 从环境变量读取且输出统一标准格式数据
- [ ] 1.5 实现东方财富/新浪财经备用行情适配器，验证输出统一标准格式数据
- [ ] 1.6 实现主备数据源自动切换与字段映射清洗，验证主源失败时自动切换备用源且格式统一
- [ ] 1.7 在 etf-service 中新增 MongoDB 集合模型（index_daily_quotes、stock_daily_kline、stock_fundamentals、stock_industry_concept、index_constituents），验证写入与去重方法可用且不改动已有集合

## 2. 优质池成分股维护

- [ ] 2.1 实现沪深300与中证500成分股拉取与存储，验证可获取并写入约800只成分股
- [ ] 2.2 实现成分股列表定时更新任务，验证重复运行时成分股不重复写入

## 3. 指数历史行情采集

- [ ] 3.1 实现主要宽基指数历史日线行情采集（上证/沪深300/中证500/创业板指等），验证写入 index_daily_quotes 且 OHLC/量额字段完整
- [ ] 3.2 实现指数行情按日更新任务与去重，验证同一指数同一交易日仅一条记录

## 4. 个股历史K线采集

- [ ] 4.1 实现优质池个股历史日线K线采集，验证写入 stock_daily_kline 且 OHLC/量额字段完整
- [ ] 4.2 实现个股K线按日增量更新与去重，验证仅更新缺失交易日数据

## 5. 财务指标与行业/概念采集

- [ ] 5.1 实现优质池个股财务指标采集（PE/PB/ROE/营收增速/净利增速/毛利率），验证写入 stock_fundamentals
- [ ] 5.2 实现个股行业与概念分类采集，验证写入 stock_industry_concept
- [ ] 5.3 实现财务与分类数据定期更新任务，验证财务指标按财报周期更新、分类每日更新

## 6. 降级与调度集成

- [ ] 6.1 为各采集任务增加失败重试与降级逻辑，验证数据源异常时记录日志且保留旧数据、服务不崩溃
- [ ] 6.2 将各采集任务接入 APScheduler 调度，验证任务按配置周期执行

```

## docs/openspec/changes/data-foundation/specs/data-ingestion/spec.md

- Source: docs/openspec/changes/data-foundation/specs/data-ingestion/spec.md
- Lines: 1-50
- SHA256: 2aee36187a7eb864095199eb6b811f20f3171cba01e4728bcbce3100c17126a1

```md
## Purpose

多数据源接入与数据统一格式能力：以 akshare 为主数据源、baostock、tushare 与东方财富/新浪财经等免费源为备用源，采集失败时自动切换备用源，并将各数据源数据统一转换为标准格式后存储到新建集合，供后续分析与展示使用。

## ADDED Requirements

### Requirement: 支持主备多数据源接入

系统 SHALL 支持多个免费数据源接入，akshare 为主源，baostock、tushare、东方财富、新浪财经等为备用源；主源失败时 MUST 自动切换到备用源继续采集。

#### Scenario: 主源失败自动切换备用源

- **WHEN** akshare 主源请求失败或返回空数据
- **THEN** 系统自动切换到备用数据源继续采集，采集结果正常返回

### Requirement: 数据统一格式处理

系统 SHALL 将各数据源的数据在入库前统一转换为标准格式，标准格式 MUST 包含标的代码、交易日期、开盘价、最高价、最低价、收盘价、成交量、成交额、数据来源与采集时间等字段。

#### Scenario: 不同数据源数据统一格式

- **WHEN** 从任意数据源采集到数据
- **THEN** 系统将该数据转换为统一标准格式后再入库，字段口径一致

### Requirement: 数据源字段差异映射与清洗

系统 SHALL 在数据源适配器内对字段差异做映射与清洗，MUST 屏蔽各数据源的字段命名与口径差异。

#### Scenario: 字段映射清洗

- **WHEN** 某数据源的字段命名或口径与标准格式不一致
- **THEN** 系统在适配器内完成字段映射与清洗，输出统一格式数据

### Requirement: 数据源密钥通过环境变量配置

系统 SHALL 将数据源所需密钥或 token（如 tushare token）通过环境变量或 `.env` 文件配置（`os.getenv` 读取），MUST NOT 硬编码在代码中。

#### Scenario: 密钥配置

- **WHEN** 数据源需要密钥或 token
- **THEN** 系统从环境变量或 `.env` 文件读取密钥，不在代码中硬编码

### Requirement: 使用新建集合存储数据

系统 SHALL 将新采集的数据存储到新建的 MongoDB 集合，MUST NOT 改造或复用已有集合（如 `market_quotes`、`etf_daily_quotes`、`etf_basic_info`）。

#### Scenario: 新建集合存储

- **WHEN** 数据采集完成
- **THEN** 系统将数据写入新建的集合，不改动已有集合

```

## docs/openspec/changes/data-foundation/specs/index-history-data/spec.md

- Source: docs/openspec/changes/data-foundation/specs/index-history-data/spec.md
- Lines: 1-32
- SHA256: cbc9b043200f6bb2cbbc7553be2d2388a2950c0648c477d51a57edcba1616476

```md
## Purpose

采集并存储主要宽基指数的历史日线行情数据，为市场状态判断（市场温度计）提供数据基础。

## ADDED Requirements

### Requirement: 采集主要宽基指数历史日线行情

系统 SHALL 通过数据源采集主要宽基指数（上证指数、沪深300、中证500、创业板指等）的历史日线行情，每条记录 MUST 包含指数代码、交易日期、开盘价、最高价、最低价、收盘价、成交量与成交额。

#### Scenario: 采集指数历史行情

- **WHEN** 数据采集任务运行
- **THEN** 系统拉取各主要宽基指数的历史日线行情并写入存储

### Requirement: 指数行情数据按日更新

系统 SHALL 通过定时任务在每个交易日更新指数最新日线行情，同一指数同一交易日 MUST 仅保留一条记录，避免重复写入。

#### Scenario: 每日更新指数行情

- **WHEN** 定时任务在交易日运行
- **THEN** 系统更新或插入当日指数行情，同一指数同一交易日仅保留一条记录

### Requirement: 采集失败时优雅降级

系统 SHALL 在数据源不可用或返回异常时记录错误日志并保持服务可用，MUST NOT 因单次采集失败导致崩溃或丢失已有数据。

#### Scenario: 数据源异常时降级

- **WHEN** 数据源请求失败或返回空数据
- **THEN** 系统记录错误日志并保留上一次成功采集的数据，服务保持可用

```

## docs/openspec/changes/data-foundation/specs/stock-fundamental-data/spec.md

- Source: docs/openspec/changes/data-foundation/specs/stock-fundamental-data/spec.md
- Lines: 1-41
- SHA256: be697856bfd8e64c89289e9eeaa91664a794fff1402d260acc1a0951cf0594e0

```md
## Purpose

采集并存储优质池个股的财务指标与行业/概念分类数据，为选股引擎的估值、成长、质量因子打分与热点识别提供数据基础。

## ADDED Requirements

### Requirement: 采集个股财务指标

系统 SHALL 采集优质池个股的财务指标，指标 MUST 至少包含市盈率（PE）、市净率（PB）、净资产收益率（ROE）、营业收入增速、净利润增速与毛利率。

#### Scenario: 采集财务指标

- **WHEN** 数据采集任务运行
- **THEN** 系统拉取优质池内个股的财务指标并写入存储

### Requirement: 采集个股行业与概念分类

系统 SHALL 采集优质池个股所属行业分类与概念板块标签，用于热点方向识别与行业聚合分析。

#### Scenario: 采集行业与概念分类

- **WHEN** 数据采集任务运行
- **THEN** 系统拉取优质池内个股的行业分类与概念标签并写入存储

### Requirement: 财务指标与分类数据定期更新

系统 SHALL 通过定时任务定期更新财务指标与行业/概念分类数据，财务指标 MUST 至少每个财报周期更新一次，分类数据 MUST 至少每日更新一次。

#### Scenario: 定期更新财务与分类数据

- **WHEN** 定时任务运行
- **THEN** 系统更新财务指标与行业/概念分类数据

### Requirement: 采集失败时优雅降级

系统 SHALL 在数据源不可用或返回异常时记录错误日志并保持服务可用，MUST NOT 导致崩溃或丢失已有数据。

#### Scenario: 财务数据采集异常时降级

- **WHEN** 财务指标或分类数据采集失败
- **THEN** 系统记录错误日志并保留上一次成功采集的数据

```

## docs/openspec/changes/data-foundation/specs/stock-history-data/spec.md

- Source: docs/openspec/changes/data-foundation/specs/stock-history-data/spec.md
- Lines: 1-41
- SHA256: 8b8d3c6b0cf3cafab114e25ad51e5e5ad958965e2867497b0d259d6c71bc95b1

```md
## Purpose

采集并存储优质池（沪深300与中证500成分股）个股的历史日线K线数据，为选股打分与交易计划提供数据基础。

## ADDED Requirements

### Requirement: 维护优质池成分股列表

系统 SHALL 维护沪深300与中证500成分股列表，并 MUST 支持定时更新成分股变化。

#### Scenario: 更新成分股列表

- **WHEN** 定时任务运行
- **THEN** 系统拉取最新沪深300与中证500成分股并更新本地列表

### Requirement: 采集优质池个股历史日线K线

系统 SHALL 采集优质池内每只个股的历史日线K线，每条记录 MUST 包含股票代码、交易日期、开盘价、最高价、最低价、收盘价、成交量与成交额。

#### Scenario: 采集个股历史K线

- **WHEN** 数据采集任务运行
- **THEN** 系统拉取优质池内个股历史日线K线并写入存储

### Requirement: 个股K线数据按日增量更新

系统 SHALL 通过定时任务在每个交易日对优质池个股做增量更新，同一股票同一交易日 MUST 仅保留一条记录。

#### Scenario: 每日增量更新个股K线

- **WHEN** 定时任务在交易日运行
- **THEN** 系统仅更新或插入缺失交易日的个股K线数据

### Requirement: 采集失败时优雅降级

系统 SHALL 在数据源不可用或返回异常时记录错误日志并保持服务可用，MUST NOT 导致崩溃或丢失已有数据。

#### Scenario: 个股K线采集异常时降级

- **WHEN** 个股历史K线采集失败
- **THEN** 系统记录错误日志并保留上一次成功采集的数据

```
