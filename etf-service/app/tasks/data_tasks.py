import logging

from ..services.index_service import index_service

logger = logging.getLogger(__name__)


def update_index_constituents():
    """每月更新沪深300/中证500成分股（upsert 去重，重复运行不重复写入）。"""
    logger.info("开始更新指数成分股")
    try:
        n = index_service.fetch_and_store_constituents()
        logger.info("指数成分股更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新指数成分股失败: %s", e, exc_info=True)
