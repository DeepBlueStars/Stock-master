"""
定时任务
定期获取涨停股票数据
"""
import asyncio
from datetime import datetime
from app.database import SessionLocal
from app.services.limit_up_service import limit_up_crawler


class LimitUpDataUpdater:
    """涨停股票数据更新器"""
    
    def __init__(self):
        self.is_running = False
        self.update_interval = 300  # 每5分钟更新一次
    
    async def start(self):
        """启动定时更新"""
        if self.is_running:
            return
        
        self.is_running = True
        print(f"[{datetime.now()}] 启动涨停股票数据定时更新")
        
        while self.is_running:
            try:
                await self._update_limit_up_data()
            except Exception as e:
                print(f"[{datetime.now()}] 更新涨停股票数据失败: {e}")
            
            await asyncio.sleep(self.update_interval)
    
    def stop(self):
        """停止定时更新"""
        self.is_running = False
        print(f"[{datetime.now()}] 停止涨停股票数据定时更新")
    
    async def _update_limit_up_data(self):
        """更新涨停股票数据"""
        # 创建数据库会话
        db = SessionLocal()
        
        try:
            # 运行爬虫并保存数据
            count = limit_up_crawler.run(db)
            
            if count > 0:
                print(f"[{datetime.now()}] 涨停股票数据更新完成，获取到 {count} 只涨停股票")
            else:
                print(f"[{datetime.now()}] 涨停股票数据更新完成，未获取到涨停股票")
        finally:
            # 关闭数据库会话
            db.close()


# 全局更新器实例
updater = LimitUpDataUpdater()


async def start_limit_up_data_update():
    """启动涨停股票数据更新"""
    await updater.start()


def stop_limit_up_data_update():
    """停止涨停股票数据更新"""
    updater.stop()
