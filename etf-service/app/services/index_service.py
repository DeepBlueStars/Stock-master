import logging
from typing import Dict

from ..datasource import get_source_chain
from ..datasource.akshare_source import AkshareSource
from ..datasource.base import fetch_with_fallback
from ..database.mongo import mongo_client
from ..normalize.schema import to_date

logger = logging.getLogger(__name__)

INDEX_CONSTITUENTS: Dict[str, str] = {
    "000300": "沪深300",
    "000905": "中证500",
}


class IndexService:
    INDEX_CODES: Dict[str, str] = {
        "sh000001": "上证指数",
        "sh000300": "沪深300",
        "sh000905": "中证500",
        "sz399006": "创业板指",
    }

    def __init__(self, ak=None, mongo=None):
        self.ak = ak or AkshareSource().ak
        self.mongo = mongo or mongo_client

    def fetch_index_daily_history(self, code: str, start_date: str = "19900101") -> int:
        """采集单个指数历史日线并写入 index_daily_quotes，返回写入条数。"""
        quotes = fetch_with_fallback(get_source_chain(), "fetch_index_daily", code, start_date=start_date)
        if not quotes:
            logger.warning("指数 %s 历史日线采集为空", code)
            return 0
        count = 0
        for q in quotes:
            record = dict(q)
            record["index_code"] = record.pop("symbol")
            if self.mongo.upsert_index_daily_quote(record):
                count += 1
        logger.info("指数 %s 历史日线采集完成，写入 %s 条", code, count)
        return count

    def fetch_all_index_daily_history(self) -> int:
        total = 0
        for code in self.INDEX_CODES:
            total += self.fetch_index_daily_history(code)
        return total

    def fetch_and_store_constituents(self) -> int:
        """拉取沪深300/中证500成分股并写入 index_constituents，返回写入条数。"""
        count = 0
        for index_code, index_name in INDEX_CONSTITUENTS.items():
            try:
                df = self.ak.index_stock_cons(symbol=index_code)
            except Exception as e:  # noqa: BLE001
                logger.error("拉取 %s(%s) 成分股失败: %s", index_name, index_code, e)
                continue
            if df is None or df.empty:
                logger.warning("拉取 %s(%s) 成分股为空", index_name, index_code)
                continue
            code_col = "成分券代码" if "成分券代码" in df.columns else df.columns[0]
            for _, r in df.iterrows():
                symbol = str(r[code_col]).strip()
                if not symbol:
                    continue
                if self.mongo.upsert_index_constituent({
                    "index_code": index_code,
                    "index_name": index_name,
                    "symbol": symbol,
                    "data_source": "akshare",
                }):
                    count += 1
        logger.info("成分股拉取完成，共写入 %s 条", count)
        return count


index_service = IndexService()
