# 交易计划 AI 周报模板（v2，已按审核意见修订）

- Change: trade-plan
- 生成频率：每周六 08:00（cron `${trade-plan.ai-summary.cron:0 0 8 ? * SAT}`）
- 生成粒度：每个用户一份；范围 = 当前持仓 + 本周清仓个股复盘
- 输出语言：中文；**不设字数上限**（以章节完整、字段齐全为准）
- 存储：`trade_plan_summary` 集合（`user_id + week_start` 去重，保留最近 12 周）
- 展示：交易计划页顶部区块；默认固定高度、内容省略，**必须一眼可见生成日期**；提供「展开/收起」查看全文
- Prompt：约束与模板均以独立 Markdown 文件提供，生成时由运行时读取（见第 7 节）

## 1. 输入数据（后端量化层提供，模型不得自行编造）

### 1.1 用户与汇总

| 字段 | 说明 |
|---|---|
| `user_name` | 用户标识（用于标题，不展示密码等敏感信息） |
| `week_start` / `week_end` / `generated_at` | 周期起止 / 生成时间 |
| `position_count` / `total_cost` / `total_market_value` / `total_float_pnl` / `float_pnl_rate` | 持仓数 / 总成本 / 总市值 / 浮动盈亏 / 收益率 |
| `cash` | 当前现金（缺失为 null） |
| `stop_loss_warnings` / `target_reached` / `degraded_count` | 距止损<3% 持仓数 / 已达止盈位数 / 计划降级数 |

### 1.2 本周买卖记录（`stock_trade` 中本周内 `created_at` 的 BUY/SELL）

| 字段 | 说明 |
|---|---|
| `trade_date` / `side` / `symbol` / `stock_name` | 日期 / 买或卖 / 代码 / 名称 |
| `quantity` / `price` / `actual_amount` | 数量 / 成交价 / 实际金额（买入含费用，卖出已扣费用） |
| `buy_method` | 买入方式（现金 / 银行卡） |
| `fees` | 佣金 + 过户费 + 印花税合计 |

### 1.3 本周清仓复盘（卖出后持仓归零的标的）

| 字段 | 说明 |
|---|---|
| `symbol` / `stock_name` | 代码 / 名称 |
| `cleared_date` / `hold_days` | 清仓日期 / 持有天数 |
| `sell_quantity` / `sell_avg_price` | 本周卖出数量 / 卖出均价 |
| `cost_basis` | 按历史交易回放计算的平均成本基准 |
| `realized_pnl` / `realized_rate` | 已实现盈亏 / 收益率 |
| `exit_vs_plan` | 卖出与计划对照：`止盈位附近 / 止损位附近 / 计划外` |
| `plan_note` | 该股原计划要点（止盈止损位、卖出策略） |

> 成本基准由交易流水按时间顺序回放（加权平均）计算；数据不足时该股 `cost_basis`、`realized_pnl`、`realized_rate` 为 null，模板中标注「成本数据缺失」。

### 1.4 当前持仓交易计划（逐仓，字段与单股计划卡片一致）

`stock_name`、`symbol`、`quantity`、`avg_cost`、`last_close`、`float_pnl`、`float_pnl_rate`、`atr14`、`atr_ratio`、`support_20d`、`resistance_20d`、`stop_loss_price`、`take_profit_price`、`dist_to_stop`、`dist_to_target`、`buy_mode`、`buy_batches`、`add_position_rule`、`sell_strategy`、`degraded`、`degraded_reason`、`monitor_linked`、`industry`

### 1.5 行业分布

`industry_rows`：每项 `industry`、`symbol_count`、`cost`、`cost_ratio`、`symbols`；`industry_unclassified_count`；`top1_ratio`（最大行业占比）。

### 1.6 下周关注方向

- `focus_industries`：最近一期热点结果 Top 3~5，字段 `name`、`hotScore`、`hypeLabel`、`hypeBasis`、`topStocks`（各含名称/代码）
- `focus_stocks`：最近一期筛选榜单（均衡/价值/成长）Top 3 与持仓行业重合者，最多 5 只，字段 `symbol`、`name`、`totalScore`、`board`（均衡/价值/成长）、`reason`
- 两者任一缺失时模板输出「暂无最新热点/选股数据」，不得编造

## 2. 固定输出模板（章节顺序与标题不得改动）

```markdown
# {YYYY年第W周} 交易计划周报（{week_start} ~ {week_end}）
生成时间：{generated_at}

## 一、组合概览
- 持仓 {position_count} 只，总成本 ¥{total_cost}，总市值 ¥{total_market_value}，浮动盈亏 ¥{total_float_pnl}（{float_pnl_rate}%）
- 现金余额：¥{cash}（缺失时写 —）
- 接近止损：{stop_loss_warnings} 只；到达止盈位：{target_reached} 只；计划降级：{degraded_count} 只

## 二、本周买卖回顾

### 2.1 买入
- {trade_date} {stock_name}({symbol}) 买入 {quantity} 股，均价 {price}，实际支出 ¥{actual_amount}，方式：{buy_method}，费用 ¥{fees}
（无买入时写「本周无买入操作」）

### 2.2 卖出
- {trade_date} {stock_name}({symbol}) 卖出 {quantity} 股，均价 {price}，实际到账 ¥{actual_amount}，费用 ¥{fees}
（无卖出时写「本周无卖出操作」）

### 2.3 清仓股复盘
#### {stock_name}（{symbol}）
- 清仓日 {cleared_date}，持有 {hold_days} 天，卖出 {sell_quantity} 股，均价 {sell_avg_price}
- 成本基准 {cost_basis}，已实现盈亏 ¥{realized_pnl}（{realized_rate}%）（缺失时写「成本数据缺失」）
- 卖出与计划对照：{exit_vs_plan}；原计划要点：{plan_note}
- 复盘结论：{一句话，仅基于上述数值与计划对照，不引入外部信息}
（本周无清仓时写「本周无清仓个股」）

## 三、当前持仓交易计划

### {stock_name}（{symbol}）
- 仓位与成本：{quantity} 股，均价 {avg_cost}，现价 {last_close}，浮盈 ¥{float_pnl}（{float_pnl_rate}%）
- 波动率与关键位：ATR(14)={atr14}（占现价 {atr_ratio}%）；支撑 {support_20d}；阻力 {resistance_20d}
- 止盈止损：建议止损 {stop_loss_price}（距现价 {dist_to_stop}%），建议止盈 {take_profit_price}（距现价 {dist_to_target}%）；依据：{依据说明}
- 买入方式：{buy_mode}（{buy_batches 明细}）；依据：{波动率档位}
- 补仓规则：{add_position_rule}
- 卖出策略：{sell_strategy}
- 行业：{industry}（缺失写「未分类」）
- 联动状态：{已联动止盈止损提醒 / 未联动}
- 提示：{degraded_reason 或「无」}
（按持仓逐只完整输出，不设数量上限）

## 四、行业分布
- 最大行业占比：{top1_ratio}%，行业集中度{判断：高/中/低}
- {industry}：{symbol_count} 只，成本 ¥{cost}，占比 {cost_ratio}%（按占比降序）
- 未分类：{industry_unclassified_count} 只

## 五、风险与关注
1. 【止损风险】{列出距止损 <3% 的持仓与距离；无则写「无」}
2. 【止盈机会】{列出距止盈 <3% 或已达止盈位的持仓；无则写「无」}
3. 【波动异常】{ATR÷现价处于组合前 20% 的持仓；无则写「无」}
4. 【计划降级】{降级持仓与原因；无则写「无」}
5. 【行业集中】{最大行业占比 ≥40% 时给出提示；否则写「集中度可控」}

## 六、下周关注方向

### 6.1 行业方向
- {name}｜热度 {hotScore}｜{hypeLabel}｜依据：{hypeBasis}｜代表个股：{topStocks}
（无数据写「暂无最新热点数据」）

### 6.2 个股观察
- {stock_name}({symbol})｜来源：{board} 榜单｜综合分 {totalScore}｜关注理由：{reason}
（无数据写「暂无最新选股数据」）

## 七、下周行动清单
- {动作：标的 + 触发条件 + 具体价位 + 比例}
（3~5 条；无有效建议时写「本周无新增操作建议，维持既有计划」）

## 八、免责声明
以上内容由量化规则与历史数据生成，仅作辅助参考，不构成投资建议；实际交易由用户自行决策并承担风险。
```

## 3. 展示约定（前端）

- 位置：交易计划页顶部「本周周报」区块，**仅展示最新一期**
- 默认：内容不全展示（固定高度省略），标题旁必须显示生成时间（`生成时间：YYYY-MM-DD HH:mm`）
- 展开：点击「展开」后在本区块内渲染全部报告内容，下方内容整体下移；按钮切换为「收起」
- 历史：区块内提供「历史」按钮，点击弹出历史报告弹窗；弹窗左侧为历史报告列表（按生成时间倒序，最多 12 期），右侧展示选中期的完整报告内容；默认选中最新一期
- 接口：`POST /trade-plan/summary/latest`（最新一期）、`POST /trade-plan/summary/list`（历史列表）、`POST /trade-plan/summary/detail`（按 `week_start` 取全文）

## 4. 模板修订记录

| 版本 | 变更 |
|---|---|
| v1 | 初稿（组合概览 / 逐仓 / 风险 / 行动清单 / 免责） |
| v2 | 新增「本周买卖回顾（含清仓复盘）」「行业分布」「下周关注方向」；取消字数上限；约束独立成 prompt 文件 |
| v3 | 展示改为：顶部仅最新一期折叠展示 + 「展开」内联全文（下方内容下移）+ 「历史」弹窗（左侧列表 / 右侧内容） |
