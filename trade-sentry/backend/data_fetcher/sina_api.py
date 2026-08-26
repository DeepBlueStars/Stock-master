"""
新浪财经API
使用统一数据模型，带重试机制
"""
import re
from decimal import Decimal
from typing import Optional, List, Dict
from data_fetcher.base_fetcher import BaseDataFetcher, RetryConfig
from data_fetcher.models import (
    UnifiedStockData, BidAskLevel, 
    UnifiedLimitUpStock, UnifiedSector
)


class SinaAPI(BaseDataFetcher):
    """新浪财经API封装"""
    
    def __init__(self):
        super().__init__(
            name="SinaAPI",
            retry_config=RetryConfig(max_retries=3, retry_delay=0.5)
        )
        self.base_url = "https://hq.sinajs.cn"
    
    async def get_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """
        获取单只股票实时行情
        
        Args:
            code: 股票代码（如 '000001'）
            
        Returns:
            UnifiedStockData或None
        """
        return await self.fetch_with_retry(self._fetch_stock_price, code)
    
    async def _fetch_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """实际获取股票价格的函数"""
        sina_code = self.format_stock_code(code)
        url = f"{self.base_url}/list={sina_code}"
        
        response = await self.http_get(
            url,
            headers={'Referer': 'https://finance.sina.com.cn'}
        )
        
        if not response or 'text' not in response:
            return None
        
        return self._parse_sina_data(code, response['text'])
    
    def _parse_sina_data(self, code: str, text: str) -> Optional[UnifiedStockData]:
        """
        解析新浪返回的数据
        
        数据格式: var hq_str_sh600000="股票名称,今日开盘价,昨日收盘价,当前价,..."
        """
        try:
            # 提取引号中的数据
            pattern = r'var hq_str_[^=]+="([^"]*)"'
            match = re.search(pattern, text)
            
            if not match:
                return None
            
            data_str = match.group(1)
            if not data_str or data_str.strip() == '':
                return None
            
            parts = data_str.split(',')
            if len(parts) < 33:
                print(f"[SinaAPI] 数据格式不完整: {len(parts)} 个字段")
                return None
            
            # 构建统一数据模型
            stock = UnifiedStockData()
            stock.code = code
            stock.name = parts[0]
            stock.source = 'sina'
            
            # 价格数据
            stock.open_price = Decimal(parts[1]) if parts[1] else Decimal('0')
            stock.prev_close = Decimal(parts[2]) if parts[2] else Decimal('0')
            stock.current_price = Decimal(parts[3]) if parts[3] else Decimal('0')
            stock.high_price = Decimal(parts[4]) if parts[4] else Decimal('0')
            stock.low_price = Decimal(parts[5]) if parts[5] else Decimal('0')
            
            # 成交量和成交额
            stock.volume = int(parts[8]) if parts[8] else 0
            stock.amount = Decimal(parts[9]) if parts[9] else Decimal('0')
            
            # 盘口数据
            stock.bid1 = BidAskLevel(
                price=Decimal(parts[11]) if parts[11] else Decimal('0'),
                volume=int(parts[10]) if parts[10] else 0
            )
            stock.bid2 = BidAskLevel(
                price=Decimal(parts[13]) if parts[13] else Decimal('0'),
                volume=int(parts[12]) if parts[12] else 0
            )
            stock.bid3 = BidAskLevel(
                price=Decimal(parts[15]) if parts[15] else Decimal('0'),
                volume=int(parts[14]) if parts[14] else 0
            )
            stock.bid4 = BidAskLevel(
                price=Decimal(parts[17]) if parts[17] else Decimal('0'),
                volume=int(parts[16]) if parts[16] else 0
            )
            stock.bid5 = BidAskLevel(
                price=Decimal(parts[19]) if parts[19] else Decimal('0'),
                volume=int(parts[18]) if parts[18] else 0
            )
            
            stock.ask1 = BidAskLevel(
                price=Decimal(parts[21]) if parts[21] else Decimal('0'),
                volume=int(parts[20]) if parts[20] else 0
            )
            stock.ask2 = BidAskLevel(
                price=Decimal(parts[23]) if parts[23] else Decimal('0'),
                volume=int(parts[22]) if parts[22] else 0
            )
            stock.ask3 = BidAskLevel(
                price=Decimal(parts[25]) if parts[25] else Decimal('0'),
                volume=int(parts[24]) if parts[24] else 0
            )
            stock.ask4 = BidAskLevel(
                price=Decimal(parts[27]) if parts[27] else Decimal('0'),
                volume=int(parts[26]) if parts[26] else 0
            )
            stock.ask5 = BidAskLevel(
                price=Decimal(parts[29]) if parts[29] else Decimal('0'),
                volume=int(parts[28]) if parts[28] else 0
            )
            
            # 计算涨跌幅
            if stock.prev_close > 0:
                stock.change = stock.current_price - stock.prev_close
                stock.change_rate = (stock.change / stock.prev_close) * 100
            
            # 判断是否涨停/跌停
            if stock.prev_close > 0:
                limit_up_price = stock.prev_close * Decimal('1.1')
                limit_down_price = stock.prev_close * Decimal('0.9')
                stock.is_limit_up = stock.current_price >= limit_up_price
                stock.is_limit_down = stock.current_price <= limit_down_price
            
            return stock
            
        except Exception as e:
            print(f"[SinaAPI] 解析数据失败: {e}")
            return None
    
    async def get_batch_stock_prices(self, codes: List[str]) -> List[UnifiedStockData]:
        """批量获取股票行情"""
        results = []
        for code in codes:
            stock = await self.get_stock_price(code)
            if stock:
                results.append(stock)
        return results
    
    async def get_all_stocks(self) -> List[UnifiedStockData]:
        """获取全市场股票列表"""
        # 新浪没有提供全市场股票列表API，返回空列表
        return []
    
    async def get_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """获取涨停股票列表"""
        # 新浪没有直接提供涨停列表API，返回空列表
        return []
    
    async def get_sectors(self) -> List[UnifiedSector]:
        """获取板块列表"""
        # 新浪没有提供板块API，返回空列表
        return []


# 测试代码
if __name__ == "__main__":
    async def test():
        async with SinaAPI() as api:
            # 测试单只股票
            result = await api.get_stock_price("000001")
            if result:
                print(f"股票数据: {result.to_dict()}")
            else:
                print("获取股票数据失败")
    
    import asyncio
    asyncio.run(test())
