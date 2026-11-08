<template>
  <div class="preview">
    <div class="pane-head">
      <h2 class="pane-title">配置结构</h2>
      <span class="pane-note">{{ assets.length }} 类资产 · 当前市值 {{ money(totalAmount) }}</span>
    </div>

    <allocation-ribbon v-if="totalAmount > 0" :assets="currentAssets" :total="100" label="当前比例" />

    <div class="statement">
      <div class="statement-bar">
        <h3>资产明细</h3>
        <span class="statement-note">金额按当前总市值折算</span>
      </div>

      <el-empty v-if="assetDetails.length === 0" description="还没有资产，先在左边加一类" :image-size="72" />

      <table v-else class="ledger">
        <thead>
          <tr>
            <th class="c-name">资产</th>
            <th class="c-num">当前比例</th>
            <th class="c-num">目标金额</th>
            <th class="c-num">差额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in assetDetails" :key="row.id">
            <td class="c-name">
              <span class="swatch" :style="{ background: row.color }" aria-hidden="true"></span>
              <span class="name">{{ row.name }}</span>
              <span v-if="getAssetTypeLabel(row.type) !== row.name" class="type">{{ getAssetTypeLabel(row.type) }}</span>
            </td>
            <td class="c-num num">{{ formatRatio(row.actualRatio) }}<span class="unit">%</span></td>
            <td class="c-num num">{{ money(row.expectedAmount) }}</td>
            <td class="c-num num" :class="diffClass(row.diffAmount)">{{ signed(row.diffAmount) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td class="c-name">合计</td>
            <td class="c-num num">{{ totalAmount > 0 ? '100' : '0' }}<span class="unit">%</span></td>
            <td class="c-num num">{{ money(totalAmount) }}</td>
            <td class="c-num num muted">—</td>
          </tr>
        </tfoot>
      </table>

      <p v-if="assetDetails.length > 0 && totalAmount === 0" class="foot-hint">
        还没拿到持仓金额，金额与差额先按 0 显示。接入行情后这里会自动折算。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import AllocationRibbon from './AllocationRibbon.vue';

const props = defineProps({
  assets: { type: Array, required: true, default: () => [] },
  categoryPositions: { type: Array, required: true, default: () => [] }
});

defineEmits(['save-config']);

const totalAmount = computed(() => {
  const sum = props.categoryPositions.reduce((acc, item) => acc + (item.positionAmount || 0), 0);
  return typeof sum === 'number' ? sum : 0;
});

// 类别口径以左侧配置为准：后端已把基金等未单独配置的持仓并入配置类别，
// 这里不再补额外行，避免明细和左侧滑杆、策略建议对不上
const assetDetails = computed(() =>
  props.assets.map((asset) => {
    const position = props.categoryPositions.find((item) => item.categoryName === asset.name) || {
      positionAmount: 0
    };
    const currentAmount = typeof position.positionAmount === 'number' ? position.positionAmount : 0;
    const actualRatio = totalAmount.value > 0 ? (currentAmount / totalAmount.value) * 100 : 0;
    const expectedAmount = totalAmount.value * (asset.ratio / 100);
    return {
      ...asset,
      currentAmount,
      actualRatio,
      expectedAmount,
      diffAmount: expectedAmount - currentAmount
    };
  })
);

// 色带和明细都以当前持仓占比为准，目标比例只用于折算目标金额和差额
const currentAssets = computed(() =>
  assetDetails.value.map((row) => ({
    id: row.id,
    name: row.name,
    ratio: row.actualRatio,
    color: row.color
  }))
);

const assetTypeLabels = { stock: '股票', fund: '基金', bond: '债券', gold: '黄金', cash: '现金', other: '其他' };
const getAssetTypeLabel = (type) => assetTypeLabels[type] || type;

const nf = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const money = (n) => nf.format(Number(n) || 0);
const formatRatio = (n) => {
  const v = Number(n) || 0;
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
};
const signed = (n) => {
  const v = Number(n) || 0;
  if (Math.abs(v) < 0.005) return '0.00';
  return (v > 0 ? '+' : '−') + nf.format(Math.abs(v));
};
const diffClass = (n) => {
  const v = Number(n) || 0;
  if (Math.abs(v) < 0.005) return 'is-flat';
  return v > 0 ? 'is-up' : 'is-down';
};
</script>

<style scoped>
.preview {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.pane-head {
  border-bottom: 1px solid var(--rule);
}

/* ---------- statement ---------- */
.statement {
  margin-top: 22px;
  padding: 0 24px 22px;
}

.statement-bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 9px;
}

.statement-bar h3 {
  font-size: var(--t-13);
  font-weight: 600;
  color: var(--ink);
  letter-spacing: 0.02em;
}

.statement-note {
  font-size: var(--t-11);
  color: var(--ink-3);
}

.ledger {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.ledger th {
  font-size: 11px;
  font-weight: 500;
  color: var(--ink-3);
  letter-spacing: 0.06em;
  text-align: left;
  padding: 0 0 8px;
  border-bottom: 1px solid var(--rule-strong);
  white-space: nowrap;
}

.ledger td {
  font-size: var(--t-13);
  color: var(--ink);
  padding: 13px 0;
  border-bottom: 1px solid var(--rule);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ledger tbody tr:hover td {
  background: color-mix(in srgb, var(--ink) 3%, transparent);
}

.ledger .c-name {
  width: 34%;
  padding-left: 2px;
}

.ledger .c-num {
  width: 22%;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.ledger th.c-num {
  padding-right: 1px;
}

.swatch {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  margin-right: 8px;
  vertical-align: 0px;
}

.name {
  font-weight: 500;
  letter-spacing: 0.01em;
}

.type {
  margin-left: 7px;
  font-size: var(--t-11);
  color: var(--ink-4);
}

.unit {
  font-size: 10px;
  color: var(--ink-3);
  margin-left: 1px;
}

.muted {
  color: var(--ink-3);
}

.is-up {
  color: var(--accent-ink);
  font-weight: 600;
}

.is-down {
  color: var(--danger);
  font-weight: 600;
}

.is-flat {
  color: var(--ink-4);
}

.ledger tfoot td {
  border-bottom: 0;
  border-top: 1px solid var(--rule-strong);
  padding: 12px 0 0;
  font-weight: 600;
  color: var(--ink);
}

.ledger tfoot .c-name {
  font-size: var(--t-12);
  font-weight: 500;
  color: var(--ink-3);
  letter-spacing: 0.04em;
}

.foot-hint {
  margin-top: 14px;
  font-size: var(--t-11);
  line-height: 1.7;
  color: var(--ink-3);
  max-width: 46ch;
}

@media (max-width: 1120px) {
  .pane-head {
    border-bottom: 0;
  }
}
</style>
