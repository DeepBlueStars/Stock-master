<script setup>
import { ref, onMounted, watch } from 'vue';
import * as echarts from 'echarts';
import { Top, Bottom, Plus, Minus, Delete, TrendCharts, Document, Histogram } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import dayjs from 'dayjs';

// 导入配置文件
import config from '../config/config';

// 使用配置文件中的API基础地址
const BASE_URL = config.baseUrl;

// 股票数据 - 从API获取
const stockData = ref([]);

// 收益曲线数据
const profitChartData = ref({
  labels: [],
  datasets: [
    {
      label: '收益曲线',
      data: [],
      borderColor: '#3b82f6',
      backgroundColor: 'rgba(59, 130, 246, 0.1)',
      borderWidth: 3,
      fill: true,
      tension: 0.4
    }
  ]
});

// 收益曲线加载状态
const loadingProfitChart = ref(false);
const errorProfitChart = ref(null);

// ECharts实例
let echartsInstance = null;

// 响应式数据
const timeFilter = ref('1月');
const showBuyModal = ref(false);
const showSellModal = ref(false);
const showTradeRecordsModal = ref(false);
const selectedStock = ref(null);
const now = ref(new Date());
const nowIso = ref(now.value.toISOString().slice(0, 16));
const updateTime = ref(now.value.toLocaleString('zh-CN'));

// Investment overview data
const investmentOverview = ref({
  totalProfit: 0,
  totalProfitRate: 0,
  totalValue: 0,
  totalCost: 0,
  positionCount: 0,
  tradeCount: 0
});

// Loading and error states
const loadingInvestmentOverview = ref(false);
const errorInvestmentOverview = ref(null);

// 模拟交易记录数据
const tradeRecords = ref([]);

// 买入表单数据
const buyForm = ref({
  stockCode: '',
  stockName: '',
  stockPrice: '',
  stockChange: '',
  buyAmount: '',
  buyShares: '',
  buyTime: nowIso.value,
  type: '',
  buyMethod: '现金'
});

// 用户现金
const userCash = ref(0);

// 买入操作加载状态
const buyingStock = ref(false);

// 卖出操作加载状态
const sellingStock = ref(false);

// 买入表单引用
const buyFormRef = ref(null);

// 配置分类数据
const configTypes = ref([]);

// 配置加载状态
const loadingConfig = ref(false);

// 买入表单验证规则
const buyFormRules = ref({
  buyAmount: [
    { required: true, message: '请输入买入金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '买入金额必须大于0', trigger: 'blur' }
  ],
  buyShares: [
    { required: true, message: '请输入买入股数', trigger: 'blur' },
    { type: 'number', min: 100, message: '买入股数必须大于等于100', trigger: 'blur' },
    { validator: validateSharesMultiple, message: '买入股数必须是100的整数倍', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择股票类型', trigger: 'blur' }
  ]
});

// 买入表单是否有效
const isBuyFormValid = ref(false);

// 股票搜索相关状态
const searchingStock = ref(false);

// 实时验证买入表单
async function validateBuyForm() {
  if (!buyFormRef.value) return;
  
  try {
    await buyFormRef.value.validateField(['buyAmount', 'buyShares']);
    isBuyFormValid.value = true;
  } catch (error) {
    isBuyFormValid.value = false;
  }
}

// 验证股数是否为100的整数倍
function validateSharesMultiple(rule, value, callback) {
  if (value && value % 100 !== 0) {
    callback(new Error('股数必须是100的整数倍'));
  } else {
    callback();
  }
}

// 验证卖出股数是否超过可用持仓
function validateSharesAvailable(rule, value, callback) {
  if (value && value > sellForm.value.availableShares) {
    callback(new Error('卖出股数不能超过可用持仓'));
  } else {
    callback();
  }
}

// 卖出表单数据
const sellForm = ref({
  stockCode: '',
  availableShares: 0,
  holdPrice: 0,
  currentPrice: 0,
  sellAmount: '',
  sellShares: '',
  sellTime: nowIso.value
});

// 卖出表单引用
const sellFormRef = ref(null);

// 卖出表单验证规则
const sellFormRules = ref({
  stockCode: [
    { required: true, message: '请选择要卖出的股票', trigger: 'blur' }
  ],
  sellAmount: [
    { required: true, message: '请输入卖出金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '卖出金额必须大于0', trigger: 'blur' }
  ],
  sellShares: [
    { required: true, message: '请输入卖出股数', trigger: 'blur' },
    { type: 'number', min: 100, message: '卖出股数必须大于等于100', trigger: 'blur' },
    { validator: validateSharesMultiple, message: '卖出股数必须是100的整数倍', trigger: 'blur' },
    { validator: validateSharesAvailable, message: '卖出股数不能超过可用持仓', trigger: 'blur' }
  ]
});

// 卖出表单是否有效
const isSellFormValid = ref(false);

// 实时验证卖出表单
async function validateSellForm() {
  if (!sellFormRef.value) return;
  
  try {
    await sellFormRef.value.validateField(['stockCode', 'sellAmount', 'sellShares']);
    isSellFormValid.value = true;
  } catch (error) {
    isSellFormValid.value = false;
  }
}

// 股票查询信息
const stockInfoVisible = ref(false);

// 计算属性
const isStockListEmpty = () => stockData.value.length === 0;

// 模拟股票查询数据
const mockStocks = {
  '000001': { name: '平安银行', price: 15.68, change: 1.25 },
  '000858': { name: '五粮液', price: 185.90, change: -0.85 },
  '300750': { name: '宁德时代', price: 235.60, change: 2.34 },
  '600519': { name: '贵州茅台', price: 1785.00, change: 0.68 },
  '000333': { name: '美的集团', price: 58.90, change: -0.51 },
  '002415': { name: '海康威视', price: 38.75, change: 1.57 },
  '601318': { name: '中国平安', price: 48.90, change: 0.82 },
  '300059': { name: '东方财富', price: 18.20, change: 2.25 }
};

// 图表实例（已迁移到ECharts，此变量保留以确保兼容性）
let profitChart = null;

// 刷新持仓列表
async function refreshStockData() {
  try {
    await fetchStockData();
  } catch (error) {
    console.error('刷新持仓列表失败:', error);
    ElMessage.error('刷新持仓列表失败，请稍后重试');
  }
}

// 获取股票数据的API函数
async function fetchStockData() {
  try {
    const response = await axios.post(`${BASE_URL}/stock/list`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    if (response.status === 200) {
      // 检查响应数据格式是否正确
      const res = response.data;
      if (res.state === 0) {
        if (Array.isArray(res.data)) {
          stockData.value = res.data;
        } else {
          // 弹窗提示API响应格式错误
          ElMessage.error('API响应格式错误：数据不是数组');
        }
      } else {
        // 处理其他状态码
        ElMessage.error('API响应错误:' + res.msg);
      }
    } else {
      // 弹窗提示API请求失败
      ElMessage.error('API请求失败，请稍后重试');
      console.error('API请求失败:', response.statusText);
    }
  } catch (error) {
    // 弹窗提示获取股票数据失败
    ElMessage.error('获取股票数据失败，请稍后重试');
    console.error('获取股票数据失败:', error.message);
  }
}

// 获取用户配置的API函数
async function fetchConfig() {
  try {
    loadingConfig.value = true;
    const response = await axios.post(`${BASE_URL}/config/get`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    if (response.status === 200) {
      const res = response.data;
      if (res.state === 0) {
        // 解析config JSON字符串
        const configData = JSON.parse(res.data.config);
        // 从解析后的对象中获取assets数组，并转换为下拉框选项格式
        configTypes.value = configData.assets.map(asset => ({
          label: asset.name,
          value: asset.name
        }));
      } else {
        // 处理其他状态码
        ElMessage.error('获取配置失败:' + (res.message || '未知错误'));
      }
    } else {
      // 弹窗提示API请求失败
      ElMessage.error('获取配置失败，请稍后重试');
      console.error('获取配置失败:', response.statusText);
    }
  } catch (error) {
    // 弹窗提示获取配置失败
    ElMessage.error('获取配置失败，请稍后重试');
    console.error('获取配置失败:', error.message);
  } finally {
    loadingConfig.value = false;
  }
}

// 获取用户现金
async function fetchUserCash() {
  try {
    const response = await axios.post(`${BASE_URL}/config/userInfo`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    if (response.status === 200) {
      const res = response.data;
      if (res.state === 0) {
        userCash.value = res.data.cash || 0;
      }
    }
  } catch (error) {
    console.error('获取用户现金失败:', error.message);
  }
}

// 格式化数字为货币格式
function formatCurrency(value) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

// 格式化百分比
function formatPercentage(value) {
  return (value * 100).toFixed(2) + '%';
}

// 获取投资总览数据的API函数
async function fetchInvestmentOverview() {
  try {
    loadingInvestmentOverview.value = true;
    errorInvestmentOverview.value = null;
    
    const response = await axios.post(`${BASE_URL}/stock/basicPosition`, {
      userId: '6928fca14f206ada1b7a9269'
    });
    
    if (response.status === 200) {
      // 检查响应数据格式是否正确
      const res = response.data;
      if (res.state === 0) {
        if (res.data && typeof res.data === 'object') {
          // 更新投资总览数据
          investmentOverview.value = {
            totalProfit: res.data.totalProfit || 0,
            totalProfitRate: res.data.totalProfitRate || 0,
            totalValue: res.data.totalValue || 0,
            totalCost: res.data.totalCost || 0,
            positionCount: res.data.positionCount || 0,
            tradeCount: res.data.tradeCount || 0
          };
        } else {
          // 弹窗提示API响应格式错误
          ElMessage.error('API响应格式错误：数据不是对象');
          errorInvestmentOverview.value = '数据格式错误';
        }
      } else {
        // 处理其他状态码
        ElMessage.error('API响应错误:' + (res.msg || '未知错误'));
        errorInvestmentOverview.value = res.msg || '未知错误';
      }
    } else {
      // 弹窗提示API请求失败
      ElMessage.error('API请求失败，请稍后重试');
      console.error('API请求失败:', response.statusText);
      errorInvestmentOverview.value = 'API请求失败';
    }
  } catch (error) {
    // 弹窗提示获取投资总览数据失败
    ElMessage.error('获取投资总览数据失败，请稍后重试');
    console.error('获取投资总览数据失败:', error.message);
    errorInvestmentOverview.value = error.message;
  } finally {
    loadingInvestmentOverview.value = false;
  }
}

// 获取收益波动曲线数据
async function fetchProfitChartData() {
  try {
    loadingProfitChart.value = true;
    errorProfitChart.value = null;
    
    // 根据时间过滤器确定时间范围
    const now = dayjs();
    let startTime = null;
    let endTime = null;
    
    switch (timeFilter.value) {
      case '1月':
        startTime = now.subtract(1, 'month');
        endTime = now;
        break;
      case '3月':
        startTime = now.subtract(3, 'month');
        endTime = now;
        break;
      case '6月':
        startTime = now.subtract(6, 'month');
        endTime = now;
        break;
      case '1年':
        startTime = now.subtract(1, 'year');
        endTime = now;
        break;
      case '全部':
        // 当选择"全部"时，startTime和endTime都为null
        startTime = null;
        endTime = null;
        break;
      default:
        // 默认获取最近一年的数据
        startTime = now.subtract(1, 'year');
        endTime = now;
        break;
    }
    
    // 格式化时间参数
    const requestData = {
      userId: '6928fca14f206ada1b7a9269',
      startTime: startTime ? startTime.format('YYYY-MM-DD HH:mm:ss') : null,
      endTime: endTime ? endTime.format('YYYY-MM-DD HH:mm:ss') : null
    };
    
    // 发送API请求
    const response = await axios.post(`${BASE_URL}/stock/listTotalReturn`, requestData);
    
    if (response.status === 200) {
      const res = response.data;
      
      if (res.state === 0) {
        if (Array.isArray(res.data)) {
          // 处理返回的数据
          const totalReturnData = res.data;
          
          // 按时间排序
          totalReturnData.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
          
          // 提取标签和数据
          const labels = totalReturnData.map(item => {
            const date = dayjs(item.createdAt);
            return date.format('MM-DD');
          });
          
          const data = totalReturnData.map(item => item.totalReturn);
          
          // 更新图表数据
          profitChartData.value.labels = labels;
          profitChartData.value.datasets[0].data = data;
          
          // 更新图表
          updateProfitChart();
          
        } else {
          errorProfitChart.value = 'API响应格式错误：数据不是数组';
          ElMessage.error('API响应格式错误：数据不是数组');
        }
      } else {
        errorProfitChart.value = res.message || 'API响应错误';
        ElMessage.error('API响应错误:' + (res.message || '未知错误'));
      }
    } else {
      errorProfitChart.value = 'API请求失败';
      ElMessage.error('API请求失败，请稍后重试');
    }
  } catch (error) {
    errorProfitChart.value = error.message;
    ElMessage.error('获取收益波动曲线数据失败，请稍后重试');
    console.error('获取收益波动曲线数据失败:', error);
  } finally {
    loadingProfitChart.value = false;
  }
}

// 更新收益图表数据
function updateProfitChart() {
  if (echartsInstance) {
    const option = echartsInstance.getOption();
    
    // 更新x轴数据
    option.xAxis[0].data = profitChartData.value.labels;
    
    // 更新系列数据
    option.series[0].data = profitChartData.value.datasets[0].data;
    
    echartsInstance.setOption(option);
  }
}

onMounted(() => {
  // 初始化收益图表
  initProfitChart();
  
  // 从API获取股票数据
  fetchStockData();
  
  // 获取投资总览数据
  fetchInvestmentOverview();
  
  // 获取收益波动曲线数据
  fetchProfitChartData();
  
  // 获取用户配置
  fetchConfig();
  
  // 获取用户现金
  fetchUserCash();
  
  // 监听卖出股票选择变化
  watch(() => sellForm.value.stockCode, (newValue) => {
    onSellStockChange(newValue);
  });
  
  // 监听时间过滤器变化
  watch(timeFilter, () => {
    fetchProfitChartData();
  });
});

// 初始化收益图表 - 使用ECharts
function initProfitChart() {
  const chartDom = document.getElementById('profitChart');
  if (chartDom) {
    echartsInstance = echarts.init(chartDom);
    
    const option = {
      title: {
        show: false
      },
      tooltip: {
        trigger: 'axis',
        formatter: function(params) {
          const data = params[0];
          return `${data.axisValue}: <b>¥${data.value.toLocaleString()}</b>`;
        },
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#e5e7eb',
        borderWidth: 1,
        textStyle: {
          color: '#374151'
        }
      },
      legend: {
        data: ['收益曲线'],
        top: 0,
        right: 0,
        textStyle: {
          fontSize: 12,
          color: '#6b7280'
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '10%',
        top: '8%',
        containLabel: true
      },
      toolbox: {
        feature: {
          dataZoom: {
            yAxisIndex: 'none'
          },
          restore: {},
          saveAsImage: {}
        },
        top: 0,
        right: '15%'
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: profitChartData.value.labels,
        axisLine: {
          lineStyle: {
            color: '#e5e7eb'
          }
        },
        axisTick: {
          show: false
        },
        axisLabel: {
          fontSize: 12,
          color: '#6b7280'
        }
      },
      yAxis: {
        type: 'value',
        axisLine: {
          lineStyle: {
            color: '#e5e7eb'
          }
        },
        axisTick: {
          show: false
        },
        axisLabel: {
          fontSize: 12,
          color: '#6b7280',
          formatter: function(value) {
            return '¥' + value.toLocaleString();
          }
        },
        splitLine: {
          lineStyle: {
            color: '#f3f4f6'
          }
        }
      },
      dataZoom: [
        {
          type: 'inside',
          start: 0,
          end: 100
        },
        {
          start: 0,
          end: 100,
          height: 15,
          bottom: '2%'
        }
      ],
      series: [
        {
          name: '收益曲线',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: {
            color: '#3b82f6',
            width: 3
          },
          itemStyle: {
            color: '#3b82f6'
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                {
                  offset: 0,
                  color: 'rgba(59, 130, 246, 0.3)'
                },
                {
                  offset: 1,
                  color: 'rgba(59, 130, 246, 0.05)'
                }
              ]
            }
          },
          emphasis: {
            focus: 'series'
          },
          data: profitChartData.value.datasets[0].data,
          animationDuration: 1000,
          animationEasing: 'cubicOut'
        }
      ]
    };
    
    echartsInstance.setOption(option);
    
    // 添加窗口大小变化监听，确保响应式
    window.addEventListener('resize', function() {
      echartsInstance.resize();
    });
  }
}

// 股票代码查询
async function searchStock() {
  if (!buyForm.value.stockCode.trim()) {
    ElMessage.warning('请输入股票代码');
    return;
  }
  
  try {
    // 设置加载状态
    searchingStock.value = true;
    
    // 发送API请求
    const response = await axios.post(`${BASE_URL}/stock/getStock`, {
      symbol: buyForm.value.stockCode.trim()
    });
    
    // 检查响应状态
    if (response.status === 200) {
      const res = response.data;
      
      // 检查业务状态码
      if (res.state === 0) {
        // 提取股票信息
        const stockData = res.data;
        buyForm.value.stockName = stockData.name;
        buyForm.value.stockCode = stockData.symbol;
        buyForm.value.stockPrice = '0.00'; // 设置默认值，确保UI不会显示空值
        buyForm.value.stockChange = '0.00'; // 设置默认值，确保UI不会显示空值
        stockInfoVisible.value = true;
      } else {
        // 处理业务错误
        ElMessage.error(res.message || '获取股票信息失败');
        stockInfoVisible.value = false;
      }
    } else {
      // 处理HTTP错误
      ElMessage.error('HTTP请求失败: ' + response.statusText);
      stockInfoVisible.value = false;
    }
  } catch (error) {
    // 处理网络错误
    console.error('获取股票信息失败:', error);
    ElMessage.error('网络异常或服务器错误，请稍后重试');
    stockInfoVisible.value = false;
  } finally {
    // 清除加载状态
    searchingStock.value = false;
  }
}

// 卖出股票选择变化
function onSellStockChange(selectedCode) {
  const stock = stockData.value.find(s => s.symbol === selectedCode);
  
  if (stock) {
    sellForm.value.availableShares = stock.totalQuantity;
    sellForm.value.holdPrice = stock.avgCostPrice;
    sellForm.value.currentPrice = stock.price;
  } else {
    sellForm.value.availableShares = 0;
    sellForm.value.holdPrice = 0;
    sellForm.value.currentPrice = 0;
  }
}

// 提交买入表单
async function submitBuyForm() {
  // 表单验证
  if (!buyFormRef.value) return;
  
  // 验证表单
  try {
    await buyFormRef.value.validate();
  } catch (error) {
    ElMessage.error('请检查表单输入是否正确');
    return;
  }
  
  // 验证必填字段
  if (!buyForm.value.stockCode.trim()) {
    ElMessage.error('请输入股票代码');
    return;
  }
  
  if (!buyForm.value.stockName.trim()) {
    ElMessage.error('请先查询股票信息');
    return;
  }
  
  // 验证数值字段
  const buyAmount = parseFloat(buyForm.value.buyAmount);
  const buyShares = parseInt(buyForm.value.buyShares);
  
  if (isNaN(buyAmount) || buyAmount <= 0) {
    ElMessage.error('请输入有效的买入金额');
    return;
  }
  
  if (isNaN(buyShares) || buyShares <= 0) {
    ElMessage.error('请输入有效的买入股数');
    return;
  }
  
  // 验证买入股数是否为100的整数倍
  if (buyShares % 100 !== 0) {
    ElMessage.error('买入股数必须是100的整数倍');
    return;
  }
  
  // 验证交易时间
  if (!buyForm.value.buyTime) {
    ElMessage.error('请选择买入时间');
    return;
  }
  
  // 设置加载状态
  buyingStock.value = true;
  
  try {
    // 准备请求参数
    const formattedTradeTime = dayjs(buyForm.value.buyTime).format('YYYY-MM-DD HH:mm:ss');
    
    const requestData = {
      userId: "6928fca14f206ada1b7a9269",
      symbol: buyForm.value.stockCode.trim(),
      stockName: buyForm.value.stockName.trim(),
      side: "买入",
      quantity: buyShares,
      price: buyAmount,
      tradeTime: formattedTradeTime,
      type: buyForm.value.type,
      buyMethod: buyForm.value.buyMethod
    };
    
    // 发送API请求
    const response = await axios.post(`${BASE_URL}/stock/buy`, requestData);
    
    // 处理响应
    if (response.status === 200) {
      const res = response.data;
      
      if (res.state === 0) {
        // 交易成功
        ElMessage.success(res.message || '股票买入成功');
        
        // 关闭弹窗并重置表单
        showBuyModal.value = false;
        resetBuyForm();
        // 刷新股票数据
        await fetchStockData();
        
        // 刷新投资总览数据
        await fetchInvestmentOverview();
        
        // 刷新收益波动曲线数据
        await fetchProfitChartData();
      } else {
        // 业务逻辑错误
        ElMessage.error(res.message || '股票买入失败');
      }
    } else {
      // HTTP错误
      ElMessage.error(`HTTP请求失败: ${response.status} ${response.statusText}`);
    }
    
  } catch (error) {
    console.error('买入股票失败:', error);
    
    if (error.response) {
      // 服务器响应错误
      ElMessage.error(`服务器错误: ${error.response.status} ${error.response.statusText}`);
    } else if (error.request) {
      // 网络错误
      ElMessage.error('网络连接失败，请检查网络连接');
    } else {
      // 其他错误
      ElMessage.error('买入股票失败，请稍后重试');
    }
  } finally {
    // 清除加载状态
    buyingStock.value = false;
  }
}

// 提交卖出表单
async function submitSellForm() {
  if (!sellFormRef.value) return;
  
  // 表单验证
  try {
    await sellFormRef.value.validate();
  } catch (error) {
    ElMessage.error('请检查表单输入是否正确');
    return;
  }
  
  // 验证必填字段
  if (!sellForm.value.stockCode.trim()) {
    ElMessage.error('请选择要卖出的股票');
    return;
  }
  
  // 验证数值字段
  const sellAmount = parseFloat(sellForm.value.sellAmount);
  const sellShares = parseInt(sellForm.value.sellShares);
  
  if (isNaN(sellAmount) || sellAmount <= 0) {
    ElMessage.error('请输入有效的卖出金额');
    return;
  }
  
  if (isNaN(sellShares) || sellShares <= 0) {
    ElMessage.error('请输入有效的卖出股数');
    return;
  }
  
  // 验证卖出股数是否为100的整数倍
  if (sellShares % 100 !== 0) {
    ElMessage.error('卖出股数必须是100的整数倍');
    return;
  }
  
  // 验证交易时间
  if (!sellForm.value.sellTime) {
    ElMessage.error('请选择卖出时间');
    return;
  }
  
  // 设置加载状态
  sellingStock.value = true;
  
  try {
    // 获取股票名称
    const selectedStock = stockData.value.find(stock => stock.symbol === sellForm.value.stockCode);
    const stockName = selectedStock ? selectedStock.stockName : '';
    
    // 准备请求参数
    const formattedTradeTime = dayjs(sellForm.value.sellTime).format('YYYY-MM-DD HH:mm:ss');
    
    const requestData = {
      userId: "6928fca14f206ada1b7a9269",
      symbol: sellForm.value.stockCode.trim(),
      stockName: stockName,
      side: "卖出",
      quantity: sellShares,
      price: sellAmount,
      tradeTime: formattedTradeTime
    };
    
    // 发送API请求
    const response = await axios.post(`${BASE_URL}/stock/sell`, requestData);
    
    // 处理响应
    if (response.status === 200) {
      const res = response.data;
      
      if (res.state === 0) {
        // 交易成功
        ElMessage.success(res.message || '股票卖出成功');
        
        // 关闭弹窗并重置表单
        showSellModal.value = false;
        resetSellForm();
        
        // 刷新持仓列表
        await refreshStockData();
        
        // 刷新投资总览数据
        await fetchInvestmentOverview();
        
        // 刷新收益波动曲线数据
        await fetchProfitChartData();
      } else {
        // 业务逻辑错误
        ElMessage.error(res.message || '股票卖出失败');
      }
    } else {
      // HTTP错误
      ElMessage.error(`HTTP请求失败: ${response.status} ${response.statusText}`);
    }
    
  } catch (error) {
    console.error('卖出股票失败:', error);
    
    if (error.response) {
      // 服务器响应错误
      ElMessage.error(`服务器错误: ${error.response.status} ${error.response.statusText}`);
    } else if (error.request) {
      // 网络错误
      ElMessage.error('网络连接失败，请检查网络连接');
    } else {
      // 其他错误
      ElMessage.error('卖出股票失败，请稍后重试');
    }
  } finally {
    // 清除加载状态
    sellingStock.value = false;
  }
}

// 重置买入表单
function resetBuyForm() {
  buyForm.value = {
    stockCode: '',
    stockName: '',
    stockPrice: '',
    stockChange: '',
    buyAmount: '',
    buyShares: '',
    buyTime: nowIso.value,
    type: ''
  };
  stockInfoVisible.value = false;
}

// 重置卖出表单
function resetSellForm() {
  sellForm.value = {
    stockCode: '',
    availableShares: 0,
    holdPrice: 0,
    currentPrice: 0,
    sellAmount: '',
    sellShares: '',
    sellTime: nowIso.value
  };
  
  isSellFormValid.value = false;
}



// 打开交易记录弹窗
async function openTradeRecords(code) {
  try {
    selectedStock.value = stockData.value.find(stock => stock.symbol === code);
    console.log("selectedStock.value:", selectedStock.value);
    if (selectedStock.value) {
      // 调用API获取交易记录
      const response = await axios.post(`${BASE_URL}/stock/listTrade`, {
        code: code,
        userId: '6928fca14f206ada1b7a9269'
      });

      // 处理API响应
      if (response.status === 200) {
        const apiData = response.data;
        // 检查返回状态和数据格式
        if (apiData.state === 0) {
          if (Array.isArray(apiData.data)) {
            tradeRecords.value = apiData.data;
          } else {
            // 弹窗提示API响应格式错误
            ElMessage.error('API响应格式错误：数据不是数组');
            console.error('API返回数据格式异常:', apiData);
            tradeRecords.value = [];
          }
        } else {
          // 处理其他状态码
          ElMessage.error('API响应错误:' + (apiData.message || '未知错误'));
          console.error('API返回错误状态:', apiData);
          tradeRecords.value = [];
        }
      } else {
        // 弹窗提示API请求失败
        ElMessage.error('API请求失败，请稍后重试');
        console.error('获取交易记录失败，状态码:', response.status);
        tradeRecords.value = [];
      }

      // 显示交易记录弹窗
      showTradeRecordsModal.value = true;
    }
  } catch (error) {
    // 弹窗提示获取交易记录失败
    ElMessage.error('获取交易记录失败，请稍后重试');
    console.error('获取交易记录时发生错误:', error);
    tradeRecords.value = [];
    // 即使发生错误，也显示弹窗（可能为空数据）
    if (selectedStock.value) {
      showTradeRecordsModal.value = true;
    }
  }
}

// 打开报告页面
function openReport(reportId) {
  if (!reportId) {
    ElMessage.warning('报告ID不存在');
    return;
  }
  window.open(`http://192.168.31.88/reports/view/${reportId}`, '_blank');
}
</script>

<template>
  <div class="w-full px-4 py-8 flex-grow dashboard-content">
    <!-- 主要布局区域 -->
    <el-row class="mb-8 w-full">
      <!-- 第一区域：关键指标 -->
      <el-col :span="12" class="column-left">
        <el-card shadow="hover" class="main-card">
          <template #header>
            <h2 class="text-xl font-bold text-gray-800 mb-6">投资总览</h2>
          </template>
          <el-row :gutter="16" class="stats-container">
            <!-- 总收益 -->
            <el-col :span="8">
              <el-card shadow="hover" class="stat-card stat-card-1">
                <div class="card-1 card-content-1">
                  <div class="stat-value value-profit">
                    <el-skeleton :rows="1" animated v-if="loadingInvestmentOverview" />
                    <template v-else>
                      {{ investmentOverview.totalProfit >= 0 ? '+' : '' }}{{ formatCurrency(investmentOverview.totalProfit) }}
                    </template>
                  </div>
                  <div class="stat-label">持仓收益</div>
                </div>
              </el-card>
            </el-col>
            
            <!-- 总收益率 -->
            <el-col :span="8">
              <el-card shadow="hover" class="stat-card stat-card-2">
                <div class="card-1 card-content-2">
                  <div class="stat-value value-rate">
                    <el-skeleton :rows="1" animated v-if="loadingInvestmentOverview" />
                    <template v-else>
                      {{ investmentOverview.totalProfitRate >= 0 ? '+' : '' }}{{ formatPercentage(investmentOverview.totalProfitRate) }}
                    </template>
                  </div>
                  <div class="stat-label">持仓收益率</div>
                </div>
              </el-card>
            </el-col>
            
            <!-- 总资产 -->
            <el-col :span="8">
              <el-card shadow="hover" class="stat-card stat-card-3">
                <div class="card-1 card-content-3">
                  <div class="stat-value value-assets">
                    <el-skeleton :rows="1" animated v-if="loadingInvestmentOverview" />
                    <template v-else>
                      {{ formatCurrency(investmentOverview.totalValue) }}
                    </template>
                  </div>
                  <div class="stat-label">总资产</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          
          <!-- 简要数据说明 -->
          <div class="summary-section">
            <el-row :gutter="40" justify="space-between">
              <el-col>
                <span class="summary-label">总投资额:</span>
                <span class="summary-value">
                  <el-skeleton :rows="1" animated v-if="loadingInvestmentOverview" />
                  <template v-else>
                    {{ formatCurrency(investmentOverview.totalCost) }}
                  </template>
                </span>
              </el-col>
              <el-col>
                <span class="summary-label">持仓股票:</span>
                <span class="summary-value">
                  <el-skeleton :rows="1" animated v-if="loadingInvestmentOverview" />
                  <template v-else>
                    {{ investmentOverview.positionCount }} 支
                  </template>
                </span>
              </el-col>
              <el-col>
                <span class="summary-label">交易次数:</span>
                <span class="summary-value">
                  <el-skeleton :rows="1" animated v-if="loadingInvestmentOverview" />
                  <template v-else>
                    {{ investmentOverview.tradeCount }} 次
                  </template>
                </span>
              </el-col>
            </el-row>
          </div>
          
          <!-- Error message -->
          <el-alert
            v-if="errorInvestmentOverview"
            title="获取投资数据失败"
            :description="errorInvestmentOverview"
            type="error"
            show-icon
            class="mt-4"
          >
            <template #default>
              <el-button type="primary" size="small" @click="fetchInvestmentOverview">重试</el-button>
            </template>
          </el-alert>
        </el-card>
      </el-col>
      
      <!-- 第二区域：收益波动曲线 -->
      <el-col :span="12" class="column-right">
        <el-card shadow="hover" class="main-card">
          <template #header>
            <div class="chart-header-container">
              <h2 class="chart-title">收益波动曲线</h2>
              <div class="time-filters">
                <el-radio-group v-model="timeFilter" size="small">
                  <el-radio-button label="1月">1月</el-radio-button>
                  <el-radio-button label="3月">3月</el-radio-button>
                  <el-radio-button label="6月">6月</el-radio-button>
                  <el-radio-button label="1年">1年</el-radio-button>
                  <el-radio-button label="全部">全部</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>
          
          <!-- 图表容器 -->
          <div class="chart-container">
            <div id="profitChart"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 第三区域：持仓列表 -->
    <el-card shadow="hover" class="mt-5 position-list-card">
      <template #header>
        <div class="position-list-header">
          <h2 class="position-list-title">持仓列表</h2>
          <div class="action-buttons">
            <el-button type="success" @click="showBuyModal = true" class="buy-button">
              <el-icon class="button-icon"><Plus /></el-icon>买入股票
            </el-button>
            <el-button type="danger" @click="showSellModal = true">
              <el-icon class="button-icon"><Minus /></el-icon>卖出股票
            </el-button>
          </div>
        </div>
      </template>
      
      <!-- 股票持仓表格 -->
      <el-table v-if="!isStockListEmpty()" :data="stockData" stripe height="100%">
        <el-table-column prop="symbol" label="股票代号" />
        <el-table-column prop="stockName" label="股票名称">
          <template #default="scope">
            <div>
              <div class="font-medium">{{ scope.row.stockName }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" />
        <el-table-column prop="price" label="当前价格">
          <template #default="scope">
            <div class="font-bold">¥ {{ scope.row.price.toFixed(3) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="recommendation" label="推荐操作">
          <template #default="scope">
            <div class="font-bold">{{ scope.row.recommendation || '未设置' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="pctChg" label="当天涨跌幅">
          <template #default="scope">
            <span :class="scope.row.pctChg >= 0 ? 'text-red-600' : 'text-green-600'">
              <el-icon class="mr-1">
                <Top v-if="scope.row.pctChg >= 0" />
                <Bottom v-else />
              </el-icon>
              {{ scope.row.pctChg >= 0 ? '+' : '' }}{{ scope.row.pctChg }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="totalQuantity" label="持仓">
          <template #default="scope">
            {{ scope.row.totalQuantity.toLocaleString() }} 股
          </template>
        </el-table-column>
        <el-table-column prop="avgCostPrice" label="持仓均价">
          <template #default="scope">
            ¥ {{ scope.row.avgCostPrice.toFixed(3) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalValue" label="总价值">
          <template #default="scope">
            <div class="font-bold">¥ {{ scope.row.totalValue.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="totalReturn" label="总收益">
          <template #default="scope">
            <span :class="scope.row.totalReturn >= 0 ? 'text-red-600' : 'text-green-600'">
              {{ scope.row.totalReturn >= 0 ? '+' : '' }}¥ {{ scope.row.totalReturn.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="totalReturnPct" label="收益率">
          <template #default="scope">
            <span :class="scope.row.totalReturnPct >= 0 ? 'text-red-600' : 'text-green-600'">
              {{ scope.row.totalReturnPct >= 0 ? '+' : '' }}{{ formatPercentage(scope.row.totalReturnPct) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间">
          <template #default="scope">
            <div>{{ scope.row.updatedAt }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <el-button type="primary" size="small" @click="openTradeRecords(scope.row.symbol)" title="交易记录">
              <el-icon><Histogram /></el-icon>
            </el-button>
            <el-button type="warning" size="small" @click="openReport(scope.row.reportId)" title="查看报告" :disabled="!scope.row.reportId">
              <el-icon><Document /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 空状态 -->
      <div v-else class="card-1 py-12">
        <el-icon class="text-4xl text-gray-300 mb-4"><TrendCharts /></el-icon>
        <h3 class="text-xl font-medium text-gray-700 mb-2">暂无持仓股票</h3>
        <p class="text-gray-500 mb-6">点击"买入股票"按钮开始您的投资之旅</p>
        <el-button type="primary" @click="showBuyModal = true">
          <el-icon class="mr-1"><Plus /></el-icon>立即买入
        </el-button>
      </div>
    </el-card>
    
    <!-- 买入股票弹窗 -->
    <el-dialog
      v-model="showBuyModal"
      title="买入股票"
      width="500px"
      center
      destroy-on-close
    >
      <el-form ref="buyFormRef" :model="buyForm" @submit.prevent="submitBuyForm" label-position="top" :rules="buyFormRules">
        <el-form-item label="股票代码">
          <el-row :gutter="10">
            <el-col :span="18">
              <el-input
                v-model="buyForm.stockCode"
                placeholder="例如: 000001"
                required
              />
            </el-col>
            <el-col :span="6">
              <el-button type="primary" @click="searchStock" class="w-full" :loading="searchingStock">
                查询
              </el-button>
            </el-col>
          </el-row>
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="14">
            <el-form-item label="股票名称">
              <el-input
                v-model="buyForm.stockName"
                placeholder="请先查询股票代码"
                disabled
                readonly
              />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="类型" prop="type">
              <el-select
                v-model="buyForm.type"
                placeholder="请选择股票类型"
                class="w-full"
                required
                :loading="loadingConfig"
              >
                <el-option
                  v-for="type in configTypes"
                  :key="type.value"
                  :label="type.label"
                  :value="type.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="买入金额(¥)" prop="buyAmount">
              <el-input-number
                v-model="buyForm.buyAmount"
                placeholder="例如: 10000"
                class="w-full"
                :min="0.01"
                :precision="3"
                @change="validateBuyForm"
              />
            </el-form-item>
          </el-col>
          
          <el-col :span="12">
            <el-form-item label="买入股数" prop="buyShares">
              <el-input-number
                v-model="buyForm.buyShares"
                placeholder="例如: 500"
                class="w-full"
                :min="100"
                :step="100"
                @change="validateBuyForm"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="14">
            <el-form-item label="买入时间">
              <el-date-picker
                v-model="buyForm.buyTime"
                type="datetime"
                placeholder="选择买入时间"
                class="w-full"
                required
              />
            </el-form-item>
          </el-col>
          
          <el-col :span="10">
            <el-form-item label="买入方式" prop="buyMethod">
              <el-select
                v-model="buyForm.buyMethod"
                placeholder="选择买入方式"
                class="w-full"
                required
              >
                <el-option
                  label="现金"
                  value="现金"
                />
                <el-option
                  label="银行卡"
                  value="银行卡"
                />
              </el-select>
              <div class="text-xs text-gray-500 mt-1">
                当前现金: ¥{{ userCash }}
              </div>
            </el-form-item>
          </el-col>
        </el-row>

      </el-form>
      <template #footer>
        <div class="flex justify-end space-x-3">
          <el-button @click="showBuyModal = false">
            取消
          </el-button>
          <el-button type="success" @click="submitBuyForm" :disabled="!isBuyFormValid" :loading="buyingStock">
            确认买入
          </el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 卖出股票弹窗 -->
    <el-dialog
      v-model="showSellModal"
      title="卖出股票"
      width="500px"
      center
      destroy-on-close
    >
      <el-form ref="sellFormRef" :model="sellForm" @submit.prevent="submitSellForm" label-position="top" :rules="sellFormRules">
        <el-form-item label="股票代码" prop="stockCode">
          <el-select
            v-model="sellForm.stockCode"
            placeholder="请选择要卖出的股票"
            style="width: 100%"
            @change="validateSellForm"
          >
            <el-option
              v-for="stock in stockData"
              :key="stock.symbol"
              :label="`${stock.symbol} ${stock.stockName} (持有: ${stock.totalQuantity}股)`"
              :value="stock.symbol"
            />
          </el-select>
        </el-form-item>
        
        <div class="stock-info-container">
          <div class="stock-info-item">
            <span class="stock-info-label">可用持仓:</span>
            <span class="stock-info-value">{{ sellForm.availableShares }}</span>
          </div>
          <div class="stock-info-item">
            <span class="stock-info-label">持仓均价:</span>
            <span class="stock-info-value">¥ {{ sellForm.holdPrice.toFixed(3) }}</span>
          </div>
          <div class="stock-info-item">
            <span class="stock-info-label">当前价格:</span>
            <span class="stock-info-value stock-info-current-price">¥ {{ sellForm.currentPrice.toFixed(3) }}</span>
          </div>
        </div>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="卖出金额(¥)" prop="sellAmount">
              <el-input-number
                v-model="sellForm.sellAmount"
                placeholder="例如: 10000"
                class="w-full"
                :min="0.01"
                :precision="3"
                :step="0.01"
                @change="validateSellForm"
                :disabled="!sellForm.stockCode"
              />
            </el-form-item>
          </el-col>
          
          <el-col :span="12">
            <el-form-item label="卖出股数" prop="sellShares">
              <el-input-number
                v-model="sellForm.sellShares"
                placeholder="例如: 500"
                class="w-full"
                :min="sellForm.availableShares >= 100 ? 100 : sellForm.availableShares"
                :max="sellForm.availableShares"
                :step="100"
                @change="validateSellForm"
                :disabled="!sellForm.stockCode"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="卖出时间">
          <el-date-picker
            v-model="sellForm.sellTime"
            type="datetime"
            placeholder="选择卖出时间"
            class="w-full"
            required
          />
        </el-form-item>
        
      </el-form>
      <template #footer>
        <div class="flex justify-end space-x-3">
          <el-button @click="showSellModal = false">
            取消
          </el-button>
          <el-button type="danger" @click="submitSellForm" :disabled="!isSellFormValid" :loading="sellingStock">
            确认卖出
          </el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 交易记录弹窗 -->
    <el-dialog
      v-model="showTradeRecordsModal"
      :title="selectedStock ? `${selectedStock.stockName}(${selectedStock.symbol}) 交易记录` : '交易记录'"
      width="95%"
      max-width="1000px"
      center
      class="trade-records-dialog"
    >
      <div v-if="selectedStock" class="mb-4 stock-info-container">
        <div class="stock-info-item">
          <p class="info-label">当前持仓：</p>
          <p class="info-value">{{ selectedStock.totalQuantity }}股</p>
        </div>
        <div class="stock-info-item">
          <p class="info-label">持仓均价：</p>
          <p class="info-value">¥{{ selectedStock.avgCostPrice.toFixed(3) }}</p>
        </div>
        <div class="stock-info-item">
          <p class="info-label">当前价格：</p>
          <p class="info-value">¥{{ selectedStock.price.toFixed(3) }}</p>
        </div>
      </div>
      
      <el-table :data="tradeRecords" stripe style="width: 100%">
        <el-table-column prop="stockName" label="股票名称" min-width="120" />
        <el-table-column prop="symbol" label="股票代码" min-width="100" />
        <el-table-column prop="side" label="交易类型" min-width="100">
          <template #default="scope">
            <el-tag :type="scope.row.side === '买入' ? 'success' : 'danger'">
              {{ scope.row.side }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tradeTime" label="交易日期" min-width="120" />
        <el-table-column prop="price" label="交易价格" min-width="100">
          <template #default="scope">
            ¥{{ scope.row.price.toFixed(3) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="交易股数" min-width="100">
          <template #default="scope">
            {{ scope.row.quantity.toLocaleString() }} 股
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="交易金额" min-width="100">
          <template #default="scope">
            ¥{{ scope.row.amount.toFixed(3) }}
          </template>
        </el-table-column>
        <el-table-column prop="actualAmount" label="实际金额" min-width="100">
          <template #default="scope">
            ¥{{ scope.row.actualAmount.toFixed(3) }}
          </template>
        </el-table-column>
        <el-table-column prop="commission" label="佣金" min-width="100">
          <template #default="scope">
            ¥{{ scope.row.commission?.toFixed(3) || '0.00' }}
          </template>
        </el-table-column>
        <el-table-column prop="transferFee" label="过户费" min-width="100">
          <template #default="scope">
            ¥{{ scope.row.transferFee?.toFixed(3) || '0.00' }}
          </template>
        </el-table-column>
        <el-table-column prop="stampTax" label="印花税" min-width="100">
          <template #default="scope">
            ¥{{ scope.row.stampTax?.toFixed(3) || '0.00' }}
          </template>
        </el-table-column>
      </el-table>
      
      <div v-if="tradeRecords.length === 0" class="text-center py-8 text-gray-500">
        <p>暂无交易记录</p>
      </div>
      
      <template #footer>
        <div class="flex justify-end">
          <el-button @click="showTradeRecordsModal = false">
            关闭
          </el-button>
        </div>
      </template>
    </el-dialog>
        
    <!-- 底部信息 -->
    <footer class="page-footer">
      <p>股票投资助手 &copy; 2025 | 数据仅供参考，投资有风险，入市需谨慎</p>
      <p>更新时间: <span id="updateTime">{{ updateTime }}</span></p>
    </footer>
</div>
</template>

<style scoped>
/* 必要的全局样式 */
body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #f8fafc;
}

/* 设置Dashboard宽度为100% */
.main-content, .dashboard-content {
  width: 100% !important;
  max-width: 100% !important;
  overflow-x: hidden;
}

/* 确保所有子元素的最大宽度不超过100% */
.dashboard-content > * {
  max-width: 100% !important;
  box-sizing: border-box;
}

/* 自定义颜色类 */
.text-green-600 {
  color: #10b981;
}

.text-red-600 {
  color: #ef4444;
}

/* 图表容器 */
.chart-container {
  position: relative;
  height: 320px;
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

/* 表格容器样式 */
el-table {
  width: 100% !important;
  max-width: 100% !important;
  overflow-x: auto !important;
  box-sizing: border-box !important;
}

/* 确保表格列不会导致溢出 */
el-table-column {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 交易记录弹窗表格 */
.trade-records-dialog .el-table {
  min-width: auto !important;
}

/* 弹窗样式调整 */
.el-dialog {
  max-width: 100vw !important;
  margin: 0 auto !important;
  box-sizing: border-box !important;
}

/* 确保所有元素都使用盒模型 */
* {
  box-sizing: border-box !important;
}

.mt-5 {
  margin-top: 1.25rem;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

.mt-12 {
  margin-top: 2rem;
  text-align: center;
}

/* 第一区域主卡片样式 */
.main-card {
  height: 450px;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

/* 统计卡片样式 */
.stat-card {
  border: none;
  box-shadow: none;
  padding: 16px;
  border-radius: 8px;
}

/* 第一个区域stat-card背景色 */
.stat-card-1 {
  background-color: #eff6ff;
}

/* 第二个区域stat-card背景色 */
.stat-card-2 {
  background-color: #f0fdf4;
}

/* 第三个区域stat-card背景色 */
.stat-card-3 {
  background-color: #faf5ff;
}

/* Stat card content styles */
.card-1 {
  text-align: center;
}

/* Stat value styles */
.stat-value {
  font-size: 2rem; /* text-2xl */
  font-weight: 700; /* font-bold */
  margin-bottom: 0.25rem;
}

/* Individual stat value colors */
.value-profit {
  color: #1d4ed8; /* text-blue-700 */
}

.value-rate {
  color: #166534; /* text-green-700 */
}

.value-assets {
  color: #7e22ce; /* text-purple-700 */
}

/* Stat label styles */
.stat-label {
  color: #4b5563; /* text-gray-600 */
  margin-top: 0.25rem;
  font-size: 1.15rem; /* text-sm */
}

/* Stat change styles */
.stat-change {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 0.5rem;
  font-size: 1rem; /* text-sm */
}

/* Positive change color */
.change-positive {
  color: #15803d; /* text-green-600 */
}

/* Change icon margin */
.change-icon {
  margin-right: 0.25rem;
}

/* Trade records dialog responsive styles */
.trade-records-dialog .el-dialog {
  max-width: 800px;
  margin: 0 auto;
}

.trade-records-dialog .el-dialog__body {
  overflow-x: auto;
  padding: 20px;
}

/* Responsive adjustments for different screen sizes */
@media (max-width: 768px) {
  .trade-records-dialog .el-dialog {
    width: 95vw !important;
    max-width: none;
    margin: 10px;
  }
  
  .trade-records-dialog .el-table {
    font-size: 12px;
  }
  
  .trade-records-dialog .el-table-column__label {
    padding: 8px 4px;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .trade-records-dialog .el-dialog {
    width: 75vw !important;
    max-width: 700px;
  }
}

@media (min-width: 1025px) {
  .trade-records-dialog .el-dialog {
    width: 70vw !important;
    max-width: 800px;
  }
}

/* 全宽元素样式 */
.w-full {
  width: 100%;
}

/* Margin bottom utilities */
.mb-4 {
  margin-bottom: 1rem;
}

/* Text color utilities */
.text-gray-600 {
  color: #4b5563;
}

.text-gray-500 {
  color: #6b7280;
}

/* Text alignment */
.text-center {
  text-align: center;
}

/* Padding utilities */
.py-8 {
  padding-bottom: 2rem;
}

/* Flexbox utilities */
.flex {
  display: flex;
}

.justify-end {
  justify-content: flex-end;
}

/* Stock information container */
.stock-info-container {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

/* Stock information item */
.stock-info-item {
  flex: 1;
  min-width: 160px;
  padding: 12px;
  background-color: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e8eaed;
}

/* Info label */
.info-label {
  font-size: 13px;
  font-weight: 400;
  color: #64748b;
  margin-right: 2px;
}

/* Info value */
.info-value {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .stock-info-container {
    flex-direction: column;
    gap: 12px;
  }
  
  .stock-info-item {
    min-width: auto;
  }
}

/* Footer styles */
.page-footer {
  margin-top: 1rem;
  color: #6b7280;
  font-size: 0.75rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 0;
  text-align: center;
}

.page-footer p {
  margin: 0;
}

.page-footer p + p {
  margin-top: 0.25rem;
}

/* Dashboard内容区域样式 */
.dashboard-content {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 统计汇总区域样式 */
.summary-section {
  margin-top: 1.5rem; /* mt-6 */
  padding-top: 1.5rem; /* pt-6 */
  border-top: 1px solid #f3f4f6; /* border-t border-gray-100 */
  width: 100%;
  box-sizing: border-box;
}

/* 汇总行样式 */
.summary-section .el-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
  margin: 0;
}

/* 汇总列样式 */
.summary-section .el-col {
  flex: 1;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

/* 第一个汇总列（紧贴左边缘） */
.summary-section .el-col:first-child {
  justify-content: flex-start;
  padding-left: 0;
}

/* 最后一个汇总列（紧贴右边缘） */
.summary-section .el-col:last-child {
  justify-content: flex-end;
  padding-right: 0;
}

/* 股票信息容器 */
.stock-info-container {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}

/* Tailwind-like utility classes for dialog section */
.mb-4 {
  margin-bottom: 1rem;
}

.text-center {
  text-align: center;
}

.py-8 {
  padding-bottom: 2rem;
}

.text-gray-500 {
  color: #6b7280;
}

.flex {
  display: flex;
}

.justify-end {
  justify-content: flex-end;
}

/* 股票信息项 */
.stock-info-item {
  display: flex;
  align-items: center;
  font-size: 14px;
}

/* 信息标签样式 */
.stock-info-label {
  color: #606266;
  margin-right: 8px;
}

/* 信息值样式 */
.stock-info-value {
  font-weight: 500;
  color: #303133;
}

/* 当前价格特殊样式 */
.stock-info-current-price {
  font-size: 16px;
}

/* 中间汇总列（居中） */
.summary-section .el-col:nth-child(2) {
  justify-content: center;
}

/* 汇总标签样式 */
.summary-label {
  color: #6b7280; /* text-gray-500 */
  font-size: 1rem; /* 增大字体大小 */
}

/* 汇总值样式 */
.summary-value {
  font-weight: 600; /* font-semibold，增强视觉效果 */
  margin-left: 0.5rem; /* ml-2 */
  font-size: 1.125rem; /* 增大字体大小 */
}

/* 图表标题容器 */
.chart-header-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 图表标题 */
.chart-title {
  font-size: 1.25rem; /* text-xl */
  font-weight: 700; /* font-bold */
  color: #1f2937; /* text-gray-800 */
}

/* 持仓列表标题容器 */
.position-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.position-list-card {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.position-list-card :deep(.el-card__header) {
  flex: 0 0 auto;
}

.position-list-card :deep(.el-card__body) {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.position-list-card :deep(.el-table) {
  min-width: 1200px;
}

/* 持仓列表标题 */
.position-list-title {
  font-size: 1.25rem; /* text-xl */
  font-weight: 700; /* font-bold */
  color: #1f2937; /* text-gray-800 */
}

/* 买入按钮样式 */
.buy-button {
  margin-right: 0.75rem; /* mr-3 */
}

/* 按钮图标样式 */
.button-icon {
  margin-right: 0.25rem; /* mr-1 */
}

/* 利润图表容器 */
#profitChart {
  width: 100%;
  height: 100%;
}

/* 满宽表格 */
.full-width-table {
  width: 100%;
}

/* 列间距样式 */
.column-left {
  padding-right: 10px;
}

.column-right {
  padding-left: 10px;
}
</style>
