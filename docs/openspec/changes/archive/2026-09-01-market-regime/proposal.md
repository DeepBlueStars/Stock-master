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
