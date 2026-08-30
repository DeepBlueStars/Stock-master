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