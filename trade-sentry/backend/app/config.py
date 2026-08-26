from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # 应用配置
    app_name: str = "TradeSentry"
    app_version: str = "1.0.0"
    debug: bool = True
    
    # 服务器配置
    host: str = "0.0.0.0"
    port: int = 8000
    
    # 数据库配置 - 使用SQLite（无需MySQL配置）
    database_url: str = "sqlite:///./trade_sentry.db"
    
    # 如果需要MySQL，取消下面的注释并配置
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "123456"
    mysql_db: str = "trade_sentry"
    
    @property
    def mysql_url(self) -> str:
        # 如果使用MySQL，取消下面的注释
        return f"mysql+pymysql://{self.mysql_user}:{self.mysql_password}@{self.mysql_host}:{self.mysql_port}/{self.mysql_db}"
        # return self.database_url
    
    # MongoDB配置（可选）
    mongodb_host: str = "localhost"
    mongodb_port: int = 27017
    mongodb_db: str = "trade_sentry"
    
    @property
    def mongodb_url(self) -> str:
        return f"mongodb://{self.mongodb_host}:{self.mongodb_port}"
    
    # Redis配置（可选）
    redis_host: str = "123.57.223.140"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: str = "eaGFeds32lk"
    
    @property
    def redis_url(self) -> str:
        return f"redis://:{self.redis_password}@{self.redis_host}:{self.redis_port}/{self.redis_db}"
    
    # JWT配置
    jwt_secret: str = "feageawfdsafewa5fea41"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 60 * 24  # 24小时
    
    # 初始资金
    initial_balance: float = 10000.0
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache()
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
