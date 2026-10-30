import logging
import os
import re
from typing import Any, Dict, Optional

from dotenv import load_dotenv
from pymongo import MongoClient, UpdateOne
from pymongo.collection import Collection

# 加载环境变量
load_dotenv()

logger = logging.getLogger(__name__)

DEFAULT_DB_NAME = "tradingagents"


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        logger.warning("环境变量 %s=%s 不是整数，使用默认值 %s", name, raw, default)
        return default


def resolve_mongo_url(in_container: Optional[bool] = None) -> str:
    """解析 MongoDB 连接串：MONGO_URL 优先；未设置时按运行环境选择默认地址。"""
    url = os.getenv("MONGO_URL", "").strip()
    if url:
        return url
    if in_container is None:
        in_container = os.path.exists("/.dockerenv")
    if in_container:
        return f"mongodb://host.docker.internal:27017/{DEFAULT_DB_NAME}"
    return f"mongodb://localhost:27017/{DEFAULT_DB_NAME}"


def mask_mongo_url(url: str) -> str:
    """隐藏连接串中的账号密码，避免日志泄露。"""
    return re.sub(r"//[^/@]+@", "//***@", url)


class MongoDBClient:
    def __init__(self):
        # 数据库连接配置
        self.mongo_url = resolve_mongo_url()
        self.db_name = os.getenv("MONGO_DB_NAME", DEFAULT_DB_NAME)

        # 初始化客户端和数据库（缩短选择超时，连不上时尽快暴露问题）
        self.client = MongoClient(
            self.mongo_url,
            serverSelectionTimeoutMS=_env_int("MONGO_SERVER_SELECTION_TIMEOUT_MS", 5000),
            connectTimeoutMS=_env_int("MONGO_CONNECT_TIMEOUT_MS", 5000),
        )
        self.db = self.client[self.db_name]
        logger.info("MongoDB 连接目标: %s (db=%s)", mask_mongo_url(self.mongo_url), self.db_name)

        
        # 集合引用
        self.etf_basic_info: Collection = self.db["etf_basic_info"]
        self.etf_daily_quotes: Collection = self.db["etf_daily_quotes"]
        # 数据地基新增集合（禁止改造已有集合）
        self.index_daily_quotes: Collection = self.db["index_daily_quotes"]
        self.stock_daily_kline: Collection = self.db["stock_daily_kline"]
        self.stock_fundamentals: Collection = self.db["stock_fundamentals"]
        self.stock_industry_concept: Collection = self.db["stock_industry_concept"]
        self.index_constituents: Collection = self.db["index_constituents"]
        self.index_valuation: Collection = self.db["index_valuation"]
    
    def get_etf_basic_info(self) -> list:
        """获取所有ETF基本信息"""
        return list(self.etf_basic_info.find())
    
    def get_etf_basic_info_by_symbol(self, symbol: str) -> dict:
        """根据代码获取ETF基本信息"""
        return self.etf_basic_info.find_one({"symbol": symbol})
    
    def upsert_etf_daily_quote(self, data: Dict[str, Any]) -> bool:
        """更新或插入ETF日行情数据"""
        try:
            # 提取updated_at的日期部分（YYYY-MM-DD）
            updated_date = data["updated_at"].strftime("%Y-%m-%d")
            
            filter_query = {
                "symbol": data["symbol"],
                "updated_at": {
                    "$gte": data["updated_at"].replace(hour=0, minute=0, second=0, microsecond=0),
                    "$lt": data["updated_at"].replace(hour=23, minute=59, second=59, microsecond=999999)
                }
            }
            
            update_query = {
                "$set": data
            }
            
            self.etf_daily_quotes.update_one(
                filter_query,
                update_query,
                upsert=True
            )
            return True
        except Exception as e:
            print(f"更新ETF日行情失败: {e}")
            return False
    
    def upsert_etf_basic_info(self, data: Dict[str, Any]) -> bool:
        """更新或插入ETF基本信息"""
        try:
            filter_query = {
                "symbol": data["symbol"]
            }
            
            update_query = {
                "$set": data
            }
            
            self.etf_basic_info.update_one(
                filter_query,
                update_query,
                upsert=True
            )
            return True
        except Exception as e:
            print(f"更新ETF基本信息失败: {e}")
            return False
    
    def upsert_index_daily_quote(self, data: Dict[str, Any]) -> bool:
        """更新或插入指数日行情（去重键 index_code + trade_date）"""
        try:
            self.index_daily_quotes.update_one(
                {"index_code": data["index_code"], "trade_date": data["trade_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新指数日行情失败: {e}")
            return False

    def upsert_stock_daily_kline(self, data: Dict[str, Any]) -> bool:
        """更新或插入个股日K线（去重键 symbol + trade_date）"""
        try:
            self.stock_daily_kline.update_one(
                {"symbol": data["symbol"], "trade_date": data["trade_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新个股日K线失败: {e}")
            return False

    def bulk_upsert_stock_daily_kline(self, records: list) -> int:
        """批量 upsert 个股日K线（单批次，去重键 symbol + trade_date）。"""
        if not records:
            return 0
        try:
            operations = [
                UpdateOne(
                    {"symbol": r["symbol"], "trade_date": r["trade_date"]},
                    {"$set": r},
                    upsert=True,
                )
                for r in records
            ]
            self.stock_daily_kline.bulk_write(operations, ordered=False)
            return len(records)
        except Exception as e:
            logger.error("批量更新个股日K线失败: %s", e)
            return 0

    def upsert_stock_fundamental(self, data: Dict[str, Any]) -> bool:
        """更新或插入个股财务指标（去重键 symbol + report_date）"""
        try:
            self.stock_fundamentals.update_one(
                {"symbol": data["symbol"], "report_date": data["report_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新个股财务指标失败: {e}")
            return False

    def upsert_stock_industry_concept(self, data: Dict[str, Any]) -> bool:
        """更新或插入个股行业概念（去重键 symbol）"""
        try:
            self.stock_industry_concept.update_one(
                {"symbol": data["symbol"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新个股行业概念失败: {e}")
            return False

    def upsert_index_constituent(self, data: Dict[str, Any]) -> bool:
        """更新或插入指数成分股（去重键 index_code + symbol）"""
        try:
            self.index_constituents.update_one(
                {"index_code": data["index_code"], "symbol": data["symbol"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新指数成分股失败: {e}")
            return False

    def upsert_index_valuation(self, data: Dict[str, Any]) -> bool:
        """更新或插入指数估值（去重键 index_code + trade_date）。"""
        try:
            self.index_valuation.update_one(
                {"index_code": data["index_code"], "trade_date": data["trade_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新指数估值失败: {e}")
            return False

    def get_constituent_symbols(self) -> list:
        """获取优质池全部成分股代码（去重）"""
        return list(self.index_constituents.distinct("symbol"))

    def get_latest_trade_date(self, symbol: str) -> Optional[str]:
        """返回某只个股在 stock_daily_kline 中的最新交易日（无数据返回 None）。"""
        doc = self.stock_daily_kline.find_one(
            {"symbol": symbol}, {"trade_date": 1}, sort=[("trade_date", -1)]
        )
        return doc.get("trade_date") if doc else None

    def get_latest_stock_trade_date(self) -> Optional[str]:
        """返回 stock_daily_kline 全集合的最新交易日（用于健康检查）。"""
        doc = self.stock_daily_kline.find_one(
            {}, {"trade_date": 1}, sort=[("trade_date", -1)]
        )
        return doc.get("trade_date") if doc else None

    def ping(self) -> bool:
        """检测数据库是否可达且认证通过，失败时打印排查提示并返回 False。"""
        try:
            self.client.admin.command("ping")
        except Exception as e:
            logger.error(
                "MongoDB 不可达: %s | 连接串: %s | 容器内 localhost 指向容器自身，"
                "请设置 MONGO_URL（如 mongodb://<mongo-host>:27017/%s）",
                e, mask_mongo_url(self.mongo_url), self.db_name,
            )
            return False
        try:
            # ping 不要求认证；再执行一次需要权限的命令，才能发现账号密码/authSource 缺失
            self.db.command("dbStats")
        except Exception as e:
            logger.error(
                "MongoDB 已连通但认证/权限不足: %s | 连接串: %s | "
                "请检查 MONGO_URL 中的账号密码与 authSource（如 ?authSource=admin）",
                e, mask_mongo_url(self.mongo_url),
            )
            return False
        return True

    def close(self):
        """关闭数据库连接"""
        self.client.close()

# 创建全局MongoDB客户端实例
mongo_client = MongoDBClient()