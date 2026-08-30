import logging
from typing import Dict

from ..datasource.akshare_source import AkshareSource
from ..database.mongo import mongo_client

logger = logging.getLogger(__name__)

INDEX_CONSTITUENTS: Dict[str, str] = {
    "000300": "沪深300",
    "000905": "中证500",
}


class IndexService:
    def __init__(self, ak=None, mongo=None):
        self.ak = ak or AkshareSource().ak
        self.mongo = mongo or mongo_client

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
