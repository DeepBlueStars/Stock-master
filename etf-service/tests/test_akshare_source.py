from unittest.mock import patch
import pandas as pd

from app.datasource.akshare_source import AkshareSource


def _index_df():
    return pd.DataFrame({
        "日期": ["2024-01-02"],
        "开盘": [3100.0],
        "收盘": [3150.5],
        "最高": [3160.0],
        "最低": [3090.0],
        "成交量": [12345678],
        "成交额": [4.5e9],
    })


def test_fetch_index_daily_maps_standard_format():
    src = AkshareSource()
    with patch.object(src, "ak") as ak:
        ak.index_zh_a_hist.return_value = _index_df()
        out = src.fetch_index_daily("000300")
    assert len(out) == 1
    q = out[0]
    assert q["symbol"] == "000300"
    assert q["trade_date"] == "2024-01-02"
    assert q["close"] == 3150.5
    assert q["amount"] == 4.5e9
    assert q["data_source"] == "akshare"


def test_fetch_stock_daily_maps_standard_format():
    src = AkshareSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_a_hist.return_value = pd.DataFrame({
            "日期": ["2024-01-02"], "开盘": [10.0], "收盘": [10.5], "最高": [11.0],
            "最低": [9.8], "成交量": [1000], "成交额": [10500.0],
        })
        out = src.fetch_stock_daily("000001")
    assert out[0]["symbol"] == "000001"
    assert out[0]["close"] == 10.5


def test_fetch_fundamentals_maps_standard_format():
    src = AkshareSource()
    with patch.object(src, "ak") as ak:
        ak.stock_a_indicator_lg.return_value = pd.DataFrame({"pe_ttm": [12.3], "pb": [1.5]})
        ak.stock_financial_analysis_indicator.return_value = pd.DataFrame({
            "日期": ["2024-03-31"],
            "净资产收益率(%)": [8.0],
            "主营业务收入增长率(%)": [10.0],
            "净利润增长率(%)": [15.0],
            "销售毛利率(%)": [30.0],
        })
        out = src.fetch_fundamentals("000001")
    f = out[0]
    assert f["symbol"] == "000001"
    assert f["report_date"] == "2024-03-31"
    assert f["pe"] == 12.3 and f["pb"] == 1.5
    assert f["roe"] == 8.0 and f["gross_margin"] == 30.0
