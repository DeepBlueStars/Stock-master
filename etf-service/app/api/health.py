from fastapi import APIRouter

from ..services.health_service import health_snapshot

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    """数据库连通性 + stock_daily_kline 最新交易日，便于确认每日采集是否成功。"""
    return health_snapshot()
