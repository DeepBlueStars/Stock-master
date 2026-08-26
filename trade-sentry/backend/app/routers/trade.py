from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from datetime import datetime
from decimal import Decimal

from app.database import get_db
from app.routers.auth import get_current_active_user
from app.models import User, TradeRecord as TradeRecordModel, Position
from app.schemas import OrderRequest, TradeRecord, ApiResponse

router = APIRouter()

# 手续费率
COMMISSION_RATE = Decimal("0.0003")  # 万3
TAX_RATE = Decimal("0.001")  # 千1印花税（卖出时）


@router.post("/order", response_model=ApiResponse)
async def create_order(
    order: OrderRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """创建订单（手动交易）"""
    
    # 计算金额
    amount = order.price * order.volume
    
    # 计算费用
    commission = amount * COMMISSION_RATE
    tax = Decimal("0") if order.tradeType == "buy" else amount * TAX_RATE
    total_cost = amount + commission + tax
    
    # 检查资金（买入时）
    if order.tradeType == "buy":
        # 检查持仓数量限制
        holding_positions_count = db.query(Position).filter(
            Position.user_id == current_user.id,
            Position.status == "holding"
        ).count()
        
        if holding_positions_count >= current_user.max_position_count:
            raise HTTPException(status_code=400, detail=f"持仓数量已达上限（{current_user.max_position_count}只）")
        
        # 检查资金
        if current_user.available_balance < total_cost:
            raise HTTPException(status_code=400, detail="可用资金不足")
        
        # 冻结资金
        current_user.available_balance -= total_cost
        current_user.frozen_balance += total_cost
    
    # 创建交易记录
    trade = TradeRecordModel(
        user_id=current_user.id,
        stock_code=order.stockCode,
        stock_name=order.stockName,
        trade_type=order.tradeType,
        trade_mode="manual",
        order_type=order.orderType,
        volume=order.volume,
        price=order.price,
        amount=amount,
        commission=commission,
        tax=tax,
        total_cost=total_cost,
        strategy=order.strategy or "sweep_board",
        board_type="first",  # 应该从股票信息中获取
        trade_date=datetime.now(),
        trade_time=datetime.now(),
        status="filled"  # 模拟成交
    )
    
    db.add(trade)
    
    # 更新持仓（买入时）
    if order.tradeType == "buy":
        # 查找是否已有持仓
        existing_position = db.query(Position).filter(
            Position.user_id == current_user.id,
            Position.stock_code == order.stockCode,
            Position.status == "holding"
        ).first()
        
        if existing_position:
            # 更新持仓
            total_volume = existing_position.total_volume + order.volume
            total_cost_amount = (existing_position.avg_cost * existing_position.total_volume) + amount
            existing_position.avg_cost = total_cost_amount / total_volume
            existing_position.total_volume = total_volume
            existing_position.available_volume = total_volume
            existing_position.current_price = order.price
            existing_position.market_value = total_volume * order.price
        else:
            # 映射交易策略到持仓策略
            strategy_map = {
                "sweep_board": "first_board",
                "queue_board": "first_board",
                "dragon_second_wave": "dragon_head"
            }
            position_strategy = strategy_map.get(order.strategy, "first_board")
            
            # 创建新持仓
            position = Position(
                user_id=current_user.id,
                stock_code=order.stockCode,
                stock_name=order.stockName,
                total_volume=order.volume,
                available_volume=order.volume,
                avg_cost=order.price,
                current_price=order.price,
                market_value=amount,
                buy_date=datetime.now(),
                strategy=position_strategy
            )
            db.add(position)
    else:
        # 卖出时更新持仓
        position = db.query(Position).filter(
            Position.user_id == current_user.id,
            Position.stock_code == order.stockCode,
            Position.status == "holding"
        ).first()
        
        if not position:
            raise HTTPException(status_code=400, detail="持仓不足")
        
        if position.available_volume < order.volume:
            raise HTTPException(status_code=400, detail="可用持仓不足")
        
        position.available_volume -= order.volume
        if position.available_volume <= 0:
            position.status = "closed"
            position.closed_at = datetime.now()
        position.current_price = order.price
        position.market_value = position.available_volume * order.price
        
        # 计算盈亏
        sell_amount = order.price * order.volume
        buy_amount = position.avg_cost * order.volume
        position.profit_loss = sell_amount - buy_amount
        position.profit_loss_rate = (position.profit_loss / buy_amount) * 100
    
    # 更新资金（卖出时解冻资金）
    if order.tradeType == "sell":
        current_user.available_balance += (amount - commission - tax)
    else:
        # 买入时解冻冻结资金
        current_user.frozen_balance -= total_cost
    
    db.commit()
    db.refresh(trade)
    
    # 构建返回的交易记录数据
    trade_data = {
        "id": trade.id,
        "userId": trade.user_id,
        "stockCode": trade.stock_code,
        "stockName": trade.stock_name,
        "tradeType": trade.trade_type,
        "tradeMode": trade.trade_mode,
        "orderType": trade.order_type,
        "volume": trade.volume,
        "price": float(trade.price),
        "amount": float(trade.amount),
        "commission": float(trade.commission),
        "tax": float(trade.tax),
        "totalCost": float(trade.total_cost),
        "strategy": trade.strategy,
        "boardType": trade.board_type,
        "tradeDate": trade.trade_date.isoformat(),
        "tradeTime": trade.trade_time.isoformat(),
        "status": trade.status,
        "errorMsg": trade.error_msg,
        "createdAt": trade.created_at.isoformat()
    }
    
    return ApiResponse(
        code=200,
        message="订单创建成功",
        data=trade_data
    )


@router.delete("/order/{order_id}", response_model=ApiResponse)
async def cancel_order(
    order_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """撤销订单"""
    trade = db.query(TradeRecordModel).filter(
        TradeRecordModel.id == order_id,
        TradeRecordModel.user_id == current_user.id
    ).first()
    
    if not trade:
        raise HTTPException(status_code=404, detail="订单不存在")
    
    if trade.status != "pending":
        raise HTTPException(status_code=400, detail="只能撤销待成交订单")
    
    # 撤销订单，解冻资金
    if trade.trade_type == "buy":
        current_user.available_balance += trade.total_cost
        current_user.frozen_balance -= trade.total_cost
    
    trade.status = "cancelled"
    db.commit()
    
    return ApiResponse(
        code=200,
        message="订单已撤销"
    )


@router.post("/cancel_all", response_model=ApiResponse)
async def cancel_all_orders(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """一键撤销所有待成交订单"""
    pending_orders = db.query(TradeRecordModel).filter(
        TradeRecordModel.user_id == current_user.id,
        TradeRecordModel.status == "pending"
    ).all()
    
    for order in pending_orders:
        if order.trade_type == "buy":
            current_user.available_balance += order.total_cost
            current_user.frozen_balance -= order.total_cost
        order.status = "cancelled"
    
    db.commit()
    
    return ApiResponse(
        code=200,
        message=f"已撤销 {len(pending_orders)} 个订单"
    )


@router.get("/orders", response_model=ApiResponse)
async def get_orders(
    status: str = None,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取订单列表"""
    query = db.query(TradeRecordModel).filter(TradeRecordModel.user_id == current_user.id)
    
    if status:
        query = query.filter(TradeRecordModel.status == status)
    
    orders = query.order_by(TradeRecordModel.created_at.desc()).all()
    
    # 构建返回的订单数据
    orders_data = []
    for order in orders:
        order_data = {
            "id": order.id,
            "userId": order.user_id,
            "stockCode": order.stock_code,
            "stockName": order.stock_name,
            "tradeType": order.trade_type,
            "tradeMode": order.trade_mode,
            "orderType": order.order_type,
            "volume": order.volume,
            "price": float(order.price),
            "amount": float(order.amount),
            "commission": float(order.commission),
            "tax": float(order.tax),
            "totalCost": float(order.total_cost),
            "strategy": order.strategy,
            "boardType": order.board_type,
            "tradeDate": order.trade_date.isoformat(),
            "tradeTime": order.trade_time.isoformat(),
            "status": order.status,
            "errorMsg": order.error_msg,
            "createdAt": order.created_at.isoformat()
        }
        orders_data.append(order_data)
    
    return ApiResponse(
        code=200,
        message="success",
        data=orders_data
    )


@router.get("/order/{order_id}", response_model=ApiResponse)
async def get_order_detail(
    order_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """获取订单详情"""
    order = db.query(TradeRecordModel).filter(
        TradeRecordModel.id == order_id,
        TradeRecordModel.user_id == current_user.id
    ).first()
    
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在")
    
    # 构建返回的订单数据
    order_data = {
        "id": order.id,
        "userId": order.user_id,
        "stockCode": order.stock_code,
        "stockName": order.stock_name,
        "tradeType": order.trade_type,
        "tradeMode": order.trade_mode,
        "orderType": order.order_type,
        "volume": order.volume,
        "price": float(order.price),
        "amount": float(order.amount),
        "commission": float(order.commission),
        "tax": float(order.tax),
        "totalCost": float(order.total_cost),
        "strategy": order.strategy,
        "boardType": order.board_type,
        "tradeDate": order.trade_date.isoformat(),
        "tradeTime": order.trade_time.isoformat(),
        "status": order.status,
        "errorMsg": order.error_msg,
        "createdAt": order.created_at.isoformat()
    }
    
    return ApiResponse(
        code=200,
        message="success",
        data=order_data
    )
