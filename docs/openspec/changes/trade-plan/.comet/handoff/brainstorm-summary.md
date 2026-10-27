# Brainstorm Summary

- Change: trade-plan
- Date: 2026-10-26

## 已确认的代码事实（探查结论）

- 买入入口：`StockAI/.../service/StockService.java#buy(StockTrade)`，返回前完成持仓 upsert、交易记录、现金扣减；提示文案拼装在 `return result.setMessage(...)`。
- 已有提醒能力：`MonitorService`（save/list/delete/enable/checkAll/checkRule），`PositionMonitor`（PRICE/RATE 两种止盈止损类型，`last_trigger_direction` 去重触发）；`MonitorController` 暴露 `/monitor/save|list|delete|enable`。
- 历史K线：`stock_daily_kline`（symbol + trade_date 去重），etf-service 实际写入 open/high/low/close/volume/amount；但 Java `StockDailyKline` 仅映射 close/amount，缺少 open/high/low，ATR 需扩展该只读映射（无需数据迁移）。
- 现价：`MonitorService#getCurrentPrice`（ETF 走 `etf_daily_quotes`，个股走 `market_quotes`）。
- AI 能力：Spring AI `ChatClient`（`ScreeningReportService` 提供 prompt + fallback 量化文本 + status 字段模式，可复用）。
- 配置能力：`ScreeningProperties`（@ConfigurationProperties）可作为交易计划参数配置的样板；`application.yml` 已有 `feishu`、`screening` 等前缀。
- 前端：Vue 3 + Element Plus，`Dashboard.vue` 已承载持仓/买卖/提醒（`/stock/*`、`/monitor/*` 调用）；路由在 `src/router/index.js`。
- 工作区现状：仅 `.vscode/settings.json` 有未提交改动（IDE 配置，与 trade-plan 无关）；`StockAI-Console/src/router/index.js` 仅有 stat 噪声，无内容差异。

## 已确认决策

1. **生成时机与生命周期 = 方案 A**：买入成功后同步生成/刷新该股计划；首次建计划，再次买入覆盖为新基准（成本/数量变化）；生成失败仅记日志，不影响买入结果；单股仅保留一条“当前计划”。
2. **可用资金口径 = 方案 A**：`buyMethod=现金` 时取买入后剩余 `Users.cash` 作为可用资金并给出金额分配；银行卡或 cash 为空时只给"批数 + 每批比例"，不给金额（不触发 Spec Patch）。

3. **止盈止损计算 = 方案 A**：止损 = 成本 − `stop_atr_multiplier`×ATR(`atr_period`)，止盈 = 成本 + `risk_reward`×风险；关键支撑阻力位仅用于补仓触发与分批卖出参考，不参与止盈定价。用户已认可默认参数：`atr_period=14`、`stop_atr_multiplier=2.0`、`risk_reward=2.0`、`support_resistance_window=20`、批次规则 `ATR/收盘价 ≥3% → 3 批`、`2%~3% → 2 批`、`<2% → 一次性`、K线不足 15 根 → 降级 `-5%/+10%` 并标记 `degraded`。

4. **AI 总结 = 用户改定：不随买卖触发**。改为定时周报：每周六早上固定生成一次（每用户一份，覆盖其全部持仓交易计划），使用固定详细模板；模板需单独产出供用户审核（见 `ai-summary-template.md`）。不再为买入链路引入 `@Async`。

## 待确认问题

5. 前端交易计划页与卡片形态（已定：新增交易计划页 + 顶部周报折叠区块；逐仓卡片列表形态待定）。
6. **联动写入 PositionMonitor = 方案 A（已确认）**：一键覆盖写入建议止损/止盈（PRICE 类型），保留 `enabled` 现值（无规则则新建并启用），返回 before/after 对比。
7. 测试策略与数据夹具来源。

## 候选方案

待确认问题收敛后补写。

## 确认的技术方案

### 计划生成与联动

- 买入链路末尾 try-catch 调用 `TradePlanService.refreshPlan`，失败仅日志
- 指标：Wilder ATR(14)、近 20 日 min(low)/max(high) 作为支撑/阻力
- 止损 = 成本 − 2×ATR；止盈 = 成本 + 2×风险；`stop_loss_price <= 0` 时降级 −5%/+10%
- 批次：ATR/现价 ≥3% 分 3 批（50/30/20）；2%~3% 分 2 批（60/40）；<2% 一次性；触发价以支撑位兜底
- 补仓：回落至支撑且未破止损，比例 ≤ 当前市值 30%；已跌破支撑则提示暂不补仓
- 卖出：止盈减仓 50%、止损清仓、接近阻力提示
- 存储：`trade_plans`（user_id+symbol 唯一）；`StockDailyKline` 补 open/high/low 只读映射（无迁移）
- 接口：`/trade-plan/list`、`/link-monitor`、`/summary/latest`、`/summary/list`、`/summary/detail`、`/summary/generate`
- 前端：新页面 `/trade-plan` + 菜单项「交易计划」；顶部周报区块 + 逐仓卡片

### 周报（AI 总结）已定稿要点

- 频率：每周六 08:00（`${trade-plan.summary-cron:0 0 8 ? * SAT}`），每用户一份
- 范围：当前持仓 + 本周清仓个股复盘
- 章节（顺序固定）：组合概览 / 本周买卖回顾（买入、卖出、清仓复盘）/ 当前持仓交易计划 / 行业分布 / 风险与关注 / 下周关注方向（行业+个股）/ 下周行动清单 / 免责声明
- 不设字数上限；逐仓不设数量上限
- 存储：`trade_plan_summary`（`user_id + week_start` 去重，保留最近 12 周）
- 展示：交易计划页顶部区块，仅最新一期；默认不全展示（生成时间常显），可展开全文；「历史」弹窗左列表右内容
- Prompt 与模板各自独立为 Markdown 文件，运行时读取：`prompts/trade-plan-weekly-report.md`（约束）+ `prompts/trade-plan-weekly-template.md`（模板）
- 清仓股成本基准：按历史交易流水时间顺序回放加权平均计算；数据不足标注「成本数据缺失」，禁止估算
- 行业分布来源：`stock_industry_concept`（industry 字段），缺失归入「未分类」
- 下周关注方向来源：最近一期 `hotspot_result`（行业方向）+ 最近一期 `screening_result` 榜单（个股）

## 关键取舍与风险

- 不新增 `@Async`/线程池：周报改为定时任务，买入链路零 AI 调用
- 覆盖用户手工提醒规则（已确认方案 A），以 before/after 提示降低误操作
- 不修正既有 `RealizedReturn` 算法，清仓复盘使用独立交易回放
- ATR 依赖 high/low → 仅补只读字段映射；脏数据走降级
- 历史持仓按需补生成计划，首次打开列表略慢（每只 1 次 K线查询）
- LLM 输出不可控 → 结构校验 + 量化文本 fallback（`status=fallback`）

## 测试策略

- `TradePlanIndicatorTest`：ATR 标准样例与递推、支撑阻力、K线不足降级
- `TradePlanCalculatorTest`：止损止盈公式、三档波动率边界、批次触发价兜底、补仓/卖出文本、现金缺失
- `TradePlanDataAssemblerTest`：交易回放加权平均、清仓识别与成本缺失、行业分布排序与未分类
- `TradePlanServiceTest`：生成/覆盖、list 幂等补生成、link-monitor 覆盖与新建 + before/after
- `TradePlanSummaryServiceTest`：数据组装、prompt 加载、结构校验失败降级、ChatClient 异常降级
- `TradePlanSummaryTaskTest`：多用户遍历、单用户失败不阻断
- `TradePlanPoTest` / `TradePlanControllerTest`：集合名与索引、接口委派
- 前端无测试框架 → 人工验证清单（折叠/展开、历史弹窗、写入提醒前后对比、降级标签、空态）

## Spec Patch

- 已在 `specs/trade-plan/spec.md` 追加 `### Requirement: 生成交易计划周报`，含 3 个 Scenario：定时生成周报、数据缺失时周报降级、查看历史周报（对应 proposal/tasks 5.1 的 AI 总结能力）

## 设计产出

- Design Doc：`docs/superpowers/specs/2026-10-26-trade-plan-design.md`
- 模板（v3）：`.comet/handoff/ai-summary-template.md`
- Prompt 约束草案：`.comet/handoff/ai-summary-prompt.md`
