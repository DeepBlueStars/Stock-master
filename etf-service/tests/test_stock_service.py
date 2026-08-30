from importlib import import_module
from unittest.mock import patch

from app.services.stock_service import StockService


class _FakeMongo:
    def __init__(self, symbols):
        self.symbols = symbols
        self.written = set()

    def get_constituent_symbols(self):
        return self.symbols

    def upsert_stock_daily_kline(self, data):
        self.written.add((data["symbol"], data["trade_date"]))
        return True


def _quote(symbol, trade_date="2024-01-02"):
    return {"symbol": symbol, "trade_date": trade_date, "open": 1.0, "high": 2.0,
            "low": 0.5, "close": 1.5, "volume": 1, "amount": 2, "data_source": "akshare"}


def test_fetch_stock_daily_history_writes_records():
    svc = StockService(mongo=_FakeMongo([]))
    mod = import_module("app.services.stock_service")
    with patch.object(mod, "fetch_with_fallback", return_value=[_quote("600000")]):
        n = svc.fetch_stock_daily_history("600000")
    assert n == 1
    assert ("600000", "2024-01-02") in svc.mongo.written


def test_fetch_all_stock_daily_history_iterates_pool():
    svc = StockService(mongo=_FakeMongo(["600000", "000001"]))
    mod = import_module("app.services.stock_service")
    with patch.object(mod, "fetch_with_fallback",
                      side_effect=lambda s, m, c, **kw: [_quote(c)]):
        n = svc.fetch_all_stock_daily_history()
    assert n == 2
