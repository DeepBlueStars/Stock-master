<template>
  <div class="asset-chart-container">
    <div ref="chartRef" class="asset-chart"></div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue';
import * as echarts from 'echarts';

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

const chartRef = ref(null);
let chartInstance = null;

// 初始化图表
const initChart = () => {
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value);
    updateChart();
  }
};

// 更新图表
const updateChart = () => {
  if (!chartInstance) return;

  // 优先使用分类持仓金额数据，如果没有则使用props.assets数据
  let chartData;
  if (props.categoryPositions.length > 0) {
    chartData = props.categoryPositions.map(item => ({
      name: item.categoryName,
      value: item.positionAmount,
      itemStyle: {
        color: item.color
      }
    }));
  } else {
    chartData = props.assets.map(asset => ({
      name: asset.name,
      value: asset.ratio,
      itemStyle: {
        color: asset.color
      }
    }));
  }

  // 计算图表数据类型（金额或比例）
  const isAmountChart = props.categoryPositions.length > 0;

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: isAmountChart 
        ? '{b}: {c} 元 ({d}%)' 
        : '{b}: {c}% ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      formatter: (name) => {
        const item = chartData.find(item => item.name === name);
        return isAmountChart 
          ? `${name}: ${item.value} 元` 
          : `${name}: ${item.value}%`;
      },
      data: chartData.map(item => item.name)
    },
    series: [
      {
        name: isAmountChart ? '分类持仓金额' : '资产配置',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold',
            formatter: isAmountChart ? '{b}\n{c} 元' : '{b}\n{c}%'
          }
        },
        labelLine: {
          show: false
        },
        data: chartData
      }
    ],
    animationType: 'scale',
    animationEasing: 'elasticOut',
    animationDelay: () => Math.random() * 200
  };

  chartInstance.setOption(option, true);
};

// 监听窗口大小变化，调整图表尺寸
const handleResize = () => {
  chartInstance?.resize();
};

// 监听资产数据变化，重新更新图表
watch(
  [() => props.assets, () => props.categoryPositions],
  () => {
    updateChart();
  },
  { deep: true }
);

// 组件挂载时初始化图表
onMounted(() => {
  initChart();
  window.addEventListener('resize', handleResize);
});

// 组件卸载时销毁图表实例
onUnmounted(() => {
  chartInstance?.dispose();
  window.removeEventListener('resize', handleResize);
});
</script>

<style scoped>
.asset-chart-container {
  width: 100%;
  height: 100%;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.asset-chart {
  width: 100%;
  height: 100%;
  min-height: 300px;
}
</style>