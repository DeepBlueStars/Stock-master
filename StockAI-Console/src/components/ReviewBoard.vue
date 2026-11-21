<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import dayjs from 'dayjs'
import config from '@/config/config'

const BASE_URL = config.baseUrl
const USER_ID = '6928fca14f206ada1b7a9269'

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

const overview = ref(null)
const levels = ref([])
const events = ref([])
const loading = ref(true)
const error = ref('')

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
  if (!current || Number.isNaN(target)) return { cls: 'far', text: '无现价' }
  const dir = directionOf(level.code)
  const gap = ((current - target) / target) * 100
  if (dir === 'none') {
    return { cls: 'far', text: `参考 ${gap >= 0 ? '+' : ''}${gap.toFixed(1)}%` }
  }
  if (dir === 'up') {
    if (current >= target) return { cls: 'hit-up', text: `已到价 +${gap.toFixed(1)}%` }
    if (gap > -3) return { cls: 'near', text: `距 ${Math.abs(gap).toFixed(1)}%` }
    return { cls: 'far', text: `还差 ${Math.abs(gap).toFixed(1)}%` }
  }
  if (current <= target) return { cls: 'hit-down', text: `已跌破 ${Math.abs(gap).toFixed(1)}%` }
  if (gap < 3) return { cls: 'near', text: `距 ${gap.toFixed(1)}%` }
  return { cls: 'far', text: `还需 ${gap.toFixed(1)}%` }
}

function daysTo(date) {
  if (!date) return null
  return dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day')
}

function timeState(time) {
  const left = daysTo(time.date)
  if (left === null) return { cls: 'far', text: '—' }
  if (left < 0) return { cls: 'hit-down', text: `已过 ${Math.abs(left)} 天` }
  if (left === 0) return { cls: 'near', text: '今天' }
  if (left <= 14) return { cls: 'near', text: `${left} 天后` }
  return { cls: 'far', text: `${left} 天后` }
}

const dueCount = computed(() => {
  let count = 0
  cards.value.forEach((card) => {
    card.levels.forEach((level) => {
      const state = evaluateLevel(level, card.price)
      if (state.cls === 'hit-up' || state.cls === 'hit-down') count += 1
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

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadOverview(), loadLevels(), loadEvents()])
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

onMounted(loadAll)
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
              <span v-if="card.price !== null" class="rung-state" :class="evaluateLevel(level, card.price).cls">
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

.rung-state.hit-up {
  border-color: var(--accent-line);
  background: var(--accent-wash);
  color: var(--accent);
}

.rung-state.hit-down {
  border-color: var(--danger);
  background: var(--danger-wash);
  color: var(--danger);
}

.rung-state.near {
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

.time-state.near {
  color: var(--warn);
}

.time-state.hit-down {
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
