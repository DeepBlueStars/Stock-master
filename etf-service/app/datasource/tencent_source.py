import logging
from datetime import datetime, timedelta
from typing import List, Optional

import requests

from ..normalize.schema import DailyQuote, to_date, to_float, to_market_symbol
from .base import DataSource

logger = logging.getLogger(__name__)


class TencentSource(DataSource):
    """腾讯行情备用源：不依赖 akshare/baostock/tushare，返回未复权日线。

    接口单页最多约 640 条，按日期向前翻页补全历史；腾讯日线不含成交额，
    amount 统一置 0（该源仅在其它数据源全部失败时兜底）。
    """

    name = "tencent"
    URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
    PAGE_SIZE = 640
    MAX_PAGES = 30

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        symbol = to_market_symbol(code)
        start = to_date(start_date or "2000-01-01") or "2000-01-01"
        end = datetime.now().strftime("%Y-%m-%d")

        rows = self._fetch_pages(symbol, start, end)
        if not rows:
            return None

        now = datetime.now()
        out = []
        seen = set()
        for row in rows:
            if len(row) < 6:
                continue
            trade_date = to_date(row[0])
            if not trade_date or trade_date < start or trade_date > end or trade_date in seen:
                continue
            seen.add(trade_date)
            out.append(DailyQuote(
                symbol=code,
                trade_date=trade_date,
                open=to_float(row[1]),
                close=to_float(row[2]),
                high=to_float(row[3]),
                low=to_float(row[4]),
                # 腾讯返回的成交量单位是「手」，库里统一按「股」存，×100 后再入库
                volume=to_float(row[5]) * 100,
                amount=0.0,
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())

        out.sort(key=lambda q: q["trade_date"])
        return out or None

    def _fetch_pages(self, symbol: str, start: str, end: str) -> list:
        rows = []
        cursor_end = end
        for _ in range(self.MAX_PAGES):
            params = {"param": f"{symbol},day,{start},{cursor_end},{self.PAGE_SIZE},"}
            try:
                response = requests.get(self.URL, params=params, timeout=15)
                response.raise_for_status()
                payload = response.json()
            except Exception as e:  # noqa: BLE001 单页失败交回上层切换其它数据源
                logger.warning("腾讯行情 %s 第 %s 页获取失败: %s", symbol, len(rows), e)
                break

            data = (payload.get("data") or {}).get(symbol) or {}
            page = data.get("day") or data.get("qfqday") or []
            if not page:
                break
            rows.extend(page)

            first = to_date(page[0][0]) if page[0] else ""
            if len(page) < self.PAGE_SIZE or not first or first <= start:
                break
            try:
                cursor_end = (datetime.strptime(first, "%Y-%m-%d") - timedelta(days=1)).strftime("%Y-%m-%d")
            except ValueError:
                break
        return rows
