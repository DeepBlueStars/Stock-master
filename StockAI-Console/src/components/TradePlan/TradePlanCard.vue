<script setup>
import { computed, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../../config/config'

const props = defineProps({
  plan: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['linked'])

const BASE_URL = config.baseUrl
const linking = ref(false)

const degraded = computed(() => Boolean(props.plan.degraded))
const buyModeText = computed(() => (props.plan.buyMode === 'BATCHED' ? '分批买入' : '一次性买入'))
const batches = computed(() => props.plan.buyBatches || [])

function money(value) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : String(value)
}

function percent(value) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const number = Number(value)
  return Number.isFinite(number) ? `${(number * 100).toFixed(2)}%` : String(value)
}

async function linkMonitor() {
  linking.value = true
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/link-monitor`, {
      userId: props.plan.userId,
      symbol: props.plan.symbol
    })
    if (response.data.state !== 0) {
      ElMessage.error(response.data.message || '写入提醒失败')
      return
    }
    const { before, after } = response.data.data
    ElMessage.success(
      `已写入提醒：止盈 ${money(before.takeProfitValue)} → ${money(after.takeProfitValue)}，` +
        `止损 ${money(before.stopLossValue)} → ${money(after.stopLossValue)}`
    )
    emit('linked')
  } catch (error) {
    console.error('写入止盈止损提醒失败', error)
    ElMessage.error('写入提醒失败，请稍后重试')
  } finally {
    linking.value = false
  }
}
</script>

<template>
  <el-card shadow="hover" class="plan-card">
    <template #header>
      <div class="plan-header">
        <div class="plan-title">
          <span class="plan-name">{{ plan.stockName || plan.symbol }}</span>
          <span class="plan-symbol">{{ plan.symbol }}</span>
          <el-tag v-if="plan.industry" size="small" type="info">{{ plan.industry }}</el-tag>
          <el-tag v-if="degraded" size="small" type="warning">计划降级</el-tag>
          <el-tag v-if="plan.monitorLinked" size="small" type="success">已联动提醒</el-tag>
        </div>
        <span class="plan-pnl" :class="{ loss: Number(plan.floatPnl) < 0 }">
          {{ money(plan.floatPnl) }}（{{ percent(plan.floatPnlRate) }}）
        </span>
      </div>
    </template>

    <div class="plan-grid">
      <div class="plan-item">
        <span class="label">仓位 / 成本</span>
        <span class="value">{{ plan.quantity ?? '—' }} 股 / {{ money(plan.avgCost) }}</span>
      </div>
      <div class="plan-item">
        <span class="label">现价</span>
        <span class="value">{{ money(plan.lastClose) }}</span>
      </div>
      <div class="plan-item">
        <span class="label">建议止损</span>
        <span class="value">{{ money(plan.stopLossPrice) }}（{{ percent(plan.distToStop) }}）</span>
      </div>
      <div class="plan-item">
        <span class="label">建议止盈</span>
        <span class="value">{{ money(plan.takeProfitPrice) }}（{{ percent(plan.distToTarget) }}）</span>
      </div>
      <div class="plan-item">
        <span class="label">ATR(14) / 占比</span>
        <span class="value">{{ money(plan.atr14) }} / {{ percent(plan.atrRatio) }}</span>
      </div>
      <div class="plan-item">
        <span class="label">支撑 / 阻力</span>
        <span class="value">{{ money(plan.support20d) }} / {{ money(plan.resistance20d) }}</span>
      </div>
    </div>

    <div class="plan-block">
      <div class="block-title">买入方式：{{ buyModeText }}</div>
      <div v-for="(batch, index) in batches" :key="index" class="block-line">
        第 {{ index + 1 }} 批：比例 {{ percent(batch.ratio) }}，触发价 {{ money(batch.price) }}
        <template v-if="batch.amount !== null && batch.amount !== undefined">
          ，金额 ¥{{ money(batch.amount) }}
        </template>
      </div>
      <div class="block-line muted">{{ plan.buyBasis || '—' }}</div>
    </div>

    <div class="plan-block">
      <div class="block-title">补仓规则</div>
      <div class="block-line">{{ plan.addPositionRule?.conditionText || '—' }}</div>
    </div>

    <div class="plan-block">
      <div class="block-title">卖出策略</div>
      <div class="block-line">{{ plan.sellStrategy?.takeProfitAction || '—' }}</div>
      <div class="block-line">{{ plan.sellStrategy?.stopLossAction || '—' }}</div>
      <div v-if="plan.sellStrategy?.resistanceNote" class="block-line muted">
        {{ plan.sellStrategy.resistanceNote }}
      </div>
    </div>

    <div v-if="degraded" class="plan-degraded">降级原因：{{ plan.degradedReason || '历史数据不足' }}</div>

    <div class="plan-footer">
      <span class="plan-time">生成时间：{{ plan.generatedAt || '—' }}</span>
      <el-button size="small" type="primary" :loading="linking" @click="linkMonitor">
        写入止盈止损提醒
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.plan-card {
  height: 100%;
}

.plan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.plan-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.plan-name {
  font-size: 15px;
  font-weight: 600;
}

.plan-symbol {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.plan-pnl {
  font-size: 13px;
  font-weight: 600;
  color: #e6a23c;
}

.plan-pnl.loss {
  color: #67c23a;
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
  margin-bottom: 8px;
}

.plan-item {
  display: flex;
  flex-direction: column;
  font-size: 12px;
}

.plan-item .label {
  color: var(--el-text-color-secondary);
}

.plan-item .value {
  font-weight: 500;
}

.plan-block {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
  margin-top: 6px;
  font-size: 12px;
}

.block-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.block-line {
  line-height: 1.6;
}

.block-line.muted {
  color: var(--el-text-color-secondary);
}

.plan-degraded {
  margin-top: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 12px;
}

.plan-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  gap: 8px;
}

.plan-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
