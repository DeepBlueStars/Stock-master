import logging
from typing import Any, List, Optional

logger = logging.getLogger(__name__)


class DataSource:
    """数据源抽象接口：子类按能力覆写方法，不支持的返回 None。"""

    name: str = "base"

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[list]:
        return None

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[list]:
        return None

    def fetch_fundamentals(self, code: str) -> Optional[list]:
        return None

    def fetch_industry_concept(self, code: str) -> Optional[dict]:
        return None


def fetch_with_fallback(sources: List[DataSource], method: str, code: str, **kwargs) -> Optional[Any]:
    """按 sources 顺序尝试，非空结果即返回；全部失败返回 None 并记 error 日志。"""
    for source in sources:
        fn = getattr(source, method, None)
        if fn is None:
            continue
        try:
            result = fn(code, **kwargs)
            if result:
                return result
        except Exception as e:  # noqa: BLE001 单源失败切换备用源
            logger.warning("数据源 %s 的 %s(%s) 失败: %s", getattr(source, "name", source), method, code, e)
            continue
    logger.error("所有数据源对 %s(%s) 均失败，保留旧数据", method, code)
    return None
