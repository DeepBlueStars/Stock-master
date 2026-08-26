<template>
  <el-card class="ratio-adjuster">
    <template #header>
      <div class="card-header">
        <span>资产比例调整</span>
        <div class="total-ratio">
          <span class="label">总比例：</span>
          <span 
            class="value" 
            :class="{ 'invalid': totalRatio !== 100 }"
            :title="totalRatio !== 100 ? '总比例必须为100%，请调整资产比例' : ''"
          >
            {{ totalRatio }}%
          </span>
          <el-tooltip 
            v-if="totalRatio !== 100" 
            content="总比例必须为100%，请调整资产比例" 
            placement="top"
            :effect="'warning'"
          >
            <el-icon-warning class="warning-icon"></el-icon-warning>
          </el-tooltip>
        </div>
      </div>
    </template>

    <div class="ratio-adjuster-list">
      <div 
        v-for="(asset, index) in assets" 
        :key="asset.id" 
        class="ratio-adjuster-item"
      >
        <div class="asset-info">
          <div class="asset-color-indicator" :style="{ backgroundColor: asset.color }"></div>
          <div class="asset-name">{{ asset.name }}</div>
          <div class="asset-type">{{ getAssetTypeLabel(asset.type) }}</div>
        </div>

        <div class="ratio-controls">
          <el-slider
            v-model="localAssets[index].ratio"
            :min="0"
            :max="100"
            :step="0.1"
            @change="handleRatioChange(index)"
            :show-stops="true"
            :marks="{
              0: '0%',
              50: '50%',
              100: '100%'
            }"
          ></el-slider>

          <div class="input-container">
            <div class="asset-actions">
              <el-button 
                type="primary" 
                size="small" 
                :icon="ElIconEdit" 
                @click="handleEditAsset(asset)"
                circle
              ></el-button>
              <el-button 
                type="danger" 
                size="small" 
                :icon="ElIconDelete" 
                @click="handleDeleteAsset(index)"
                circle
                :disabled="assets.length <= 1 || asset.type === 'cash'"
                :title="asset.type === 'cash' ? '现金资产不能删除' : ''"
              ></el-button>
            </div>
            <el-input-number
              v-model="localAssets[index].ratio"
              :min="0"
              :max="100"
              :step="0.1"
              @change="handleRatioChange(index)"
              :precision="1"
              size="small"
            >
              <template #append>%</template>
            </el-input-number>
          </div>
        </div>
      </div>

      <div v-if="assets.length === 0" class="empty-tip">
        <el-empty description="暂无资产，请先添加资产类型"></el-empty>
      </div>
      
      <!-- 添加资产按钮 -->
      <div class="add-asset-button" @click="showAddDialog = true">
        <el-icon-plus class="add-icon"></el-icon-plus>
      </div>

      <!-- 加载已保存的配置和保存配置按钮 -->
      <div class="saved-configs-container">
        <!-- 下拉框 -->
        <div class="saved-configs-row">
          <el-select 
            v-model="selectedConfigId" 
            placeholder="加载已保存的配置" 
            size="large"
            @change="handleLoadConfig"
            class="saved-configs-select"
          >
            <el-option 
              v-for="config in savedConfigs" 
              :key="config.id" 
              :label="`${config.name} (${new Date(config.createdAt).toLocaleString()})`" 
              :value="config.id"
            >
              <div class="option-content">
                <div class="option-name">{{ config.name }}</div>
                <div class="option-desc">{{ config.description || '无描述' }}</div>
                <div class="option-time">{{ new Date(config.createdAt).toLocaleString() }}</div>
              </div>
            </el-option>
          </el-select>
        </div>
        
        <!-- 按钮行 -->
        <div class="saved-configs-buttons">
          <el-button 
            type="success" 
            size="large" 
            @click="handleUpdateConfig"
            :disabled="localAssets.length === 0 || !selectedConfigId"
            class="save-config-btn"
          >
            <el-icon-download></el-icon-download> 保存配置
          </el-button>
          <el-button 
            type="primary" 
            size="large" 
            @click="handleSaveConfig"
            :disabled="localAssets.length === 0"
            class="new-config-btn"
          >
            <el-icon-download></el-icon-download> 新建配置
          </el-button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑资产对话框 -->
    <el-dialog
      v-model="showAddDialog"
      :title="editingAsset ? '编辑资产' : '添加资产'"
      width="400px"
    >
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="资产名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入资产名称"></el-input>
        </el-form-item>

        <el-form-item label="资产类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择资产类型">
            <el-option label="股票" value="stock"></el-option>
            <el-option label="债券" value="bond"></el-option>
            <el-option label="黄金" value="gold"></el-option>
            <el-option label="现金" value="cash"></el-option>
            <el-option label="其他" value="other"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="资产颜色" prop="color">
          <div class="color-picker-container">
            <el-color-picker v-model="formData.color" show-alpha size="large"></el-color-picker>
            <span class="color-value">{{ formData.color }}</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button type="primary" @click="handleSaveAsset">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 保存配置对话框 -->
    <el-dialog
      v-model="showSaveDialog"
      title="保存配置方案"
      width="400px"
    >
      <el-form :model="saveForm" :rules="saveRules" ref="saveFormRef" label-width="80px">
        <el-form-item label="方案名称" prop="name">
          <el-input v-model="saveForm.name" placeholder="请输入配置方案名称"></el-input>
        </el-form-item>

        <el-form-item label="方案描述" prop="description">
          <el-input 
            v-model="saveForm.description" 
            placeholder="请输入配置方案描述"
            type="textarea"
            :rows="3"
          ></el-input>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showSaveDialog = false">取消</el-button>
          <el-button type="primary" @click="handleConfirmSave">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue';
import {
  Warning as ElIconWarning,
  Plus as ElIconPlus,
  Edit as ElIconEdit,
  Delete as ElIconDelete,
  Download as ElIconDownload
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';

// 导入配置文件
import config from '../../config/config';

// 使用配置文件中的API基础地址
const BASE_URL = config.baseUrl;

// 保存的配置方案
const savedConfigs = ref([]);
const selectedConfigId = ref('');

// 组件挂载时加载保存的配置
onMounted(() => {
  // 加载用户的所有配置
  loadConfigs();
});

// 加载用户的所有配置
const loadConfigs = async () => {
  try {
    // 获取用户的所有配置列表
    const listResponse = await axios.post(`${BASE_URL}/config/list`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    
    if (listResponse.status === 200 && listResponse.data.state === 0) {
      // 解析配置列表
      const configs = listResponse.data.data;
      savedConfigs.value = configs.map(config => {
        // 将配置字符串解析为JSON对象
        try {
          const configJson = JSON.parse(config.config);
          return {
            id: config.id,
            name: configJson.name,
            description: configJson.description,
            createdAt: configJson.createdAt,
            updatedAt: configJson.updatedAt,
            assets: configJson.assets
          };
        } catch (error) {
          console.error('解析配置失败:', error);
          return {
            id: config.id,
            name: '无效配置',
            description: '配置格式错误',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            assets: []
          };
        }
      });
    }
    
    // 获取当前用户使用的配置
    const currentResponse = await axios.post(`${BASE_URL}/config/get`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    
    if (currentResponse.status === 200 && currentResponse.data.state === 0 && currentResponse.data.data) {
      // 设置当前选中的配置ID
      selectedConfigId.value = currentResponse.data.data.id;
      
      // 解析当前配置内容
      const currentConfig = currentResponse.data.data;
      try {
        const configJson = JSON.parse(currentConfig.config);
        // 更新本地资产列表
        localAssets.value = JSON.parse(JSON.stringify(configJson.assets));
        emitRatioChange();
      } catch (error) {
        console.error('解析当前配置失败:', error);
        ElMessage.error('当前配置格式错误，加载失败');
      }
    }
  } catch (error) {
    console.error('加载配置失败:', error);
    ElMessage.error('加载配置失败，请稍后重试');
  }
};

// 从localStorage加载保存的配置
const loadSavedConfigs = () => {
  // 不再从localStorage加载配置，改为从后端加载
  savedConfigs.value = [];
};

// 获取保存的配置
// 从localStorage获取配置
const getSavedConfigs = () => {
  // 不再从localStorage获取配置，改为从后端获取
  return [];
};

// 处理加载配置
const handleLoadConfig = async (configId) => {
  if (!configId) return;
  
  try {
    // 调用后端接口获取配置详情
    const response = await axios.post(`${BASE_URL}/config/getById`, {
      configId: configId
    });
    
    if (response.status === 200 && response.data.state === 0 && response.data.data) {
      // 解析配置内容
      const config = response.data.data;
      try {
        const configJson = JSON.parse(config.config);
        // 更新本地资产列表
        localAssets.value = JSON.parse(JSON.stringify(configJson.assets));
        emitRatioChange();
        
        // 更新用户当前使用的配置
        await axios.post(`${BASE_URL}/config/updateCurrent`, {
          userId: '6928fca14f206ada1b7a9269',
          configId: configId
        });
        
        ElMessage.success('配置加载成功');
      } catch (error) {
        console.error('解析配置失败:', error);
        ElMessage.error('配置格式错误，加载失败');
      }
    } else {
      ElMessage.error('配置加载失败: ' + (response.data.message || '未知错误'));
    }
  } catch (error) {
    console.error('加载配置失败:', error);
    ElMessage.error('配置加载失败，请稍后重试');
  }
};

const props = defineProps({
  assets: {
    type: Array,
    required: true,
    default: () => []
  }
});

const emit = defineEmits(['update:assets', 'ratio-change', 'add-asset', 'edit-asset', 'delete-asset', 'save-config']);

// 本地资产列表，用于双向绑定
const localAssets = ref([...props.assets]);

// 添加/编辑资产相关状态
const showAddDialog = ref(false);
const editingAsset = ref(null);
const formRef = ref(null);

// 保存配置对话框
const showSaveDialog = ref(false);
const saveFormRef = ref(null);

// 保存表单数据
const saveForm = ref({
  name: '',
  description: ''
});

// 保存表单验证规则
const saveRules = {
  name: [
    { required: true, message: '请输入配置方案名称', trigger: 'blur' },
    { min: 1, max: 50, message: '方案名称长度在 1 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { max: 200, message: '方案描述长度不能超过 200 个字符', trigger: 'blur' }
  ]
};

// 计算总比例
const totalRatio = computed(() => {
  return Number(localAssets.value.reduce((sum, asset) => sum + asset.ratio, 0).toFixed(1));
});

// 监听props变化，更新本地资产列表
watch(
  () => props.assets,
  (newAssets) => {
    localAssets.value = [...newAssets];
  },
  { deep: true }
);

// 资产类型标签映射
const assetTypeLabels = {
  stock: '股票',
  bond: '债券',
  gold: '黄金',
  cash: '现金',
  other: '其他'
};

// 获取资产类型标签
const getAssetTypeLabel = (type) => {
  return assetTypeLabels[type] || type;
};

// 表单数据
const formData = ref({
  name: '',
  type: 'stock',
  color: '#409EFF',
  ratio: 0
});

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入资产名称', trigger: 'blur' },
    { min: 1, max: 20, message: '资产名称长度在 1 到 20 个字符', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择资产类型', trigger: 'change' }
  ],
  color: [
    { required: true, message: '请选择资产颜色', trigger: 'change' }
  ]
};

// 处理比例变化
const handleRatioChange = (changedIndex) => {
  // 计算当前总比例
  const currentTotal = localAssets.value.reduce((sum, asset) => sum + asset.ratio, 0);
  
  // 如果只有一种资产，直接设为100%
  if (localAssets.value.length === 1) {
    localAssets.value[0].ratio = 100;
    emitRatioChange();
    return;
  }
  
  // 计算需要调整的差值
  const diff = currentTotal - 100;
  
  if (Math.abs(diff) < 0.01) {
    // 差值很小，直接返回
    emitRatioChange();
    return;
  }
  
  // 复制一份资产列表，避免直接修改
  const tempAssets = [...localAssets.value];
  const changedAsset = tempAssets[changedIndex];
  const isCashChanged = changedAsset.type === 'cash';
  
  // 找到现金资产的索引
  const cashIndex = tempAssets.findIndex(asset => asset.type === 'cash');
  
  if (!isCashChanged && cashIndex !== -1) {
    // 调整的是非现金资产，优先调整现金资产
    const cashAsset = tempAssets[cashIndex];
    const newCashRatio = cashAsset.ratio - diff;
    
    if (newCashRatio >= 0) {
      // 现金比例足够调整，只调整现金
      tempAssets[cashIndex].ratio = newCashRatio;
    }
    // 否则，保持原样，总比例会显示无效
  } else {
    // 调整的是现金资产，或者没有现金资产，按比例调整其他资产
    // 排除当前修改的资产和现金资产（如果存在）
    const otherAssets = tempAssets.filter((asset, index) => 
      index !== changedIndex && index !== cashIndex
    );
    const totalOtherRatio = otherAssets.reduce((sum, asset) => sum + asset.ratio, 0);
    
    if (totalOtherRatio > 0) {
      // 按比例分配差值到其他非现金资产
      otherAssets.forEach(asset => {
        const assetIndex = tempAssets.findIndex(a => a.id === asset.id);
        const adjustDiff = (asset.ratio / totalOtherRatio) * diff;
        tempAssets[assetIndex].ratio = Math.max(0, Math.min(100, asset.ratio - adjustDiff));
      });
    } else {
      // 如果其他资产比例都为0，无法调整，保持原样
    }
  }
  
  localAssets.value = tempAssets;
  emitRatioChange();
};

// 向父组件发送比例变化事件
const emitRatioChange = () => {
  emit('update:assets', [...localAssets.value]);
  emit('ratio-change', [...localAssets.value]);
};

// 编辑资产
const handleEditAsset = (asset) => {
  editingAsset.value = asset;
  formData.value = { ...asset };
  showAddDialog.value = true;
};

// 删除资产
const handleDeleteAsset = (index) => {
  if (props.assets.length <= 1) {
    ElMessage.warning('至少需要保留一种资产类型');
    return;
  }

  const assetToDelete = props.assets[index];
  if (assetToDelete.type === 'cash') {
    ElMessage.warning('现金资产是必须存在的，不能删除');
    return;
  }

  ElMessageBox.confirm('确定要删除该资产类型吗？', '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const newAssets = [...props.assets];
    newAssets.splice(index, 1);
    emit('update:assets', newAssets);
    emit('delete-asset', index);
    ElMessage.success('删除成功');
  }).catch(() => {
    // 取消删除
  });
};

// 保存资产
const handleSaveAsset = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      if (editingAsset.value) {
        // 编辑现有资产
        const newAssets = props.assets.map(asset => 
          asset.id === editingAsset.value.id ? { ...formData.value } : asset
        );
        emit('update:assets', newAssets);
        emit('edit-asset', formData.value);
        ElMessage.success('编辑成功');
      } else {
        // 添加新资产
        const newAsset = {
          ...formData.value,
          id: Date.now().toString(),
          ratio: 0
        };
        const newAssets = [...props.assets, newAsset];
        emit('update:assets', newAssets);
        emit('add-asset', newAsset);
        ElMessage.success('添加成功');
      }
      showAddDialog.value = false;
      resetForm();
    }
  });
};

// 重置表单
const resetForm = () => {
  formData.value = {
    name: '',
    type: 'stock',
    color: '#409EFF',
    ratio: 0
  };
  editingAsset.value = null;
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

// 处理保存配置
const handleSaveConfig = () => {
  showSaveDialog.value = true;
};

// 确认保存配置
const handleConfirmSave = async () => {
  saveFormRef.value.validate(async (valid) => {
    if (valid) {
      const configData = {
        ...saveForm.value,
        assets: localAssets.value,
        totalRatio: totalRatio.value,
        createdAt: new Date(),
        updatedAt: new Date(),
        id: Date.now().toString()
      };
      
      try {
        // 调用后端API保存配置
        const response = await axios.post(`${BASE_URL}/config/save`, {
          userId: '6928fca14f206ada1b7a9269',
          config: JSON.stringify(configData)
        });
        
        if (response.status === 200) {
          const res = response.data;
          if (res.state === 0) {
            ElMessage.success('配置保存成功');
            // 重新加载配置列表
            loadConfigs();
          } else {
            ElMessage.error('配置保存失败: ' + (res.message || '未知错误'));
          }
        } else {
          ElMessage.error('配置保存失败: ' + response.statusText);
        }
      } catch (error) {
        console.error('保存配置失败:', error);
        ElMessage.error('配置保存失败，请稍后重试');
      }
      
      showSaveDialog.value = false;
      resetSaveForm();
    }
  });
};

// 处理更新配置
const handleUpdateConfig = async () => {
  if (!selectedConfigId.value) {
    ElMessage.warning('请先选择要保存的配置');
    return;
  }
  
  try {
    // 找到当前选中的配置
    const currentConfig = savedConfigs.value.find(config => config.id === selectedConfigId.value);
    
    // 准备配置数据
    const configData = {
      name: currentConfig ? currentConfig.name : '未命名配置',
      description: currentConfig ? currentConfig.description : '',
      assets: localAssets.value,
      totalRatio: totalRatio.value,
      createdAt: currentConfig ? currentConfig.createdAt : new Date(),
      updatedAt: new Date(),
      id: selectedConfigId.value
    };
    
    // 调用后端接口更新配置
    const response = await axios.post(`${BASE_URL}/config/update`, {
      configId: selectedConfigId.value,
      config: JSON.stringify(configData)
    });
    
    if (response.status === 200) {
      const res = response.data;
      if (res.state === 0) {
        ElMessage.success('配置保存成功');
        // 重新加载配置列表
        loadConfigs();
      } else {
        ElMessage.error('配置保存失败: ' + (res.message || '未知错误'));
      }
    } else {
      ElMessage.error('配置保存失败: ' + response.statusText);
    }
  } catch (error) {
    console.error('保存配置失败:', error);
    ElMessage.error('配置保存失败，请稍后重试');
  }
};

// 重置保存表单
const resetSaveForm = () => {
  saveForm.value = {
    name: '',
    description: ''
  };
  if (saveFormRef.value) {
    saveFormRef.value.resetFields();
  }
};
</script>

<style scoped>
.ratio-adjuster {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-ratio {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.total-ratio .label {
  color: #606266;
}

.total-ratio .value {
  color: #67c23a;
}

.total-ratio .value.invalid {
  color: #f56c6c;
  font-weight: bold;
}

.warning-icon {
  color: #e6a23c;
  margin-left: 8px;
  cursor: help;
  font-size: 16px;
  vertical-align: middle;
}

.ratio-adjuster-list {
  flex: 1;
}

.ratio-adjuster-item {
  margin-bottom: 24px;
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.ratio-adjuster-item:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.asset-info {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  justify-content: space-between;
}

.asset-info-left {
  display: flex;
  align-items: center;
}

.asset-color-indicator {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  margin-right: 8px;
}

.asset-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.asset-type {
  font-size: 12px;
  color: #606266;
  margin-left: 8px;
  padding: 2px 8px;
  background-color: #ecf5ff;
  border-radius: 10px;
}

.input-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-top: 10px;
}

.asset-actions {
  display: flex;
  gap: 8px;
  margin-right: 12px;
  justify-content: flex-start;
  align-items: center;
}

.asset-actions :deep(.el-button) {
  font-size: 14px;
  line-height: 1;
}

.asset-actions :deep(.el-button__icon) {
  font-size: 16px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 20px;
  width: 20px;
}

.add-asset-button {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  margin-top: 16px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #606266;
}

.add-asset-button:hover {
  border-color: #409eff;
  color: #409eff;
  background-color: #ecf5ff;
}

/* 保存的配置容器 */
.saved-configs-container {
  margin-top: 16px;
}

/* 保存的配置行 */
.saved-configs-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

/* 按钮行 */
.saved-configs-buttons {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin: 0 auto;
  max-width: 400px;
}

/* 保存的配置下拉框 */
.saved-configs-select {
  flex: 1;
  max-width: 400px;
  height: 48px;
}

/* 保存配置按钮 */
.save-config-btn {
  flex: 1;
  max-width: calc(50% - 6px);
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 16px;
  min-width: auto;
}

/* 新建配置按钮 */
.new-config-btn {
  flex: 1;
  max-width: calc(50% - 6px);
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 16px;
  min-width: auto;
}

.save-config-btn :deep(.el-button__icon),
.new-config-btn :deep(.el-button__icon) {
  font-size: 16px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 20px;
  width: 20px;
}

/* 下拉选项样式 */
.option-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
}

.option-name {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.option-desc {
  font-size: 12px;
  color: #606266;
}

.option-time {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.add-icon {
  font-size: 16px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  width: 24px;
}

.color-picker-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-value {
  font-size: 12px;
  color: #606266;
  font-family: monospace;
}

.ratio-controls {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-container :deep(.el-input-number) {
  width: 120px;
}

.empty-tip {
  text-align: center;
  padding: 40px 0;
}

.adjust-tip {
  margin-top: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .ratio-adjuster-item {
    padding: 12px;
  }
}
</style>