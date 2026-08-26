<template>
  <div class="asset-config-container">
    <!-- 主内容区域 -->
    <el-container class="main-container">
      <!-- 左侧：资产类型管理和比例调整 -->
      <el-aside class="left-panel">
        <div class="panel-content">
          <!-- 资产比例调整（含类型管理功能） -->
          <div class="panel-section">
            <ratio-adjuster 
              v-model:assets="assets" 
              @ratio-change="handleRatioChange"
              @add-asset="handleAddAsset"
              @edit-asset="handleEditAsset"
              @delete-asset="handleDeleteAsset"
            ></ratio-adjuster>
          </div>
        </div>
      </el-aside>

      <!-- 右侧：配置预览 -->
      <el-main class="right-panel">
        <config-preview 
          :assets="assets" 
          :category-positions="categoryPositions"
          @save-config="handleSaveConfig"
        ></config-preview>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import RatioAdjuster from './RatioAdjuster.vue';
import ConfigPreview from './ConfigPreview.vue';
import axios from 'axios';
import config from '../../config/config';

// 使用配置文件中的API基础地址
const BASE_URL = config.baseUrl;

// 资产配置数据
const assets = ref([
  {
    id: '1',
    name: '股票',
    type: 'stock',
    ratio: 50,
    color: '#67C23A'
  },
  {
    id: '2',
    name: '债券',
    type: 'bond',
    ratio: 30,
    color: '#409EFF'
  },
  {
    id: '3',
    name: '黄金',
    type: 'gold',
    ratio: 10,
    color: '#E6A23C'
  },
  {
    id: '4',
    name: '现金',
    type: 'cash',
    ratio: 10,
    color: '#F56C6C'
  }
]);

// 资产分类持仓金额数据
const categoryPositions = ref([]);

// 获取资产分类持仓金额
const fetchAssetCategoryPositions = async () => {
  try {
    const response = await axios.post(`${BASE_URL}/stock/assetCategoryPosition`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    if (response.data.state === 0) {
      categoryPositions.value = response.data.data;
    } else {
      console.error('获取资产分类持仓金额失败:', response.data.message);
    }
  } catch (error) {
    console.error('获取资产分类持仓金额出错:', error);
  }
};

// 组件挂载时获取数据
onMounted(() => {
  fetchAssetCategoryPositions();
});

// 处理添加资产
const handleAddAsset = (asset) => {
  console.log('添加资产:', asset);
  // 可以在这里添加额外的逻辑，如初始化比例
  adjustRatiosAfterAdd();
};

// 处理编辑资产
const handleEditAsset = (asset) => {
  console.log('编辑资产:', asset);
};

// 处理删除资产
const handleDeleteAsset = (index) => {
  console.log('删除资产:', index);
  adjustRatiosAfterDelete(index);
};

// 处理比例变化
const handleRatioChange = (newAssets) => {
  console.log('比例变化:', newAssets);
};

// 处理保存配置
const handleSaveConfig = (config) => {
  console.log('保存配置:', config);
  
  // 生成唯一ID
  const configWithId = {
    ...config,
    id: Date.now().toString()
  };
  
  // 保存到localStorage
  const savedConfigsList = getSavedConfigs();
  savedConfigsList.push(configWithId);
  localStorage.setItem('assetConfigs', JSON.stringify(savedConfigsList));
  
  ElMessage.success('配置方案保存成功');
};

// 获取保存的配置
const getSavedConfigs = () => {
  const saved = localStorage.getItem('assetConfigs');
  return saved ? JSON.parse(saved) : [];
};

// 添加资产后调整比例
const adjustRatiosAfterAdd = () => {
  if (assets.value.length > 1) {
    // 计算平均比例
    const avgRatio = 100 / assets.value.length;
    
    // 调整所有资产比例
    assets.value.forEach((asset, index) => {
      assets.value[index].ratio = avgRatio;
    });
  } else {
    // 只有一种资产，设为100%
    assets.value[0].ratio = 100;
  }
};

// 删除资产后调整比例
const adjustRatiosAfterDelete = (deletedIndex) => {
  if (assets.value.length > 0) {
    // 计算需要重新分配的比例
    const deletedRatio = assets.value[deletedIndex].ratio;
    const avgAdjustment = deletedRatio / assets.value.length;
    
    // 调整剩余资产比例
    assets.value.forEach((asset, index) => {
      if (index !== deletedIndex) {
        assets.value[index].ratio = Math.min(100, asset.ratio + avgAdjustment);
      }
    });
  }
};
</script>

<style scoped>
.asset-config-container {
  width: 100%;
  height: 100%;
  min-height: 100%;
  background-color: #f5f7fa;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 24px;
  text-align: center;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.page-description {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.main-container {
  height: 100%;
  min-height: 100%;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: row;
}

.left-panel {
  width: 400px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  height: 100%;
  min-height: 100%;
}

.panel-content {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
  box-sizing: border-box;
}

.panel-section {
  flex: 0 0 auto;
}

.right-panel {
  padding: 20px;
  overflow-y: auto;
  height: 100%;
  min-height: 100%;
  box-sizing: border-box;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .left-panel {
    width: 350px;
  }
  
  .main-container {
    flex-direction: column;
  }
  
  .left-panel {
    width: 100%;
    height: auto;
    min-height: 400px;
    max-height: 50%;
    border-right: none;
    border-bottom: 1px solid #e4e7ed;
  }
  
  .right-panel {
    height: auto;
    min-height: 50%;
  }
}

@media (max-width: 768px) {
  .asset-config-container {
    padding: 10px;
  }
  
  .page-header h1 {
    font-size: 24px;
  }
  
  .panel-content {
    padding: 12px;
  }
  
  .right-panel {
    padding: 12px;
  }
}
</style>