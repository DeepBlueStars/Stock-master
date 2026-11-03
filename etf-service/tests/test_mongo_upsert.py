from datetime import datetime
from unittest.mock import patch, MagicMock

from app.database.mongo import MongoDBClient


def _client():
    with patch("app.database.mongo.MongoClient") as mc:
        fake_db = MagicMock()
        mc.return_value.__getitem__.return_value = fake_db
        c = MongoDBClient()
        return c, fake_db


def test_new_collections_registered():
    c, _ = _client()
    for name in ("index_daily_quotes", "stock_daily_kline", "stock_fundamentals",
                 "stock_industry_concept", "index_constituents", "market_quotes"):
        assert getattr(c, name) is not None


def test_get_market_quotes_only_reads_copy_fields():
    c, _ = _client()
    c.market_quotes.find.return_value = []
    assert c.get_market_quotes() == []
    projection = c.market_quotes.find.call_args.args[1]
    assert projection["_id"] == 0
    for field in ("symbol", "trade_date", "open", "high", "low", "close", "volume", "amount"):
        assert field in projection


def test_upsert_index_daily_quote_uses_dedup_key():
    c, _ = _client()
    data = {"index_code": "000300", "trade_date": "2024-01-02", "close": 3150.0}
    assert c.upsert_index_daily_quote(data) is True
    c.index_daily_quotes.update_one.assert_called_once_with(
        {"index_code": "000300", "trade_date": "2024-01-02"}, {"$set": data}, upsert=True)


def test_upsert_stock_daily_kline_uses_dedup_key():
    c, _ = _client()
    data = {"symbol": "000001", "trade_date": "2024-01-02", "close": 10.5}
    assert c.upsert_stock_daily_kline(data) is True
    c.stock_daily_kline.update_one.assert_called_once_with(
        {"symbol": "000001", "trade_date": "2024-01-02"}, {"$set": data}, upsert=True)


def test_bulk_upsert_stock_daily_kline_writes_in_one_batch():
    c, _ = _client()
    records = [
        {"symbol": "000001", "trade_date": "2024-01-02", "close": 10.5},
        {"symbol": "000001", "trade_date": "2024-01-03", "close": 10.8},
    ]
    assert c.bulk_upsert_stock_daily_kline(records) == 2
    ops = c.stock_daily_kline.bulk_write.call_args.args[0]
    assert len(ops) == 2
    assert c.stock_daily_kline.bulk_write.call_args.kwargs == {"ordered": False}


def test_bulk_upsert_stock_daily_kline_returns_zero_on_error():
    c, _ = _client()
    c.stock_daily_kline.bulk_write.side_effect = RuntimeError("mongo down")
    assert c.bulk_upsert_stock_daily_kline([
        {"symbol": "000001", "trade_date": "2024-01-02", "close": 10.5},
    ]) == 0


def test_upsert_etf_daily_quote_skips_existing_row():
    """快照不能覆盖回填好的日线：当日已有行时直接返回，不 update 也不 insert。"""
    c, _ = _client()
    data = {
        "symbol": "515180",
        "trade_date": "2026-10-30",
        "close": 1.383,
        "high": 0.0,
        "updated_at": datetime(2026, 10, 30, 9, 35),
    }
    c.etf_daily_quotes.find_one.return_value = {"_id": "existing"}

    assert c.upsert_etf_daily_quote(data) is True

    c.etf_daily_quotes.find_one.assert_called_once_with(
        {"symbol": "515180", "trade_date": "2026-10-30"}, {"_id": 1})
    c.etf_daily_quotes.insert_one.assert_not_called()
    c.etf_daily_quotes.update_one.assert_not_called()


def test_upsert_etf_daily_quote_inserts_when_absent():
    c, _ = _client()
    data = {
        "symbol": "515180",
        "trade_date": "2026-10-30",
        "close": 1.383,
        "updated_at": datetime(2026, 10, 30, 9, 35),
    }
    c.etf_daily_quotes.find_one.return_value = None

    assert c.upsert_etf_daily_quote(data) is True

    c.etf_daily_quotes.insert_one.assert_called_once_with(data)


def test_upsert_etf_daily_quote_falls_back_to_updated_at_date():
    c, _ = _client()
    data = {"symbol": "515180", "close": 1.383, "updated_at": datetime(2026, 10, 30, 9, 35)}
    c.etf_daily_quotes.find_one.return_value = {"_id": "existing"}

    c.upsert_etf_daily_quote(data)

    assert c.etf_daily_quotes.find_one.call_args.args[0]["trade_date"] == "2026-10-30"
