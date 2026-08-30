import logging
from datetime import datetime
from typing import Dict, List, Optional

from ..datasource.akshare_source import AkshareSource
from ..database.mongo import mongo_client
from ..normalize.schema import IndexValuation, compute_percentile

logger = logging.getLogger(__name__)


class ValuationService:
    VALUATION_INDEX_NAMES: Dict[str, str] = {
        "sh000300": "沪深300",
        "sh000905": "中证500",
        "sz399006": "创业板指",
        "sh000001": "上证指数",
    }

    def __init__(self, ak=None, mongo=None):
        self.ak = ak or AkshareSource().ak
        self.mongo = mongo or mongo_client

    def fetch_index_valuation(self, index_code: str) -> int:
        """采集单个指数 PE/PB 历史、逐日计算历史分位并写入 index_valuation，返回写入条数。"""
        name = self.VALUATION_INDEX_NAMES.get(index_code)
        if not name:
            logger.warning("未配置指数 %s 的估值名称", index_code)
            return 0
        pe_rows = self.ak.fetch_index_valuation_history(name, "pe")
        pb_rows = self.ak.fetch_index_valuation_history(name, "pb")
        pe_map = {r["trade_date"]: r["pe"] for r in (pe_rows or [])}
        pb_map = {r["trade_date"]: r["pb"] for r in (pb_rows or [])}
        dates = sorted(set(pe_map) | set(pb_map))
        pe_history: List[float] = []
        pb_history: List[float] = []
        count = 0
        for d in dates:
            pe = pe_map.get(d)
            pb = pb_map.get(d)
            if pe is not None:
                pe_history.append(pe)
            if pb is not None:
                pb_history.append(pb)
            record = IndexValuation(
                index_code=index_code,
                trade_date=d,
                pe=pe if pe is not None else 0.0,
                pb=pb if pb is not None else 0.0,
                pe_percentile=compute_percentile(pe_history, pe) if pe is not None else 0.0,
                pb_percentile=compute_percentile(pb_history, pb) if pb is not None else 0.0,
                data_source="akshare",
                updated_at=datetime.now(),
            ).to_dict()
            if self.mongo.upsert_index_valuation(record):
                count += 1
        logger.info("指数 %s 估值采集完成，写入 %s 条", index_code, count)
        return count

    def update_all_index_valuation(self) -> int:
        total = 0
        for code in self.VALUATION_INDEX_NAMES:
            total += self.fetch_index_valuation(code)
        logger.info("指数估值采集完成，共写入 %s 条", total)
        return total


valuation_service = ValuationService()
