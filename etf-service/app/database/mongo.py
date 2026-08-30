from pymongo import MongoClient
from pymongo.collection import Collection
from typing import Dict, Any
import os
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

class MongoDBClient:
    def __init__(self):
        # 数据库连接配置
        # self.mongo_url = os.getenv("MONGO_URL", "mongodb://admin:tradingagents123@192.168.31.88:27017/tradingagents?authSource=admin")
        self.mongo_url = os.getenv("MONGO_URL", "mongodb://localhost:27017/tradingagents")
        self.db_name = os.getenv("MONGO_DB_NAME", "tradingagents")
        
        # 初始化客户端和数据库
        self.client = MongoClient(self.mongo_url)
        self.db = self.client[self.db_name]
        
        # 集合引用
        self.etf_basic_info: Collection = self.db["etf_basic_info"]
        self.etf_daily_quotes: Collection = self.db["etf_daily_quotes"]
        # 数据地基新增集合（禁止改造已有集合）
        self.index_daily_quotes: Collection = self.db["index_daily_quotes"]
        self.stock_daily_kline: Collection = self.db["stock_daily_kline"]
        self.stock_fundamentals: Collection = self.db["stock_fundamentals"]
        self.stock_industry_concept: Collection = self.db["stock_industry_concept"]
        self.index_constituents: Collection = self.db["index_constituents"]
    
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

    def get_constituent_symbols(self) -> list:
        """获取优质池全部成分股代码（去重）"""
        return list(self.index_constituents.distinct("symbol"))

    def close(self):
        """关闭数据库连接"""
        self.client.close()

# 创建全局MongoDB客户端实例
mongo_client = MongoDBClient()