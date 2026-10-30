from importlib import import_module
from unittest.mock import patch

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
    assert [s.name for s in chain] == ["akshare", "baostock", "tushare", "tencent", "eastmoney_sina"]


def test_fallback_uses_second_source_when_first_fails():
    ok = _FakeSource("second", result=[{"close": 1.0}])
    out = fetch_with_fallback([_FakeSource("first", fail=True), ok], "fetch_stock_daily", "000001")
    assert out == [{"close": 1.0}]


def test_fallback_returns_none_when_all_fail():
    out = fetch_with_fallback([_FakeSource("first", fail=True)], "fetch_stock_daily", "000001")
    assert out is None


def test_fallback_retries_each_source():
    flaky = _FakeSource("flaky", result=[{"close": 1.0}], fail=True)
    base = import_module("app.datasource.base")
    with patch.object(base.time, "sleep") as slp:
        out = fetch_with_fallback([flaky], "fetch_stock_daily", "000001", retries=2, retry_delay=1)
    assert out is None
    assert slp.call_count == 2  # 初次失败 + 2 次重试间各 sleep 一次


def test_fallback_does_not_raise_on_total_failure():
    out = fetch_with_fallback([_FakeSource("f", fail=True)], "fetch_stock_daily", "000001", retries=1, retry_delay=0)
    assert out is None
