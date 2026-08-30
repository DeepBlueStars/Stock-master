from unittest.mock import patch
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
