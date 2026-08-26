from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Boolean, Enum, Text
from sqlalchemy.types import DECIMAL as Decimal
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base


class User(Base):
    """用户表"""
    __tablename__ = "users"
    
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    email = Column(String(100), nullable=True)
    phone = Column(String(20), nullable=True)
    
    # 资金信息
    total_balance = Column(Decimal(15, 2), default=10000.00)
    available_balance = Column(Decimal(15, 2), default=10000.00)
    frozen_balance = Column(Decimal(15, 2), default=0.00)
    
    # 风控设置
    risk_level = Column(Enum('low', 'medium', 'high'), default='high')
    max_position_count = Column(Integer, default=3)
    stop_loss_rate = Column(Decimal(5, 2), default=-6.00)
    
    # 状态
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    last_login = Column(DateTime, nullable=True)
    
    # 关联
    positions = relationship("Position", back_populates="user", cascade="all, delete-orphan")
    trades = relationship("TradeRecord", back_populates="user", cascade="all, delete-orphan")


class Position(Base):
    """持仓表"""
    __tablename__ = "positions"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), nullable=False)
    
    # 持仓信息
    total_volume = Column(Integer, nullable=False)
    available_volume = Column(Integer, nullable=False)
    avg_cost = Column(Decimal(10, 4), nullable=False)
    current_price = Column(Decimal(10, 4), nullable=True)
    market_value = Column(Decimal(15, 2), nullable=True)
    
    # 盈亏
    profit_loss = Column(Decimal(15, 2), default=0)
    profit_loss_rate = Column(Decimal(8, 4), default=0)
    
    # 交易信息
    buy_date = Column(DateTime, nullable=False)
    strategy = Column(Enum('first_board', 'second_board', 'third_board', 'dragon_head', 'bounce_back'), nullable=False)
    status = Column(Enum('holding', 'closed'), default='holding')
    
    # 时间戳
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    closed_at = Column(DateTime, nullable=True)
    
    # 关联
    user = relationship("User", back_populates="positions")


class TradeRecord(Base):
    """交易记录表"""
    __tablename__ = "trade_records"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), nullable=False)
    
    # 交易信息
    trade_type = Column(Enum('buy', 'sell'), nullable=False)
    trade_mode = Column(Enum('manual'), default='manual')
    order_type = Column(Enum('market', 'limit'), nullable=False)
    
    # 成交信息
    volume = Column(Integer, nullable=False)
    price = Column(Decimal(10, 4), nullable=False)
    amount = Column(Decimal(15, 2), nullable=False)
    
    # 费用
    commission = Column(Decimal(10, 2), default=0)
    tax = Column(Decimal(10, 2), default=0)
    total_cost = Column(Decimal(15, 2), nullable=False)
    
    # 策略
    strategy = Column(Enum('sweep_board', 'queue_board', 'dragon_second_wave'), nullable=False)
    board_type = Column(Enum('first', 'second', 'third', 'high'), nullable=True)
    
    # 时间
    trade_date = Column(DateTime, nullable=False)
    trade_time = Column(DateTime, nullable=False)
    
    # 状态
    status = Column(Enum('pending', 'filled', 'partial_filled', 'cancelled', 'failed'), default='pending')
    error_msg = Column(Text, nullable=True)
    
    # 时间戳
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # 关联
    user = relationship("User", back_populates="trades")


class RiskLog(Base):
    """风控记录表"""
    __tablename__ = "risk_logs"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    
    # 风控信息
    risk_type = Column(Enum('stop_loss', 'take_profit', 'position_limit', 'market_extreme', 'manual_stop'), nullable=False)
    stock_code = Column(String(10), nullable=True)
    trigger_price = Column(Decimal(10, 4), nullable=True)
    trigger_condition = Column(Text, nullable=False)
    action_taken = Column(Text, nullable=False)
    
    # 时间戳
    executed_at = Column(DateTime, default=datetime.utcnow)


class LimitUpStock(Base):
    """涨停股票表"""
    __tablename__ = "limit_up_stocks"
    
    id = Column(Integer, primary_key=True, index=True)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), nullable=False)
    
    # 价格信息
    current_price = Column(Decimal(10, 4), nullable=False)
    change_rate = Column(Decimal(8, 4), nullable=False)
    change_amount = Column(Decimal(10, 4), nullable=False)
    open_price = Column(Decimal(10, 4), nullable=False)
    high_price = Column(Decimal(10, 4), nullable=False)
    low_price = Column(Decimal(10, 4), nullable=False)
    pre_close = Column(Decimal(10, 4), nullable=False)
    
    # 交易信息
    volume = Column(Integer, nullable=False)  # 成交量(手)
    amount = Column(Decimal(15, 2), nullable=False)  # 成交额(元)
    amplitude = Column(Decimal(8, 4), nullable=False)  # 振幅
    turnover_rate = Column(Decimal(8, 4), nullable=False)  # 换手率
    pe_ttm = Column(Decimal(10, 2), nullable=True)  # 市盈率(TTM)
    pb = Column(Decimal(10, 2), nullable=True)  # 市净率
    
    # 市值信息
    total_market_cap = Column(Decimal(15, 2), nullable=True)  # 总市值
    circulating_market_cap = Column(Decimal(15, 2), nullable=True)  # 流通市值
    
    # 涨停信息
    limit_up_price = Column(Decimal(10, 4), nullable=False)  # 涨停价
    limit_down_price = Column(Decimal(10, 4), nullable=False)  # 跌停价
    main_inflow = Column(Decimal(15, 2), nullable=True)  # 主力净流入
    
    # 市场类型
    market_type = Column(String(10), nullable=False)  # 深市/沪市
    
    # 时间信息
    capture_date = Column(DateTime, nullable=False, index=True)  # 抓取日期
    
    # 时间戳
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


# 导出所有模型
__all__ = ['User', 'Position', 'TradeRecord', 'RiskLog', 'LimitUpStock', 'Base']
