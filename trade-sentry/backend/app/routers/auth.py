from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
from jose import JWTError, jwt
from passlib.context import CryptContext
from typing import Optional

from app.database import get_db
from app.config import settings
from app.models import User as UserModel
from app.schemas import (
    LoginRequest, LoginResponse, RegisterRequest, 
    User, UserUpdate, UserBalance, ApiResponse
)

router = APIRouter()

# 密码加密
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# OAuth2 scheme
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """验证密码"""
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    """获取密码哈希"""
    return pwd_context.hash(password)


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    """创建JWT token"""
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=settings.jwt_expire_minutes)
    
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(
        to_encode, 
        settings.jwt_secret, 
        algorithm=settings.jwt_algorithm
    )
    return encoded_jwt


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: Session = Depends(get_db)
) -> UserModel:
    """获取当前用户"""
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="无效的认证凭据",
        headers={"WWW-Authenticate": "Bearer"},
    )
    
    try:
        payload = jwt.decode(
            token, 
            settings.jwt_secret, 
            algorithms=[settings.jwt_algorithm]
        )
        username: str = payload.get("sub")
        if username is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
    
    user = db.query(UserModel).filter(UserModel.username == username).first()
    if user is None:
        raise credentials_exception
    
    return user


async def get_current_active_user(
    current_user: UserModel = Depends(get_current_user)
) -> UserModel:
    """获取当前活跃用户"""
    if not current_user.is_active:
        raise HTTPException(status_code=400, detail="用户已被禁用")
    return current_user


@router.post("/register", response_model=ApiResponse)
async def register(
    request: RegisterRequest,
    db: Session = Depends(get_db)
):
    """用户注册"""
    # 检查用户名是否已存在
    existing_user = db.query(UserModel).filter(
        UserModel.username == request.username
    ).first()
    
    if existing_user:
        raise HTTPException(
            status_code=400,
            detail="用户名已存在"
        )
    
    # 创建新用户
    hashed_password = get_password_hash(request.password)
    new_user = UserModel(
        username=request.username,
        password_hash=hashed_password,
        email=request.email,
        phone=request.phone,
        total_balance=settings.initial_balance,
        available_balance=settings.initial_balance
    )
    
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    return ApiResponse(message="注册成功")


@router.post("/login", response_model=ApiResponse)
async def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db)
):
    """用户登录"""
    # 查找用户
    user = db.query(UserModel).filter(
        UserModel.username == form_data.username
    ).first()
    
    if not user or not verify_password(form_data.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="用户名或密码错误",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    # 更新最后登录时间
    user.last_login = datetime.utcnow()
    db.commit()
    
    # 创建token
    access_token_expires = timedelta(minutes=settings.jwt_expire_minutes)
    access_token = create_access_token(
        data={"sub": user.username},
        expires_delta=access_token_expires
    )
    
    return ApiResponse(
        data={
            "token": access_token,
            "user": User.from_orm(user)
        }
    )


@router.get("/profile", response_model=ApiResponse)
async def get_profile(
    current_user: UserModel = Depends(get_current_active_user)
):
    """获取用户信息"""
    return ApiResponse(data=User.from_orm(current_user))


@router.put("/profile", response_model=ApiResponse)
async def update_profile(
    update_data: UserUpdate,
    current_user: UserModel = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """更新用户信息"""
    # 更新字段
    if update_data.email is not None:
        current_user.email = update_data.email
    if update_data.phone is not None:
        current_user.phone = update_data.phone
    if update_data.risk_level is not None:
        current_user.risk_level = update_data.risk_level
    if update_data.max_position_count is not None:
        current_user.max_position_count = update_data.max_position_count
    if update_data.stop_loss_rate is not None:
        current_user.stop_loss_rate = update_data.stop_loss_rate
    
    db.commit()
    db.refresh(current_user)
    
    return ApiResponse(
        message="更新成功",
        data=User.from_orm(current_user)
    )


@router.post("/logout", response_model=ApiResponse)
async def logout():
    """用户登出"""
    # JWT token无法真正失效，客户端需要删除token
    return ApiResponse(message="登出成功")
