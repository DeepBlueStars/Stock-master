<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import dayjs from 'dayjs'
import config from '@/config/config'
import { renderMarkdown } from '@/utils/markdown'

const BASE_URL = config.baseUrl
const USER_ID = '6928fca14f206ada1b7a9269'
const router = useRouter()

const TYPE_LABELS = {
  PORTFOLIO: '组合',
  INDUSTRY: '行业',
  STOCK: '个股'
}

const SOURCE_LABELS = {
  OPENCLAW: 'OpenClaw',
  SYSTEM: '系统',
  MANUAL: '手工'
}

const filters = [
  { value: '', label: '全部' },
  { value: 'PORTFOLIO', label: '组合' },
  { value: 'INDUSTRY', label: '行业' },
  { value: 'STOCK', label: '个股' }
]

const reports = ref([])
const activeType = ref('')
const selectedId = ref('')
const detail = ref(null)
const loading = ref(false)
const loadingDetail = ref(false)
const error = ref('')
const focusMap = ref({})

const activeFocus = computed(() => {
  const symbol = detail.value?.symbol
  return symbol ? focusMap.value[symbol] || null : null
})

const latestDate = computed(() => {
  if (!reports.value.length) return '—'
  return dayjs(reports.value[0].reportDate).format('YYYY-MM-DD')
})

const rendered = computed(() => {
  if (!detail.value) return ''
  // 正文里的一级标题与页头标题重复，去掉第一个 H1
  const body = (detail.value.content || '').replace(/^\s*#\s+[^\n]*\n/, '')
  return renderMarkdown(body)
})

async function loadReports() {
  loading.value = true
  error.value = ''
  try {
    const response = await axios.post(`${BASE_URL}/research/report/list`, {
      userId: USER_ID,
      type: activeType.value || null,
      limit: 60
    })
    if (response.data && response.data.state === 0) {
      reports.value = response.data.data || []
      const stillThere = reports.value.some((item) => item.id === selectedId.value)
      if (!stillThere) {
        selectedId.value = reports.value.length ? reports.value[0].id : ''
      }
      if (selectedId.value) {
        await loadDetail(selectedId.value)
      } else {
        detail.value = null
      }
    } else {
      error.value = response.data?.message || '读取报告列表失败'
    }
  } catch (e) {
    error.value = e?.message || '网络错误'
  } finally {
    loading.value = false
  }
}

async function loadDetail(id) {
  if (!id) return
  loadingDetail.value = true
  try {
    const response = await axios.get(`${BASE_URL}/research/report/${id}`, {
      params: { userId: USER_ID }
    })
    if (response.data && response.data.state === 0) {
      detail.value = response.data.data
    }
  } catch (e) {
    console.error('读取报告正文失败', e)
  } finally {
    loadingDetail.value = false
  }
}

function selectReport(report) {
  if (selectedId.value === report.id) return
  selectedId.value = report.id
  loadDetail(report.id)
}

function pickType(value) {
  if (activeType.value === value) return
  activeType.value = value
  loadReports()
}

async function loadFocusPool() {
  try {
    const response = await axios.post(`${BASE_URL}/research/focus/list`, { userId: USER_ID })
    if (response.data && response.data.state === 0) {
      const map = {}
      for (const view of response.data.data || []) {
        if (view?.profile?.symbol) {
          map[view.profile.symbol] = view
        }
      }
      focusMap.value = map
    }
  } catch (e) {
    console.error('读取关注档案失败', e)
  }
}

function isFocused(report) {
  return Boolean(report?.symbol && focusMap.value[report.symbol])
}

function openFocus() {
  const symbol = detail.value?.symbol
  if (!symbol || !focusMap.value[symbol]) return
  router.push({ path: '/review', query: { symbol } })
}

function formatDate(value, pattern = 'YYYY-MM-DD') {
  return value ? dayjs(value).format(pattern) : '—'
}

onMounted(() => {
  loadReports()
  loadFocusPool()
})
</script>

<template>
  <div class="page report-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">报告库</h1>
        <p class="page-sub">OpenClaw 分析完把 Markdown 写进来，这里只读、不改结论。</p>
      </div>
      <div class="page-meta">
        <div class="meta">
          <span class="k">报告</span>
          <span class="v num">{{ reports.length }} 份</span>
        </div>
        <div class="meta">
          <span class="k">最近</span>
          <span class="v num">{{ latestDate }}</span>
        </div>
        <button class="ghost-btn" type="button" @click="loadReports">刷新</button>
      </div>
    </header>

    <p v-if="error" class="load-error">读不到报告：{{ error }}</p>

    <div class="split">
      <section class="sheet list-pane">
        <div class="filter-row">
          <button
            v-for="item in filters"
            :key="item.value"
            type="button"
            class="chip"
            :class="{ 'is-on': activeType === item.value }"
            @click="pickType(item.value)"
          >
            {{ item.label }}
          </button>
        </div>

        <p v-if="loading && !reports.length" class="empty">正在读取…</p>
        <p v-else-if="!reports.length" class="empty">
          还没有报告。OpenClaw 通过 MCP 写入后，这里会自动出现。
        </p>
        <div v-else class="rep-list">
          <button
            v-for="report in reports"
            :key="report.id"
            type="button"
            class="rep"
            :class="{ 'is-active': report.id === selectedId }"
            @click="selectReport(report)"
          >
            <span class="rep-date num">{{ formatDate(report.reportDate, 'MM-DD') }}</span>
            <span class="rep-body">
              <span class="rep-title">{{ report.title }}</span>
              <span class="rep-sum">{{ report.summary || '（无摘要）' }}</span>
              <span class="rep-foot">
                <span v-if="report.rating" class="rating num">{{ report.rating }}</span>
                <span class="kind">{{ TYPE_LABELS[report.type] || report.type }}</span>
                <span class="src">{{ SOURCE_LABELS[report.source] || report.source }}</span>
                <span v-if="isFocused(report)" class="in-focus">已在关注池</span>
              </span>
            </span>
          </button>
        </div>
      </section>

      <section class="sheet reader">
        <template v-if="detail">
          <header class="reader-head">
            <h2 class="reader-title">{{ detail.title }}</h2>
            <div class="reader-meta">
              <span class="num">{{ formatDate(detail.reportDate) }}</span>
              <span v-if="detail.periodStart || detail.periodEnd" class="num">
                {{ formatDate(detail.periodStart) }} ~ {{ formatDate(detail.periodEnd) }}
              </span>
              <span v-if="detail.rating" class="rating num">{{ detail.rating }}</span>
              <span class="kind">{{ TYPE_LABELS[detail.type] || detail.type }}</span>
              <span v-for="tag in detail.tags || []" :key="tag" class="tag">{{ tag }}</span>
              <button v-if="activeFocus" type="button" class="focus-link" @click="openFocus">
                打开关注档案
              </button>
            </div>
            <p v-if="detail.summary" class="reader-sum">{{ detail.summary }}</p>
          </header>
          <div v-if="loadingDetail" class="empty">正在读取正文…</div>
          <article v-else class="md" v-html="rendered"></article>
        </template>
        <p v-else class="empty reader-empty">
          左边选一份报告，正文会显示在这里。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
}

.split {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  min-height: 0;
}

.sheet {
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-sheet);
}

.list-pane {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 176px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.filter-row {
  display: flex;
  gap: 6px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--rule);
  flex: none;
}

.chip {
  border: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-3);
  font-size: var(--t-12);
  padding: 3px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: all var(--dur-1) var(--ease);
}

.chip:hover {
  color: var(--ink);
  border-color: var(--rule-strong);
}

.chip.is-on {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--surface);
}

.rep-list {
  overflow-y: auto;
  padding: 6px;
}

.rep {
  display: flex;
  gap: 10px;
  width: 100%;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius);
  padding: 10px 10px;
  cursor: pointer;
  transition: background var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease);
}

.rep:hover {
  background: var(--surface-sunken);
}

.rep.is-active {
  background: var(--accent-wash);
  border-color: var(--accent-line);
}

.rep-date {
  flex: none;
  font-size: var(--t-12);
  color: var(--ink-3);
  padding-top: 2px;
  letter-spacing: 0.02em;
}

.rep-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.rep-title {
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
  line-height: 1.4;
}

.rep-sum {
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rep-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.rating {
  font-weight: 650;
  color: var(--accent);
  letter-spacing: 0.02em;
}

.kind,
.src,
.tag {
  font-size: var(--t-11);
  color: var(--ink-3);
  border: 1px solid var(--rule);
  border-radius: 999px;
  padding: 1px 7px;
}

.in-focus {
  font-size: var(--t-11);
  color: var(--accent);
  border: 1px solid var(--accent-line);
  background: var(--accent-wash);
  border-radius: 999px;
  padding: 1px 7px;
  white-space: nowrap;
}

.focus-link {
  border: 1px solid var(--accent-line);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: var(--t-11);
  border-radius: 999px;
  padding: 2px 9px;
  cursor: pointer;
}

.focus-link:hover {
  background: var(--accent);
  color: var(--surface);
}

.reader {
  padding: 26px 34px 40px;
  max-height: calc(100vh - 176px);
  overflow-y: auto;
}

.reader-head {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--rule);
  margin-bottom: 20px;
}

.reader-title {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ink);
  line-height: 1.35;
}

.reader-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  font-size: var(--t-12);
  color: var(--ink-3);
}

.reader-sum {
  margin-top: 12px;
  padding: 10px 14px;
  border-left: 2px solid var(--accent);
  background: var(--accent-wash);
  border-radius: 0 var(--radius-s) var(--radius-s) 0;
  font-size: var(--t-13);
  line-height: 1.7;
  color: var(--ink-2);
}

.empty {
  padding: 20px 16px;
  font-size: var(--t-13);
  color: var(--ink-3);
  line-height: 1.7;
}

.reader-empty {
  padding: 60px 0;
  text-align: center;
}

.load-error {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: var(--radius);
  background: var(--danger-wash);
  color: var(--danger);
  font-size: var(--t-13);
}

.ghost-btn {
  margin-left: 10px;
  border: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--t-12);
  padding: 4px 12px;
  border-radius: var(--radius-s);
  cursor: pointer;
}

.ghost-btn:hover {
  border-color: var(--rule-strong);
  color: var(--ink);
}

/* ---------- 正文排版 ---------- */
.md {
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.9;
}

.md :deep(h1),
.md :deep(h2),
.md :deep(h3),
.md :deep(h4) {
  color: var(--ink);
  line-height: 1.45;
  letter-spacing: -0.005em;
}

.md :deep(h1) {
  font-size: 22px;
  margin: 8px 0 16px;
}

.md :deep(h2) {
  font-size: 17px;
  margin: 34px 0 12px;
  padding-top: 14px;
  border-top: 1px solid var(--rule);
}

.md :deep(h3) {
  font-size: 15px;
  margin: 24px 0 8px;
}

.md :deep(h4) {
  font-size: 14px;
  margin: 18px 0 6px;
}

.md :deep(p) {
  margin: 10px 0;
}

.md :deep(strong) {
  color: var(--ink);
  font-weight: 600;
}

.md :deep(ul),
.md :deep(ol) {
  margin: 10px 0;
  padding-left: 22px;
}

.md :deep(li) {
  margin: 5px 0;
}

.md :deep(li > ul),
.md :deep(li > ol) {
  margin: 4px 0;
}

.md :deep(blockquote) {
  margin: 14px 0;
  padding: 4px 16px;
  border-left: 2px solid var(--accent-line);
  background: var(--surface-sunken);
  border-radius: 0 var(--radius-s) var(--radius-s) 0;
  color: var(--ink-3);
}

.md :deep(blockquote p) {
  margin: 6px 0;
}

.md :deep(code) {
  font-family: var(--font-num);
  font-size: 0.92em;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--surface-sunken);
  border: 1px solid var(--rule);
  color: var(--ink);
}

.md :deep(pre) {
  margin: 14px 0;
  padding: 14px 16px;
  border-radius: var(--radius);
  background: var(--surface-sunken);
  border: 1px solid var(--rule);
  overflow-x: auto;
}

.md :deep(pre code) {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 12.5px;
  line-height: 1.7;
}

.md :deep(hr) {
  border: 0;
  height: 1px;
  background: var(--rule);
  margin: 26px 0;
}

.md :deep(a) {
  color: var(--accent);
  text-decoration: none;
  border-bottom: 1px solid var(--accent-line);
}

.md :deep(.md-table) {
  margin: 14px 0;
  overflow-x: auto;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
}

.md :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  line-height: 1.65;
}

.md :deep(th) {
  background: var(--surface-sunken);
  color: var(--ink-3);
  font-weight: 500;
  text-align: left;
  padding: 8px 12px;
  border-bottom: 1px solid var(--rule-strong);
  white-space: nowrap;
}

.md :deep(td) {
  padding: 8px 12px;
  border-bottom: 1px solid var(--rule);
  color: var(--ink-2);
  vertical-align: top;
}

.md :deep(tr:last-child td) {
  border-bottom: 0;
}

@media (max-width: 960px) {
  .split {
    grid-template-columns: 1fr;
  }
  .list-pane,
  .reader {
    position: static;
    max-height: none;
  }
}
</style>
