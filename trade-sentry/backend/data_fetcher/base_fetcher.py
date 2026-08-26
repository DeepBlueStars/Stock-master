"""
API基础类
提供重试机制和通用功能
"""
import asyncio
import aiohttp
from typing import Optional, TypeVar, Generic, Callable
from dataclasses import dataclass
from abc import ABC, abstractmethod
import time


T = TypeVar('T')


@dataclass
class RetryConfig:
    """重试配置"""
    max_retries: int = 3
    retry_delay: float = 1.0
    max_delay: float = 10.0
    backoff_factor: float = 2.0
    timeout: float = 10.0


class BaseDataFetcher(ABC):
    """数据获取基类"""
    
    def __init__(self, name: str, retry_config: Optional[RetryConfig] = None):
        self.name = name
        self.retry_config = retry_config or RetryConfig()
        self.session: Optional[aiohttp.ClientSession] = None
    
    async def _init_session(self):
        """初始化HTTP会话"""
        if not self.session:
            timeout = aiohttp.ClientTimeout(total=self.retry_config.timeout)
            self.session = aiohttp.ClientSession(timeout=timeout)
    
    async def _close_session(self):
        """关闭HTTP会话"""
        if self.session:
            await self.session.close()
            self.session = None
    
    async def fetch_with_retry(
        self,
        fetch_func: Callable[..., T],
        *args,
        **kwargs
    ) -> Optional[T]:
        """
        带重试机制的数据获取
        
        Args:
            fetch_func: 数据获取函数
            *args, **kwargs: 传递给fetch_func的参数
            
        Returns:
            获取到的数据，失败返回None
        """
        delay = self.retry_config.retry_delay
        last_exception = None
        
        for attempt in range(self.retry_config.max_retries):
            try:
                print(f"[{self.name}] 第 {attempt + 1} 次尝试获取数据...")
                result = await fetch_func(*args, **kwargs)
                
                if result:
                    print(f"[{self.name}] 数据获取成功")
                    return result
                
                print(f"[{self.name}] 返回空数据，准备重试")
                
            except Exception as e:
                last_exception = e
                print(f"[{self.name}] 第 {attempt + 1} 次尝试失败: {e}")
            
            # 如果不是最后一次尝试，等待后重试
            if attempt < self.retry_config.max_retries - 1:
                print(f"[{self.name}] 等待 {delay} 秒后重试...")
                await asyncio.sleep(delay)
                delay = min(delay * self.retry_config.backoff_factor, self.retry_config.max_delay)
        
        print(f"[{self.name}] 所有 {self.retry_config.max_retries} 次尝试都失败")
        return None
    
    async def http_get(self, url: str, **kwargs) -> Optional[dict]:
        """
        发送HTTP GET请求
        
        Args:
            url: 请求URL
            **kwargs: 其他参数
            
        Returns:
            JSON响应数据，失败返回None
        """
        await self._init_session()
        
        # 设置默认headers
        headers = kwargs.pop('headers', {})
        headers.setdefault('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36')
        
        try:
            import ssl
            # 创建SSL上下文，允许不验证证书（某些API可能需要）
            ssl_context = ssl.create_default_context()
            ssl_context.check_hostname = False
            ssl_context.verify_mode = ssl.CERT_NONE
            
            async with self.session.get(url, headers=headers, ssl=ssl_context, **kwargs) as response:
                if response.status == 200:
                    content_type = response.headers.get('Content-Type', '')
                    
                    if 'application/json' in content_type:
                        return await response.json()
                    else:
                        # 处理文本响应
                        text = await response.text()
                        return {'text': text, 'status': response.status}
                else:
                    print(f"[{self.name}] HTTP {response.status}: {url}")
                    return None
                    
        except asyncio.TimeoutError:
            print(f"[{self.name}] 请求超时: {url}")
            return None
        except Exception as e:
            print(f"[{self.name}] 请求异常: {e}")
            return None
    
    @staticmethod
    def format_stock_code(code: str) -> str:
        """
        格式化股票代码
        沪市: 600xxx -> sh600xxx
        深市: 000xxx -> sz000xxx
        """
        code = code.strip()
        if not code:
            return ''
        
        # 如果已经是带前缀的格式，直接返回
        if code.startswith(('sh', 'sz')):
            return code
        
        # 根据代码规则判断市场
        if code.startswith(('6', '688', '689')):
            return f"sh{code}"
        else:
            return f"sz{code}"
    
    @staticmethod
    def parse_stock_code(sina_code: str) -> str:
        """将带前缀的代码转回普通代码"""
        if sina_code.startswith(('sh', 'sz')):
            return sina_code[2:]
        return sina_code
    
    async def __aenter__(self):
        await self._init_session()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self._close_session()
