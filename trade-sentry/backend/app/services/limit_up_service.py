import requests
import json
import time
from datetime import datetime
from sqlalchemy.orm import Session
import redis
from app.models import LimitUpStock
from app.config import settings
from decimal import Decimal


class LimitUpCrawler:
    def __init__(self):
        self.api_url = "https://push2.eastmoney.com/api/qt/clist/get"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }
        self.field_mapping = {
            "f2": "current_price",
            "f3": "change_rate",
            "f4": "change_amount",
            "f5": "volume",
            "f6": "amount",
            "f7": "amplitude",
            "f8": "turnover_rate",
            "f9": "pe_ttm",
            "f10": "volume_ratio",
            "f12": "stock_code",
            "f13": "market_type",
            "f14": "stock_name",
            "f15": "high_price",
            "f16": "low_price",
            "f17": "open_price",
            "f18": "pre_close",
            "f20": "total_market_cap",
            "f21": "circulating_market_cap",
            "f23": "pb",
            "f24": "limit_up_price",
            "f25": "limit_down_price",
            "f62": "main_inflow",
            "f115": "pe_static"
        }
        
        # 初始化Redis连接
        self.redis_client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            password=settings.redis_password,
            decode_responses=True
        )
    
    def get_limit_up_stocks(self, page_size=50):
        """获取涨停股票数据"""
        all_stocks = []
        page = 1
        total_count = 0
        
        while True:
            params = {
                "pn": str(page),
                "pz": str(page_size),
                "po": "1",
                "np": "1",
                "ut": "b2884a393a59ad64002292a3e90d46a5",
                "fltt": "2",
                "invt": "2",
                "fid": "f3",
                "fs": "b:BK0815",
                "fields": "f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f62,f115",
                "_": str(int(time.time() * 1000))
            }
            
            try:
                response = requests.get(self.api_url, headers=self.headers, params=params, timeout=10)
                response.raise_for_status()
                data = response.json()
                
                if data.get("rc") != 0:
                    print(f"API请求失败: {data.get('msg', '未知错误')}")
                    break
                
                if "data" not in data or "diff" not in data["data"]:
                    print("数据格式错误")
                    break
                
                stocks = data["data"]["diff"]
                if not stocks:
                    break
                
                all_stocks.extend(stocks)
                
                if total_count == 0:
                    total_count = data["data"].get("total", 0)
                
                if len(all_stocks) >= total_count:
                    break
                
                page += 1
                time.sleep(1)  # 防止请求过快被封
                
            except Exception as e:
                print(f"请求失败: {str(e)}")
                break
        
        return all_stocks
    
    def save_to_database(self, stocks, db: Session):
        """保存涨停股票数据到数据库"""
        if not stocks:
            return 0
        
        saved_count = 0
        today = datetime.now().date()
        
        for stock in stocks:
            try:
                # 格式化数据
                stock_data = {
                    "stock_code": stock.get("f12", ""),
                    "stock_name": stock.get("f14", ""),
                    "current_price": Decimal(str(stock.get("f2", 0))),
                    "change_rate": Decimal(str(stock.get("f3", 0))),
                    "change_amount": Decimal(str(stock.get("f4", 0))),
                    "open_price": Decimal(str(stock.get("f17", 0))),
                    "high_price": Decimal(str(stock.get("f15", 0))),
                    "low_price": Decimal(str(stock.get("f16", 0))),
                    "pre_close": Decimal(str(stock.get("f18", 0))),
                    "volume": int(stock.get("f5", 0)),
                    "amount": Decimal(str(stock.get("f6", 0))),
                    "amplitude": Decimal(str(stock.get("f7", 0))),
                    "turnover_rate": Decimal(str(stock.get("f8", 0))),
                    "pe_ttm": Decimal(str(stock.get("f9", 0))) if stock.get("f9") else None,
                    "pb": Decimal(str(stock.get("f23", 0))) if stock.get("f23") else None,
                    "total_market_cap": Decimal(str(stock.get("f20", 0))) if stock.get("f20") else None,
                    "circulating_market_cap": Decimal(str(stock.get("f21", 0))) if stock.get("f21") else None,
                    "limit_up_price": Decimal(str(stock.get("f24", 0))),
                    "limit_down_price": Decimal(str(stock.get("f25", 0))),
                    "main_inflow": Decimal(str(stock.get("f62", 0))) if stock.get("f62") else None,
                    "market_type": "深市" if stock.get("f13") == 0 else "沪市",
                    "capture_date": datetime.now()
                }
                
                # 检查是否已存在
                existing_stock = db.query(LimitUpStock).filter(
                    LimitUpStock.stock_code == stock_data["stock_code"],
                    LimitUpStock.capture_date >= datetime(today.year, today.month, today.day)
                ).first()
                
                if existing_stock:
                    # 更新现有记录
                    for key, value in stock_data.items():
                        setattr(existing_stock, key, value)
                else:
                    # 创建新记录
                    new_stock = LimitUpStock(**stock_data)
                    db.add(new_stock)
                
                saved_count += 1
                
            except Exception as e:
                print(f"保存股票数据失败: {str(e)}")
                continue
        
        try:
            db.commit()
            print(f"成功保存 {saved_count} 条涨停股票数据到数据库")
        except Exception as e:
            db.rollback()
            print(f"数据库提交失败: {str(e)}")
            saved_count = 0
        
        return saved_count
    
    def save_to_redis(self, stocks):
        """保存涨停股票代码到Redis"""
        if not stocks:
            return 0
        
        saved_count = 0
        today = datetime.now().strftime("%Y%m%d")
        redis_key = f"limit_up_stocks:{today}"
        
        # 清空当天的涨停股票列表
        self.redis_client.delete(redis_key)
        
        for stock in stocks:
            try:
                stock_code = stock.get("f12", "")
                if stock_code:
                    # 添加到Redis集合
                    self.redis_client.sadd(redis_key, stock_code)
                    saved_count += 1
            except Exception as e:
                print(f"保存到Redis失败: {str(e)}")
                continue
        
        # 设置过期时间为24小时
        self.redis_client.expire(redis_key, 86400)
        print(f"成功保存 {saved_count} 条涨停股票代码到Redis")
        
        return saved_count
    
    def run(self, db: Session):
        """运行爬虫并保存数据"""
        print("开始获取涨停股票数据...")
        start_time = time.time()
        
        # 获取涨停股票数据
        stocks = self.get_limit_up_stocks()
        
        if not stocks:
            print("未获取到涨停股票数据")
            return 0
        
        # 保存到数据库
        db_count = self.save_to_database(stocks, db)
        
        # 保存到Redis
        redis_count = self.save_to_redis(stocks)
        
        end_time = time.time()
        print(f"涨停股票爬虫运行完成，耗时 {end_time - start_time:.2f} 秒")
        print(f"共获取 {len(stocks)} 只涨停股票，保存到数据库 {db_count} 只，保存到Redis {redis_count} 只")
        
        return len(stocks)


# 全局爬虫实例
limit_up_crawler = LimitUpCrawler()


def get_limit_up_service():
    """获取涨停股票服务实例"""
    return limit_up_crawler
