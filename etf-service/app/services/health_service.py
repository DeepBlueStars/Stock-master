from ..database.mongo import mongo_client


def health_snapshot(mongo=None) -> dict:
    """汇总数据库连通性与数据新鲜度，供 /health 与运维排查使用。"""
    mongo = mongo or mongo_client
    mongo_ok = bool(mongo.ping())

    latest_trade_date = None
    if mongo_ok:
        try:
            latest_trade_date = mongo.get_latest_stock_trade_date()
        except Exception:  # noqa: BLE001 数据库异常已由 ping 暴露，健康检查不再抛错
            latest_trade_date = None

    return {
        "status": "ok" if mongo_ok else "degraded",
        "mongo": {"ok": mongo_ok, "db": getattr(mongo, "db_name", None)},
        "latest_stock_trade_date": latest_trade_date,
    }
