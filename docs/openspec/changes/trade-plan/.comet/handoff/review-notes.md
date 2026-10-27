# trade-plan 代码审查记录（review_mode=standard）

- 范围：`df0af979f32006c05e555d0604be00c5133d1709` → 当前 HEAD（本会话无子代理派发能力，采用主会话内联审查）
- 审查维度：正确性、安全、边界、既有回归

## 结论

未发现 CRITICAL / IMPORTANT 问题；以下为可接受的小问题（已记录原因与影响范围）。

## 已核对要点

1. 买入链路：`StockService.buy` 在现金不足提前返回时不会触发计划生成；计划生成异常被 try-catch 隔离，交易结果不受影响（`StockServiceTradePlanTest` 覆盖）。
2. 计划覆盖：重复买入时保留既有 `id` 与 `monitorLinked`，不会产生重复计划（`TradePlanServiceTest` 覆盖）。
3. 联动写入：已确认方案 A，覆盖取值保留 `enabled`，新建时启用；返回 before/after（`TradePlanLinkMonitorTest` 覆盖）。
4. 降级路径：K线不足、ATR 导致止损价非正均走固定百分比并标记 `degraded`（`TradePlanCalculatorTest` 覆盖）。
5. 周报：结构校验（八章节 + 免责声明 + 逐仓齐全）失败或模型异常均降级为量化文本，`status=fallback`；同周重复生成 upsert（`TradePlanSummaryServiceTest` 覆盖）。
6. 定时任务：单用户失败不阻断，用户去重（`TradePlanSummaryTaskTest` 覆盖）。
7. 清仓复盘：交易回放加权平均，历史不足标记「成本数据缺失」，不估算（`TradePlanSummaryDataAssemblerTest` 覆盖）。
8. 前端：`npm run build` 通过；周报默认折叠、展开内联全文、历史弹窗左右分栏、写入提醒前后对比、降级标签与空态均已实现。

## 可接受的小问题

| # | 问题 | 影响 | 处置 |
|---|---|---|---|
| 1 | `linkMonitor` 新建 PositionMonitor 时 `type` 留空 | `MonitorService.checkRule` 不依赖 `type`；后续在提醒页保存时会按持仓补全 | 接受 |
| 2 | `TradePlanService.list` 对缺失计划逐只补生成 | 历史持仓较多时首次打开页面为 N 次查询（每只 1 次K线查询） | 设计已确认取舍，接受 |
| 3 | `TradePlanSummaryDataAssembler` 对每个清仓标的额外查询一次交易流水（N+1） | 清仓数量通常极少，周报为低频离线任务 | 接受 |
