<template>
  <div class="market-page">
    <div class="page-header">
      <h2>行情监控</h2>
      <div class="market-stats">
        <el-statistic title="涨停家数" :value="marketStore.limitUpStocks.length">
          <template #suffix>家</template>
        </el-statistic>
        <el-statistic title="首板" :value="firstBoardCount" class="stat-item">
          <template #suffix>家</template>
        </el-statistic>
        <el-statistic title="二板" :value="secondBoardCount" class="stat-item">
          <template #suffix>家</template>
        </el-statistic>
        <el-statistic title="三板及以上" :value="highBoardCount" class="stat-item">
          <template #suffix>家</template>
        </el-statistic>
      </div>
    </div>
    
    <div class="content-grid">
      <!-- 涨停股票列表 -->
      <el-card class="stock-list-card">
        <template #header>
          <div class="card-header">
            <span>涨停股票</span>
            <el-radio-group v-model="activeBoardType" size="small">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="first">首板</el-radio-button>
              <el-radio-button label="second">二板</el-radio-button>
              <el-radio-button label="third">三板</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        
        <el-table
          :data="filteredStocks"
          style="width: 100%"
          @row-click="handleRowClick"
        >
          <el-table-column prop="code" label="代码" width="90">
            <template #default="{ row }">
              <span class="font-mono">{{ row.code }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="100" />
          <el-table-column label="板类型" width="80">
            <template #default="{ row }">
              <el-tag :type="getBoardTypeTag(row.boardType)" size="small">
                {{ getBoardTypeText(row.boardType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="涨停时间" width="100">
            <template #default="{ row }">
              <span class="text-sm">{{ row.limitUpTime.substring(0, 5) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="板块" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.sector }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="强度" width="80">
            <template #default="{ row }">
              <el-progress
                :percentage="row.sectorStrength * 10"
                :color="getStrengthColor"
                :show-text="false"
                :stroke-width="6"
              />
            </template>
          </el-table-column>
          <el-table-column label="AI评分" width="90" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.aiScore >= 80 ? 'success' : row.aiScore >= 60 ? 'warning' : 'info'"
                size="small"
              >
                {{ row.aiScore }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click.stop="goToTrade(row)">
                买入
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
      
      <!-- 板块排行 -->
      <el-card class="sector-card">
        <template #header>
          <div class="card-header">
            <span>板块排行</span>
          </div>
        </template>
        
        <div class="sector-list">
          <div
            v-for="sector in marketStore.sectors.slice(0, 10)"
            :key="sector.code"
            class="sector-item"
            :class="{ active: selectedSector === sector.code }"
            @click="selectSector(sector.code)"
          >
            <div class="sector-info">
              <span class="sector-name">{{ sector.name }}</span>
              <div class="sector-tags">
                <el-tag size="small" type="info">{{ sector.limitUpCount }}家涨停</el-tag>
              </div>
            </div>
            <div class="sector-change" :class="getChangeClass(sector.changeRate)">
              <span class="font-mono">{{ sector.changeRate > 0 ? '+' : '' }}{{ sector.changeRate.toFixed(2) }}%</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMarketStore } from '@/stores/market'
import type { LimitUpStock, BoardType } from '@/types'

const router = useRouter()
const marketStore = useMarketStore()
const activeBoardType = ref<'all' | BoardType>('all')
const selectedSector = ref<string | null>(null)

// 统计数据
const firstBoardCount = computed(() => marketStore.firstBoardStocks.length)
const secondBoardCount = computed(() => marketStore.secondBoardStocks.length)
const highBoardCount = computed(() => 
  marketStore.thirdBoardStocks.length + marketStore.limitUpStocks.filter(s => s.boardType === 'high').length
)

// 过滤后的股票
const filteredStocks = computed(() => {
  let stocks = marketStore.limitUpStocks
  
  // 按板类型过滤
  if (activeBoardType.value !== 'all') {
    stocks = stocks.filter(s => s.boardType === activeBoardType.value)
  }
  
  // 按板块过滤
  if (selectedSector.value) {
    stocks = stocks.filter(s => s.sector === selectedSector.value)
  }
  
  return stocks
})

// 获取板类型标签样式
const getBoardTypeTag = (type: BoardType): 'info' | 'warning' | 'danger' => {
  const map = {
    first: 'info',
    second: 'warning',
    third: 'danger',
    high: 'danger'
  }
  return map[type] as 'info' | 'warning' | 'danger'
}

// 获取板类型文本
const getBoardTypeText = (type: BoardType) => {
  const map = {
    first: '首板',
    second: '二板',
    third: '三板',
    high: '高位'
  }
  return map[type]
}

// 强度颜色
const getStrengthColor = [
  { color: '#EF4444', percentage: 20 },
  { color: '#F59E0B', percentage: 40 },
  { color: '#3B82F6', percentage: 60 },
  { color: '#22C55E', percentage: 80 },
  { color: '#10B981', percentage: 100 }
]

// 获取涨跌样式类
const getChangeClass = (change: number) => {
  if (change > 0) return 'text-up'
  if (change < 0) return 'text-down'
  return 'text-flat'
}

// 选择板块
const selectSector = (code: string) => {
  if (selectedSector.value === code) {
    selectedSector.value = null
  } else {
    selectedSector.value = code
  }
}

// 点击行
const handleRowClick = (row: LimitUpStock) => {
  marketStore.fetchStockDetail(row.code)
}

// 跳转到交易
const goToTrade = (row: LimitUpStock) => {
  router.push({
    path: '/trade',
    query: { code: row.code, name: row.name }
  })
}

// 初始化
  onMounted(() => {
    marketStore.fetchLimitUpStocks()
    marketStore.fetchSectors()
    
    // 每60秒刷新
    setInterval(() => {
      marketStore.fetchLimitUpStocks()
    }, 60000)
  })
</script>

<style scoped lang="scss">
.market-page {
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
    
    .market-stats {
      display: flex;
      gap: 32px;
      
      :deep(.el-statistic__content) {
        font-size: 24px;
        font-weight: 600;
        color: var(--color-text-primary);
        font-family: 'Roboto Mono', monospace;
      }
      
      :deep(.el-statistic__title) {
        font-size: 13px;
        color: var(--color-text-secondary);
      }
    }
  }
  
  .content-grid {
    display: grid;
    grid-template-columns: 1fr 360px;
    gap: 24px;
    width: 100%;
  }
  
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  
  .stock-list-card {
    background-color: var(--color-surface);
    border-color: var(--color-border);
    width: 100%;
    
    :deep(.el-card__header) {
      background-color: var(--color-surface);
      border-color: var(--color-border);
    }
    
    :deep(.el-card__body) {
      padding: 0;
      width: 100%;
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
        cursor: pointer;
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
  
  .sector-card {
    background-color: var(--color-surface);
    border-color: var(--color-border);
    width: 100%;
    
    :deep(.el-card__header) {
      background-color: var(--color-surface);
      border-color: var(--color-border);
    }
    
    :deep(.el-card__body) {
      padding: 16px;
      width: 100%;
    }
    
    .sector-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      width: 100%;
    }
    
    .sector-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      border: 1px solid transparent;
      background-color: var(--color-surface-light);
      width: 100%;
      
      &:hover {
        background-color: var(--color-surface-light);
      }
      
      &.active {
        background-color: rgba(59, 130, 246, 0.1);
        border-color: var(--color-primary);
      }
      
      .sector-info {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      
      .sector-name {
        font-size: 14px;
        font-weight: 500;
        color: var(--color-text-primary);
      }
      
      .sector-change {
        font-size: 16px;
        font-weight: 600;
        font-family: 'Roboto Mono', monospace;
      }
    }
  }
}
</style>
