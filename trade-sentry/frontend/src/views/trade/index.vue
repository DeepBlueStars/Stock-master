<template>
  <div class="trade-page">
    <div class="page-header">
      <h2>交易面板</h2>
      <div class="account-info">
        <el-statistic title="可用资金" :value="userStore.balance.available" :precision="2">
          <template #suffix>元</template>
        </el-statistic>
        <el-statistic title="持仓市值" :value="positionStore.totalMarketValue" :precision="2" class="stat-item">
          <template #suffix>元</template>
        </el-statistic>
        <el-statistic title="今日盈亏" :value="positionStore.totalProfit" :precision="2" class="stat-item">
          <template #suffix>元</template>
        </el-statistic>
      </div>
    </div>

    <div class="trade-content">
      <!-- 左侧：交易表单 -->
      <el-card class="trade-form-card">
        <template #header>
          <div class="card-header">
            <span>下单交易</span>
            <el-radio-group v-model="tradeType" size="small">
              <el-radio-button label="buy">买入</el-radio-button>
              <el-radio-button label="sell">卖出</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="trade-form"
        >
          <el-form-item label="股票代码" prop="stockCode">
            <el-input
              v-model="form.stockCode"
              placeholder="输入股票代码"
              maxlength="6"
              @input="handleCodeInput"
              @blur="fetchStockInfo"
            >
              <template #append>
                <el-button @click="fetchStockInfo">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
          </el-form-item>

          <div v-if="loadingStock" class="stock-info loading">
            <div class="loading-container">
              <div class="loading-spinner"></div>
              <span class="loading-text">加载股票信息中...</span>
            </div>
          </div>
          <div v-else-if="currentStock.name" class="stock-info">
            <div class="stock-name">{{ currentStock.name }}</div>
            <div class="stock-price" :class="getChangeClass(currentStock.changeRate)">
              <span class="price">{{ formatPrice(currentStock.currentPrice) }}</span>
              <span class="change">{{ currentStock.changeRate > 0 ? '+' : '' }}{{ currentStock.changeRate?.toFixed(2) }}%</span>
            </div>
          </div>

          <el-form-item label="委托方式" prop="orderType">
            <el-radio-group v-model="form.orderType">
              <el-radio label="limit">限价</el-radio>
              <el-radio label="market">市价</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="委托价格" prop="price" v-if="form.orderType === 'limit'">
            <el-input-number
              v-model="form.price"
              :min="0.01"
              :step="0.01"
              :precision="2"
              :max="9999"
              style="width: 100%"
              @change="calculateAmount"
            />
            <div class="price-buttons">
              <el-button size="small" @click="setPrice('limit_up')">涨停</el-button>
              <el-button size="small" @click="setPrice('prev_close')">昨收</el-button>
              <el-button size="small" @click="setPrice('current')">现价</el-button>
              <el-button size="small" @click="setPrice('limit_down')">跌停</el-button>
            </div>
          </el-form-item>

          <el-form-item label="委托数量" prop="volume">
            <el-input-number
              v-model="form.volume"
              :min="100"
              :step="100"
              :max="1000000"
              style="width: 100%"
              @change="calculateAmount"
            />
            <div class="volume-buttons">
              <el-button size="small" @click="setVolume(0.25)">1/4仓</el-button>
              <el-button size="small" @click="setVolume(0.5)">半仓</el-button>
              <el-button size="small" @click="setVolume(0.75)">3/4仓</el-button>
              <el-button size="small" @click="setVolume(1)">全仓</el-button>
            </div>
          </el-form-item>

          <el-form-item label="交易策略" prop="strategy">
            <el-select v-model="form.strategy" style="width: 100%">
              <el-option label="扫板" value="sweep_board" />
              <el-option label="排板" value="queue_board" />
              <el-option label="龙头二波" value="dragon_second_wave" />
            </el-select>
          </el-form-item>

          <div class="trade-summary">
            <div class="summary-row">
              <span>成交金额</span>
              <span class="value">¥{{ formatNumber(calculatedAmount) }}</span>
            </div>
            <div class="summary-row">
              <span>手续费</span>
              <span class="value">¥{{ formatNumber(calculatedCommission) }}</span>
            </div>
            <div class="summary-row">
              <span>印花税</span>
              <span class="value">¥{{ formatNumber(calculatedTax) }}</span>
            </div>
            <div class="summary-row total">
              <span>总费用</span>
              <span class="value">¥{{ formatNumber(calculatedTotal) }}</span>
            </div>
          </div>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              style="width: 100%"
              :disabled="!canSubmit"
              :loading="submitting"
              @click="handleSubmit"
            >
              {{ tradeType === 'buy' ? '买入' : '卖出' }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 右侧：盘口和最近交易 -->
      <div class="right-panel">
        <el-card class="order-book-card" v-if="currentStock.bidAsk">
          <template #header>
            <span>五档盘口</span>
          </template>
          <div class="order-book">
            <div class="ask-list">
              <div v-for="i in 5" :key="`ask${6-i}`" class="book-row">
                <span class="label">卖{{ 6-i }}</span>
                <span class="price text-up">{{ formatPrice(currentStock.bidAsk[`ask${6-i}`]?.price) }}</span>
                <span class="volume">{{ formatVolume(currentStock.bidAsk[`ask${6-i}`]?.volume) }}</span>
              </div>
            </div>
            <el-divider />
            <div class="bid-list">
              <div v-for="i in 5" :key="`bid${i}`" class="book-row">
                <span class="label">买{{ i }}</span>
                <span class="price text-down">{{ formatPrice(currentStock.bidAsk[`bid${i}`]?.price) }}</span>
                <span class="volume">{{ formatVolume(currentStock.bidAsk[`bid${i}`]?.volume) }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="recent-trades-card">
          <template #header>
            <div class="card-header">
              <span>今日委托</span>
              <el-button link size="small" @click="refreshTrades">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </template>
          <el-table :data="todayTrades" size="small">
            <el-table-column prop="stockCode" label="代码" width="80" />
            <el-table-column prop="stockName" label="名称" width="80" />
            <el-table-column label="类型" width="60">
              <template #default="{ row }">
                <el-tag :type="row.tradeType === 'buy' ? 'danger' : 'success'" size="small">
                  {{ row.tradeType === 'buy' ? '买' : '卖' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="volume" label="数量" width="80" />
            <el-table-column prop="price" label="价格" width="80">
              <template #default="{ row }">
                <span class="font-mono">{{ formatPrice(row.price) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button 
                  v-if="row.status === 'pending'" 
                  link 
                  type="danger" 
                  size="small"
                  @click="cancelOrder(row.id)"
                >
                  撤单
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePositionStore } from '@/stores/position'
import { useMarketStore } from '@/stores/market'
import type { TradeType, OrderType, Strategy } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const positionStore = usePositionStore()
const marketStore = useMarketStore()

const formRef = ref()
const tradeType = ref<TradeType>('buy')
const submitting = ref(false)

const form = reactive({
  stockCode: '',
  stockName: '',
  orderType: 'limit' as OrderType,
  price: 0,
  volume: 100,
  strategy: 'sweep_board' as Strategy
})

const currentStock = ref<any>({})
const todayTrades = ref<any[]>([])
const loadingStock = ref(false)

// 手续费率
const COMMISSION_RATE = 0.0003
const TAX_RATE = 0.001

const rules = {
  stockCode: [{ required: true, message: '请输入股票代码', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  volume: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

// 计算成交金额
const calculatedAmount = computed(() => {
  return form.price * form.volume
})

// 计算手续费
const calculatedCommission = computed(() => {
  return calculatedAmount.value * COMMISSION_RATE
})

// 计算印花税（卖出时）
const calculatedTax = computed(() => {
  return tradeType.value === 'sell' ? calculatedAmount.value * TAX_RATE : 0
})

// 计算总费用
const calculatedTotal = computed(() => {
  return calculatedAmount.value + calculatedCommission.value + calculatedTax.value
})

// 是否可以提交
const canSubmit = computed(() => {
  return form.stockCode && form.stockCode.length === 6 && form.price > 0 && form.volume >= 100
})

// 处理代码输入
const handleCodeInput = (value: string) => {
  form.stockCode = value.replace(/[^0-9]/g, '').slice(0, 6)
}

// 获取股票信息
const fetchStockInfo = async () => {
  if (form.stockCode.length !== 6) {
    ElMessage.warning('请输入6位股票代码')
    return
  }
  
  // 清空当前股票信息，确保加载动画能够显示
  currentStock.value = {}
  loadingStock.value = true
  
  try {
    // 模拟网络延迟，确保加载动画有足够时间显示
    await new Promise(resolve => setTimeout(resolve, 500))
    
    const stock = await marketStore.fetchStockDetail(form.stockCode)
    if (stock) {
      currentStock.value = stock
      form.stockName = stock.name
      form.price = stock.currentPrice
      calculateAmount()
      ElMessage.success('股票信息加载成功')
    } else {
      ElMessage.error('获取股票信息失败，请检查股票代码是否正确')
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loadingStock.value = false
  }
}

// 设置价格
const setPrice = (type: string) => {
  if (!currentStock.value.currentPrice) return
  
  const prevClose = currentStock.value.prevClose
  const limitUp = prevClose * 1.1
  const limitDown = prevClose * 0.9
  
  switch (type) {
    case 'limit_up':
      form.price = Math.round(limitUp * 100) / 100
      break
    case 'limit_down':
      form.price = Math.round(limitDown * 100) / 100
      break
    case 'prev_close':
      form.price = prevClose
      break
    case 'current':
      form.price = currentStock.value.currentPrice
      break
  }
  calculateAmount()
}

// 设置仓位
const setVolume = (ratio: number) => {
  if (!form.price) return
  
  const available = tradeType.value === 'buy' 
    ? userStore.balance.available 
    : positionStore.holdingPositions.find(p => p.stockCode === form.stockCode)?.availableVolume || 0
  
  const maxVolume = Math.floor((available / form.price) * ratio / 100) * 100
  form.volume = Math.max(100, maxVolume)
  calculateAmount()
}

// 计算金额
const calculateAmount = () => {
  // 触发计算属性更新
}

// 提交订单
const handleSubmit = async () => {
  if (!formRef.value) return
  console.log('form', form)
  
  try {
    debugger
    const action = tradeType.value === 'buy' ? '买入' : '卖出'
    
    // 先验证必要字段
    if (!form.stockCode || !form.stockName || form.price <= 0 || form.volume < 100) {
      ElMessage.warning('请填写完整的交易信息')
      return
    }
    
    submitting.value = true
    
    const order = {
      stockCode: form.stockCode,
      stockName: form.stockName,
      tradeType: tradeType.value,
      orderType: form.orderType,
      volume: form.volume,
      price: form.price,
      strategy: form.strategy
    }
    
    const result = await positionStore.createOrder(order)
    
    submitting.value = false
    
    if (result) {
      ElMessage.success(`${action}成功`)
      refreshTrades()
      userStore.fetchBalance()
      positionStore.fetchPositions()
    } else {
      ElMessage.error(`${action}失败`)
    }
  } catch (error) {
    console.error('表单验证失败', error)
    return
  }
}

// 刷新今日委托
const refreshTrades = async () => {
  await positionStore.fetchTodayTrades()
  todayTrades.value = positionStore.todayTrades
}

// 撤单
const cancelOrder = async (orderId: number) => {
  const success = await positionStore.cancelOrder(orderId)
  if (success) {
    ElMessage.success('撤单成功')
    refreshTrades()
  } else {
    ElMessage.error('撤单失败')
  }
}

// 工具函数
const formatPrice = (price: number) => {
  return price?.toFixed(2) || '--'
}

const formatNumber = (num: number) => {
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatVolume = (volume: number) => {
  if (!volume) return '--'
  if (volume >= 10000) {
    return (volume / 10000).toFixed(1) + '万'
  }
  return volume.toString()
}

const getChangeClass = (change: number) => {
  if (change > 0) return 'text-up'
  if (change < 0) return 'text-down'
  return 'text-flat'
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
  // 从URL参数获取股票代码
  const code = route.query.code as string
  if (code) {
    // 确保股票代码是6位数字
    form.stockCode = code.replace(/[^0-9]/g, '').slice(0, 6)
    if (form.stockCode.length === 6) {
      fetchStockInfo()
    }
  }
  
  refreshTrades()
})
</script>

<style scoped lang="scss">
.trade-page {
  width: 100%;
  padding: 24px;
  
  .page-header {
    margin-bottom: 24px;
    
    h2 {
      font-size: 24px;
      font-weight: 600;
      color: var(--color-text-primary);
      margin-bottom: 16px;
    }
    
    .account-info {
      display: flex;
      gap: 32px;
    }
  }
  
  .trade-content {
    display: grid;
    grid-template-columns: 480px 1fr;
    gap: 24px;
  }
  
  .trade-form-card {
      background-color: var(--color-surface);
      border-color: var(--color-border);
      
      :deep(.el-card__header) {
        background-color: var(--color-surface);
        border-color: var(--color-border);
      }
      
      :deep(.el-card__body) {
        background-color: var(--color-surface);
      }
      
      /* 输入框样式 */
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
        
        &.is-disabled {
          color: var(--color-text-secondary);
          opacity: 0.6;
        }
      }
      
      /* 选择器样式 */
      :deep(.el-select__wrapper) {
        background-color: var(--color-surface-light);
        border-color: var(--color-border);
        
        &:hover {
          border-color: var(--color-primary);
        }
        
        &.is-focus {
          border-color: var(--color-primary);
        }
      }
      
      :deep(.el-select__placeholder),
      :deep(.el-select__input) {
        color: var(--color-text-primary);
      }
      
      :deep(.el-select__caret) {
        color: var(--color-text-secondary);
      }
      
      /* 按钮样式 */
      :deep(.el-button) {
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
        
        &.el-button--small {
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
      
      /* 单选按钮样式 */
      :deep(.el-radio__label) {
        color: var(--color-text-primary);
      }
      
      :deep(.el-radio__input.is-checked .el-radio__inner) {
        border-color: var(--color-primary);
        background-color: var(--color-primary);
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
      
      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      
      .stock-info {
        text-align: center;
        padding: 16px;
        background-color: var(--color-surface-light);
        border-radius: 8px;
        margin-bottom: 16px;
        
        &.loading {
          display: flex;
          justify-content: center;
          align-items: center;
          min-height: 120px;
        }
        
        .loading-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 12px;
        }
        
        .loading-spinner {
          width: 40px;
          height: 40px;
          border: 4px solid var(--color-border);
          border-top-color: var(--color-primary);
          border-radius: 50%;
          animation: spin 1s linear infinite;
        }
        
        .loading-text {
          font-size: 14px;
          color: var(--color-text-secondary);
        }
        
        .stock-name {
          font-size: 18px;
          font-weight: 600;
          color: var(--color-text-primary);
          margin-bottom: 8px;
        }
        
        .stock-price {
          .price {
            font-size: 28px;
            font-weight: 700;
            font-family: 'Roboto Mono', monospace;
            margin-right: 12px;
          }
          
          .change {
            font-size: 14px;
          }
        }
      }
      
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
      
      .price-buttons,
      .volume-buttons {
        display: flex;
        gap: 8px;
        margin-top: 8px;
        
        .el-button {
          flex: 1;
        }
      }
      
      .trade-summary {
        background-color: var(--color-surface-light);
        padding: 16px;
        border-radius: 8px;
        margin-bottom: 24px;
        
        .summary-row {
          display: flex;
          justify-content: space-between;
          margin-bottom: 8px;
          font-size: 14px;
          color: var(--color-text-secondary);
          
          &.total {
            margin-top: 12px;
            padding-top: 12px;
            border-top: 1px solid var(--color-border);
            font-weight: 600;
            font-size: 16px;
            color: var(--color-text-primary);
          }
          
          .value {
            font-family: 'Roboto Mono', monospace;
          }
        }
      }
    }
  
  .right-panel {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  
  .order-book-card {
    background-color: var(--color-surface);
    border-color: var(--color-border);
    
    :deep(.el-card__header) {
      background-color: var(--color-surface);
      border-color: var(--color-border);
    }
    
    :deep(.el-card__body) {
      background-color: var(--color-surface);
    }
    
    .order-book {
      .book-row {
        display: flex;
        align-items: center;
        padding: 8px 0;
        
        .label {
          width: 40px;
          color: var(--color-text-secondary);
          font-size: 13px;
        }
        
        .price {
          flex: 1;
          font-family: 'Roboto Mono', monospace;
          font-weight: 600;
        }
        
        .volume {
          width: 80px;
          text-align: right;
          color: var(--color-text-secondary);
          font-size: 13px;
        }
      }
    }
  }
  
  .recent-trades-card {
    flex: 1;
    background-color: var(--color-surface);
    border-color: var(--color-border);
    
    :deep(.el-card__header) {
      background-color: var(--color-surface);
      border-color: var(--color-border);
    }
    
    :deep(.el-card__body) {
      background-color: var(--color-surface);
    }
    
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
    
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }
}
</style>
