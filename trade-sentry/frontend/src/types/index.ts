// ============================================
// TypeScript 类型定义
// ============================================

// 基础响应类型
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

// 用户类型
export interface User {
  id: number
  username: string
  email?: string
  phone?: string
  totalBalance: number
  availableBalance: number
  frozenBalance: number
  maxPositionCount: number
  stopLossRate: number
  createdAt: string
}

// 登录/注册
export interface LoginCredentials {
  username: string
  password: string
}

export interface RegisterData extends LoginCredentials {
  email?: string
  phone?: string
}

// 股票类型
export interface Stock {
  code: string
  name: string
  currentPrice: number
  openPrice: number
  highPrice: number
  lowPrice: number
  prevClose: number
  volume: number
  amount: number
  changeRate: number
  changeValue: number
  turnoverRate: number
  volumeRatio: number
  marketCap: number
  sector: string
  isLimitUp: boolean
  isLimitDown: boolean
  limitUpPrice: number
  limitDownPrice: number
  bidAsk: BidAsk
}

export interface BidAsk {
  bid1: { price: number; volume: number }
  bid2: { price: number; volume: number }
  bid3: { price: number; volume: number }
  bid4: { price: number; volume: number }
  bid5: { price: number; volume: number }
  ask1: { price: number; volume: number }
  ask2: { price: number; volume: number }
  ask3: { price: number; volume: number }
  ask4: { price: number; volume: number }
  ask5: { price: number; volume: number }
}

// 涨停股票类型
export type BoardType = 'first' | 'second' | 'third' | 'high'

export interface LimitUpStock {
  code: string
  name: string
  boardType: BoardType
  limitUpTime: string
  openCount: number
  sector: string
  sectorStrength: number
  isDragonHead: boolean
  volumeRatio: number
  turnoverRate: number
  marketCap: number
  reason?: string
  tags: string[]
  aiScore: number
  sealAmount: number
  currentPrice: number
  changeRate: number
}

// 板块类型
export interface Sector {
  code: string
  name: string
  changeRate: number
  leadingStock: string
  stocksCount: number
  limitUpCount: number
  avgTurnover: number
  strengthScore: number
  topStocks: string[]
}

// 持仓类型
export interface Position {
  id: number
  userId: number
  stockCode: string
  stockName: string
  totalVolume: number
  availableVolume: number
  avgCost: number
  currentPrice: number
  marketValue: number
  profitLoss: number
  profitLossRate: number
  buyDate: string
  strategy: string
  status: 'holding' | 'closed'
  createdAt: string
  updatedAt: string
}

// 交易记录类型
export type TradeType = 'buy' | 'sell'
export type TradeMode = 'manual'
export type OrderType = 'market' | 'limit'
export type OrderStatus = 'pending' | 'filled' | 'partial_filled' | 'cancelled' | 'failed'
export type Strategy = 'sweep_board' | 'queue_board' | 'dragon_second_wave'

export interface TradeRecord {
  id: number
  userId: number
  stockCode: string
  stockName: string
  tradeType: TradeType
  tradeMode: TradeMode
  orderType: OrderType
  volume: number
  price: number
  amount: number
  commission: number
  tax: number
  totalCost: number
  strategy: Strategy
  boardType: BoardType
  tradeDate: string
  tradeTime: string
  status: OrderStatus
  errorMsg?: string
  createdAt: string
}

// 订单类型
export interface Order {
  stockCode: string
  stockName: string
  tradeType: TradeType
  orderType: OrderType
  volume: number
  price: number
  strategy?: Strategy
}

// 风控类型
export type RiskType = 'stop_loss' | 'take_profit' | 'position_limit' | 'market_extreme' | 'manual_stop'

export interface RiskAlert {
  id: number
  userId: number
  riskType: RiskType
  stockCode?: string
  triggerPrice?: number
  triggerCondition: string
  actionTaken: string
  executedAt: string
}

// 选股条件类型
export interface StockScreenerParams {
  sectors?: string[]
  marketCapMin?: number
  marketCapMax?: number
  priceMin?: number
  priceMax?: number
  turnoverMin?: number
  volumeRatioMin?: number
  profitMin?: number
  revenueMin?: number
  boardType?: BoardType
}

// AI评分类型
export interface AIScore {
  stockCode: string
  score: number
  techScore: number
  fundScore: number
  capitalScore: number
  sentiScore: number
  dragonScore: number
  reasons: string[]
}

// 龙虎榜类型
export interface DragonTigerSeat {
  seatName: string
  seatType: 'retail' | 'institution' | 'hot_money'
  buyAmount: number
  sellAmount: number
  netAmount: number
  isFamous: boolean
  famousName?: string
}

export interface DragonTigerData {
  date: string
  stockCode: string
  stockName: string
  dataType: 'buy' | 'sell'
  details: DragonTigerSeat[]
  summary: {
    totalBuy: number
    totalSell: number
    institutionBuy: number
    institutionSell: number
  }
}

// 情绪周期类型
export type SentimentPhase = 'cold' | 'recovery' | 'high_tide' | 'decline'

export interface SentimentData {
  date: string
  timestamp: string
  upCount: number
  downCount: number
  limitUpCount: number
  limitDownCount: number
  maxBoardHeight: number
  sentimentScore: number
  cyclePhase: SentimentPhase
  marketTemperature: 'cold' | 'warm' | 'hot'
}

// 复盘报告类型
export interface DailyReview {
  date: string
  summary: {
    market: {
      indexChange: number
      upCount: number
      downCount: number
      limitUpCount: number
      limitDownCount: number
      sentiment: string
    }
    limitUpAnalysis: {
      firstBoard: number
      secondBoard: number
      thirdBoard: number
      highBoard: number
      avgLimitUpTime: string
      maxBoardHeight: number
    }
    sectors: Sector[]
    dragonTiger: DragonTigerData[]
  }
  trades: TradeRecord[]
  statistics: {
    totalTrades: number
    successTrades: number
    failedTrades: number
    totalProfit: number
    profitRate: number
    maxDrawdown: number
  }
  recommendations: string[]
}

// WebSocket消息类型
export type WebSocketMessageType = 
  | 'price_update'
  | 'limit_up_alert'
  | 'board_broken'
  | 'order_filled'
  | 'risk_triggered'
  | 'market_extreme'

export interface WebSocketMessage {
  type: WebSocketMessageType
  data: any
  timestamp: number
}

// 路由元信息
export interface RouteMeta {
  title: string
  icon?: string
  requiresAuth?: boolean
  hidden?: boolean
}
