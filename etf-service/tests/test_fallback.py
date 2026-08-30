from app.datasource import get_source_chain
from app.datasource.base import fetch_with_fallback


class _FakeSource:
    def __init__(self, name, result=None, fail=False):
        self.name = name
        self._result = result
        self._fail = fail

    def fetch_stock_daily(self, code, start_date=None):
        if self._fail:
            raise RuntimeError("boom")
        return self._result


def test_source_chain_order():
    chain = get_source_chain()
    assert [s.name for s in chain] == ["akshare", "baostock", "tushare", "eastmoney_sina"]


def test_fallback_uses_second_source_when_first_fails():
    ok = _FakeSource("second", result=[{"close": 1.0}])
    out = fetch_with_fallback([_FakeSource("first", fail=True), ok], "fetch_stock_daily", "000001")
    assert out == [{"close": 1.0}]


def test_fallback_returns_none_when_all_fail():
    out = fetch_with_fallback([_FakeSource("first", fail=True)], "fetch_stock_daily", "000001")
    assert out is None
