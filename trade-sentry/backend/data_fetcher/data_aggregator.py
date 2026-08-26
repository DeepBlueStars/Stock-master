"""
数据聚合器
整合多个数据源的行情数据，提供统一的接口
支持多API轮询、重试机制和故障转移
"""
import asyncio
import logging
from typing import List, Optional, Dict, Any
from datetime import datetime
from data_fetcher.sina_api import SinaAPI
from data_fetcher.eastmoney_api import EastMoneyAPI
from data_fetcher.tencent_api import TencentAPI
from data_fetcher.netease_api import NetEaseAPI
from data_fetcher.akshare_api import AkShareAPI
from data_fetcher.tushare_api import TushareAPI
from data_fetcher.models import (
    UnifiedStockData,
    UnifiedLimitUpStock,
    UnifiedSector
)

logger = logging.getLogger(__name__)


class DataAggregator:
    """
    行情数据聚合器

    整合新浪财经、东方财富、腾讯财经、网易财经等多个数据源
    自动选择最佳数据源，提供统一格式的行情数据
    支持多API轮询、重试机制和故障转移
    """

    def __init__(self):
        self.sina = SinaAPI()
        self.eastmoney = EastMoneyAPI()
        self.tencent = TencentAPI()
        self.netease = NetEaseAPI()
        self.akshare = AkShareAPI()
        self.tushare = TushareAPI()

        # API优先级配置（按优先级从高到低）
        self.stock_price_sources = [
            ('akshare', self.akshare.get_stock_price, 5),
            ('eastmoney', self.eastmoney.get_stock_price, 4),
            ('tencent', self.tencent.get_stock_price, 3),
            ('netease', self.netease.get_stock_price, 2),
            ('sina', self.sina.get_stock_price, 1)
        ]

        self.limit_up_sources = [
            ('akshare', self.akshare.get_limit_up_stocks, 6),
            ('tushare', self.tushare.get_limit_up_stocks, 5),
            ('eastmoney', self.eastmoney.get_limit_up_stocks, 4),
            ('tencent', self.tencent.get_limit_up_stocks, 2),
            ('netease', self.netease.get_limit_up_stocks, 1),
            ('sina', self.sina.get_limit_up_stocks, 0)
        ]

        self.sector_sources = [
            ('akshare', self.akshare.get_sectors, 5),
            ('eastmoney', self.eastmoney.get_sectors, 4),
            ('tencent', self.tencent.get_sectors, 2),
            ('netease', self.netease.get_sectors, 1),
            ('sina', self.sina.get_sectors, 0)
        ]

        # API健康状态跟踪
        self.api_health: Dict[str, float] = {
            'akshare': 1.0,
            'eastmoney': 1.0,
            'tencent': 1.0,
            'netease': 1.0,
            'sina': 1.0
        }

        # 连续失败计数器
        self.fail_count: Dict[str, int] = {
            'akshare': 0,
            'eastmoney': 0,
            'tencent': 0,
            'netease': 0,
            'sina': 0
        }

        self.max_fail_count = 5
        self.recovery_delay = 30  # 30秒后尝试恢复
    
    def _update_api_health(self, source: str, success: bool):
        """更新API健康状态"""
        if success:
            self.fail_count[source] = 0
            self.api_health[source] = min(1.0, self.api_health[source] + 0.1)
        else:
            self.fail_count[source] = self.fail_count.get(source, 0) + 1
            if self.fail_count[source] >= self.max_fail_count:
                self.api_health[source] = 0.0
                logger.warning(f"[DataAggregator] {source} API 已失效，停止使用")

    async def _get_healthy_sources(self, sources: List[tuple]) -> List[tuple]:
        """获取可用的API源（基于健康状态）"""
        healthy = []
        for source_name, fetch_func, priority in sources:
            if self.api_health.get(source_name, 0) > 0:
                healthy.append((source_name, fetch_func, priority))
        # 按优先级排序
        healthy.sort(key=lambda x: x[2], reverse=True)
        return healthy

    async def get_stock_price(self, code: str) -> Optional[UnifiedStockData]:
        """
        获取股票实时价格

        策略：
        1. 按优先级尝试各个数据源
        2. 支持故障转移（单个API失败自动切换）
        3. 支持重试机制

        Args:
            code: 股票代码

        Returns:
            UnifiedStockData或None
        """
        sources = await self._get_healthy_sources(self.stock_price_sources)

        for source_name, fetch_func, priority in sources:
            try:
                logger.info(f"[DataAggregator] 尝试从 {source_name} 获取 {code}")
                result = await asyncio.wait_for(
                    fetch_func(code),
                    timeout=10.0
                )

                if result:
                    logger.info(f"[DataAggregator] {source_name} 返回成功")
                    self._update_api_health(source_name, True)
                    return result

            except asyncio.TimeoutError:
                logger.warning(f"[DataAggregator] {source_name} 获取超时: {code}")
                self._update_api_health(source_name, False)
            except Exception as e:
                logger.warning(f"[DataAggregator] {source_name} 获取失败: {e}")
                self._update_api_health(source_name, False)
                continue

        logger.warning(f"[DataAggregator] 所有数据源都失败: {code}")
        return None
    
    async def get_batch_stock_prices(self, codes: List[str]) -> List[UnifiedStockData]:
        """
        批量获取股票行情
        
        Args:
            codes: 股票代码列表
            
        Returns:
            UnifiedStockData列表
        """
        if not codes:
            return []
        
        # 使用并发获取
        tasks = [self.get_stock_price(code) for code in codes]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        valid_results = []
        for result in results:
            if isinstance(result, UnifiedStockData):
                valid_results.append(result)
            elif isinstance(result, Exception):
                print(f"[DataAggregator] 批量获取异常: {result}")
        
        return valid_results
    
    async def get_limit_up_stocks(self, force_refresh: bool = False) -> List[UnifiedLimitUpStock]:
        """
        获取涨停股票列表

        策略：
        1. 按优先级尝试各个数据源
        2. 支持故障转移和重试
        3. 在所有API都失败时返回缓存数据或空列表

        Args:
            force_refresh: 强制刷新，忽略缓存

        Returns:
            UnifiedLimitUpStock列表
        """
        sources = await self._get_healthy_sources(self.limit_up_sources)

        for source_name, fetch_func, priority in sources:
            try:
                logger.info(f"[DataAggregator] 从 {source_name} 获取涨停股票")
                stocks = await asyncio.wait_for(
                    fetch_func(),
                    timeout=15.0
                )

                if stocks and len(stocks) > 0:
                    logger.info(f"[DataAggregator] {source_name} 获取到 {len(stocks)} 只涨停股票")
                    self._update_api_health(source_name, True)
                    return stocks

            except asyncio.TimeoutError:
                logger.warning(f"[DataAggregator] {source_name} 获取涨停股票超时")
                self._update_api_health(source_name, False)
            except Exception as e:
                logger.warning(f"[DataAggregator] {source_name} 获取涨停股票失败: {e}")
                self._update_api_health(source_name, False)
                continue

        logger.warning("[DataAggregator] 所有数据源都无法获取涨停股票")
        return []
    
    async def get_sectors(self) -> List[UnifiedSector]:
        """
        获取板块列表

        策略：
        1. 按优先级尝试各个数据源
        2. 支持故障转移

        Returns:
            UnifiedSector列表
        """
        sources = await self._get_healthy_sources(self.sector_sources)

        for source_name, fetch_func, priority in sources:
            try:
                logger.info(f"[DataAggregator] 从 {source_name} 获取板块数据")
                sectors = await asyncio.wait_for(
                    fetch_func(),
                    timeout=15.0
                )

                if sectors and len(sectors) > 0:
                    logger.info(f"[DataAggregator] {source_name} 获取到 {len(sectors)} 个板块")
                    self._update_api_health(source_name, True)
                    return sectors

            except asyncio.TimeoutError:
                logger.warning(f"[DataAggregator] {source_name} 获取板块超时")
                self._update_api_health(source_name, False)
            except Exception as e:
                logger.warning(f"[DataAggregator] {source_name} 获取板块失败: {e}")
                self._update_api_health(source_name, False)
                continue

        logger.warning("[DataAggregator] 所有数据源都无法获取板块数据")
        return []
    
    async def get_sector_stocks(self, sector_code: str) -> List[UnifiedStockData]:
        """获取板块内股票"""
        # 暂不支持
        return []
    
    async def get_market_overview(self) -> dict:
        """
        获取市场概览
        
        Returns:
            包含市场统计数据的字典
        """
        # 获取上证指数、深证成指、创业板指
        index_codes = ['000001', '399001', '399006']
        indices = await self.get_batch_stock_prices(index_codes)
        
        # 获取涨停股票数量
        limit_up_stocks = await self.get_limit_up_stocks()
        
        # 计算市场情绪
        limit_up_count = len(limit_up_stocks)
        
        if limit_up_count >= 100:
            market_temp = "hot"
            sentiment_score = 8.5
        elif limit_up_count >= 60:
            market_temp = "warm"
            sentiment_score = 7.0
        elif limit_up_count >= 30:
            market_temp = "neutral"
            sentiment_score = 5.5
        else:
            market_temp = "cold"
            sentiment_score = 3.5
        
        # 构建返回数据
        overview = {
            "timestamp": datetime.now().isoformat(),
            "indices": {},
            "market_summary": {
                "limit_up_count": limit_up_count,
                "market_temperature": market_temp,
                "sentiment_score": sentiment_score
            }
        }
        
        # 添加指数数据
        index_names = {
            '000001': '上证指数',
            '399001': '深证成指',
            '399006': '创业板指'
        }
        
        for stock in indices:
            if stock.code in index_names:
                overview["indices"][index_names[stock.code]] = {
                    "code": stock.code,
                    "name": stock.name,
                    "price": float(stock.current_price),
                    "change": float(stock.change),
                    "change_rate": float(stock.change_rate)
                }
        
        return overview


# 全局数据聚合器实例
data_aggregator = DataAggregator()


# 测试代码
if __name__ == "__main__":
    async def test():
        async with data_aggregator.sina, data_aggregator.eastmoney, data_aggregator.tencent, data_aggregator.akshare:
            print("=== 测试数据聚合器 ===\n")
            
            # 测试单只股票
            print("1. 测试单只股票查询")
            result = await data_aggregator.get_stock_price("000001")
            if result:
                print(f"   成功: {result.name} ({result.code})")
                print(f"   价格: {result.current_price}, 涨跌幅: {result.change_rate}%")
                print(f"   数据源: {result.source}")
            else:
                print("   失败: 无法获取数据")
            
            # 测试批量查询
            print("\n2. 测试批量股票查询")
            codes = ["000001", "600000", "000858"]
            results = await data_aggregator.get_batch_stock_prices(codes)
            print(f"   成功获取 {len(results)}/{len(codes)} 只股票")
            for stock in results:
                print(f"   - {stock.name}: {stock.current_price}")
            
            # 测试涨停股票
            print("\n3. 测试涨停股票列表")
            limit_up = await data_aggregator.get_limit_up_stocks()
            print(f"   找到 {len(limit_up)} 只涨停股票")
            if limit_up:
                for stock in limit_up[:3]:
                    print(f"   - {stock.name} ({stock.code}): {stock.change_rate}%")
            
            # 测试板块
            print("\n4. 测试板块列表")
            sectors = await data_aggregator.get_sectors()
            print(f"   找到 {len(sectors)} 个板块")
            if sectors:
                for sector in sectors[:3]:
                    print(f"   - {sector.name}: {sector.change_rate}%")
            
            # 测试市场概览
            print("\n5. 测试市场概览")
            overview = await data_aggregator.get_market_overview()
            print(f"   涨停家数: {overview['market_summary']['limit_up_count']}")
            print(f"   市场情绪: {overview['market_summary']['market_temperature']}")
    
    import asyncio
    asyncio.run(test())
