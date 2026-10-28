---
change: market-dashboard-merge
design-doc: docs/superpowers/specs/2026-10-28-market-dashboard-merge-design.md
base-ref: 15c15905b26f9d915f54c15e99a8694dfc4580dd
---

# 市场页面合并实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「温度计」与「选股」两个窄页面合并为一个高密度、响应式的「市场」页面，并保留旧路由兼容。

**Architecture:** 新建单一页面组件 `MarketOverview.vue`，父组件负责四个接口的并行请求与分区状态，模板按「摘要条 → 温度/选股双栏 → 热点双栏 → 历史折叠」组织；路由新增 `/market` 并把旧路径重定向；导航收敛为单一入口；删除旧组件。

**Tech Stack:** Vue 3 `<script setup>` (JavaScript)、Element Plus 2.11、axios、Vite 7。

**Spec:** `docs/superpowers/specs/2026-10-28-market-dashboard-merge-design.md`；能力规格 `docs/openspec/changes/market-dashboard-merge/specs/market-dashboard/spec.md`。

## Global Constraints

- 不新增前端依赖、不引入测试框架、不修改后端。
- 页面容器不得设置窄 `max-width`；宽屏双栏、992px 以下堆叠。
- 四个区块独立 loading/error/空态；单接口失败不得清空其他区块。
- 旧路径 `/regime`、`/screener` 必须重定向到 `/market`。
- 产物语言：简体中文；代码注释/文案与现有项目风格一致。
- 构建命令：`cd StockAI-Console && npm run build`。

---

### Task 1: 页面骨架与数据层

**Files:**
- Create: `StockAI-Console/src/components/MarketOverview.vue`
- Reference: `StockAI-Console/src/components/MarketRegime.vue`、`StockAI-Console/src/components/StockScreener.vue`、`StockAI-Console/src/config/config.js`

**Interfaces:**
- Consumes: `config.baseUrl`。
- Produces: `refreshAll()`、`sections.{regime,history,screening,hotspot}`、`regimeInfo`、`positionAdviceText()`、`summary`、`boardTabs`、`currentBoard`、`pctText()`、`numText()`，供 Task 2 的模板使用。

- [ ] **Step 1: 创建组件脚本中的常量与状态**

```js
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../config/config'

const BASE_URL = config.baseUrl

const REGIME_LABEL = {
  BULL: { text: '牛', color: '#ef4444', advice: '激进（高仓位）' },
  BEAR: { text: '熊', color: '#10b981', advice: '防御（低仓位）' },
  SIDEWAYS: { text: '震荡', color: '#f59e0b', advice: '中性（中等仓位）' },
  RISK: { text: '风险', color: '#8b5cf6', advice: '空仓/防御' }
}
const ADVICE_LABEL = {
  AGGRESSIVE: '激进（高仓位）',
  NEUTRAL: '中性（中等仓位）',
  DEFENSIVE: '防御（低仓位）',
  CASH: '空仓/防御'
}
const RATING_LABEL = { HIGH: '高', MEDIUM: '中', LOW: '低', UNKNOWN: '未知' }
const RATING_TYPE = { HIGH: 'success', MEDIUM: 'warning', LOW: 'danger', UNKNOWN: 'info' }
const HYPE_LABEL = { SUPPORTED: '有基本面支撑', NEUTRAL: '中性', HYPE_RISK: '纯炒作风险高' }
const HYPE_TYPE = { SUPPORTED: 'success', NEUTRAL: 'info', HYPE_RISK: 'danger' }

const sections = reactive({
  regime: { loading: false, error: '', data: null },
  history: { loading: false, error: '', data: [] },
  screening: { loading: false, error: '', data: null },
  hotspot: { loading: false, error: '', data: null }
})
const activeBoard = ref('balancedBoard')
const historyOpen = ref(['history'])
const refreshing = ref(false)
```

- [ ] **Step 2: 实现分区请求与并行刷新**

```js
async function loadSection(key, request, fallback) {
  const section = sections[key]
  section.loading = true
  section.error = ''
  try {
    const response = await request()
    const body = response.data || {}
    if (body.state !== 0) {
      section.error = body.message || '接口返回失败'
      section.data = fallback
      return false
    }
    section.data = body.data ?? fallback
    return true
  } catch (error) {
    console.error(`加载 ${key} 失败:`, error)
    section.error = '加载失败，请稍后重试'
    section.data = fallback
    return false
  } finally {
    section.loading = false
  }
}

const loadRegime = () => loadSection('regime', () => axios.post(`${BASE_URL}/market-regime/latest`), null)
const loadHistory = () => loadSection('history', () => axios.post(`${BASE_URL}/market-regime/history?limit=30`), [])
const loadScreening = () => loadSection('screening', () => axios.post(`${BASE_URL}/screening/latest`), null)
const loadHotspot = () => loadSection('hotspot', () => axios.post(`${BASE_URL}/hotspot/latest`), null)

async function refreshAll() {
  refreshing.value = true
  const results = await Promise.allSettled([loadRegime(), loadHistory(), loadScreening(), loadHotspot()])
  refreshing.value = false
  if (results.some(r => r.status === 'rejected' || r.value === false)) {
    ElMessage.warning('部分市场数据加载失败，请稍后刷新重试')
  }
}
onMounted(refreshAll)
```

- [ ] **Step 3: 实现派生数据、格式化与榜单映射**

```js
const regimeInfo = computed(() => {
  const regime = sections.regime.data?.regime
  return REGIME_LABEL[regime] || { text: regime || '-', color: '#6b7280', advice: '-' }
})
const positionAdviceText = (advice) => ADVICE_LABEL[advice] || advice || '-'
const numText = (value, digits = 2) => value == null ? '-' : Number(value).toFixed(digits)
const pctText = (value) => value == null ? '-' : (Number(value) * 100).toFixed(2) + '%'
const ratingText = (rating) => RATING_LABEL[rating] || rating || '-'
const ratingType = (rating) => RATING_TYPE[rating] || 'info'
const hypeText = (label) => HYPE_LABEL[label] || '中性'
const hypeType = (label) => HYPE_TYPE[label] || 'info'

const boardTabs = computed(() => {
  const data = sections.screening.data
  if (!data) return []
  return [
    { key: 'balancedBoard', label: '均衡型', list: data.balancedBoard || [] },
    { key: 'valueBoard', label: '价值型', list: data.valueBoard || [] },
    { key: 'growthBoard', label: '成长型', list: data.growthBoard || [] }
  ]
})
const currentBoard = computed(() => boardTabs.value.find(t => t.key === activeBoard.value) || null)

const summary = computed(() => {
  const boards = boardTabs.value.flatMap(t => t.list)
  const scores = boards.map(b => Number(b.totalScore)).filter(Number.isFinite)
  const symbols = new Set(boards.map(b => b.symbol).filter(Boolean))
  const industries = sections.hotspot.data?.industries || []
  const concepts = sections.hotspot.data?.concepts || []
  const limitUp = industries.reduce((sum, i) => sum + (Number(i.limitUpCount) || 0), 0)
  return {
    regime: regimeInfo.value.text,
    advice: regimeInfo.value.advice,
    tradeDate: sections.screening.data?.tradeDate || sections.regime.data?.date || sections.hotspot.data?.tradeDate || '-',
    maxScore: scores.length ? Math.max(...scores) : '-',
    candidateCount: symbols.size,
    hotspotCount: industries.length + concepts.length,
    limitUpCount: limitUp
  }
})
```

- [ ] **Step 4: 添加最小模板骨架并运行构建**

模板包含页头、摘要条、四个后续区块的占位 `el-card`；无接口数据时显示空态。运行：

```bash
cd StockAI-Console
npm run build
```

预期：构建成功，无 Vue 模板编译错误。

- [ ] **Step 5: 提交**

```bash
git add StockAI-Console/src/components/MarketOverview.vue
git commit -m "feat(market): 新增市场页面骨架与并行数据层"
```

---

### Task 2: 四个内容区块

**Files:**
- Modify: `StockAI-Console/src/components/MarketOverview.vue`（模板与局部样式）

**Interfaces:**
- Consumes: Task 1 的 `sections`、`regimeInfo`、`boardTabs`、`currentBoard`、`pctText()` 等。
- Produces: 完整的市场温度、选股榜单、热点方向、历史记录 DOM，供 Task 5 调整响应式样式。

- [ ] **Step 1: 市场温度区块**

```vue
<el-card shadow="hover" class="panel-card">
  <template #header>
    <div class="panel-head">
      <h3>市场温度</h3>
      <span v-if="sections.regime.data" class="muted">{{ sections.regime.data.date }}</span>
    </div>
  </template>
  <div v-loading="sections.regime.loading">
    <el-alert v-if="sections.regime.error" :title="sections.regime.error" type="warning" :closable="false" show-icon />
    <template v-else-if="sections.regime.data">
      <div class="regime-hero">
        <span class="regime-badge" :style="{ backgroundColor: regimeInfo.color }">{{ regimeInfo.text }}</span>
        <div>
          <div class="regime-advice">{{ regimeInfo.advice }}</div>
          <div class="muted">仓位建议：{{ positionAdviceText(sections.regime.data.positionAdvice) }}</div>
        </div>
      </div>
      <div class="indicator-grid" v-if="sections.regime.data.indicators">
        <div class="indicator-item">MA20<span>{{ numText(sections.regime.data.indicators.ma20) }}</span></div>
        <div class="indicator-item">MA60<span>{{ numText(sections.regime.data.indicators.ma60) }}</span></div>
        <div class="indicator-item">MA250<span>{{ numText(sections.regime.data.indicators.ma250) }}</span></div>
        <div class="indicator-item">量比<span>{{ numText(sections.regime.data.indicators.volumeRatio) }}</span></div>
        <div class="indicator-item">波动率<span>{{ pctText(sections.regime.data.indicators.volatility) }}</span></div>
        <div class="indicator-item">PE 分位<span>{{ pctText(sections.regime.data.indicators.pePercentile) }}</span></div>
        <div class="indicator-item">PB 分位<span>{{ pctText(sections.regime.data.indicators.pbPercentile) }}</span></div>
      </div>
      <div class="report-box">{{ sections.regime.data.report || '暂无报告' }}</div>
    </template>
    <el-empty v-else description="暂无市场状态记录" />
  </div>
</el-card>
```

- [ ] **Step 2: 选股榜单区块**

包含 AI 报告、`aiReportStatus === 'fallback'` 提示、`el-tabs` 三风格、Top20 表格（序号、股票、综合分、估值/成长/质量/动量、长期价值、入选理由）。

- [ ] **Step 3: 热点方向区块**

行业与概念各一张表，列为：名称、热度、股票数、涨停数、单日均涨幅、5日均涨幅、炒作风险、代表股；`hypeBasis` 放入 `el-tooltip`，代表股用 `topStocks` 遍历渲染。

- [ ] **Step 4: 历史记录区块**

`el-collapse` 默认展开，内部表格列：日期、状态、仓位建议（`positionAdviceText`）、报告（`show-overflow-tooltip`）。

- [ ] **Step 5: 运行构建并提交**

```bash
cd StockAI-Console && npm run build
git add StockAI-Console/src/components/MarketOverview.vue
git commit -m "feat(market): 完成市场温度、选股、热点与历史区块"
```

预期：构建成功；四个区块在数据存在时字段齐全。

---

### Task 3: 路由与侧边导航

**Files:**
- Modify: `StockAI-Console/src/router/index.js`
- Modify: `StockAI-Console/src/App.vue`（侧边菜单）

**Interfaces:**
- Consumes: Task 1 创建的 `MarketOverview.vue`。
- Produces: `/market` 路由与单一「市场」导航入口；`/regime`、`/screener` 重定向。

- [ ] **Step 1: 更新路由**

```js
import MarketOverview from '../components/MarketOverview.vue'

// routes 中：
{ path: '/market', name: '市场', component: MarketOverview },
{ path: '/regime', redirect: '/market' },
{ path: '/screener', redirect: '/market' }
```

删除原 `MarketRegime`、`StockScreener` 的 import 与路由记录。

- [ ] **Step 2: 更新导航**

```vue
<el-menu-item index="/market">
  <el-icon><TrendCharts /></el-icon>
  <span>市场</span>
</el-menu-item>
```

删除原「温度计」「选股」两个 `el-menu-item`；`TrendCharts` 已从 `@element-plus/icons-vue` 导入。

- [ ] **Step 3: 构建并提交**

```bash
cd StockAI-Console && npm run build
git add StockAI-Console/src/router/index.js StockAI-Console/src/App.vue
git commit -m "feat(market): 合并导航入口并保留旧路由重定向"
```

预期：构建成功；路由表含 `/market`、`/regime`、`/screener`。

---

### Task 4: 删除旧组件

**Files:**
- Delete: `StockAI-Console/src/components/MarketRegime.vue`
- Delete: `StockAI-Console/src/components/StockScreener.vue`

**Interfaces:**
- Consumes: Task 3 已移除所有 import。
- Produces: 无重复实现的前端代码。

- [ ] **Step 1: 全局搜索引用**

```bash
rg -n "MarketRegime|StockScreener" StockAI-Console/src docs/openspec/changes/market-dashboard-merge
```

预期：`StockAI-Console/src` 内无引用（文档中出现属正常）。

- [ ] **Step 2: 删除文件并构建**

```bash
git rm StockAI-Console/src/components/MarketRegime.vue StockAI-Console/src/components/StockScreener.vue
cd StockAI-Console && npm run build
```

预期：构建成功。

- [ ] **Step 3: 提交**

```bash
git commit -m "chore(market): 删除已合并的旧页面组件"
```

---

### Task 5: 响应式排版与视觉一致性

**Files:**
- Modify: `StockAI-Console/src/components/MarketOverview.vue`（scoped 样式）

**Interfaces:**
- Consumes: Task 2 的 DOM 结构。
- Produces: 无横向溢出的宽屏双栏 / 窄屏堆叠布局；深色模式可读样式。

- [ ] **Step 1: 页面与摘要条样式**

```css
.market-page { width: 100%; display: flex; flex-direction: column; gap: 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1200px) { .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .summary-grid { grid-template-columns: 1fr; } }
```

- [ ] **Step 2: 双栏、指标网格与表格样式**

主区使用 `el-row :gutter="16"`，左 `:xs="24" :lg="8"`，右 `:xs="24" :lg="16"`；热点各 `:xs="24" :lg="12"`。指标网格 4 列，≤1200px 3 列，≤768px 2 列。表格 `size="small"`、`max-height`、表格容器 `overflow-x: auto`；AI 报告 `white-space: pre-wrap; max-height: 220px; overflow-y: auto`。

- [ ] **Step 3: 深色模式局部覆盖**

```css
.dark .indicator-item { background: #1e293b; border-color: #334155; }
.dark .report-box { background: #0f172a; border-color: #334155; color: #e2e8f0; }
```

仅覆盖本页内部元素，不重复定义卡片主题色。

- [ ] **Step 4: 构建并提交**

```bash
cd StockAI-Console && npm run build
git add StockAI-Console/src/components/MarketOverview.vue
git commit -m "style(market): 优化高密度响应式排版与深色模式"
```

预期：构建成功；1440/1024/768 宽度下页面无横向滚动。

---

### Task 6: 集成验证与文档勾选

**Files:**
- Modify: `docs/openspec/changes/market-dashboard-merge/tasks.md`
- Modify: `docs/openspec/changes/market-dashboard-merge/.comet.yaml`（通过 `comet state` 命令）
- Create: 验证记录（写入最终回复与 verify 报告）

**Interfaces:**
- Consumes: Task 1-5 的全部产物。
- Produces: 可进入 verify 阶段的构建证据与任务勾选。

- [ ] **Step 1: 构建验证**

```bash
cd StockAI-Console && npm run build
```

预期：exit code 0，无新增错误。

- [ ] **Step 2: 路由与导航人工核对**

启动 `npm run dev`（端口 8889），访问 `/regime`、`/screener`、`/market`；确认旧路径地址变为 `/market`，侧栏只有「市场」。若无法启动开发服务器，记录为环境限制并保留构建证据。

- [ ] **Step 3: 规格 Scenario 核对**

对照 `specs/market-dashboard/spec.md` 六个 Requirement 与全部 Scenario，逐条确认；将结果写入验证报告。

- [ ] **Step 4: 勾选 tasks.md 与提交**

```bash
git add docs/openspec/changes/market-dashboard-merge/tasks.md
git commit -m "docs(market): 完成合并页面任务清单"
```

预期：`tasks.md` 全部为 `- [x]`，且 `comet guard market-dashboard-merge build --apply` 通过。
