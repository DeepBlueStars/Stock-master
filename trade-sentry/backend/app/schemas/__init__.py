from pydantic import BaseModel, Field
from typing import Optional, List, Any
from datetime import datetime
from decimal import Decimal


# 基础响应模型
class ApiResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: Optional[Any] = None
    timestamp: int = Field(default_factory=lambda: int(datetime.utcnow().timestamp()))


# ==================== 用户相关 ====================

class UserBase(BaseModel):
    username: str
    email: Optional[str] = None
    phone: Optional[str] = None


class UserCreate(UserBase):
    password: str


class UserUpdate(BaseModel):
    email: Optional[str] = None
    phone: Optional[str] = None
    risk_level: Optional[str] = None
    max_position_count: Optional[int] = None
    stop_loss_rate: Optional[Decimal] = None


class User(UserBase):
    id: int
    total_balance: Decimal
    available_balance: Decimal
    frozen_balance: Decimal
    risk_level: str
    max_position_count: int
    stop_loss_rate: Decimal
    created_at: datetime
    
    class Config:
        from_attributes = True


class UserBalance(BaseModel):
    total: Decimal
    available: Decimal
    frozen: Decimal


# ==================== 登录认证 ====================

class LoginRequest(BaseModel):
    username: str
    password: str


class LoginResponse(BaseModel):
    token: str
    user: User


class RegisterRequest(BaseModel):
    username: str
    password: str
    email: Optional[str] = None
    phone: Optional[str] = None


# ==================== 股票相关 ====================

class BidAsk(BaseModel):
    price: Decimal
    volume: int


class Stock(BaseModel):
    code: str
    name: str
    current_price: Decimal
    open_price: Decimal
    high_price: Decimal
    low_price: Decimal
    prev_close: Decimal
    volume: int
    amount: Decimal
    change_rate: Decimal
    change_value: Decimal
    turnover_rate: Decimal
    volume_ratio: Decimal
    market_cap: Decimal
    sector: str
    is_limit_up: bool
    is_limit_down: bool
    limit_up_price: Decimal
    limit_down_price: Decimal
    bid_ask: dict


class LimitUpStock(BaseModel):
    code: str
    name: str
    board_type: str  # first, second, third, high
    limit_up_time: str
    open_count: int
    sector: str
    sector_strength: float
    is_dragon_head: bool
    volume_ratio: float
    turnover_rate: float
    market_cap: Decimal
    reason: Optional[str]
    tags: List[str]
    ai_score: float
    seal_amount: Decimal
    current_price: Decimal
    change_rate: Decimal


class Sector(BaseModel):
    code: str
    name: str
    change_rate: Decimal
    leading_stock: str
    stocks_count: int
    limit_up_count: int
    avg_turnover: float
    strength_score: float
    top_stocks: List[str]


# ==================== 持仓相关 ====================

class Position(BaseModel):
    id: int
    user_id: int
    stock_code: str
    stock_name: str
    total_volume: int
    available_volume: int
    avg_cost: Decimal
    current_price: Optional[Decimal]
    market_value: Optional[Decimal]
    profit_loss: Decimal
    profit_loss_rate: Decimal
    buy_date: datetime
    strategy: str
    status: str
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True


# ==================== 交易相关 ====================

class OrderRequest(BaseModel):
    stockCode: str = Field(..., alias="stock_code")
    stockName: str = Field(..., alias="stock_name")
    tradeType: str = Field(..., alias="trade_type")  # buy, sell
    orderType: str = Field(..., alias="order_type")  # market, limit
    volume: int = Field(..., gt=0)
    price: Decimal = Field(..., gt=0)
    strategy: Optional[str] = None  # sweep_board, queue_board, dragon_second_wave
    
    class Config:
        populate_by_name = True


class TradeRecord(BaseModel):
    id: int
    user_id: int
    stock_code: str
    stock_name: str
    trade_type: str
    trade_mode: str
    order_type: str
    volume: int
    price: Decimal
    amount: Decimal
    commission: Decimal
    tax: Decimal
    total_cost: Decimal
    strategy: str
    board_type: Optional[str]
    trade_date: datetime
    trade_time: datetime
    status: str
    error_msg: Optional[str]
    created_at: datetime
    
    class Config:
        from_attributes = True


# ==================== 风控相关 ====================

class RiskAlert(BaseModel):
    id: int
    user_id: int
    risk_type: str
    stock_code: Optional[str]
    trigger_price: Optional[Decimal]
    trigger_condition: str
    action_taken: str
    executed_at: datetime
    
    class Config:
        from_attributes = True


class RiskConfig(BaseModel):
    stop_loss_rate: Decimal = Field(default=Decimal("-6.00"))
    max_position_count: int = Field(default=3)


# ==================== 选股相关 ====================

class StockScreenerParams(BaseModel):
    sectors: Optional[List[str]] = None
    market_cap_min: Optional[Decimal] = None
    market_cap_max: Optional[Decimal] = None
    price_min: Optional[Decimal] = None
    price_max: Optional[Decimal] = None
    turnover_min: Optional[float] = None
    volume_ratio_min: Optional[float] = None
    profit_min: Optional[Decimal] = None
    revenue_min: Optional[Decimal] = None
    board_type: Optional[str] = None


class AIScore(BaseModel):
    stock_code: str
    score: float
    tech_score: float
    fund_score: float
    capital_score: float
    senti_score: float
    dragon_score: float
    reasons: List[str]


# ==================== 龙虎榜相关 ====================

class DragonTigerSeat(BaseModel):
    seat_name: str
    seat_type: str
    buy_amount: Decimal
    sell_amount: Decimal
    net_amount: Decimal
    is_famous: bool
    famous_name: Optional[str]


class DragonTigerData(BaseModel):
    date: str
    stock_code: str
    stock_name: str
    data_type: str
    details: List[DragonTigerSeat]
    summary: dict


# ==================== 情绪周期相关 ====================

class SentimentData(BaseModel):
    date: str
    timestamp: str
    up_count: int
    down_count: int
    limit_up_count: int
    limit_down_count: int
    max_board_height: int
    sentiment_score: float
    cycle_phase: str
    market_temperature: str


# ==================== 复盘相关 ====================

class DailyReview(BaseModel):
    date: str
    summary: dict
    trades: List[TradeRecord]
    statistics: dict
    recommendations: List[str]
