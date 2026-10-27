# trade-plan 验证报告

- Change: trade-plan
- 验证模式：full（任务 9 > 3、变更文件 60 > 8）
- 验证时间：2026-10-26
- 产物语言：zh-CN
- base-ref：`df0af979f32006c05e555d0604be00c5133d1709`

## 总览

| 维度 | 结果 |
|---|---|
| 完整性 | tasks.md 9/9 已勾选；delta spec 7 个 Requirement 均有实现 |
| 正确性 | 10 个 Scenario 均有实现与测试覆盖 |
| 一致性 | 实现符合 `design.md` 三项决策与 Design Doc 技术方案；无矛盾 |

## 验证证据（本轮重新执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端测试 | `mvn -o test`（StockAI） | Tests run: 170, Failures: 0, Errors: 0；BUILD SUCCESS |
| 后端打包 | `mvn -o clean package -DskipTests` | BUILD SUCCESS，产出 `stockai-service-1.0.0.jar` |
| 前端构建 | `npm run build`（StockAI-Console） | `✓ built in 15.10s`，无编译错误 |
| 代码审查 | `review_mode=standard`，build 阶段完成后审查 | 记录见 `docs/openspec/changes/trade-plan/.comet/handoff/review-notes.md`，无 CRITICAL/IMPORTANT |

## 需求实现映射

| Requirement（delta spec） | 实现位置 | 测试覆盖 |
|---|---|---|
| 买入后自动生成交易计划 | `StockService.buy`（try-catch 调用）→ `TradePlanService.refreshPlan` | `StockServiceTradePlanTest` |
| 给出止盈止损位建议 | `TradePlanCalculator#calculate`（ATR×倍数止损 + 风险回报止盈，含 `stopBasis`/`targetBasis`） | `TradePlanCalculatorTest` |
| 给出买入方式建议 | `TradePlanCalculator#applyBuyMode`（≥3% 三批 / 2%~3% 两批 / <2% 一次性；现金口径 `REMAINING_CASH`/`UNKNOWN`） | `TradePlanCalculatorTest`（三档 + 现金缺失） |
| 给出补仓规则 | `TradePlanCalculator#applyAddPositionRule`（回落支撑且未破止损，比例 30%；跌破支撑给提示） | `TradePlanCalculatorTest` |
| 给出卖出策略 | `TradePlanCalculator#applySellStrategy`（止盈减仓 50%、止损清仓、接近阻力提示） | `TradePlanCalculatorTest` |
| 联动现有止盈止损提醒 | `TradePlanService#linkMonitor` + `POST /trade-plan/link-monitor`（覆盖取值、保留 enabled、before/after） | `TradePlanLinkMonitorTest`、`TradePlanControllerTest` |
| 生成交易计划周报 | `TradePlanSummaryService` + `TradePlanSummaryTask`（周六 08:00）+ 4 个 summary 接口 + `TradePlan.vue`/`WeeklySummaryBlock.vue` | `TradePlanSummaryServiceTest`、`TradePlanSummaryTaskTest`、`TradePlanControllerTest` |

## 场景覆盖映射

| Scenario | 覆盖情况 |
|---|---|
| 买入后生成交易计划 | `StockServiceTradePlanTest#buyTriggersTradePlanRefresh` |
| 生成止盈止损位 | `TradePlanCalculatorTest#stopLossAndTakeProfitFollowAtrAndRiskReward` |
| 生成买入方式建议 | `TradePlanCalculatorTest`（低/中/高波动三例） |
| 生成补仓规则 | `TradePlanCalculatorTest` 断言 `addPositionRule` 触发价与比例 |
| 生成卖出策略 | `TradePlanCalculatorTest` 断言止盈减仓/止损清仓文本 |
| 联动止盈止损提醒 | `TradePlanLinkMonitorTest`（覆盖 + 新建 + 计划缺失） |
| 定时生成周报 | `TradePlanSummaryTaskTest#generatesForEveryActiveUserAndContinuesAfterFailure` |
| 数据缺失时周报降级 | `TradePlanSummaryServiceTest#fallsBackToQuantTextWhenChatClientThrows`/`fallsBackWhenStructureIncomplete`；`TradePlanSummaryDataAssemblerTest#replayMarksMissingCostWhenHistoryIncomplete` |
| 查看历史周报 | `POST /trade-plan/summary/list|detail`（`TradePlanControllerTest`）+ 前端历史弹窗 |
| 降级计划（K线不足） | `TradePlanCalculatorTest#degradedWhenKlinesInsufficient` |

## 设计与实现一致性

| design.md 决策 | 实现 |
|---|---|
| 决策1：ATR 倍数止损 + 风险回报比止盈，参数可配置 | `TradePlanProperties`（`trade-plan.*`）与 `TradePlanCalculator`，YAML 已落配置 |
| 决策2：波动率决定分批/一次性，趋势决定补仓 | `applyBuyMode` 三档 + `applyAddPositionRule`（支撑与止损双重条件） |
| 决策3：`trade_plans` 集合 + 一键写入 PositionMonitor | `TradePlan`/`TradePlanRepository`/`TradePlanService#linkMonitor` |
| 周报：周六 08:00、每用户一份、固定模板与独立 prompt 文件、结构校验与降级 | `TradePlanSummaryTask` + `prompts/trade-plan-weekly-report.md` + `prompts/trade-plan-weekly-template.md` + 校验/降级逻辑 |
| 清仓复盘：交易回放计算成本基准，不足标注缺失 | `TradePlanSummaryDataAssembler#replayClearedPosition` |

## 问题清单

### CRITICAL

无。

### WARNING

无。

### SUGGESTION

1. `.comet/handoff/ai-summary-template.md` 中周报 cron 示例仍写作 `trade-plan.ai-summary.cron`，实际配置键为 `trade-plan.summary-cron`。建议后续统一文档措辞（不影响运行）。
2. `TradePlanService#linkMonitor` 新建 `PositionMonitor` 时 `type` 留空（既有 `MonitorService.save` 会按持仓补全）。可考虑显式设置以便排查。
3. 周报任务按用户串行生成，用户量增大时可考虑分批执行或加限流；当前规模下无需改动。

## 未检查项（明确跳过）

- 前端无自动化测试框架，采用人工验证清单（折叠/展开、历史弹窗、写入提醒前后对比、降级标签、空态），未纳入自动验证。

## 结论

所有 CRITICAL/IMPORTANT 检查通过，无阻断项；存在 3 条 SUGGESTION（不影响正确性与验收）。验证通过，可进入归档阶段。
