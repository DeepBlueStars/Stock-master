"""ETF 实时快照 → 日线文档的字段换算测试（不连网络、不连数据库）。"""

from datetime import datetime

from app.services.etf_service import ETFService


def _eastmoney_row():
    """东财 fund_etf_spot_em 的一行：成交量单位是「手」。"""
    return {
        "代码": "515180",
        "名称": "红利ETF易方达",
        "开盘价": 1.374,
        "最高价": 1.392,
        "最低价": 1.362,
        "最新价": 1.383,
        "成交量": 6818096,
        "成交额": 943959952.0,
        "涨跌额": 0.009,
        "涨跌幅": 0.65,
    }


def test_eastmoney_volume_is_converted_from_lots_to_shares():
    data = ETFService().transform_etf_data_for_db("sh515180", _eastmoney_row())

    assert data["symbol"] == "515180"
    assert data["volume"] == 681809600.0
    assert data["open"] == 1.374
    assert data["high"] == 1.392
    assert data["low"] == 1.362
    assert data["close"] == 1.383
    assert data["trade_date"] == datetime.now().strftime("%Y-%m-%d")


def test_sina_fallback_volume_stays_in_shares():
    """新浪兜底源没有「开盘价」等字段，成交量本身就是股，不能再乘 100。"""
    row = {"代码": "515180", "最新价": 1.383, "成交量": 681809600, "成交额": 943959952.0, "涨跌幅": "-0.65%"}

    data = ETFService().transform_etf_data_for_db("515180", row)

    assert data["volume"] == 681809600.0
    assert data["pct_change"] == -0.65
