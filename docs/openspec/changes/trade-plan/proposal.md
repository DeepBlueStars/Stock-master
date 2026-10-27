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
