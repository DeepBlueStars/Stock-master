import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get } from '@/api/request'
import type { LimitUpStock, Sector, Stock, StockScreenerParams, AIScore } from '@/types'

export const useMarketStore = defineStore('market', () => {
  // State
  const limitUpStocks = ref<LimitUpStock[]>([])
  const sectors = ref<Sector[]>([])
  const selectedSector = ref<string | null>(null)
  const stockDetail = ref<Stock | null>(null)
  const loading = ref(false)
  const aiScores = ref<Map<string, AIScore>>(new Map())
  
  // Getters
  const sortedLimitUpStocks = computed(() => {
    return [...limitUpStocks.value].sort((a, b) => {
      // 按板类型排序：首板 > 二板 > 三板 > 高位
      const boardTypeOrder = { first: 0, second: 1, third: 2, high: 3 }
      const typeDiff = boardTypeOrder[a.boardType] - boardTypeOrder[b.boardType]
      if (typeDiff !== 0) return typeDiff
      
      // 同板类型按涨停时间排序
      return a.limitUpTime.localeCompare(b.limitUpTime)
    })
  })
  
  const firstBoardStocks = computed(() => 
    limitUpStocks.value.filter(s => s.boardType === 'first')
  )
  
  const secondBoardStocks = computed(() => 
    limitUpStocks.value.filter(s => s.boardType === 'second')
  )
  
  const thirdBoardStocks = computed(() => 
    limitUpStocks.value.filter(s => s.boardType === 'third')
  )
  
  const dragonHeadStocks = computed(() => 
    limitUpStocks.value.filter(s => s.isDragonHead)
  )
  
  const filteredBySector = computed(() => {
    if (!selectedSector.value) return limitUpStocks.value
    return limitUpStocks.value.filter(s => s.sector === selectedSector.value)
  })
  
  // 工具函数：将蛇形命名转换为驼峰命名
  const snakeToCamel = (obj: any): any => {
    if (Array.isArray(obj)) {
      return obj.map(item => snakeToCamel(item))
    } else if (obj && typeof obj === 'object') {
      const camelObj: any = {}
      for (const key in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, key)) {
          const camelKey = key.replace(/(_[a-z])/g, (g) => g[1].toUpperCase())
          camelObj[camelKey] = snakeToCamel(obj[key])
        }
      }
      return camelObj
    }
    return obj
  }

  // Actions
  const fetchLimitUpStocks = async () => {
    loading.value = true
    try {
      const res = await get<any[]>('/market/limitup')
      // 转换字段名格式
      const transformedStocks = snakeToCamel(res)
      limitUpStocks.value = transformedStocks
    } catch (error) {
      console.error('获取涨停股票失败', error)
    } finally {
      loading.value = false
    }
  }
  
  const fetchSectors = async () => {
    try {
      const res = await get<any[]>('/market/sectors')
      // 转换字段名格式
      const transformedSectors = snakeToCamel(res)
      sectors.value = transformedSectors
    } catch (error) {
      console.error('获取板块数据失败', error)
    }
  }
  
  const fetchStockDetail = async (code: string) => {
    try {
      const res = await get<Stock>(`/market/stock/${code}`)
      const transformedStock = snakeToCamel(res)
      stockDetail.value = transformedStock
      return transformedStock
    } catch (error) {
      console.error('获取股票详情失败', error)
      return null
    }
  }
  
  const screenStocks = async (params: StockScreenerParams) => {
    loading.value = true
    try {
      const res = await get<LimitUpStock[]>('/screener/scan', params)
      return res
    } catch (error) {
      console.error('选股失败', error)
      return []
    } finally {
      loading.value = false
    }
  }
  
  const fetchAIScore = async (code: string) => {
    try {
      const res = await get<AIScore>(`/screener/ai_score?code=${code}`)
      aiScores.value.set(code, res)
      return res
    } catch (error) {
      console.error('获取AI评分失败', error)
      return null
    }
  }
  
  const setSelectedSector = (sector: string | null) => {
    selectedSector.value = sector
  }
  
  return {
    limitUpStocks,
    sectors,
    selectedSector,
    stockDetail,
    loading,
    aiScores,
    sortedLimitUpStocks,
    firstBoardStocks,
    secondBoardStocks,
    thirdBoardStocks,
    dragonHeadStocks,
    filteredBySector,
    fetchLimitUpStocks,
    fetchSectors,
    fetchStockDetail,
    screenStocks,
    fetchAIScore,
    setSelectedSector
  }
})
