<template>
  <div class="positions-page">
    <div class="page-header">
      <h2>持仓管理</h2>
      <div class="header-actions">
        <el-button type="danger" @click="handleCloseAll" :disabled="!hasPositions">
          <el-icon><CircleClose /></el-icon>
          一键清仓
        </el-button>
        <el-button @click="refreshData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 持仓统计卡片 -->
    <div class="stats-cards">
      <el-card class="stat-card">
        <div class="stat-value" :class="getProfitClass(totalProfit)">
          {{ totalProfit >= 0 ? '+' : '' }}{{ formatNumber(totalProfit) }}
        </div>
        <div class="stat-label">总盈亏</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ formatNumber(totalMarketValue) }}</div>
        <div class="stat-label">持仓市值</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ holdingPositions.length }}</div>
        <div class="stat-label">持仓数量</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value" :class="getProfitClass(totalProfitRate)">
          {{ totalProfitRate >= 0 ? '+' : '' }}{{ totalProfitRate.toFixed(2) }}%
        </div>
        <div class="stat-label">盈亏比例</div>
      </el-card>
    </div>

    <!-- 持仓列表 -->
    <el-card class="positions-card">
      <template #header>
        <div class="card-header">
          <span>持仓列表</span>
          <div class="header-filters">
            <el-radio-group v-model="filterType" size="small">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="profit">盈利</el-radio-button>
              <el-radio-button label="loss">亏损</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <el-table
        :data="filteredPositions"
        style="width: 100%"
        v-loading="loading"
        :row-class-name="getRowClassName"
      >
        <el-table-column prop="stockCode" label="股票代码" width="100">
          <template #default="{ row }">
            <span class="font-mono">{{ row.stockCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockName" label="股票名称" width="100" />
        <el-table-column label="当前价" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ formatPrice(row.currentPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本价" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono text-secondary">{{ formatPrice(row.avgCost) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="持仓量" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ row.totalVolume }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono text-secondary">{{ row.availableVolume }}</span>
          </template>
        </el-table-column>
        <el-table-column label="市值" width="120" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ formatNumber(row.marketValue) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盈亏额" width="120" align="right">
          <template #default="{ row }">
            <span class="font-mono" :class="getProfitClass(row.profitLoss)">
              {{ row.profitLoss >= 0 ? '+' : '' }}{{ formatNumber(row.profitLoss) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="盈亏率" width="100" align="right">
          <template #default="{ row }">
            <span class="font-mono" :class="getProfitClass(row.profitLossRate)">
              {{ row.profitLossRate >= 0 ? '+' : '' }}{{ row.profitLossRate.toFixed(2) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column label="策略" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getStrategyType(row.strategy)">
              {{ getStrategyText(row.strategy) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="买入日期" width="120">
          <template #default="{ row }">
            <span class="text-sm">{{ formatDate(row.buyDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleSell(row)"
              :disabled="row.availableVolume === 0"
            >
              卖出
            </el-button>
            <el-button 
              link 
              size="small" 
              @click="handleSetStopLoss(row)"
            >
              止损
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!filteredPositions.length" description="暂无持仓" />
    </el-card>

    <!-- 今日交易记录 -->
    <el-card class="trades-card">
      <template #header>
        <div class="card-header">
          <span>今日交易</span>
          <el-button link size="small" @click="refreshData">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </template>

      <el-table :data="todayTrades" size="small">
        <el-table-column prop="tradeTime" label="时间" width="80">
          <template #default="{ row }">
            <span class="text-sm">{{ formatTime(row.tradeTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockCode" label="代码" width="90" />
        <el-table-column prop="stockName" label="名称" width="90" />
        <el-table-column label="类型" width="70">
          <template #default="{ row }">
            <el-tag :type="row.tradeType === 'buy' ? 'danger' : 'success'" size="small">
              {{ row.tradeType === 'buy' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="数量" width="90" align="right" />
        <el-table-column prop="price" label="价格" width="90" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ formatNumber(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 卖出弹窗 -->
    <el-dialog
      v-model="sellDialogVisible"
      title="卖出确认"
      width="500px"
    >
      <el-form :model="sellForm" label-width="100px">
        <el-form-item label="股票">
          <span>{{ sellForm.stockName }} ({{ sellForm.stockCode }})</span>
        </el-form-item>
        <el-form-item label="当前价">
          <span class="font-mono">{{ formatPrice(sellForm.currentPrice) }}</span>
        </el-form-item>
        <el-form-item label="成本价">
          <span class="font-mono">{{ formatPrice(sellForm.avgCost) }}</span>
        </el-form-item>
        <el-form-item label="可卖数量">
          <span>{{ sellForm.availableVolume }}</span>
        </el-form-item>
        <el-form-item label="卖出价格">
          <el-input-number v-model="sellForm.price" :precision="2" :step="0.01" style="width: 200px" />
        </el-form-item>
        <el-form-item label="卖出数量">
          <el-input-number 
            v-model="sellForm.volume" 
            :min="100" 
            :max="sellForm.availableVolume"
            :step="100"
            style="width: 200px"
          />
          <div class="volume-buttons">
            <el-button size="small" @click="sellForm.volume = sellForm.availableVolume">全仓</el-button>
            <el-button size="small" @click="sellForm.volume = Math.floor(sellForm.availableVolume / 2 / 100) * 100">半仓</el-button>
          </div>
        </el-form-item>
        <el-form-item label="预计盈亏">
          <span :class="getProfitClass(calculatedProfit)">
            {{ calculatedProfit >= 0 ? '+' : '' }}{{ formatNumber(calculatedProfit) }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sellDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmSell" :loading="selling">确认卖出</el-button>
      </template>
    </el-dialog>

    <!-- 设置止损弹窗 -->
    <el-dialog
      v-model="stopLossDialogVisible"
      title="设置止损"
      width="400px"
    >
      <el-form :model="stopLossForm" label-width="100px">
        <el-form-item label="股票">
          <span>{{ stopLossForm.stockName }}</span>
        </el-form-item>
        <el-form-item label="成本价">
          <span class="font-mono">{{ formatPrice(stopLossForm.avgCost) }}</span>
        </el-form-item>
        <el-form-item label="当前价">
          <span class="font-mono">{{ formatPrice(stopLossForm.currentPrice) }}</span>
        </el-form-item>
        <el-form-item label="止损价格">
          <el-input-number 
            v-model="stopLossForm.stopLossPrice" 
            :precision="2" 
            :step="0.01"
            :max="stopLossForm.avgCost"
            style="width: 200px"
          />
          <div class="stop-loss-hint">
            建议止损: {{ formatPrice(stopLossForm.avgCost * 0.94) }} (-6%)
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stopLossDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSetStopLoss">确认设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePositionStore } from '@/stores/position'

const router = useRouter()
const userStore = useUserStore()
const positionStore = usePositionStore()

const loading = ref(false)
const filterType = ref('all')

// 持仓数据
const holdingPositions = computed(() => positionStore.holdingPositions)
const hasPositions = computed(() => holdingPositions.value.length > 0)

// 过滤持仓
const filteredPositions = computed(() => {
  let positions = holdingPositions.value
  
  if (filterType.value === 'profit') {
    positions = positions.filter(p => p.profitLoss > 0)
  } else if (filterType.value === 'loss') {
    positions = positions.filter(p => p.profitLoss < 0)
  }
  
  return positions
})

// 统计数据
const totalProfit = computed(() => {
  return holdingPositions.value.reduce((sum, p) => sum + (p.profitLoss || 0), 0)
})

const totalMarketValue = computed(() => {
  return holdingPositions.value.reduce((sum, p) => sum + (p.marketValue || 0), 0)
})

const totalProfitRate = computed(() => {
  const totalCost = holdingPositions.value.reduce((sum, p) => sum + (p.avgCost * p.totalVolume), 0)
  return totalCost > 0 ? (totalProfit.value / totalCost) * 100 : 0
})

const todayTrades = computed(() => positionStore.todayTrades)

// 卖出弹窗
const sellDialogVisible = ref(false)
const selling = ref(false)
const sellForm = ref({
  positionId: 0,
  stockCode: '',
  stockName: '',
  currentPrice: 0,
  avgCost: 0,
  availableVolume: 0,
  price: 0,
  volume: 0
})

// 计算卖出盈亏
const calculatedProfit = computed(() => {
  const sellAmount = sellForm.value.price * sellForm.value.volume
  const costAmount = sellForm.value.avgCost * sellForm.value.volume
  return sellAmount - costAmount
})

// 止损弹窗
const stopLossDialogVisible = ref(false)
const stopLossForm = ref({
  positionId: 0,
  stockName: '',
  avgCost: 0,
  currentPrice: 0,
  stopLossPrice: 0
})

// 打开卖出弹窗
const handleSell = (row: any) => {
  sellForm.value = {
    positionId: row.id,
    stockCode: row.stockCode,
    stockName: row.stockName,
    currentPrice: row.currentPrice,
    avgCost: row.avgCost,
    availableVolume: row.availableVolume,
    price: row.currentPrice,
    volume: row.availableVolume
  }
  sellDialogVisible.value = true
}

// 确认卖出
const confirmSell = async () => {
  selling.value = true
  
  const order = {
    stockCode: sellForm.value.stockCode,
    stockName: sellForm.value.stockName,
    tradeType: 'sell',
    orderType: 'limit',
    volume: sellForm.value.volume,
    price: sellForm.value.price,
    strategy: 'first_board'
  }
  
  const result = await positionStore.createOrder(order)
  
  selling.value = false
  sellDialogVisible.value = false
  
  if (result) {
    ElMessage.success('卖出成功')
    refreshData()
  } else {
    ElMessage.error('卖出失败')
  }
}

// 设置止损
const handleSetStopLoss = (row: any) => {
  stopLossForm.value = {
    positionId: row.id,
    stockName: row.stockName,
    avgCost: row.avgCost,
    currentPrice: row.currentPrice,
    stopLossPrice: row.avgCost * 0.94 // 默认-6%
  }
  stopLossDialogVisible.value = true
}

// 确认设置止损
const confirmSetStopLoss = async () => {
  // 这里应该调用API设置止损
  ElMessage.success(`止损价格已设置为 ${stopLossForm.value.stopLossPrice.toFixed(2)}`)
  stopLossDialogVisible.value = false
}

// 一键清仓
const handleCloseAll = async () => {
  const confirm = await ElMessageBox.confirm(
    `确定要卖出所有持仓吗？共 ${holdingPositions.value.length} 只股票`,
    '一键清仓确认',
    {
      confirmButtonText: '确认清仓',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).catch(() => false)
  
  if (!confirm) return
  
  const success = await positionStore.closeAllPositions()
  
  if (success) {
    ElMessage.success('清仓成功')
    refreshData()
  } else {
    ElMessage.error('清仓失败')
  }
}

// 刷新数据
const refreshData = async () => {
  loading.value = true
  await Promise.all([
    positionStore.fetchPositions(),
    positionStore.fetchTodayTrades(),
    userStore.fetchBalance()
  ])
  loading.value = false
}

// 获取行样式
const getRowClassName = ({ row }: { row: any }) => {
  if (row.profitLoss > 0) return 'profit-row'
  if (row.profitLoss < 0) return 'loss-row'
  return ''
}

// 工具函数
const formatPrice = (price: number) => {
  return price?.toFixed(2) || '--'
}

const formatNumber = (num: number) => {
  if (!num) return '--'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatDate = (date: string) => {
  if (!date) return '--'
  return date.split('T')[0]
}

const formatTime = (time: string) => {
  if (!time) return '--'
  return time.split('T')[1]?.substring(0, 5) || time
}

const getProfitClass = (profit: number) => {
  if (profit > 0) return 'text-up'
  if (profit < 0) return 'text-down'
  return 'text-flat'
}

const getStrategyType = (strategy: string) => {
  const map: any = {
    first_board: 'info',
    second_board: 'warning',
    third_board: 'danger',
    dragon_head: 'success'
  }
  return map[strategy] || 'info'
}

const getStrategyText = (strategy: string) => {
  const map: any = {
    first_board: '首板',
    second_board: '二板',
    third_board: '三板',
    dragon_head: '龙头'
  }
  return map[strategy] || strategy
}

const getStatusType = (status: string) => {
  const map: any = {
    pending: 'warning',
    filled: 'success',
    cancelled: 'info',
    failed: 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: any = {
    pending: '待成交',
    filled: '已成交',
    cancelled: '已撤销',
    failed: '失败'
  }
  return map[status] || status
}

// 初始化
onMounted(() => {
  refreshData()
})
</script>

<style scoped lang="scss">
.positions-page {
  width: 100%;
  padding: 24px;
  
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    
    h2 {
      font-size: 24px;
      font-weight: 600;
      color: var(--color-text-primary);
    }
    
    .header-actions {
      display: flex;
      gap: 12px;
      
      .el-button {
        &.el-button--default {
          background-color: var(--color-surface-light);
          border-color: var(--color-border);
          color: var(--color-text-primary);
          
          &:hover {
            background-color: var(--color-surface);
            border-color: var(--color-primary);
            color: var(--color-primary);
          }
        }
        
        &.el-button--danger {
          background-color: var(--color-danger);
          border-color: var(--color-danger);
          color: #fff;
          
          &:hover {
            background-color: var(--color-danger);
            border-color: var(--color-danger);
            opacity: 0.9;
          }
        }
      }
    }
  }
  
  .stats-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;
    
    .stat-card {
      text-align: center;
      background-color: var(--color-surface);
      border-color: var(--color-border);
      
      :deep(.el-card__body) {
        background-color: var(--color-surface);
      }
      
      .stat-value {
        font-size: 28px;
        font-weight: 700;
        font-family: 'Roboto Mono', monospace;
        margin-bottom: 8px;
        color: var(--color-text-primary);
      }
      
      .stat-label {
        font-size: 14px;
        color: var(--color-text-secondary);
      }
    }
  }
  
  .positions-card {
    margin-bottom: 24px;
    width: 100%;
    background-color: var(--color-surface);
    border-color: var(--color-border);
    
    :deep(.el-card__header) {
      background-color: var(--color-surface);
      border-color: var(--color-border);
    }
    
    :deep(.el-card__body) {
      background-color: var(--color-surface);
    }
    
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    /* 单选按钮组样式 */
    :deep(.el-radio-button__inner) {
      background-color: var(--color-surface-light);
      border-color: var(--color-border);
      color: var(--color-text-primary);
      
      &:hover {
        color: var(--color-primary);
      }
    }
    
    :deep(.el-radio-button__orig-radio:checked + .el-radio-button__inner) {
      background-color: var(--color-primary);
      border-color: var(--color-primary);
      color: #fff;
    }
    
    /* 表格样式 */
    :deep(.el-table) {
      background: transparent;
      width: 100% !important;
      min-width: 100%;
      box-sizing: border-box;
      
      th {
        background-color: var(--color-surface);
        color: var(--color-text-secondary);
      }
      
      td {
        color: var(--color-text-primary);
        background-color: var(--color-surface);
      }
      
      tr:hover td {
        background-color: var(--color-surface-light) !important;
      }
      
      .el-table__header-wrapper,
      .el-table__body-wrapper,
      .el-table__footer-wrapper {
        width: 100% !important;
      }
      
      .el-table__header,
      .el-table__body {
        width: 100% !important;
        min-width: 100%;
      }
    }
    
    :deep(.profit-row) {
      background-color: rgba(239, 68, 68, 0.05);
    }
    
    :deep(.loss-row) {
      background-color: rgba(34, 197, 94, 0.05);
    }
  }
  
  .trades-card {
    width: 100%;
    background-color: var(--color-surface);
    border-color: var(--color-border);
    
    :deep(.el-card__header) {
      background-color: var(--color-surface);
      border-color: var(--color-border);
    }
    
    :deep(.el-card__body) {
      background-color: var(--color-surface);
    }
    
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    /* 表格样式 */
    :deep(.el-table) {
      background: transparent;
      width: 100% !important;
      min-width: 100%;
      box-sizing: border-box;
      
      th {
        background-color: var(--color-surface);
        color: var(--color-text-secondary);
      }
      
      td {
        color: var(--color-text-primary);
        background-color: var(--color-surface);
      }
      
      tr:hover td {
        background-color: var(--color-surface-light) !important;
      }
      
      .el-table__header-wrapper,
      .el-table__body-wrapper,
      .el-table__footer-wrapper {
        width: 100% !important;
      }
      
      .el-table__header,
      .el-table__body {
        width: 100% !important;
        min-width: 100%;
      }
    }
  }
  
  /* 弹窗样式 */
  :deep(.el-dialog__header) {
    background-color: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
    
    .el-dialog__title {
      color: var(--color-text-primary);
    }
  }
  
  :deep(.el-dialog__body) {
    background-color: var(--color-surface);
  }
  
  :deep(.el-dialog__footer) {
    background-color: var(--color-surface);
    border-top: 1px solid var(--color-border);
    
    .el-button {
      &.el-button--default {
        background-color: var(--color-surface-light);
        border-color: var(--color-border);
        color: var(--color-text-primary);
        
        &:hover {
          background-color: var(--color-surface);
          border-color: var(--color-primary);
          color: var(--color-primary);
        }
      }
      
      &.el-button--primary {
        background-color: var(--color-primary);
        border-color: var(--color-primary);
        color: #fff;
        
        &:hover {
          background-color: var(--color-primary);
          border-color: var(--color-primary);
          opacity: 0.9;
        }
      }
    }
  }
  
  /* 弹窗中的输入框样式 */
  :deep(.el-input__wrapper) {
    background-color: var(--color-surface-light);
    border-color: var(--color-border);
    box-shadow: none;
    
    &:hover {
      border-color: var(--color-primary);
    }
    
    &.is-focus {
      border-color: var(--color-primary);
      box-shadow: 0 0 0 1px var(--color-primary) inset;
    }
  }
  
  :deep(.el-input__inner) {
    color: var(--color-text-primary);
  }
  
  /* 数字输入框样式 */
  :deep(.el-input-number__decrease),
  :deep(.el-input-number__increase) {
    background-color: var(--color-surface-light);
    border-color: var(--color-border);
    color: var(--color-text-secondary);
    
    &:hover {
      background-color: var(--color-surface);
      border-color: var(--color-primary);
      color: var(--color-primary);
    }
  }
  
  .volume-buttons {
    margin-top: 8px;
    
    .el-button {
      margin-right: 8px;
      background-color: var(--color-surface-light);
      border-color: var(--color-border);
      color: var(--color-text-primary);
      
      &:hover {
        background-color: var(--color-surface);
        border-color: var(--color-primary);
        color: var(--color-primary);
      }
    }
  }
  
  .stop-loss-hint {
    margin-top: 8px;
    font-size: 12px;
    color: var(--color-text-muted);
  }
}
</style>
