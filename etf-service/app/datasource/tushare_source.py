import logging
import os
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import DailyQuote, Fundamental, to_float, to_date, row_get
from .base import DataSource

logger = logging.getLogger(__name__)


class TushareSource(DataSource):
    name = "tushare"

    def __init__(self):
        self.token = os.getenv("TUSHARE_TOKEN", "")
        self._pro = None

    def _ensure_pro(self):
        if not self.token:
            return None
        if self._pro is None:
            import tushare as ts
            ts.set_token(self.token)
            self._pro = ts.pro_api()
        return self._pro

    def _ts_code(self, code: str) -> str:
        code = code.upper()
        if code.endswith((".SH", ".SZ", ".BJ")):
            return code
        if code.startswith(("SH", "SZ")):
            return code[2:] + "." + code[:2]
        return code + (".SH" if code.startswith("6") else ".SZ")

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        pro = self._ensure_pro()
        if pro is None:
            return None
        start = (start_date or "19900101").replace("-", "")
        end = datetime.now().strftime("%Y%m%d")
        df = pro.index_daily(ts_code=self._ts_code(code), start_date=start, end_date=end)
        return self._map_daily(code, df)

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        pro = self._ensure_pro()
        if pro is None:
            return None
        start = (start_date or "19900101").replace("-", "")
        end = datetime.now().strftime("%Y%m%d")
        df = pro.daily(ts_code=self._ts_code(code), start_date=start, end_date=end)
        return self._map_daily(code, df)

    def _map_daily(self, code: str, df) -> Optional[List[dict]]:
        if df is None or df.empty:
            return None
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "trade_date")),
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "vol", "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_fundamentals(self, code: str) -> Optional[List[dict]]:
        pro = self._ensure_pro()
        if pro is None:
            return None
        ts_code = self._ts_code(code)
        pe = pb = 0.0
        try:
            df_val = pro.daily_basic(ts_code=ts_code)
            if df_val is not None and not df_val.empty:
                last = df_val.iloc[-1]
                pe = to_float(row_get(last, "pe_ttm", "pe"))
                pb = to_float(row_get(last, "pb"))
        except Exception as e:  # noqa: BLE001
            logger.warning("tushare 估值指标获取失败 %s: %s", code, e)
        try:
            df_fin = pro.fina_indicator(ts_code=ts_code)
        except Exception as e:  # noqa: BLE001
            logger.warning("tushare 财务指标获取失败 %s: %s", code, e)
            df_fin = None
        if df_fin is None or df_fin.empty:
            return None
        now = datetime.now()
        rows = []
        for _, r in df_fin.iterrows():
            rows.append((to_date(row_get(r, "end_date", "ann_date")), r))
        latest_report = max((rd for rd, _ in rows), default="")
        out = []
        for report_date, r in rows:
            out.append(Fundamental(
                symbol=code,
                report_date=report_date,
                pe=pe if report_date == latest_report else 0.0,
                pb=pb if report_date == latest_report else 0.0,
                roe=to_float(row_get(r, "roe")),
                revenue_yoy=to_float(row_get(r, "q_sales_yoy", "revenue_yoy")),
                profit_yoy=to_float(row_get(r, "q_profit_yoy", "profit_yoy")),
                gross_margin=to_float(row_get(r, "grossprofit_margin", "gross_margin")),
                data_source=self.name,
                updated_at=now,
            ).to_dict())
        return out
