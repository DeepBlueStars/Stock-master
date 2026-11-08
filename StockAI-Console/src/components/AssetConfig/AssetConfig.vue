<template>
  <div class="overview">
    <div class="ov-head">
      <div class="ov-title">
        <h1>总览</h1>
        <p>把资产按目标比例摆好，右边随时看结构对不对。</p>
      </div>
      <div class="ov-meta">
        <span class="ov-meta-label">资产类别</span>
        <span class="ov-meta-value num">{{ assets.length }}</span>
      </div>
    </div>

    <div class="sheet">
      <section class="pane pane--adjust" aria-label="资产比例调整">
        <ratio-adjuster
          ref="adjusterRef"
          v-model:assets="assets"
          @ratio-change="handleRatioChange"
          @add-asset="handleAddAsset"
          @edit-asset="handleEditAsset"
          @delete-asset="handleDeleteAsset"
        />
      </section>

      <section class="pane pane--preview" aria-label="配置预览">
        <config-preview :assets="assets" :category-positions="categoryPositions" @save-config="handleSaveConfig" />
      </section>
    </div>

    <div v-if="policy" class="policy">
      <div class="policy-head">
        <span class="policy-title">策略建议</span>
        <span class="policy-regime">
          {{ regimeLabel(policy.effectiveRegime) }}
          <span v-if="!policy.confirmed" class="policy-warn">未连续 {{ policy.confirmDays }} 日确认，暂按震荡档</span>
        </span>
        <span class="policy-note">按市场状态给的目标比例，类别和你左侧配置一致</span>
      </div>
      <p class="policy-targets">
        <span class="k">{{ regimeLabel(policy.effectiveRegime) }}档目标</span>
        <span v-for="row in policy.rows" :key="row.category" class="item">
          {{ row.category }} {{ row.target.toFixed(0) }}%
        </span>
      </p>
      <ul v-if="policy.breaches.length" class="policy-list">
        <li v-for="item in policy.breaches" :key="item.category" :class="item.status === 'OVER' ? 'is-over' : 'is-under'">
          {{ item.text }}
        </li>
      </ul>
      <p v-else class="policy-ok">各类资产都在带宽内，不用调。</p>

      <div class="policy-actions">
        <el-button size="small" :loading="applying" @click="applyPolicy">
          按{{ regimeLabel(policy.effectiveRegime) }}档写成方案
        </el-button>
        <span class="policy-hint">会另存一份新方案并切换过去，原来那份不会动</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import RatioAdjuster from './RatioAdjuster.vue';
import ConfigPreview from './ConfigPreview.vue';
import axios from 'axios';
import config from '../../config/config';

// 使用配置文件中的API基础地址
const BASE_URL = config.baseUrl;

// 初始为空：真实配置由 RatioAdjuster 从 /config/get 拉取。
// 曾经这里写死 50/30/10/10，接口一失败页面就会显示一套不存在的配置。
const assets = ref([]);

// 资产分类持仓金额数据
const categoryPositions = ref([]);

// 策略建议（按市场状态给的目标比例 + 超配低配）
const policy = ref(null);

const REGIME_LABELS = {
  BULL: '牛市',
  SIDEWAYS: '震荡',
  BEAR: '熊市',
  RISK: '风险规避'
};

const regimeLabel = (regime) => REGIME_LABELS[regime] || regime;

/* 「策略建议」→「我的配置」：把这档的目标比例写成一份新方案，原来那份不动 */
const adjusterRef = ref(null);
const applying = ref(false);

const applyPolicy = async () => {
  if (!policy.value || applying.value) return;
  const label = regimeLabel(policy.value.effectiveRegime);
  const targets = policy.value.rows
    .filter((row) => Number(row.target) > 0)
    .map((row) => ({ category: row.category, target: Number(row.target) }));
  const detail = targets.map((t) => `${t.category} ${t.target}%`).join(' · ');

  try {
    await ElMessageBox.confirm(
      `将新建一份「${label}档」方案：${detail}。原来那份配置不会被修改。`,
      `按${label}档写成方案`,
      { confirmButtonText: '新建并切换', cancelButtonText: '算了', type: 'info' }
    );
  } catch (e) {
    return;
  }

  applying.value = true;
  try {
    const result = await adjusterRef.value?.applyTargets(targets, `${label}档 ${today()}`);
    if (result?.ok) {
      ElMessage.success(`已新建「${label}档」方案并切换`);
      await fetchPolicy();
    } else {
      ElMessage.error(result?.message || '方案保存失败');
    }
  } catch (error) {
    console.error('写入方案失败:', error);
    ElMessage.error('写入方案失败，请稍后重试');
  } finally {
    applying.value = false;
  }
};

const today = () => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

const fetchPolicy = async () => {
  try {
    const response = await axios.post(`${BASE_URL}/policy/summary`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    if (response.data.state === 0) {
      policy.value = response.data.data;
    } else {
      console.error('获取策略建议失败:', response.data.message);
    }
  } catch (error) {
    // 后端还没升级时这里会 404，静默即可，不影响上面的配置编辑
    console.warn('策略建议接口不可用:', error?.message);
  }
};

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

onMounted(() => {
  fetchAssetCategoryPositions();
  fetchPolicy();
});

const handleAddAsset = (asset) => {
  console.log('添加资产:', asset);
  adjustRatiosAfterAdd();
};

const handleEditAsset = (asset) => {
  console.log('编辑资产:', asset);
};

const handleDeleteAsset = (index) => {
  console.log('删除资产:', index);
  adjustRatiosAfterDelete(index);
};

const handleRatioChange = (newAssets) => {
  console.log('比例变化:', newAssets);
};

const handleSaveConfig = (config) => {
  const configWithId = { ...config, id: Date.now().toString() };
  const savedConfigsList = getSavedConfigs();
  savedConfigsList.push(configWithId);
  localStorage.setItem('assetConfigs', JSON.stringify(savedConfigsList));
  ElMessage.success('配置方案保存成功');
};

const getSavedConfigs = () => {
  const saved = localStorage.getItem('assetConfigs');
  return saved ? JSON.parse(saved) : [];
};

const adjustRatiosAfterAdd = () => {
  if (assets.value.length > 1) {
    const avgRatio = 100 / assets.value.length;
    assets.value.forEach((asset, index) => {
      assets.value[index].ratio = avgRatio;
    });
  } else {
    assets.value[0].ratio = 100;
  }
};

const adjustRatiosAfterDelete = (deletedIndex) => {
  if (assets.value.length > 0) {
    const deletedRatio = assets.value[deletedIndex].ratio;
    const avgAdjustment = deletedRatio / assets.value.length;
    assets.value.forEach((asset, index) => {
      if (index !== deletedIndex) {
        assets.value[index].ratio = Math.min(100, asset.ratio + avgAdjustment);
      }
    });
  }
};
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  max-width: var(--page-max);
  min-height: calc(100vh - 56px);
  margin: 0 auto;
  padding: 32px 32px 48px;
}

.ov-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.ov-title h1 {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--ink);
}

.ov-title p {
  margin-top: 5px;
  font-size: var(--t-13);
  color: var(--ink-3);
  letter-spacing: 0.01em;
}

.ov-meta {
  display: flex;
  align-items: baseline;
  gap: 7px;
  padding-top: 4px;
  white-space: nowrap;
}

.ov-meta-label {
  font-size: var(--t-12);
  color: var(--ink-3);
}

.ov-meta-value {
  font-size: var(--t-17);
  font-weight: 600;
  color: var(--ink);
}

.sheet {
  display: grid;
  grid-template-columns: minmax(0, 42%) minmax(0, 1fr);
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-sheet);
  overflow: hidden;
}

.pane {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.pane--adjust {
  background: var(--surface-sunken);
}

.pane--preview {
  background: var(--surface);
}

.policy {
  margin-top: 16px;
  padding: 16px 20px;
  background: var(--surface);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sheet);
}

.policy-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.policy-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.policy-regime {
  font-size: 12px;
  color: var(--accent-ink);
  padding: 1px 8px;
  border: 1px solid var(--accent-line);
  border-radius: 4px;
}

.policy-warn {
  margin-left: 8px;
  color: var(--warn);
  border: 0;
  padding: 0;
}

.policy-note {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-3);
}

.policy-targets {
  margin: 10px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 12px;
  color: var(--ink-2);
}

.policy-targets .k {
  color: var(--ink-3);
}

.policy-targets .num,
.policy-targets .item {
  font-variant-numeric: tabular-nums;
}

.policy-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 4px;
}

.policy-list li {
  font-size: 12px;
}

.policy-list li.is-over {
  color: var(--danger);
}

.policy-list li.is-under {
  color: var(--ink-2);
}

.policy-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.policy-hint {
  font-size: 11px;
  color: var(--ink-3);
}

.policy-ok {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--ink-2);
}

@media (max-width: 720px) {
  .policy-note {
    margin-left: 0;
    width: 100%;
  }
}

@media (max-width: 1120px) {
  .sheet {
    grid-template-columns: 1fr;
  }
  .pane--adjust {
    border-bottom: 1px solid var(--rule);
  }
}

@media (max-width: 720px) {
  .overview {
    padding: 20px 16px 32px;
  }
  .ov-title h1 {
    font-size: 19px;
  }
  .ov-meta {
    display: none;
  }
}
</style>
