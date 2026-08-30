<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../config/config'

const BASE_URL = config.baseUrl
const loading = ref(false)
const record = ref(null)

const REGIME_LABEL = {
  BULL: { text: '牛', color: '#ef4444', advice: '激进（高仓位）' },
  BEAR: { text: '熊', color: '#10b981', advice: '防御（低仓位）' },
  SIDEWAYS: { text: '震荡', color: '#f59e0b', advice: '中性（中等仓位）' },
  RISK: { text: '风险', color: '#8b5cf6', advice: '空仓/防御' }
}

const regimeInfo = computed(() => {
  if (!record.value) return null
  return REGIME_LABEL[record.value.regime] || { text: record.value.regime, color: '#6b7280', advice: '-' }
})

async function fetchLatest() {
  loading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/market-regime/latest`)
    if (response.data && response.data.state === 0) {
      record.value = response.data.data
    } else {
      record.value = null
      ElMessage.info('暂无市场状态记录')
    }
  } catch (error) {
    console.error('获取市场状态失败:', error)
    ElMessage.error('获取市场状态失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(fetchLatest)
</script>

<template>
  <div class="regime-page" v-loading="loading">
    <el-card shadow="hover" class="regime-card">
      <template #header>
        <h2 class="regime-title">市场温度计</h2>
      </template>
      <template v-if="record">
        <div class="regime-main">
          <div class="regime-badge" :style="{ backgroundColor: regimeInfo.color }">
            {{ regimeInfo.text }}
          </div>
          <div class="regime-advice">{{ regimeInfo.advice }}</div>
          <div class="regime-date">{{ record.date }}</div>
        </div>
        <el-divider />
        <div class="report-box">{{ record.report }}</div>
        <el-divider />
        <div class="indicators" v-if="record.indicators">
          <div class="indicator-item">MA20：{{ record.indicators.ma20 }}</div>
          <div class="indicator-item">MA60：{{ record.indicators.ma60 }}</div>
          <div class="indicator-item">MA250：{{ record.indicators.ma250 }}</div>
          <div class="indicator-item">量比：{{ record.indicators.volumeRatio }}</div>
          <div class="indicator-item">波动率：{{ (record.indicators.volatility * 100).toFixed(2) }}%</div>
          <div class="indicator-item">PE分位：{{ record.indicators.pePercentile }}</div>
          <div class="indicator-item">PB分位：{{ record.indicators.pbPercentile }}</div>
        </div>
      </template>
      <el-empty v-else description="暂无市场状态记录" />
    </el-card>
  </div>
</template>

<style scoped>
.regime-page {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
}
.regime-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}
.regime-main {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}
.regime-badge {
  color: #fff;
  font-size: 1.5rem;
  font-weight: 700;
  padding: 12px 28px;
  border-radius: 12px;
}
.regime-advice {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
}
.regime-date {
  margin-left: auto;
  color: #6b7280;
}
.report-box {
  background-color: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  color: #374151;
  line-height: 1.6;
}
.indicators {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
}
.indicator-item {
  color: #4b5563;
  font-size: 0.875rem;
}
</style>
