from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
from typing import Optional

from app.database import get_db
from app.routers.auth import get_current_active_user
from app.models import User, TradeRecord, Position
from app.schemas import DailyReview, TradeRecord, ApiResponse

router = APIRouter()


@router.get("/daily", response_model=ApiResponse)
async def get_daily_review(
    date: Optional[str] = None,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取每日复盘报告"""
    if not date:
        date = datetime.now().strftime("%Y-%m-%d")
    
    # 获取当日交易记录
    trades = db.query(TradeRecord).filter(
        TradeRecord.user_id == current_user.id,
        TradeRecord.trade_date == date
    ).all()
    
    # 计算统计数据
    total_trades = len(trades)
    success_trades = len([t for t in trades if t.profit_loss and t.profit_loss > 0])
    failed_trades = len([t for t in trades if t.profit_loss and t.profit_loss < 0])
    total_profit = sum([t.profit_loss for t in trades if t.profit_loss]) if trades else 0
    
    # 计算收益率
    profit_rate = (total_profit / current_user.total_balance * 100) if current_user.total_balance > 0 else 0
    
    review = {
        "date": date,
        "summary": {
            "market": {
                "index_change": 0.85,
                "up_count": 2800,
                "down_count": 1200,
                "limit_up_count": 85,
                "limit_down_count": 5,
                "sentiment": "强势"
            },
            "limit_up_analysis": {
                "first_board": 45,
                "second_board": 20,
                "third_board": 12,
                "high_board": 8,
                "avg_limit_up_time": "09:42:15",
                "max_board_height": 6
            },
            "sectors": [],
            "dragon_tiger": []
        },
        "trades": [TradeRecord.from_orm(t) for t in trades],
        "statistics": {
            "total_trades": total_trades,
            "success_trades": success_trades,
            "failed_trades": failed_trades,
            "total_profit": float(total_profit),
            "profit_rate": float(profit_rate),
            "max_drawdown": -2.5
        },
        "recommendations": [
            "今日市场情绪较好，涨停家数达到85家",
            "人工智能板块表现强势，可关注龙头标的",
            "建议严格执行-6%止损纪律",
            "仓位控制在3只以内，分散风险"
        ]
    }
    
    return ApiResponse(data=review)


@router.get("/trades", response_model=ApiResponse)
async def get_trade_history(
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取交易历史"""
    query = db.query(TradeRecord).filter(TradeRecord.user_id == current_user.id)
    
    if start_date:
        query = query.filter(TradeRecord.trade_date >= start_date)
    if end_date:
        query = query.filter(TradeRecord.trade_date <= end_date)
    
    # 默认查询最近30天
    if not start_date and not end_date:
        thirty_days_ago = datetime.now() - timedelta(days=30)
        query = query.filter(TradeRecord.trade_date >= thirty_days_ago)
    
    trades = query.order_by(TradeRecord.trade_date.desc()).all()
    
    return ApiResponse(data=[TradeRecord.from_orm(t) for t in trades])


@router.get("/statistics", response_model=ApiResponse)
async def get_statistics(
    days: int = 30,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取交易统计"""
    start_date = datetime.now() - timedelta(days=days)
    
    trades = db.query(TradeRecord).filter(
        TradeRecord.user_id == current_user.id,
        TradeRecord.trade_date >= start_date
    ).all()
    
    # 计算统计数据
    total_trades = len(trades)
    
    if total_trades == 0:
        return ApiResponse(data={
            "period": days,
            "total_trades": 0,
            "win_rate": 0,
            "avg_profit": 0,
            "max_profit": 0,
            "max_loss": 0,
            "total_return": 0
        })
    
    profits = [t.profit_loss for t in trades if t.profit_loss]
    
    win_count = len([p for p in profits if p > 0])
    win_rate = (win_count / len(profits) * 100) if profits else 0
    
    avg_profit = sum(profits) / len(profits) if profits else 0
    max_profit = max(profits) if profits else 0
    max_loss = min(profits) if profits else 0
    
    total_return = sum(profits)
    
    return ApiResponse(data={
        "period": days,
        "total_trades": total_trades,
        "win_rate": float(win_rate),
        "avg_profit": float(avg_profit),
        "max_profit": float(max_profit),
        "max_loss": float(max_loss),
        "total_return": float(total_return)
    })


@router.get("/backtest", response_model=ApiResponse)
async def run_backtest(
    strategy: str,
    start_date: str,
    end_date: str,
    initial_capital: float = 10000,
    current_user: User = Depends(get_current_active_user)
):
    """策略回测"""
    # 这里应该实现真实的回测逻辑
    # 现在返回模拟数据
    
    backtest_result = {
        "strategy": strategy,
        "period": f"{start_date} to {end_date}",
        "initial_capital": initial_capital,
        "final_capital": initial_capital * 1.15,
        "total_return": 15.0,
        "annualized_return": 56.8,
        "max_drawdown": -8.5,
        "win_rate": 65.3,
        "profit_loss_ratio": 1.8,
        "trade_count": 120,
        "avg_holding_days": 1.2
    }
    
    return ApiResponse(data=backtest_result)


@router.get("/sentiment", response_model=ApiResponse)
async def get_sentiment_history(
    days: int = 7,
    current_user: User = Depends(get_current_active_user)
):
    """获取情绪周期历史"""
    sentiment_data = []
    
    for i in range(days):
        date = (datetime.now() - timedelta(days=i)).strftime("%Y-%m-%d")
        sentiment_data.append({
            "date": date,
            "up_count": 2800 - i * 100,
            "down_count": 1200 + i * 50,
            "limit_up_count": 85 - i * 5,
            "limit_down_count": 5 + i,
            "sentiment_score": max(0, 7.5 - i * 0.5),
            "cycle_phase": "high_tide" if i < 3 else "decline" if i < 5 else "recovery"
        })
    
    return ApiResponse(data=sentiment_data)
