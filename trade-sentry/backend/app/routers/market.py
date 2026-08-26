from fastapi import APIRouter, Depends, HTTPException, WebSocket
from typing import List, Optional
from datetime import datetime

from app.routers.auth import get_current_active_user
from app.models import User
from app.services.market_service import market_service
from app.services.tushare_service import tushare_service
from app.schemas import (
    Stock, LimitUpStock, Sector, StockScreenerParams, 
    AIScore, DragonTigerData, SentimentData, ApiResponse
)

router = APIRouter()

# 模拟数据 - 当真实API不可用时使用
MOCK_LIMIT_UP_STOCKS = [
    {
        "code": "000001",
        "name": "平安银行",
        "board_type": "first",
        "limit_up_time": "09:35:28",
        "open_count": 0,
        "sector": "银行",
        "sector_strength": 8.5,
        "is_dragon_head": False,
        "volume_ratio": 5.2,
        "turnover_rate": 8.5,
        "market_cap": 2500000000,
        "reason": "业绩预增",
        "tags": ["首板", "业绩"],
        "ai_score": 82,
        "seal_amount": 15800000,
        "current_price": 12.80,
        "change_rate": 10.02
    },
    {
        "code": "000063",
        "name": "中兴通讯",
        "board_type": "second",
        "limit_up_time": "09:42:15",
        "open_count": 1,
        "sector": "通信设备",
        "sector_strength": 9.2,
        "is_dragon_head": True,
        "volume_ratio": 4.8,
        "turnover_rate": 12.3,
        "market_cap": 1800000000,
        "reason": "AI算力概念",
        "tags": ["二板", "AI", "龙头"],
        "ai_score": 91,
        "seal_amount": 32000000,
        "current_price": 35.60,
        "change_rate": 10.01
    },
    {
        "code": "002230",
        "name": "科大讯飞",
        "board_type": "third",
        "limit_up_time": "09:28:45",
        "open_count": 0,
        "sector": "人工智能",
        "sector_strength": 9.5,
        "is_dragon_head": True,
        "volume_ratio": 6.5,
        "turnover_rate": 15.2,
        "market_cap": 1200000000,
        "reason": "大模型发布",
        "tags": ["三板", "AI", "龙头"],
        "ai_score": 95,
        "seal_amount": 45000000,
        "current_price": 58.90,
        "change_rate": 10.00
    }
]

MOCK_SECTORS = [
    {
        "code": "AI001",
        "name": "人工智能",
        "change_rate": 5.8,
        "leading_stock": "002230",
        "stocks_count": 150,
        "limit_up_count": 15,
        "avg_turnover": 12.5,
        "strength_score": 9.5,
        "top_stocks": ["002230", "000063", "000938"]
    },
    {
        "code": "BANK001",
        "name": "银行",
        "change_rate": 2.3,
        "leading_stock": "000001",
        "stocks_count": 42,
        "limit_up_count": 3,
        "avg_turnover": 8.2,
        "strength_score": 6.8,
        "top_stocks": ["000001", "600036", "601398"]
    },
    {
        "code": "COM001",
        "name": "通信设备",
        "change_rate": 4.5,
        "leading_stock": "000063",
        "stocks_count": 85,
        "limit_up_count": 8,
        "avg_turnover": 10.8,
        "strength_score": 8.2,
        "top_stocks": ["000063", "600487", "600498"]
    }
]


@router.get("/stocks", response_model=ApiResponse)
async def get_stocks(
    sector: Optional[str] = None,
    board_type: Optional[str] = None,
    current_user: User = Depends(get_current_active_user)
):
    """获取股票列表"""
    stocks = MOCK_LIMIT_UP_STOCKS
    
    if sector:
        stocks = [s for s in stocks if s["sector"] == sector]
    
    if board_type:
        stocks = [s for s in stocks if s["board_type"] == board_type]
    
    return ApiResponse(data=stocks)


@router.get("/limitup", response_model=ApiResponse)
async def get_limit_up_stocks(
    current_user: User = Depends(get_current_active_user)
):
    """获取涨停股票列表"""
    try:
        import asyncio

        # 设置超时时间为60秒
        stocks = await asyncio.wait_for(
            market_service.get_limit_up_stocks(),
            timeout=60.0
        )

        if stocks and len(stocks) > 0:
            return ApiResponse(data=stocks)

        # 如果真实API没有数据，返回模拟数据（兼容模式）
        print("[Market] 真实API无数据，返回模拟数据")
        return ApiResponse(data=MOCK_LIMIT_UP_STOCKS)

    except asyncio.TimeoutError:
        print("[Market] 获取涨停股票超时，返回模拟数据")
        return ApiResponse(data=MOCK_LIMIT_UP_STOCKS)
    except Exception as e:
        print(f"[Market] 获取涨停股票失败: {e}")
        # 出错时返回模拟数据，保证系统可用性
        return ApiResponse(data=MOCK_LIMIT_UP_STOCKS)


@router.get("/stock/{code}", response_model=ApiResponse)
async def get_stock_detail(
    code: str,
    current_user: User = Depends(get_current_active_user)
):
    """获取单只股票详情"""
    try:
        # 从行情服务获取真实数据
        stock = await market_service.get_stock_detail(code)
        
        if not stock:
            # 如果没有数据，从模拟数据中查找
            stock = next((s for s in MOCK_LIMIT_UP_STOCKS if s["code"] == code), None)
            if not stock:
                raise HTTPException(status_code=404, detail="股票不存在")
        
        return ApiResponse(data=stock)
    except Exception as e:
        print(f"获取股票详情失败: {e}")
        # 出错时从模拟数据中查找
        stock = next((s for s in MOCK_LIMIT_UP_STOCKS if s["code"] == code), None)
        if not stock:
            raise HTTPException(status_code=404, detail="股票不存在")
        return ApiResponse(data=stock)


@router.get("/sectors", response_model=ApiResponse)
async def get_sectors(
    current_user: User = Depends(get_current_active_user)
):
    """获取板块列表"""
    try:
        sectors = await market_service.get_sectors()
        
        if not sectors:
            # 如果没有数据，返回模拟数据
            sorted_sectors = sorted(
                MOCK_SECTORS,
                key=lambda x: x["strength_score"],
                reverse=True
            )
            return ApiResponse(data=sorted_sectors)
        
        # 按强度排序
        sectors.sort(key=lambda x: x.get("strength_score", 0), reverse=True)
        return ApiResponse(data=sectors)
    except Exception as e:
        print(f"获取板块列表失败: {e}")
        sorted_sectors = sorted(
            MOCK_SECTORS,
            key=lambda x: x["strength_score"],
            reverse=True
        )
        return ApiResponse(data=sorted_sectors)


@router.get("/sector/{code}", response_model=ApiResponse)
async def get_sector_detail(
    code: str,
    current_user: User = Depends(get_current_active_user)
):
    """获取板块详情"""
    sector = next((s for s in MOCK_SECTORS if s["code"] == code), None)
    
    if not sector:
        raise HTTPException(status_code=404, detail="板块不存在")
    
    return ApiResponse(data=sector)


@router.post("/screener/scan", response_model=ApiResponse)
async def screen_stocks(
    params: StockScreenerParams,
    current_user: User = Depends(get_current_active_user)
):
    """智能选股"""
    stocks = MOCK_LIMIT_UP_STOCKS.copy()
    
    # 应用筛选条件
    if params.board_type:
        stocks = [s for s in stocks if s["board_type"] == params.board_type]
    
    if params.sectors:
        stocks = [s for s in stocks if s["sector"] in params.sectors]
    
    if params.ai_score_min:
        stocks = [s for s in stocks if s["ai_score"] >= params.ai_score_min]
    
    # 按AI评分排序
    stocks.sort(key=lambda x: x["ai_score"], reverse=True)
    
    return ApiResponse(data=stocks)


@router.get("/screener/ai_score", response_model=ApiResponse)
async def get_ai_score(
    code: str,
    current_user: User = Depends(get_current_active_user)
):
    """获取AI评分"""
    stock = next((s for s in MOCK_LIMIT_UP_STOCKS if s["code"] == code), None)
    
    if not stock:
        raise HTTPException(status_code=404, detail="股票不存在")
    
    # 模拟AI评分详情
    ai_detail = {
        "stock_code": code,
        "score": stock["ai_score"],
        "tech_score": stock["ai_score"] * 0.25,
        "fund_score": stock["ai_score"] * 0.20,
        "capital_score": stock["ai_score"] * 0.25,
        "senti_score": stock["ai_score"] * 0.20,
        "dragon_score": stock["ai_score"] * 0.10,
        "reasons": [
            "技术面：突破关键压力位",
            "资金面：主力资金净流入",
            "情绪面：板块效应明显",
            "基本面：业绩符合预期"
        ]
    }
    
    return ApiResponse(data=ai_detail)


@router.get("/dragon_tiger", response_model=ApiResponse)
async def get_dragon_tiger_list(
    date: Optional[str] = None,
    current_user: User = Depends(get_current_active_user)
):
    """获取龙虎榜数据"""
    # 模拟龙虎榜数据
    dragon_tiger_data = [
        {
            "date": date or datetime.now().strftime("%Y-%m-%d"),
            "stock_code": "002230",
            "stock_name": "科大讯飞",
            "data_type": "buy",
            "details": [
                {
                    "seat_name": "华泰证券深圳益田路",
                    "seat_type": "hot_money",
                    "buy_amount": 15800000,
                    "sell_amount": 5200000,
                    "net_amount": 10600000,
                    "is_famous": True,
                    "famous_name": "章盟主"
                }
            ],
            "summary": {
                "total_buy": 52000000,
                "total_sell": 38000000,
                "institution_buy": 12000000,
                "institution_sell": 8000000
            }
        }
    ]
    
    return ApiResponse(data=dragon_tiger_data)


@router.get("/sentiment", response_model=ApiResponse)
async def get_sentiment(
    current_user: User = Depends(get_current_active_user)
):
    """获取市场情绪数据"""
    try:
        # 从行情服务获取真实数据
        sentiment = await market_service.get_sentiment()
        return ApiResponse(data=sentiment)
    except Exception as e:
        print(f"获取情绪数据失败: {e}")
        # 出错时返回默认数据
        sentiment = {
            "date": datetime.now().strftime("%Y-%m-%d"),
            "timestamp": datetime.now().isoformat(),
            "up_count": 2800,
            "down_count": 1200,
            "limit_up_count": 85,
            "limit_down_count": 5,
            "max_board_height": 6,
            "sentiment_score": 7.5,
            "cycle_phase": "high_tide",
            "market_temperature": "hot"
        }
        return ApiResponse(data=sentiment)


@router.websocket("/ws")
async def market_websocket(websocket: WebSocket):
    """WebSocket实时行情推送"""
    await websocket.accept()
    
    try:
        while True:
            # 接收客户端消息
            data = await websocket.receive_text()
            
            # 处理订阅逻辑
            # 这里应该连接真实的行情推送
            
            # 模拟推送数据
            await websocket.send_json({
                "type": "price_update",
                "data": {
                    "code": "000001",
                    "price": 12.81,
                    "change": 0.01,
                    "time": datetime.now().isoformat()
                }
            })
    except Exception:
        pass
    finally:
        await websocket.close()


@router.get("/limitup/history", response_model=ApiResponse)
async def get_limit_up_stocks_history(
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    current_user: User = Depends(get_current_active_user)
):
    """获取涨停股票历史行情"""
    try:
        # 从Tushare服务获取涨停股票的历史行情
        history_data = tushare_service.get_limit_up_stocks_history(start_date, end_date)
        
        # 转换为可序列化的格式
        result = {}
        for stock_code, df in history_data.items():
            if df is not None:
                # 转换DataFrame为字典列表
                result[stock_code] = df.to_dict('records')
        
        return ApiResponse(data=result)
    except Exception as e:
        print(f"获取涨停股票历史行情失败: {e}")
        return ApiResponse(data={}, message=f"获取失败: {str(e)}")


@router.get("/limitup/analysis", response_model=ApiResponse)
async def get_limit_up_stocks_analysis(
    current_user: User = Depends(get_current_active_user)
):
    """获取涨停股票分析结果"""
    try:
        # 从Tushare服务获取涨停股票的分析结果
        analysis_result = tushare_service.analyze_limit_up_stocks()
        return ApiResponse(data=analysis_result)
    except Exception as e:
        print(f"分析涨停股票失败: {e}")
        return ApiResponse(data={}, message=f"分析失败: {str(e)}")
