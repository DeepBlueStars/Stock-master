import pandas as pd

from app.datasource.tushare_source import TushareSource


def test_no_token_returns_none(monkeypatch):
    monkeypatch.delenv("TUSHARE_TOKEN", raising=False)
    src = TushareSource()
    assert src.fetch_stock_daily("000001") is None
    assert src.fetch_fundamentals("000001") is None


def test_fetch_fundamentals_maps_standard_format(monkeypatch):
    monkeypatch.setenv("TUSHARE_TOKEN", "test-token")
    src = TushareSource()
    src._pro = type("Pro", (), {
        "daily_basic": lambda self, **kw: pd.DataFrame({"pe_ttm": [12.3], "pb": [1.5]}),
        "fina_indicator": lambda self, **kw: pd.DataFrame({
            "end_date": ["20240331"], "roe": [8.0],
            "grossprofit_margin": [30.0], "q_sales_yoy": [10.0], "q_profit_yoy": [15.0],
        }),
    })()
    out = src.fetch_fundamentals("000001")
    f = out[0]
    assert f["pe"] == 12.3 and f["pb"] == 1.5
    assert f["roe"] == 8.0 and f["gross_margin"] == 30.0
    assert f["revenue_yoy"] == 10.0 and f["profit_yoy"] == 15.0


def test_fetch_stock_daily_maps_standard_format(monkeypatch):
    monkeypatch.setenv("TUSHARE_TOKEN", "test-token")
    src = TushareSource()
    src._pro = type("Pro", (), {
        "daily": lambda self, **kw: pd.DataFrame({
            "trade_date": ["20240102"], "open": [10.0], "high": [11.0],
            "low": [9.8], "close": [10.5], "vol": [1000.0], "amount": [1050.0],
        }),
    })()
    out = src.fetch_stock_daily("000001")
    q = out[0]
    assert q["symbol"] == "000001"
    assert q["close"] == 10.5
    assert q["data_source"] == "tushare"


def test_ts_code_handles_prefixed_index_codes():
    src = TushareSource()
    assert src._ts_code("sh000001") == "000001.SH"
    assert src._ts_code("sz399006") == "399006.SZ"
    assert src._ts_code("sh000300") == "000300.SH"


def test_ts_code_keeps_stock_and_suffixed_codes():
    src = TushareSource()
    assert src._ts_code("000001") == "000001.SZ"
    assert src._ts_code("600000") == "600000.SH"
    assert src._ts_code("000001.SH") == "000001.SH"
    assert src._ts_code("600000.SZ") == "600000.SZ"


def test_fetch_index_daily_preserves_full_symbol(monkeypatch):
    monkeypatch.setenv("TUSHARE_TOKEN", "test-token")
    src = TushareSource()
    src._pro = type("Pro", (), {
        "index_daily": lambda self, **kw: pd.DataFrame({
            "trade_date": ["20240102"], "open": [10.0], "high": [11.0],
            "low": [9.8], "close": [10.5], "vol": [1000.0], "amount": [1050.0],
        }),
    })()
    out = src.fetch_index_daily("sh000001")
    assert out[0]["symbol"] == "sh000001"


def test_fetch_fundamentals_only_applies_pe_pb_to_latest_report(monkeypatch):
    monkeypatch.setenv("TUSHARE_TOKEN", "test-token")
    src = TushareSource()
    src._pro = type("Pro", (), {
        "daily_basic": lambda self, **kw: pd.DataFrame({"pe_ttm": [12.3], "pb": [1.5]}),
        "fina_indicator": lambda self, **kw: pd.DataFrame({
            "end_date": ["20231231", "20240331"], "roe": [7.0, 8.0],
            "grossprofit_margin": [29.0, 30.0], "q_sales_yoy": [9.0, 10.0],
            "q_profit_yoy": [14.0, 15.0],
        }),
    })()
    out = src.fetch_fundamentals("000001")
    by_date = {f["report_date"]: f for f in out}
    assert by_date["2024-03-31"]["pe"] == 12.3
    assert by_date["2024-03-31"]["pb"] == 1.5
    assert by_date["2023-12-31"]["pe"] == 0.0
    assert by_date["2023-12-31"]["pb"] == 0.0
