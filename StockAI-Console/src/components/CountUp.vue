<script setup>
import { onUnmounted, ref, watch } from 'vue'

/**
 * 数字滚动：一次入场动效，不做循环。
 * 金额、比例统一走 format，负号用「−」，正号可选。
 */
const props = defineProps({
  value: { type: Number, default: 0 },
  decimals: { type: Number, default: 0 },
  sign: { type: Boolean, default: false },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  duration: { type: Number, default: 900 },
  grouping: { type: Boolean, default: true }
})

const display = ref('')
let frame = 0

const prefersReduced = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

function format(v) {
  const negative = v < 0
  const showSign = props.sign || negative
  const body = Math.abs(v).toLocaleString('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals,
    useGrouping: props.grouping
  })
  return `${showSign ? (negative ? '−' : '+') : ''}${props.prefix}${body}${props.suffix}`
}

function animate(from, to) {
  cancelAnimationFrame(frame)
  if (!Number.isFinite(to)) {
    display.value = '—'
    return
  }
  const start = Number.isFinite(from)
    ? from
    : Math.abs(to) >= 1000
      ? to * 0.85
      : 0

  if (prefersReduced() || Math.abs(to - start) < 0.001) {
    display.value = format(to)
    return
  }

  const t0 = performance.now()
  const step = (now) => {
    const t = Math.min(1, (now - t0) / props.duration)
    const eased = 1 - Math.pow(1 - t, 3)
    display.value = format(start + (to - start) * eased)
    if (t < 1) frame = requestAnimationFrame(step)
  }
  frame = requestAnimationFrame(step)
}

watch(
  () => props.value,
  (to, from) => animate(Number(from), Number(to)),
  { immediate: true }
)

onUnmounted(() => cancelAnimationFrame(frame))
</script>

<template>
  <span class="num">{{ display }}</span>
</template>
