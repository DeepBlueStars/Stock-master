<template>
  <div class="ribbon">
    <div class="ribbon-head">
      <span class="ribbon-label">{{ label }}</span>
      <span class="ribbon-total" :class="{ 'is-off': !isBalanced }">
        <span class="num">{{ format(totalRatio) }}</span
        ><span class="pct">%</span>
        <span v-if="!isBalanced" class="ribbon-flag">{{
          totalRatio > 100 ? '超出 100%，请下调' : '未满 100%，还有 ' + format(100 - totalRatio) + '% 未分配'
        }}</span>
      </span>
    </div>

    <div
      class="ribbon-bar"
      :class="{ 'is-ready': ready }"
      role="img"
      :aria-label="ariaLabel"
    >
      <div
        v-for="(seg, i) in segments"
        :key="seg.key"
        class="seg"
        :class="{ 'seg--empty': seg.empty }"
        :style="{ flexGrow: seg.value, background: seg.empty ? undefined : seg.color, '--i': i }"
        :title="`${seg.name} ${format(seg.value)}%`"
      ></div>
    </div>

    <div class="ribbon-scale" aria-hidden="true">
      <span v-for="t in ticks" :key="t" class="tick" :style="{ left: t + '%' }">
        <i></i>
        <b class="num">{{ t }}</b>
      </span>
    </div>

    <!-- 图例放在带子外面：窄段（黄金、现金常见 10%）在 46px 高的色块里塞不下两个字加数字 -->
    <ul class="ribbon-legend">
      <li v-for="seg in segments" :key="seg.key" class="legend-item">
        <i class="swatch" :class="{ 'swatch--empty': seg.empty }" :style="{ background: seg.empty ? undefined : seg.color }"></i>
        <span class="name">{{ seg.name }}</span>
        <span class="value num">{{ format(seg.value) }}<span class="unit">%</span></span>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const props = defineProps({
  assets: { type: Array, required: true, default: () => [] },
  total: { type: Number, default: 0 },
  label: { type: String, default: '目标比例' }
})

const ticks = [0, 25, 50, 75, 100]

const totalRatio = computed(() => Number(props.total) || 0)
const isBalanced = computed(() => Math.abs(totalRatio.value - 100) < 0.05)

const format = (n) => {
  const v = Number(n) || 0
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

const segments = computed(() => {
  const list = props.assets
    .filter((a) => Number(a.ratio) > 0)
    .map((a) => ({ key: a.id ?? a.name, name: a.name, value: Number(a.ratio), color: a.color }))

  const rest = 100 - totalRatio.value
  if (rest > 0.05) {
    list.push({ key: '__unallocated', name: '未分配', value: rest, empty: true })
  }
  return list
})

const ariaLabel = computed(
  () =>
    `${props.label}构成：` +
    props.assets.map((a) => `${a.name} ${format(a.ratio)}%`).join('，') +
    `，合计 ${format(totalRatio.value)}%`
)

/* 只在第一次进页面时让这条带子从左边铺开一次，之后改数字只做长度过渡 */
const ready = ref(false)
onMounted(() => {
  requestAnimationFrame(() => requestAnimationFrame(() => (ready.value = true)))
})
</script>

<style scoped>
.ribbon {
  padding: 18px 24px 0;
}

.ribbon-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.ribbon-label {
  font-size: var(--t-12);
  color: var(--ink-3);
  letter-spacing: 0.04em;
}

.ribbon-total {
  display: flex;
  align-items: baseline;
  gap: 1px;
  font-size: var(--t-17);
  font-weight: 600;
  color: var(--ink);
  letter-spacing: -0.01em;
}

.ribbon-total .pct {
  font-size: var(--t-12);
  color: var(--ink-3);
  font-weight: 500;
  margin-right: 8px;
}

.ribbon-total.is-off .num,
.ribbon-total.is-off .pct {
  color: var(--warn);
}

.ribbon-flag {
  font-size: var(--t-11);
  font-weight: 500;
  color: var(--warn);
  background: var(--warn-wash);
  border-radius: 4px;
  padding: 1px 7px;
  letter-spacing: 0.01em;
}

/* ---- the bar ---- */
.ribbon-bar {
  display: flex;
  height: 34px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--surface-sunken);
  clip-path: inset(0 100% 0 0);
  transition: clip-path 900ms var(--ease);
}

.ribbon-bar.is-ready {
  clip-path: inset(0 0 0 0);
}

/* 段与段之间用 1px 纸色细缝分隔，而不是留 2px 空隙——
   空隙会让相邻两段的宽度看起来对不上，还浪费 4×2px 的长度 */
.seg {
  position: relative;
  flex-basis: 0;
  min-width: 2px;
  overflow: hidden;
  transition: flex-grow 620ms var(--ease);
}

.seg + .seg {
  box-shadow: inset 1px 0 0 var(--surface);
}

.dark .seg + .seg {
  box-shadow: inset 1px 0 0 #101315;
}

.seg--empty {
  background: repeating-linear-gradient(
    -45deg,
    var(--rule-strong) 0 1px,
    transparent 1px 6px
  );
}

/* ---- scale ---- */
.ribbon-scale {
  position: relative;
  height: 18px;
  margin-top: 5px;
}

.tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.tick:first-child {
  transform: translateX(0);
  align-items: flex-start;
}

.tick:last-child {
  transform: translateX(-100%);
  align-items: flex-end;
}

.tick i {
  display: block;
  width: 1px;
  height: 3px;
  background: var(--rule-strong);
}

.tick b {
  font-size: 10px;
  font-weight: 500;
  color: var(--ink-3);
  letter-spacing: 0.02em;
}

/* ---- legend ---- */
.ribbon-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--t-12);
}

.legend-item .swatch {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex: none;
}

.swatch--empty {
  border: 1px dashed var(--rule-strong);
  background: transparent;
}

.legend-item .name {
  color: var(--ink-2);
}

.legend-item .value {
  color: var(--ink);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.legend-item .unit {
  font-size: 10px;
  font-weight: 500;
  color: var(--ink-3);
  margin-left: 1px;
}
</style>
