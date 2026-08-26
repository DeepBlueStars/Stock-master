from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
import os

# 解决相对导入问题
try:
    from .api import api_router
    from .tasks.etf_task import start_scheduler
except ImportError:
    import sys
    from pathlib import Path
    # 将项目根目录添加到Python路径
    sys.path.append(str(Path(__file__).parent.parent))
    from app.api import api_router
    from app.tasks.etf_task import start_scheduler

# 创建FastAPI应用实例
app = FastAPI(
    title="ETF数据服务",
    description="提供ETF实时数据查询和定时更新服务",
    version="1.0.0"
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册API路由
app.include_router(api_router)

# 根路径
@app.get("/")
async def root():
    return {"message": "ETF数据服务API"}

# 启动定时任务
@app.on_event("startup")
async def startup_event():
    start_scheduler()

# 主函数
if __name__ == "__main__":
    # 获取端口配置
    port = int(os.getenv("PORT", "8887"))
    host = os.getenv("HOST", "0.0.0.0")
    
    # 启动UVicorn服务器
    uvicorn.run("app.main:app", host=host, port=port, reload=True)