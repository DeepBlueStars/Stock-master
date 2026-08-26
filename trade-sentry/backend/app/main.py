import asyncio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager

from app.config import settings
from app.database import init_db
from app.routers import auth, market, trade, position, review
from app.tasks.market_tasks import start_market_data_update, stop_market_data_update
from app.tasks.dragon_head_tasks import dragon_head_tasks
from app.tasks.limit_up_tasks import start_limit_up_data_update, stop_limit_up_data_update
from data_fetcher.data_aggregator import data_aggregator


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时执行
    print(f"Starting {settings.app_name} v{settings.app_version}")
    init_db()  # 初始化数据库

    # 初始化数据聚合器会话
    await data_aggregator.sina._init_session()
    await data_aggregator.eastmoney._init_session()
    await data_aggregator.tencent._init_session()
    await data_aggregator.netease._init_session()

    # 启动涨停股票数据更新任务（最高优先级）
    asyncio.create_task(start_limit_up_data_update())
    
    # 启动行情数据更新任务
    asyncio.create_task(start_market_data_update())
    
    # 启动龙头战法分析任务
    asyncio.create_task(dragon_head_tasks.start_scheduler())

    yield

    # 关闭时执行
    stop_limit_up_data_update()
    stop_market_data_update()
    
    # 停止龙头战法分析任务
    dragon_head_tasks.stop_scheduler()

    # 关闭数据聚合器会话
    await data_aggregator.sina._close_session()
    await data_aggregator.eastmoney._close_session()
    await data_aggregator.tencent._close_session()
    await data_aggregator.netease._close_session()

    print(f"Shutting down {settings.app_name}")


# 创建FastAPI应用
app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="打板龙头战法交易系统后端API",
    lifespan=lifespan
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境需要限制
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 统一响应格式
@app.middleware("http")
async def add_process_time_header(request, call_next):
    import time
    start_time = time.time()
    response = await call_next(request)
    process_time = time.time() - start_time
    response.headers["X-Process-Time"] = str(process_time)
    return response


# 健康检查
@app.get("/health")
async def health_check():
    return {"status": "healthy", "version": settings.app_version}


# 注册路由
app.include_router(auth.router, prefix="/api/v1/auth", tags=["认证"])
app.include_router(market.router, prefix="/api/v1/market", tags=["行情"])
app.include_router(trade.router, prefix="/api/v1/trade", tags=["交易"])
app.include_router(position.router, prefix="/api/v1/position", tags=["持仓"])
app.include_router(review.router, prefix="/api/v1/review", tags=["复盘"])


# 全局异常处理
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    import time
    return JSONResponse(
        status_code=500,
        content={
            "code": 500,
            "message": "服务器内部错误",
            "error": str(exc),
            "timestamp": int(time.time())
        }
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )
