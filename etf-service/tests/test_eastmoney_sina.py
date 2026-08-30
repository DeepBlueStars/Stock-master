from unittest.mock import patch
import datetime

import pandas as pd

from app.datasource.eastmoney_sina import EastmoneySinaSource


def test_fetch_stock_daily_maps_standard_format():
    src = EastmoneySinaSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_a_daily.return_value = pd.DataFrame({
            "date": ["2024-01-02"], "open": [10.0], "high": [11.0], "low": [9.8],
            "close": [10.5], "volume": [1000], "amount": [10500.0],
        })
        out = src.fetch_stock_daily("600000")
    q = out[0]
    assert q["symbol"] == "600000"
    assert q["close"] == 10.5
    assert q["data_source"] == "eastmoney_sina"


def test_fetch_index_daily_no_amount_defaults_zero():
    src = EastmoneySinaSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_index_daily.return_value = pd.DataFrame({
            "date": ["2024-01-02"], "open": [3100.0], "high": [3160.0],
            "low": [3090.0], "close": [3150.0], "volume": [12345678],
        })
        out = src.fetch_index_daily("sh000300")
    q = out[0]
    assert q["amount"] == 0.0
    assert q["close"] == 3150.0


def test_fetch_index_daily_handles_datetime_date_column():
    src = EastmoneySinaSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_index_daily.return_value = pd.DataFrame({
            "date": [datetime.date(2024, 1, 2), datetime.date(2024, 1, 3)],
            "open": [3100.0, 3110.0], "high": [3160.0, 3170.0],
            "low": [3090.0, 3100.0], "close": [3150.0, 3160.0],
            "volume": [12345678, 23456789],
        })
        out = src.fetch_index_daily("sh000001", "20240102")
    assert len(out) == 2
    assert out[0]["trade_date"] == "2024-01-02"
    assert out[1]["trade_date"] == "2024-01-03"


def test_norm_date():
    assert EastmoneySinaSource._norm_date("20250101") == "2025-01-01"
    assert EastmoneySinaSource._norm_date("2025-01-01") == "2025-01-01"
    assert EastmoneySinaSource._norm_date("1990-01-01") == "1990-01-01"
