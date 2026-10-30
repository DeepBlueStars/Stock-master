import logging
from datetime import datetime, timedelta
from typing import List

from ..datasource import get_source_chain
from ..datasource.base import fetch_with_fallback
from ..database.mongo import mongo_client

logger = logging.getLogger(__name__)

DEFAULT_HISTORY_START = "2000-01-01"
BULK_WRITE_SIZE = 1000


class StockService:
    def __init__(self, mongo=None):
        self.mongo = mongo or mongo_client

    def _pool(self) -> List[str]:
        return self.mongo.get_constituent_symbols()

    def _write_quotes(self, quotes: List[dict]) -> int:
        """优先批量 upsert；测试/旧客户端不支持时回退逐条写入。"""
        bulk = getattr(self.mongo, "bulk_upsert_stock_daily_kline", None)
        if bulk is None:
            return sum(1 for q in quotes if self.mongo.upsert_stock_daily_kline(q))
        written = 0
        for i in range(0, len(quotes), BULK_WRITE_SIZE):
            written += bulk(quotes[i:i + BULK_WRITE_SIZE])
        return written

    def fetch_stock_daily_history(self, symbol: str, start_date: str = "19900101") -> int:
        quotes = fetch_with_fallback(get_source_chain(), "fetch_stock_daily", symbol, start_date=start_date)
        if not quotes:
            logger.warning("个股 %s 历史K线采集为空", symbol)
            return 0
        return self._write_quotes(quotes)

    def fetch_all_stock_daily_history(self) -> int:
        total = 0
        for symbol in self._pool():
            total += self.fetch_stock_daily_history(symbol)
        logger.info("优质池个股历史K线采集完成，写入 %s 条", total)
        return total

    def _incremental_start_date(self, symbol: str) -> str:
        """从库内最新交易日的次日开始拉取，避免每天都重拉全部历史。"""
        latest = None
        getter = getattr(self.mongo, "get_latest_trade_date", None)
        if getter is not None:
            latest = getter(symbol)
        if not latest:
            return DEFAULT_HISTORY_START
        try:
            next_day = datetime.strptime(str(latest)[:10], "%Y-%m-%d") + timedelta(days=1)
        except ValueError:
            return DEFAULT_HISTORY_START
        return next_day.strftime("%Y-%m-%d")

    def update_stock_daily_incremental(self) -> int:
        """按日增量更新个股K线，仅补缺失交易日（upsert 去重）。"""
        total = 0
        today = datetime.now().strftime("%Y-%m-%d")
        for symbol in self._pool():
            start_date = self._incremental_start_date(symbol)
            if start_date > today:
                continue
            # 批量补采时不做多次重试，单源失败立即切下一个源，避免卡在被限流的源上
            quotes = fetch_with_fallback(
                get_source_chain(), "fetch_stock_daily", symbol,
                start_date=start_date, retries=0, retry_delay=0,
            )
            if not quotes:
                continue
            total += self._write_quotes(quotes)
        logger.info("个股K线增量更新完成，写入 %s 条", total)
        return total

    def fetch_and_store_fundamentals(self) -> int:
        """采集优质池个股财务指标（PE/PB/ROE/营收增速/净利增速/毛利率）写入 stock_fundamentals。"""
        total = 0
        for symbol in self._pool():
            fundamentals = fetch_with_fallback(get_source_chain(), "fetch_fundamentals", symbol)
            if not fundamentals:
                continue
            for f in fundamentals:
                if self.mongo.upsert_stock_fundamental(f):
                    total += 1
        logger.info("个股财务指标采集完成，写入 %s 条", total)
        return total

    def fetch_and_store_industry_concept(self) -> int:
        """采集优质池个股行业与概念分类写入 stock_industry_concept。"""
        total = 0
        for symbol in self._pool():
            info = fetch_with_fallback(get_source_chain(), "fetch_industry_concept", symbol)
            if not info:
                continue
            if self.mongo.upsert_stock_industry_concept(info):
                total += 1
        logger.info("个股行业概念采集完成，写入 %s 条", total)
        return total


stock_service = StockService()
