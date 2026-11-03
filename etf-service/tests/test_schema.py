from datetime import datetime

from app.normalize.schema import (
    DailyQuote,
    Fundamental,
    IndustryConcept,
    to_float,
    to_date,
    to_market_symbol,
)


def test_daily_quote_to_dict_has_standard_fields():
    q = DailyQuote(symbol="000001", trade_date="2024-01-02", open=1.0, high=2.0,
                   low=0.5, close=1.5, volume=100, amount=200, data_source="akshare")
    d = q.to_dict()
    assert set(d) == {"symbol", "trade_date", "open", "high", "low", "close",
                      "volume", "amount", "data_source", "created_at", "updated_at"}
    assert d["trade_date"] == "2024-01-02"


def test_to_float_cleans_percent_and_comma():
    assert to_float("12.34%") == 12.34
    assert to_float("1,234.5") == 1234.5
    assert to_float("--", 0.0) == 0.0
    assert to_float(None, 0.0) == 0.0


def test_to_date_normalizes_formats():
    assert to_date("20240102") == "2024-01-02"
    assert to_date("2024/01/02") == "2024-01-02"
    assert to_date("2024-01-02 00:00:00") == "2024-01-02"


def test_to_market_symbol():
    assert to_market_symbol("600000") == "sh600000"
    assert to_market_symbol("000001") == "sz000001"
    assert to_market_symbol("sh600000") == "sh600000"
    # 5 开头是沪市 ETF/LOF，不能归到深市
    assert to_market_symbol("515180") == "sh515180"
    assert to_market_symbol("159919") == "sz159919"
