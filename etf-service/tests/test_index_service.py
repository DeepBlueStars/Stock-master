from importlib import import_module
from unittest.mock import patch
import pandas as pd

from app.services.index_service import IndexService, INDEX_CONSTITUENTS


def test_fetch_and_store_constituents_writes_records():
    svc = IndexService()
    svc.ak = _FakeAk()
    svc.mongo = _FakeMongo()
    n = svc.fetch_and_store_constituents()
    assert n == 4
    assert ("000300", "600000") in svc.mongo.written


class _FakeAk:
    def index_stock_cons(self, symbol=None):
        return pd.DataFrame({"成分券代码": ["600000", "000001"]})


class _FakeMongo:
    def __init__(self):
        self.written = set()

    def upsert_index_constituent(self, data):
        self.written.add((data["index_code"], data["symbol"]))
        return True


class _IdxMongo(_FakeMongo):
    def upsert_index_daily_quote(self, data):
        self.written.add((data["index_code"], data["trade_date"]))
        return True


def test_fetch_index_daily_history_writes_records():
    svc = IndexService()
    svc.ak = None
    svc.mongo = _IdxMongo()

    def fake_fetch(sources, method, code, **kwargs):
        return [{"symbol": "sh000300", "trade_date": "2024-01-02", "close": 3150.0,
                 "open": 3100.0, "high": 3160.0, "low": 3090.0, "volume": 1, "amount": 2,
                 "data_source": "akshare"}]

    mod = import_module("app.services.index_service")
    with patch.object(mod, "fetch_with_fallback", side_effect=fake_fetch):
        n = svc.fetch_index_daily_history("sh000300")
    assert n == 1
    assert ("sh000300", "2024-01-02") in svc.mongo.written


def test_update_index_daily_writes_and_dedups():
    svc = IndexService()
    svc.ak = None
    svc.mongo = _IdxMongo()

    def fake_fetch(sources, method, code, **kwargs):
        return [{"symbol": code, "trade_date": "2024-01-02", "close": 1.0,
                 "open": 1.0, "high": 1.0, "low": 1.0, "volume": 1, "amount": 1,
                 "data_source": "akshare"}]

    mod = import_module("app.services.index_service")
    with patch.object(mod, "fetch_with_fallback", side_effect=fake_fetch):
        n = svc.update_index_daily()
    assert n == len(IndexService.INDEX_CODES)
    # 同一指数同一交易日只写入一次（upsert 去重）
    assert len(svc.mongo.written) == n
