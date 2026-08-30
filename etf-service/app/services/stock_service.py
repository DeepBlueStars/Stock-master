import logging
from typing import List

from ..datasource import get_source_chain
from ..datasource.base import fetch_with_fallback
from ..database.mongo import mongo_client

logger = logging.getLogger(__name__)


class StockService:
    def __init__(self, mongo=None):
        self.mongo = mongo or mongo_client

    def _pool(self) -> List[str]:
        return self.mongo.get_constituent_symbols()

    def fetch_stock_daily_history(self, symbol: str, start_date: str = "19900101") -> int:
        quotes = fetch_with_fallback(get_source_chain(), "fetch_stock_daily", symbol, start_date=start_date)
        if not quotes:
            logger.warning("个股 %s 历史K线采集为空", symbol)
            return 0
        count = 0
        for q in quotes:
            if self.mongo.upsert_stock_daily_kline(q):
                count += 1
        return count

    def fetch_all_stock_daily_history(self) -> int:
        total = 0
        for symbol in self._pool():
            total += self.fetch_stock_daily_history(symbol)
        logger.info("优质池个股历史K线采集完成，写入 %s 条", total)
        return total

    def update_stock_daily_incremental(self) -> int:
        """按日增量更新个股K线，仅补缺失交易日（upsert 去重）。"""
        total = 0
        for symbol in self._pool():
            quotes = fetch_with_fallback(get_source_chain(), "fetch_stock_daily", symbol, start_date="20000101")
            if not quotes:
                continue
            for q in quotes:
                if self.mongo.upsert_stock_daily_kline(q):
                    total += 1
        logger.info("个股K线增量更新完成，写入 %s 条", total)
        return total


stock_service = StockService()
