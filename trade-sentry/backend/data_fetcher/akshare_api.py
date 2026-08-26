"""
AkShare API
使用 akshare 库获取股票数据，带重试机制
"""
import asyncio
from decimal import Decimal
from typing import Optional, List, Dict
from data_fetcher.base_fetcher import BaseDataFetcher, RetryConfig
from data_fetcher.models import (
    UnifiedStockData, 
    UnifiedLimitUpStock, 
    UnifiedSector
)
import akshare as ak


class AkShareAPI(BaseDataFetcher):
    """AkShare API封装"""
    
    def __init__(self):
        super().__init__(
            name="AkShareAPI",
            retry_config=RetryConfig(max_retries=3, retry_delay=1.0)
        )
    
    async def get_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """获取单只股票实时行情"""
        return await self.fetch_with_retry(self._fetch_stock_price, code)
    
    async def _fetch_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """实际获取股票价格的函数"""
        try:
            # 尝试使用东方财富数据源
            stock_zh_a_spot_df = ak.stock_zh_a_spot_em()
            
            # 查找指定代码的股票
            stock_data = stock_zh_a_spot_df[stock_zh_a_spot_df['代码'] == code]
            
            if not stock_data.empty:
                return self._parse_stock_data(code, stock_data.iloc[0])
            
            # 如果没有找到，返回None
            return None
            
        except Exception as e:
            print(f"[AkShareAPI] 获取股票数据失败: {e}")
            return None
    
    def _parse_stock_data(self, code: str, data: Dict) -> Optional[UnifiedStockData]:
        """解析股票数据"""
        try:
            stock = UnifiedStockData()
            stock.code = data.get('代码', code)
            stock.name = data.get('名称', '')
            stock.source = 'akshare'
            
            # 价格数据
            stock.current_price = Decimal(str(data.get('最新价', 0)))
            stock.open_price = Decimal(str(data.get('开盘价', 0)))
            stock.high_price = Decimal(str(data.get('最高价', 0)))
            stock.low_price = Decimal(str(data.get('最低价', 0)))
            stock.prev_close = Decimal(str(data.get('昨收', 0)))
            
            # 成交量和成交额
            stock.volume = int(data.get('成交量', 0))
            stock.amount = Decimal(str(data.get('成交额', 0)))
            
            # 涨跌幅
            stock.change = Decimal(str(data.get('涨跌额', 0)))
            stock.change_rate = Decimal(str(data.get('涨跌幅', 0)))
            
            # 判断是否涨停/跌停
            if stock.prev_close > 0:
                stock.is_limit_up = stock.change_rate >= 9.9
                stock.is_limit_down = stock.change_rate <= -9.9
            
            return stock
            
        except Exception as e:
            print(f"[AkShareAPI] 解析数据失败: {e}")
            return None
    
    async def get_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """获取涨停股票列表"""
        result = await self.fetch_with_retry(self._fetch_limit_up_stocks)
        return result if result is not None else []
    
    async def _fetch_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """实际获取涨停列表的函数"""
        try:
            # 尝试使用东方财富数据源
            stock_zh_a_spot_df = ak.stock_zh_a_spot_em()
            
            # 筛选涨停股票（涨跌幅 >= 9.9%）
            limit_up_stocks = stock_zh_a_spot_df[stock_zh_a_spot_df['涨跌幅'] >= 9.9]
            
            result = self._parse_limit_up_data(limit_up_stocks)
            if result:
                return result
            
            # 如果没有找到涨停股票，返回空列表
            return []
            
        except Exception as e:
            print(f"[AkShareAPI] 获取涨停股票失败: {e}")
            return []
    
    def _parse_limit_up_data(self, data) -> List[UnifiedLimitUpStock]:
        """解析涨停股票数据"""
        stocks = []
        
        try:
            for _, item in data.iterrows():
                stock = UnifiedLimitUpStock()
                stock.code = item.get('代码', '')
                stock.name = item.get('名称', '')
                stock.current_price = Decimal(str(item.get('最新价', 0)))
                stock.change_rate = Decimal(str(item.get('涨跌幅', 0)))
                
                # 估算换手率（使用成交量/流通股本，这里简化处理）
                stock.turnover_rate = Decimal(str(item.get('换手率', 0)))
                stock.volume_ratio = float(stock.turnover_rate) * 2  # 估算量比
                
                # 估算市值
                stock.market_cap = Decimal(str(item.get('总市值', 0))) / 10000  # 转换为亿
                
                # 估算封单金额
                stock.seal_amount = Decimal(str(item.get('成交额', 0))) / 10000 * Decimal('0.1')
                
                # 计算板类型（简化处理）
                stock.board_type = 'first'  # 默认为首板
                stock.limit_up_time = "09:35:00"  # 简化处理
                stock.sector = "未分类"
                stock.sector_strength = 7.5
                stock.ai_score = 70 + int(stock.change_rate * 10)
                stock.tags = [stock.board_type]
                stock.source = 'akshare'
                
                stocks.append(stock)
            
            print(f"[AkShareAPI] 找到 {len(stocks)} 只涨停股票")
            return stocks
            
        except Exception as e:
            print(f"[AkShareAPI] 解析涨停数据失败: {e}")
            return []
    
    async def get_sectors(self) -> List[UnifiedSector]:
        """获取板块列表"""
        result = await self.fetch_with_retry(self._fetch_sectors)
        return result if result is not None else []
    
    async def _fetch_sectors(self) -> List[UnifiedSector]:
        """实际获取板块列表的函数"""
        try:
            # 使用 akshare 获取行业板块数据
            industry_board_df = ak.stock_board_industry_name_ths()
            
            return self._parse_sector_data(industry_board_df)
            
        except Exception as e:
            print(f"[AkShareAPI] 获取板块数据失败: {e}")
            return []
    
    def _parse_sector_data(self, data) -> List[UnifiedSector]:
        """解析板块数据"""
        sectors = []
        
        try:
            for _, item in data.iterrows():
                sector = UnifiedSector()
                sector.code = item.get('code', '')
                sector.name = item.get('name', '')
                # 板块数据没有涨跌幅，设置为0
                sector.change_rate = Decimal('0')
                
                # 简化处理，实际应该从其他接口获取
                sector.limit_up_count = 0
                sector.limit_down_count = 0
                sector.leading_stock = ""
                sector.strength_score = 0
                sector.source = 'akshare'
                
                sectors.append(sector)
            
            print(f"[AkShareAPI] 找到 {len(sectors)} 个板块")
            return sectors
            
        except Exception as e:
            print(f"[AkShareAPI] 解析板块数据失败: {e}")
            return []
    
    async def get_batch_stock_prices(self, codes: List[str]) -> List[UnifiedStockData]:
        """批量获取股票行情"""
        results = []
        try:
            # 使用东方财富数据源
            stock_zh_a_spot_df = ak.stock_zh_a_spot_em()
            
            for code in codes:
                stock_data = stock_zh_a_spot_df[stock_zh_a_spot_df['代码'] == code]
                if not stock_data.empty:
                    stock = self._parse_stock_data(code, stock_data.iloc[0])
                    if stock:
                        results.append(stock)
            
        except Exception as e:
            print(f"[AkShareAPI] 批量获取股票数据失败: {e}")
        
        return results


# 测试代码
if __name__ == "__main__":
    async def test():
        async with AkShareAPI() as api:
            # 测试单只股票
            print("\n=== 测试 AkShare API ===")
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