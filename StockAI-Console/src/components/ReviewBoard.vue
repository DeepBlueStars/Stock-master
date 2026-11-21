<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import config from '@/config/config'

const BASE_URL = config.baseUrl
const USER_ID = '6928fca14f206ada1b7a9269'
const route = useRoute()

const LEVEL_LABELS = {
  TAKE_PROFIT_1: '止盈一档',
  TAKE_PROFIT_2: '止盈二档',
  TRAILING: '移动止盈',
  REVIEW: '价格复核',
  HARD_STOP: '硬风险线',
  REBALANCE: '再平衡'
}

const LEVEL_DIRECTION = {
  TAKE_PROFIT_1: 'up',
  TAKE_PROFIT_2: 'up',
  TRAILING: 'up',
  REVIEW: 'down',
  HARD_STOP: 'down'
}

const IMPORTANCE_LABELS = { HIGH: '重要', MEDIUM: '关注', LOW: '一般' }
const STATUS_LABELS = { PENDING: '待处理', DONE: '已完成', SKIPPED: '已跳过' }

const FOCUS_STATUS_LABELS = {
  WATCHING: '关注中',
  TRIGGERED: '已触发',
  HOLDING: '已建仓',
  PAUSED: '已暂停',
  DROPPED: '已放弃',
  CLOSED: '已归档'
}

const FOCUS_FLOW = {
  WATCHING: ['TRIGGERED', 'PAUSED', 'DROPPED'],
  TRIGGERED: ['HOLDING', 'WATCHING', 'PAUSED', 'DROPPED'],
  HOLDING: ['CLOSED'],
  PAUSED: ['WATCHING', 'DROPPED'],
  DROPPED: ['WATCHING'],
  CLOSED: []
}

const FLAG_LABELS = {
  NEAR_BUY: '接近买点',
  IN_BUY_ZONE: '已进入买点',
  ABOVE_MAX: '高于不追价',
  STALE: '报告过期'
}

const TIER_STATUS_LABELS = {
  WAITING: '等待',
  TRIGGERED: '已触发',
  DONE: '已成交',
  CANCELLED: '已取消'
}

const DRAWER_TABS = [
  { key: 'buy', label: '买入计划' },
  { key: 'sell', label: '卖出纪律' },
  { key: 'monitor', label: '监测清单' },
  { key: 'memo', label: '决策备忘录' },
  { key: 'source', label: '来源报告' }
]

const overview = ref(null)
const levels = ref([])
const events = ref([])
const loading = ref(true)
const error = ref('')

const focusStocks = ref([])
const focusLoading = ref(true)
const focusError = ref('')
const drawerFocus = ref(null)
const drawerTab = ref('buy')
const drawerStatus = ref('')
const drawerNote = ref('')
const savingFocus = ref(false)

const cursor = ref(dayjs().startOf('month'))
const selectedDay = ref(dayjs().format('YYYY-MM-DD'))

function buildCard(level, holding) {
  const list = [...(level?.priceLevels || [])].sort((a, b) => Number(b.price) - Number(a.price))
  const timeList = [...(level?.timeReviews || [])].sort((a, b) => String(a.date).localeCompare(String(b.date)))
  return {
    symbol: level?.symbol || holding?.symbol,
    name: holding?.name || level?.stockName || level?.symbol,
    holding: holding || null,
    hasPosition: Boolean(holding),
    quantity: holding?.quantity ?? 0,
    price: holding?.price ?? null,
    cost: holding?.cost ?? null,
    returnPct: holding?.returnPct ?? null,
    level,
    levels: list,
    times: timeList
  }
}

const cards = computed(() => {
  const holdings = (overview.value?.holdings || []).filter((item) => (item.quantity || 0) > 0)
  const bySymbol = new Map(holdings.map((item) => [item.symbol, item]))
  const used = new Set()
  const out = []
  // 复核线是报告给的，不因“系统里没有这只票的持仓记录”就消失
  levels.value.forEach((level) => {
    out.push(buildCard(level, bySymbol.get(level.symbol) || null))
    used.add(level.symbol)
  })
  holdings.forEach((holding) => {
    if (!used.has(holding.symbol)) out.push(buildCard(null, holding))
  })
  return out
})

function directionOf(code) {
  return LEVEL_DIRECTION[code] || 'none'
}

function evaluateLevel(level, current) {
  const target = Number(level.price)
  if (!current || Number.isNaN(target)) return { cls: 'st-far', text: '无现价' }
  const dir = directionOf(level.code)
  const gap = ((current - target) / target) * 100
  if (dir === 'none') {
    return { cls: 'st-far', text: `参考 ${gap >= 0 ? '+' : ''}${gap.toFixed(1)}%` }
  }
  if (dir === 'up') {
    if (current >= target) return { cls: 'st-hit-up', text: `已到价 +${gap.toFixed(1)}%` }
    if (gap > -3) return { cls: 'st-near', text: `距 ${Math.abs(gap).toFixed(1)}%` }
    return { cls: 'st-far', text: `还差 ${Math.abs(gap).toFixed(1)}%` }
  }
  if (current <= target) return { cls: 'st-hit-down', text: `已跌破 ${Math.abs(gap).toFixed(1)}%` }
  if (gap < 3) return { cls: 'st-near', text: `距 ${gap.toFixed(1)}%` }
  return { cls: 'st-far', text: `还需 ${gap.toFixed(1)}%` }
}

function daysTo(date) {
  if (!date) return null
  return dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day')
}

function timeState(time) {
  const left = daysTo(time.date)
  if (left === null) return { cls: 'st-far', text: '—' }
  if (left < 0) return { cls: 'st-hit-down', text: `已过 ${Math.abs(left)} 天` }
  if (left === 0) return { cls: 'st-near', text: '今天' }
  if (left <= 14) return { cls: 'st-near', text: `${left} 天后` }
  return { cls: 'st-far', text: `${left} 天后` }
}

const dueCount = computed(() => {
  let count = 0
  cards.value.forEach((card) => {
    card.levels.forEach((level) => {
      const state = evaluateLevel(level, card.price)
      if (state.cls === 'st-hit-up' || state.cls === 'st-hit-down') count += 1
    })
    card.times.forEach((time) => {
      const left = daysTo(time.date)
      if (left !== null && left <= 0) count += 1
    })
  })
  return count
})

const nextReview = computed(() => {
  const dates = []
  cards.value.forEach((card) => {
    card.times.forEach((time) => {
      if (time.date) dates.push({ date: time.date, label: time.label, name: card.name })
    })
  })
  dates.sort((a, b) => String(a.date).localeCompare(String(b.date)))
  return dates[0] || null
})

const nextEvent = computed(() => {
  const today = dayjs().startOf('day')
  return (
    events.value
      .filter((event) => !dayjs(event.eventDate).isBefore(today))
      .sort((a, b) => String(a.eventDate).localeCompare(String(b.eventDate)))[0] || null
  )
})

const monthEvents = computed(() => events.value.filter((e) => dayjs(e.eventDate).isSame(cursor.value, 'month')))

const eventsByDate = computed(() => {
  const map = new Map()
  events.value.forEach((event) => {
    const key = dayjs(event.eventDate).format('YYYY-MM-DD')
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(event)
  })
  return map
})

const calendarCells = computed(() => {
  const first = cursor.value.startOf('month')
  const offset = (first.day() + 6) % 7
  const start = first.subtract(offset, 'day')
  return Array.from({ length: 42 }, (_, index) => {
    const day = start.add(index, 'day')
    const key = day.format('YYYY-MM-DD')
    return {
      key,
      day,
      label: day.date(),
      inMonth: day.isSame(cursor.value, 'month'),
      isToday: day.isSame(dayjs(), 'day'),
      events: eventsByDate.value.get(key) || []
    }
  })
})

const selectedEvents = computed(() => eventsByDate.value.get(selectedDay.value) || [])

const selectedLabel = computed(() => dayjs(selectedDay.value).format('M月D日 dddd'))

function money(value, digits = 2) {
  if (value === null || value === undefined) return '—'
  return Number(value).toFixed(digits)
}

// 低价标的（ETF、银行）保留三位，避免 1.368 被显示成 1.37
function priceText(value) {
  if (value === null || value === undefined) return '—'
  const v = Number(value)
  return v.toFixed(Math.abs(v) < 10 ? 3 : 2)
}

function signedPct(value) {
  if (value === null || value === undefined) return '—'
  const v = Number(value)
  return `${v >= 0 ? '+' : ''}${v.toFixed(2)}%`
}

function riseClass(value) {
  if (value === null || value === undefined) return ''
  return Number(value) >= 0 ? 'is-up' : 'is-down'
}

function shortDate(value) {
  return value ? dayjs(value).format('MM-DD') : '—'
}

async function loadOverview() {
  const response = await axios.get(`${BASE_URL}/exit/overview`, { params: { userId: USER_ID } })
  if (response.data?.state === 0) overview.value = response.data.data
}

async function loadLevels() {
  const response = await axios.post(`${BASE_URL}/research/review/list`, { userId: USER_ID })
  if (response.data?.state === 0) levels.value = response.data.data || []
}

function monthRange() {
  const from = dayjs().subtract(180, 'day').format('YYYY-MM-DD')
  const to = dayjs().add(400, 'day').format('YYYY-MM-DD')
  return { from, to }
}

async function loadEvents() {
  const { from, to } = monthRange()
  const response = await axios.post(`${BASE_URL}/research/event/list`, { userId: USER_ID, from, to })
  if (response.data?.state === 0) events.value = response.data.data || []
}

/* ---------------- 关注池 ---------------- */

async function loadFocus() {
  focusLoading.value = true
  focusError.value = ''
  try {
    const response = await axios.post(`${BASE_URL}/research/focus/list`, { userId: USER_ID })
    if (response.data?.state === 0) {
      focusStocks.value = response.data.data || []
    } else {
      focusError.value = response.data?.message || '读取关注档案失败'
    }
  } catch (e) {
    focusError.value = e?.message || '网络错误'
  } finally {
    focusLoading.value = false
  }
}

function tiersOf(view) {
  const tiers = view?.profile?.buyPlan?.tiers || []
  return [...tiers].sort((a, b) => (a.tier || 0) - (b.tier || 0))
}

function rangeText(tier) {
  if (tier.priceLow === null || tier.priceLow === undefined) return priceText(tier.priceHigh)
  if (tier.priceHigh === null || tier.priceHigh === undefined) return priceText(tier.priceLow)
  return `${priceText(tier.priceLow)} – ${priceText(tier.priceHigh)}`
}

function tierState(view, tier) {
  if (tier.status === 'DONE') return { cls: 'tier-done', text: '已成交' }
  if (tier.status === 'CANCELLED') return { cls: 'tier-off', text: '已取消' }
  if (tier.status === 'TRIGGERED') return { cls: 'tier-hit', text: '已触发' }
  const price = Number(view.price)
  const low = Number(tier.priceLow)
  const high = Number(tier.priceHigh)
  if (!price || Number.isNaN(low) || Number.isNaN(high)) return { cls: 'tier-off', text: '无现价' }
  if (price >= low && price <= high) return { cls: 'tier-hit', text: '已进入区间' }
  if (price > high) {
    const gap = ((price - high) / price) * 100
    return { cls: gap <= 3 ? 'tier-near' : 'tier-far', text: `还需跌 ${gap.toFixed(1)}%` }
  }
  return { cls: 'tier-below', text: '已低于该档' }
}

function distanceText(view) {
  const gap = view.distanceToTier1Pct
  if (gap === null || gap === undefined) return ''
  return Number(gap) < 0
    ? `距第一档上沿还需下跌 ${Math.abs(Number(gap)).toFixed(1)}%`
    : `已低于第一档上沿 ${Number(gap).toFixed(1)}%`
}

function hasFlag(view, flag) {
  return (view.derivedFlags || []).includes(flag)
}

function focusReportDate(view) {
  return view?.profile?.reportDate ? dayjs(view.profile.reportDate).format('YYYY-MM-DD') : '—'
}

function monitorPreview(view) {
  return (view?.profile?.monitorItems || []).slice(0, 4)
}

function sellPreview(view) {
  return (view?.profile?.sellDiscipline?.targets || []).slice(0, 3)
}

function openDrawer(view) {
  drawerFocus.value = view
  drawerTab.value = 'buy'
  drawerStatus.value = view.profile.status
  drawerNote.value = view.profile.statusNote || ''
}

function closeDrawer() {
  drawerFocus.value = null
}

const drawerStatusOptions = computed(() => {
  const current = drawerFocus.value?.profile?.status
  if (!current) return []
  return [current, ...(FOCUS_FLOW[current] || [])]
})

const drawerTimeline = computed(() => {
  const list = drawerFocus.value?.profile?.timeline || []
  return [...list].reverse().slice(0, 8)
})

const drawerTiers = computed(() => tiersOf(drawerFocus.value))

const drawerStops = computed(() => {
  const stops = drawerFocus.value?.profile?.sellDiscipline?.stops
  if (!stops) return []
  return [
    { key: 'fundamental', label: '基本面', text: stops.fundamental },
    { key: 'priceStop', label: '价格', text: stops.priceStop },
    { key: 'portfolioStop', label: '组合', text: stops.portfolioStop },
    { key: 'systematicException', label: '系统性下跌例外', text: stops.systematicException }
  ].filter((item) => item.text)
})

function syncDrawer(symbol) {
  if (!drawerFocus.value) return
  const next = focusStocks.value.find((item) => item.profile.symbol === symbol)
  if (!next) {
    drawerFocus.value = null
    return
  }
  drawerFocus.value = next
  drawerStatus.value = next.profile.status
  drawerNote.value = next.profile.statusNote || ''
}

async function postFocusStatus(symbol, payload) {
  try {
    const response = await axios.post(`${BASE_URL}/research/focus/status`, {
      userId: USER_ID,
      symbol,
      ...payload
    })
    if (response.data?.state === 0) {
      ElMessage.success('已更新')
      await loadFocus()
      syncDrawer(symbol)
      return true
    }
    ElMessage.error(response.data?.message || '更新失败')
  } catch (e) {
    ElMessage.error(e?.message || '网络错误')
  }
  return false
}

async function markTriggered(view) {
  const tiers = tiersOf(view)
  const first = tiers.find((tier) => tier.status !== 'DONE' && tier.status !== 'CANCELLED') || tiers[0]
  await postFocusStatus(view.profile.symbol, {
    status: 'TRIGGERED',
    tier: first?.tier,
    tierStatus: 'TRIGGERED',
    note: '手工标记买点触发'
  })
}

function askReason(view, status) {
  const label = FOCUS_STATUS_LABELS[status] || status
  ElMessageBox.prompt(`放弃/暂停需要留一句原因，方便以后回看当时的判断。`, `标记为${label}`, {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '例如：逻辑已破坏，不再跟踪',
    inputValidator: (value) => (value && value.trim() ? true : '原因不能为空')
  })
    .then(async ({ value }) => {
      await postFocusStatus(view.profile.symbol, { status, note: value.trim() })
    })
    .catch(() => {})
}

async function saveDrawerStatus() {
  if (!drawerFocus.value) return
  const symbol = drawerFocus.value.profile.symbol
  const payload = {}
  if (drawerStatus.value !== drawerFocus.value.profile.status) {
    payload.status = drawerStatus.value
  }
  if (drawerNote.value && drawerNote.value.trim()) {
    payload.note = drawerNote.value.trim()
  }
  if (!payload.status && !payload.note) {
    ElMessage.info('没有需要保存的改动')
    return
  }
  savingFocus.value = true
  await postFocusStatus(symbol, payload)
  savingFocus.value = false
}

async function setTierStatus(tier, tierStatus) {
  if (!drawerFocus.value) return
  const view = drawerFocus.value
  const payload = { tier: tier.tier, tierStatus }
  if (tierStatus === 'TRIGGERED' && view.profile.status === 'WATCHING') {
    payload.status = 'TRIGGERED'
  }
  if (tierStatus === 'DONE' && view.profile.status === 'TRIGGERED') {
    payload.status = 'HOLDING'
  }
  await postFocusStatus(view.profile.symbol, payload)
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadOverview(), loadLevels(), loadEvents(), loadFocus()])
    // 本月没有安排时，直接把日历停在“下一个事件”那个月，别让人对着空日历
    const hasThisMonth = events.value.some((event) => dayjs(event.eventDate).isSame(dayjs(), 'month'))
    if (!hasThisMonth && nextEvent.value) {
      const target = dayjs(nextEvent.value.eventDate)
      cursor.value = target.startOf('month')
      selectedDay.value = target.format('YYYY-MM-DD')
    }
  } catch (e) {
    error.value = e?.message || '网络错误'
  } finally {
    loading.value = false
  }
}

function shiftMonth(step) {
  cursor.value = cursor.value.add(step, 'month')
}

function goToday() {
  cursor.value = dayjs().startOf('month')
  selectedDay.value = dayjs().format('YYYY-MM-DD')
}

function pickDay(cell) {
  selectedDay.value = cell.key
  if (!cell.inMonth) {
    cursor.value = cell.day.startOf('month')
  }
}

async function setStatus(event, status) {
  if (event.status === status) return
  try {
    const response = await axios.post(`${BASE_URL}/research/event/status`, {
      userId: USER_ID,
      id: event.id,
      status
    })
    if (response.data?.state === 0) {
      event.status = status
    }
  } catch (e) {
    console.error('更新事件状态失败', e)
  }
}

onMounted(async () => {
  await loadAll()
  const symbol = route.query.symbol
  if (symbol) {
    const hit = focusStocks.value.find((item) => item.profile.symbol === String(symbol))
    if (hit) openDrawer(hit)
  }
})
</script>

<template>
  <div class="page review-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">复核台</h1>
        <p class="page-sub">报告给的价，只当“什么时候该回头看一眼”用；触发之后让 OpenClaw 重读论文，不自动下单。</p>
      </div>
      <div class="page-meta">
        <div class="meta">
          <span class="k">已到价 / 已到期</span>
          <span class="v num" :class="{ 'is-danger': dueCount > 0 }">{{ loading ? '—' : dueCount }}</span>
        </div>
        <div class="meta">
          <span class="k">下次复核</span>
          <span class="v num">{{ nextReview ? shortDate(nextReview.date) : '—' }}</span>
        </div>
        <div class="meta">
          <span class="k">下次事件</span>
          <span class="v num">{{ nextEvent ? shortDate(nextEvent.eventDate) : '—' }}</span>
        </div>
        <button class="ghost-btn" type="button" @click="loadAll">刷新</button>
      </div>
    </header>

    <p v-if="error" class="load-error">读不到数据：{{ error }}</p>

    <!-- ============ 价格复核 ============ -->
    <section class="block">
      <div class="block-head">
        <h2 class="block-title">价格复核线</h2>
        <span class="block-note">来自最近一份组合报告；现价取决策台同一份行情</span>
      </div>

      <p v-if="loading" class="empty">正在读取…</p>
      <p v-else-if="!cards.length" class="empty">
        还没有持仓或复核线。OpenClaw 通过 MCP 写入 `review_level` 后，这里会按股票列出价位。
      </p>
      <div v-else class="card-grid">
        <article v-for="card in cards" :key="card.symbol" class="holding-card">
          <header class="hc-head">
            <div class="hc-id">
              <span class="hc-name">{{ card.name }}</span>
              <span class="hc-symbol num">{{ card.symbol }}</span>
            </div>
            <div class="hc-price">
              <span class="hc-last num" :class="riseClass(card.returnPct)">{{ priceText(card.price) }}</span>
              <span class="hc-return num" :class="riseClass(card.returnPct)">{{ signedPct(card.returnPct) }}</span>
            </div>
          </header>

          <p class="hc-meta num">
            <template v-if="card.hasPosition">
              {{ card.quantity }} 股 · 成本 {{ money(card.cost, 3) }}
            </template>
            <template v-else>系统里没有这只票的持仓记录</template>
            <template v-if="card.level && card.level.reportDate"> · 来源 {{ shortDate(card.level.reportDate) }}</template>
          </p>
          <p v-if="card.level?.note" class="hc-note">{{ card.level.note }}</p>

          <div v-if="card.levels.length" class="ladder">
            <div v-for="level in card.levels" :key="level.code" class="rung">
              <span class="rung-label">{{ level.label || LEVEL_LABELS[level.code] || level.code }}</span>
              <span class="rung-price num">{{ priceText(level.price) }}</span>
              <span class="rung-state" :class="evaluateLevel(level, card.price).cls">
                {{ evaluateLevel(level, card.price).text }}
              </span>
              <span v-if="level.action" class="rung-action">{{ level.action }}</span>
            </div>
          </div>
          <p v-else class="hc-none">OpenClaw 还没给这只票写复核价位。</p>

          <div v-if="card.times.length" class="times">
            <div class="times-head">时间复核</div>
            <div v-for="time in card.times" :key="time.date + time.label" class="time-row">
              <span class="time-date num">{{ shortDate(time.date) }}</span>
              <span class="time-label">{{ time.label }}</span>
              <span class="time-state" :class="timeState(time).cls">{{ timeState(time).text }}</span>
            </div>
            <p v-if="card.times[0]?.question" class="time-q">复核问题：{{ card.times[0].question }}</p>
          </div>

          <div v-if="card.level?.thesisStops?.length" class="thesis">
            <div class="thesis-head">论文止损</div>
            <ul>
              <li v-for="item in card.level.thesisStops" :key="item">{{ item }}</li>
            </ul>
          </div>
        </article>
      </div>
    </section>

    <!-- ============ 关注池 ============ -->
    <section class="block">
      <div class="block-head">
        <h2 class="block-title">关注池</h2>
        <span class="block-note">报告给的买点与纪律；价格只算距离，状态由人确认</span>
      </div>

      <p v-if="focusError" class="load-error">读不到关注档案：{{ focusError }}</p>
      <p v-if="focusLoading" class="empty">正在读取…</p>
      <p v-else-if="!focusStocks.length" class="empty">
        关注池里还没有带报告的股票。OpenClaw 通过 MCP 调用 `save_focus_stock` 后，这里会出现买入阶梯、卖出纪律与监测清单。
      </p>
      <div v-else class="focus-grid">
        <article
          v-for="view in focusStocks"
          :key="view.profile.symbol"
          class="focus-card"
          :class="{ 'is-hot': hasFlag(view, 'IN_BUY_ZONE'), 'is-stale': hasFlag(view, 'STALE') }"
        >
          <header class="wc-head">
            <div class="wc-id">
              <span class="wc-name">{{ view.profile.stockName || view.profile.symbol }}</span>
              <span class="wc-symbol num">{{ view.profile.symbol }}</span>
            </div>
            <div class="wc-price">
              <span class="wc-last num" :class="riseClass(view.changePct)">{{ priceText(view.price) }}</span>
              <span class="wc-chg num" :class="riseClass(view.changePct)">{{ signedPct(view.changePct) }}</span>
            </div>
          </header>

          <div class="wc-tags">
            <span class="wflag" :class="'wst-' + view.profile.status.toLowerCase()">
              {{ FOCUS_STATUS_LABELS[view.profile.status] || view.profile.status }}
            </span>
            <span v-for="flag in view.derivedFlags" :key="flag" class="wflag" :class="'wflag-' + flag.toLowerCase()">
              {{ FLAG_LABELS[flag] || flag }}
            </span>
          </div>

          <p class="wc-stance">
            {{ view.profile.stance || '—' }}
            <span v-if="view.profile.oneLine" class="wc-oneline">· {{ view.profile.oneLine }}</span>
          </p>

          <div class="wc-cols">
            <div class="wc-col">
              <div class="wc-col-head">买入阶梯</div>
              <div v-for="tier in tiersOf(view)" :key="tier.tier" class="tier-row">
                <span class="tier-label">{{ tier.label || '第 ' + tier.tier + ' 档' }}</span>
                <span class="tier-price num">{{ rangeText(tier) }}</span>
                <span class="tier-state" :class="tierState(view, tier).cls">{{ tierState(view, tier).text }}</span>
              </div>
              <p v-if="distanceText(view)" class="wc-hint num">{{ distanceText(view) }}</p>
              <p v-if="!tiersOf(view).length" class="wc-hint">报告没给分档买点。</p>
            </div>

            <div class="wc-col">
              <div class="wc-col-head">卖出纪律</div>
              <div v-for="target in sellPreview(view)" :key="target.action + target.priceLow" class="sell-row">
                <span class="sell-price num">{{ rangeText(target) }}</span>
                <span class="sell-action">{{ target.action || '—' }}</span>
              </div>
              <p v-if="view.profile.sellDiscipline?.invalidation?.length" class="wc-hint">
                {{ view.profile.sellDiscipline.invalidation.length }} 条证伪条件
              </p>
              <p v-if="!sellPreview(view).length" class="wc-hint">报告没给卖出目标。</p>
            </div>

            <div class="wc-col">
              <div class="wc-col-head">监测清单</div>
              <div v-for="item in monitorPreview(view)" :key="item.key || item.metric" class="mon-row">
                <span class="mon-metric">{{ item.metric }}</span>
                <span class="mon-value num">{{ item.currentValue || '—' }}</span>
                <span v-if="item.eventDate" class="mon-date num">{{ shortDate(item.eventDate) }}</span>
              </div>
              <p v-if="view.profile.monitorItems?.length > 4" class="wc-hint">
                还有 {{ view.profile.monitorItems.length - 4 }} 项，打开档案看全部
              </p>
              <p v-if="!view.profile.monitorItems?.length" class="wc-hint">报告没给监测项。</p>
            </div>
          </div>

          <footer class="wc-foot">
            <span class="wc-report num">报告 {{ focusReportDate(view) }}</span>
            <button type="button" class="mini-btn" @click="openDrawer(view)">查看档案</button>
            <button
              v-if="view.profile.status === 'WATCHING'"
              type="button"
              class="mini-btn"
              @click="markTriggered(view)"
            >标记已触发</button>
            <button
              v-if="view.profile.status === 'WATCHING'"
              type="button"
              class="mini-btn"
              @click="askReason(view, 'DROPPED')"
            >放弃跟踪</button>
          </footer>
        </article>
      </div>
    </section>

    <!-- ============ 监测日历 ============ -->
    <section class="block">
      <div class="block-head">
        <h2 class="block-title">监测日历</h2>
        <span class="block-note">报告“八、监测清单”落到具体日期；到点由人（或 OpenClaw）去看，不在这里推送</span>
      </div>

      <div class="cal-sheet">
        <div class="cal-main">
          <div class="cal-bar">
            <div class="cal-month num">{{ cursor.format('YYYY 年 M 月') }}</div>
            <div class="cal-nav">
              <button type="button" class="nav-btn" @click="shiftMonth(-1)">←</button>
              <button type="button" class="nav-btn today" @click="goToday">本月</button>
              <button type="button" class="nav-btn" @click="shiftMonth(1)">→</button>
            </div>
          </div>

          <div class="cal-week">
            <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</span>
          </div>

          <div class="cal-grid">
            <button
              v-for="cell in calendarCells"
              :key="cell.key"
              type="button"
              class="cal-cell"
              :class="{
                'is-out': !cell.inMonth,
                'is-today': cell.isToday,
                'is-selected': cell.key === selectedDay,
                'has-events': cell.events.length > 0
              }"
              @click="pickDay(cell)"
            >
              <span class="cell-day num">{{ cell.label }}</span>
              <span class="cell-events">
                <span
                  v-for="event in cell.events.slice(0, 2)"
                  :key="event.id"
                  class="cell-event"
                  :class="'imp-' + (event.importance || 'MEDIUM').toLowerCase()"
                >
                  {{ event.title }}
                </span>
                <span v-if="cell.events.length > 2" class="cell-more num">+{{ cell.events.length - 2 }}</span>
              </span>
            </button>
          </div>
        </div>

        <aside class="cal-side">
          <div class="side-head">
            <span class="side-date num">{{ selectedLabel }}</span>
            <span class="side-count num">{{ selectedEvents.length }} 个事件</span>
          </div>

          <p v-if="!selectedEvents.length" class="empty side-empty">这天没有安排。</p>
          <div v-else class="event-list">
            <article v-for="event in selectedEvents" :key="event.id" class="event">
              <div class="event-top">
                <span class="imp-dot" :class="'imp-' + (event.importance || 'MEDIUM').toLowerCase()"></span>
                <span class="event-title">{{ event.title }}</span>
                <span class="event-imp">{{ IMPORTANCE_LABELS[event.importance] || event.importance }}</span>
              </div>
              <p v-if="event.category" class="event-cat">{{ event.category }}</p>
              <p v-if="event.detail" class="event-detail">{{ event.detail }}</p>
              <p v-if="event.action" class="event-action">→ {{ event.action }}</p>
              <div class="event-foot">
                <span class="num">{{ shortDate(event.eventDate) }}</span>
                <span class="event-status" :class="'st-' + event.status.toLowerCase()">{{ STATUS_LABELS[event.status] }}</span>
                <button
                  v-if="event.status !== 'DONE'"
                  type="button"
                  class="mini-btn"
                  @click="setStatus(event, 'DONE')"
                >标记完成</button>
                <button
                  v-if="event.status !== 'PENDING'"
                  type="button"
                  class="mini-btn"
                  @click="setStatus(event, 'PENDING')"
                >撤回</button>
              </div>
            </article>
          </div>

          <div v-if="monthEvents.length" class="month-list">
            <div class="month-list-head num">本月全部 {{ monthEvents.length }} 项</div>
            <button
              v-for="event in monthEvents"
              :key="'m-' + event.id"
              type="button"
              class="month-row"
              @click="selectedDay = dayjs(event.eventDate).format('YYYY-MM-DD')"
            >
              <span class="num">{{ shortDate(event.eventDate) }}</span>
              <span class="mr-title">{{ event.title }}</span>
              <span class="imp-dot" :class="'imp-' + (event.importance || 'MEDIUM').toLowerCase()"></span>
            </button>
          </div>
        </aside>
      </div>
    </section>

    <!-- ============ 关注档案抽屉 ============ -->
    <div v-if="drawerFocus" class="drawer-mask" @click.self="closeDrawer">
      <aside class="drawer">
        <header class="drawer-head">
          <div>
            <h3 class="drawer-title">{{ drawerFocus.profile.stockName || drawerFocus.profile.symbol }}</h3>
            <p class="drawer-sub num">
              {{ drawerFocus.profile.symbol }} · 报告 {{ focusReportDate(drawerFocus) }}
              <template v-if="drawerFocus.profile.reportDate"> · 已 {{ drawerFocus.staleDays }} 天</template>
            </p>
          </div>
          <button type="button" class="drawer-close" @click="closeDrawer">×</button>
        </header>

        <div class="drawer-flags">
          <span class="wflag" :class="'wst-' + drawerFocus.profile.status.toLowerCase()">
            {{ FOCUS_STATUS_LABELS[drawerFocus.profile.status] || drawerFocus.profile.status }}
          </span>
          <span
            v-for="flag in drawerFocus.derivedFlags"
            :key="'d-' + flag"
            class="wflag"
            :class="'wflag-' + flag.toLowerCase()"
          >{{ FLAG_LABELS[flag] || flag }}</span>
          <span class="drawer-price num">
            {{ priceText(drawerFocus.price) }}
            <em :class="riseClass(drawerFocus.changePct)">{{ signedPct(drawerFocus.changePct) }}</em>
          </span>
        </div>

        <nav class="drawer-tabs">
          <button
            v-for="tab in DRAWER_TABS"
            :key="tab.key"
            type="button"
            class="drawer-tab"
            :class="{ 'is-on': drawerTab === tab.key }"
            @click="drawerTab = tab.key"
          >{{ tab.label }}</button>
        </nav>

        <div class="drawer-body">
          <!-- 买入计划 -->
          <div v-if="drawerTab === 'buy'">
            <p class="drawer-stance">
              {{ drawerFocus.profile.stance || '—' }}
              <span v-if="drawerFocus.profile.oneLine" class="wc-oneline">· {{ drawerFocus.profile.oneLine }}</span>
            </p>
            <p v-if="drawerFocus.profile.coreThesis" class="drawer-thesis">{{ drawerFocus.profile.coreThesis }}</p>

            <div class="kv-grid num">
              <span class="kv-k">不追价</span>
              <span class="kv-v">{{ priceText(drawerFocus.profile.buyPlan?.maxBuyPrice) }}</span>
              <span class="kv-k">上限</span>
              <span class="kv-v">
                {{ drawerFocus.profile.buyPlan?.maxShares ?? '—' }} 股
                / {{ drawerFocus.profile.buyPlan?.maxWeightPct ?? '—' }}%
              </span>
              <span class="kv-k">持有期</span>
              <span class="kv-v">{{ drawerFocus.profile.buyPlan?.holdingPeriod || '—' }}</span>
            </div>

            <ul v-if="drawerFocus.profile.buyPlan?.rules?.length" class="plain-list">
              <li v-for="rule in drawerFocus.profile.buyPlan.rules" :key="rule">{{ rule }}</li>
            </ul>

            <div v-for="tier in drawerTiers" :key="'dt-' + tier.tier" class="tier-block">
              <div class="tier-block-head">
                <span class="tier-block-label">{{ tier.label || '第 ' + tier.tier + ' 档' }}</span>
                <span class="tier-block-price num">{{ rangeText(tier) }}</span>
                <span class="tier-state" :class="tierState(drawerFocus, tier).cls">
                  {{ TIER_STATUS_LABELS[tier.status] || tier.status || '等待' }}
                </span>
              </div>
              <p class="tier-block-meta num">
                {{ tier.shares ?? '—' }} 股
                <template v-if="tier.weightPct"> · 约 {{ tier.weightPct }}%</template>
                <template v-if="tier.condition"> · 条件：{{ tier.condition }}</template>
              </p>
              <p v-if="tier.note" class="tier-block-note">{{ tier.note }}</p>
              <div class="tier-actions">
                <button
                  v-if="tier.status === 'WAITING'"
                  type="button"
                  class="mini-btn"
                  @click="setTierStatus(tier, 'TRIGGERED')"
                >标记触发</button>
                <button
                  v-if="tier.status === 'TRIGGERED'"
                  type="button"
                  class="mini-btn"
                  @click="setTierStatus(tier, 'DONE')"
                >标记成交</button>
                <button
                  v-if="tier.status === 'WAITING' || tier.status === 'TRIGGERED'"
                  type="button"
                  class="mini-btn"
                  @click="setTierStatus(tier, 'CANCELLED')"
                >取消该档</button>
              </div>
            </div>
            <p v-if="!drawerTiers.length" class="empty">报告没给分档买点。</p>
          </div>

          <!-- 卖出纪律 -->
          <div v-if="drawerTab === 'sell'">
            <div class="drawer-section">
              <div class="drawer-section-head">卖出目标</div>
              <div
                v-for="target in drawerFocus.profile.sellDiscipline?.targets || []"
                :key="'st-' + target.priceLow + target.action"
                class="target-row"
              >
                <span class="target-price num">{{ rangeText(target) }}</span>
                <span class="target-valuation">{{ target.valuation || '' }}</span>
                <span class="target-action">{{ target.action || '—' }}</span>
              </div>
              <p v-if="!drawerFocus.profile.sellDiscipline?.targets?.length" class="empty">报告没给卖出目标。</p>
            </div>

            <div v-if="drawerStops.length" class="drawer-section">
              <div class="drawer-section-head">止损规则</div>
              <div v-for="stop in drawerStops" :key="stop.key" class="stop-row">
                <span class="stop-label">{{ stop.label }}</span>
                <span class="stop-text">{{ stop.text }}</span>
              </div>
            </div>

            <div v-if="drawerFocus.profile.sellDiscipline?.invalidation?.length" class="drawer-section">
              <div class="drawer-section-head">基本面证伪条件</div>
              <ul class="plain-list">
                <li v-for="item in drawerFocus.profile.sellDiscipline.invalidation" :key="item">{{ item }}</li>
              </ul>
            </div>

            <div v-if="drawerFocus.profile.sellDiscipline?.addSignals?.length" class="drawer-section">
              <div class="drawer-section-head">加仓信号</div>
              <ul class="plain-list">
                <li v-for="item in drawerFocus.profile.sellDiscipline.addSignals" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>

          <!-- 监测清单 -->
          <div v-if="drawerTab === 'monitor'">
            <div
              v-for="item in drawerFocus.profile.monitorItems || []"
              :key="'mi-' + (item.key || item.metric)"
              class="monitor-item"
            >
              <div class="monitor-head">
                <span class="monitor-metric">{{ item.metric }}</span>
                <span class="monitor-value num">{{ item.currentValue || '—' }}</span>
              </div>
              <p class="monitor-line">
                <span class="monitor-k">警戒线</span>{{ item.threshold || '—' }}
              </p>
              <p v-if="item.action" class="monitor-line">
                <span class="monitor-k">动作</span>{{ item.action }}
              </p>
              <p class="monitor-foot num">
                <span v-if="item.eventDate">{{ shortDate(item.eventDate) }} 已进日历</span>
                <span v-else-if="item.cadence">{{ item.cadence }}</span>
                <span>{{ IMPORTANCE_LABELS[item.importance] || item.importance || '关注' }}</span>
              </p>
            </div>
            <p v-if="!drawerFocus.profile.monitorItems?.length" class="empty">报告没给监测项。</p>
          </div>

          <!-- 决策备忘录 -->
          <div v-if="drawerTab === 'memo'">
            <div
              v-for="dim in drawerFocus.profile.decisionMemo?.dimensions || []"
              :key="'dim-' + dim.name"
              class="dim-row"
            >
              <div class="dim-head">
                <span class="dim-name">{{ dim.name }}</span>
                <span class="dim-conf num">{{ dim.confidence || '—' }}</span>
              </div>
              <p class="dim-text">{{ dim.conclusion }}</p>
            </div>

            <div v-if="drawerFocus.profile.decisionMemo?.emptyPosition" class="memo-block">
              <div class="memo-k">空仓者</div>
              <p class="memo-v">{{ drawerFocus.profile.decisionMemo.emptyPosition }}</p>
            </div>
            <div v-if="drawerFocus.profile.decisionMemo?.holder" class="memo-block">
              <div class="memo-k">持仓者</div>
              <p class="memo-v">{{ drawerFocus.profile.decisionMemo.holder }}</p>
            </div>
            <div v-if="drawerFocus.profile.decisionMemo?.sellSignals?.length" class="memo-block">
              <div class="memo-k">卖出信号</div>
              <ul class="plain-list">
                <li v-for="item in drawerFocus.profile.decisionMemo.sellSignals" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div v-if="drawerFocus.profile.decisionMemo?.addSignals?.length" class="memo-block">
              <div class="memo-k">加仓信号</div>
              <ul class="plain-list">
                <li v-for="item in drawerFocus.profile.decisionMemo.addSignals" :key="item">{{ item }}</li>
              </ul>
            </div>
            <p v-if="drawerFocus.profile.decisionMemo?.oneLine" class="memo-oneline">
              {{ drawerFocus.profile.decisionMemo.oneLine }}
            </p>
          </div>

          <!-- 来源报告 -->
          <div v-if="drawerTab === 'source'">
            <div class="kv-grid num">
              <span class="kv-k">报告日期</span>
              <span class="kv-v">{{ focusReportDate(drawerFocus) }}</span>
              <span class="kv-k">报告ID</span>
              <span class="kv-v">{{ drawerFocus.profile.reportId || '—' }}</span>
              <span class="kv-k">更新时间</span>
              <span class="kv-v">{{ drawerFocus.profile.updatedAt ? dayjs(drawerFocus.profile.updatedAt).format('YYYY-MM-DD HH:mm') : '—' }}</span>
            </div>
            <p class="drawer-stance">
              整份 Markdown 留在报告库，这里只存 §10 / §12 / §14 的摘要。
            </p>
            <router-link class="primary-btn drawer-link" :to="{ path: '/reports' }">打开报告库</router-link>

            <div v-if="drawerTimeline.length" class="drawer-section">
              <div class="drawer-section-head">时间线</div>
              <div v-for="item in drawerTimeline" :key="item.at + item.text" class="timeline-row">
                <span class="timeline-at num">{{ dayjs(item.at).format('MM-DD HH:mm') }}</span>
                <span class="timeline-text">{{ item.text }}</span>
              </div>
            </div>
          </div>
        </div>

        <footer class="drawer-foot">
          <select v-model="drawerStatus" class="drawer-select">
            <option v-for="status in drawerStatusOptions" :key="status" :value="status">
              {{ FOCUS_STATUS_LABELS[status] || status }}
            </option>
          </select>
          <input v-model="drawerNote" class="drawer-input" placeholder="备注（暂停/放弃必填）" />
          <button type="button" class="mini-btn" @click="askReason(drawerFocus, 'PAUSED')">暂停</button>
          <button type="button" class="primary-btn" :disabled="savingFocus" @click="saveDrawerStatus">
            {{ savingFocus ? '保存中…' : '保存' }}
          </button>
        </footer>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.block-title {
  font-size: var(--t-15);
  font-weight: 650;
  color: var(--ink);
  letter-spacing: -0.005em;
}

.block-note {
  font-size: var(--t-12);
  color: var(--ink-3);
}

/* ---------- 持仓卡 ---------- */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 14px;
}

.holding-card {
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-sheet);
  padding: 18px 20px 16px;
}

.hc-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hc-id {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hc-name {
  font-size: var(--t-15);
  font-weight: 650;
  color: var(--ink);
}

.hc-symbol {
  font-size: var(--t-11);
  color: var(--ink-4);
  letter-spacing: 0.04em;
}

.hc-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
}

.hc-last {
  font-size: var(--t-21);
  line-height: 1.1;
  letter-spacing: -0.01em;
}

.hc-return {
  font-size: var(--t-12);
}

.hc-meta {
  margin-top: 6px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.hc-note {
  margin-top: 4px;
  font-size: var(--t-11);
  color: var(--ink-3);
  line-height: 1.6;
}

.ladder {
  margin-top: 14px;
  border-top: 1px solid var(--rule);
}

.rung {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--rule);
}

.rung:last-child {
  border-bottom: 0;
}

.rung-label {
  font-size: var(--t-13);
  color: var(--ink-2);
}

.rung-price {
  font-size: var(--t-13);
  color: var(--ink);
  font-weight: 600;
}

.rung-state {
  font-size: var(--t-11);
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--rule);
  color: var(--ink-3);
  white-space: nowrap;
}

.rung-state.st-hit-up {
  border-color: var(--accent-line);
  background: var(--accent-wash);
  color: var(--accent);
}

.rung-state.st-hit-down {
  border-color: var(--danger);
  background: var(--danger-wash);
  color: var(--danger);
}

.rung-state.st-near {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-wash);
}

.rung-action {
  grid-column: 1 / -1;
  font-size: var(--t-11);
  color: var(--ink-3);
  line-height: 1.5;
  margin-top: -2px;
  padding-bottom: 2px;
}

.hc-none {
  margin-top: 14px;
  font-size: var(--t-12);
  color: var(--ink-4);
}

.times {
  margin-top: 12px;
  border-top: 1px solid var(--rule);
  padding-top: 10px;
}

.times-head,
.thesis-head {
  font-size: var(--t-11);
  color: var(--ink-4);
  letter-spacing: 0.06em;
  margin-bottom: 6px;
}

.time-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: baseline;
  padding: 3px 0;
}

.time-date {
  font-size: var(--t-12);
  color: var(--ink-2);
}

.time-label {
  font-size: var(--t-12);
  color: var(--ink-2);
}

.time-state {
  font-size: var(--t-11);
  color: var(--ink-4);
  white-space: nowrap;
}

.time-state.st-near {
  color: var(--warn);
}

.time-state.st-hit-down {
  color: var(--danger);
  font-weight: 600;
}

.time-q {
  margin-top: 6px;
  font-size: var(--t-11);
  color: var(--ink-3);
  line-height: 1.6;
}

.thesis {
  margin-top: 12px;
  border-top: 1px solid var(--rule);
  padding-top: 10px;
}

.thesis ul {
  margin: 0;
  padding-left: 16px;
}

.thesis li {
  font-size: var(--t-11);
  color: var(--ink-3);
  line-height: 1.65;
}

/* ---------- 关注池 ---------- */
.focus-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.focus-card {
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-sheet);
  padding: 18px 20px 14px;
  transition: border-color var(--dur-1) var(--ease);
}

.focus-card.is-hot {
  border-color: var(--accent-line);
  box-shadow: inset 3px 0 0 var(--accent), var(--shadow-sheet);
}

.focus-card.is-stale {
  opacity: 0.82;
}

.wc-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.wc-id {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wc-name {
  font-size: var(--t-15);
  font-weight: 650;
  color: var(--ink);
}

.wc-symbol {
  font-size: var(--t-11);
  color: var(--ink-4);
  letter-spacing: 0.04em;
}

.wc-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
}

.wc-last {
  font-size: var(--t-21);
  line-height: 1.1;
  letter-spacing: -0.01em;
}

.wc-chg {
  font-size: var(--t-12);
}

.wc-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.wflag {
  font-size: 10.5px;
  line-height: 1.6;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--rule);
  color: var(--ink-3);
  white-space: nowrap;
}

.wflag.wst-watching {
  border-color: var(--accent-line);
  color: var(--accent);
  background: var(--accent-wash);
}

.wflag.wst-triggered {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-wash);
}

.wflag.wst-holding {
  border-color: var(--accent-line);
  color: var(--accent);
}

.wflag.wst-paused,
.wflag.wst-dropped,
.wflag.wst-closed {
  color: var(--ink-4);
}

.wflag.wflag-in_buy_zone {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--surface);
}

.wflag.wflag-near_buy {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-wash);
}

.wflag.wflag-above_max {
  color: var(--ink-3);
}

.wflag.wflag-stale {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-wash);
}

.wc-stance {
  margin-top: 8px;
  font-size: var(--t-13);
  color: var(--ink-2);
  line-height: 1.6;
}

.wc-oneline {
  color: var(--ink-4);
}

.wc-cols {
  display: grid;
  grid-template-columns: 1.05fr 1fr 1.15fr;
  gap: 20px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--rule);
}

.wc-col-head {
  font-size: var(--t-11);
  color: var(--ink-4);
  letter-spacing: 0.06em;
  margin-bottom: 6px;
}

.tier-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: baseline;
  padding: 3px 0;
}

.tier-label {
  font-size: var(--t-12);
  color: var(--ink-2);
  white-space: nowrap;
}

.tier-price {
  font-size: var(--t-12);
  color: var(--ink);
  font-weight: 600;
  white-space: nowrap;
}

.tier-state {
  font-size: 10.5px;
  color: var(--ink-4);
  white-space: nowrap;
}

.tier-state.tier-hit {
  color: var(--accent);
  font-weight: 600;
}

.tier-state.tier-near {
  color: var(--warn);
}

.tier-state.tier-done {
  color: var(--accent);
}

.tier-state.tier-off,
.tier-state.tier-far,
.tier-state.tier-below {
  color: var(--ink-4);
}

.sell-row,
.mon-row {
  display: grid;
  gap: 8px;
  align-items: baseline;
  padding: 3px 0;
}

.sell-row {
  grid-template-columns: auto 1fr;
}

.mon-row {
  grid-template-columns: 1fr auto auto;
}

.sell-price,
.mon-value,
.mon-date {
  font-size: var(--t-12);
  color: var(--ink);
  white-space: nowrap;
}

.sell-price {
  font-weight: 600;
}

.sell-action {
  font-size: var(--t-11);
  color: var(--ink-3);
  line-height: 1.5;
}

.mon-metric {
  font-size: var(--t-12);
  color: var(--ink-2);
}

.mon-date {
  color: var(--ink-4);
}

.wc-hint {
  margin-top: 6px;
  font-size: var(--t-11);
  color: var(--ink-4);
  line-height: 1.6;
}

.wc-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--rule);
}

.wc-report {
  flex: 1;
  font-size: var(--t-11);
  color: var(--ink-4);
}

/* ---------- 关注档案抽屉 ---------- */
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  justify-content: flex-end;
  background: rgba(24, 26, 23, 0.28);
}

.drawer {
  display: flex;
  flex-direction: column;
  width: 620px;
  max-width: 100%;
  height: 100%;
  background: var(--surface);
  border-left: 1px solid var(--rule);
  box-shadow: -18px 0 40px rgba(24, 26, 23, 0.14);
  animation: drawer-in 180ms var(--ease);
}

@keyframes drawer-in {
  from {
    transform: translateX(24px);
    opacity: 0.6;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 12px;
}

.drawer-title {
  font-size: var(--t-15);
  font-weight: 650;
  color: var(--ink);
  letter-spacing: -0.005em;
}

.drawer-sub {
  margin-top: 4px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.drawer-close {
  border: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-3);
  width: 26px;
  height: 26px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.drawer-close:hover {
  border-color: var(--rule-strong);
  color: var(--ink);
}

.drawer-flags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 22px 12px;
}

.drawer-price {
  margin-left: auto;
  font-size: var(--t-15);
  font-weight: 600;
  color: var(--ink);
}

.drawer-price em {
  margin-left: 6px;
  font-style: normal;
  font-size: var(--t-12);
  font-weight: 400;
}

.drawer-tabs {
  display: flex;
  gap: 4px;
  padding: 0 22px;
  border-bottom: 1px solid var(--rule);
}

.drawer-tab {
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--ink-3);
  font-size: var(--t-12);
  padding: 8px 10px;
  cursor: pointer;
}

.drawer-tab:hover {
  color: var(--ink);
}

.drawer-tab.is-on {
  color: var(--ink);
  border-bottom-color: var(--ink);
  font-weight: 600;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px 28px;
}

.drawer-stance {
  font-size: var(--t-13);
  color: var(--ink-2);
  line-height: 1.7;
}

.drawer-thesis {
  margin-top: 6px;
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.7;
}

.kv-grid {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 6px 12px;
  margin-top: 14px;
  padding: 12px 14px;
  background: var(--surface-sunken);
  border-radius: var(--radius);
  font-size: var(--t-12);
}

.kv-k {
  color: var(--ink-4);
}

.kv-v {
  color: var(--ink);
}

.plain-list {
  margin: 10px 0 0;
  padding-left: 16px;
}

.plain-list li {
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.75;
}

.tier-block {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
}

.tier-block-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.tier-block-label {
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
}

.tier-block-price {
  font-size: var(--t-13);
  color: var(--ink);
}

.tier-block-head .tier-state {
  margin-left: auto;
}

.tier-block-meta {
  margin-top: 6px;
  font-size: var(--t-11);
  color: var(--ink-3);
  line-height: 1.7;
}

.tier-block-note {
  margin-top: 4px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.tier-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.tier-actions .mini-btn {
  margin-left: 0;
}

.drawer-section {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--rule);
}

.drawer-section-head {
  font-size: var(--t-11);
  color: var(--ink-4);
  letter-spacing: 0.06em;
  margin-bottom: 8px;
}

.target-row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--rule);
}

.target-row:last-child {
  border-bottom: 0;
}

.target-price {
  font-size: var(--t-12);
  font-weight: 600;
  color: var(--ink);
}

.target-valuation {
  font-size: var(--t-11);
  color: var(--ink-4);
  text-align: right;
}

.target-action {
  grid-column: 1 / -1;
  font-size: var(--t-12);
  color: var(--ink-3);
}

.stop-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 10px;
  padding: 5px 0;
}

.stop-label {
  font-size: var(--t-11);
  color: var(--ink-4);
}

.stop-text {
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.7;
}

.monitor-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
}

.monitor-item:last-child {
  border-bottom: 0;
}

.monitor-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.monitor-metric {
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
}

.monitor-value {
  font-size: var(--t-13);
  color: var(--ink);
}

.monitor-line {
  margin-top: 4px;
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.7;
}

.monitor-k {
  margin-right: 8px;
  color: var(--ink-4);
  font-size: var(--t-11);
}

.monitor-foot {
  display: flex;
  gap: 12px;
  margin-top: 5px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.dim-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--rule);
}

.dim-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.dim-name {
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
}

.dim-conf {
  font-size: var(--t-11);
  color: var(--ink-4);
}

.dim-text {
  margin-top: 4px;
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.7;
}

.memo-block {
  margin-top: 14px;
}

.memo-k {
  font-size: var(--t-11);
  color: var(--ink-4);
  letter-spacing: 0.06em;
}

.memo-v {
  margin-top: 4px;
  font-size: var(--t-12);
  color: var(--ink-2);
  line-height: 1.75;
}

.memo-oneline {
  margin-top: 18px;
  padding: 12px 14px;
  border-left: 2px solid var(--accent);
  background: var(--accent-wash);
  border-radius: 0 var(--radius-s) var(--radius-s) 0;
  font-size: var(--t-13);
  color: var(--ink-2);
  line-height: 1.7;
}

.timeline-row {
  display: flex;
  gap: 10px;
  padding: 5px 0;
  font-size: var(--t-12);
  color: var(--ink-3);
}

.timeline-at {
  flex: none;
  color: var(--ink-4);
  font-size: var(--t-11);
}

.timeline-text {
  line-height: 1.6;
}

.drawer-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 22px;
  border-top: 1px solid var(--rule);
  background: var(--surface);
}

.drawer-select,
.drawer-input {
  height: 30px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-s);
  background: var(--surface);
  color: var(--ink);
  font-size: var(--t-12);
  padding: 0 8px;
}

.drawer-select {
  flex: none;
  width: 104px;
}

.drawer-input {
  flex: 1;
  min-width: 0;
}

.primary-btn {
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--surface);
  font-size: var(--t-12);
  padding: 5px 14px;
  border-radius: var(--radius-s);
  cursor: pointer;
  white-space: nowrap;
}

.primary-btn:hover {
  opacity: 0.88;
}

.primary-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.drawer-link {
  display: inline-block;
  margin-top: 14px;
  text-decoration: none;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .wc-cols {
    grid-template-columns: 1fr;
    gap: 14px;
  }
}

/* ---------- 日历 ---------- */
.cal-sheet {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 0;
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-sheet);
  overflow: hidden;
}

.cal-main {
  padding: 18px 20px 20px;
  border-right: 1px solid var(--rule);
}

.cal-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.cal-month {
  font-size: var(--t-15);
  font-weight: 600;
  color: var(--ink);
}

.cal-nav {
  display: flex;
  gap: 6px;
}

.nav-btn {
  border: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--t-12);
  padding: 3px 10px;
  border-radius: var(--radius-s);
  cursor: pointer;
}

.nav-btn:hover {
  border-color: var(--rule-strong);
  color: var(--ink);
}

.cal-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding-bottom: 6px;
  border-bottom: 1px solid var(--rule);
}

.cal-week span {
  font-size: var(--t-11);
  color: var(--ink-4);
  text-align: center;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border-left: 1px solid var(--rule);
}

.cal-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 92px;
  padding: 7px 7px 6px;
  border: 0;
  border-right: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur-1) var(--ease);
}

.cal-cell:hover {
  background: var(--surface-sunken);
}

.cal-cell.is-out {
  color: var(--ink-4);
}

.cal-cell.is-out .cell-day {
  color: var(--ink-4);
}

.cal-cell.is-selected {
  background: var(--accent-wash);
  box-shadow: inset 0 0 0 1px var(--accent-line);
}

.cell-day {
  font-size: var(--t-12);
  color: var(--ink-3);
}

.cal-cell.is-today .cell-day {
  color: var(--surface);
  background: var(--ink);
  width: 20px;
  height: 20px;
  line-height: 20px;
  border-radius: 50%;
  text-align: center;
}

.cell-events {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-event {
  font-size: 10.5px;
  line-height: 1.4;
  padding: 1px 5px;
  border-radius: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: var(--surface-sunken);
  color: var(--ink-3);
  border-left: 2px solid var(--rule-strong);
}

.cell-event.imp-high {
  border-left-color: var(--danger);
  color: var(--danger);
  background: var(--danger-wash);
}

.cell-event.imp-medium {
  border-left-color: var(--warn);
  color: var(--warn);
}

.cell-more {
  font-size: 10px;
  color: var(--ink-4);
  padding-left: 5px;
}

.cal-side {
  padding: 18px 18px 20px;
  max-height: 720px;
  overflow-y: auto;
}

.side-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--rule);
}

.side-date {
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
}

.side-count {
  font-size: var(--t-11);
  color: var(--ink-4);
}

.event-list {
  padding-top: 4px;
}

.event {
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
}

.event:last-child {
  border-bottom: 0;
}

.event-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.imp-dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--rule-strong);
}

.imp-dot.imp-high {
  background: var(--danger);
}

.imp-dot.imp-medium {
  background: var(--warn);
}

.event-title {
  flex: 1;
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
  line-height: 1.45;
}

.event-imp {
  font-size: 10.5px;
  color: var(--ink-4);
  white-space: nowrap;
}

.event-cat {
  margin-top: 3px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.event-detail {
  margin-top: 5px;
  font-size: var(--t-12);
  color: var(--ink-3);
  line-height: 1.65;
}

.event-action {
  margin-top: 5px;
  font-size: var(--t-12);
  color: var(--accent);
  line-height: 1.6;
}

.event-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.event-status {
  border: 1px solid var(--rule);
  border-radius: 999px;
  padding: 1px 7px;
}

.event-status.st-pending {
  color: var(--ink-3);
}

.event-status.st-done {
  color: var(--accent);
  border-color: var(--accent-line);
  background: var(--accent-wash);
}

.event-status.st-skipped {
  color: var(--ink-4);
}

.mini-btn {
  margin-left: auto;
  border: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-3);
  font-size: 10.5px;
  padding: 2px 8px;
  border-radius: 999px;
  cursor: pointer;
}

.mini-btn:hover {
  border-color: var(--rule-strong);
  color: var(--ink);
}

.side-empty {
  padding: 24px 0;
}

.month-list {
  margin-top: 16px;
  border-top: 1px solid var(--rule);
  padding-top: 10px;
}

.month-list-head {
  font-size: var(--t-11);
  color: var(--ink-4);
  margin-bottom: 4px;
}

.month-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 5px 0;
  cursor: pointer;
  text-align: left;
  font-size: var(--t-11);
  color: var(--ink-3);
}

.month-row:hover .mr-title {
  color: var(--ink);
}

.mr-title {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 通用 ---------- */
.empty {
  padding: 22px 20px;
  font-size: var(--t-13);
  color: var(--ink-3);
  line-height: 1.75;
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
}

.side-empty {
  background: transparent;
  border: 0;
  padding: 20px 0;
}

.load-error {
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

.is-up {
  color: var(--up);
}

.is-down {
  color: var(--down);
}

.is-danger {
  color: var(--danger);
}

@media (max-width: 1100px) {
  .cal-sheet {
    grid-template-columns: 1fr;
  }
  .cal-main {
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
  .cal-side {
    max-height: none;
  }
}
</style>
