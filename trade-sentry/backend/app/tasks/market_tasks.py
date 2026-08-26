"""
定时任务
定期更新行情数据
"""
import asyncio
from datetime import datetime
from app.services.market_service import market_service


class MarketDataUpdater:
    """行情数据更新器"""
    
    def __init__(self):
        self.is_running = False
        self.update_interval = 300  # 每5分钟更新一次
    
    async def start(self):
        """启动定时更新"""
        if self.is_running:
            return
        
        self.is_running = True
        print(f"[{datetime.now()}] 启动行情数据定时更新")
        
        while self.is_running:
            try:
                await self._update_all_data()
            except Exception as e:
                print(f"[{datetime.now()}] 更新行情数据失败: {e}")
            
            await asyncio.sleep(self.update_interval)
    
    def stop(self):
        """停止定时更新"""
        self.is_running = False
        print(f"[{datetime.now()}] 停止行情数据定时更新")
    
    async def _update_all_data(self):
        """更新所有行情数据"""
        # 更新涨停股票列表
        await market_service.get_limit_up_stocks()
        
        # 更新板块数据
        await market_service.get_sectors()
        
        # 更新情绪数据
        await market_service.get_sentiment()
        
        # 可选：更新热门股票详情
        # hot_stocks = ['000001', '000858', '600519']
        # for code in hot_stocks:
        #     await market_service.get_stock_detail(code)
        
        print(f"[{datetime.now()}] 行情数据更新完成")


# 全局更新器实例
updater = MarketDataUpdater()


async def start_market_data_update():
    """启动行情数据更新"""
    await updater.start()


def stop_market_data_update():
    """停止行情数据更新"""
    updater.stop()
