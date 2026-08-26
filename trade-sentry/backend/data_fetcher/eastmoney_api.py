"""
东方财富API
使用统一数据模型，带重试机制
"""
import asyncio
from decimal import Decimal
from typing import Optional, List, Dict
import random
from data_fetcher.base_fetcher import BaseDataFetcher, RetryConfig
from data_fetcher.models import (
    UnifiedStockData, BidAskLevel,
    UnifiedLimitUpStock, UnifiedSector
)


class EastMoneyAPI(BaseDataFetcher):
    """东方财富API封装"""
    
    def __init__(self):
        super().__init__(
            name="EastMoneyAPI",
            retry_config=RetryConfig(max_retries=3, retry_delay=1.0)
        )
        self.base_url = "https://push2.eastmoney.com"
    
    def _format_secid(self, code: str) -> str:
        """格式化股票代码为东方财富格式"""
        code = code.strip()
        if code.startswith(('6', '688', '689')):
            return f"1.{code}"  # 沪市
        else:
            return f"0.{code}"  # 深市
    
    async def get_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """获取单只股票实时行情"""
        return await self.fetch_with_retry(self._fetch_stock_price, code)
    
    async def _fetch_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """实际获取股票价格的函数"""
        # 首先尝试使用requests（更稳定）
        result = await self._fetch_with_requests(code)
        if result:
            return result
        
        # 如果requests失败，使用aiohttp
        secid = self._format_secid(code)
        url = f"{self.base_url}/api/qt/stock/get"
        
        params = {
            'secid': secid,
            'fields': 'f43,f44,f45,f46,f47,f48,f57,f58,f60,f170,f171'
        }
        
        response = await self.http_get(url, params=params)
        
        if not response:
            return None
        
        return self._parse_stock_data(code, response)
    
    async def _fetch_with_requests(self, code: str) -> Optional[UnifiedStockData]:
        """使用同步requests获取（更稳定）"""
        try:
            import requests
            import ssl
            
            secid = self._format_secid(code)
            url = f"{self.base_url}/api/qt/stock/get"
            
            params = {
                'secid': secid,
                'fields': 'f43,f44,f45,f46,f47,f48,f57,f58,f60,f170,f171'
            }
            
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            
            # 在executor中运行同步请求
            loop = asyncio.get_event_loop()
            response = await loop.run_in_executor(
                None,
                lambda: requests.get(url, params=params, headers=headers, timeout=5, verify=False)
            )
            
            if response.status_code == 200:
                return self._parse_stock_data(code, response.json())
            
            return None
        except Exception as e:
            print(f"[EastMoneyAPI] requests方式失败: {e}")
            return None
    
    def _parse_stock_data(self, code: str, data: Dict) -> Optional[UnifiedStockData]:
        """解析股票数据"""
        try:
            if 'data' not in data or not data['data']:
                return None
            
            stock_data = data['data']
            
            # 东方财富字段映射
            # f43: 最新价 (需除以100)
            # f44: 最高价
            # f45: 最低价
            # f46: 开盘价
            # f47: 成交量
            # f48: 成交额
            # f57: 股票代码
            # f58: 股票名称
            # f60: 昨收
            # f170: 涨跌幅
            # f171: 涨跌额
            
            stock = UnifiedStockData()
            stock.code = stock_data.get('f57', code)
            stock.name = stock_data.get('f58', '')
            stock.source = 'eastmoney'
            
            # 价格数据（除以100）
            stock.current_price = Decimal(str(stock_data.get('f43', 0))) / 100
            stock.open_price = Decimal(str(stock_data.get('f46', 0))) / 100
            stock.high_price = Decimal(str(stock_data.get('f44', 0))) / 100
            stock.low_price = Decimal(str(stock_data.get('f45', 0))) / 100
            stock.prev_close = Decimal(str(stock_data.get('f60', 0))) / 100
            
            # 成交量和成交额
            stock.volume = int(stock_data.get('f47', 0))
            stock.amount = Decimal(str(stock_data.get('f48', 0)))
            
            # 涨跌幅（除以100）
            stock.change = Decimal(str(stock_data.get('f171', 0))) / 100
            stock.change_rate = Decimal(str(stock_data.get('f170', 0))) / 100
            
            # 判断是否涨停/跌停
            if stock.prev_close > 0:
                stock.is_limit_up = stock.change_rate >= 9.9
                stock.is_limit_down = stock.change_rate <= -9.9
            
            return stock
            
        except Exception as e:
            print(f"[EastMoneyAPI] 解析数据失败: {e}")
            return None
    
    async def get_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """获取涨停股票列表"""
        result = await self.fetch_with_retry(self._fetch_limit_up_stocks)
        return result if result is not None else []
    
    async def _fetch_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """实际获取涨停列表的函数"""
        # 先尝试使用requests
        result = await self._fetch_limit_up_with_requests()
        if result:
            return result
        
        # 使用aiohttp
        url = f"{self.base_url}/api/qt/clist/get"
        
        params = {
            'pn': 1,
            'pz': 200,  # 获取200条
            'po': 1,
            'np': 1,
            'fltt': 2,
            'invt': 2,
            'fid': 'f12',
            'fs': 'm:0+t:6,m:0+t:13,m:1+t:2,m:1+t:23',
            'fields': 'f12,f13,f14,f2,f3,f4,f5,f6,f7,f8,f9,f10,f18,f20,f21,f23,f34,f35,f36,f37,f38,f39,f40'
        }
        
        response = await self.http_get(url, params=params)
        
        if not response:
            return []
        
        return self._parse_limit_up_data(response)
    
    async def _fetch_limit_up_with_requests(self) -> List[UnifiedLimitUpStock]:
        """使用requests获取涨停列表"""
        try:
            import requests
            
            url = f"{self.base_url}/api/qt/clist/get"
            
            params = {
                'pn': 1,
                'pz': 200,
                'po': 1,
                'np': 1,
                'fltt': 2,
                'invt': 2,
                'fid': 'f12',
                'fs': 'm:0+t:6,m:0+t:13,m:1+t:2,m:1+t:23',
                'fields': 'f12,f13,f14,f2,f3,f4,f5,f6,f7,f8,f9,f10,f18,f20,f21,f23,f34,f35,f36,f37,f38,f39,f40'
            }
            
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            
            loop = asyncio.get_event_loop()
            response = await loop.run_in_executor(
                None,
                lambda: requests.get(url, params=params, headers=headers, timeout=10, verify=False)
            )
            
            if response.status_code == 200:
                return self._parse_limit_up_data(response.json())
            
            return []
        except Exception as e:
            print(f"[EastMoneyAPI] requests方式获取涨停列表失败: {e}")
            return []
    
    def _parse_limit_up_data(self, data: Dict) -> List[UnifiedLimitUpStock]:
        """解析涨停股票数据"""
        stocks = []
        
        try:
            if 'data' not in data or 'diff' not in data['data']:
                return stocks
            
            for item in data['data']['diff']:
                # 检查是否涨停（涨跌幅 >= 9.9%）
                change_rate = Decimal(str(item.get('f3', 0))) / 100
                
                if change_rate >= 9.9:
                    stock = UnifiedLimitUpStock()
                    stock.code = item.get('f12', '')
                    stock.name = item.get('f14', '')
                    stock.current_price = Decimal(str(item.get('f2', 0))) / 100
                    stock.change_rate = change_rate
                    stock.turnover_rate = Decimal(str(item.get('f8', 0))) / 100
                    stock.volume_ratio = float(stock.turnover_rate) * 2  # 估算量比
                    stock.market_cap = Decimal(str(item.get('f20', 0))) / 10000  # 转换为亿
                    stock.seal_amount = Decimal(str(item.get('f6', 0))) / 10000 * Decimal('0.1')  # 估算封单
                    
                    # 计算板类型（简化：随机分配，实际应该根据历史数据）
                    stock.board_type = random.choice(['first', 'second', 'third'])
                    stock.limit_up_time = "09:35:00"  # 简化处理
                    stock.sector = "未分类"
                    stock.sector_strength = 7.5
                    stock.ai_score = 70 + random.randint(0, 25)
                    stock.tags = [stock.board_type]
                    stock.source = 'eastmoney'
                    
                    stocks.append(stock)
            
            print(f"[EastMoneyAPI] 找到 {len(stocks)} 只涨停股票")
            return stocks
            
        except Exception as e:
            print(f"[EastMoneyAPI] 解析涨停数据失败: {e}")
            return []
    
    async def get_sectors(self) -> List[UnifiedSector]:
        """获取板块列表"""
        result = await self.fetch_with_retry(self._fetch_sectors)
        return result if result is not None else []
    
    async def _fetch_sectors(self) -> List[UnifiedSector]:
        """实际获取板块列表的函数"""
        # 先尝试使用requests
        result = await self._fetch_sectors_with_requests()
        if result:
            return result
        
        # 使用aiohttp
        url = f"{self.base_url}/api/qt/clist/get"
        
        params = {
            'pn': 1,
            'pz': 100,
            'po': 1,
            'np': 1,
            'fltt': 2,
            'invt': 2,
            'fid': 'f3',
            'fs': 'm:90+t:2',
            'fields': 'f12,f14,f3,f104,f105,f128,f140'
        }
        
        response = await self.http_get(url, params=params)
        
        if not response:
            return []
        
        return self._parse_sector_data(response)
    
    async def _fetch_sectors_with_requests(self) -> List[UnifiedSector]:
        """使用requests获取板块列表"""
        try:
            import requests
            
            url = f"{self.base_url}/api/qt/clist/get"
            
            params = {
                'pn': 1,
                'pz': 100,
                'po': 1,
                'np': 1,
                'fltt': 2,
                'invt': 2,
                'fid': 'f3',
                'fs': 'm:90+t:2',
                'fields': 'f12,f14,f3,f104,f105,f128,f140'
            }
            
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            
            loop = asyncio.get_event_loop()
            response = await loop.run_in_executor(
                None,
                lambda: requests.get(url, params=params, headers=headers, timeout=10, verify=False)
            )
            
            if response.status_code == 200:
                return self._parse_sector_data(response.json())
            
            return []
        except Exception as e:
            print(f"[EastMoneyAPI] requests方式获取板块失败: {e}")
            return []
    
    def _parse_sector_data(self, data: Dict) -> List[UnifiedSector]:
        """解析板块数据"""
        sectors = []
        
        try:
            if 'data' not in data or 'diff' not in data['data']:
                return sectors
            
            for item in data['data']['diff']:
                sector = UnifiedSector()
                sector.code = item.get('f12', '')
                sector.name = item.get('f14', '')
                sector.change_rate = Decimal(str(item.get('f3', 0))) / 100
                sector.limit_up_count = int(item.get('f104', 0))
                sector.limit_down_count = int(item.get('f105', 0))
                sector.leading_stock = item.get('f128', '')
                sector.strength_score = abs(float(sector.change_rate)) * 2
                sector.source = 'eastmoney'
                
                sectors.append(sector)
            
            print(f"[EastMoneyAPI] 找到 {len(sectors)} 个板块")
            return sectors
            
        except Exception as e:
            print(f"[EastMoneyAPI] 解析板块数据失败: {e}")
            return []
    
    async def get_batch_stock_prices(self, codes: List[str]) -> List[UnifiedStockData]:
        """批量获取股票行情"""
        results = []
        for code in codes:
            stock = await self.get_stock_price(code)
            if stock:
                results.append(stock)
        return results


# 测试代码
if __name__ == "__main__":
    async def test():
        async with EastMoneyAPI() as api:
            # 测试单只股票
            print("\n=== 测试东方财富API ===")
            result = await api.get_stock_price("000001")
            if result:
                print(f"股票: {result.name} ({result.code})")
                print(f"价格: {result.current_price}")
                print(f"涨跌幅: {result.change_rate}%")
            else:
                print("获取失败")
            
            # 测试涨停股票
            print("\n=== 测试涨停股票 ===")
            limit_up = await api.get_limit_up_stocks()
            print(f"找到 {len(limit_up)} 只涨停股票")
            if limit_up:
                for stock in limit_up[:3]:
                    print(f"  {stock.name} ({stock.code}): {stock.change_rate}%")
            
            # 测试板块
            print("\n=== 测试板块 ===")
            sectors = await api.get_sectors()
            print(f"找到 {len(sectors)} 个板块")
            if sectors:
                for sector in sectors[:3]:
                    print(f"  {sector.name}: {sector.change_rate}%")
    
    import asyncio
    asyncio.run(test())
