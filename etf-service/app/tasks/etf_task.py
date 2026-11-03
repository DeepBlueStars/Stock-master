from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.interval import IntervalTrigger
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.date import DateTrigger
from datetime import datetime, timedelta
import logging
import os
import time

from ..services.etf_service import etf_service
from ..database.mongo import mongo_client
from .data_tasks import (
    update_index_constituents,
    update_stock_fundamentals,
    update_stock_industry_concept,
)
from ..services.index_service import index_service
from ..services.kline_backfill_service import KIND_ETF, KIND_STOCK, KlineBackfillService
from ..services.stock_service import stock_service
from ..services.valuation_service import valuation_service

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 定时任务统一使用北京时间，避免容器默认 UTC 导致“交易日 15:30”偏移
SCHEDULER_TIMEZONE = os.getenv("SCHEDULER_TIMEZONE", "Asia/Shanghai")

# 创建调度器实例
scheduler = BackgroundScheduler(timezone=SCHEDULER_TIMEZONE)

# 允许错过的任务在 12 小时内补跑；同一任务不并发
DAILY_JOB_OPTIONS = {
    "coalesce": True,
    "max_instances": 1,
    "misfire_grace_time": 12 * 3600,
}


def run_with_retry(label: str, operation, attempts: int = 3, delay: int = 60) -> bool:
    """执行任务并在失败后重试；全部失败返回 False，避免异常冒泡到调度器线程。"""
    for attempt in range(1, attempts + 1):
        try:
            operation()
            return True
        except Exception as e:  # noqa: BLE001 定时任务需要兜底，避免整轮采集丢失
            logger.error("%s 第 %s/%s 次执行失败: %s", label, attempt, attempts, e, exc_info=True)
            if attempt < attempts and delay > 0:
                time.sleep(delay)
    return False


def _ensure_mongo():
    """数据库不可达时快速失败，避免逐条写入时每条都等待超时。"""
    ping = getattr(mongo_client, "ping", None)
    if ping is not None and not ping():
        raise RuntimeError("MongoDB 不可达，跳过本轮任务（请检查 MONGO_URL）")


def _with_mongo(operation):
    def wrapped():
        _ensure_mongo()
        operation()
    return wrapped


def _schedule_startup_jobs():
    """服务启动后异步补跑一次采集，避免阻塞 FastAPI 启动与调度器注册。"""
    run_at = datetime.now() + timedelta(seconds=5)
    jobs = (
        ("startup_update_etf_daily_quotes", update_etf_daily_quotes, "启动后更新ETF日行情数据"),
        ("startup_update_etf_basic_info", update_etf_basic_info, "启动后更新ETF基本信息"),
        ("startup_update_index_constituents", update_index_constituents, "启动后更新指数成分股"),
        ("startup_update_stock_industry_concept", update_stock_industry_concept, "启动后更新个股行业与概念"),
    )
    for job_id, func, name in jobs:
        scheduler.add_job(
            func=func,
            trigger=DateTrigger(run_date=run_at),
            id=job_id,
            name=name,
            replace_existing=True,
            max_instances=1,
        )
    logger.info("已注册启动补采任务，将在 %s 秒后后台执行", 5)


def _update_etf_daily_quotes_once():
    # 周末不产生交易日数据，快照写进去的就是周五收盘的复制行，属于脏数据
    if datetime.now().weekday() >= 5:
        logger.info("周末非交易日，跳过ETF日行情快照")
        return
    _ensure_mongo()
    all_etf_data = etf_service.get_all_etf_data_for_db()
    if not all_etf_data:
        raise RuntimeError("获取ETF实时数据失败，数据为空")

    success_count = 0
    for etf_data in all_etf_data:
        if mongo_client.upsert_etf_daily_quote(etf_data):
            success_count += 1

    logger.info("ETF日行情数据更新完成: 成功更新 %s 条数据，总数据 %s 条",
                success_count, len(all_etf_data))
    if success_count == 0:
        raise RuntimeError("ETF日行情写入 0 条，请检查 MongoDB 连接（MONGO_URL）")


def update_etf_daily_quotes():
    """更新ETF日行情数据（失败自动重试）。"""
    run_with_retry("更新ETF日行情数据", _update_etf_daily_quotes_once, delay=10)


def _update_etf_basic_info_once():
    _ensure_mongo()
    all_etf_basic_info = etf_service.get_all_etf_basic_info_for_db()
    if not all_etf_basic_info:
        raise RuntimeError("获取ETF基本信息失败，数据为空")

    success_count = 0
    for etf_basic in all_etf_basic_info:
        if mongo_client.upsert_etf_basic_info(etf_basic):
            success_count += 1

    logger.info("ETF基本信息更新完成: 成功更新 %s 条数据，总数据 %s 条",
                success_count, len(all_etf_basic_info))
    if success_count == 0:
        raise RuntimeError("ETF基本信息写入 0 条，请检查 MongoDB 连接（MONGO_URL）")


def update_etf_basic_info():
    """更新ETF基本信息（失败自动重试）。"""
    run_with_retry("更新ETF基本信息", _update_etf_basic_info_once, delay=10)


def update_index_daily():
    """增量更新指数行情（失败自动重试）。"""
    run_with_retry("增量更新指数行情", _with_mongo(index_service.update_index_daily), delay=10)


def update_index_valuation():
    """指数估值（PE/PB 及其历史分位）收盘后更新。"""
    run_with_retry("更新指数估值", _with_mongo(valuation_service.update_all_index_valuation), delay=10)


def update_stock_daily_incremental():
    """增量更新个股K线（仅作为 market_quotes 无当日数据时的兜底）。"""
    run_with_retry("增量更新个股K线", _with_mongo(stock_service.update_stock_daily_incremental), delay=10)


def _update_kline_incremental_once():
    """交易日收盘后增量补全「持仓 + 交易计划 + 自选股」的日线。

    这条路径是 etf_daily_quotes / stock_daily_kline 的唯一权威写入口：
    与 30 分钟实时快照不同，它带完整 OHLC、成交量按「股」存储，并且是可幂等的 upsert。
    只覆盖关注池而非全市场——全市场 2440 只按限速要一个多小时，会跨过 17:30 的判定时点；
    全量刷新交给周六凌晨的 update_kline_weekly_sweep。
    """
    _ensure_mongo()
    service = KlineBackfillService(sleep_seconds=0.5)
    summary = []
    for kind in (KIND_ETF, KIND_STOCK):
        targets = service.focus_symbols(kind)
        if not targets:
            summary.append(f"{kind}: 关注池为空，跳过")
            continue
        stats = service.run(kind, symbols=targets, ensure_index=True)
        summary.append(
            f"{kind}: {len(targets)} 只，成功 {stats.ok}，失败 {stats.failed}，写入 {stats.rows} 行"
        )
        if stats.total and stats.failed >= stats.total:
            raise RuntimeError(f"{kind} 日线增量全部失败，请检查数据源是否可用")
    logger.info("日线增量补全完成 —— %s", "；".join(summary))


def update_kline_incremental():
    """增量补全关注池日线（失败自动重试）。"""
    run_with_retry("增量补全日线", _update_kline_incremental_once, attempts=2, delay=30)


def _update_kline_weekly_sweep_once():
    """周末全量扫一遍，把一周里没进关注池的标的补齐（覆盖 10 天，含节假日）。"""
    _ensure_mongo()
    service = KlineBackfillService(sleep_seconds=0.5, incremental_overlap_days=10)
    summary = []
    for kind in (KIND_ETF, KIND_STOCK):
        stats = service.run(kind, ensure_index=True)
        summary.append(
            f"{kind}: 成功 {stats.ok}，跳过 {stats.skipped}，失败 {stats.failed}，写入 {stats.rows} 行"
        )
        if stats.total and stats.failed >= stats.total:
            raise RuntimeError(f"{kind} 全量补全全部失败，请检查数据源是否可用")
    logger.info("周度全量补全完成 —— %s", "；".join(summary))


def update_kline_weekly_sweep():
    """周度全市场补全（失败不重试，下周还有一轮）。"""
    run_with_retry("周度全量补全日线", _update_kline_weekly_sweep_once, attempts=1, delay=0)


def update_stock_daily_from_market_quotes():
    """从 market_quotes 复制当日个股K线（失败自动重试）。"""
    run_with_retry("同步个股K线", _with_mongo(stock_service.sync_stock_daily_from_market_quotes), delay=10)


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
                func=update_index_daily,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_index_daily",
                name="交易日15:30后增量更新指数行情",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            scheduler.add_job(
                func=update_index_valuation,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_index_valuation",
                name="交易日15:30后更新指数估值",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            scheduler.add_job(
                func=update_stock_daily_from_market_quotes,
                trigger=CronTrigger(day_of_week="mon-fri", hour=16, minute=0),
                id="update_stock_daily_from_market_quotes",
                name="交易日16:00从market_quotes同步个股K线",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            scheduler.add_job(
                func=update_kline_incremental,
                trigger=CronTrigger(day_of_week="mon-fri", hour=16, minute=20),
                id="update_kline_incremental",
                name="交易日16:20增量补全关注池日线",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            # 全市场 2440 只逐个请求要一个多小时，放周末凌晨跑，不挡盘后判定
            scheduler.add_job(
                func=update_kline_weekly_sweep,
                trigger=CronTrigger(day_of_week="sat", hour=2, minute=0),
                id="update_kline_weekly_sweep",
                name="周六02:00全市场补全日线",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            scheduler.add_job(
                func=update_index_constituents,
                trigger=CronTrigger(day=1, hour=1, minute=0),
                id="update_index_constituents",
                name="每月1日更新指数成分股",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            scheduler.add_job(
                func=update_stock_fundamentals,
                trigger=CronTrigger(month="4,8,10", day="last", hour=2, minute=0),
                id="update_stock_fundamentals",
                name="财报季更新财务指标",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            scheduler.add_job(
                func=update_stock_industry_concept,
                trigger=CronTrigger(hour=3, minute=0),
                id="update_stock_industry_concept",
                name="每日更新个股行业与概念分类",
                replace_existing=True,
                **DAILY_JOB_OPTIONS,
            )

            # 先启动调度器，再异步补跑启动任务，避免采集耗时阻塞服务启动
            scheduler.start()
            logger.info("定时任务调度器已启动（时区 %s）", SCHEDULER_TIMEZONE)
            _schedule_startup_jobs()
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
