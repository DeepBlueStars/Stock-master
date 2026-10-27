---
comet_change: trade-plan
role: technical-design
canonical_spec: openspec
---

# 交易计划（trade-plan）技术设计

> 上游事实源：`docs/openspec/changes/trade-plan/proposal.md`、`design.md`、`specs/trade-plan/spec.md`
> 本文件是深度技术细化，不替代 delta spec；需求口径以 delta spec 为准。

## 1. 目标与范围

买入后基于 ATR/持仓成本/趋势生成可解释的交易计划，并与现有止盈止损提醒联动；每周六生成一份交易计划周报（固定模板）。

**范围内**：计划计算、计划存储、计划查询、一键写入 PositionMonitor、周报定时生成与展示。
**范围外**：自动下单、行情采集改动、既有 `RealizedReturn` 算法修正、多用户权限体系。

## 2. 架构与模块

```
StockService.buy() ──try──> TradePlanService.refreshPlan(userId, symbol)
                                ├─ IndicatorCalculator    ATR(14) / 支撑阻力（纯计算）
                                ├─ TradePlanCalculator    止盈止损 / 批次 / 补仓 / 卖出策略
                                └─ TradePlanRepository    user_id+symbol upsert

TradePlanService.list(userId)            逐仓计划（缺失时幂等补生成）
TradePlanService.linkMonitor(userId, symbol)  写入 PRICE 类型止盈止损 + before/after

TradePlanSummaryTask（周六 08:00） → TradePlanSummaryService
   ├─ TradePlanSummaryDataAssembler  汇总/买卖/清仓回放/行业分布/热点与榜单
   ├─ ChatClient + 两个 prompt 资源   生成周报
   ├─ 结构校验 → 失败则量化文本 fallback
   └─ TradePlanSummaryRepository      user_id+week_start upsert
```

新增包 `com.xian.stockai.service.tradeplan`：

| 类 | 职责 | 依赖 |
|---|---|---|
| `IndicatorCalculator` | 由K线序列计算 ATR(14)、近 20 日支撑/阻力、ATR 占比 | 无（静态纯函数，便于单测） |
| `TradePlanCalculator` | 由成本/ATR/趋势/资金生成计划字段与解释文本 | `TradePlanProperties` |
| `TradePlanService` | 生成/刷新/查询计划，联动写入提醒 | 上述 + 各 Repository + `MonitorService` |
| `TradePlanSummaryDataAssembler` | 组装周报输入数据（含交易回放） | 各 Repository |
| `TradePlanSummaryService` | 生成/校验/降级/存储周报 | `ChatClient`、`TradePlanSummaryDataAssembler`、prompt Resource |
| `TradePlanSummaryTask` | 定时触发全部用户周报 | `TradePlanSummaryService` |

配置类 `com.xian.stockai.config.TradePlanProperties`（`@ConfigurationProperties(prefix = "trade-plan")`），字段：`atrPeriod`、`stopAtrMultiplier`、`riskReward`、`supportResistanceWindow`、`highVolatilityThreshold`、`midVolatilityThreshold`、`minKlineCount`、`degradedStopLossRate`、`degradedTakeProfitRate`、`summaryCron`、`summaryRetentionWeeks`。

## 3. 指标与规则（可复现定义）

### 3.1 ATR（Wilder）

- `TR_t = max(high_t - low_t, |high_t - close_{t-1}|, |low_t - close_{t-1}|)`
- 首个 ATR 取前 `atrPeriod` 个 TR 的算术平均；其后 `ATR_t = (ATR_{t-1} × (n-1) + TR_t) / n`，`n = atrPeriod`
- K线按 `trade_date` 升序；有效K线数量 `< minKlineCount(15)` → 计划降级

### 3.2 支撑 / 阻力

- 取最近 `supportResistanceWindow(20)` 根K线
- `support_20d = min(low)`、`resistance_20d = max(high)`

### 3.3 止盈止损

- `stop_loss_price = avg_cost - stopAtrMultiplier × ATR`（保留 2 位，向下取整到 0.01）
- 风险 `risk = avg_cost - stop_loss_price`
- `take_profit_price = avg_cost + riskReward × risk`
- 若 `stop_loss_price <= 0`（ATR 过大）→ 降级：`stop_loss_price = avg_cost × (1 - 5%)`、`take_profit_price = avg_cost × (1 + 10%)`
- `stop_basis`/`target_basis` 为可解释文本，例如「成本 12.00 − 2.0×ATR 0.35」「成本 12.00 + 2.0×风险 0.70」

### 3.4 买入方式与批次

- `atr_ratio = atr / last_close`
- `atr_ratio ≥ 3%` → `BATCHED`，3 批（50%/30%/20%）；`2% ≤ atr_ratio < 3%` → `BATCHED`，2 批（60%/40%）均分两次；`< 2%` → `LUMP_SUM`
- 各批触发价：第一批 = 当前价；第二批 = 当前价 − 0.5×ATR；第三批 = 当前价 − 1.0×ATR（不低于支撑位；低于支撑则取支撑位）
- 金额：`cash_basis = REMAINING_CASH` 时按 `available_cash` 计算每批金额；否则 `cash_basis = UNKNOWN`，只给比例

### 3.5 补仓规则

- 触发条件：价格回落至 `support_20d` 且 `last_close > stop_loss_price`（趋势未破）
- 补仓比例：不超过当前持仓市值的 30%（`add_position_rule.ratio = 0.3`）
- 若 `support_20d >= last_close`（已跌破）→ 文本改为「已跌破支撑，暂不补仓，等待重新站上支撑」

### 3.6 卖出策略

- 止盈：到 `take_profit_price` 减仓 50%
- 止损：跌破 `stop_loss_price` 全部清仓
- 附带阻力位提示：`last_close` 距 `resistance_20d` 不足 2% 时提示「接近近 20 日阻力，注意分批减仓」

## 4. 数据模型

### 4.1 `trade_plans`（`user_id + symbol` 唯一）

| 字段 | 类型 | 说明 |
|---|---|---|
| id / user_id / symbol / stock_name / industry | String | 基础标识 |
| quantity / avg_cost / last_close | Number | 持仓与现价 |
| as_of_date | String | 计算基准交易日 |
| atr14 / atr_ratio | Number | ATR 与占比 |
| support_20d / resistance_20d | Number | 关键位 |
| stop_loss_price / take_profit_price | Number | 建议价位 |
| stop_basis / target_basis | String | 计算依据 |
| buy_mode | String | BATCHED / LUMP_SUM |
| buy_batches | List<{ratio, price, amount}> | 批次明细 |
| buy_basis | String | 波动率档位说明 |
| add_position_rule | {trigger_price, ratio, condition_text} | 补仓规则 |
| sell_strategy | {take_profit_action, stop_loss_action, resistance_note} | 卖出策略 |
| available_cash / cash_basis | Number / String | REMAINING_CASH / UNKNOWN |
| degraded / degraded_reason | Boolean / String | 降级标记与原因 |
| monitor_linked | Boolean | 是否已写入提醒 |
| generated_at / updated_at | LocalDateTime | 时间戳 |

索引：`CompoundIndex(user_id, symbol)` 唯一。

### 4.2 `trade_plan_summary`（`user_id + week_start` 唯一）

`week_start`（ISO 周一日期字符串）、`week_end`、`generated_at`、`content`（Markdown 全文）、`status`（success/fallback）、`fallback_reason`、`created_at`、`updated_at`。保留最近 12 期（超出按 `week_start` 升序删除）。

### 4.3 只读模型扩展

`StockDailyKline` 增加 `open`、`high`、`low` 字段映射（集合已含这些字段，无需迁移）。

## 5. 接口（`@RequestMapping("/trade-plan")`）

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| POST | `/list` | `{userId}` | `Result<List<TradePlanRes>>`；缺失计划的持仓按需生成 |
| POST | `/link-monitor` | `{userId, symbol}` | `Result<MonitorLinkRes>`：`before{takeProfitValue, stopLossValue, enabled}`、`after{...}` |
| POST | `/summary/latest` | `{userId}` | `Result<TradePlanSummaryRes>`（含 content、generatedAt、status） |
| POST | `/summary/list` | `{userId}` | `Result<List<TradePlanSummaryBriefRes>>`（weekStart、generatedAt、status） |
| POST | `/summary/detail` | `{userId, weekStart}` | `Result<TradePlanSummaryRes>` |
| POST | `/summary/generate` | `{userId}` | `Result<TradePlanSummaryRes>`（手动生成当期，供验证与补偿） |

`link-monitor` 规则（已确认方案 A）：按 `user_id + symbol` 查找 `PositionMonitor`；存在则覆盖 `takeProfitType/Value = PRICE/take_profit_price`、`stopLossType/Value = PRICE/stop_loss_price`，保留 `enabled`；不存在则新建并 `enabled=true`；写入成功后把计划 `monitor_linked` 置 true。

## 6. 周报生成

### 6.1 触发与范围

- `TradePlanSummaryTask`：`@Scheduled(cron = "${trade-plan.summary-cron:0 0 8 ? * SAT}")`
- 遍历 `position` 集合中的全部 `user_id`（持仓数量 > 0），逐个生成当期周报
- 单个用户失败仅记录日志与失败计数，不阻断其余用户

### 6.2 数据组装

| 数据块 | 来源 |
|---|---|
| 汇总 | `position` + `trade_plans` + 现价（`market_quotes` / `etf_daily_quotes`） |
| 本周买卖 | `stock_trade` 中 `created_at ∈ [week_start, week_end]`，按 BUY/SELL 分组 |
| 清仓复盘 | 本周 SELL 后持仓数量归零的 symbol；成本基准由该 symbol 全部交易按时间回放加权平均 |
| 行业分布 | `stock_industry_concept` 的 `industry`，缺失归入「未分类」；按持仓成本占比排序 |
| 关注方向 | 最近一期 `hotspot_result`（industries Top5）+ 最近一期 `screening_result`（三榜 Top3 与持仓行业重合者，最多 5 只） |

交易回放算法：按 `created_at` 升序，买入时 `qty += q, cost += actual_amount`；卖出时 `costPerShare = cost / qty`，`realized += price × q − costPerShare × q`，`qty -= q, cost -= costPerShare × q`；任一时点 `qty <= 0` 或字段缺失 → 该股判定「成本数据缺失」。

### 6.3 Prompt 与校验

- 约束：`classpath:/prompts/trade-plan-weekly-report.md`
- 模板：`classpath:/prompts/trade-plan-weekly-template.md`
- system 使用约束文件全文；user 为「模板 + 数据 JSON」
- 结构校验：八个章节标题齐全、包含免责声明、当前持仓每只都有条目；失败 → fallback
- fallback：量化文本周报（同模板、固定句式、只填数值），`status=fallback`，`fallback_reason` 记录原因

## 7. 前端设计

### 7.1 路由与菜单

- `src/router/index.js` 新增 `{ path: '/trade-plan', name: '交易计划', component: TradePlan }`
- `src/App.vue` 侧边菜单新增「交易计划」项（排在「选股」之后）

### 7.2 页面结构 `src/components/TradePlan.vue`

- 顶部「本周周报」区块（`WeeklySummaryBlock.vue`）
  - 标题 + 生成时间（始终可见）
  - 默认折叠（固定高度、内容省略）
  - 「展开」→ 区块内渲染全文，下方内容整体下移；再点「收起」
  - 「历史」→ `el-dialog`：左侧历史列表（最多 12 期，倒序），右侧选中期全文；默认选中最新一期
  - 无周报：空态「暂无周报，每周六 08:00 自动生成」+「立即生成」按钮（调 `summary/generate`）
- 逐仓计划卡片（`TradePlanCard.vue`，`el-card` 栅格）
  - 头部：名称(代码)、行业、`degraded` 标签
  - 主体：成本/现价/浮盈 · 止损止盈价与距离 · ATR(14)/支撑/阻力 · 批次明细 · 补仓规则 · 卖出策略
  - 底部：「写入止盈止损提醒」按钮；成功后 `ElMessage` 展示 before → after 并刷新列表
  - 无持仓：空态提示去「股票」页买入

### 7.3 接口调用

复用现有 `axios` + `BASE_URL`（`src/config/config.js`）方式，全部 `POST`。

## 8. 异常与边界

| 场景 | 处理 |
|---|---|
| 无历史K线 / 不足 15 根 | 计划降级（固定 -5%/+10%），`degraded=true`，卡片显示「计划降级」标签与原因 |
| 现价缺失 | 计划仍生成但 `last_close` 为 null 时跳过距离字段；列表中该卡片显示「现价缺失」 |
| `avg_cost` 为 0 或空 | 不生成计划，记录日志；列表跳过该持仓 |
| 买入后计划生成失败 | 仅日志，买入返回不变 |
| 提醒规则不存在 | 新建并启用 |
| 周报所需热点/榜单缺失 | 对应章节写「暂无最新热点数据 / 暂无最新选股数据」 |
| LLM 超时/异常/输出结构不合规 | 量化文本 fallback，`status=fallback` |
| 用户无持仓 | 不生成周报；`latest` 返回 null 并给出空态 |
| 重复生成同一周 | `user_id + week_start` upsert 覆盖 |

## 9. 测试策略

| 测试类 | 覆盖点 |
|---|---|
| `TradePlanIndicatorTest` | ATR 与标准样例一致、Wilder 递推、支撑阻力取极值、K线不足返回降级信号 |
| `TradePlanCalculatorTest` | 止损/止盈公式、风险回报、三档波动率边界（1.99%/2.00%/2.99%/3.00%）、批次触发价不低于支撑、补仓/卖出文本、现金缺失分支 |
| `TradePlanServiceTest` | 首次生成、再次买入覆盖、`list` 幂等补生成、`link-monitor` 覆盖/新建两分支与 before/after |
| `TradePlanSummaryServiceTest` | 数据组装、prompt 资源加载、结构校验失败降级、ChatClient 异常降级、upsert 幂等 |
| `TradePlanSummaryTaskTest` | 多用户遍历、单用户失败不阻断、失败计数 |
| `TradePlanDataAssemblerTest` | 交易回放加权平均、清仓识别、成本缺失分支、行业分布排序与未分类 |
| `TradePlanPoTest` | 集合名、索引、字段映射（仿 `ScreenerPoTest`） |
| `TradePlanControllerTest` | 六个接口委派与参数传递 |

前端无测试框架，交付人工验证清单：折叠默认态/展开/收起、历史弹窗左右联动、写入提醒前后对比、降级标签、空态。

## 10. 风险与取舍

- ATR 依赖 high/low：集合已有字段，仅需补只读映射；历史脏数据缺失时走降级，不报错
- 买入链路同步计算（约 1 次 K线查询 + 纯计算）：耗时可接受；异常隔离保证交易成功
- 覆盖用户手工提醒规则（已确认方案 A）：以 before/after 提示降低误操作风险
- 周报 LLM 成本与耗时：按用户逐个生成、失败降级；不做并发池，避免新增线程池基础设施
- 不修正既有 `RealizedReturn` 算法：清仓复盘使用独立回放，避免扩大范围

## 11. 实施顺序（对应 tasks.md）

1. 指标与关键位（1.1、1.2）→ `IndicatorCalculator` + 单测
2. 计划生成（2.1~2.3）→ `TradePlanCalculator` + 单测
3. 存储（3.1）→ PO/Repository/VO + 买入链路接入
4. 联动（4.1）→ `linkMonitor` + 接口 + 单测
5. 周报与前端（5.1、5.2）→ 数据组装、prompt 文件、任务、接口、页面
