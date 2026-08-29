# Comet Design Handoff

- Change: position-monitoring-alerts
- Phase: design
- Mode: compact
- Context hash: 5c08e372276ecbe822a515e6a01f0228ded1a83e7d0cca7b1531d23e3ae07049

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/position-monitoring-alerts/proposal.md

- Source: docs/openspec/changes/position-monitoring-alerts/proposal.md
- Lines: 1-33
- SHA256: a1b9de5e1f004d413a0dbd38f97f438fb9fdde3a6b6868a1ab21c801c0291874

```md
## Why

当前系统仅有持仓的买卖记录与收益展示，缺少对持仓股价格波动的监控。用户无法在价格达到预设目标时及时获知，容易错过止盈或止损时机，影响收益与风险控制。

## What Changes

- 新增持仓监控规则：针对某一只持仓股手动预设止盈与止损。
- 止盈、止损均支持两种模式：
  - 价格模式：设置目标价格阈值。
  - 收益率模式：设置相对持仓均价（`avgCostPrice`）的涨跌幅阈值。
- 新增定时监测任务：基于日线收盘价（`market_quotes` / `etf_daily_quotes` 的 `close`）判定是否触达止盈/止损。
- 触达后产生站内提醒（消息记录，支持已读/未读）；规则保持启用，可持续监控并重复提醒。
- 触达后（可选）推送飞书自定义机器人消息：通过全局 webhook 配置启用，推送失败不影响站内提醒。

## Capabilities

### New Capabilities

- `position-monitoring`: 持仓止盈止损监控，包含监控规则管理、价格/收益率触达判定、站内提醒。

### Modified Capabilities

<!-- 无既有 capability，需求层面的改动不涉及修改。 -->

## Impact

- 后端 StockAI（Java 21 / Spring Boot 3.5 / MongoDB）：
  - 新增 `position_monitor`（监控规则）与 `notification`（站内提醒）两个集合及对应 PO / Repository。
  - 新增监控 Service、Controller 与基于 `@Scheduled` 的定时判定任务。
  - 新增 `FeishuNotificationSender`（通知通道实现），通过全局 webhook 配置（`feishu.*`）可选启用，复用 Spring 6 `RestClient` 发送。
- 前端 StockAI-Console（Vue 3 / Vite / Element Plus）：
  - 持仓列表新增监控设置入口与止盈止损展示。
  - 新增站内提醒展示（未读提示与消息列表）。
- 依赖：无新增第三方依赖，复用现有 MongoDB、Spring 调度与前端技术栈。
```

## docs/openspec/changes/position-monitoring-alerts/design.md

- Source: docs/openspec/changes/position-monitoring-alerts/design.md
- Lines: 1-43
- SHA256: 7defffb942a3bfb29c79b9a4f4f6bd8738b8576ca58d88b639f1450c511f2ff4

```md
## Context

本功能落在 StockAI（Java 21 / Spring Boot 3.5 / MongoDB）后端与 StockAI-Console（Vue 3 / Vite / Element Plus）前端。现有持仓数据在 `position` 集合（含 `avgCostPrice`、`totalQuantity`、`type`），现价来自 `market_quotes`（股票，`close`）或 `etf_daily_quotes`（ETF，`close`）。系统已有 `@Scheduled` 定时任务先例（每日 23:00 计算总收益）。当前无任何通知/提醒基础设施。动机见 proposal.md。

## Goals / Non-Goals

**Goals:**

- 建立监控规则与站内提醒两类数据模型，并提供后端增删改查与触发判定。
- 前端在持仓列表提供监控设置入口与站内提醒展示。
- 定时任务基于现有日线收盘价完成触达判定。

**Non-Goals:**

- 本期不接入飞书机器人，仅预留通知通道接口。
- 不新增实时/盘中行情源，判定统一基于现有日线收盘价。
- 不做自动下单/自动平仓，仅提醒不改动持仓。

## Decisions

- **新增两个 MongoDB 集合**
  - `position_monitor`：`userId`、`symbol`、`stockName`、`type`、`takeProfitType`（`PRICE`/`RATE`）、`takeProfitValue`、`stopLossType`（`PRICE`/`RATE`）、`stopLossValue`、`enabled`、`lastTriggerDirection`（`NONE`/`TAKE_PROFIT`/`STOP_LOSS`）、时间戳；唯一约束 `(userId, symbol)`。
  - `notification`：`userId`、`type`（`TAKE_PROFIT`/`STOP_LOSS`）、`symbol`、`stockName`、`content`、`read`、`createdAt`。
  - 备选：复用 `Position` 嵌入规则字段。否决原因：规则与持仓生命周期不同，且需要独立的启停与状态字段，拆分集合更清晰。
- **判定逻辑与去重**
  - 止盈：价格模式比较收盘价 >= `takeProfitValue`；收益率模式比较（收盘价 - `avgCostPrice`）/ `avgCostPrice` >= `takeProfitValue`。
  - 止损：价格模式比较收盘价 <= `stopLossValue`；收益率模式比较收益率 <= `stopLossValue`。
  - 去重采用 `lastTriggerDirection`：仅当触达方向与上次不同（或为 `NONE`）时才生成提醒并更新方向，实现"持续监控但避免同一方向重复提醒"。
- **定时判定任务**
  - 复用 `@Scheduled`，新增每日收盘后（如 15:30，交易日）的扫描任务 `MonitorTask`，遍历启用规则并复核当前价。
- **提醒通道抽象**
  - 定义 `NotificationSender` 接口，本期实现 `InAppNotificationSender`（写入 `notification` 集合），为后续 `FeishuNotificationSender` 预留扩展点。
- **现有价格源复用**
  - 复用 `StockService.getPositionList` 中股票/ETF 的价格查询分支逻辑，交由独立 `MonitorService` 复用，避免重复实现。

## Risks / Trade-offs

- [日线收盘价可能无法及时反映盘中极值] → 明确本期为收盘价判定，后续接入实时行情可无缝替换价格源。
- [定时任务并发/重复执行] → 去重字段 `lastTriggerDirection` 保证同一阈值区间内不重复提醒；任务单实例部署下无并发写入风险。
- [飞书接入未来需要新集合/配置] → 通过 `NotificationSender` 接口隔离，避免改动判定逻辑。

## Open Questions

无（触发器语意、是否一次性、价格粒度均已与用户确认）。
```

## docs/openspec/changes/position-monitoring-alerts/tasks.md

- Source: docs/openspec/changes/position-monitoring-alerts/tasks.md
- Lines: 1-35
- SHA256: 6fa15fe5e78c916ea431f38a731eecb0eb5dac1dac14097c5740c458fe4199a0

```md
## 1. 后端数据模型

- [x] 1.1 新增 `PositionMonitor` PO（字段与 design.md 一致），验证：编译通过且字段映射符合 design
- [x] 1.2 新增 `Notification` PO，验证：编译通过
- [x] 1.3 新增 `PositionMonitorRepository` 与 `NotificationRepository`，验证：接口编译通过且查询方法符合 service 需求

## 2. 后端监控与提醒逻辑

- [x] 2.1 实现 `MonitorService` 的规则新增/修改/删除/启停，验证：单元测试覆盖保存与停用
- [x] 2.2 实现价格/收益率止盈止损判定（复用股票 `market_quotes` / ETF `etf_daily_quotes` 价格源），验证：单元测试覆盖四种模式
- [x] 2.3 实现 `NotificationSender` 接口与 `InAppNotificationSender`（写入 notification 集合），验证：单元测试触发后生成未读提醒
- [x] 2.4 实现去重逻辑（`lastTriggerDirection`），验证：单元测试证明同一方向不重复提醒、穿越回安全区间后再次提醒
- [x] 2.5 实现 `@Scheduled` 定时判定任务，验证：手动触发后仅对满足条件的启用规则生成提醒

## 3. 后端接口

- [x] 3.1 新增监控规则 Controller 端点（新增/修改/删除/查询），验证：Swagger 或 curl 返回 `Result` 正常
- [x] 3.2 新增站内提醒 Controller 端点（列表/标记已读），验证：curl 返回列表与已读状态更新正常

## 4. 前端（StockAI-Console）

- [x] 4.1 持仓列表新增监控设置入口与止盈止损表单，验证：页面可设置并回显规则
- [x] 4.2 新增站内提醒展示（未读提示 + 消息列表 + 标记已读），验证：页面展示提醒并可标记已读

## 5. 联调验证

- [x] 5.1 后端编译与测试通过，验证：在 StockAI 目录运行 `./mvnw test` 全部通过
- [x] 5.2 前后端端到端联调：设置规则 → 模拟收盘价触达 → 前端看到未读提醒，验证：手工端到端流程通过

## 6. 飞书推送通道

- [ ] 6.1 新增飞书配置项（`application.yml` 的 `feishu.enabled/webhook-url/secret`），验证：编译通过且配置可注入
- [ ] 6.2 新增 `FeishuNotificationSender`（`RestClient` 发送 + 可选加签 + 失败吞异常），验证：单元测试覆盖 payload 结构、加签、失败不抛
- [ ] 6.3 `MonitorService` 改为注入 `List<NotificationSender>` 多渠道循环发送，验证：单元测试证明触达时所有 sender 均被调用
- [ ] 6.4 补全单元测试（`FeishuNotificationSenderTest` + `MonitorServiceTest` 多通道），验证：在 StockAI 目录运行 `./mvnw test` 全部通过
- [ ] 6.5 端到端联调：启用飞书 → 触达 → 飞书群收到机器人消息且站内提醒不受影响，验证：手工端到端流程通过
```

## docs/openspec/changes/position-monitoring-alerts/specs/position-monitoring/spec.md

- Source: docs/openspec/changes/position-monitoring-alerts/specs/position-monitoring/spec.md
- Lines: 1-87
- SHA256: d3d9ab417cedeef653340d4e929c07586e3c1709439f4f5e4402eaeed5646d3a

[TRUNCATED]

```md
## Purpose

持仓止盈止损监控能力：允许用户为持仓股设定止盈/止损规则，并在日线收盘价触达阈值时产生站内提醒，可选推送飞书机器人消息，帮助用户及时把握止盈与止损时机。

## ADDED Requirements

### Requirement: 用户可为持仓股设置止盈止损监控规则

系统 SHALL 允许用户针对其持有的某一只股票或 ETF 设置监控规则；一条规则 MUST 至少包含止盈或止损其中一项。用户 MUST 能够新增、修改、删除和启停规则。

#### Scenario: 新增价格型止盈止损规则

- **WHEN** 用户为某持仓股提交一条规则，指定止盈价格与止损价格
- **THEN** 系统保存该规则并返回成功，规则处于启用状态

#### Scenario: 修改并停用规则

- **WHEN** 用户修改某持仓股已有规则的阈值，或将规则设为停用
- **THEN** 系统更新规则，后续定时判定仅对启用中的规则生效

### Requirement: 止盈与止损支持价格和收益率两种模式

系统 SHALL 支持价格模式与收益率模式。价格模式 MUST 以最新收盘价与目标价格比较；收益率模式 MUST 以（最新收盘价 - 持仓均价）/ 持仓均价 与目标收益率阈值比较。止盈与止损 MUST 可各自独立选择模式。

#### Scenario: 价格模式触达止盈

- **WHEN** 某规则采用价格止盈，最新收盘价大于等于止盈目标价
- **THEN** 系统判定为触达止盈并产生提醒

#### Scenario: 收益率模式触达止损

- **WHEN** 某规则采用收益率止损，最新收益率小于等于止损收益率阈值（负值表示亏损）
- **THEN** 系统判定为触达止损并产生提醒

### Requirement: 定时基于日线收盘价判定触达

系统 SHALL 通过定时任务，对全部启用中的规则基于最新日线收盘价进行触达判定，股票使用 `market_quotes` 收盘价、ETF 使用 `etf_daily_quotes` 收盘价。

#### Scenario: 收盘价触达阈值

- **WHEN** 定时任务运行，且某启用规则的最新收盘价满足其止盈或止损条件
- **THEN** 系统为该规则生成一条站内提醒

### Requirement: 触达后生成站内提醒

系统 SHALL 在规则触达止盈或止损时为对应用户生成一条站内提醒，提醒内容 MUST 包含股票名称、代码、触达类型（止盈/止损）与触发价格。

#### Scenario: 触达产生站内提醒

- **WHEN** 规则触达止盈
- **THEN** 系统生成一条归属该用户、未读状态、类型为止盈的站内提醒

### Requirement: 持续监控并避免同一方向重复提醒

系统 SHALL 在提醒触发后保持规则启用并持续监控；对同一方向（止盈或止损），MUST 在价格未穿越回安全区间前不重复生成提醒。

#### Scenario: 价格穿越回安全区间后再次触达

- **WHEN** 止盈触达后价格回落到止盈阈值以下，随后再次升至止盈阈值以上
- **THEN** 系统再次生成止盈提醒

### Requirement: 站内提醒支持查看与已读

系统 SHALL 支持查询某用户的站内提醒列表（可区分已读与未读），并支持将提醒标记为已读。

#### Scenario: 查询未读提醒

- **WHEN** 用户请求查询自己的站内提醒列表
- **THEN** 系统按时间倒序返回该用户的提醒及其已读/未读状态

#### Scenario: 标记提醒为已读

- **WHEN** 用户将一条或多条提醒标记为已读
- **THEN** 系统更新对应提醒的已读状态

### Requirement: 触达后可推送飞书机器人消息

系统 MAY 在飞书推送启用时，将止盈/止损触达提醒同时推送到配置的飞书自定义机器人 webhook；推送 MUST NOT 影响站内提醒的生成。

#### Scenario: 启用飞书推送后触达

```

Full source: docs/openspec/changes/position-monitoring-alerts/specs/position-monitoring/spec.md
