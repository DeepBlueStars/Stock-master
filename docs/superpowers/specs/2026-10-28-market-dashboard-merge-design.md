---
comet_change: market-dashboard-merge
role: technical-design
canonical_spec: openspec
archived-with: 2026-10-28-market-dashboard-merge
status: final
---

# 市场页面合并技术设计（market-dashboard-merge）

## 1. 背景与约束

- 需求与动机见 `docs/openspec/changes/market-dashboard-merge/proposal.md`，能力契约见 `specs/market-dashboard/spec.md`。
- 现状：`MarketRegime.vue`（`max-width: 900px`）与 `StockScreener.vue`（`max-width: 1100px`）各自只读展示后端已生成的当日记录；两者没有共享状态，接口互不依赖。
- 技术栈：Vue 3 `<script setup>`（JavaScript，非 TS）、Element Plus 2.11、axios、Vite 7；无前端测试框架、无 ESLint/TS 严格配置。
- 运行环境：`App.vue` 主内容区已经设置 `padding: 2rem` 且可纵向滚动；卡片圆角、边框、阴影和深色模式由 `App.vue` 的 scoped 样式统一提供。

## 2. 设计目标与非目标

**目标**

1. 单页面 `/market` 完整承载原两页全部信息，首屏可见「市场状态 + 选股榜单」。
2. 1440px 宽度下无大面积两侧留白；992px 以下自动堆叠且页面无横向滚动。
3. 四个数据源并行请求、分区降级；旧路径访问可用。
4. 不改变后端契约、不新增依赖。

**非目标**

- 不新增后端接口、不修改 Mongo 结构、不改定时任务。
- 不引入图表、实时刷新、虚拟滚动或新的筛选交互。
- 不抽象全局请求层，不改造其他页面。

## 3. 组件与文件结构

```
StockAI-Console/src/
├── App.vue                          # 导航：删除“温度计/选股”，新增“市场”
├── router/index.js                  # 新增 /market；/regime、/screener → redirect
└── components/
    ├── MarketOverview.vue           # 新增：唯一市场页面（数据编排 + 排版 + 四个区块）
    ├── MarketRegime.vue             # 删除
    └── StockScreener.vue            # 删除
```

**为什么仍用单文件页面，而不是拆成四个子组件：**

- 四个区块只在本页面出现，没有复用场景；拆分会引入 props/emit 管道，增加同步成本。
- 项目现有页面（`Dashboard.vue` 67KB、`AssetConfig.vue`）均采用单文件多区块模式，保持一致。
- 单文件内通过模板分节 + 命名清晰的 helper 保持可读性；若后续该文件超过约 800 行或出现第二处复用，再按区块拆分。

`MarketOverview.vue` 内部结构：

| 区域 | 内容 | 关键实现 |
| --- | --- | --- |
| 页头 | 标题「市场」、基准交易日、刷新按钮 | `header-meta` + `el-button` |
| 摘要条 | 市场状态、仓位建议、最高综合分、候选股数、热点/涨停 | CSS Grid，5 列 |
| 市场温度 | 徽标、建议、日期、指标网格、AI 报告 | `el-card` + CSS Grid 指标 |
| 选股榜单 | AI 报告、三风格 Tabs、Top20 表格 | `el-tabs` + `el-table` |
| 热点方向 | 行业 Top10、概念 Top10 | `el-row` 两个 `el-col`：常规宽屏 `:lg="24"` 上下排列，超宽屏 `:xl="12"` 双栏 |
| 历史记录 | 最近 30 条市场状态 | `el-collapse` 默展开 + `el-table` |

## 4. 数据契约与获取

### 4.1 接口

| 区块 | 方法/路径 | 关键字段 |
| --- | --- | --- |
| 市场状态 | `POST /market-regime/latest` | `date, regime, positionAdvice, indicators{ma20,ma60,ma250,volumeRatio,volatility,latestClose,pePercentile,pbPercentile,benchmarkIndex}, report` |
| 市场历史 | `POST /market-regime/history?limit=30` | `date, regime, positionAdvice, report` |
| 选股结果 | `POST /screening/latest` | `tradeDate, aiReport, aiReportStatus, balancedBoard[], valueBoard[], growthBoard[]`，条目含 `symbol,name,totalScore,valuation,growth,quality,momentum,longTermRating,reason` |
| 热点方向 | `POST /hotspot/latest` | `tradeDate, industries[], concepts[]`，条目含 `name,hotScore,stockCount,limitUpCount,dailyAvgChange,fiveDayAvgChange,hypeLabel,hypeBasis,topStocks[]` |

统一判定：`response.data.state === 0` 视为成功，`data` 可能为 `null`。

### 4.2 请求与状态模型

```js
const sections = reactive({
  regime:  { loading: false, error: '', data: null },
  history: { loading: false, error: '', data: [] },
  screening:{ loading: false, error: '', data: null },
  hotspot: { loading: false, error: '', data: null }
})
```

- `loadSection(key, request)`：设置 `loading`，`try/catch` 内写入 `data` 或 `error`，`finally` 清除 loading；函数自身不抛出异常。
- `refreshAll()`：`Promise.allSettled([loadRegime(), loadHistory(), loadScreening(), loadHotspot()])`，保证并行；全部结束后若有失败，手动刷新时用一次 `ElMessage.warning` 汇总提示，首次加载只展示区块内错误文案，避免 4 条重复弹窗。
- `onMounted(refreshAll)`；页头刷新按钮绑定 `refreshAll`。
- 每个区块的 `v-loading` 只绑定自身 `loading`，避免一个慢接口阻塞整页视觉。

### 4.3 派生数据

- `regimeInfo`：`BULL→牛/#ef4444/激进（高仓位）`、`BEAR→熊/#10b981/防御（低仓位）`、`SIDEWAYS→震荡/#f59e0b/中性（中等仓位）`、`RISK→风险/#8b5cf6/空仓/防御`；未知状态回退灰色和 `-`。
- `positionAdviceText`：将历史记录中的 `AGGRESSIVE/NEUTRAL/DEFENSIVE/CASH` 映射为中文，避免直接显示枚举码。
- 摘要条：
  - 市场状态：`regimeInfo.text`，无数据为 `-`
  - 仓位建议：`regimeInfo.advice` 或映射后的建议
  - 最高综合分：三个榜单全部条目的 `max(totalScore)`，无数据 `-`
  - 候选股数：三个榜单 `symbol` 去重后的数量
  - 热点：`industries.length + concepts.length` 个方向，涨停数按行业榜 `limitUpCount` 求和
- 列表默认值：`boardTabs` 在无数据时为空数组；表格 `:data` 绑定 `[]`，避免 Element Plus 渲染异常。
- 数值格式化：`pctText(v)` 对 `null/undefined` 返回 `-`，否则 `(v*100).toFixed(2)+'%'`；`numText(v)` 同理处理指标；`volatility` 仅在非空时乘 100。
- 评级与炒作风险映射保持原页面的 `HIGH/MEDIUM/LOW/UNKNOWN` 与 `SUPPORTED/NEUTRAL/HYPE_RISK` 语义。

## 5. 排版与响应式设计

### 5.1 页面骨架（CSS 关键点）

```css
.market-page { width: 100%; display: flex; flex-direction: column; gap: 16px; }
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 1200px) { .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px)  { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px)  { .summary-grid { grid-template-columns: 1fr; } }
```

- 不设置页面级 `max-width`，内容随主区铺满；卡片之间统一 `16px` 间距。
- 主双栏使用 Element Plus 栅格：市场温度 `el-col :xs="24" :md="24" :lg="8"`，选股 `:xs="24" :md="24" :lg="16"`；1200px 以下自然堆叠。
- 热点方向：常规宽屏（1200–1919px）行业与概念各占整行（`:lg="24"`），超宽屏（≥1920px）切换为 `:xl="12"` 双栏；这样两个榜单都能完整展示炒作风险与代表股列。
- 指标网格：`grid-template-columns: repeat(4, minmax(0,1fr))`，`max-width: 1200px` 下 3 列，`768px` 下 2 列。
- 表格统一 `size="small"`、`stripe`、`show-overflow-tooltip`；选股表 `max-height="420"`，历史表 `max-height="320"`，允许内部纵向滚动；热点榜只展示 Top 10，不使用内部纵向滚动，确保 10 行全部可见。
- 选股表列宽：序号 40、股票 `min-width: 110`、综合分 74、四因子各 54、长期价值 76、入选理由 `min-width: 160`。这样在 1440px 右栏内无需横向滚动；更窄视口允许表格自身横向滚动，页面不滚动。
- 热点表列宽：序号 38、名称 `min-width: 86`、热度 54、股票数 62、涨停数 60、单日/5日各 68、炒作风险 106、代表股 `min-width: 150`，整表约 692px，可在超宽屏半栏内完整展示。
- 文本溢出：股票名/理由使用 `show-overflow-tooltip`；AI 报告使用 `white-space: pre-wrap` + `max-height: 220px` + `overflow-y: auto`，保留换行。

### 5.2 视觉一致性

- 状态/风险颜色沿用原语义（牛红、熊绿、震荡橙、风险紫；风险标签用 `success/info/danger`）。
- 卡片不重复定义背景与边框，复用 `App.vue` 的 `.app-content .el-card`；仅补充内部间距。
- 深色模式使用 `.dark` 祖先选择器做局部覆盖（指标底色、分隔线、报告框），文本颜色优先使用 `var(--el-text-color-*)`。
- 页头标题使用 `h2`，区块标题使用 `h3`，保持文档层级。

## 6. 路由与导航

```js
{ path: '/market', name: '市场', component: MarketOverview },
{ path: '/regime', redirect: '/market' },
{ path: '/screener', redirect: '/market' }
```

- `redirect` 会把地址栏统一为 `/market`，满足规格中的跳转断言。
- `App.vue`：删除两个旧 `el-menu-item`，新增 `index="/market"`、图标 `TrendCharts`、文案「市场」；`default-active="route.path"` 在重定向后高亮正确。
- 组件删除前先全局搜索 `MarketRegime`、`StockScreener`、`/regime`、`/screener`，确认引用只存在于即将修改的路由/导航/文档中。

## 7. 边界条件与错误处理

| 场景 | 行为 |
| --- | --- |
| 接口 `state !== 0` | 区块 `error` 记录 `message`，展示空态/错误文案，不使用旧数据 |
| `data` 为 `null` | 对应区块显示 `el-empty`，摘要条显示 `-` |
| `indicators` 缺失 | 指标网格显示 `-`，不抛异常 |
| 榜单为空/字段缺失 | 表格展示空态；`pctText`/`numText` 兜底 |
| 四个接口全部失败 | 页面仍渲染骨架与四个错误区块，刷新按钮可用 |
| 手动快速重复刷新 | `loading` 期间禁用刷新按钮，避免并发请求叠加 |
| 历史记录超过 30 条 | 只请求 30 条，表格内部滚动 |
| 深色模式 | 卡片与文本跟随主题，局部覆盖不留浅色底 |

## 8. 测试与验证策略

无前端测试框架，且本次明确不新增依赖，因此采用「构建验证 + 人工场景核对」：

1. **构建**：`cd StockAI-Console && npm run build`，必须成功；记录任何新增警告。
2. **路由**：开发服务器访问 `/regime`、`/screener`，确认地址变为 `/market`；直接访问 `/market` 正常渲染。
3. **导航**：侧边栏只有「市场」入口，选中态正确。
4. **排版**：1920/1440/1024/768 四档宽度检查主双栏、热点上下/双栏切换、表格横向滚动、页面无横向滚动。
5. **数据完整性**：对照 `specs/market-dashboard/spec.md` 的 Scenario 逐条核对市场状态、三风格榜单、热点双榜、历史记录。
6. **降级**：停用或篡改任一接口（或浏览器网络面板阻断）验证单区块失败不影响其他区块。
7. **深色模式**：切换后检查卡片、表格、标签、报告区对比度。

## 9. 实施顺序与回滚

1. 新建 `MarketOverview.vue` 并完成数据层 → 2. 路由/导航 → 3. 删除旧组件 → 4. `npm run build` → 5. 人工核对。

回滚：在 `comet/market-dashboard-merge` 分支上反向提交或删除新组件、恢复路由与导航即可；后端无迁移、无数据变更。

## 10. 风险与缓解

- **信息拥挤**：摘要条 + 分区卡片 + 表格限高；理由列可换行/悬浮查看。
- **窄屏表格过窄**：表格容器独立横向滚动，四因子列固定较小宽度。
- **深色模式样式失效**：不硬编码背景，局部覆盖只针对本页元素。
- **删除组件导致漏改引用**：删除前全局搜索，构建验证兜底。
- **旧书签失效**：`redirect` 保留兼容入口。

## 11. 需求映射

| Spec Requirement | 设计落点 |
| --- | --- |
| 统一的市场页面 | `/market` + 单页四区块 + 单一导航入口 |
| 高密度响应式排版 | 摘要条、`el-row/el-col` 双栏、CSS Grid 指标、断点堆叠 |
| 旧路由兼容 | `/regime`、`/screener` redirect |
| 区块独立加载与降级 | 四份 section 状态 + `Promise.allSettled` + 分区空态 |
| 保留原有信息完整性 | 四区块字段映射表（第 4.1/4.3 节） |
| 视觉与交互一致性 | 复用 App 卡片主题、状态色语义、`.dark` 局部覆盖 |
