from app.services.health_service import health_snapshot


class _HealthyMongo:
    db_name = "tradingagents"

    def ping(self):
        return True

    def get_latest_stock_trade_date(self):
        return "2024-01-05"


class _DownMongo:
    db_name = "tradingagents"

    def ping(self):
        return False

    def get_latest_stock_trade_date(self):
        raise RuntimeError("connection refused")


def test_health_snapshot_ok_when_mongo_reachable():
    snapshot = health_snapshot(mongo=_HealthyMongo())
    assert snapshot["status"] == "ok"
    assert snapshot["mongo"] == {"ok": True, "db": "tradingagents"}
    assert snapshot["latest_stock_trade_date"] == "2024-01-05"


def test_health_snapshot_degraded_when_mongo_unreachable():
    snapshot = health_snapshot(mongo=_DownMongo())
    assert snapshot["status"] == "degraded"
    assert snapshot["mongo"]["ok"] is False
    assert snapshot["latest_stock_trade_date"] is None
