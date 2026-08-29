## Why

当前系统仅有持仓的买卖记录与收益展示，缺少对持仓股价格波动的监控。用户无法在价格达到预设目标时及时获知，容易错过止盈或止损时机，影响收益与风险控制。

## What Changes

- 新增持仓监控规则：针对某一只持仓股手动预设止盈与止损。
- 止盈、止损均支持两种模式：
  - 价格模式：设置目标价格阈值。
  - 收益率模式：设置相对持仓均价（`avgCostPrice`）的涨跌幅阈值。
- 新增定时监测任务：基于日线收盘价（`market_quotes` / `etf_daily_quotes` 的 `close`）判定是否触达止盈/止损。
- 触达后产生站内提醒（消息记录，支持已读/未读）；规则保持启用，可持续监控并重复提醒。
- 提醒通道预留飞书机器人接入点，本期仅实现站内提醒。

## Capabilities

### New Capabilities

- `position-monitoring`: 持仓止盈止损监控，包含监控规则管理、价格/收益率触达判定、站内提醒。

### Modified Capabilities

<!-- 无既有 capability，需求层面的改动不涉及修改。 -->

## Impact

- 后端 StockAI（Java 21 / Spring Boot 3.5 / MongoDB）：
  - 新增 `position_monitor`（监控规则）与 `notification`（站内提醒）两个集合及对应 PO / Repository。
  - 新增监控 Service、Controller 与基于 `@Scheduled` 的定时判定任务。
- 前端 StockAI-Console（Vue 3 / Vite / Element Plus）：
  - 持仓列表新增监控设置入口与止盈止损展示。
  - 新增站内提醒展示（未读提示与消息列表）。
- 依赖：无新增第三方依赖，复用现有 MongoDB、Spring 调度与前端技术栈。