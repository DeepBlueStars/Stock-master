import logging
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import (
    DailyQuote,
    Fundamental,
    IndustryConcept,
    to_float,
    to_date,
    row_get,
)
from .base import DataSource

logger = logging.getLogger(__name__)

_concept_map_cache = None


class AkshareSource(DataSource):
    name = "akshare"

    def __init__(self):
        import akshare as ak
        self.ak = ak

    def _now(self):
        return datetime.now()

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "19900101").replace("-", "")
        end = self._now().strftime("%Y%m%d")
        df = self.ak.index_zh_a_hist(symbol=code[-6:], period="daily", start_date=start, end_date=end)
        if df is None or df.empty:
            return None
        now = self._now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "日期", "date", "trade_date")),
                open=to_float(row_get(r, "开盘", "open")),
                high=to_float(row_get(r, "最高", "high")),
                low=to_float(row_get(r, "最低", "low")),
                close=to_float(row_get(r, "收盘", "close")),
                volume=to_float(row_get(r, "成交量", "volume", "vol")),
                amount=to_float(row_get(r, "成交额", "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "19900101").replace("-", "")
        end = self._now().strftime("%Y%m%d")
        df = self.ak.stock_zh_a_hist(symbol=code, period="daily", start_date=start, end_date=end, adjust="")
        if df is None or df.empty:
            return None
        now = self._now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "日期", "date", "trade_date")),
                open=to_float(row_get(r, "开盘", "open")),
                high=to_float(row_get(r, "最高", "high")),
                low=to_float(row_get(r, "最低", "low")),
                close=to_float(row_get(r, "收盘", "close")),
                volume=to_float(row_get(r, "成交量", "volume", "vol")),
                amount=to_float(row_get(r, "成交额", "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_fundamentals(self, code: str) -> Optional[List[dict]]:
        pe = pb = 0.0
        try:
            df_val = self.ak.stock_a_indicator_lg(symbol=code)
            if df_val is not None and not df_val.empty:
                last = df_val.iloc[-1]
                pe = to_float(row_get(last, "pe_ttm", "pe"))
                pb = to_float(row_get(last, "pb"))
        except Exception as e:  # noqa: BLE001
            logger.warning("akshare 估值指标获取失败 %s: %s", code, e)
        try:
            df_fin = self.ak.stock_financial_analysis_indicator(symbol=code)
        except Exception as e:  # noqa: BLE001
            logger.warning("akshare 财务指标获取失败 %s: %s", code, e)
            df_fin = None
        if df_fin is None or df_fin.empty:
            return None
        now = self._now()
        rows = []
        for _, r in df_fin.iterrows():
            rows.append((to_date(row_get(r, "日期", "报告期", "报告日", "date")), r))
        latest_report = max((rd for rd, _ in rows), default="")
        out = []
        for report_date, r in rows:
            out.append(Fundamental(
                symbol=code,
                report_date=report_date,
                pe=pe if report_date == latest_report else 0.0,
                pb=pb if report_date == latest_report else 0.0,
                roe=to_float(row_get(r, "净资产收益率(%)", "净资产收益率", "roe")),
                revenue_yoy=to_float(row_get(r, "主营业务收入增长率(%)", "营业收入同比增长率", "revenue_yoy")),
                profit_yoy=to_float(row_get(r, "净利润增长率(%)", "净利润同比增长率", "profit_yoy")),
                gross_margin=to_float(row_get(r, "销售毛利率(%)", "销售毛利率", "gross_margin")),
                data_source=self.name,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_industry_concept(self, code: str) -> Optional[dict]:
        industry = ""
        try:
            info = self.ak.stock_individual_info_em(symbol=code)
            if info is not None and not info.empty:
                for _, r in info.iterrows():
                    if str(r.get("item")) == "行业":
                        industry = str(r.get("value", ""))
        except Exception as e:  # noqa: BLE001
            logger.warning("akshare 个股行业获取失败 %s: %s", code, e)
        concepts = self._get_concepts(code)
        return IndustryConcept(
            symbol=code,
            industry=industry,
            concepts=concepts,
            data_source=self.name,
            updated_at=self._now(),
        ).to_dict()

    def fetch_index_valuation_history(self, index_name: str, indicator: str) -> Optional[List[dict]]:
        """拉取指数 PE/PB 历史序列（乐咕乐股），indicator 为 'pe' 或 'pb'。

        返回 [{trade_date, pe|pb}]。接口列名随 akshare 版本可能变化，
        使用 row_get 候选列名做防御映射；接口不可用时备选
        ak.index_value_hist_funddb(symbol=index_name, indicator='市盈率'|'市净率')。
        """
        try:
            if indicator == "pe":
                df = self.ak.stock_index_pe_lg(symbol=index_name)
                col_candidates = ("市盈率", "平均市盈率", "滚动市盈率", "pe")
            else:
                df = self.ak.stock_index_pb_lg(symbol=index_name)
                col_candidates = ("市净率", "平均市净率", "pb")
        except Exception as e:  # noqa: BLE001
            logger.warning("指数 %s %s 估值获取失败: %s", index_name, indicator, e)
            return None
        if df is None or df.empty:
            return None
        out = []
        for _, r in df.iterrows():
            date = to_date(row_get(r, "日期", "date", "trade_date"))
            value = to_float(row_get(r, *col_candidates))
            if not date:
                continue
            out.append({"trade_date": date, indicator: value})
        return out

    def _get_concepts(self, code: str) -> List[str]:
        global _concept_map_cache
        if _concept_map_cache is None:
            _concept_map_cache = self._load_concept_map()
        return _concept_map_cache.get(code, [])

    def _load_concept_map(self) -> dict:
        df = self.ak.stock_board_concept_name_em()
        mapping = {}
        if df is None or df.empty or "板块名称" not in df.columns:
            return mapping
        for name in df["板块名称"].tolist():
            try:
                cons = self.ak.stock_board_concept_cons_em(symbol=name)
                if cons is not None and not cons.empty and "代码" in cons.columns:
                    for sym in cons["代码"].astype(str).tolist():
                        mapping.setdefault(sym, []).append(name)
            except Exception as e:  # noqa: BLE001
                logger.warning("概念板块 %s 成分获取失败: %s", name, e)
                continue
        return mapping
