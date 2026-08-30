import logging
import time
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


def fetch_with_fallback(sources: List[DataSource], method: str, code: str,
                        retries: int = 2, retry_delay: int = 1, **kwargs) -> Optional[Any]:
    """按 sources 顺序尝试（每源失败重试 retries 次），非空即返回；全部失败返回 None。"""
    for source in sources:
        fn = getattr(source, method, None)
        if fn is None:
            continue
        for attempt in range(retries + 1):
            try:
                result = fn(code, **kwargs)
                if result:
                    return result
                break  # 非异常但为空，切换下一源
            except Exception as e:  # noqa: BLE001 单源失败重试后切换备用源
                logger.warning("数据源 %s 的 %s(%s) 第 %s 次失败: %s",
                               getattr(source, "name", source), method, code, attempt + 1, e)
                if attempt < retries and retry_delay > 0:
                    time.sleep(retry_delay)
                continue
    logger.error("所有数据源对 %s(%s) 均失败，保留旧数据", method, code)
    return None
