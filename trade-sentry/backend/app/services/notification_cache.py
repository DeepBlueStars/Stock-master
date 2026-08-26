"""
通知缓存服务
避免当天重复提醒
"""
import json
from datetime import datetime, timedelta
from typing import Optional, Dict

import redis

from app.config import settings


class NotificationCache:
    """通知缓存服务"""
    
    def __init__(self):
        # Redis缓存
        try:
            self.redis_client = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                db=settings.redis_db,
                password=settings.redis_password,
                decode_responses=True
            )
            self.redis_client.ping()
            self.use_redis = True
            print("[NotificationCache] Redis连接成功")
        except Exception as e:
            print(f"[NotificationCache] Redis连接失败: {e}，使用内存缓存")
            self.use_redis = False
            self._cache = {}
        
        self.default_ttl = 86400  # 默认缓存24小时
        
        print("[NotificationCache] 初始化完成")
    
    def _get_today_key(self, stock_code: str, notification_type: str) -> str:
        """获取当天的缓存键
        
        Args:
            stock_code: 股票代码
            notification_type: 通知类型 (buy/sell/stop_loss)
            
        Returns:
            str: 缓存键
        """
        today = datetime.now().strftime("%Y%m%d")
        return f"notification:{today}:{notification_type}:{stock_code}"
    
    def _get_today_expire_time(self) -> int:
        """获取当天缓存的过期时间（秒）
        
        Returns:
            int: 过期时间（秒）
        """
        # 计算距离当天结束的秒数
        now = datetime.now()
        tomorrow = now.replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1)
        expire_seconds = int((tomorrow - now).total_seconds())
        return expire_seconds
    
    def has_notified_today(self, stock_code: str, notification_type: str) -> bool:
        """检查今天是否已经通知过
        
        Args:
            stock_code: 股票代码
            notification_type: 通知类型 (buy/sell/stop_loss)
            
        Returns:
            bool: 是否已通知
        """
        key = self._get_today_key(stock_code, notification_type)
        
        if self.use_redis:
            try:
                exists = self.redis_client.exists(key)
                return exists > 0
            except Exception as e:
                print(f"[NotificationCache] Redis检查失败: {e}")
                # 回退到内存缓存
                return key in self._cache
        else:
            return key in self._cache
    
    def mark_as_notified(self, stock_code: str, notification_type: str, data: Optional[Dict] = None) -> bool:
        """标记为已通知
        
        Args:
            stock_code: 股票代码
            notification_type: 通知类型 (buy/sell/stop_loss)
            data: 通知数据（可选）
            
        Returns:
            bool: 是否标记成功
        """
        key = self._get_today_key(stock_code, notification_type)
        expire_time = self._get_today_expire_time()
        
        # 构建缓存值
        cache_value = {
            'timestamp': datetime.now().isoformat(),
            'data': data or {}
        }
        
        if self.use_redis:
            try:
                self.redis_client.setex(
                    key,
                    expire_time,
                    json.dumps(cache_value, ensure_ascii=False)
                )
                print(f"[NotificationCache] 标记为已通知: {key}")
                return True
            except Exception as e:
                print(f"[NotificationCache] Redis设置失败: {e}")
                # 回退到内存缓存
                self._cache[key] = cache_value
                return True
        else:
            self._cache[key] = cache_value
            print(f"[NotificationCache] 标记为已通知（内存缓存）: {key}")
            return True
    
    def get_notification_data(self, stock_code: str, notification_type: str) -> Optional[Dict]:
        """获取通知数据
        
        Args:
            stock_code: 股票代码
            notification_type: 通知类型 (buy/sell/stop_loss)
            
        Returns:
            Optional[Dict]: 通知数据
        """
        key = self._get_today_key(stock_code, notification_type)
        
        if self.use_redis:
            try:
                data = self.redis_client.get(key)
                if data:
                    return json.loads(data)
            except Exception as e:
                print(f"[NotificationCache] Redis获取失败: {e}")
                # 回退到内存缓存
                return self._cache.get(key)
        else:
            return self._cache.get(key)
        
        return None
    
    def clear_today_cache(self) -> bool:
        """清除今天的缓存
        
        Returns:
            bool: 是否清除成功
        """
        today = datetime.now().strftime("%Y%m%d")
        pattern = f"notification:{today}:*"
        
        if self.use_redis:
            try:
                keys = self.redis_client.keys(pattern)
                if keys:
                    self.redis_client.delete(*keys)
                    print(f"[NotificationCache] 清除今天缓存: {len(keys)} 个键")
                return True
            except Exception as e:
                print(f"[NotificationCache] Redis清除失败: {e}")
                # 回退到内存缓存
                self._cache = {k: v for k, v in self._cache.items() if not k.startswith(f"notification:{today}:")}
                return True
        else:
            self._cache = {k: v for k, v in self._cache.items() if not k.startswith(f"notification:{today}:")}
            print("[NotificationCache] 清除今天缓存（内存缓存）")
            return True
    
    def clear_all_cache(self) -> bool:
        """清除所有缓存
        
        Returns:
            bool: 是否清除成功
        """
        pattern = "notification:*"
        
        if self.use_redis:
            try:
                keys = self.redis_client.keys(pattern)
                if keys:
                    self.redis_client.delete(*keys)
                    print(f"[NotificationCache] 清除所有缓存: {len(keys)} 个键")
                return True
            except Exception as e:
                print(f"[NotificationCache] Redis清除失败: {e}")
                # 回退到内存缓存
                self._cache = {}
                return True
        else:
            self._cache = {}
            print("[NotificationCache] 清除所有缓存（内存缓存）")
            return True
    
    def get_today_notifications(self) -> Dict[str, Dict]:
        """获取今天的所有通知
        
        Returns:
            Dict[str, Dict]: 通知数据
        """
        today = datetime.now().strftime("%Y%m%d")
        pattern = f"notification:{today}:*"
        
        notifications = {}
        
        if self.use_redis:
            try:
                keys = self.redis_client.keys(pattern)
                for key in keys:
                    data = self.redis_client.get(key)
                    if data:
                        notifications[key] = json.loads(data)
            except Exception as e:
                print(f"[NotificationCache] Redis获取失败: {e}")
                # 回退到内存缓存
                for key, value in self._cache.items():
                    if key.startswith(f"notification:{today}:"):
                        notifications[key] = value
        else:
            for key, value in self._cache.items():
                if key.startswith(f"notification:{today}:"):
                    notifications[key] = value
        
        return notifications


# 全局通知缓存实例
notification_cache = NotificationCache()
