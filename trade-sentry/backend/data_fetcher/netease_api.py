"""
网易财经API
作为备用数据源，提供股票价格和涨停信息
"""
import re
from decimal import Decimal
from typing import Optional, List, Dict
from data_fetcher.base_fetcher import BaseDataFetcher, RetryConfig
from data_fetcher.models import UnifiedStockData, BidAskLevel, UnifiedLimitUpStock, UnifiedSector


class NetEaseAPI(BaseDataFetcher):
    """网易财经API封装"""

    def __init__(self):
        super().__init__(
            name="NetEaseAPI",
            retry_config=RetryConfig(max_retries=2, retry_delay=1.0)
        )
        self.base_url = "https://quotes.money.163.com"

    def format_stock_code(self, code: str) -> str:
        """格式化股票代码为网易格式"""
        code = code.strip()
        if not code:
            return ''

        if code.startswith(('6', '688', '689')):
            return f"0{code}"  # 沪市
        else:
            return f"1{code}"  # 深市

    async def get_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """
        获取单只股票实时行情

        API: https://quotes.money.163.com/service/chddata.html?code=000001
        返回格式: 股票代码,名称,日期,开盘价,最高价,最低价,收盘价,成交量,成交额...
        """
        return await self.fetch_with_retry(self._fetch_stock_price, code)

    async def _fetch_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """实际获取股票价格的函数"""
        net_code = self.format_stock_code(code)
        url = f"{self.base_url}/quotes/service/chddata.html"

        params = {
            'code': net_code,
            'start': '20240101',
            'end': '20261231',
            'fields': 'TOPEN;HIGH;LOW;TCLOSE;VOTURNOVER;VATURNOVER'
        }

        response = await self.http_get(url, params=params)

        if not response or 'text' not in response:
            return None

        return self._parse_netease_data(code, response['text'])

    def _parse_netease_data(self, code: str, text: str) -> Optional[UnifiedStockData]:
        """解析网易返回的数据"""
        try:
            lines = text.strip().split('\n')
            if len(lines) < 2:
                return None

            # 取最后一行（最新数据）
            last_line = lines[-1]
            parts = last_line.split(',')

            if len(parts) < 8:
                return None

            stock = UnifiedStockData()
            stock.code = code
            stock.name = parts[1] if parts[1] else ''
            stock.source = 'netease'

            # 价格数据
            stock.open_price = Decimal(parts[3]) if parts[3] else Decimal('0')
            stock.high_price = Decimal(parts[4]) if parts[4] else Decimal('0')
            stock.low_price = Decimal(parts[5]) if parts[5] else Decimal('0')
            stock.current_price = Decimal(parts[6]) if parts[6] else Decimal('0')

            # 成交量和成交额
            stock.volume = int(parts[7]) if parts[7] else 0
            stock.amount = Decimal(parts[8]) if parts[8] else Decimal('0')

            # 计算涨跌幅（需要昨收数据，取倒数第二行）
            if len(lines) >= 3:
                prev_line = lines[-2]
                prev_parts = prev_line.split(',')
                if len(prev_parts) >= 8 and prev_parts[6]:
                    prev_close = Decimal(prev_parts[6])
                    if prev_close > 0:
                        stock.prev_close = prev_close
                        stock.change = stock.current_price - prev_close
                        stock.change_rate = (stock.change / prev_close) * 100

            # 判断是否涨停/跌停
            if hasattr(stock, 'prev_close') and stock.prev_close > 0:
                limit_up_price = stock.prev_close * Decimal('1.1')
                limit_down_price = stock.prev_close * Decimal('0.9')
                stock.is_limit_up = stock.current_price >= limit_up_price
                stock.is_limit_down = stock.current_price <= limit_down_price

            return stock

        except Exception as e:
            print(f"[NetEaseAPI] 解析数据失败: {e}")
            return None

    async def get_batch_stock_prices(self, codes: List[str]) -> List[UnifiedStockData]:
        """批量获取股票行情"""
        results = []
        for code in codes:
            stock = await self.get_stock_price(code)
            if stock:
                results.append(stock)
        return results

    async def get_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """获取涨停股票列表"""
        # 网易没有直接的涨停API，返回空列表
        return []

    async def get_sectors(self) -> List[UnifiedSector]:
        """获取板块列表"""
        # 网易没有直接的板块API，返回空列表
        return []


# 测试代码
if __name__ == "__main__":
    async def test():
        async with NetEaseAPI() as api:
            print("=== 测试网易财经API ===")
            result = await api.get_stock_price("000001")
            if result:
                print(f"股票: {result.name} ({result.code})")
                print(f"价格: {result.current_price}")
                print(f"涨跌幅: {result.change_rate}%")
            else:
                print("获取失败")

    import asyncio
    asyncio.run(test())
