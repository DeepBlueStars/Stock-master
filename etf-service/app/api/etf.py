from fastapi import APIRouter, HTTPException
from typing import Dict, Any
from ..services.etf_service import etf_service

# 创建路由实例
router = APIRouter(
    prefix="/etf",
    tags=["etf"],
    responses={404: {"description": "Not found"}},
)

@router.get("/getInfo/{symbol}", response_model=Dict[str, Any])
async def get_etf_info(symbol: str):
    """
    根据股票代码获取ETF实时详情
    
    - **symbol**: ETF代码
    - 返回字段: 基金代码、基金名称、市价、涨跌幅
    """
    etf_info = etf_service.get_etf_info_by_symbol(symbol)
    if not etf_info:
        raise HTTPException(status_code=404, detail="ETF not found")
    return etf_info