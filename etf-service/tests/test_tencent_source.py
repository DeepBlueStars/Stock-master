from unittest.mock import MagicMock, patch

from app.datasource.tencent_source import TencentSource


def _response(payload):
    response = MagicMock()
    response.raise_for_status.return_value = None
    response.json.return_value = payload
    return response


def test_fetch_stock_daily_maps_tencent_fields():
    payload = {
        "code": 0,
        "data": {
            "sh600000": {
                "day": [
                    ["2024-01-02", "6.630", "6.600", "6.650", "6.600", "220667.000"],
                ]
            }
        },
    }
    with patch("app.datasource.tencent_source.requests.get", return_value=_response(payload)) as get:
        rows = TencentSource().fetch_stock_daily("600000", start_date="20240101")

    assert len(rows) == 1
    quote = rows[0]
    assert quote["symbol"] == "600000"
    assert quote["trade_date"] == "2024-01-02"
    assert quote["open"] == 6.63
    assert quote["close"] == 6.60
    assert quote["high"] == 6.65
    assert quote["low"] == 6.60
    # 腾讯是「手」，入库统一成「股」
    assert quote["volume"] == 22066700.0
    assert quote["data_source"] == "tencent"
    assert get.call_args.kwargs["params"]["param"].startswith("sh600000,day,2024-01-01,")


def test_fetch_stock_daily_returns_none_when_empty():
    payload = {"code": 0, "data": {"sh600000": {"day": []}}}
    with patch("app.datasource.tencent_source.requests.get", return_value=_response(payload)):
        assert TencentSource().fetch_stock_daily("600000") is None
