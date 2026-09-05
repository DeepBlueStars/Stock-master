# Comet Design Handoff

- Change: stock-screener
- Phase: design
- Mode: compact
- Context hash: 82c2494bf31bf86090b7e1f3867ce8b09fe6c2cea74a89ffed238f0ce1218d4a

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/stock-screener/proposal.md

- Source: docs/openspec/changes/stock-screener/proposal.md
- Lines: 1-29
- SHA256: 6b096e30dd8624736d13f95912acdb3f10a8089b03a27df8cd10114f1d6ec702

```md
## Why

用户不知道该筛选出当前适合投资的股票，无法准确判断市场热点和适合投资的方向，也无法判断股票是否具有长期投资价值，同时不希望投资纯炒作的热点。需要一个选股引擎，在优质池内做多因子打分，识别有基本面支撑的热点方向，并标注炒作风险。

## What Changes

- 新增多因子打分选股：在优质池（沪深300+中证500）内，用估值/成长/质量/动量因子打分，生成候选股列表
- 新增长期价值判断：结合基本面质量因子，判断个股是否值得长期投资
- 新增热点方向识别：基于行业/概念聚合成交额、涨幅、涨停家数，识别当前热点方向
- 新增炒作风险标注：用基本面质量因子区分「有基本面支撑」与「纯炒作」
- 新增 AI 总结：基于打分结果与热点识别生成自然语言报告（量化规则打底 + AI 总结）
- 前端新增选股结果展示页

## Capabilities

### New Capabilities

- `stock-screening`: 优质池内多因子打分选股与长期价值判断
- `hotspot-analysis`: 市场热点方向识别与炒作风险标注

### Modified Capabilities

<!-- 无 -->

## Impact

- 后端 StockAI：新增选股打分服务、热点识别服务、AI 总结，新增候选股/热点记录 MongoDB 集合
- 前端 StockAI-Console：新增选股结果展示页
- 依赖：data-foundation 提供的财务指标与行业/概念数据

```

## docs/openspec/changes/stock-screener/design.md

- Source: docs/openspec/changes/stock-screener/design.md
- Lines: 1-35
- SHA256: 1ac634fd4198140116f648c5fc505ba86dde7e72ab019a75a2c59d751eef83bd

```md
## Context

data-foundation 已提供优质池个股的财务指标、行业/概念分类与历史K线。本 change 在其上构建选股打分与热点识别能力。后端为 Java/Spring Boot，已有 AI（qwen-max）能力。

## Goals / Non-Goals

**Goals:**
- 优质池内多因子打分选股
- 识别热点方向并区分基本面/炒作
- 量化规则可复现，AI 仅做总结

**Non-Goals:**
- 不做全A股覆盖
- 不做盘中实时选股
- 不自动推荐买卖时点

## Decisions

### 决策1：多因子模型
- **选择**：估值（PE/PB）、成长（营收/净利增速）、质量（ROE/毛利率）、动量（价格趋势）四类因子打分，加权合成综合评分
- **理由**：覆盖价值与趋势，避免单一因子偏差；权重可配置便于回测调参

### 决策2：热点识别与炒作区分
- **选择**：按行业/概念聚合成交额、涨幅、涨停家数识别热点；用质量因子（ROE/毛利率/净利增速）过滤，低质量高热度标注为「纯炒作」
- **理由**：热点靠量价，价值靠基本面，二者结合才能区分

### 决策3：AI 总结
- **选择**：复用 Spring AI ChatClient，将评分与热点结果注入 prompt 生成报告
- **理由**：量化打底，AI 仅总结

## Risks / Trade-offs

- [因子权重主观] → 权重抽到配置，后续用历史回测校准
- [财务数据滞后/缺失] → 缺失时用最近一次可用数据，并标注数据时效
- [热点识别滞后于盘面] → 收盘后基于日线数据识别，明确为日频辅助

```

## docs/openspec/changes/stock-screener/tasks.md

- Source: docs/openspec/changes/stock-screener/tasks.md
- Lines: 1-23
- SHA256: f8bbe8d4802b285b1ae5cb31b00661955ec74cb3802d2ea392eb7ea7603bbfff

```md
## 1. 多因子打分选股

- [ ] 1.1 实现估值/成长/质量/动量四类因子计算，验证各因子得分可复现
- [ ] 1.2 实现综合评分加权合成与候选股列表生成，验证候选股按评分排序且附理由

## 2. 长期价值判断

- [ ] 2.1 实现基于质量因子的长期价值评级，验证输出长期价值评级

## 3. 热点方向识别

- [ ] 3.1 实现按行业/概念聚合量价指标识别热点方向，验证输出热点方向及热度排序
- [ ] 3.2 实现炒作风险标注（质量因子过滤低质量高热度），验证区分基本面/纯炒作

## 4. AI 总结与定期任务

- [ ] 4.1 实现选股与热点结果的 AI 总结报告，验证报告包含评分依据与炒作风险提示
- [ ] 4.2 实现定时任务定期更新选股与热点结果，验证交易日更新评分

## 5. 前端展示

- [ ] 5.1 新增选股结果展示页，验证展示候选股评分与理由
- [ ] 5.2 新增热点方向展示，验证展示热点方向与炒作风险标注

```

## docs/openspec/changes/stock-screener/specs/hotspot-analysis/spec.md

- Source: docs/openspec/changes/stock-screener/specs/hotspot-analysis/spec.md
- Lines: 1-23
- SHA256: 4c0988e5e5261c66718a78887d4968ef063177c7bbd436d6463a28e8b6bd6552

```md
## Purpose

热点方向识别能力：基于优质池个股的行业/概念分类与行情数据，识别当前市场热点方向，并用基本面质量因子区分「有基本面支撑」与「纯炒作」，帮助用户避免投资纯炒作热点。

## ADDED Requirements

### Requirement: 识别市场热点方向

系统 SHALL 基于行业/概念分类聚合成交额、涨幅与涨停家数等指标，识别当前市场热点方向，MUST 输出热点方向及热度排序。

#### Scenario: 识别热点方向

- **WHEN** 热点识别任务运行
- **THEN** 系统输出当前热点方向列表及热度排序

### Requirement: 标注炒作风险

系统 SHALL 用基本面质量因子评估热点方向，区分「有基本面支撑」与「纯炒作」，MUST 在热点方向结果中标注炒作风险。

#### Scenario: 标注炒作风险

- **WHEN** 热点方向识别完成
- **THEN** 系统为每个热点方向标注是否存在炒作风险及其依据

```

## docs/openspec/changes/stock-screener/specs/stock-screening/spec.md

- Source: docs/openspec/changes/stock-screener/specs/stock-screening/spec.md
- Lines: 1-41
- SHA256: 0d60285da28582a26240d4cef6c60e15c818f0bde8f5cd70887812e995517356

```md
## Purpose

选股打分能力：在优质池（沪深300与中证500成分股）内，基于估值、成长、质量、动量因子对个股打分，生成候选股列表，并判断个股是否具备长期投资价值。

## ADDED Requirements

### Requirement: 优质池内多因子打分

系统 SHALL 在优质池内对每只个股按估值、成长、质量、动量四类因子打分，MUST 输出综合评分与各因子得分。

#### Scenario: 计算个股评分

- **WHEN** 选股任务运行
- **THEN** 系统输出优质池内每只个股的综合评分与各因子得分

### Requirement: 生成候选股列表

系统 SHALL 基于综合评分生成候选股列表，列表 MUST 包含股票代码、名称、综合评分与入选理由。

#### Scenario: 生成候选股列表

- **WHEN** 选股任务运行完成
- **THEN** 系统生成按评分排序的候选股列表，每只附评分与理由

### Requirement: 判断长期投资价值

系统 SHALL 结合基本面质量因子（如ROE、毛利率、净利润增速）判断个股是否具备长期投资价值，MUST 在结果中标注长期价值评级。

#### Scenario: 输出长期价值评级

- **WHEN** 个股打分完成
- **THEN** 系统输出该个股的长期价值评级

### Requirement: 选股结果定期更新

系统 SHALL 通过定时任务定期更新选股结果，MUST 至少每个交易日更新一次评分。

#### Scenario: 定期更新选股结果

- **WHEN** 定时任务在交易日运行
- **THEN** 系统重新计算并更新候选股评分

```
