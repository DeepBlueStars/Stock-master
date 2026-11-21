# 个股关注接入方案（给 OpenClaw 阅读）

> 版本：2026-11-21 草案（待用户确认后实现）
> 适用：个股深度报告 → 报告库 + 复核台「关注池」
> 样例：`恒瑞医药_20261119.pdf`
> 配套设计：`docs/个股关注台-方案-20261121.md`

---

## 0. 你要做什么

每分析完一只股票，按顺序完成四件事：

1. 把**整份 Markdown 报告**存进报告库，同时给一句投资结论作为列表摘要；
2. 把报告 **§10 买入计划与卖出纪律、§12 监测清单、§14 综合决策备忘录** 压缩成结构化摘要，写进关注档案（落在关注池 `user_favorites` 该股票的 `focus` 字段，股票不在池里会自动加入）；
3. 带明确日期的监测项由服务端**自动同步**到监测日历（你不需要再单独写一遍）；
4. 如果这只票已经建仓，再补一次卖出价位（`save_review_levels`）；关注阶段不要写。

**你不要做的事**：

- 不要传报告原文表格或整段论证，系统只要结论；
- 不要自己判断「现在该不该买」，只搬运报告里的计划；
- 不要因为价格到了就改状态，状态必须显式调用接口；
- 不要发明报告里没有的数字、日期、评级。

---

## 1. 调用顺序

```
① save_research_report   → 拿到 reportId
② save_focus_stock       → 传 reportId + §10/§12/§14 摘要
③ （服务端自动）sync monitor_event
④ 仅当已建仓：save_review_levels
```

### 1.1 第一步：存整份报告

```
save_research_report(
  type      = "STOCK",
  title     = "恒瑞医药 600276 · 2026-11-19 个股深度",
  symbol    = "600276",
  reportDate= "2026-11-19",
  rating    = "A",
  tagsCsv   = "创新药,观察,恒瑞医药",
  summary   = "好公司但价格不对：46.88 元对应 2026E 33.7x，三年期望年化 3.7%。观察，不买。",
  content   = "<整份 Markdown>"
)
```

返回示例：

```json
{"state":0,"data":{"id":"6743...","title":"恒瑞医药 600276 · 2026-11-19 个股深度"}}
```

- `reportDate` 用**报告的数据日**（2026-11-19），不是今天；
- `rating` 填报告里明确给出的评级；没有就留空，不要编；
- `summary` 给 1–2 句结论（≤120 字），个股报告不要让它取到「信息丰富度 A 级」这种非投资结论；
- `content` 传整份 Markdown，不要传 PDF 二进制，不要只传摘要。

### 1.2 第二步：写关注档案

```
save_focus_stock(
  symbol              = "600276",
  stockName           = "恒瑞医药",
  reportId            = "<上一步返回的 id>",
  reportDate          = "2026-11-19",
  rating              = "A",
  status              = "WATCHING",
  stance              = "观察，不买入",
  oneLine             = "等价格，不等故事",
  coreThesis          = "创新药平台型公司，收入=创新药+仿制药+BD 授权",
  buyPlanJson         = "<见 §2.1>",
  sellDisciplineJson  = "<见 §2.2>",
  monitorItemsJson    = "<见 §2.3>",
  decisionMemoJson    = "<见 §2.4>"
)
```

- `status` 首次写入固定 `WATCHING`；
- 同一 `symbol` 再调用会**整体覆盖**上一份档案，所以每次都要传完整清单；
- `reportId` 必须传，否则页面上「来源报告」是空的。

### 1.3 第三步：不需要你手动写日历

`monitorItemsJson` 里凡是带 `eventDate` 的项，服务端会自动生成 `monitor_event`。只有**不是监测指标**的事件（例如股东大会、指数调整）才需要另外调用 `save_monitor_events`。

### 1.4 第四步：建仓后才写卖出线

只有 `关注档案 status` 已经变成 `HOLDING` 时，才调用 `save_review_levels`，把 §10.3 的目标价写成 `review_level`。关注阶段重复写会造成页面上两套价位。

---

## 2. 字段规范

### 2.1 `buyPlanJson`

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `maxBuyPrice` | 否 | number | 高于此价不追，如 43 |
| `maxShares` | 否 | int | 最大股数，如 200 |
| `maxWeightPct` | 否 | number | 最大占总资产比例，如 16 |
| `holdingPeriod` | 否 | string | 如「3-5 年」 |
| `rules` | 否 | string[] | 执行规则，最多 6 条，每条 ≤50 字 |
| `tiers` | **是** | array | 至少 1 档，按价格从高到低 |

`tiers[]`：

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `tier` | 是 | int | 1、2… |
| `label` | 否 | string | 第一档 |
| `priceLow` / `priceHigh` | 是 | number | 买入区间 |
| `shares` | 是 | int | 该档股数 |
| `amountLow` / `amountHigh` | 否 | number | 金额区间 |
| `weightPct` | 否 | number | 占总资产比例 |
| `condition` | 否 | string | 触发条件，≤60 字 |
| `note` | 否 | string | 补充 |

> 若报告只给了一个价格，`priceLow = priceHigh`；不要在系统里自行扩展区间。

### 2.2 `sellDisciplineJson`

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `targets` | 是 | array | 目标价档位，按价格升序 |
| `invalidation` | 是 | string[] | 基本面证伪条件，≤8 条 |
| `stops` | 否 | object | 四类止损 |
| `addSignals` | 否 | string[] | 加仓信号，≤6 条 |

`targets[]`：`{ "priceLow": 52, "priceHigh": 56, "valuation": "2027E 32.5-35x", "action": "持有，不追" }`，只有单一时可省略 `priceHigh`。

`stops`：`{ "fundamental": "...", "priceStop": "...", "portfolioStop": "...", "systematicException": "..." }`。

### 2.3 `monitorItemsJson`

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `key` | 是 | string | 稳定标识，大写+下划线，同一指标每次用同一个 |
| `metric` | 是 | string | 指标名 |
| `currentValue` | 是 | string | 现值，带单位，如 `+17.05%`、`27.81 亿` |
| `threshold` | 是 | string | 警戒线 |
| `action` | 是 | string | 触发后做什么，≤30 字 |
| `importance` | 否 | string | `HIGH` / `MEDIUM` / `LOW` |
| `eventDate` | 否 | string | `yyyy-MM-dd`，有日期才会进日历 |
| `cadence` | 否 | string | `QUARTERLY` / `ONCE` / `EVENT` |
| `previousValue` | 否 | string | 上一份报告的值 |
| `trend` | 否 | string | `UP` / `DOWN` / `FLAT` |

> `key` 必须稳定：这次叫 `INNOVATION_GROWTH`，下次不能改成 `INNOVATIVE_DRUG_GROWTH`，否则监测项会被当成两个。

### 2.4 `decisionMemoJson`

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `dimensions` | 是 | array | 六维，固定顺序：生意质量、护城河、管理层、最大风险、文明趋势、估值 |
| `dimensions[].name` | 是 | string | 维度名 |
| `dimensions[].conclusion` | 是 | string | ≤70 字，先结论后数字 |
| `dimensions[].confidence` | 是 | string | `HIGH` / `MEDIUM_HIGH` / `MEDIUM` / `LOW` |
| `emptyPosition` | 是 | string | 空仓者怎么做，≤80 字 |
| `holder` | 是 | string | 持仓者怎么做，≤80 字 |
| `sellSignals` | 否 | string[] | ≤6 条 |
| `addSignals` | 否 | string[] | ≤6 条 |
| `oneLine` | 是 | string | 一句话总结，≤60 字 |

---

## 3. 完整示例：恒瑞医药 600276

### 3.1 `buyPlanJson`

```json
{
  "maxBuyPrice": 43,
  "maxShares": 200,
  "maxWeightPct": 16,
  "holdingPeriod": "3-5 年",
  "rules": [
    "43 元以上不买，50 元以上不看",
    "跌破 38 元且基本面恶化，取消第二档，不摊平",
    "只到 43 元就反弹，只持 100 股，不追加",
    "单票上限 20% = 10,200 元，200 股涨到 50 元也只占 19.6%"
  ],
  "tiers": [
    {
      "tier": 1,
      "label": "第一档",
      "priceLow": 42,
      "priceHigh": 43,
      "shares": 100,
      "amountLow": 4200,
      "amountHigh": 4300,
      "weightPct": 8.3,
      "condition": "价格到位 + 2026Q4 创新药（扣BD）增速 ≥15% + 无 BD 终止/双艾失败"
    },
    {
      "tier": 2,
      "label": "第二档",
      "priceLow": 38,
      "priceHigh": 39,
      "shares": 100,
      "amountLow": 3800,
      "amountHigh": 3900,
      "weightPct": 7.6,
      "condition": "价格到位 + 2027Q1 创新药（扣BD）增速 ≥15% + 双艾 FDA 按计划推进"
    }
  ]
}
```

### 3.2 `sellDisciplineJson`

```json
{
  "targets": [
    {"priceLow": 52, "priceHigh": 56, "valuation": "2027E 32.5-35x", "action": "持有，不追；仓位 >20% 可微调"},
    {"priceLow": 65, "valuation": "2028E 35x", "action": "减仓 1/3"},
    {"priceLow": 75, "valuation": "2028E 41x", "action": "再减 1/3"},
    {"priceLow": 85, "valuation": "2028E 47x", "action": "清仓或只留 100 股观察"}
  ],
  "invalidation": [
    "创新药（扣BD）连续 2 季 <10%，或 2027 全年 <12%",
    "现有 BD 被终止/退货，或连续 4 季无新 BD 且 2027 BD 收入 <20 亿",
    "双艾 FDA 再 CRL，或 PDUFA 推迟到 2028 年后",
    "HRS9531 全球 III 期未达主要终点，或 KRAS G12D / HER2 ADC 关键数据失败",
    "前三大品种谈判降价 >50%，或核心品种被调出医保",
    "孙飘扬突然离任且无认可继任者；核心研发高管集体离职；研发资本化率 >30%",
    "经营现金流连续 2 季为负；净现金 <300 亿；应收增速持续高于营收"
  ],
  "stops": {
    "fundamental": "触发任一证伪条件 → 全部卖出，不等价格",
    "priceStop": "无基本面消息跌破成本 -20% 减半，-25% 清仓",
    "portfolioStop": "占组合 >20% 强制减至 20%；单票亏损 >25% 且基本面恶化清仓",
    "systematicException": "沪深300 同期跌幅 >10% 且无个股利空，不触发价格止损，按计划买入下一档，不超 200 股"
  },
  "addSignals": [
    "价格 ≤43 元（第一档）",
    "价格 ≤38.5 元（第二档）",
    "2026Q4 创新药（扣BD）增速 ≥20%",
    "双艾 FDA 2027-04-09 获批",
    "HRS9531 中国获批上市、首年销售 >10 亿元",
    "新 BD 首付 ≥3 亿美元"
  ]
}
```

### 3.3 `monitorItemsJson`

```json
[
  {"key": "INNOVATION_GROWTH", "metric": "创新药收入（扣BD）增速", "currentValue": "+17.05%",
   "threshold": "连续 2 季 <10%", "action": "下调估值，暂停加仓", "importance": "HIGH", "cadence": "QUARTERLY"},
  {"key": "NON_ONCO_GROWTH", "metric": "非肿瘤创新药增速", "currentValue": "+75.36%",
   "threshold": "<30%", "action": "增长逻辑受损", "importance": "MEDIUM", "cadence": "QUARTERLY"},
  {"key": "GENERIC_GROWTH", "metric": "仿制药收入增速", "currentValue": "-16.41%",
   "threshold": "跌幅扩大至 -25%", "action": "拖累超预期", "importance": "MEDIUM", "cadence": "QUARTERLY"},
  {"key": "BD_REVENUE", "metric": "BD 收入", "currentValue": "27.81 亿",
   "threshold": "全年 <25 亿", "action": "BD 空窗", "importance": "HIGH", "cadence": "QUARTERLY"},
  {"key": "OPERATING_CASHFLOW", "metric": "经营现金流", "currentValue": "78.44 亿",
   "threshold": "连续 2 季为负", "action": "利润质量恶化", "importance": "HIGH", "cadence": "QUARTERLY"},
  {"key": "RND_RATIO", "metric": "研发投入/营收", "currentValue": "29.3%",
   "threshold": "<25%", "action": "管线投入不足", "importance": "MEDIUM", "cadence": "QUARTERLY"},
  {"key": "CAP_RATE", "metric": "研发资本化率", "currentValue": "24.1%",
   "threshold": ">30%", "action": "利润质量黄色", "importance": "MEDIUM", "cadence": "QUARTERLY"},
  {"key": "NET_CASH", "metric": "净现金", "currentValue": "430.25 亿",
   "threshold": "<300 亿", "action": "资产负债表恶化", "importance": "HIGH", "cadence": "QUARTERLY"},
  {"key": "FDA_SHUANGAI", "metric": "双艾 FDA", "currentValue": "PDUFA 2027-04-09",
   "threshold": "再 CRL / 推迟", "action": "海外逻辑受损", "importance": "HIGH",
   "eventDate": "2027-04-09", "cadence": "ONCE"},
  {"key": "HRS9531_PH3", "metric": "HRS9531 全球 III 期", "currentValue": "2028 年中读出",
   "threshold": "主要终点失败", "action": "最大期权破灭", "importance": "HIGH",
   "eventDate": "2028-06-30", "cadence": "ONCE"},
  {"key": "MANAGEMENT", "metric": "孙飘扬/核心团队", "currentValue": "孙 68 岁，连任至 2029",
   "threshold": "突然离任", "action": "治理风险", "importance": "MEDIUM", "cadence": "EVENT"},
  {"key": "VALUATION", "metric": "估值", "currentValue": "2026E 33.7x",
   "threshold": ">40x", "action": "减仓", "importance": "MEDIUM", "cadence": "QUARTERLY"}
]
```

> `HRS9531_PH3` 报告只写「2028 年中读出」，`eventDate` 是估算的占位日期，`currentValue` 保留原文，不要写成精确日期。

### 3.4 `decisionMemoJson`

```json
{
  "dimensions": [
    {"name": "生意质量", "conclusion": "优秀：创新药 64.59%、毛利率 86.78%、净现金 430 亿；但营收仅 +5.3%，利润依赖 BD", "confidence": "HIGH"},
    {"name": "护城河", "conclusion": "强且在变宽，2026 年变宽放缓；平台、临床规模、医保准入、BD 信誉四条护城河", "confidence": "HIGH"},
    {"name": "管理层", "conclusion": "优秀但有接班风险；孙飘扬 68 岁，连任至 2029", "confidence": "MEDIUM"},
    {"name": "最大风险", "conclusion": "BD 不可持续 + 创新药增速跌破 10% + 双艾/管线失败 + 接班", "confidence": "MEDIUM"},
    {"name": "文明趋势", "conclusion": "顺风：中国创新药全球份额提升、老龄化、医保双目录；不会赢家通吃", "confidence": "HIGH"},
    {"name": "估值", "conclusion": "三年期望年化 3.7%，合理偏贵；十年 IRR 1.0%，估值不支持买入", "confidence": "MEDIUM_HIGH"}
  ],
  "emptyPosition": "观察，不买。46.88 元不追；42 元以下分两档各 100 股，最大 200 股 / 16%",
  "holder": "持有不加；65 元以上减 1/3，75 元以上再减 1/3；基本面证伪清仓",
  "sellSignals": [
    "创新药（扣BD）连续 2 季 <10%",
    "BD 终止/空窗",
    "双艾再 CRL",
    "核心管线失败",
    "孙飘扬突然离任",
    "净现金 <300 亿"
  ],
  "addSignals": [
    "≤43 元",
    "≤38.5 元",
    "Q4 创新药 ≥20%",
    "双艾获批",
    "HRS9531 首年 >10 亿",
    "新 BD 首付 ≥3 亿美元"
  ],
  "oneLine": "等价格，不等故事。"
}
```

---

## 4. 摘要规则

1. **保留数字，删掉论证**：`42 元以下买 100 股` 要留；「为什么 42 元合理」留在报告库。
2. **一条只表达一个动作**：`跌破 -20% 减半，-25% 清仓` 拆成两条，不要写成长句。
3. **证伪条件去重**：报告 §10.3 有 7 条，若两条其实同一因子（如营收/特饮/茶饮），合并成一条并注明「任一」。
4. **监测项只取可核验的数字**：股息率、PE 分位这类没有数据源的，保留文字，但不要伪造 `currentValue`；可以写 `未提供`。
5. **日期只写报告明确给出的**：`PDUFA 2027-04-09` 可以；「2028 年中」只能作为文字，`eventDate` 用近似日期并在 `currentValue` 保留「2028 年中」。
6. **信心度只映射报告原词**：高→`HIGH`，中高→`MEDIUM_HIGH`，中→`MEDIUM`，低→`LOW`；不要自己升级。
7. **一句话总结优先用报告原文**：报告 §14.3 已经有一句，压缩到 60 字以内即可。

---

## 5. 幂等、覆盖与错误

### 5.1 覆盖规则

- `save_focus_stock` 只覆盖该股票在关注池里的 `focus`，不碰标签、备注、提醒价；同一份报告重复调用结果一致；
- `buyPlan.tiers`、`monitorItems`、`decisionMemo.dimensions` 都是**全量替换**，不是增量合并；
- 服务端会比较新旧档案，把变化写进 `timeline`，你不需要自己写时间线。

### 5.1.1 返回值约定

所有工具返回 `{ state, message, data }`：

- `state = 0` 才算成功，继续下一步；
- `state != 0` 时停止，把 `message` 原样带回给用户，不要猜测性重试；
- `save_focus_stock` 成功后，用返回的 `data.symbol` 和 `data.status` 做一次自检。

### 5.2 日历去重

- 服务端按 `reportId + symbol + 类别=个股监测` 替换；
- 只删除 `PENDING`，已 `DONE` 的保留；
- 同一指标换了日期，以新报告为准。

### 5.3 常见错误

| 返回 | 原因 | 怎么修 |
| --- | --- | --- |
| `代码不能为空` | 没传 `symbol` | 传 6 位代码 |
| `买入档位不能为空` | `tiers` 空数组 | 至少给一档 |
| `价格必须大于 0` | `priceLow/priceHigh` 为 0 | 检查报告数字 |
| `监测项 key 重复` | 同一份 JSON 里两个相同 `key` | 合并或改名 |
| `状态流转不允许` | 例如 `DROPPED → HOLDING` | 先恢复 `WATCHING`，或重新导入报告 |
| `报告不存在` | `reportId` 写错 | 用第一步返回的 id |

### 5.4 写之前先读

覆盖前建议先调 `get_focus_stock(symbol)` 或 `list_focus_stocks`，确认这只票当前状态：

- 如果 `status=HOLDING`，不要把它改回 `WATCHING`；
- 如果已有档位 `DONE`，新报告里不要把同档位写成 `DONE`，除非确实又买了一次。

---

## 6. 自检清单

一次写入完成后，逐条确认：

- [ ] 报告库能按「个股」筛到这份报告，标题、日期、评级正确；
- [ ] 关注档案卡显示股票名、代码、现价、评级、报告日期；
- [ ] 买入阶梯档数、价格区间、股数、金额与报告一致；
- [ ] 「距第一档」的百分比是系统按现价算的，不是你传的；
- [ ] 监测清单里带日期的项出现在监测日历，且只出现一次；
- [ ] 决策备忘录六维顺序正确，信心度没有升级；
- [ ] 「来源报告」能打开报告库原文；
- [ ] 再调用一次 `save_focus_stock`，不产生重复记录、不产生重复日历事件；
- [ ] 没有把 §11 相关性、§13 大师点评、§8 估值推演塞进关注档案。

---

## 7. 明确不做

- 不写 `review_level`，除非这只票已经 `HOLDING`；
- 不修改 `关注档案 status` 来表达「价格到了」，价格只由页面计算展示；
- 不把报告全文塞进 `favorites[].focus`，全文只进 `research_report`；
- 不自动生成买入/卖出信号，不调用交易接口；
- 不删除报告库里的历史报告，历史结论靠报告库保留。
