from datetime import datetime
import tushare as ts
import pandas as pd
from typing import List
from data_fetcher.models import UnifiedLimitUpStock
from decimal import Decimal

class TushareAPI:
    def __init__(self):
        self.token = 'c1151554d70cf6c7a448fbf2b4278968fd3496f8c8e8d8cd2b6e4319'
        self.pro = ts.pro_api(self.token)
        self.name = 'tushare'
    
    async def get_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """获取涨停股票列表"""
        try:
            # 获取当日日期
            today = datetime.now().strftime('%Y%m%d')
            
            # 调用Tushare接口获取当日行情数据
            df = self.pro.daily(trade_date=today)
            
            if df.empty:
                return []
            
            # 筛选涨停股票（涨跌幅 >= 9.9%）
            limit_up_stocks = df[df['pct_chg'] >= 9.9]
            
            result = self._parse_limit_up_data(limit_up_stocks)
            if result:
                return result
            
            # 如果没有找到涨停股票，返回空列表
            return []
            
        except Exception as e:
            print(f"[TushareAPI] 获取涨停股票失败: {e}")
            return []
    
    def _parse_limit_up_data(self, data) -> List[UnifiedLimitUpStock]:
        """解析涨停股票数据"""
        stocks = []
        
        try:
            for _, item in data.iterrows():
                stock = UnifiedLimitUpStock()
                stock.code = item.get('ts_code', '')
                stock.name = self._get_stock_name(stock.code)
                stock.current_price = Decimal(str(item.get('close', 0)))
                stock.change_rate = Decimal(str(item.get('pct_chg', 0)))
                
                # 估算换手率（使用成交量/流通股本，这里简化处理）
                stock.turnover_rate = Decimal(str(item.get('turnover_rate', 0)))
                stock.volume_ratio = float(stock.turnover_rate) * 2  # 估算量比
                
                # 估算市值
                stock.market_cap = Decimal(str(item.get('total_mv', 0))) / 10000  # 转换为亿
                
                # 估算封单金额
                stock.seal_amount = Decimal(str(item.get('amount', 0))) / 10000 * Decimal('0.1')
                
                # 计算板类型（简化处理）
                stock.board_type = 'first'  # 默认为首板
                stock.limit_up_time = "09:35:00"  # 简化处理
                stock.sector = self._get_stock_sector(stock.code)
                stock.sector_strength = 7.5
                stock.ai_score = 70 + int(stock.change_rate * 10)
                stock.tags = [stock.board_type]
                stock.source = 'tushare'
                
                stocks.append(stock)
            
            print(f"[TushareAPI] 找到 {len(stocks)} 只涨停股票")
            return stocks
            
        except Exception as e:
            print(f"[TushareAPI] 解析涨停数据失败: {e}")
            return []
    
    def _get_stock_name(self, ts_code: str) -> str:
        """根据股票代码获取股票名称"""
        try:
            # 获取股票基本信息
            stock_basic = self.pro.stock_basic(ts_code=ts_code)
            if not stock_basic.empty:
                return stock_basic['name'].iloc[0]
            return ''
        except:
            return ''
    
    def _get_stock_sector(self, ts_code: str) -> str:
        """根据股票代码获取所属行业"""
        try:
            # 获取股票基本信息
            stock_basic = self.pro.stock_basic(ts_code=ts_code)
            if not stock_basic.empty:
                industry = stock_basic['industry'].iloc[0]
                return industry if pd.notna(industry) else "未分类"
            return "未分类"
        except:
            return "未分类"
