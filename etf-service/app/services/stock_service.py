import logging
import math
import os
from datetime import datetime, timedelta
from typing import List, Optional

from ..datasource import get_source_chain
from ..datasource.base import fetch_with_fallback
from ..database.mongo import mongo_client
from ..normalize.schema import beijing_now, to_date, to_float

logger = logging.getLogger(__name__)

DEFAULT_HISTORY_START = "2000-01-01"
BULK_WRITE_SIZE = 1000
MARKET_QUOTES_SOURCE = "market_quotes"
SHARES_PER_LOT = 100


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        logger.warning("环境变量 %s=%s 不是整数，使用默认值 %s", name, raw, default)
        return default


# market_quotes 的 trade_date 比实际交易日早一天（上游按 UTC 归档），复制时顺延；
# 上游修复日期后可用 MARKET_QUOTES_TRADE_DATE_OFFSET_DAYS=0 关闭。
MARKET_QUOTES_TRADE_DATE_OFFSET_DAYS = _env_int("MARKET_QUOTES_TRADE_DATE_OFFSET_DAYS", 1)


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

    def _market_quote_trade_date(self, raw) -> str:
        """YYYYMMDD → YYYY-MM-DD，并按偏移顺延到 market_quotes 数据的实际交易日。"""
        date_text = to_date(raw)
        if not date_text:
            return ""
        try:
            trade_date = datetime.strptime(date_text[:10], "%Y-%m-%d")
        except ValueError:
            return ""
        return (trade_date + timedelta(days=MARKET_QUOTES_TRADE_DATE_OFFSET_DAYS)).strftime("%Y-%m-%d")

    def _market_quote_record(self, doc: dict, now: datetime) -> Optional[dict]:
        """把一条 market_quotes 快照转换成 stock_daily_kline 文档；价格无效返回 None。"""
        symbol = str(doc.get("symbol") or doc.get("code") or "").strip()
        trade_date = self._market_quote_trade_date(doc.get("trade_date"))
        if not symbol or not trade_date:
            return None
        open_price = to_float(doc.get("open"))
        high = to_float(doc.get("high"))
        low = to_float(doc.get("low"))
        close = to_float(doc.get("close"))
        if not all(math.isfinite(v) and v > 0 for v in (open_price, high, low, close)):
            return None
        volume = to_float(doc.get("volume"))
        amount = to_float(doc.get("amount"))
        if not (math.isfinite(volume) and math.isfinite(amount)) or volume < 0 or amount < 0:
            return None
        return {
            "symbol": symbol,
            "trade_date": trade_date,
            "open": open_price,
            "high": high,
            "low": low,
            "close": close,
            # market_quotes 的 volume 单位是股，stock_daily_kline 统一为手；amount 上游已是元
            "volume": round(volume / SHARES_PER_LOT, 2),
            "amount": amount,
            "data_source": MARKET_QUOTES_SOURCE,
            "created_at": now,
            "updated_at": now,
        }

    def sync_stock_daily_from_market_quotes(self) -> int:
        """把 market_quotes 当日快照复制到 stock_daily_kline；无当日数据时回退数据源拉取。"""
        getter = getattr(self.mongo, "get_market_quotes", None)
        quotes = getter() if getter is not None else []
        now = beijing_now()
        today = now.strftime("%Y-%m-%d")
        records = {}
        for doc in quotes or []:
            record = self._market_quote_record(doc, now)
            if record is None:
                continue
            # 同一 symbol+trade_date 只保留一条，避免批量 upsert 产生重复
            records[(record["symbol"], record["trade_date"])] = record

        if not records:
            logger.warning("market_quotes 无可用行情数据，回退数据源增量拉取")
            return self.update_stock_daily_incremental()

        latest = max(record["trade_date"] for record in records.values())
        if latest != today:
            logger.warning("market_quotes 最新交易日为 %s，非当日 %s，回退数据源增量拉取", latest, today)
            return self.update_stock_daily_incremental()

        daily_records = [record for record in records.values() if record["trade_date"] == latest]
        written = self._write_quotes(daily_records)
        logger.info("market_quotes 同步个股K线完成，交易日 %s，写入 %s 条", latest, written)
        return written

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
