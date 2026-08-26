from fastapi import APIRouter
from .etf import router as etf_router

# 创建主路由实例
api_router = APIRouter()

# 包含所有子路由
api_router.include_router(etf_router)

__all__ = ["api_router"]