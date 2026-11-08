<template>
  <div class="adjuster">
    <div class="pane-head">
      <h2 class="pane-title">资产比例</h2>
      <div class="total" :class="{ 'is-off': totalRatio !== 100 }">
        <span class="total-num num">{{ formatRatio(totalRatio) }}</span>
        <span class="total-pct">%</span>
        <el-tooltip
          v-if="totalRatio !== 100"
          content="各资产比例相加必须正好 100%"
          placement="top"
        >
          <el-icon class="warn"><ElIconWarning /></el-icon>
        </el-tooltip>
      </div>
    </div>

    <div class="rows">
      <div v-for="(asset, index) in localAssets" :key="asset.id" class="row">
        <div class="row-main">
          <span class="dot" :style="{ background: asset.color }" aria-hidden="true"></span>
          <span class="row-name">{{ asset.name }}</span>
          <span v-if="typeLabel(asset.type) !== asset.name" class="row-type">{{ typeLabel(asset.type) }}</span>

          <div class="row-tools">
            <button type="button" class="tool" title="编辑" aria-label="编辑资产" @click="handleEditAsset(asset)">
              <el-icon><ElIconEdit /></el-icon>
            </button>
            <button
              type="button"
              class="tool tool--danger"
              title="删除"
              aria-label="删除资产"
              :disabled="localAssets.length <= 1 || asset.type === 'cash'"
              @click="handleDeleteAsset(index)"
            >
              <el-icon><ElIconDelete /></el-icon>
            </button>
          </div>

          <div class="row-value">
            <el-input-number
              v-model="localAssets[index].ratio"
              :min="0"
              :max="100"
              :step="0.1"
              :precision="1"
              :controls="false"
              size="small"
              @change="handleRatioChange(index)"
            />
            <span class="pct">%</span>
          </div>
        </div>

        <el-slider
          v-model="localAssets[index].ratio"
          :min="0"
          :max="100"
          :step="0.1"
          :show-tooltip="false"
          class="row-slider"
          @change="handleRatioChange(index)"
        />
      </div>

      <el-empty v-if="localAssets.length === 0" description="还没有资产类别" :image-size="64" />

      <button type="button" class="add-row" @click="showAddDialog = true">
        <el-icon><ElIconPlus /></el-icon>
        添加资产类别
      </button>
    </div>

    <div class="foot">
      <span class="foot-label">已保存的方案</span>
      <el-select
        v-model="selectedConfigId"
        placeholder="载入一个方案"
        size="default"
        class="foot-select"
        @change="handleLoadConfig"
      >
        <el-option
          v-for="item in savedConfigs"
          :key="item.id"
          :label="`${item.name} (${new Date(item.createdAt).toLocaleString()})`"
          :value="item.id"
        >
          <div class="option-content">
            <div class="option-name">{{ item.name }}</div>
            <div class="option-desc">{{ item.description || '无描述' }}</div>
            <div class="option-time num">{{ new Date(item.createdAt).toLocaleString() }}</div>
          </div>
        </el-option>
      </el-select>
      <div class="foot-actions">
        <el-button :disabled="!selectedConfigId" @click="handleUpdateConfig">更新此方案</el-button>
        <el-button type="primary" :disabled="localAssets.length === 0" @click="handleSaveConfig">另存为新方案</el-button>
      </div>
    </div>

    <!-- 添加/编辑资产 -->
    <el-dialog
      v-model="showAddDialog"
      :title="editingAsset ? '编辑资产' : '添加资产'"
      width="400px"
    >
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="资产名称" prop="name">
          <el-input v-model="formData.name" placeholder="例如：美股"></el-input>
        </el-form-item>

        <el-form-item label="资产类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择资产类型">
            <el-option label="股票" value="stock"></el-option>
            <el-option label="基金" value="fund"></el-option>
            <el-option label="债券" value="bond"></el-option>
            <el-option label="黄金" value="gold"></el-option>
            <el-option label="现金" value="cash"></el-option>
            <el-option label="其他" value="other"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="资产颜色" prop="color">
          <div class="color-picker-container">
            <el-color-picker v-model="formData.color" show-alpha size="large"></el-color-picker>
            <span class="color-value num">{{ formData.color }}</span>
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

    <!-- 保存配置 -->
    <el-dialog v-model="showSaveDialog" title="保存配置方案" width="400px">
      <el-form :model="saveForm" :rules="saveRules" ref="saveFormRef" label-width="80px">
        <el-form-item label="方案名称" prop="name">
          <el-input v-model="saveForm.name" placeholder="例如：稳健 50/30"></el-input>
        </el-form-item>

        <el-form-item label="方案说明" prop="description">
          <el-input
            v-model="saveForm.description"
            placeholder="一句话说明这个方案的用途"
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
  </div>
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
        localAssets.value = normalizeAssetColors(JSON.parse(JSON.stringify(configJson.assets)));
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
        localAssets.value = normalizeAssetColors(JSON.parse(JSON.stringify(configJson.assets)));
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
  fund: '基金',
  bond: '债券',
  gold: '黄金',
  cash: '现金',
  other: '其他'
};

// 台账配色：与 tokens.css 的 --data-* 对齐
const LEDGER_COLORS = ['#1f3b4d', '#4e7c8a', '#b0802c', '#8f8a7c', '#6b5f7a'];
// 老配置里存的是 Element 默认色，按它原来的语义顺序换成台账色
const LEGACY_COLORS = {
  '#67c23a': 0,
  '#409eff': 1,
  '#e6a23c': 2,
  '#f56c6c': 3
};

// 读盘时顺手把配色迁移到台账色，避免图表和 DESIGN.md 打架
const normalizeAssetColors = (list) => {
  if (!Array.isArray(list)) return [];
  return list.map((asset, index) => {
    const raw = String(asset.color || '').toLowerCase();
    const legacyIndex = LEGACY_COLORS[raw];
    const color = legacyIndex !== undefined
      ? LEDGER_COLORS[legacyIndex]
      : (asset.color || LEDGER_COLORS[index % LEDGER_COLORS.length]);
    return { ...asset, color };
  });
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

/* 类别 → 资产类型，用来给策略建议里「配置还没写过」的类别补 type */
const CATEGORY_TYPES = {
  股票: 'stock',
  基金: 'fund',
  债券: 'bond',
  黄金: 'gold',
  现金: 'cash'
};

/**
 * 按给定目标比例新建一份方案并切换过去（「策略建议」的落地入口）。
 * 已有的同名类别沿用原来的颜色，缺的按台账色板补，保证和左边滑杆配色一致。
 */
const applyTargets = async (targets, name) => {
  const next = [];
  targets
    .filter((item) => Number(item.target) > 0)
    .forEach((item, index) => {
      const existing = localAssets.value.find((asset) => asset.name === item.category);
      next.push({
        id: existing?.id ?? `auto-${Date.now()}-${index}`,
        name: item.category,
        type: existing?.type ?? CATEGORY_TYPES[item.category] ?? 'other',
        ratio: Number(item.target),
        color: existing?.color ?? LEDGER_COLORS[index % LEDGER_COLORS.length]
      });
    });

  if (!next.length) {
    return { ok: false, message: '目标比例为空' };
  }

  const sum = next.reduce((acc, asset) => acc + asset.ratio, 0);
  if (Math.abs(sum - 100) > 0.05) {
    return { ok: false, message: `目标合计 ${sum.toFixed(1)}%，不是 100%` };
  }

  localAssets.value = next;
  emitRatioChange();

  const configData = {
    name,
    description: '由「策略建议」按市场状态生成',
    assets: next,
    totalRatio: 100,
    createdAt: new Date(),
    updatedAt: new Date(),
    id: Date.now().toString()
  };

  try {
    const response = await axios.post(`${BASE_URL}/config/save`, {
      userId: '6928fca14f206ada1b7a9269',
      config: JSON.stringify(configData)
    });
    const res = response.data;
    if (res.state !== 0) {
      return { ok: false, message: res.message || '保存失败' };
    }
    loadConfigs();
    return { ok: true };
  } catch (error) {
    console.error('保存配置失败:', error);
    return { ok: false, message: '接口异常，保存失败' };
  }
};

defineExpose({ applyTargets });

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

// 展示用的小工具
const typeLabels = { stock: '股票', fund: '基金', bond: '债券', gold: '黄金', cash: '现金', other: '其他' };
const typeLabel = (t) => typeLabels[t] || t;
const formatRatio = (n) => {
  const v = Number(n) || 0;
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
};

</script>

<style scoped>
.adjuster {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.pane-head {
  align-items: baseline;
  border-bottom: 1px solid var(--rule);
}

.total {
  display: flex;
  align-items: baseline;
  gap: 1px;
  color: var(--ink);
}

.total-num {
  font-size: var(--t-17);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.total-pct {
  font-size: var(--t-12);
  color: var(--ink-3);
  font-weight: 500;
}

.total.is-off .total-num,
.total.is-off .total-pct {
  color: var(--warn);
}

.warn {
  margin-left: 6px;
  font-size: 13px;
  color: var(--warn);
  cursor: help;
}

/* ---------- rows ---------- */
.rows {
  flex: none;
}

.row {
  padding: 13px 24px 10px;
  border-bottom: 1px solid var(--rule);
  transition: background-color var(--dur-2) var(--ease);
}

.row:hover {
  background: color-mix(in srgb, var(--ink) 2.5%, transparent);
}

.row-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 22px;
}

.dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 2px;
  transform: rotate(45deg);
}

.row-name {
  font-size: 13.5px;
  font-weight: 500;
  letter-spacing: 0.015em;
  color: var(--ink);
}

.row-type {
  font-size: var(--t-11);
  color: var(--ink-4);
}

.row-tools {
  margin-left: auto;
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--dur-1) var(--ease);
}

.row:hover .row-tools,
.row:focus-within .row-tools {
  opacity: 1;
}

.tool {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink-3);
  font-size: 13px;
  cursor: pointer;
  transition: background-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.tool:hover {
  background: color-mix(in srgb, var(--ink) 7%, transparent);
  color: var(--ink);
}

.tool--danger:hover {
  background: var(--danger-wash);
  color: var(--danger);
}

.tool:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.tool:disabled:hover {
  background: transparent;
  color: var(--ink-3);
}

.row-value {
  display: flex;
  align-items: baseline;
  gap: 1px;
  margin-left: 6px;
}

.row-value :deep(.el-input-number) {
  width: 54px;
}

.row-value :deep(.el-input__wrapper) {
  padding: 0 4px;
  background: transparent;
  box-shadow: 0 0 0 1px transparent inset;
  transition: box-shadow var(--dur-1) var(--ease), background-color var(--dur-1) var(--ease);
}

.row:hover .row-value :deep(.el-input__wrapper),
.row-value :deep(.el-input__wrapper.is-focus) {
  background: var(--surface);
  box-shadow: 0 0 0 1px var(--rule-strong) inset;
}

.row-value :deep(.el-input__inner) {
  text-align: right;
  font-size: 13.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  height: 22px;
}

.pct {
  font-size: var(--t-11);
  color: var(--ink-3);
}

.row-slider {
  margin-top: 2px;
}

.row-slider :deep(.el-slider__runway) {
  height: 2px;
  background: var(--rule-strong);
}

.row-slider :deep(.el-slider__bar) {
  background: var(--ink);
}

.row-slider :deep(.el-slider__button-wrapper) {
  top: -15px;
}

.row-slider :deep(.el-slider__button) {
  width: 10px;
  height: 10px;
  border-width: 2px;
}

.row-slider :deep(.el-slider__button-wrapper:hover .el-slider__button) {
  transform: scale(1.45);
}

/* ---------- add ---------- */
.add-row {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 13px 24px;
  border: 0;
  border-bottom: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-3);
  font-size: 13px;
  letter-spacing: 0.01em;
  text-align: left;
  cursor: pointer;
  transition: color var(--dur-1) var(--ease), background-color var(--dur-1) var(--ease);
}

.add-row:hover {
  color: var(--ink);
  background: color-mix(in srgb, var(--ink) 2.5%, transparent);
}

.add-row .el-icon {
  font-size: 13px;
}

/* ---------- saved configs ---------- */
.foot {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 18px 24px 20px;
}

.foot-label {
  font-size: var(--t-11);
  letter-spacing: 0.06em;
  color: var(--ink-3);
}

.foot-select {
  width: 100%;
}

.foot-actions {
  display: flex;
  gap: 8px;
  margin-top: 2px;
}

.foot-actions .el-button {
  flex: 1;
  margin-left: 0;
}

.option-content {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 4px 0;
  line-height: 1.5;
}

.option-name {
  font-weight: 600;
  color: var(--ink);
}

.option-desc {
  font-size: var(--t-12);
  color: var(--ink-3);
}

.option-time {
  font-size: var(--t-11);
  color: var(--ink-4);
}

.color-picker-container {
  display: flex;
  align-items: center;
  gap: 10px;
}

.color-value {
  font-size: var(--t-12);
  color: var(--ink-3);
  letter-spacing: 0.02em;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 1120px) {
  .pane-head {
    border-bottom: 1px solid var(--rule);
  }
}
</style>
