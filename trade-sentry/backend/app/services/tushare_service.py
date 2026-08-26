import tushare as ts
import pandas as pd
from datetime import datetime, timedelta
import redis
from app.config import settings


class TushareService:
    """Tushare服务"""
    
    def __init__(self):
        # 使用用户提供的token
        self.token = 'c1151554d70cf6c7a448fbf2b4278968fd3496f8c8e8d8cd2b6e4319'
        self.pro = ts.pro_api(self.token)
        
        # 初始化Redis连接
        self.redis_client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            password=settings.redis_password,
            decode_responses=True
        )
    
    def get_limit_up_stocks_from_redis(self):
        """从Redis中获取涨停股票代码"""
        today = datetime.now().strftime("%Y%m%d")
        redis_key = f"limit_up_stocks:{today}"
        
        try:
            # 从Redis集合中获取所有涨停股票代码
            stock_codes = self.redis_client.smembers(redis_key)
            return list(stock_codes)
        except Exception as e:
            print(f"从Redis获取涨停股票代码失败: {str(e)}")
            return []
    
    def get_stock_history(self, stock_code, start_date=None, end_date=None):
        """获取股票历史日线行情"""
        if not stock_code:
            return None
        
        # 如果没有指定日期范围，默认获取最近30天的数据
        if not end_date:
            end_date = datetime.now().strftime("%Y%m%d")
        
        if not start_date:
            start_date = (datetime.now() - timedelta(days=30)).strftime("%Y%m%d")
        
        try:
            # 调用Tushare的daily接口获取历史行情
            df = self.pro.daily(
                ts_code=stock_code,
                start_date=start_date,
                end_date=end_date
            )
            
            if df.empty:
                return None
            
            # 按日期排序（从早到晚）
            df = df.sort_values(by='trade_date')
            
            return df
        except Exception as e:
            print(f"获取股票{stock_code}历史行情失败: {str(e)}")
            return None
    
    def get_limit_up_stocks_history(self, start_date=None, end_date=None):
        """获取所有涨停股票的历史行情"""
        # 从Redis中获取涨停股票代码
        stock_codes = self.get_limit_up_stocks_from_redis()
        
        if not stock_codes:
            print("未从Redis中获取到涨停股票代码")
            return {}
        
        history_data = {}
        
        for stock_code in stock_codes:
            # 确保股票代码格式正确（Tushare需要带市场后缀）
            if stock_code:
                # 为股票代码添加市场后缀
                if stock_code.startswith('6'):
                    ts_code = f"{stock_code}.SH"
                else:
                    ts_code = f"{stock_code}.SZ"
                
                # 获取历史行情
                df = self.get_stock_history(ts_code, start_date, end_date)
                if df is not None:
                    history_data[stock_code] = df
        
        print(f"成功获取 {len(history_data)} 只涨停股票的历史行情")
        return history_data
    
    def analyze_limit_up_stocks(self):
        """分析涨停股票的历史表现"""
        # 获取涨停股票的历史行情
        history_data = self.get_limit_up_stocks_history()
        
        if not history_data:
            return {}
        
        analysis_result = {}
        
        for stock_code, df in history_data.items():
            if df.empty:
                continue
            
            # 计算基本指标
            latest_data = df.iloc[-1]
            
            # 计算最近5天的涨跌幅
            if len(df) >= 5:
                five_day_change = (df.iloc[-1]['close'] - df.iloc[-5]['close']) / df.iloc[-5]['close'] * 100
            else:
                five_day_change = None
            
            # 计算最近10天的涨跌幅
            if len(df) >= 10:
                ten_day_change = (df.iloc[-1]['close'] - df.iloc[-10]['close']) / df.iloc[-10]['close'] * 100
            else:
                ten_day_change = None
            
            # 计算波动率（最近30天的收盘价标准差）
            if len(df) >= 30:
                volatility = df['close'].std() / df['close'].mean() * 100
            else:
                volatility = df['close'].std() / df['close'].mean() * 100 if len(df) > 1 else 0
            
            # 构建分析结果
            analysis_result[stock_code] = {
                "latest_price": float(latest_data['close']),
                "latest_date": latest_data['trade_date'],
                "latest_change": float(latest_data['pct_chg']),
                "five_day_change": five_day_change,
                "ten_day_change": ten_day_change,
                "volatility": volatility,
                "total_trading_days": len(df)
            }
        
        print(f"成功分析 {len(analysis_result)} 只涨停股票的历史表现")
        return analysis_result


# 全局Tushare服务实例
tushare_service = TushareService()


def get_tushare_service():
    """获取Tushare服务实例"""
    return tushare_service
