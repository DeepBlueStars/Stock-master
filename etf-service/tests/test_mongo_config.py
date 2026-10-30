from unittest.mock import MagicMock, patch

from pymongo.errors import OperationFailure

from app.database import mongo as mongo_module
from app.database.mongo import MongoDBClient, resolve_mongo_url


def _client():
    with patch.object(mongo_module, "MongoClient") as mc:
        fake_db = MagicMock()
        mc.return_value.__getitem__.return_value = fake_db
        return MongoDBClient(), mc


def test_env_mongo_url_wins(monkeypatch):
    monkeypatch.setenv("MONGO_URL", "mongodb://db.example:27017/tradingagents")
    assert resolve_mongo_url(in_container=True) == "mongodb://db.example:27017/tradingagents"


def test_container_default_uses_host_docker_internal(monkeypatch):
    monkeypatch.delenv("MONGO_URL", raising=False)
    assert resolve_mongo_url(in_container=True) == "mongodb://host.docker.internal:27017/tradingagents"


def test_local_default_uses_localhost(monkeypatch):
    monkeypatch.delenv("MONGO_URL", raising=False)
    assert resolve_mongo_url(in_container=False) == "mongodb://localhost:27017/tradingagents"


def test_client_uses_resolved_url_and_short_timeouts(monkeypatch):
    monkeypatch.setenv("MONGO_URL", "mongodb://db.example:27017/tradingagents")
    _, mc = _client()
    assert mc.call_args.args[0] == "mongodb://db.example:27017/tradingagents"
    assert mc.call_args.kwargs["serverSelectionTimeoutMS"] == 5000
    assert mc.call_args.kwargs["connectTimeoutMS"] == 5000


def test_ping_returns_false_when_database_unreachable():
    with patch.object(mongo_module, "MongoClient") as mc:
        mc.return_value.admin.command.side_effect = RuntimeError("connection refused")
        mc.return_value.__getitem__.return_value = MagicMock()
        assert MongoDBClient().ping() is False


def test_ping_returns_true_when_database_reachable():
    c, _ = _client()
    assert c.ping() is True


def test_ping_returns_false_when_auth_required():
    c, _ = _client()
    c.db.command.side_effect = OperationFailure("command dbStats requires authentication", 13)
    assert c.ping() is False


def test_get_latest_trade_date_uses_symbol_and_descending_sort():
    c, _ = _client()
    c.stock_daily_kline.find_one.return_value = {"trade_date": "2024-01-05"}
    assert c.get_latest_trade_date("600000") == "2024-01-05"
    _, kwargs = c.stock_daily_kline.find_one.call_args
    assert kwargs["sort"] == [("trade_date", -1)]


def test_get_latest_trade_date_returns_none_when_empty():
    c, _ = _client()
    c.stock_daily_kline.find_one.return_value = None
    assert c.get_latest_trade_date("600000") is None


def test_get_latest_stock_trade_date_returns_none_when_empty():
    c, _ = _client()
    c.stock_daily_kline.find_one.return_value = None
    assert c.get_latest_stock_trade_date() is None
