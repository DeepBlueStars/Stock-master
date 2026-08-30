from unittest.mock import MagicMock

from app.datasource.baostock_source import BaostockSource


class _Rs:
    """模拟 baostock 返回的 ResultData：error_code + fields + next()/get_row_data()。"""

    def __init__(self, fields, rows):
        self.error_code = "0"
        self.error_msg = ""
        self.fields = fields
        self._rows = rows
        self._idx = -1

    def next(self):
        self._idx += 1
        return self._idx < len(self._rows)

    def get_row_data(self):
        return self._rows[self._idx]


def test_fetch_stock_daily_maps_standard_format():
    src = BaostockSource()
    src._bs = MagicMock()
    src._bs.query_history_k_data_plus.return_value = _Rs(
        ["date", "open", "high", "low", "close", "volume", "amount"],
        [["2024-01-02", "10.0", "11.0", "9.8", "10.5", "1000", "10500.0"]],
    )
    out = src.fetch_stock_daily("000001")
    assert len(out) == 1
    q = out[0]
    assert q["symbol"] == "000001"
    assert q["trade_date"] == "2024-01-02"
    assert q["close"] == 10.5
    assert q["data_source"] == "baostock"


def test_fetch_fundamentals_maps_standard_format():
    src = BaostockSource()
    src._bs = MagicMock()
    src._bs.query_profit_data.return_value = _Rs(
        ["code", "pubDate", "statDate", "roeAvg", "gpMargin"],
        [["sh.600000", "2024-04-01", "2024-03-31", "8.0", "30.0"]],
    )
    src._bs.query_growth_data.return_value = _Rs(
        ["code", "statDate", "YOYNI"],
        [["sh.600000", "2024-03-31", "15.0"]],
    )
    out = src.fetch_fundamentals("600000")
    f = out[0]
    assert f["roe"] == 8.0
    assert f["gross_margin"] == 30.0
    assert f["profit_yoy"] == 15.0
