from datetime import datetime
from importlib import import_module
from unittest.mock import patch

from app.services.stock_service import StockService


class _FakeMongo:
    def __init__(self, symbols, latest=None):
        self.symbols = symbols
        self.latest = latest or {}
        self.written = set()
        self.batches = []

    def get_constituent_symbols(self):
        return self.symbols

    def get_latest_trade_date(self, symbol):
        return self.latest.get(symbol)

    def bulk_upsert_stock_daily_kline(self, records):
        self.batches.append(len(records))
        for data in records:
            self.written.add((data["symbol"], data["trade_date"]))
        return len(records)

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


def test_update_stock_daily_incremental_only_upserts():
    svc = StockService(mongo=_FakeMongo(["600000"]))
    mod = import_module("app.services.stock_service")
    with patch.object(mod, "fetch_with_fallback",
                      return_value=[_quote("600000")]):
        n = svc.update_stock_daily_incremental()
    assert n == 1
    assert ("600000", "2024-01-02") in svc.mongo.written


def test_update_stock_daily_incremental_starts_after_latest_trade_date():
    svc = StockService(mongo=_FakeMongo(["600000"], latest={"600000": "2024-01-02"}))
    mod = import_module("app.services.stock_service")
    captured = {}

    def fake_fetch(sources, method, code, **kwargs):
        captured["code"] = code
        captured["start_date"] = kwargs.get("start_date")
        return [_quote(code, "2024-01-03")]

    with patch.object(mod, "fetch_with_fallback", side_effect=fake_fetch):
        svc.update_stock_daily_incremental()

    assert captured["code"] == "600000"
    assert captured["start_date"] == "2024-01-03"


def test_update_stock_daily_incremental_skips_symbol_already_up_to_date():
    today = datetime.now().strftime("%Y-%m-%d")
    svc = StockService(mongo=_FakeMongo(["600000"], latest={"600000": today}))
    mod = import_module("app.services.stock_service")
    with patch.object(mod, "fetch_with_fallback") as fetch:
        svc.update_stock_daily_incremental()
    fetch.assert_not_called()


def test_update_stock_daily_incremental_defaults_to_dashed_start_date():
    svc = StockService(mongo=_FakeMongo(["600000"]))
    mod = import_module("app.services.stock_service")
    captured = {}

    def fake_fetch(sources, method, code, **kwargs):
        captured["start_date"] = kwargs.get("start_date")
        return []

    with patch.object(mod, "fetch_with_fallback", side_effect=fake_fetch):
        svc.update_stock_daily_incremental()

    assert captured["start_date"] == "2000-01-01"


def test_update_stock_daily_incremental_batches_bulk_writes():
    svc = StockService(mongo=_FakeMongo(["600000"]))
    mod = import_module("app.services.stock_service")
    quotes = [_quote("600000", "2024-01-02") for _ in range(1001)]
    with patch.object(mod, "fetch_with_fallback", return_value=quotes):
        n = svc.update_stock_daily_incremental()

    assert n == 1001
    assert svc.mongo.batches == [1000, 1]


class _MarketQuoteMongo(_FakeMongo):
    def __init__(self, quotes, symbols=None, latest=None):
        super().__init__(symbols or [], latest)
        self.quotes = quotes
        self.records = []

    def get_market_quotes(self):
        return self.quotes

    def bulk_upsert_stock_daily_kline(self, records):
        self.records.extend(records)
        return super().bulk_upsert_stock_daily_kline(records)


def _market_quote(symbol="000001", trade_date="20261029", **overrides):
    doc = {
        "symbol": symbol, "code": symbol, "trade_date": trade_date,
        "open": 11.53, "high": 11.67, "low": 11.40, "close": 11.64,
        "volume": 186424507.0, "amount": 2152393886.0,
    }
    doc.update(overrides)
    return doc


def test_market_quote_trade_date_defaults_to_next_day():
    svc = StockService(mongo=_FakeMongo([]))
    assert svc._market_quote_trade_date("20261029") == "2026-10-30"
    assert svc._market_quote_trade_date(None) == ""


def test_sync_stock_daily_from_market_quotes_maps_snapshot(monkeypatch):
    mod = import_module("app.services.stock_service")
    now = datetime(2026, 10, 30, 16, 0)
    monkeypatch.setattr(mod, "beijing_now", lambda: now)
    svc = StockService(mongo=_MarketQuoteMongo([_market_quote()]))

    n = svc.sync_stock_daily_from_market_quotes()

    assert n == 1
    record = svc.mongo.records[0]
    assert record["symbol"] == "000001"
    assert record["trade_date"] == "2026-10-30"
    assert record["open"] == 11.53
    assert record["high"] == 11.67
    assert record["low"] == 11.40
    assert record["close"] == 11.64
    # 股 → 手
    assert record["volume"] == 1864245.07
    # amount 保持元
    assert record["amount"] == 2152393886.0
    assert record["data_source"] == "market_quotes"
    assert record["created_at"] == record["updated_at"] == now


def test_sync_stock_daily_from_market_quotes_skips_invalid_prices(monkeypatch):
    mod = import_module("app.services.stock_service")
    monkeypatch.setattr(mod, "beijing_now", lambda: datetime(2026, 10, 30, 16, 0))
    quotes = [
        _market_quote("000001"),
        _market_quote("600400", close=float("nan")),
        _market_quote("600519", open=0.0),
    ]
    svc = StockService(mongo=_MarketQuoteMongo(quotes))

    n = svc.sync_stock_daily_from_market_quotes()

    assert n == 1
    assert [record["symbol"] for record in svc.mongo.records] == ["000001"]


def test_sync_stock_daily_from_market_quotes_deduplicates_symbol_and_date(monkeypatch):
    mod = import_module("app.services.stock_service")
    monkeypatch.setattr(mod, "beijing_now", lambda: datetime(2026, 10, 30, 16, 0))
    svc = StockService(mongo=_MarketQuoteMongo([
        _market_quote("000001", close=11.64),
        _market_quote("000001", close=11.65),
    ]))

    n = svc.sync_stock_daily_from_market_quotes()

    assert n == 1
    assert len(svc.mongo.records) == 1
    assert svc.mongo.records[0]["close"] == 11.65


def test_sync_stock_daily_from_market_quotes_falls_back_when_empty(monkeypatch):
    mod = import_module("app.services.stock_service")
    monkeypatch.setattr(mod, "beijing_now", lambda: datetime(2026, 10, 30, 16, 0))
    svc = StockService(mongo=_MarketQuoteMongo([], symbols=["600000"]))

    with patch.object(mod, "fetch_with_fallback", return_value=[_quote("600000", "2026-10-30")]):
        n = svc.sync_stock_daily_from_market_quotes()

    assert n == 1
    assert ("600000", "2026-10-30") in svc.mongo.written


def test_sync_stock_daily_from_market_quotes_falls_back_when_stale(monkeypatch):
    mod = import_module("app.services.stock_service")
    monkeypatch.setattr(mod, "beijing_now", lambda: datetime(2026, 10, 31, 16, 0))
    svc = StockService(mongo=_MarketQuoteMongo([_market_quote()], symbols=["600000"]))

    with patch.object(mod, "fetch_with_fallback", return_value=[_quote("600000", "2026-10-31")]):
        n = svc.sync_stock_daily_from_market_quotes()

    assert n == 1
    assert ("600000", "2026-10-31") in svc.mongo.written
    assert all(record["symbol"] != "000001" for record in svc.mongo.records)


def _fundamental(symbol):
    return {"symbol": symbol, "report_date": "2024-03-31", "pe": 12.0, "pb": 1.5,
            "roe": 8.0, "revenue_yoy": 10.0, "profit_yoy": 15.0, "gross_margin": 30.0,
            "data_source": "akshare"}


class _FundMongo(_FakeMongo):
    def upsert_stock_fundamental(self, data):
        self.written.add((data["symbol"], data["report_date"]))
        return True


def test_fetch_and_store_fundamentals_writes_records():
    svc = StockService(mongo=_FundMongo(["600000"]))
    mod = import_module("app.services.stock_service")
    with patch.object(mod, "fetch_with_fallback",
                      side_effect=lambda s, m, c, **kw: [_fundamental(c)]):
        n = svc.fetch_and_store_fundamentals()
    assert n == 1
    assert ("600000", "2024-03-31") in svc.mongo.written


def _industry_concept(symbol):
    return {"symbol": symbol, "industry": "银行", "concepts": ["融资融券"],
            "data_source": "akshare"}


class _IcMongo(_FakeMongo):
    def upsert_stock_industry_concept(self, data):
        self.written.add(data["symbol"])
        return True


def test_fetch_and_store_industry_concept_writes_records():
    svc = StockService(mongo=_IcMongo(["600000"]))
    mod = import_module("app.services.stock_service")
    with patch.object(mod, "fetch_with_fallback",
                      side_effect=lambda s, m, c, **kw: _industry_concept(c)):
        n = svc.fetch_and_store_industry_concept()
    assert n == 1
    assert "600000" in svc.mongo.written
