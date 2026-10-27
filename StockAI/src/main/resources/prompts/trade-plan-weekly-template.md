# 交易计划周报模板

```markdown
# {YYYY年第W周} 交易计划周报（{weekStart} ~ {weekEnd}）
生成时间：{generatedAt}

## 一、组合概览
- 持仓 {positionCount} 只，总成本 ¥{totalCost}，总市值 ¥{totalMarketValue}，浮动盈亏 ¥{totalFloatPnl}（{floatPnlRate}%）
- 现金余额：¥{cash}（缺失时写 —）
- 接近止损：{stopLossWarnings} 只；到达止盈位：{targetReached} 只；计划降级：{degradedCount} 只

## 二、本周买卖回顾

### 2.1 买入
- {tradeDate} {stockName}({symbol}) 买入 {quantity} 股，均价 {price}，实际支出 ¥{actualAmount}，方式：{buyMethod}，费用 ¥{fees}
（无买入时写「本周无买入操作」）

### 2.2 卖出
- {tradeDate} {stockName}({symbol}) 卖出 {quantity} 股，均价 {price}，实际到账 ¥{actualAmount}，费用 ¥{fees}
（无卖出时写「本周无卖出操作」）

### 2.3 清仓股复盘
#### {stockName}（{symbol}）
- 清仓日 {clearedDate}，持有 {holdDays} 天，卖出 {sellQuantity} 股，均价 {sellAvgPrice}
- 成本基准 {costBasis}，已实现盈亏 ¥{realizedPnl}（{realizedRate}%）（缺失时写「成本数据缺失」）
- 卖出与计划对照：{exitVsPlan}；原计划要点：{planNote}
- 复盘结论：{一句话，仅基于上述数值与本文对照，不引入外部信息}
（本周无清仓时写「本周无清仓个股」）

## 三、当前持仓交易计划

### {stockName}（{symbol}）
- 仓位与成本：{quantity} 股，均价 {avgCost}，现价 {lastClose}，浮盈 ¥{floatPnl}（{floatPnlRate}%）
- 波动率与关键位：ATR(14)={atr14}（占现价 {atrRatio}%）；支撑 {support20d}；阻力 {resistance20d}
- 止盈止损：建议止损 {stopLossPrice}（距现价 {distToStop}%），建议止盈 {takeProfitPrice}（距现价 {distToTarget}%）；依据：{stopBasis} / {targetBasis}
- 买入方式：{buyMode}（{buyBatches 明细：比例/触发价/金额}）；依据：{buyBasis}
- 补仓规则：{addPositionRule}
- 卖出策略：{sellStrategy}
- 行业：{industry}（缺失写「未分类」）
- 联动状态：{已联动止盈止损提醒 / 未联动}
- 提示：{degradedReason 或「无」}
（按持仓逐只完整输出，不设数量上限）

## 四、行业分布
- 最大行业占比：{top1Ratio}%，行业集中度{判断：高/中/低}
- {industry}：{symbolCount} 只，成本 ¥{cost}，占比 {costRatio}%（按占比降序）
- 未分类：{unclassifiedCount} 只

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
- {stockName}({symbol})｜来源：{board} 榜单｜综合分 {totalScore}｜关注理由：{reason}
（无数据写「暂无最新选股数据」）

## 七、下周行动清单
- {动作：标的 + 触发条件 + 具体价位 + 比例}
（3~5 条；无有效建议时写「本周无新增操作建议，维持既有计划」）

## 八、免责声明
以上内容由量化规则与历史数据生成，仅作辅助参考，不构成投资建议；实际交易由用户自行决策并承担风险。
```
