from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from datetime import datetime
from decimal import Decimal

from app.database import get_db
from app.routers.auth import get_current_active_user
from app.models import User, Position, TradeRecord
from app.schemas import Position, TradeRecord, ApiResponse

router = APIRouter()


@router.get("/list", response_model=ApiResponse)
async def get_positions(
    status: str = "holding",
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取持仓列表"""
    positions = db.query(Position).filter(
        Position.user_id == current_user.id,
        Position.status == status
    ).all()
    
    return ApiResponse(data=[Position.from_orm(p) for p in positions])


@router.get("/{position_id}", response_model=ApiResponse)
async def get_position_detail(
    position_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取持仓详情"""
    position = db.query(Position).filter(
        Position.id == position_id,
        Position.user_id == current_user.id
    ).first()
    
    if not position:
        raise HTTPException(status_code=404, detail="持仓不存在")
    
    return ApiResponse(data=Position.from_orm(position))


@router.post("/close/{position_id}", response_model=ApiResponse)
async def close_position(
    position_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """平仓（卖出全部持仓）"""
    position = db.query(Position).filter(
        Position.id == position_id,
        Position.user_id == current_user.id,
        Position.status == "holding"
    ).first()
    
    if not position:
        raise HTTPException(status_code=404, detail="持仓不存在或已平仓")
    
    # 创建卖出订单
    # 这里简化处理，实际应该调用交易逻辑
    position.status = "closed"
    position.closed_at = datetime.now()
    position.available_volume = 0
    
    db.commit()
    
    return ApiResponse(message="平仓成功")


@router.post("/close_all", response_model=ApiResponse)
async def close_all_positions(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """一键清仓（卖出所有持仓）"""
    positions = db.query(Position).filter(
        Position.user_id == current_user.id,
        Position.status == "holding"
    ).all()
    
    for position in positions:
        position.status = "closed"
        position.closed_at = datetime.now()
        position.available_volume = 0
    
    db.commit()
    
    return ApiResponse(
        message=f"已清仓 {len(positions)} 个持仓"
    )


@router.put("/{position_id}/stop_loss", response_model=ApiResponse)
async def set_stop_loss(
    position_id: int,
    stop_loss_price: Decimal,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """设置止损价格"""
    position = db.query(Position).filter(
        Position.id == position_id,
        Position.user_id == current_user.id
    ).first()
    
    if not position:
        raise HTTPException(status_code=404, detail="持仓不存在")
    
    # 这里应该存储止损价格到数据库
    # 为简化，暂时返回成功
    
    return ApiResponse(
        message=f"止损价格已设置为 {stop_loss_price}"
    )


@router.get("/balance", response_model=ApiResponse)
async def get_balance(
    current_user: User = Depends(get_current_active_user)
):
    """获取资金信息"""
    return ApiResponse(data={
        "total": float(current_user.total_balance),
        "available": float(current_user.available_balance),
        "frozen": float(current_user.frozen_balance)
    })


@router.get("/statistics", response_model=ApiResponse)
async def get_position_statistics(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取持仓统计信息"""
    positions = db.query(Position).filter(
        Position.user_id == current_user.id,
        Position.status == "holding"
    ).all()
    
    total_market_value = sum(p.market_value for p in positions if p.market_value)
    total_profit = sum(p.profit_loss for p in positions)
    
    return ApiResponse(data={
        "position_count": len(positions),
        "total_market_value": float(total_market_value),
        "total_profit": float(total_profit),
        "total_profit_rate": float(total_profit / current_user.total_balance * 100) if current_user.total_balance > 0 else 0
    })
