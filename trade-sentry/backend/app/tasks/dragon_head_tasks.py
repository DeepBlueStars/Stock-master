"""
龙头战法定时任务
定期执行龙头战法分析并发送通知
"""
import asyncio
import logging
from datetime import datetime, time
from typing import Optional

from app.services.market_service import market_service
from app.services.feishu_service import feishu_service
from app.services.notification_cache import notification_cache

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class DragonHeadTasks:
    """龙头战法定时任务"""
    
    def __init__(self):
        self.is_running = False
        self.last_analysis_time = None
        logger.info("[DragonHeadTasks] 初始化完成")
    
    def is_trading_hours(self) -> bool:
        """判断是否为交易时段
        
        Returns:
            bool: 是否为交易时段
        """
        current_time = datetime.now().time()
        current_date = datetime.now().date()
        
        # 检查是否为交易日（简化版，实际需要更复杂的判断）
        # 这里只判断是否为周末
        if current_date.weekday() >= 5:
            return False
        
        # 上午交易时间: 09:30-11:30
        # 下午交易时间: 13:00-15:00
        morning_start = time(9, 30)
        morning_end = time(11, 30)
        afternoon_start = time(13, 0)
        afternoon_end = time(15, 0)
        
        return (
            (morning_start <= current_time <= morning_end) or
            (afternoon_start <= current_time <= afternoon_end)
        )
    
    async def analyze_dragon_head(self):
        """执行龙头战法分析并发送通知"""
        try:
            logger.info("[DragonHeadTasks] 开始执行龙头战法分析...")
            
            # 执行龙头战法分析
            analysis_result = await market_service.analyze_dragon_head()
            
            # 发送市场情绪通知（每天只发送一次）
            await self._send_market_sentiment_notification(analysis_result.get('market_sentiment'))
            
            # 处理买入机会
            for opportunity in analysis_result.get('buy_opportunities', []):
                await self._process_buy_opportunity(opportunity)
            
            # 处理卖出信号
            for signal in analysis_result.get('sell_signals', []):
                await self._process_sell_signal(signal)
            
            # 处理龙头股
            for dragon_head in analysis_result.get('dragon_heads', []):
                await self._process_dragon_head(dragon_head)
            
            self.last_analysis_time = datetime.now()
            logger.info(f"[DragonHeadTasks] 龙头战法分析完成，耗时: {datetime.now() - self.last_analysis_time}")
            
        except Exception as e:
            logger.error(f"[DragonHeadTasks] 执行分析时发生异常: {e}")
    
    async def _send_market_sentiment_notification(self, sentiment: dict):
        """发送市场情绪通知
        
        Args:
            sentiment: 市场情绪信息
        """
        # 每天只发送一次市场情绪通知
        if not notification_cache.has_notified_today('market', 'sentiment'):
            success = feishu_service.send_market_sentiment_notification(sentiment)
            if success:
                notification_cache.mark_as_notified('market', 'sentiment', sentiment)
                logger.info("[DragonHeadTasks] 市场情绪通知发送成功")
            else:
                logger.error("[DragonHeadTasks] 市场情绪通知发送失败")
    
    async def _process_buy_opportunity(self, opportunity: dict):
        """处理买入机会
        
        Args:
            opportunity: 买入机会信息
        """
        stock = opportunity.get('stock', {})
        signal = opportunity.get('signal', '')
        stock_code = stock.get('code', '')
        
        if not stock_code:
            return
        
        # 检查今天是否已经通知过
        if not notification_cache.has_notified_today(stock_code, 'buy'):
            success = feishu_service.send_buy_notification(stock, signal)
            if success:
                notification_cache.mark_as_notified(stock_code, 'buy', opportunity)
                logger.info(f"[DragonHeadTasks] 买入机会通知发送成功: {stock.get('name')} ({stock_code})")
            else:
                logger.error(f"[DragonHeadTasks] 买入机会通知发送失败: {stock.get('name')} ({stock_code})")
    
    async def _process_sell_signal(self, signal: dict):
        """处理卖出信号
        
        Args:
            signal: 卖出信号信息
        """
        stock = signal.get('stock', {})
        signal_type = signal.get('signal', '')
        stock_code = stock.get('code', '')
        
        if not stock_code:
            return
        
        # 检查今天是否已经通知过
        if not notification_cache.has_notified_today(stock_code, 'sell'):
            success = feishu_service.send_sell_notification(stock, signal_type)
            if success:
                notification_cache.mark_as_notified(stock_code, 'sell', signal)
                logger.info(f"[DragonHeadTasks] 卖出提醒通知发送成功: {stock.get('name')} ({stock_code})")
            else:
                logger.error(f"[DragonHeadTasks] 卖出提醒通知发送失败: {stock.get('name')} ({stock_code})")
    
    async def _process_dragon_head(self, dragon_head: dict):
        """处理龙头股
        
        Args:
            dragon_head: 龙头股信息
        """
        stock_code = dragon_head.get('code', '')
        
        if not stock_code:
            return
        
        # 检查今天是否已经通知过
        if not notification_cache.has_notified_today(stock_code, 'dragon_head'):
            # 构建龙头股通知内容
            text = f"🐉 **龙头股发现**\n"
            text += f"📈 **股票**: {dragon_head.get('name')} ({dragon_head.get('code')})\n"
            text += f"💰 **当前价**: {dragon_head.get('current_price', 0):.2f}元\n"
            text += f"📊 **涨跌幅**: +{dragon_head.get('change_rate', 0):.2f}%\n"
            text += f"🏷️ **板类型**: {self._get_board_type_name(dragon_head.get('board_type', 'first'))}\n"
            text += f"⭐ **AI评分**: {dragon_head.get('ai_score', 0)}\n"
            text += f"🏭 **所属板块**: {dragon_head.get('sector', '未知')}\n"
            text += f"💪 **板块强度**: {dragon_head.get('sector_strength', 0):.1f}/10\n"
            text += f"⏰ **涨停时间**: {dragon_head.get('limit_up_time', '未知')}\n"
            text += f"💥 **封单金额**: {dragon_head.get('seal_amount', 0):,.0f}元\n"
            text += f"\n📋 **操作建议**:\n"
            text += "- 密切关注龙头股走势\n"
            text += "- 等待合适的买入时机\n"
            text += "- 严格控制仓位\n"
            text += f"\n⚠️ **风险提示**: 龙头股波动较大，请谨慎操作！\n"
            text += f"🕒 **通知时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
            
            success = feishu_service.send_message('text', {'text': text})
            if success:
                notification_cache.mark_as_notified(stock_code, 'dragon_head', dragon_head)
                logger.info(f"[DragonHeadTasks] 龙头股通知发送成功: {dragon_head.get('name')} ({stock_code})")
            else:
                logger.error(f"[DragonHeadTasks] 龙头股通知发送失败: {dragon_head.get('name')} ({stock_code})")
    
    def _get_board_type_name(self, board_type: str) -> str:
        """获取板类型名称
        
        Args:
            board_type: 板类型代码
            
        Returns:
            str: 板类型名称
        """
        board_map = {
            'first': '首板',
            'second': '二板',
            'third': '三板',
            'high': '高位板'
        }
        return board_map.get(board_type, '未知')
    
    async def start_scheduler(self):
        """启动调度器"""
        self.is_running = True
        logger.info("[DragonHeadTasks] 启动龙头战法分析调度器")
        
        while self.is_running:
            try:
                # 执行分析
                await self.analyze_dragon_head()
                
                # 根据是否为交易时段调整等待时间
                if self.is_trading_hours():
                    # 交易时段每5分钟分析一次
                    wait_time = 300  # 5分钟
                    logger.info(f"[DragonHeadTasks] 交易时段，等待 {wait_time} 秒后再次分析")
                else:
                    # 非交易时段每小时分析一次
                    wait_time = 3600  # 1小时
                    logger.info(f"[DragonHeadTasks] 非交易时段，等待 {wait_time} 秒后再次分析")
                
                # 等待指定时间
                await asyncio.sleep(wait_time)
                
            except Exception as e:
                logger.error(f"[DragonHeadTasks] 调度器运行时发生异常: {e}")
                # 发生异常后等待1分钟再继续
                await asyncio.sleep(60)
    
    def stop_scheduler(self):
        """停止调度器"""
        self.is_running = False
        logger.info("[DragonHeadTasks] 停止龙头战法分析调度器")


# 全局龙头战法任务实例
dragon_head_tasks = DragonHeadTasks()


# 用于单独运行的主函数
async def main():
    """主函数"""
    try:
        # 立即执行一次分析
        await dragon_head_tasks.analyze_dragon_head()
        
        # 启动调度器
        await dragon_head_tasks.start_scheduler()
    except KeyboardInterrupt:
        logger.info("[DragonHeadTasks] 收到中断信号，正在停止...")
        dragon_head_tasks.stop_scheduler()


if __name__ == "__main__":
    asyncio.run(main())
