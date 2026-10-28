<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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

const regimeRecord = computed(() => sections.regime.data)
const screeningResult = computed(() => sections.screening.data)
const hotspotResult = computed(() => sections.hotspot.data)

const regimeInfo = computed(() => {
  const regime = regimeRecord.value?.regime
  return REGIME_LABEL[regime] || { text: regime || '-', color: '#6b7280', advice: '-' }
})

const positionAdvice = computed(() =>
  positionAdviceText(regimeRecord.value?.positionAdvice) || regimeInfo.value.advice
)

const boardTabs = computed(() => {
  const data = screeningResult.value
  if (!data) return []
  return [
    { key: 'balancedBoard', label: '均衡型', list: data.balancedBoard || [] },
    { key: 'valueBoard', label: '价值型', list: data.valueBoard || [] },
    { key: 'growthBoard', label: '成长型', list: data.growthBoard || [] }
  ]
})

const currentBoard = computed(() =>
  boardTabs.value.find(tab => tab.key === activeBoard.value) || null
)

const hotspotBoards = computed(() => {
  const data = hotspotResult.value
  if (!data) return []
  return [
    { key: 'industries', label: '行业 Top 10', list: data.industries || [] },
    { key: 'concepts', label: '概念 Top 10', list: data.concepts || [] }
  ]
})

const summary = computed(() => {
  const boards = boardTabs.value.flatMap(tab => tab.list)
  const scores = boards.map(item => Number(item.totalScore)).filter(Number.isFinite)
  const symbols = new Set(boards.map(item => item.symbol).filter(Boolean))
  const industries = hotspotResult.value?.industries || []
  const concepts = hotspotResult.value?.concepts || []
  const limitUpCount = industries.reduce((sum, item) => sum + (Number(item.limitUpCount) || 0), 0)
  const hasScreening = !!screeningResult.value
  const hasHotspot = !!hotspotResult.value
  return {
    tradeDate: screeningResult.value?.tradeDate
      || regimeRecord.value?.date
      || hotspotResult.value?.tradeDate
      || '-',
    regime: regimeInfo.value.text,
    advice: positionAdvice.value,
    maxScore: hasScreening && scores.length ? Math.max(...scores).toFixed(1) : '-',
    candidateCount: hasScreening ? symbols.size : '-',
    hotspotCount: hasHotspot ? industries.length + concepts.length : '-',
    limitUpCount: hasHotspot ? limitUpCount : '-'
  }
})

const summaryItems = computed(() => [
  {
    label: '市场状态',
    value: summary.value.regime,
    extra: `仓位建议：${summary.value.advice}`,
    color: regimeInfo.value.color
  },
  { label: '最高综合分', value: summary.value.maxScore, extra: '三风格榜单最高分' },
  { label: '候选股', value: summary.value.candidateCount, extra: '三风格去重候选' },
  { label: '热点方向', value: summary.value.hotspotCount, extra: '行业 + 概念' },
  { label: '行业涨停', value: summary.value.limitUpCount, extra: '优质池口径' }
])

function positionAdviceText(advice) {
  return ADVICE_LABEL[advice] || advice || ''
}

function ratingText(rating) {
  return RATING_LABEL[rating] || rating || '-'
}

function ratingType(rating) {
  return RATING_TYPE[rating] || 'info'
}

function hypeText(label) {
  return HYPE_LABEL[label] || '中性'
}

function hypeType(label) {
  return HYPE_TYPE[label] || 'info'
}

function numText(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(digits) : '-'
}

function scoreText(value) {
  return numText(value, 1)
}

function pctText(value) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number) ? `${(number * 100).toFixed(2)}%` : '-'
}

function changeClass(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number === 0) return 'flat'
  return number > 0 ? 'up' : 'down'
}

function regimeBadgeStyle(regime) {
  const color = REGIME_LABEL[regime]?.color || '#6b7280'
  return { backgroundColor: color }
}

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
    console.error(`加载 ${key} 数据失败:`, error)
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
  const results = await Promise.allSettled([
    loadRegime(),
    loadHistory(),
    loadScreening(),
    loadHotspot()
  ])
  refreshing.value = false
  if (results.some(result => result.status === 'rejected' || result.value === false)) {
    ElMessage.warning('部分市场数据加载失败，请稍后刷新重试')
  }
}

onMounted(refreshAll)
</script>

<template>
  <div class="market-page">
    <div class="page-head">
      <div class="page-title-group">
        <h2 class="page-title">市场</h2>
        <p class="page-subtitle">市场温度 · 选股榜单 · 热点方向</p>
      </div>
      <div class="page-actions">
        <span class="trade-date">交易日：{{ summary.tradeDate }}</span>
        <el-button type="primary" :loading="refreshing" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <div v-for="item in summaryItems" :key="item.label" class="summary-card">
        <div class="summary-label">{{ item.label }}</div>
        <div class="summary-value" :style="item.color ? { color: item.color } : null">{{ item.value }}</div>
        <div class="summary-extra">{{ item.extra }}</div>
      </div>
    </div>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :sm="24" :md="24" :lg="8">
        <el-card shadow="hover" class="panel-card">
          <template #header>
            <div class="panel-head">
              <h3 class="panel-title">市场温度</h3>
              <span class="panel-meta">{{ regimeRecord?.date || '-' }}</span>
            </div>
          </template>
          <div v-loading="sections.regime.loading" class="panel-body">
            <el-alert
              v-if="sections.regime.error"
              :title="sections.regime.error"
              type="warning"
              :closable="false"
              show-icon
            />
            <template v-else-if="regimeRecord">
              <div class="regime-hero">
                <div class="regime-badge" :style="{ backgroundColor: regimeInfo.color }">
                  {{ regimeInfo.text }}
                </div>
                <div class="regime-hero-info">
                  <div class="regime-advice">{{ regimeInfo.advice }}</div>
                  <div class="muted">仓位建议：{{ positionAdvice }}</div>
                  <div v-if="regimeRecord.indicators?.benchmarkIndex" class="muted">
                    基准指数：{{ regimeRecord.indicators.benchmarkIndex }}
                  </div>
                </div>
              </div>
              <div v-if="regimeRecord.indicators" class="indicator-grid">
                <div class="indicator-item">
                  <span class="indicator-label">MA20</span>
                  <span class="indicator-value">{{ numText(regimeRecord.indicators.ma20) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">MA60</span>
                  <span class="indicator-value">{{ numText(regimeRecord.indicators.ma60) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">MA250</span>
                  <span class="indicator-value">{{ numText(regimeRecord.indicators.ma250) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">量比</span>
                  <span class="indicator-value">{{ numText(regimeRecord.indicators.volumeRatio) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">波动率</span>
                  <span class="indicator-value">{{ pctText(regimeRecord.indicators.volatility) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">PE 分位</span>
                  <span class="indicator-value">{{ pctText(regimeRecord.indicators.pePercentile) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">PB 分位</span>
                  <span class="indicator-value">{{ pctText(regimeRecord.indicators.pbPercentile) }}</span>
                </div>
                <div class="indicator-item">
                  <span class="indicator-label">收盘</span>
                  <span class="indicator-value">{{ numText(regimeRecord.indicators.latestClose) }}</span>
                </div>
              </div>
              <div class="report-box">
                <div class="report-heading">AI 市场报告</div>
                <p class="report-content">{{ regimeRecord.report || '暂无报告' }}</p>
              </div>
            </template>
            <el-empty v-else description="暂无市场状态记录" :image-size="72" />
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="24" :lg="16">
        <el-card shadow="hover" class="panel-card">
          <template #header>
            <div class="panel-head">
              <h3 class="panel-title">选股榜单</h3>
              <div class="panel-meta-group">
                <el-tag v-if="screeningResult?.aiReportStatus === 'fallback'" type="warning" size="small">
                  AI 不可用，已降级量化文本
                </el-tag>
                <span class="panel-meta">{{ screeningResult?.tradeDate || '-' }}</span>
              </div>
            </div>
          </template>
          <div v-loading="sections.screening.loading" class="panel-body">
            <el-alert
              v-if="sections.screening.error"
              :title="sections.screening.error"
              type="warning"
              :closable="false"
              show-icon
            />
            <template v-else-if="screeningResult">
              <div class="report-box screening-report">
                <div class="report-heading">AI 综合报告</div>
                <p class="report-content">{{ screeningResult.aiReport || '暂无报告' }}</p>
              </div>
              <el-tabs v-model="activeBoard" class="board-tabs">
                <el-tab-pane
                  v-for="tab in boardTabs"
                  :key="tab.key"
                  :label="`${tab.label} (${tab.list.length})`"
                  :name="tab.key"
                />
              </el-tabs>
              <el-table
                :data="currentBoard?.list || []"
                size="small"
                stripe
                max-height="420"
                class="board-table"
                empty-text="暂无候选股"
              >
                <el-table-column type="index" label="#" width="48" />
                <el-table-column label="股票" min-width="130">
                  <template #default="{ row }">
                    <div class="stock-cell">
                      <span class="stock-name">{{ row.name }}</span>
                      <span class="stock-symbol">{{ row.symbol }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="综合分" width="82" sortable prop="totalScore">
                  <template #default="{ row }">
                    <span class="score-value">{{ scoreText(row.totalScore) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="valuation" label="估值" width="64" />
                <el-table-column prop="growth" label="成长" width="64" />
                <el-table-column prop="quality" label="质量" width="64" />
                <el-table-column prop="momentum" label="动量" width="64" />
                <el-table-column label="长期价值" width="88">
                  <template #default="{ row }">
                    <el-tag :type="ratingType(row.longTermRating)" size="small">
                      {{ ratingText(row.longTermRating) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="reason" label="入选理由" min-width="220" show-overflow-tooltip />
              </el-table>
            </template>
            <el-empty v-else description="暂无选股结果" :image-size="72" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="panel-card">
      <template #header>
        <div class="panel-head">
          <h3 class="panel-title">热点方向</h3>
          <span class="panel-meta">
            {{ hotspotResult?.tradeDate || '-' }}（涨停家数仅优质池口径）
          </span>
        </div>
      </template>
      <div v-loading="sections.hotspot.loading" class="panel-body">
        <el-alert
          v-if="sections.hotspot.error"
          :title="sections.hotspot.error"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-row v-else-if="hotspotResult" :gutter="16">
          <el-col v-for="board in hotspotBoards" :key="board.key" :xs="24" :lg="12" class="hotspot-col">
            <h4 class="sub-title">{{ board.label }}</h4>
            <el-table
              :data="board.list"
              size="small"
              stripe
              max-height="300"
              class="hotspot-table"
              empty-text="暂无热点数据"
            >
              <el-table-column type="index" label="#" width="44" />
              <el-table-column prop="name" :label="board.key === 'industries' ? '行业' : '概念'" min-width="96" />
              <el-table-column prop="hotScore" label="热度" width="64" sortable />
              <el-table-column prop="stockCount" label="股票数" width="70" />
              <el-table-column prop="limitUpCount" label="涨停数" width="70" />
              <el-table-column label="单日" width="78">
                <template #default="{ row }">
                  <span :class="changeClass(row.dailyAvgChange)">{{ pctText(row.dailyAvgChange) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="5日" width="78">
                <template #default="{ row }">
                  <span :class="changeClass(row.fiveDayAvgChange)">{{ pctText(row.fiveDayAvgChange) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="炒作风险" width="130">
                <template #default="{ row }">
                  <el-tooltip v-if="row.hypeBasis" :content="row.hypeBasis" placement="top">
                    <el-tag :type="hypeType(row.hypeLabel)" size="small">
                      {{ hypeText(row.hypeLabel) }}
                    </el-tag>
                  </el-tooltip>
                  <el-tag v-else :type="hypeType(row.hypeLabel)" size="small">
                    {{ hypeText(row.hypeLabel) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="代表股" min-width="180">
                <template #default="{ row }">
                  <div v-if="row.topStocks?.length" class="top-stocks">
                    <span
                      v-for="stock in row.topStocks"
                      :key="stock.symbol"
                      class="top-stock"
                      :class="changeClass(stock.dailyChange)"
                    >
                      {{ stock.name }} {{ pctText(stock.dailyChange) }}
                    </span>
                  </div>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
        </el-row>
        <el-empty v-else description="暂无热点结果" :image-size="72" />
      </div>
    </el-card>

    <el-card shadow="hover" class="panel-card">
      <template #header>
        <div class="panel-head">
          <h3 class="panel-title">历史记录</h3>
          <span class="panel-meta">最近 30 条市场状态</span>
        </div>
      </template>
      <div v-loading="sections.history.loading" class="panel-body">
        <el-alert
          v-if="sections.history.error"
          :title="sections.history.error"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-collapse v-else-if="sections.history.data.length" v-model="historyOpen">
          <el-collapse-item title="展开 / 收起历史明细" name="history">
            <el-table :data="sections.history.data" size="small" stripe max-height="320">
              <el-table-column prop="date" label="日期" width="120" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <span class="history-badge" :style="regimeBadgeStyle(row.regime)">
                    {{ REGIME_LABEL[row.regime]?.text || row.regime || '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="仓位建议" width="150">
                <template #default="{ row }">
                  {{ positionAdviceText(row.positionAdvice) || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="report" label="报告" min-width="260" show-overflow-tooltip />
            </el-table>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="暂无历史记录" :image-size="72" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.market-page {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  color: var(--el-text-color-primary);
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trade-date {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  min-width: 0;
}

.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-extra {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-row {
  align-items: stretch;
}

.panel-card {
  height: 100%;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.panel-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.panel-meta-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-body {
  min-height: 120px;
}

.regime-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 14px;
}

.regime-badge {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  color: #ffffff;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.regime-hero-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.regime-advice {
  font-size: 18px;
  font-weight: 700;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.indicator-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.indicator-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #eef2f7;
  background: #f8fafc;
  min-width: 0;
}

.indicator-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.indicator-value {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-box {
  border: 1px solid #eef2f7;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
}

.report-heading {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.report-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  max-height: 220px;
  overflow-y: auto;
}

.screening-report {
  margin-bottom: 12px;
}

.board-tabs {
  margin-top: -4px;
}

.stock-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.stock-name {
  font-weight: 600;
}

.stock-symbol {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.score-value {
  font-weight: 700;
  color: #2563eb;
}

.hotspot-col {
  margin-bottom: 8px;
}

.sub-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.top-stocks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.top-stock {
  font-size: 12px;
  white-space: nowrap;
}

.up {
  color: #ef4444;
}

.down {
  color: #10b981;
}

.flat {
  color: var(--el-text-color-secondary);
}

.history-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  color: #ffffff;
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .indicator-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 992px) {
  .panel-card {
    height: auto;
  }

  .main-row :deep(.el-col) {
    margin-bottom: 16px;
  }
}

@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .indicator-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .page-actions {
    width: 100%;
    justify-content: space-between;
  }

  .report-content {
    max-height: 280px;
  }
}

@media (max-width: 480px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .regime-hero {
    align-items: flex-start;
  }

  .regime-badge {
    width: 52px;
    height: 52px;
    font-size: 20px;
  }
}

.dark .summary-card {
  background: #1e293b;
  border-color: #334155;
  box-shadow: none;
}

.dark .indicator-item,
.dark .report-box {
  background: #0f172a;
  border-color: #334155;
}

.dark .report-content {
  color: #cbd5e1;
}

.dark :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #1e293b;
  --el-table-border-color: #334155;
  --el-table-text-color: #e2e8f0;
  --el-table-header-text-color: #cbd5e1;
  --el-table-row-hover-bg-color: #1e293b;
}

.dark :deep(.el-collapse) {
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  border-color: #334155;
}
</style>
