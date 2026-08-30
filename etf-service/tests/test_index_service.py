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
