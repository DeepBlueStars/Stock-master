<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../config/config'
import WeeklySummaryBlock from './TradePlan/WeeklySummaryBlock.vue'
import TradePlanCard from './TradePlan/TradePlanCard.vue'

// 与 Dashboard.vue 保持一致的当前用户
const userId = '6928fca14f206ada1b7a9269'
const BASE_URL = config.baseUrl

const loading = ref(false)
const plans = ref([])

async function fetchPlans() {
  loading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/trade-plan/list`, { userId })
    if (response.data.state === 0) {
      plans.value = response.data.data || []
    } else {
      ElMessage.error(response.data.message || '获取交易计划失败')
      plans.value = []
    }
  } catch (error) {
    console.error('获取交易计划失败', error)
    ElMessage.error('获取交易计划失败，请稍后重试')
    plans.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchPlans)
</script>

<template>
  <div class="trade-plan-page">
    <WeeklySummaryBlock :user-id="userId" />

    <div class="plan-section-title">
      <span>持仓交易计划</span>
      <el-button size="small" @click="fetchPlans">刷新</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="plans.length === 0" description="暂无持仓交易计划，买入股票后自动生成">
        <router-link to="/stock">
          <el-button type="primary" size="small">去买入</el-button>
        </router-link>
      </el-empty>

      <el-row v-else :gutter="16">
        <el-col
          v-for="plan in plans"
          :key="plan.symbol"
          :xs="24"
          :sm="12"
          :lg="8"
          class="plan-col"
        >
          <TradePlanCard :plan="plan" @linked="fetchPlans" />
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.trade-plan-page {
  padding: 4px;
}

.plan-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  font-weight: 600;
  margin: 8px 0 12px;
}

.plan-col {
  margin-bottom: 16px;
}
</style>
