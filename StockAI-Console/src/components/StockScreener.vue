<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../config/config'

const BASE_URL = config.baseUrl
const loading = ref(false)
const result = ref(null)

const RATING_LABEL = { HIGH: '高', MEDIUM: '中', LOW: '低', UNKNOWN: '未知' }
const RATING_TYPE = { HIGH: 'success', MEDIUM: 'warning', LOW: 'danger', UNKNOWN: 'info' }

const activeBoard = ref('balancedBoard')

const boardTabs = computed(() => {
  if (!result.value) return []
  return [
    { key: 'balancedBoard', label: '均衡型' },
    { key: 'valueBoard', label: '价值型' },
    { key: 'growthBoard', label: '成长型' }
  ].map(t => ({ ...t, list: result.value[t.key] || [] }))
})

const currentBoard = computed(() =>
  boardTabs.value.find(t => t.key === activeBoard.value) || null
)

const ratingText = (rating) => RATING_LABEL[rating] || rating
const ratingType = (rating) => RATING_TYPE[rating] || 'info'

async function fetchLatest() {
  loading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/screening/latest`)
    if (response.data && response.data.state === 0) {
      result.value = response.data.data
    } else {
      result.value = null
      ElMessage.info('暂无选股结果')
    }
  } catch (error) {
    console.error('获取选股结果失败:', error)
    ElMessage.error('获取选股结果失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(fetchLatest)
</script>

<template>
  <div class="screener-page" v-loading="loading">
    <el-card shadow="hover" class="screener-card">
      <template #header>
        <div class="card-header">
          <h2 class="card-title">选股</h2>
          <span v-if="result" class="card-date">{{ result.tradeDate }}</span>
        </div>
      </template>
      <template v-if="result">
        <div class="report-box">
          <div class="report-head">
            <span class="report-title">AI 综合报告</span>
            <el-tag v-if="result.aiReportStatus === 'fallback'" type="warning" size="small">
              AI 不可用，已降级量化文本
            </el-tag>
          </div>
          <div class="report-content">{{ result.aiReport }}</div>
        </div>
        <el-divider />
        <el-tabs v-model="activeBoard">
          <el-tab-pane
            v-for="tab in boardTabs"
            :key="tab.key"
            :label="tab.label"
            :name="tab.key"
          />
        </el-tabs>
        <el-table v-if="currentBoard" :data="currentBoard.list" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="股票" min-width="140">
            <template #default="scope">
              <span class="stock-name">{{ scope.row.name }}</span>
              <span class="stock-symbol">{{ scope.row.symbol }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="综合分" width="90" sortable />
          <el-table-column prop="valuation" label="估值" width="70" />
          <el-table-column prop="growth" label="成长" width="70" />
          <el-table-column prop="quality" label="质量" width="70" />
          <el-table-column prop="momentum" label="动量" width="70" />
          <el-table-column label="长期价值" width="90">
            <template #default="scope">
              <el-tag :type="ratingType(scope.row.longTermRating)" size="small">
                {{ ratingText(scope.row.longTermRating) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="入选理由" min-width="260" show-overflow-tooltip />
        </el-table>
      </template>
      <el-empty v-else description="暂无选股结果" />
    </el-card>
  </div>
</template>

<style scoped>
.screener-page {
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}
.card-date {
  color: #6b7280;
}
.report-box {
  background-color: #f8fafc;
  padding: 16px;
  border-radius: 8px;
}
.report-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.report-title {
  font-weight: 600;
  color: #1f2937;
}
.report-content {
  color: #374151;
  line-height: 1.6;
  white-space: pre-wrap;
}
.stock-name {
  font-weight: 600;
  color: #1f2937;
  margin-right: 8px;
}
.stock-symbol {
  color: #6b7280;
  font-size: 0.8125rem;
}
</style>
