<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import config from '../config/config'
import CountUp from './CountUp.vue'
import WeeklySummaryBlock from './TradePlan/WeeklySummaryBlock.vue'

/**
 * 决策台：今日决策（卖出纪律）与交易计划（买入阶梯）合并为一页。
 *
 * 卖出侧来自 GET /exit/overview，买入侧来自 POST /trade-plan/list。
 * 两侧共用同一份持仓与现金口径：判定只读，动作回写只发生在信号确认与批次打勾。
 */
const USER_ID = '6928fca14f206ada1b7a9269'
const BASE_URL = config.baseUrl

const loading = ref(true)
const loadError = ref('')
const planError = ref('')
const tradeDate = ref('')
const evaluatedAt = ref('')
const runStats = ref(null)
const handledToday = ref(0)
const holdings = ref([])
const signals = ref([])
const plans = ref([])
const regime = ref('')
const pendingBatchKey = ref('')
const refOpen = ref(false)
const barsReady = ref(false)

/* ---------- 数值 ---------- */
const n = (v) => {
  const num = Number(v)
  return Number.isFinite(num) ? num : 0
}
const decOf = (v) => (Math.abs(Number(v)) < 10 ? 3 : 2)
const priceText = (v) => {
  const num = Number(v)
  return Number.isFinite(num) ? num.toFixed(decOf(num)) : '—'
}
const pctText = (v) => {
  const num = Number(v)
  if (!Number.isFinite(num)) return '—'
  return `${num >= 0 ? '+' : '−'}${Math.abs(num * 100).toFixed(1)}%`
}
const numText = (v, digits = 0) => {
  const num = Number(v)
  if (!Number.isFinite(num)) return '—'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}
const dist = (target, price) => {
  const a = Number(target)
  const b = Number(price)
  return Number.isFinite(a) && Number.isFinite(b) && b ? a / b - 1 : NaN
}
const isNearDist = (d) => Number.isFinite(d) && Math.abs(d) < 0.1
const fillOf = (d) => {
  if (!Number.isFinite(d)) return 0
  return Math.max(3, Math.min(100, (Math.abs(d) / 0.30) * 100))
}

/* ---------- 载入 ---------- */
const load = async () => {
  loading.value = true
  loadError.value = ''
  planError.value = ''
  barsReady.value = false

  const [overviewRes, planRes, policyRes] = await Promise.allSettled([
    axios.get(`${BASE_URL}/exit/overview`, { params: { userId: USER_ID } }),
    axios.post(`${BASE_URL}/trade-plan/list`, { userId: USER_ID }),
    axios.post(`${BASE_URL}/policy/summary`, { userId: USER_ID })
  ])

  if (overviewRes.status === 'fulfilled' && overviewRes.value.data.state === 0) {
    const data = overviewRes.value.data.data || {}
    tradeDate.value = data.tradeDate || ''
    evaluatedAt.value = data.evaluatedAt || ''
    runStats.value = data.evaluated == null ? null : data
    handledToday.value = data.handledToday || 0
    holdings.value = (data.holdings || []).map((h) => ({ ...h }))
    signals.value = (data.signals || []).map((s) => ({ ...s, state: 'idle', live: '' }))
  } else {
    const message = overviewRes.status === 'fulfilled'
      ? overviewRes.value.data.message
      : overviewRes.reason?.message
    loadError.value = message || '接口异常'
    holdings.value = []
    signals.value = []
  }

  if (planRes.status === 'fulfilled' && planRes.value.data.state === 0) {
    plans.value = planRes.value.data.data || []
  } else {
    const message = planRes.status === 'fulfilled'
      ? planRes.value.data.message
      : planRes.reason?.message
    planError.value = message || '接口异常'
    plans.value = []
  }

  if (policyRes.status === 'fulfilled' && policyRes.value.data.state === 0) {
    regime.value = policyRes.value.data.data.effectiveRegime || ''
  }

  loading.value = false
  await nextTick()
  barsReady.value = true
}

onMounted(load)

/* ---------- 卖出侧 ---------- */
const handled = (sig) => sig.state !== 'idle'
const pendingSell = computed(() => signals.value.filter((s) => s.state === 'idle').length)

const sortedHoldings = computed(() =>
  holdings.value
    .map((h) => {
      const stopDist = dist(h.stop, h.price)
      return {
        ...h,
        stopDist,
        floatDist: dist(h.price, h.cost),
        near: isNearDist(stopDist),
        fill: fillOf(stopDist)
      }
    })
    .sort((a, b) => {
      const da = Number.isFinite(a.stopDist) ? Math.abs(a.stopDist) : Infinity
      const db = Number.isFinite(b.stopDist) ? Math.abs(b.stopDist) : Infinity
      return da - db
    })
)

const signalClass = (sig) =>
  sig.state === 'done' ? 'is-done' : sig.state === 'snoozed' ? 'is-snoozed' : ''

function signalStopGap(sig) {
  const holding = holdings.value.find((h) => h.symbol === sig.symbol)
  return holding ? pctText(dist(holding.stop, holding.price)) : '—'
}

async function ackSignal(sig, kind) {
  if (handled(sig)) return
  if (kind === 'snooze') {
    sig.state = 'snoozed'
    sig.live = '已搁置，明天再提'
    return
  }
  sig.state = 'done'
  sig.live = '已确认执行'
  try {
    const response = await axios.post(
      `${BASE_URL}/exit/signals/${sig.id}/ack?userId=${encodeURIComponent(USER_ID)}`,
      { note: '已执行' }
    )
    if (response.data.state !== 0) {
      throw new Error(response.data.message || '确认失败')
    }
    handledToday.value += 1
  } catch (error) {
    console.error('确认信号失败:', error)
    sig.state = 'idle'
    sig.live = '确认失败，请重试'
    ElMessage.error('确认失败，请稍后重试')
  }
}

/* ---------- 买入侧 ---------- */
const cost = computed(() =>
  plans.value.reduce((sum, p) => sum + n(p.quantity) * n(p.avgCost), 0)
)
const marketValue = computed(() =>
  plans.value.reduce((sum, p) => sum + n(p.quantity) * n(p.lastClose), 0)
)
const floatPnl = computed(() => marketValue.value - cost.value)
const floatPct = computed(() => (cost.value > 0 ? floatPnl.value / cost.value : 0))
const availableCash = computed(() => {
  const plan = plans.value.find((p) => p.availableCash != null)
  return plan ? n(plan.availableCash) : null
})

const planRows = computed(() =>
  plans.value.map((plan) => {
    const batches = Array.isArray(plan.buyBatches) ? plan.buyBatches : []
    const bought = batches.filter((b) => b.bought).length
    return {
      ...plan,
      batches,
      bought,
      total: batches.length,
      progress: batches.length ? (bought / batches.length) * 100 : 0
    }
  })
)

const nearBuyCount = computed(() =>
  planRows.value.reduce(
    (sum, plan) =>
      sum + plan.batches.filter((b) => !b.bought && Math.abs(dist(b.price, plan.lastClose)) < 0.03).length,
    0
  )
)

async function toggleBatch(plan, index) {
  const batch = plan.batches[index]
  const key = `${plan.symbol}:${index}`
  if (pendingBatchKey.value === key) return
  const next = !batch.bought
  const before = { bought: batch.bought, boughtAt: batch.boughtAt }
  batch.bought = next
  pendingBatchKey.value = key
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/batch/state`, {
      userId: USER_ID,
      symbol: plan.symbol,
      index,
      bought: next
    })
    if (response.data.state !== 0) {
      throw new Error(response.data.message || '标记失败')
    }
    const fresh = response.data.data
    if (fresh && Array.isArray(fresh.buyBatches)) {
      const target = plans.value.find((p) => p.symbol === plan.symbol)
      if (target) target.buyBatches = fresh.buyBatches
    }
  } catch (error) {
    console.error('标记失败', error)
    batch.bought = before.bought
    batch.boughtAt = before.boughtAt
    ElMessage.error('标记失败，请稍后重试')
  } finally {
    pendingBatchKey.value = ''
  }
}

function modeLabel(plan) {
  return plan.buyMode === 'LUMP_SUM' ? '一次性买入' : '分批买入'
}

function addPositionText(plan) {
  const rule = plan.addPositionRule
  if (!rule) return '没有补仓规则，按计划批次执行即可。'
  if (rule.conditionText) return rule.conditionText
  if (rule.triggerPrice != null) {
    return `跌破 ${priceText(rule.triggerPrice)} 补 ${numText(n(rule.ratio) * 100)}%。`
  }
  return '没有补仓规则，按计划批次执行即可。'
}

function batchGap(plan, batch) {
  return dist(batch.price, plan.lastClose)
}

function distanceText(plan, batch) {
  if (batch.bought) return '已成交'
  return pctText(batchGap(plan, batch))
}

/* ---------- 结论 ---------- */
const totalActions = computed(() => pendingSell.value + nearBuyCount.value)
const calm = computed(
  () => !loading.value && !loadError.value && !planError.value && totalActions.value === 0
)

const stampText = computed(() => {
  if (loading.value) return '读取中'
  if (loadError.value || planError.value) return '待重试'
  return calm.value ? '无需操作' : '需处理'
})

const headline = computed(() => {
  if (loading.value) return '正在读取今天的判定与计划'
  if (loadError.value) return '读不到判定结果'
  if (planError.value) return '读不到交易计划'
  if (calm.value) return '今天不用动'
  const sell = pendingSell.value
  const buy = nearBuyCount.value
  if (sell && buy) return `卖出 ${sell} 件、买入 ${buy} 件，今天 ${sell + buy} 件事`
  if (sell) return `今天有 ${sell} 件卖出需要处理`
  return `今天有 ${buy} 个买入档位近触发`
})

const detail = computed(() => {
  if (loading.value) return '稍等一下，正在从服务端取信号、持仓与计划。'
  if (loadError.value) return `原因：${loadError.value}。刷新页面可以重试。`
  if (planError.value) return `交易计划读取失败：${planError.value}。刷新页面可以重试。`
  if (!holdings.value.length && !plans.value.length) {
    return '还没有持仓或交易计划，先去录入再来看这本账。'
  }
  if (calm.value) {
    const done = handledToday.value ? `已处理 ${handledToday.value} 条 · ` : ''
    return `${done}卖出信号都处理完了，买入档位也都离得远。`
  }
  const parts = []
  if (pendingSell.value) parts.push(`${pendingSell.value} 条卖出信号已触发`)
  if (nearBuyCount.value) parts.push(`${nearBuyCount.value} 个买入档位距现价不到 3%`)
  return `${parts.join('，')}；其余持仓都在纪律区间内。`
})

/* ---------- 页头 / 参考 ---------- */
const tradeDateLabel = computed(() => (tradeDate.value ? tradeDate.value.slice(5) : '—'))
const regimeLabel = computed(
  () => ({ BULL: '牛市', SIDEWAYS: '震荡', BEAR: '熊市', RISK: '风险规避' })[regime.value] || '—'
)
const cashLabel = computed(() =>
  availableCash.value == null ? '—' : `¥${numText(availableCash.value)}`
)
const evaluatedLabel = computed(() => {
  if (!evaluatedAt.value) return '—'
  const d = new Date(evaluatedAt.value)
  if (Number.isNaN(d.getTime())) return evaluatedAt.value
  const p = (x) => String(x).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
})
const runStatsText = computed(() => {
  if (!runStats.value) return '—'
  return `评估 ${runStats.value.evaluated} · 触发 ${runStats.value.triggered} · 跳过 ${runStats.value.skipped}`
})

const statusText = { ok: '正常', warn: '警戒', tp1: '止盈一档已触发', nodata: '数据未更新' }
const statusShort = { ok: '正常', warn: '警戒', tp1: '已触发', nodata: '无数据' }
const statusClass = { ok: 'is-ok', warn: 'is-warn', tp1: 'is-tp1', nodata: 'is-ok' }
</script>

<template>
  <div class="page desk">
    <header class="page-head">
      <div>
        <h1 class="page-title">决策台</h1>
        <p class="page-sub">卖出守纪律，买入按阶梯；同一本账，两页对着读。</p>
      </div>
      <div class="page-meta">
        <div class="meta">
          <span class="k">数据截至</span>
          <span class="v num">{{ tradeDateLabel }} 收盘</span>
        </div>
        <div class="meta">
          <span class="k">市场</span>
          <span class="v">{{ regimeLabel }}</span>
        </div>
        <div class="meta">
          <span class="k">可用现金</span>
          <span class="v num">{{ cashLabel }}</span>
        </div>
      </div>
    </header>

    <section class="verdict" :class="{ 'is-calm': calm }">
      <span class="stamp">{{ stampText }}</span>
      <div class="verdict-copy">
        <p class="verdict-text">{{ headline }}</p>
        <p class="verdict-sub">{{ detail }}</p>
      </div>
      <div class="verdict-split">
        <div class="vstat">
          <span class="k">卖出</span>
          <CountUp class="v" :value="pendingSell" />
        </div>
        <div class="vstat">
          <span class="k">买入</span>
          <CountUp class="v" :value="nearBuyCount" />
        </div>
        <div class="vstat">
          <span class="k">浮动</span>
          <CountUp
            class="v"
            :class="floatPct >= 0 ? 'delta-up' : 'delta-down'"
            :value="n(floatPct) * 100"
            :decimals="1"
            sign
            suffix="%"
          />
        </div>
      </div>
    </section>

    <div class="duo">
      <!-- 卖出侧 -->
      <section class="col">
        <div class="col-head">
          <h2 class="col-title sell"><span class="dot" aria-hidden="true"></span>卖出 · 守纪律</h2>
          <span class="col-note">已有持仓</span>
          <span class="col-stat num">{{ pendingSell }} 信号 / {{ holdings.length }} 只</span>
        </div>

        <section class="sheet">
          <div class="pane-head">
            <h2 class="pane-title"><span class="idx">01</span>需要处理</h2>
            <span class="pane-note">只列命中阈值的</span>
          </div>

          <p v-if="loading" class="empty">正在读取今天的判定结果…</p>
          <p v-else-if="loadError" class="empty is-error">
            读不到判定结果：{{ loadError }}。刷新页面可以重试。
          </p>
          <p v-else-if="!signals.length" class="empty">没有需要处理的信号，持仓都在纪律区间内。</p>
          <template v-else>
            <article
              v-for="sig in signals"
              :key="sig.id"
              class="signal"
              :class="signalClass(sig)"
            >
              <div class="signal-top">
                <span class="tag" :class="sig.tone === 'up' ? 'is-up' : 'is-down'">{{ sig.tag }}</span>
                <span class="signal-name">
                  {{ sig.name }}<span class="code num">{{ sig.symbol }}</span>
                </span>
                <CountUp
                  class="signal-pct"
                  :class="sig.tone === 'down' ? 'delta-down' : 'delta-up'"
                  :value="n(sig.returnPct) * 100"
                  :decimals="1"
                  sign
                  suffix="%"
                />
              </div>
              <p class="signal-quote">
                触发 <span class="num">{{ priceText(sig.triggerPrice) }}</span>
                <span class="sep">·</span>
                现价 <span class="num">{{ priceText(sig.close) }}</span>
                <span class="sep">·</span>
                止损余量 <span class="num">{{ signalStopGap(sig) }}</span>
              </p>
              <p class="signal-do">{{ sig.action }}</p>
              <div class="signal-foot">
                <span class="signal-fail">失效 · {{ sig.invalidate }}</span>
                <div class="signal-actions">
                  <button
                    class="mini"
                    type="button"
                    :class="{ 'is-snoozed': sig.state === 'snoozed' }"
                    :disabled="handled(sig)"
                    @click="ackSignal(sig, 'snooze')"
                  >
                    {{ sig.state === 'snoozed' ? '已搁置' : '搁置' }}
                  </button>
                  <button
                    class="mini primary"
                    type="button"
                    :class="{ 'is-done': sig.state === 'done' }"
                    :disabled="handled(sig)"
                    @click="ackSignal(sig, 'done')"
                  >
                    {{ sig.state === 'done' ? '已执行' : '确认执行' }}
                  </button>
                </div>
              </div>
              <span class="sr-only" role="status" aria-live="polite">{{ sig.live }}</span>
            </article>
          </template>
        </section>

        <section class="sheet">
          <div class="pane-head">
            <h2 class="pane-title"><span class="idx">02</span>持仓纪律</h2>
            <span class="pane-note">按距止损从近到远</span>
          </div>

          <p v-if="loading" class="empty">正在读取持仓与阈值…</p>
          <p v-else-if="loadError" class="empty is-error">读不到持仓：{{ loadError }}。</p>
          <p v-else-if="!sortedHoldings.length" class="empty">还没有持仓，先去录入。</p>
          <table v-else class="ledger">
            <colgroup>
              <col style="width: 28%" />
              <col style="width: 16%" />
              <col style="width: 18%" />
              <col style="width: 24%" />
              <col style="width: 14%" />
            </colgroup>
            <thead>
              <tr>
                <th class="c-name">标的</th>
                <th>现价</th>
                <th>浮动</th>
                <th>止损余量</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in sortedHoldings" :key="h.symbol">
                <td class="c-name">
                  {{ h.name }}<span class="code num">{{ h.symbol }}</span>
                </td>
                <td class="c-num" data-label="现价">
                  <CountUp class="price" :value="n(h.price)" :decimals="decOf(h.price)" />
                </td>
                <td class="c-num" data-label="浮动">
                  <CountUp
                    :class="n(h.floatDist) >= 0 ? 'delta-up' : 'delta-down'"
                    :value="n(h.floatDist) * 100"
                    :decimals="1"
                    sign
                    suffix="%"
                  />
                </td>
                <td class="c-num stop" data-label="止损余量">
                  <span class="num" :class="{ near: h.near }">{{ pctText(h.stopDist) }}</span>
                  <span class="meter" :class="{ 'is-near': h.near }" aria-hidden="true">
                    <i :style="{ width: barsReady ? h.fill + '%' : '0%' }"></i>
                  </span>
                </td>
                <td class="c-num" data-label="状态">
                  <span class="tag" :class="statusClass[h.status] || 'is-ok'">
                    {{ statusShort[h.status] || '—' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </section>

      <div class="spine" aria-hidden="true"></div>

      <!-- 买入侧 -->
      <section class="col">
        <div class="col-head">
          <h2 class="col-title buy"><span class="dot" aria-hidden="true"></span>买入 · 按阶梯</h2>
          <span class="col-note">计划与现金</span>
          <span class="col-stat num">{{ planRows.length }} 计划 / {{ cashLabel }}</span>
        </div>

        <div class="cash">
          <div>
            <span class="k">已投入</span>
            <CountUp class="v" :value="n(cost)" prefix="¥" />
            <span class="s">{{ planRows.length }} 只持仓</span>
          </div>
          <div>
            <span class="k">浮动</span>
            <CountUp
              class="v"
              :class="floatPnl >= 0 ? 'delta-up' : 'delta-down'"
              :value="n(floatPnl)"
              prefix="¥"
              sign
            />
            <CountUp
              class="s"
              :class="floatPnl >= 0 ? 'delta-up' : 'delta-down'"
              :value="n(floatPct) * 100"
              :decimals="1"
              sign
              suffix="%"
            />
          </div>
          <div>
            <span class="k">可用现金</span>
            <CountUp v-if="availableCash != null" class="v" :value="availableCash" prefix="¥" />
            <span v-else class="v num">—</span>
            <span class="s">按批次分配</span>
          </div>
        </div>

        <section class="sheet">
          <div class="pane-head">
            <h2 class="pane-title"><span class="idx">03</span>买入计划</h2>
            <span class="pane-note">
              {{ planRows.length ? `${planRows.reduce((sum, p) => sum + p.bought, 0)}/${planRows.reduce((sum, p) => sum + p.total, 0)} 批次已买入` : '买入后自动生成' }}
            </span>
          </div>

          <p v-if="loading" class="empty">正在加载…</p>
          <p v-else-if="planError" class="empty is-error">读不到交易计划：{{ planError }}。</p>
          <p v-else-if="!planRows.length" class="empty">还没有计划 —— 买入股票后会自动生成。</p>
          <div v-else class="plans">
            <article v-for="plan in planRows" :key="plan.symbol" class="plan">
              <div class="plan-head">
                <span class="plan-name">{{ plan.stockName || plan.symbol }}</span>
                <span class="plan-code num">{{ plan.symbol }}</span>
                <span class="plan-mode">{{ modeLabel(plan) }}</span>
                <span class="plan-prog" :class="{ 'is-full': plan.bought === plan.total }">
                  {{ plan.bought }}/{{ plan.total }} 已买入
                </span>
                <span v-if="plan.degraded" class="plan-warn" :title="plan.degradedReason || 'K 线不足，指标降级'">
                  数据降级
                </span>
                <div class="plan-right">
                  <CountUp class="plan-price" :value="n(plan.lastClose)" :decimals="decOf(plan.lastClose)" />
                  <CountUp
                    class="plan-pct"
                    :class="n(plan.floatPnlRate) >= 0 ? 'delta-up' : 'delta-down'"
                    :value="n(plan.floatPnlRate) * 100"
                    :decimals="1"
                    sign
                    suffix="%"
                  />
                </div>
              </div>

              <div class="plan-bar" aria-hidden="true">
                <i :style="{ width: barsReady ? plan.progress + '%' : '0%' }"></i>
              </div>

              <p v-if="!plan.batches.length" class="plan-empty">
                历史 K 线不足，暂时算不出分批价；等数据补齐会自动重算。
              </p>
              <table v-else class="ladder">
                <colgroup>
                  <col style="width: 26%" />
                  <col style="width: 26%" />
                  <col style="width: 34%" />
                  <col style="width: 14%" />
                </colgroup>
                <thead>
                  <tr><th>批次</th><th>触发价</th><th>距现价</th><th>已买入</th></tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(batch, index) in plan.batches"
                    :key="index"
                    :class="{ 'is-done': batch.bought }"
                  >
                    <td>{{ plan.batches.length > 1 ? `第 ${index + 1} 批` : '一次性' }}</td>
                    <td data-label="触发价">
                      <CountUp class="px" :value="n(batch.price)" :decimals="decOf(batch.price)" />
                    </td>
                    <td data-label="距现价" class="num">
                      <span :class="batch.bought ? '' : batchGap(plan, batch) >= 0 ? 'delta-up' : 'delta-down'">
                        {{ distanceText(plan, batch) }}
                      </span>
                    </td>
                    <td data-label="已买入" class="c-chk">
                      <button
                        class="chk"
                        type="button"
                        :aria-pressed="batch.bought ? 'true' : 'false'"
                        :disabled="pendingBatchKey === `${plan.symbol}:${index}`"
                        :aria-label="`标记${plan.stockName || plan.symbol}第${index + 1}批已买入`"
                        @click="toggleBatch(plan, index)"
                      >
                        <span></span>
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>

              <p class="plan-note"><span class="k">补仓</span>{{ addPositionText(plan) }}</p>
            </article>
          </div>
        </section>
      </section>
    </div>

    <!-- 参考信息 -->
    <section class="sheet ref">
      <button class="ref-head" type="button" :aria-expanded="refOpen" @click="refOpen = !refOpen">
        <span class="pane-title"><span class="idx">04</span>参考信息</span>
        <span class="pane-note">市场状态、上次判定统计 —— 判定时已计入，这里只是背景</span>
        <el-icon class="ref-chev" :class="{ 'is-open': refOpen }"><ArrowDown /></el-icon>
      </button>
      <div class="ref-body" :class="{ 'is-open': refOpen }">
        <div class="ref-cell"><span class="k">市场状态</span><span class="v">{{ regimeLabel }}</span></div>
        <div class="ref-cell"><span class="k">上次判定</span><span class="v num">{{ evaluatedLabel }}</span></div>
        <div class="ref-cell"><span class="k">上次统计</span><span class="v num">{{ runStatsText }}</span></div>
        <div class="ref-cell"><span class="k">下次判定</span><span class="v">工作日 17:30</span></div>
      </div>
    </section>

    <WeeklySummaryBlock :user-id="USER_ID" />

    <p class="foot-note">
      止损价、止盈线由卖出纪律引擎算出；判定每个交易日 17:30 跑一次（等日线落库之后）。
      买入与卖出共用同一份持仓与现金口径，任意一侧确认后另一侧同步收口。
    </p>
  </div>
</template>

<style scoped>
.desk {
  max-width: var(--page-max);
  margin-inline: auto;
  padding: var(--s-6) var(--s-6) var(--s-7);
  color: var(--ink);
}

/* ---------- 页头 ---------- */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--s-5);
  margin-bottom: 18px;
}

.page-title {
  margin: 0;
  font-size: var(--t-21);
  font-weight: 600;
  letter-spacing: -0.015em;
}

.page-sub {
  margin: 6px 0 0;
  font-size: var(--t-13);
  color: var(--ink-3);
}

.page-meta {
  display: flex;
  gap: 22px;
  text-align: right;
}

.meta .k {
  display: block;
  font-size: var(--t-11);
  letter-spacing: 0.06em;
  color: var(--ink-3);
}

.meta .v {
  display: block;
  margin-top: 2px;
  font-size: var(--t-13);
}

/* ---------- 结论条 ---------- */
.verdict {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--s-5);
  padding: 20px 24px;
  margin-bottom: 16px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  background: linear-gradient(90deg, var(--warn-wash), var(--surface) 44%);
}

.verdict.is-calm {
  background: linear-gradient(90deg, var(--accent-wash), var(--surface) 44%);
}

.stamp {
  flex: none;
  padding: 5px 10px;
  border: 1px solid var(--warn);
  border-radius: 3px;
  color: var(--warn);
  background: color-mix(in srgb, var(--surface) 55%, transparent);
  font-size: var(--t-11);
  font-weight: 600;
  letter-spacing: 0.14em;
  transform: rotate(-2deg);
  animation: stampIn 480ms var(--ease) both;
}

.verdict.is-calm .stamp {
  border-color: var(--accent);
  color: var(--accent-ink);
}

@keyframes stampIn {
  from {
    transform: rotate(-6deg) scale(1.14);
    opacity: 0;
  }
  60% {
    transform: rotate(0.4deg) scale(0.99);
    opacity: 1;
  }
  to {
    transform: rotate(-2deg) scale(1);
  }
}

.verdict-copy {
  flex: 1;
  min-width: 0;
}

.verdict-text {
  margin: 0;
  font-size: var(--t-17);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.verdict-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ink-2);
}

.verdict-split {
  display: flex;
  margin-left: auto;
}

.vstat {
  padding: 0 18px;
  border-left: 1px solid var(--rule);
}

.vstat:first-child {
  border-left: 0;
}

.vstat .k {
  display: block;
  font-size: var(--t-11);
  letter-spacing: 0.06em;
  color: var(--ink-3);
}

.vstat .v {
  display: block;
  margin-top: 4px;
  font-size: var(--t-17);
  font-weight: 500;
  letter-spacing: -0.01em;
}

/* ---------- 双栏 ---------- */
.duo {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 1px minmax(0, 1fr);
  gap: 26px;
  align-items: start;
  margin-bottom: 16px;
}

.col {
  min-width: 0;
}

.spine {
  align-self: stretch;
  min-height: 240px;
  border-left: 1px dashed var(--rule-strong);
}

.col-head {
  display: flex;
  align-items: baseline;
  gap: 9px;
  margin: 2px 2px 12px;
}

.col-title {
  margin: 0;
  display: inline-flex;
  align-items: center;
  font-size: var(--t-15);
  font-weight: 600;
  letter-spacing: 0.01em;
}

.dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 7px;
  border-radius: 50%;
  vertical-align: 2px;
}

.col-title.sell .dot {
  background: var(--danger);
}

.col-title.buy .dot {
  background: var(--accent);
}

.col-note {
  font-size: var(--t-11);
  color: var(--ink-3);
}

.col-stat {
  margin-left: auto;
  font-size: var(--t-11);
  color: var(--ink-2);
}

/* ---------- 纸 ---------- */
.sheet {
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-sheet);
  margin-bottom: 14px;
  overflow: hidden;
}

.pane-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 15px 20px 12px;
}

.pane-title {
  margin: 0;
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--t-15);
  font-weight: 600;
  letter-spacing: 0.01em;
}

.idx {
  font-family: var(--font-num);
  font-size: 10px;
  font-weight: 500;
  color: var(--ink-4);
  letter-spacing: 0.1em;
}

.pane-note {
  font-size: var(--t-11);
  color: var(--ink-3);
}

.empty {
  margin: 0;
  padding: 4px 20px 22px;
  font-size: var(--t-13);
  color: var(--ink-3);
}

.empty.is-error {
  color: var(--danger);
}

/* ---------- 信号卡 ---------- */
.signal {
  padding: 15px 20px 16px;
  border-top: 1px solid var(--rule);
  transition: background-color var(--dur-1) var(--ease);
}

.signal:hover {
  background: color-mix(in srgb, var(--ink) 2%, transparent);
}

.signal.is-done .signal-top,
.signal.is-done .signal-quote,
.signal.is-done .signal-do,
.signal.is-snoozed .signal-top,
.signal.is-snoozed .signal-quote,
.signal.is-snoozed .signal-do {
  opacity: 0.42;
  transition: opacity var(--dur-2) var(--ease);
}

.signal-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.signal-name {
  font-size: var(--t-13);
  font-weight: 600;
}

.signal-name .code {
  margin-left: 7px;
}

.signal-pct {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
}

.signal-quote {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--ink-3);
}

.signal-quote .sep {
  margin: 0 4px;
}

.signal-do {
  margin: 7px 0 0;
  font-size: var(--t-15);
  font-weight: 600;
}

.signal-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
}

.signal-fail {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--ink-3);
}

.signal-actions {
  display: flex;
  gap: 6px;
  flex: none;
}

/* ---------- 小按钮 ---------- */
.mini {
  height: 26px;
  padding: 0 11px;
  border: 1px solid var(--rule-strong);
  border-radius: 4px;
  background: var(--surface);
  color: var(--ink-2);
  font-size: 11px;
  cursor: pointer;
  transition: background-color 180ms var(--ease), border-color 180ms var(--ease),
    color 180ms var(--ease), transform 140ms var(--ease);
}

.mini:hover {
  border-color: var(--ink-4);
  color: var(--ink);
}

.mini:active {
  transform: scale(0.97);
}

.mini.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.mini.primary:hover {
  background: var(--accent-ink);
  border-color: var(--accent-ink);
}

.mini.is-done {
  background: var(--accent-wash);
  border-color: var(--accent-line);
  color: var(--accent-ink);
  pointer-events: none;
}

.mini.is-snoozed {
  background: var(--surface-sunken);
  color: var(--ink-3);
  pointer-events: none;
}

.mini:disabled {
  cursor: default;
}

:global(.dark) .mini.primary {
  color: #101315;
}

/* ---------- 标签 ---------- */
.tag {
  display: inline-block;
  padding: 1px 7px;
  border: 1px solid var(--rule-strong);
  border-radius: 4px;
  background: var(--surface-sunken);
  color: var(--ink-3);
  font-size: var(--t-11);
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.tag.is-up {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-wash);
}

.tag.is-down {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-wash);
}

.tag.is-warn {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-wash);
}

.tag.is-tp1 {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-wash);
}

.tag.is-ok {
  border-color: var(--rule-strong);
  color: var(--ink-3);
  background: var(--surface-sunken);
}

.delta-up {
  color: var(--up);
}

.delta-down {
  color: var(--down);
}

/* ---------- 账表 ---------- */
.ledger {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: var(--t-13);
}

.ledger th {
  padding: 0 20px 7px;
  border-bottom: 1px solid var(--rule-strong);
  font-size: var(--t-11);
  font-weight: 400;
  letter-spacing: 0.06em;
  color: var(--ink-3);
  text-align: right;
}

.ledger th.c-name {
  text-align: left;
}

.ledger td {
  padding: 11px 20px;
  border-bottom: 1px solid var(--rule);
  text-align: right;
  vertical-align: top;
}

.ledger tbody tr:last-child td {
  border-bottom: 0;
}

.ledger td.c-name {
  text-align: left;
  font-weight: 500;
}

.ledger td.c-name .code {
  display: block;
  margin-top: 2px;
}

.ledger .price {
  font-size: var(--t-13);
  font-weight: 500;
  letter-spacing: -0.01em;
}

.ledger .stop .near {
  color: var(--danger);
}

.meter {
  display: block;
  width: 56px;
  height: 2px;
  margin: 5px 0 0 auto;
  border-radius: 2px;
  background: var(--rule);
  overflow: hidden;
}

.meter i {
  display: block;
  height: 100%;
  width: 0;
  border-radius: 2px;
  background: var(--ink-3);
  transition: width 900ms var(--ease);
}

.meter.is-near i {
  background: var(--danger);
}

/* ---------- 买入侧汇总 ---------- */
.cash {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: 14px;
  border: 1px solid var(--rule);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-sheet);
  overflow: hidden;
}

.cash > div {
  padding: 13px 16px;
  border-right: 1px solid var(--rule);
}

.cash > div:last-child {
  border-right: 0;
}

.cash .k {
  display: block;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--ink-3);
}

.cash .v {
  display: block;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 500;
}

.cash .s {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--ink-3);
}

/* ---------- 买入计划 ---------- */
.plan {
  padding: 13px 20px 3px;
  border-top: 1px solid var(--rule);
}

.plan-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.plan-name {
  font-size: var(--t-13);
  font-weight: 600;
}

.plan-code {
  font-size: var(--t-11);
  color: var(--ink-3);
}

.plan-mode,
.plan-prog {
  padding: 1px 6px;
  border: 1px solid var(--rule);
  border-radius: 4px;
  font-size: var(--t-11);
  color: var(--ink-3);
  white-space: nowrap;
}

.plan-prog {
  border-radius: 999px;
}

.plan-prog.is-full {
  color: var(--accent-ink);
  border-color: var(--accent-line);
  background: var(--accent-wash);
}

.plan-warn {
  font-size: var(--t-11);
  color: var(--danger);
  border: 1px solid currentColor;
  border-radius: 4px;
  padding: 0 6px;
}

.plan-right {
  margin-left: auto;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.plan-price {
  font-size: var(--t-15);
  font-weight: 600;
}

.plan-pct {
  font-size: var(--t-12);
  font-weight: 600;
}

.plan-bar {
  height: 2px;
  margin: 8px 0 2px;
  border-radius: 2px;
  background: var(--rule);
  overflow: hidden;
}

.plan-bar i {
  display: block;
  height: 100%;
  width: 0;
  border-radius: 2px;
  background: var(--accent);
  transition: width 900ms var(--ease);
}

.plan-empty {
  margin: 10px 0 6px;
  padding: 10px 12px;
  font-size: var(--t-12);
  color: var(--ink-3);
  background: var(--surface-sunken);
  border-radius: 6px;
}

.ladder {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  margin-top: 8px;
  font-size: var(--t-13);
}

.ladder th {
  padding: 0 6px 6px;
  border-bottom: 1px solid var(--rule-strong);
  font-size: var(--t-11);
  font-weight: 400;
  letter-spacing: 0.06em;
  color: var(--ink-3);
  text-align: right;
}

.ladder th:first-child {
  text-align: left;
}

.ladder td {
  padding: 7px 6px;
  border-bottom: 1px solid var(--rule);
  text-align: right;
  color: var(--ink-2);
}

.ladder td:first-child {
  text-align: left;
  color: var(--ink);
}

.ladder tr:last-child td {
  border-bottom: 0;
}

.ladder .px {
  font-size: var(--t-13);
  font-weight: 500;
  color: var(--ink);
}

.ladder tr.is-done td,
.ladder tr.is-done .px {
  color: var(--ink-3);
  font-weight: 400;
}

.ladder td.c-chk,
.ladder th:last-child {
  text-align: right;
}

.plan-note {
  margin: 6px 0 0;
  padding-bottom: 12px;
  font-size: 11px;
  color: var(--ink-3);
}

.plan-note .k {
  color: var(--ink-4);
  margin-right: 6px;
}

/* ---------- 打钩 ---------- */
.chk {
  appearance: none;
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  padding: 0;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--rule-strong);
  border-radius: 3px;
  background: transparent;
  cursor: pointer;
  transition: border-color 140ms ease-out;
}

.chk:hover {
  border-color: var(--ink-3);
}

.chk:disabled {
  cursor: progress;
  opacity: 0.5;
}

.chk span {
  width: 9px;
  height: 9px;
  border-radius: 1px;
  background: var(--accent);
  transform: scale(0);
  opacity: 0;
  transition: transform 200ms cubic-bezier(0.16, 1, 0.3, 1), opacity 140ms ease-out;
}

.chk[aria-pressed='true'] {
  border-color: var(--accent);
}

.chk[aria-pressed='true'] span {
  transform: scale(1);
  opacity: 1;
}

/* ---------- 参考信息 ---------- */
.ref-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  width: 100%;
  padding: 15px 20px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.ref-chev {
  margin-left: auto;
  color: var(--ink-3);
  transition: transform var(--dur-2) var(--ease);
}

.ref-chev.is-open {
  transform: rotate(180deg);
}

.ref-body {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-height: 0;
  padding: 0 20px;
  opacity: 0;
  overflow: hidden;
  transition: max-height var(--dur-2) var(--ease), opacity var(--dur-2) var(--ease),
    padding var(--dur-2) var(--ease);
}

.ref-body.is-open {
  max-height: 140px;
  padding-bottom: 18px;
  opacity: 1;
}

.ref-cell .k {
  display: block;
  font-size: var(--t-11);
  letter-spacing: 0.06em;
  color: var(--ink-3);
}

.ref-cell .v {
  display: block;
  margin-top: 3px;
  font-size: var(--t-13);
}

.foot-note {
  margin: 14px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: var(--ink-3);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1180px) {
  .desk {
    padding: var(--s-5) var(--s-5) var(--s-7);
  }

  .duo {
    gap: 22px;
  }
}

@media (max-width: 1080px) {
  .duo {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .spine {
    display: none;
  }

  .col + .col {
    margin-top: 22px;
  }
}

@media (max-width: 900px) {
  .desk {
    max-width: none;
    padding: 24px 20px 48px;
  }

  .page-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .page-meta {
    text-align: left;
  }

  .verdict {
    flex-wrap: wrap;
    gap: 16px;
  }

  .verdict-split {
    width: 100%;
    margin-left: 0;
    padding-top: 12px;
    border-top: 1px solid var(--rule);
  }

  .vstat:first-child {
    padding-left: 0;
  }
}

/* 账表在窄屏改为竖排键值对 */
@media (max-width: 820px) {
  .ledger,
  .ledger tbody,
  .ledger tr,
  .ledger td {
    display: block;
    width: auto;
  }

  .ledger colgroup,
  .ledger thead {
    display: none;
  }

  .ledger tr {
    padding: 14px 0 10px;
    border-bottom: 1px solid var(--rule);
  }

  .ledger tbody tr:last-child {
    border-bottom: 0;
  }

  .ledger td {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    padding: 3px 20px;
    border: 0;
    text-align: right;
  }

  .ledger td::before {
    content: attr(data-label);
    flex: none;
    font-size: var(--t-11);
    letter-spacing: 0.06em;
    color: var(--ink-3);
  }

  .ledger td.c-name {
    display: block;
    padding-bottom: 8px;
    font-size: 14px;
  }

  .ledger td.c-name::before {
    content: none;
  }

  .ledger td.c-name .code {
    display: inline;
    margin: 0 0 0 8px;
  }

  .ledger td.stop .meter {
    display: inline-block;
    margin: 0 0 0 8px;
    vertical-align: middle;
  }

  .ref-body {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .desk {
    padding: 20px 14px 40px;
  }

  .verdict {
    padding: 16px;
  }

  .verdict-split {
    flex-wrap: wrap;
    gap: 8px 0;
  }

  .vstat {
    padding: 0 14px;
  }

  .page-meta {
    flex-wrap: wrap;
    gap: 12px 18px;
  }

  .signal-foot {
    flex-wrap: wrap;
  }

  .signal-actions {
    width: 100%;
    justify-content: flex-end;
    margin-top: 6px;
  }

  .cash {
    grid-template-columns: 1fr;
  }

  .cash > div {
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }

  .cash > div:last-child {
    border-bottom: 0;
  }

  .plan {
    padding: 13px 16px 3px;
  }

  .ladder,
  .ladder tbody,
  .ladder tr,
  .ladder td {
    display: block;
    width: auto;
  }

  .ladder colgroup,
  .ladder thead {
    display: none;
  }

  .ladder tr {
    padding: 7px 0;
    border-bottom: 1px solid var(--rule);
  }

  .ladder tr:last-child {
    border-bottom: 0;
  }

  .ladder td {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 2px 0;
    border: 0;
  }

  .ladder td::before {
    content: attr(data-label);
    font-size: var(--t-11);
    color: var(--ink-3);
  }

  .ladder td:first-child {
    font-weight: 600;
    padding-bottom: 4px;
  }

  .ladder td:first-child::before {
    content: none;
  }
}
</style>
