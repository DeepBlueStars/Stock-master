"""
行情数据获取模块
提供新浪财经、东方财富、腾讯财经、网易财经等多个免费数据源的行情数据获取
"""
from data_fetcher.base_fetcher import BaseDataFetcher, RetryConfig
from data_fetcher.models import (
    UnifiedStockData,
    UnifiedLimitUpStock,
    UnifiedSector,
    BidAskLevel
)
from data_fetcher.sina_api import SinaAPI
from data_fetcher.eastmoney_api import EastMoneyAPI
from data_fetcher.tencent_api import TencentAPI
from data_fetcher.netease_api import NetEaseAPI
from data_fetcher.data_aggregator import DataAggregator, data_aggregator

__all__ = [
    # 基础类
    'BaseDataFetcher',
    'RetryConfig',

    # 数据模型
    'UnifiedStockData',
    'UnifiedLimitUpStock',
    'UnifiedSector',
    'BidAskLevel',

    # API类
    'SinaAPI',
    'EastMoneyAPI',
    'TencentAPI',
    'NetEaseAPI',

    # 聚合器
    'DataAggregator',
    'data_aggregator'
]
