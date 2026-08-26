import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, post, del } from '@/api/request'
import type { Position, TradeRecord, Order, RiskAlert } from '@/types'

export const usePositionStore = defineStore('position', () => {
  // State
  const positions = ref<Position[]>([])
  const todayTrades = ref<TradeRecord[]>([])
  const riskAlerts = ref<RiskAlert[]>([])
  const loading = ref(false)
  
  // Getters
  const holdingPositions = computed(() => 
    positions.value.filter(p => p.status === 'holding')
  )
  
  const closedPositions = computed(() => 
    positions.value.filter(p => p.status === 'closed')
  )
  
  const totalProfit = computed(() => {
    return holdingPositions.value.reduce((sum, p) => sum + p.profitLoss, 0)
  })
  
  const totalMarketValue = computed(() => {
    return holdingPositions.value.reduce((sum, p) => sum + p.marketValue, 0)
  })
  
  const canOpenPosition = computed(() => {
    return holdingPositions.value.length < 3 // 最多3只
  })
  
  // Actions
  const fetchPositions = async () => {
    loading.value = true
    try {
      const res = await get<Position[]>('/positions')
      positions.value = res
    } catch (error) {
      console.error('获取持仓失败', error)
    } finally {
      loading.value = false
    }
  }
  
  const fetchTodayTrades = async () => {
    try {
      const today = new Date().toISOString().split('T')[0]
      const res = await get<TradeRecord[]>(`/review/trades?date=${today}`)
      todayTrades.value = res
    } catch (error) {
      console.error('获取今日交易失败', error)
    }
  }
  
  const createOrder = async (order: Order) => {
    try {
      const res = await post<TradeRecord>('/trade/order', order)
      return res
    } catch (error) {
      console.error('创建订单失败', error)
      return null
    }
  }
  
  const closePosition = async (positionId: number) => {
    try {
      const res = await post<TradeRecord>(`/position/close/${positionId}`)
      await fetchPositions()
      return res
    } catch (error) {
      console.error('平仓失败', error)
      return null
    }
  }
  
  const closeAllPositions = async () => {
    try {
      await post('/position/close_all')
      await fetchPositions()
      return true
    } catch (error) {
      console.error('一键清仓失败', error)
      return false
    }
  }
  
  const cancelOrder = async (orderId: number) => {
    try {
      await del(`/trade/order/${orderId}`)
      return true
    } catch (error) {
      console.error('撤单失败', error)
      return false
    }
    }
  
  const cancelAllOrders = async () => {
    try {
      await post('/trade/cancel_all')
      return true
    } catch (error) {
      console.error('一键撤单失败', error)
      return false
    }
  }
  
  const fetchRiskAlerts = async () => {
    try {
      const res = await get<RiskAlert[]>('/system/risk_alerts')
      riskAlerts.value = res
    } catch (error) {
      console.error('获取风控提醒失败', error)
    }
  }
  
  return {
    positions,
    todayTrades,
    riskAlerts,
    loading,
    holdingPositions,
    closedPositions,
    totalProfit,
    totalMarketValue,
    canOpenPosition,
    fetchPositions,
    fetchTodayTrades,
    createOrder,
    closePosition,
    closeAllPositions,
    cancelOrder,
    cancelAllOrders,
    fetchRiskAlerts
  }
})
