from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.interval import IntervalTrigger
from apscheduler.triggers.cron import CronTrigger
from datetime import datetime
import logging
from ..services.etf_service import etf_service
from ..database.mongo import mongo_client
from .data_tasks import (
    update_index_constituents,
    update_stock_fundamentals,
    update_stock_industry_concept,
)
from ..services.index_service import index_service
from ..services.stock_service import stock_service

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 创建调度器实例
scheduler = BackgroundScheduler()

def update_etf_daily_quotes():
    """更新ETF日行情数据"""
    logger.info("开始更新ETF日行情数据")
    
    try:
        # 获取所有ETF实时数据
        all_etf_data = etf_service.get_all_etf_data_for_db()
        if not all_etf_data:
            logger.warning("获取ETF实时数据失败，数据为空")
            return
        
        # 直接更新或插入所有ETF日行情数据
        success_count = 0
        for etf_data in all_etf_data:
            if mongo_client.upsert_etf_daily_quote(etf_data):
                success_count += 1
        
        logger.info(f"ETF日行情数据更新完成: 成功更新 {success_count} 条数据，总数据 {len(all_etf_data)} 条")
        
    except Exception as e:
        logger.error(f"更新ETF日行情数据失败: {e}", exc_info=True)

def update_etf_basic_info():
    """更新ETF基本信息"""
    logger.info("开始更新ETF基本信息")
    
    try:
        # 获取所有ETF基本信息
        all_etf_basic_info = etf_service.get_all_etf_basic_info_for_db()
        if not all_etf_basic_info:
            logger.warning("获取ETF基本信息失败，数据为空")
            return
        
        # 更新或插入ETF基本信息
        success_count = 0
        for etf_basic in all_etf_basic_info:
            if mongo_client.upsert_etf_basic_info(etf_basic):
                success_count += 1
        
        logger.info(f"ETF基本信息更新完成: 成功更新 {success_count} 条数据，总数据 {len(all_etf_basic_info)} 条")
        
    except Exception as e:
        logger.error(f"更新ETF基本信息失败: {e}", exc_info=True)

def start_scheduler():
    """启动定时任务"""
    try:
        # 检查调度器是否已经在运行
        if not scheduler.running:
            # 添加定时任务，每30分钟执行一次
            scheduler.add_job(
                func=update_etf_daily_quotes,
                trigger=IntervalTrigger(minutes=30),
                id="update_etf_daily_quotes",
                name="每30分钟更新ETF日行情数据",
                replace_existing=True
            )
            
            # 添加每天凌晨0点执行的ETF基本信息更新任务
            scheduler.add_job(
                func=update_etf_basic_info,
                trigger=CronTrigger(hour=0, minute=0),
                id="update_etf_basic_info",
                name="每天凌晨0点更新ETF基本信息",
                replace_existing=True
            )

            # —— 数据地基任务 ——
            scheduler.add_job(
                func=index_service.update_index_daily,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_index_daily",
                name="交易日15:30后增量更新指数行情",
                replace_existing=True,
            )

            scheduler.add_job(
                func=stock_service.update_stock_daily_incremental,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_stock_daily_incremental",
                name="交易日15:30后增量更新个股K线",
                replace_existing=True,
            )

            scheduler.add_job(
                func=update_index_constituents,
                trigger=CronTrigger(day=1, hour=1, minute=0),
                id="update_index_constituents",
                name="每月1日更新指数成分股",
                replace_existing=True,
            )

            scheduler.add_job(
                func=update_stock_fundamentals,
                trigger=CronTrigger(month="4,8,10", day="last", hour=2, minute=0),
                id="update_stock_fundamentals",
                name="财报季更新财务指标",
                replace_existing=True,
            )

            scheduler.add_job(
                func=update_stock_industry_concept,
                trigger=CronTrigger(hour=3, minute=0),
                id="update_stock_industry_concept",
                name="每日更新个股行业与概念分类",
                replace_existing=True,
            )

            # 立即执行一次任务
            update_etf_daily_quotes()
            update_etf_basic_info()  # 立即执行一次ETF基本信息更新

            # 立即执行一次数据地基任务
            update_index_constituents()
            update_stock_industry_concept()
            
            # 启动调度器
            scheduler.start()
            logger.info("定时任务调度器已启动")
        else:
            logger.info("定时任务调度器已经在运行")
    except Exception as e:
        logger.error(f"启动定时任务调度器失败: {e}", exc_info=True)

def stop_scheduler():
    """停止定时任务"""
    try:
        if scheduler.running:
            scheduler.shutdown()
            logger.info("定时任务调度器已停止")
    except Exception as e:
        logger.error(f"停止定时任务调度器失败: {e}", exc_info=True)