import logging
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import DailyQuote, Fundamental, to_float, to_date, row_get
from .base import DataSource

logger = logging.getLogger(__name__)


class BaostockSource(DataSource):
    name = "baostock"

    def __init__(self):
        self._bs = None

    def _ensure_login(self):
        if self._bs is None:
            import baostock as bs
            self._bs = bs
            lg = bs.login()
            if lg.error_code != "0":
                raise RuntimeError(f"baostock 登录失败: {lg.error_msg}")
        return self._bs

    def _bs_code(self, code: str) -> str:
        code = code.lower().replace(".", "")
        if code.startswith(("sh", "sz")):
            return f"{code[:2]}.{code[2:]}"
        return ("sh." if code.startswith("6") else "sz.") + code

    def _fetch_daily(self, code: str, start_date: Optional[str], bs_code: str) -> Optional[List[dict]]:
        bs = self._ensure_login()
        start = (start_date or "1990-01-01").replace("/", "-")
        end = datetime.now().strftime("%Y-%m-%d")
        rs = bs.query_history_k_data_plus(
            bs_code, "date,open,high,low,close,volume,amount",
            start_date=start, end_date=end, frequency="d", adjustflag="3")
        if rs.error_code != "0":
            logger.warning("baostock %s 查询失败: %s", bs_code, rs.error_msg)
            return None
        rows = []
        while rs.next():
            rows.append(rs.get_row_data())
        if not rows:
            return None
        fields = rs.fields
        now = datetime.now()
        out = []
        for raw in rows:
            row = dict(zip(fields, raw))
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row.get("date")),
                open=to_float(row.get("open")),
                high=to_float(row.get("high")),
                low=to_float(row.get("low")),
                close=to_float(row.get("close")),
                volume=to_float(row.get("volume")),
                amount=to_float(row.get("amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        return self._fetch_daily(code, start_date, self._bs_code(code))

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        return self._fetch_daily(code, start_date, self._bs_code(code))

    def fetch_fundamentals(self, code: str) -> Optional[List[dict]]:
        bs = self._ensure_login()
        bs_code = self._bs_code(code)
        year = datetime.now().year
        profit_rows = []
        growth_rows = []
        for quarter in (1, 2, 3, 4):
            rs_profit = bs.query_profit_data(code=bs_code, year=year, quarter=quarter)
            if rs_profit.error_code == "0":
                while rs_profit.next():
                    profit_rows.append(dict(zip(rs_profit.fields, rs_profit.get_row_data())))
            rs_growth = bs.query_growth_data(code=bs_code, year=year, quarter=quarter)
            if rs_growth.error_code == "0":
                while rs_growth.next():
                    growth_rows.append(dict(zip(rs_growth.fields, rs_growth.get_row_data())))
        if not profit_rows:
            return None
        growth_by_stat = {g.get("statDate"): g for g in growth_rows}
        now = datetime.now()
        out = []
        for p in profit_rows:
            g = growth_by_stat.get(p.get("statDate"), {})
            out.append(Fundamental(
                symbol=code,
                report_date=to_date(p.get("statDate")),
                pe=0.0,
                pb=0.0,
                roe=to_float(p.get("roeAvg")),
                revenue_yoy=0.0,
                profit_yoy=to_float(g.get("YOYNI")),
                gross_margin=to_float(p.get("gpMargin")),
                data_source=self.name,
                updated_at=now,
            ).to_dict())
        return out
