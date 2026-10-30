import logging

from ..database.mongo import mongo_client
from ..services.index_service import index_service
from ..services.stock_service import stock_service

logger = logging.getLogger(__name__)


def _ensure_mongo():
    """数据库不可达或认证失败时快速跳过本轮，避免逐条写入重复报错。"""
    if not mongo_client.ping():
        raise RuntimeError("MongoDB 不可达或认证失败，跳过本轮任务")


def update_index_constituents():
    """每月更新沪深300/中证500成分股（upsert 去重，重复运行不重复写入）。"""
    logger.info("开始更新指数成分股")
    try:
        _ensure_mongo()
        n = index_service.fetch_and_store_constituents()
        logger.info("指数成分股更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新指数成分股失败: %s", e, exc_info=True)


def update_stock_fundamentals():
    """财务指标按财报周期更新（检测到新财报则拉取，upsert 去重）。"""
    logger.info("开始更新个股财务指标")
    try:
        _ensure_mongo()
        n = stock_service.fetch_and_store_fundamentals()
        logger.info("个股财务指标更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新个股财务指标失败: %s", e, exc_info=True)


def update_stock_industry_concept():
    """个股行业与概念分类每日更新。"""
    logger.info("开始更新个股行业与概念分类")
    try:
        _ensure_mongo()
        n = stock_service.fetch_and_store_industry_concept()
        logger.info("个股行业与概念分类更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新个股行业与概念分类失败: %s", e, exc_info=True)
