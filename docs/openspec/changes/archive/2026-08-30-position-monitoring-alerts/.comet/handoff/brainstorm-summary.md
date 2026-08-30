# Brainstorm Summary

- Change: position-monitoring-alerts
- Date: 2026-08-29

## 确认的技术方案

新增持仓止盈/止损监控子系统（后端 StockAI + 前端 StockAI-Console）：

- 后端新增 `PositionMonitor`、`Notification` 两个 MongoDB 集合及 PO/Repository。
- `MonitorService` 承担规则 CRUD 与触达判定；`NotificationSender` 接口（`InAppNotificationSender` 实现，飞书预留扩展点）。
- `MonitorTask` 通过 `@Scheduled` 每日收盘后判定启用规则。
- `MonitorController` 提供规则与提醒 REST 端点。
- 前端持仓列表新增「监控」入口，顶部导航新增提醒铃铛 + 未读数 + 消息列表（已读/未读）。

## 关键取舍与风险

- 集合独立 `position_monitor`（而非嵌入 Position），规则与持仓生命周期解耦。
- 判定采用定时扫描（复用 `@Scheduled` + 日线收盘价），不引入实时行情。
- 持续监控 + 去重：`lastTriggerDirection` + 回到安全区复位。
- 风险：日线收盘价无法反映盘中极值（后续可替换价格源）；飞书接入通过接口隔离。

## 测试策略

- 单测：判定四模式（价格/收益率 × 止盈/止损）、去重逻辑（Mockito mock repository）。
- 接口测试：规则 CRUD、提醒列表/标记已读。
- 端到端：手动联调。

## Spec Patch

无（delta spec 已覆盖验收场景，无需回写）。