import logging
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import DailyQuote, to_float, to_date, row_get, to_market_symbol
from .base import DataSource

logger = logging.getLogger(__name__)


class EastmoneySinaSource(DataSource):
    name = "eastmoney_sina"

    def __init__(self):
        import akshare as ak
        self.ak = ak

    @staticmethod
    def _norm_date(value: str) -> str:
        """把 YYYYMMDD 或 YYYY-MM-DD 归一化为 YYYY-MM-DD。"""
        s = str(value).replace("-", "").strip()
        if len(s) >= 8 and s[:8].isdigit():
            return f"{s[:4]}-{s[4:6]}-{s[6:8]}"
        return str(value)[:10]

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = self._norm_date(start_date or "1990-01-01")
        end = datetime.now().strftime("%Y-%m-%d")
        df = self.ak.stock_zh_index_daily(symbol=to_market_symbol(code))
        if df is None or df.empty:
            return None
        df["date"] = df["date"].astype(str)
        df = df[(df["date"] >= start) & (df["date"] <= end)]
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "date")),
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "1990-01-01")[:10]
        end = datetime.now().strftime("%Y-%m-%d")
        df = self.ak.stock_zh_a_daily(symbol=to_market_symbol(code), start_date=start, end_date=end, adjust="")
        if df is None or df.empty:
            return None
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "date")),
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_etf_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        """新浪 ETF 历史日线（fund_etf_hist_sina）。

        该接口不接收起始日期，返回全量历史，这里在本地按 start_date 截断。
        成交量单位是股，与 etf_daily_quotes 存量口径一致，不做换算。
        """
        start = (start_date or "1990-01-01")[:10]
        df = self.ak.fund_etf_hist_sina(symbol=to_market_symbol(code))
        if df is None or df.empty:
            return None
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            trade_date = to_date(row_get(r, "date"))
            if not trade_date or trade_date < start:
                continue
            out.append(DailyQuote(
                symbol=code,
                trade_date=trade_date,
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out
