"""
统一行情数据模型
定义标准化的股票数据结构
"""
from typing import Optional, List, Dict
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal


@dataclass
class BidAskLevel:
    """盘口档位"""
    price: Decimal = Decimal('0')
    volume: int = 0


@dataclass
class UnifiedStockData:
    """统一的股票数据结构"""
    # 基本信息
    code: str = ''                          # 股票代码
    name: str = ''                          # 股票名称
    source: str = ''                        # 数据来源
    
    # 价格信息
    current_price: Decimal = Decimal('0')   # 当前价格
    open_price: Decimal = Decimal('0')      # 开盘价
    high_price: Decimal = Decimal('0')      # 最高价
    low_price: Decimal = Decimal('0')       # 最低价
    prev_close: Decimal = Decimal('0')      # 昨收价
    
    # 涨跌幅
    change: Decimal = Decimal('0')          # 涨跌额
    change_rate: Decimal = Decimal('0')     # 涨跌幅(%)
    
    # 成交量信息
    volume: int = 0                         # 成交量（股）
    amount: Decimal = Decimal('0')          # 成交额（元）
    turnover_rate: Decimal = Decimal('0')   # 换手率(%)
    volume_ratio: Decimal = Decimal('0')    # 量比
    
    # 盘口数据
    bid1: BidAskLevel = field(default_factory=BidAskLevel)
    bid2: BidAskLevel = field(default_factory=BidAskLevel)
    bid3: BidAskLevel = field(default_factory=BidAskLevel)
    bid4: BidAskLevel = field(default_factory=BidAskLevel)
    bid5: BidAskLevel = field(default_factory=BidAskLevel)
    ask1: BidAskLevel = field(default_factory=BidAskLevel)
    ask2: BidAskLevel = field(default_factory=BidAskLevel)
    ask3: BidAskLevel = field(default_factory=BidAskLevel)
    ask4: BidAskLevel = field(default_factory=BidAskLevel)
    ask5: BidAskLevel = field(default_factory=BidAskLevel)
    
    # 市值信息
    market_cap: Decimal = Decimal('0')      # 总市值（亿元）
    float_market_cap: Decimal = Decimal('0')  # 流通市值（亿元）
    
    # 财务指标
    pe_ratio: Optional[Decimal] = None      # 市盈率
    pb_ratio: Optional[Decimal] = None      # 市净率
    
    # 状态
    is_limit_up: bool = False               # 是否涨停
    is_limit_down: bool = False             # 是否跌停
    
    # 时间戳
    timestamp: datetime = field(default_factory=datetime.now)
    
    def to_dict(self) -> Dict:
        """转换为字典格式"""
        return {
            'code': self.code,
            'name': self.name,
            'source': self.source,
            'current_price': float(self.current_price),
            'open_price': float(self.open_price),
            'high_price': float(self.high_price),
            'low_price': float(self.low_price),
            'prev_close': float(self.prev_close),
            'change': float(self.change),
            'change_rate': float(self.change_rate),
            'volume': self.volume,
            'amount': float(self.amount),
            'turnover_rate': float(self.turnover_rate),
            'volume_ratio': float(self.volume_ratio),
            'bid_ask': {
                'bid1': {'price': float(self.bid1.price), 'volume': self.bid1.volume},
                'bid2': {'price': float(self.bid2.price), 'volume': self.bid2.volume},
                'bid3': {'price': float(self.bid3.price), 'volume': self.bid3.volume},
                'bid4': {'price': float(self.bid4.price), 'volume': self.bid4.volume},
                'bid5': {'price': float(self.bid5.price), 'volume': self.bid5.volume},
                'ask1': {'price': float(self.ask1.price), 'volume': self.ask1.volume},
                'ask2': {'price': float(self.ask2.price), 'volume': self.ask2.volume},
                'ask3': {'price': float(self.ask3.price), 'volume': self.ask3.volume},
                'ask4': {'price': float(self.ask4.price), 'volume': self.ask4.volume},
                'ask5': {'price': float(self.ask5.price), 'volume': self.ask5.volume},
            },
            'market_cap': float(self.market_cap),
            'pe_ratio': float(self.pe_ratio) if self.pe_ratio else None,
            'pb_ratio': float(self.pb_ratio) if self.pb_ratio else None,
            'is_limit_up': self.is_limit_up,
            'is_limit_down': self.is_limit_down,
            'timestamp': self.timestamp.isoformat()
        }


@dataclass
class UnifiedLimitUpStock:
    """统一的涨停股票数据结构"""
    code: str = ''
    name: str = ''
    board_type: str = 'first'               # first/second/third/high
    limit_up_time: str = ''                 # 涨停时间
    open_count: int = 0                     # 炸板次数
    sector: str = ''                        # 所属板块
    sector_strength: float = 0.0            # 板块强度
    is_dragon_head: bool = False            # 是否龙头
    
    # 量价指标
    volume_ratio: float = 0.0               # 量比
    turnover_rate: float = 0.0              # 换手率
    market_cap: Decimal = Decimal('0')      # 流通市值（亿元）
    seal_amount: Decimal = Decimal('0')     # 封单金额（万元）
    
    # 价格信息
    current_price: Decimal = Decimal('0')
    change_rate: Decimal = Decimal('0')
    
    # AI评分
    ai_score: float = 60.0
    
    # 标签
    tags: List[str] = field(default_factory=list)
    reason: str = ''
    
    # 数据来源
    source: str = ''
    
    def to_dict(self) -> Dict:
        """转换为字典格式"""
        return {
            'code': self.code,
            'name': self.name,
            'board_type': self.board_type,
            'limit_up_time': self.limit_up_time,
            'open_count': self.open_count,
            'sector': self.sector,
            'sector_strength': self.sector_strength,
            'is_dragon_head': self.is_dragon_head,
            'volume_ratio': self.volume_ratio,
            'turnover_rate': self.turnover_rate,
            'market_cap': float(self.market_cap),
            'seal_amount': float(self.seal_amount),
            'current_price': float(self.current_price),
            'change_rate': float(self.change_rate),
            'ai_score': self.ai_score,
            'tags': self.tags,
            'reason': self.reason,
            'source': self.source
        }


@dataclass
class UnifiedSector:
    """统一的板块数据结构"""
    code: str = ''
    name: str = ''
    change_rate: Decimal = Decimal('0')     # 涨跌幅
    stocks_count: int = 0                   # 股票总数
    limit_up_count: int = 0                 # 涨停家数
    limit_down_count: int = 0               # 跌停家数
    leading_stock: str = ''                 # 龙头股代码
    top_stocks: List[str] = field(default_factory=list)
    strength_score: float = 0.0             # 强度评分
    source: str = ''
    
    def to_dict(self) -> Dict:
        return {
            'code': self.code,
            'name': self.name,
            'change_rate': float(self.change_rate),
            'stocks_count': self.stocks_count,
            'limit_up_count': self.limit_up_count,
            'limit_down_count': self.limit_down_count,
            'leading_stock': self.leading_stock,
            'top_stocks': self.top_stocks,
            'strength_score': self.strength_score,
            'source': self.source
        }
