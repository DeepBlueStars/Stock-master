from typing import List

from .base import DataSource, fetch_with_fallback
from .akshare_source import AkshareSource
from .baostock_source import BaostockSource
from .tushare_source import TushareSource
from .eastmoney_sina import EastmoneySinaSource
from .tencent_source import TencentSource


def get_source_chain() -> List[DataSource]:
    """主备切换顺序：akshare → baostock → tushare → 腾讯 → 东财/新浪。"""
    return [AkshareSource(), BaostockSource(), TushareSource(), TencentSource(), EastmoneySinaSource()]


__all__ = ["DataSource", "fetch_with_fallback", "get_source_chain"]
