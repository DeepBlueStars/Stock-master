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
        self.mongo_url = os.getenv("MONGO_URL", "mongodb://admin:tradingagents123@192.168.31.88:27017/tradingagents?authSource=admin")
        self.db_name = os.getenv("MONGO_DB_NAME", "tradingagents")
        
        # 初始化客户端和数据库
        self.client = MongoClient(self.mongo_url)
        self.db = self.client[self.db_name]
        
        # 集合引用
        self.etf_basic_info: Collection = self.db["etf_basic_info"]
        self.etf_daily_quotes: Collection = self.db["etf_daily_quotes"]
    
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
    
    def close(self):
        """关闭数据库连接"""
        self.client.close()

# 创建全局MongoDB客户端实例
mongo_client = MongoDBClient()