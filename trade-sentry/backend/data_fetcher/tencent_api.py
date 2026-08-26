"""
腾讯财经API
作为备用数据源
"""
from decimal import Decimal
from typing import Optional, List
from data_fetcher.base_fetcher import BaseDataFetcher, RetryConfig
from data_fetcher.models import UnifiedStockData, BidAskLevel, UnifiedLimitUpStock, UnifiedSector


class TencentAPI(BaseDataFetcher):
    """腾讯财经API封装"""
    
    def __init__(self):
        super().__init__(
            name="TencentAPI",
            retry_config=RetryConfig(max_retries=2, retry_delay=0.5)
        )
        self.base_url = "https://qt.gtimg.cn"
    
    async def get_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """
        获取单只股票实时行情
        
        API: https://qt.gtimg.cn/q=sh600000
        返回格式: v_sh600000="股票名称~代码~当前价~昨收~今开~成交量~外盘~内盘~...";
        """
        return await self.fetch_with_retry(self._fetch_stock_price, code)
    
    async def _fetch_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """实际获取股票价格的函数"""
        sina_code = self.format_stock_code(code)
        url = f"{self.base_url}/q={sina_code}"
        
        response = await self.http_get(url)
        
        if not response or 'text' not in response:
            return None
        
        return self._parse_tencent_data(code, response['text'])
    
    def _parse_tencent_data(self, code: str, text: str) -> Optional[UnifiedStockData]:
        """解析腾讯返回的数据"""
        try:
            # 数据格式: v_sh600000="股票名称~代码~当前价~昨收~今开~...";
            import re
            
            # 提取引号中的数据
            pattern = r'v_[^=]+="([^"]*)"'
            match = re.search(pattern, text)
            
            if not match:
                return None
            
            data_str = match.group(1)
            if not data_str:
                return None
            
            parts = data_str.split('~')
            if len(parts) < 45:
                print(f"[TencentAPI] 数据格式不完整: {len(parts)} 个字段")
                return None
            
            # 构建统一数据模型
            # 字段索引说明:
            # 0: 股票名称, 1: 代码, 2: 当前价, 3: 昨收, 4: 今开
            # 5: 成交量(手), 6-7: 外盘/内盘
            # 32-41: 买卖5档
            
            stock = UnifiedStockData()
            stock.code = code
            stock.name = parts[0]
            stock.source = 'tencent'
            
            # 价格数据
            stock.current_price = Decimal(str(parts[2])) if parts[2] else Decimal('0')
            stock.prev_close = Decimal(str(parts[3])) if parts[3] else Decimal('0')
            stock.open_price = Decimal(str(parts[4])) if parts[4] else Decimal('0')
            stock.high_price = Decimal(str(parts[32])) if parts[32] else Decimal('0')
            stock.low_price = Decimal(str(parts[33])) if parts[33] else Decimal('0')
            
            # 成交量（转换为股）
            stock.volume = int(parts[5]) * 100 if parts[5] else 0
            
            # 成交额（腾讯不提供，用估算）
            stock.amount = stock.current_price * Decimal(stock.volume)
            
            # 盘口数据
            # 买1-5: 9, 11, 13, 15, 17 (价格), 10, 12, 14, 16, 18 (数量)
            stock.bid1 = BidAskLevel(
                price=Decimal(str(parts[9])) if parts[9] else Decimal('0'),
                volume=int(float(parts[10])) if parts[10] else 0
            )
            stock.bid2 = BidAskLevel(
                price=Decimal(str(parts[11])) if parts[11] else Decimal('0'),
                volume=int(float(parts[12])) if parts[12] else 0
            )
            stock.bid3 = BidAskLevel(
                price=Decimal(str(parts[13])) if parts[13] else Decimal('0'),
                volume=int(float(parts[14])) if parts[14] else 0
            )
            stock.bid4 = BidAskLevel(
                price=Decimal(str(parts[15])) if parts[15] else Decimal('0'),
                volume=int(float(parts[16])) if parts[16] else 0
            )
            stock.bid5 = BidAskLevel(
                price=Decimal(str(parts[17])) if parts[17] else Decimal('0'),
                volume=int(float(parts[18])) if parts[18] else 0
            )

            # 卖1-5: 19, 21, 23, 25, 27 (价格), 20, 22, 24, 26, 28 (数量)
            stock.ask1 = BidAskLevel(
                price=Decimal(str(parts[19])) if parts[19] else Decimal('0'),
                volume=int(float(parts[20])) if parts[20] else 0
            )
            stock.ask2 = BidAskLevel(
                price=Decimal(str(parts[21])) if parts[21] else Decimal('0'),
                volume=int(float(parts[22])) if parts[22] else 0
            )
            stock.ask3 = BidAskLevel(
                price=Decimal(str(parts[23])) if parts[23] else Decimal('0'),
                volume=int(float(parts[24])) if parts[24] else 0
            )
            stock.ask4 = BidAskLevel(
                price=Decimal(str(parts[25])) if parts[25] else Decimal('0'),
                volume=int(float(parts[26])) if parts[26] else 0
            )
            stock.ask5 = BidAskLevel(
                price=Decimal(str(parts[27])) if parts[27] else Decimal('0'),
                volume=int(float(parts[28])) if parts[28] else 0
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
            print(f"[TencentAPI] 解析数据失败: {e}")
            return None
    
    async def get_batch_stock_prices(self, codes: List[str]) -> List[UnifiedStockData]:
        """批量获取股票行情"""
        # 腾讯支持批量查询，格式: q=sh600000,sz000001
        if not codes:
            return []

        codes_str = ','.join([self.format_stock_code(c) for c in codes])
        url = f"{self.base_url}/q={codes_str}"

        response = await self.http_get(url)

        if not response or 'text' not in response:
            return []

        results = []
        lines = response['text'].strip().split('\n')

        for line in lines:
            # 提取股票代码
            import re
            match = re.search(r'v_(sh\d+|sz\d+)=', line)
            if match:
                sina_code = match.group(1)
                code = self.parse_stock_code(sina_code)
                stock = self._parse_tencent_data(code, line)
                if stock:
                    results.append(stock)

        return results

    async def get_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """获取涨停股票列表"""
        # 腾讯没有直接的涨停API，返回空列表
        return []

    async def get_sectors(self) -> List[UnifiedSector]:
        """获取板块列表"""
        # 腾讯没有直接的板块API，返回空列表
        return []


# 测试代码
if __name__ == "__main__":
    async def test():
        async with TencentAPI() as api:
            # 测试单只股票
            print("=== 测试腾讯财经API ===")
            result = await api.get_stock_price("000001")
            if result:
                print(f"股票: {result.name} ({result.code})")
                print(f"价格: {result.current_price}")
                print(f"涨跌幅: {result.change_rate}%")
                print(f"买一: {result.bid1.price} / {result.bid1.volume}")
                print(f"卖一: {result.ask1.price} / {result.ask1.volume}")
            else:
                print("获取失败")
    
    import asyncio
    asyncio.run(test())
