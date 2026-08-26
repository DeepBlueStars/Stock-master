<template>
  <el-card class="config-preview">
    <template #header>
      <div class="card-header">
        <span>配置预览</span>
      </div>
    </template>

    <div class="config-preview-content">
      <!-- 资产配置图表 -->
      <div class="chart-section">
        <asset-chart :assets="assets" :category-positions="categoryPositions"></asset-chart>
      </div>

      <!-- 资产配置详情 -->
      <div class="detail-section">
        <h3 class="section-title">资产配置详情</h3>
        
        <div v-if="assets.length === 0" class="empty-detail">
          <el-empty description="暂无资产配置信息"></el-empty>
        </div>
        
        <el-table v-else :data="assetDetails" stripe border style="width: 100%" size="small">
          <el-table-column prop="name" label="资产名称" min-width="120"></el-table-column>
          <el-table-column prop="type" label="资产类型" min-width="100">
            <template #default="scope">
              <el-tag :type="getAssetTypeTagType(scope.row.type)">
                {{ getAssetTypeLabel(scope.row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ratio" label="预期比例" min-width="120">
            <template #default="scope">
              <div class="ratio-bar-container">
                <div 
                  class="ratio-bar" 
                  :style="{
                    width: scope.row.ratio + '%',
                    backgroundColor: scope.row.color
                  }"
                ></div>
                <span class="ratio-text">{{ scope.row.ratio }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="实际比例" min-width="120">
            <template #default="scope">
              <div class="ratio-bar-container">
                <div 
                  class="ratio-bar" 
                  :style="{
                    width: scope.row.actualRatio + '%',
                    backgroundColor: scope.row.color
                  }"
                ></div>
                <span class="ratio-text">{{ scope.row.actualRatio }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="当前金额" min-width="120">
            <template #default="scope">
              <span>{{ (scope.row.currentAmount || 0).toFixed(2) }} 元</span>
            </template>
          </el-table-column>
          <el-table-column label="预期金额" min-width="120">
            <template #default="scope">
              <span>{{ (scope.row.expectedAmount || 0).toFixed(2) }} 元</span>
            </template>
          </el-table-column>
          <el-table-column label="相差金额" min-width="120">
            <template #default="scope">
              <span :class="(scope.row.diffAmount || 0) > 0 ? 'diff-positive' : (scope.row.diffAmount || 0) < 0 ? 'diff-negative' : ''">
                {{ (scope.row.diffAmount || 0).toFixed(2) }} 元
              </span>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 总金额显示 -->
        <div v-if="assetDetails.length > 0" class="total-amount">
          <p>总金额: <strong>{{ (totalAmount || 0).toFixed(2) }} 元</strong></p>
        </div>
      </div>

      <!-- 配置描述 -->
      <div v-if="assets.length > 0" class="description-section">
        <h3 class="section-title">配置描述</h3>
        <div class="config-description">
          <p>该配置方案包含 <strong>{{ assets.length }}</strong> 种资产类型，具体配置如下：</p>
          <ul class="asset-list">
            <li 
              v-for="asset in assets" 
              :key="asset.id"
              class="asset-item"
            >
              <span 
                class="asset-color-dot" 
                :style="{ backgroundColor: asset.color }"
              ></span>
              <span class="asset-item-name">{{ asset.name }}</span>
              <span class="asset-item-ratio">{{ asset.ratio }}%</span>
            </li>
          </ul>
          <p>总资产配置比例为 <strong>{{ totalRatio }}%</strong>，符合配置要求。</p>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import AssetChart from './AssetChart.vue';
import axios from 'axios';

// 导入配置文件
import config from '../../config/config';

// 使用配置文件中的API基础地址
const BASE_URL = config.baseUrl;

const props = defineProps({
  assets: {
    type: Array,
    required: true,
    default: () => []
  },
  categoryPositions: {
    type: Array,
    required: true,
    default: () => []
  }
});

const emit = defineEmits(['save-config']);

// 计算总比例
const totalRatio = computed(() => {
  return Number(props.assets.reduce((sum, asset) => sum + asset.ratio, 0).toFixed(1));
});

// 计算所有配置分类总金额
const totalAmount = computed(() => {
  const sum = props.categoryPositions.reduce((sum, item) => sum + (item.positionAmount || 0), 0);
  return typeof sum === 'number' ? sum : 0;
});

// 计算资产配置详情数据（包含实际比例和相差金额）
const assetDetails = computed(() => {
  return props.assets.map(asset => {
    // 查找对应的分类持仓金额
    const categoryPosition = props.categoryPositions.find(item => item.categoryName === asset.name) || { positionAmount: 0 };
    // 确保currentAmount是数值类型
    const currentAmount = typeof categoryPosition.positionAmount === 'number' ? categoryPosition.positionAmount : 0;
    
    // 计算实际比例
    const actualRatio = totalAmount.value > 0 ? (currentAmount / totalAmount.value * 100).toFixed(2) : 0;
    
    // 计算预期金额
    const expectedAmount = totalAmount.value * (asset.ratio / 100);
    
    // 计算相差金额（预期金额 - 当前金额）
    const diffAmount = expectedAmount - currentAmount;
    
    return {
      ...asset,
      currentAmount,
      actualRatio,
      expectedAmount,
      diffAmount
    };
  });
});

// 资产类型标签映射
const assetTypeLabels = {
  stock: '股票',
  bond: '债券',
  gold: '黄金',
  cash: '现金',
  other: '其他'
};

// 资产类型标签类型映射
const assetTypeTagTypes = {
  stock: 'success',
  bond: 'info',
  gold: 'warning',
  cash: 'primary',
  other: 'default'
};

// 获取资产类型标签
const getAssetTypeLabel = (type) => {
  return assetTypeLabels[type] || type;
};

// 获取资产类型标签类型
const getAssetTypeTagType = (type) => {
  return assetTypeTagTypes[type] || 'default';
};
</script>

<style scoped>
.config-preview {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-preview :deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-preview-content {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chart-section {
  height: 350px;
  background-color: #fafafa;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.detail-section {
  background-color: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
  border-left: 4px solid #409EFF;
  padding-left: 12px;
}

.empty-detail {
  text-align: center;
  padding: 40px 0;
}

.ratio-bar-container {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 0;
}

.ratio-bar {
  height: 10px;
  border-radius: 5px;
  transition: width 0.3s ease;
}

.ratio-text {
  font-weight: 500;
  min-width: 50px;
  text-align: right;
}

.color-preview {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-box {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
}

.color-value {
  font-size: 12px;
  color: #606266;
  font-family: monospace;
}

.description-section {
  background-color: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.config-description {
  background-color: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  line-height: 1.6;
}

.asset-list {
  margin: 12px 0;
  padding-left: 20px;
}

.asset-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.asset-color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.asset-item-name {
  flex: 1;
  font-weight: 500;
}

.asset-item-ratio {
  font-weight: 600;
  color: #409EFF;
}

/* 相差金额样式 */
.diff-positive {
  color: #F56C6C;
  font-weight: 500;
}

.diff-negative {
  color: #67C23A;
  font-weight: 500;
}

/* 总金额样式 */
.total-amount {
  margin-top: 16px;
  text-align: right;
  font-size: 14px;
  color: #303133;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chart-section {
    height: 300px;
  }
  
  .section-title {
    font-size: 14px;
  }
}
</style>