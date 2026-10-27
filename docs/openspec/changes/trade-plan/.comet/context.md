# Comet Design Handoff

- Change: trade-plan
- Phase: design
- Mode: compact
- Context hash: 0179b02e85d8859a112b68c81ad26229c748fd3cbf4349ecc17196693be25ea9

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/trade-plan/proposal.md

- Source: docs/openspec/changes/trade-plan/proposal.md
- Lines: 1-28
- SHA256: 03668be5deda5b1cc4ac6e3f15369ef00ca2d2831f3717243e7a198dfd1ad2c9

```md
## Why

用户买入股票后，不知道该如何设置止盈止损、如何补仓、是分批买入还是一次性买入、是全部卖出还是分批卖出。需要交易计划引擎，在买入后自动生成交易计划卡片，联动现有止盈止损提醒。

## What Changes

- 新增交易计划引擎：买入后基于波动率(ATR)、持仓成本、趋势，自动生成交易计划
- 交易计划包含：止盈止损位建议、分批/一次性买入建议、补仓规则、分批卖出策略
- 联动现有止盈止损提醒（PositionMonitor）
- 新增 AI 总结：基于量化规则生成的交易计划做自然语言说明
- 前端新增交易计划卡片展示

## Capabilities

### New Capabilities

- `trade-plan`: 买入后自动生成交易计划（止盈止损位/补仓/分批买卖）

### Modified Capabilities

<!-- 无 -->

## Impact

- 后端 StockAI：新增交易计划服务、AI 总结，新增交易计划 MongoDB 集合
- 联动：现有 PositionMonitor 止盈止损提醒
- 前端 StockAI-Console：新增交易计划卡片
- 依赖：data-foundation 提供的个股历史K线（用于计算波动率ATR）

```

## docs/openspec/changes/trade-plan/design.md

- Source: docs/openspec/changes/trade-plan/design.md
- Lines: 1-34
- SHA256: f8e53f2bc6f8682933e9b9450deb3e52fb70ecda63349fc177dbcc780ba0284d

```md
## Context

现有 MonitorService 已支持止盈止损提醒（价格/收益率双模式）。data-foundation 已提供优质池个股历史K线。本 change 在其上构建交易计划生成能力，并联动现有提醒。

## Goals / Non-Goals

**Goals:**
- 买入后自动生成交易计划
- 止盈止损/补仓/分批买卖建议可量化、可解释
- 与现有 PositionMonitor 联动

**Non-Goals:**
- 不自动下单
- 不替代人工决策

## Decisions

### 决策1：止盈止损位计算
- **选择**：基于 ATR（平均真实波幅）的倍数设定止损位（如成本价 - N*ATR），止盈位用风险回报比（如 2:1）或关键阻力位
- **理由**：ATR 自适应个股波动，比固定百分比更贴合个股特性

### 决策2：分批/一次性与补仓规则
- **选择**：高波动股建议分批买入（如3批），低波动股一次性；补仓在回落至关键支撑且趋势未破时触发
- **理由**：波动率决定建仓节奏，趋势决定补仓与否

### 决策3：交易计划存储与联动
- **选择**：新增 MongoDB 集合 `trade_plans`，买入后生成；提供接口将建议止盈止损写入 PositionMonitor
- **理由**：交易计划独立存储便于查询与追踪，联动复用现有提醒能力

## Risks / Trade-offs

- [ATR 参数（倍数）需校准] → 参数抽到配置，后续回测调参
- [建议可能误判] → 明确为辅助建议，用户可手动修改，不自动执行
- [历史K线缺失] → 缺失时降级为固定百分比，并标注

```

## docs/openspec/changes/trade-plan/tasks.md

- Source: docs/openspec/changes/trade-plan/tasks.md
- Lines: 1-23
- SHA256: 64f1783d79cc3c9c1ffc333307d95a1675f95afc4d5e5b29557af013c8171a25

```md
## 1. 波动率与关键位计算

- [ ] 1.1 实现基于历史K线的 ATR 计算，验证 ATR 结果与标准算法一致
- [ ] 1.2 实现关键支撑阻力位识别，验证识别结果可复现

## 2. 交易计划生成

- [ ] 2.1 实现止盈止损位建议（ATR倍数 + 风险回报比），验证建议值合理可解释
- [ ] 2.2 实现分批/一次性买入建议，验证基于波动率给出
- [ ] 2.3 实现补仓规则与分批卖出策略，验证触发条件明确

## 3. 交易计划存储

- [ ] 3.1 新增交易计划 MongoDB 集合与仓储，验证买入后计划可写入与查询

## 4. 联动现有提醒

- [ ] 4.1 实现交易计划止盈止损一键写入 PositionMonitor，验证写入后提醒规则生效

## 5. AI 总结与前端

- [ ] 5.1 实现交易计划的 AI 总结说明，验证说明包含止盈止损/补仓/卖出依据
- [ ] 5.2 前端新增交易计划卡片展示，验证展示止盈止损位/补仓/卖出策略及联动入口

```

## docs/openspec/changes/trade-plan/specs/trade-plan/spec.md

- Source: docs/openspec/changes/trade-plan/specs/trade-plan/spec.md
- Lines: 1-79
- SHA256: 5e980801b988976b970c1c1b70a1c9f78e3f7deb16ebadd505eb9270e1891595

```md
## Purpose

交易计划能力：用户买入某只股票后，系统基于波动率（ATR）、持仓成本与趋势自动生成交易计划，包含止盈止损位建议、分批/一次性买入建议、补仓规则与分批卖出策略，并联动现有止盈止损提醒。

## ADDED Requirements

### Requirement: 买入后自动生成交易计划

系统 SHALL 在用户买入某只股票后，基于该股的历史K线（波动率ATR）、持仓成本与趋势，自动生成一份交易计划。

#### Scenario: 买入后生成交易计划

- **WHEN** 用户买入某只股票
- **THEN** 系统自动生成该股的交易计划

### Requirement: 给出止盈止损位建议

系统 SHALL 在交易计划中给出止盈位与止损位建议，建议 MUST 基于该股的波动率或关键支撑阻力位计算得出。

#### Scenario: 生成止盈止损位

- **WHEN** 交易计划生成
- **THEN** 系统输出该股的止盈位与止损位建议及计算依据

### Requirement: 给出买入方式建议

系统 SHALL 在交易计划中给出分批买入或一次性买入建议，建议 MUST 基于该股波动率与用户可用资金给出。

#### Scenario: 生成买入方式建议

- **WHEN** 交易计划生成
- **THEN** 系统输出分批或一次性买入建议

### Requirement: 给出补仓规则

系统 SHALL 在交易计划中给出补仓规则，规则 MUST 说明在何种条件（如回落至关键支撑且逻辑未破）下补仓。

#### Scenario: 生成补仓规则

- **WHEN** 交易计划生成
- **THEN** 系统输出补仓触发条件与补仓比例建议

### Requirement: 给出卖出策略

系统 SHALL 在交易计划中给出分批卖出或全部卖出策略，策略 MUST 说明止盈减仓与止损清仓的触发条件。

#### Scenario: 生成卖出策略

- **WHEN** 交易计划生成
- **THEN** 系统输出分批或全部卖出策略及触发条件

### Requirement: 联动现有止盈止损提醒

系统 SHALL 将交易计划中的止盈止损位与现有止盈止损提醒（PositionMonitor）联动，MUST 支持一键将建议值写入提醒规则。

#### Scenario: 联动止盈止损提醒

- **WHEN** 用户确认采用交易计划中的止盈止损建议
- **THEN** 系统将建议值写入该股的止盈止损监控规则

### Requirement: 生成交易计划周报

系统 SHALL 在每个周六 08:00 为每个持仓用户生成一份交易计划周报；周报 MUST 包含组合概览、本周买卖回顾（含清仓复盘）、当前持仓交易计划、行业分布、风险关注、下周关注方向与行动清单，并 MUST 支持查看最近 12 期历史周报。

#### Scenario: 定时生成周报

- **WHEN** 到达每周六 08:00
- **THEN** 系统为每个有持仓的用户生成当期周报，用户可查询最新一期与历史各期全文

#### Scenario: 数据缺失时周报降级

- **WHEN** 热点、选股数据或个股历史K线缺失
- **THEN** 周报对应章节标注数据缺失且不编造内容；模型生成失败时降级为量化文本周报

#### Scenario: 查看历史周报

- **WHEN** 用户打开历史周报弹窗
- **THEN** 系统按生成时间倒序展示最近 12 期，可选择任一期查看全文


```
