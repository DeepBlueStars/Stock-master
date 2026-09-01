# Comet Design Handoff

- Change: market-regime
- Phase: design
- Mode: compact
- Context hash: 3c1a70ca17b135ec662bed674ceda3d5f338e859c5c8fcdb780769c6d748c8c2

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/market-regime/proposal.md

- Source: docs/openspec/changes/market-regime/proposal.md
- Lines: 1-29
- SHA256: db1d3b7914c57010a629f8abbc7e4c298bf87289d39269b40b0d8fba035a5495

```md
## Why

用户平时工作较忙，无法持续关注A股市场。需要在每个交易日收盘后自动判断当前市场处于牛市、熊市、震荡或风险状态，并给出大资金仓位建议，帮助用户判断当前是否适合大资金投资。

## What Changes

- 新增「市场温度计」能力：基于主要宽基指数（上证、沪深300、中证500、创业板指等）的历史行情与估值数据，用量化规则判定市场状态
- 新增市场状态判定结果存储与历史查询
- 新增收盘后定时任务，自动生成每日市场状态报告
- 新增 AI 总结：基于量化判定结果生成自然语言报告（量化规则打底 + AI 总结）
- 新增飞书推送：收盘后将市场状态报告推送到飞书
- 前端新增市场温度计页面，展示当前状态与历史记录

## Capabilities

### New Capabilities

- `market-regime`: 市场状态判定（牛/熊/震荡/风险）与仓位建议

### Modified Capabilities

<!-- 无 -->

## Impact

- 后端 StockAI（Java/Spring Boot）：新增市场状态判定服务、AI 总结、定时任务，新增市场状态记录 MongoDB 集合
- 前端 StockAI-Console（Vue）：新增市场温度计页面
- 复用：飞书推送（FeishuNotificationSender）、AI 对话（qwen-max）
- 依赖：data-foundation 提供的指数历史与估值数据

```

## docs/openspec/changes/market-regime/design.md

- Source: docs/openspec/changes/market-regime/design.md
- Lines: 1-34
- SHA256: 83f5185354167095389272aea319ea550987d670ea510e89b2fb1974e4778be4

```md
## Context

data-foundation 已提供主要宽基指数历史行情与估值数据。本 change 在其上构建市场状态判定能力。StockAI 后端为 Java/Spring Boot，已有 AI（qwen-max via Spring AI）与飞书推送（FeishuNotificationSender）。

## Goals / Non-Goals

**Goals:**
- 收盘后自动判定牛/熊/震荡/风险并给出仓位建议
- 量化规则可复现，AI 仅做总结
- 复用现有飞书推送与 AI 能力

**Non-Goals:**
- 不做盘中实时判定
- 不做个股择时

## Decisions

### 决策1：判定引擎
- **选择**：确定性量化规则打分（均线/成交额/估值分位/波动率/市场宽度）→ 映射到四态，AI 仅基于分数与指标生成自然语言总结
- **理由**：稳定可复现，避免大模型直接判定的不稳定与不可复现

### 决策2：状态判定结果存储
- **选择**：新增 MongoDB 集合 `market_regime_records`，每日一条，含状态、仓位建议、指标快照、AI 报告文本
- **理由**：便于历史回溯与前端查询

### 决策3：AI 总结方式
- **选择**：复用 Spring AI ChatClient，用结构化 prompt 将量化分数与指标注入，生成报告
- **理由**：复用现有 AI 配置，避免新增大模型依赖

## Risks / Trade-offs

- [量化规则阈值需要历史回测校准] → 阈值抽到配置，便于后续调参
- [AI 报告可能不稳定] → AI 仅做总结，量化结论不依赖 AI，AI 失败时仍输出量化结论
- [指数/估值数据缺失] → 判定前校验数据完整性，缺失时降级并记录日志

```

## docs/openspec/changes/market-regime/tasks.md

- Source: docs/openspec/changes/market-regime/tasks.md
- Lines: 1-28
- SHA256: 297f9b25c360f1214604cc0ff8b7446e0a85028caeb45ef62409d7f7a22c7e8f

```md
## 1. 指数估值采集（etf-service）

- [ ] 1.1 实现主要宽基指数 PE/PB 估值采集（akshare 估值接口），验证写入 index_valuation 集合且字段完整
- [ ] 1.2 实现指数估值数据定时更新，验证 PE/PB 及其历史分位可计算

## 2. 市场状态判定服务（StockAI）

- [ ] 2.1 实现量化规则判定（均线/成交额/估值分位/波动率），验证同一输入数据得到一致的四态判定结果
- [ ] 2.2 实现大资金仓位建议映射（激进/中性/防御/空仓），验证四态与仓位建议一一对应

## 3. 状态记录存储

- [ ] 3.1 新增市场状态记录 MongoDB 集合与仓储，验证每日状态记录可写入与查询

## 4. AI 总结报告

- [ ] 4.1 实现 AI 总结（将量化分数与指标注入 prompt 生成报告），验证报告包含结论/依据/仓位建议
- [ ] 4.2 实现 AI 失败降级（AI 异常时仍输出量化结论），验证 AI 失败不影响判定结果

## 5. 定时任务与推送

- [ ] 5.1 实现收盘后定时任务触发市场状态判定，验证交易日收盘后生成当日状态记录
- [ ] 5.2 实现飞书日报推送，验证飞书启用时推送报告、失败不影响记录生成

## 6. 前端温度计页面

- [ ] 6.1 新增市场温度计页面展示当前状态与仓位建议，验证页面正确展示四态与建议
- [ ] 6.2 实现历史状态记录查询与展示，验证可按时间倒序查看历史

```

## docs/openspec/changes/market-regime/specs/market-regime/spec.md

- Source: docs/openspec/changes/market-regime/specs/market-regime/spec.md
- Lines: 1-68
- SHA256: 9f3c482f23bf9bdaa552a3b66f5cfc8fd6ea83ac774c7143a2087dc406b6ec73

```md
## Purpose

市场状态判定（市场温度计）能力：基于主要宽基指数历史行情与估值数据，在每个交易日收盘后自动判定市场处于牛市、熊市、震荡或风险状态，并给出大资金仓位建议，帮助用户判断当前是否适合大资金投资。

## ADDED Requirements

### Requirement: 收盘后自动判定市场状态

系统 SHALL 在每个交易日收盘后通过定时任务，基于主要宽基指数的历史行情与估值数据，判定市场状态为牛市、熊市、震荡或风险之一。

#### Scenario: 收盘后生成市场状态

- **WHEN** 定时任务在交易日收盘后运行
- **THEN** 系统生成一条当日的市场状态记录，状态为牛市/熊市/震荡/风险之一

### Requirement: 给出大资金仓位建议

系统 SHALL 基于判定的市场状态给出大资金仓位建议，建议 MUST 明确表达当前是否适合大资金投资（如激进/中性/防御/空仓）。

#### Scenario: 生成仓位建议

- **WHEN** 市场状态判定完成
- **THEN** 系统给出对应的大资金仓位建议

### Requirement: 基于量化规则判定

系统 SHALL 使用可复现的量化规则判定市场状态，规则 MUST 综合均线、成交额、估值分位、波动率等指标。

#### Scenario: 量化规则判定

- **WHEN** 系统计算市场状态
- **THEN** 判定结果由确定性量化规则得出，同一输入数据得到同一结果

### Requirement: 估值分位指标数据来源

系统 SHALL 从指数估值数据（`index_valuation` 集合）读取 PE/PB 及其历史分位用于市场状态判定，估值数据由数据采集服务写入。

#### Scenario: 读取指数估值分位

- **WHEN** 系统计算市场状态的估值分位指标
- **THEN** 系统从指数估值数据读取 PE/PB 与历史分位

### Requirement: AI 生成自然语言报告

系统 SHALL 在量化判定结果基础上，调用 AI 生成自然语言市场状态报告，报告 MUST 包含判定结论、主要依据与仓位建议说明。

#### Scenario: 生成 AI 总结报告

- **WHEN** 量化判定完成
- **THEN** 系统生成自然语言报告，包含结论、依据与仓位建议

### Requirement: 收盘后推送飞书日报

系统 SHALL 在飞书推送启用时，将每日市场状态报告推送到配置的飞书自定义机器人 webhook；推送 MUST NOT 影响报告生成。

#### Scenario: 推送飞书日报

- **WHEN** 飞书推送已启用且市场状态报告生成完成
- **THEN** 系统向配置的 webhook 推送市场状态报告

### Requirement: 支持历史状态查询

系统 SHALL 支持查询历史市场状态记录，用户 MUST 能查看历史每日市场状态与仓位建议。

#### Scenario: 查询历史市场状态

- **WHEN** 用户请求查看市场状态历史
- **THEN** 系统按时间倒序返回历史市场状态记录

```
