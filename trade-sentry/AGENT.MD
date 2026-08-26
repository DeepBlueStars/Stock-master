# AGENT.MD - TradeSentry 开发要求文档

> **项目**: TradeSentry 打板龙头战法交易系统  
> **版本**: v1.0  
> **创建日期**: 2026-02-07  
> **适用人员**: 前端开发工程师、后端开发工程师

---

## 一、前端开发要求

### 1.1 组件库选择

**主要组件库**: Element Plus
- **版本**: ^2.5.0
- **安装**: `npm install element-plus`
- **文档**: https://element-plus.org/

**使用原则**:
- ✅ 优先使用 Element Plus 官方组件
- ✅ 统一使用组件库提供的图标 (Element Plus Icons)
- ✅ 遵循组件库的设计规范和交互逻辑
- ✅ 使用组件库提供的主题定制功能

**其他推荐组件库**（根据需要选用）:
| 组件库 | 用途 | 安装命令 |
|--------|------|----------|
| **Element Plus** | 主要UI组件 | `npm install element-plus` |
| **VueUse** | Vue组合式工具 | `npm install @vueuse/core` |
| **ECharts** | 图表组件 | `npm install echarts vue-echarts` |
| **Day.js** | 日期处理 | `npm install dayjs` |

### 1.2 UI/UX 设计规范

**使用 ui-ux-pro-max 技能进行设计**

#### 设计系统生成

在开发每个页面之前，必须使用 ui-ux-pro-max 技能生成设计系统：

```bash
# 生成全局设计系统（必须）
python3 skills/ui-ux-pro-max/scripts/search.py "stock trading dashboard professional fintech" --design-system --persist -p "TradeSentry"

# 为特定页面生成设计（如需要）
python3 skills/ui-ux-pro-max/scripts/search.py "stock market monitoring" --design-system --persist -p "TradeSentry" --page "market-monitor"
```

#### 设计风格要求

**核心风格关键词**:
- **Professional** (专业)
- **Dark Mode** (深色模式，股票交易软件标准)
- **Data-Dense** (信息密集)
- **Real-time** (实时感)
- **Fintech** (金融科技)

**设计原则**:
1. **深色主题为主**: 股票交易软件行业标准，减少视觉疲劳
   - 背景色: `#0F172A` (Slate-900) 或更深深色
   - 卡片背景: `#1E293B` (Slate-800)
   - 主要文字: `#F8FAFC` (Slate-50)
   - 次要文字: `#94A3B8` (Slate-400)

2. **涨跌幅颜色规范**:
   - 上涨: `#EF4444` (红色) - A股市场传统
   - 下跌: `#22C55E` (绿色) - A股市场传统
   - 平盘: `#94A3B8` (灰色)

3. **数据可视化**:
   - 使用 ECharts 实现 K线图、分时图、成交量图
   - 折线图用于趋势展示
   - 柱状图用于板块强度、资金流向
   - 热力图用于市场情绪

4. **字体规范**:
   - 主要字体: `Inter` 或系统默认无衬线字体
   - 数字字体: `Roboto Mono` 等宽字体，便于对齐
   - 字体大小层级:
     - 页面标题: 24-28px
     - 卡片标题: 16-18px
     - 正文: 14px
     - 辅助文字: 12px
     - 价格数字: 16-20px，加粗

#### 布局规范

**整体布局结构**:
```
┌─────────────────────────────────────────────────┐
│  Header (固定顶部, 高度 60px)                      │
├──────────┬──────────────────────────────────────┤
│          │                                      │
│ Sidebar  │         Main Content                 │
│ (固定    │         (自适应宽度)                  │
│  左侧,   │                                      │
│  宽度    │                                      │
│  200px)  │                                      │
│          │                                      │
└──────────┴──────────────────────────────────────┘
```

**卡片设计**:
- 圆角: `border-radius: 8px`
- 背景: 半透明深色 `bg-slate-800/80`
- 边框: 细边框 `border border-slate-700`
- 阴影: 轻微阴影 `shadow-lg`
- 内边距: `padding: 16-20px`

**间距规范**:
- 页面边距: `24px`
- 卡片间距: `16px`
- 元素间距: `8-12px`
- 表格行高: `44px`

#### 交互规范

**悬停效果**:
- 卡片悬停: 轻微上浮 + 边框高亮
- 按钮悬停: 亮度提升 + 阴影加深
- 表格行悬停: 背景色变化

**过渡动画**:
- 时长: `150-300ms`
- 缓动函数: `ease-in-out`
- 使用 CSS transition，避免复杂动画影响性能

**响应式设计**:
- 桌面端: >1280px，展开所有面板
- 平板端: 768-1280px，侧边栏可收起
- 移动端: <768px，使用底部导航栏

### 1.3 组件封装要求

**业务组件列表** (必须封装):

```typescript
// 1. 股票卡片组件
interface StockCardProps {
  code: string;           // 股票代码
  name: string;           // 股票名称
  price: number;          // 当前价格
  changeRate: number;     // 涨跌幅
  isLimitUp: boolean;     // 是否涨停
  boardType: 'first' | 'second' | 'third' | 'high';  // 板类型
  aiScore: number;        // AI评分
  sector: string;         // 所属板块
  onClick: () => void;    // 点击事件
}

// 2. 涨停标识组件
interface LimitUpBadgeProps {
  type: 'first' | 'second' | 'third' | 'high';
  size?: 'small' | 'medium' | 'large';
}

// 3. 评分标签组件
interface ScoreBadgeProps {
  score: number;          // 0-100分
  type: 'ai' | 'sector' | 'strength';
}

// 4. 行情表格组件
interface MarketTableProps {
  data: StockData[];
  columns: ColumnConfig[];
  loading: boolean;
  onRowClick: (row: StockData) => void;
}

// 5. 板块排行组件
interface SectorRankingProps {
  sectors: SectorData[];
  onSectorClick: (sector: SectorData) => void;
}

// 6. 持仓卡片组件
interface PositionCardProps {
  position: Position;
  onSell: () => void;
  onSetStopLoss: () => void;
}

// 7. 风控提醒弹窗
interface RiskAlertProps {
  type: 'stop_loss' | 'take_profit' | 'market_extreme';
  stockCode: string;
  stockName: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}

// 8. 交易表单组件
interface TradeFormProps {
  stockCode: string;
  stockName: string;
  currentPrice: number;
  availableBalance: number;
  maxVolume: number;
  onSubmit: (order: OrderData) => void;
}

// 9. 实时价格组件
interface RealTimePriceProps {
  code: string;
  price: number;
  changeRate: number;
  decimals?: number;
}

// 10. 图表组件
interface StockChartProps {
  code: string;
  type: 'time' | 'kline' | 'volume';
  data: ChartData[];
  height?: number;
}
```

### 1.4 状态管理规范

**使用 Pinia 进行状态管理**:

```typescript
// stores/user.ts
export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    userInfo: null,
    balance: {
      total: 10000,
      available: 10000,
      frozen: 0
    }
  }),
  actions: {
    async login(credentials: LoginCredentials) {
      // 登录逻辑
    },
    async fetchBalance() {
      // 获取资金
    }
  }
})

// stores/market.ts
export const useMarketStore = defineStore('market', {
  state: () => ({
    limitUpStocks: [],
    sectors: [],
    selectedSector: null,
    stockDetail: null
  }),
  actions: {
    async fetchLimitUpStocks() {
      // 获取涨停股票
    },
    async fetchSectors() {
      // 获取板块数据
    }
  }
})

// stores/position.ts
export const usePositionStore = defineStore('position', {
  state: () => ({
    positions: [],
    todayTrades: [],
    riskAlerts: []
  }),
  actions: {
    async fetchPositions() {
      // 获取持仓
    },
    async createOrder(order: OrderData) {
      // 创建订单
    }
  }
})
```

### 1.5 API 调用规范

**使用 axios 封装请求**:

```typescript
// utils/request.ts
import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = useUserStore().token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // 统一错误处理
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default request
```

### 1.6 WebSocket 实时数据

**实现 WebSocket 连接管理**:

```typescript
// composables/useWebSocket.ts
import { ref, onMounted, onUnmounted } from 'vue'

export function useWebSocket(url: string) {
  const ws = ref<WebSocket | null>(null)
  const isConnected = ref(false)
  const lastMessage = ref(null)
  
  const connect = () => {
    ws.value = new WebSocket(url)
    
    ws.value.onopen = () => {
      isConnected.value = true
      console.log('WebSocket connected')
    }
    
    ws.value.onmessage = (event) => {
      lastMessage.value = JSON.parse(event.data)
    }
    
    ws.value.onclose = () => {
      isConnected.value = false
      // 自动重连逻辑
      setTimeout(connect, 3000)
    }
  }
  
  const send = (data: any) => {
    if (ws.value?.readyState === WebSocket.OPEN) {
      ws.value.send(JSON.stringify(data))
    }
  }
  
  const subscribe = (channel: string) => {
    send({ action: 'subscribe', channel })
  }
  
  onMounted(connect)
  onUnmounted(() => ws.value?.close())
  
  return {
    isConnected,
    lastMessage,
    send,
    subscribe
  }
}
```

### 1.7 性能优化要求

1. **虚拟滚动**: 股票列表使用虚拟滚动，一次渲染最多50条
2. **防抖节流**: 搜索输入防抖300ms，窗口调整节流200ms
3. **懒加载**: 图表组件、详情页面使用懒加载
4. **缓存**: 使用 VueUse 的 `useFetch` 缓存非实时数据
5. **代码分割**: 路由级别代码分割，减少首屏加载时间

### 1.8 代码规范

**使用 ESLint + Prettier**:

```bash
# 安装
npm install -D eslint prettier eslint-plugin-vue @vue/eslint-config-typescript

# .eslintrc.js
module.exports = {
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    '@vue/eslint-config-typescript'
  ],
  rules: {
    'vue/multi-word-component-names': 'off',
    'vue/require-default-prop': 'off',
    '@typescript-eslint/no-explicit-any': 'warn'
  }
}

# .prettierrc
{
  "semi": false,
  "singleQuote": true,
  "tabWidth": 2,
  "trailingComma": "es5"
}
```

**命名规范**:
- 组件名: PascalCase (如 `StockCard.vue`)
- 组合式函数: camelCase，以 `use` 开头 (如 `useWebSocket`)
- Store: 以 `use` 开头 + 模块名 + `Store` (如 `useMarketStore`)
- 类型定义: PascalCase，以 `Props`/`Data`/`Config` 结尾

---

## 二、后端开发要求

### 2.1 技术栈

- **框架**: FastAPI (Python 3.10+)
- **数据库**: MySQL 8.0 + SQLAlchemy 2.0
- **缓存**: Redis 7.0
- **消息队列**: Redis List / Celery
- **WebSocket**: FastAPI WebSocket
- **API 文档**: FastAPI 自动生成 Swagger/OpenAPI

### 2.2 项目结构

```
trade-sentry-backend/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI 应用入口
│   ├── config.py               # 配置管理
│   ├── database.py             # 数据库连接
│   ├── redis.py                # Redis 连接
│   ├── models/                 # SQLAlchemy 模型
│   │   ├── __init__.py
│   │   ├── user.py
│   │   ├── position.py
│   │   └── trade.py
│   ├── schemas/                # Pydantic 数据模型
│   │   ├── __init__.py
│   │   ├── user.py
│   │   ├── market.py
│   │   └── trade.py
│   ├── routers/                # API 路由
│   │   ├── __init__.py
│   │   ├── auth.py
│   │   ├── market.py
│   │   ├── trade.py
│   │   └── review.py
│   ├── services/               # 业务逻辑
│   │   ├── __init__.py
│   │   ├── market_service.py
│   │   ├── trade_service.py
│   │   └── risk_service.py
│   ├── tasks/                  # 定时任务
│   │   ├── __init__.py
│   │   ├── market_tasks.py
│   │   └── risk_tasks.py
│   └── utils/                  # 工具函数
│       ├── __init__.py
│       ├── decorators.py
│       └── helpers.py
├── data_fetcher/               # 行情数据获取
│   ├── __init__.py
│   ├── sina_api.py
│   ├── eastmoney_api.py
│   └── data_aggregator.py
├── tests/                      # 测试
│   └── test_api.py
├── alembic/                    # 数据库迁移
├── requirements.txt
└── README.md
```

### 2.3 API 设计规范

**RESTful API 设计**:

```python
# 统一响应格式
{
    "code": 200,           # 状态码
    "message": "success",  # 消息
    "data": { ... }       # 数据
}

# 错误响应格式
{
    "code": 400,
    "message": "参数错误",
    "error": "stock_code is required"
}
```

**路由组织**:

```python
# app/routers/market.py
from fastapi import APIRouter, Depends
from app.services.market_service import MarketService

router = APIRouter(prefix="/api/v1/market", tags=["market"])

@router.get("/stocks")
async def get_stocks(
    sector: str = None,
    board_type: str = None,
    service: MarketService = Depends()
):
    """获取股票列表"""
    return await service.get_stocks(sector, board_type)

@router.get("/limitup")
async def get_limit_up_stocks(service: MarketService = Depends()):
    """获取涨停股票列表"""
    return await service.get_limit_up_stocks()

@router.get("/sectors")
async def get_sectors(service: MarketService = Depends()):
    """获取板块列表"""
    return await service.get_sectors()

@router.websocket("/ws")
async def market_websocket(websocket: WebSocket):
    """WebSocket 实时行情推送"""
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_text()
            # 处理订阅逻辑
            await websocket.send_json({"type": "price_update", "data": {...}})
    except WebSocketDisconnect:
        pass
```

### 2.4 数据库规范

**SQLAlchemy 模型定义**:

```python
# app/models/position.py
from sqlalchemy import Column, Integer, String, Decimal, DateTime, ForeignKey, Enum
from sqlalchemy.orm import relationship
from app.database import Base

class Position(Base):
    __tablename__ = "positions"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), nullable=False)
    total_volume = Column(Integer, nullable=False)
    available_volume = Column(Integer, nullable=False)
    avg_cost = Column(Decimal(10, 4), nullable=False)
    current_price = Column(Decimal(10, 4))
    strategy = Column(Enum("first_board", "second_board", "third_board", "dragon_head"))
    status = Column(Enum("holding", "closed"), default="holding")
    buy_date = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    user = relationship("User", back_populates="positions")
```

**数据库迁移**:

```bash
# 初始化 alembic
alembic init alembic

# 创建迁移脚本
alembic revision --autogenerate -m "init tables"

# 执行迁移
alembic upgrade head
```

### 2.5 行情数据获取

**多数据源聚合**:

```python
# data_fetcher/data_aggregator.py
import asyncio
from typing import Dict, List
from data_fetcher.sina_api import SinaAPI
from data_fetcher.eastmoney_api import EastMoneyAPI

class DataAggregator:
    def __init__(self):
        self.sina = SinaAPI()
        self.eastmoney = EastMoneyAPI()
    
    async def get_stock_price(self, code: str) -> Dict:
        """获取股票实时价格（多源聚合）"""
        # 同时调用多个数据源
        tasks = [
            self.sina.get_price(code),
            self.eastmoney.get_price(code)
        ]
        
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # 数据验证和选择
        valid_results = [r for r in results if not isinstance(r, Exception)]
        if valid_results:
            # 优先选择时间戳最新的数据
            return max(valid_results, key=lambda x: x['timestamp'])
        
        raise Exception("All data sources failed")
    
    async def get_limit_up_stocks(self) -> List[Dict]:
        """获取涨停股票列表"""
        # 东方财富数据更全面
        return await self.eastmoney.get_limit_up_stocks()
```

### 2.6 风控系统实现

**实时风控检查**:

```python
# app/services/risk_service.py
from datetime import datetime
from app.models import Position
from app.redis import redis_client

class RiskService:
    def __init__(self):
        self.stop_loss_rate = -6.0
    
    async def check_stop_loss(self, user_id: int, position: Position):
        """检查是否触发止损"""
        current_price = await self.get_current_price(position.stock_code)
        loss_rate = (current_price - position.avg_cost) / position.avg_cost * 100
        
        if loss_rate <= self.stop_loss_rate:
            # 发送止损提醒
            await self.send_stop_loss_reminder(user_id, position, loss_rate)
            return True
        
        return False
    
    async def send_stop_loss_reminder(self, user_id: int, position: Position, loss_rate: float):
        """发送止损提醒通知"""
        notification = {
            "type": "stop_loss",
            "user_id": user_id,
            "stock_code": position.stock_code,
            "stock_name": position.stock_name,
            "current_price": float(position.current_price),
            "avg_cost": float(position.avg_cost),
            "loss_rate": loss_rate,
            "message": f"止损提醒：{position.stock_name}({position.stock_code}) 已跌破止损线{self.stop_loss_rate}%，建议立即卖出！",
            "timestamp": datetime.now().isoformat()
        }
        
        # 存入Redis，供WebSocket推送
        await redis_client.publish(f"user:{user_id}:notifications", json.dumps(notification))
```

### 2.7 定时任务

**使用 Celery 实现定时任务**:

```python
# app/tasks/market_tasks.py
from celery import Celery
from app.services.market_service import MarketService

celery_app = Celery("trade_sentry", broker="redis://localhost:6379/0")

@celery_app.task
def update_market_data():
    """每3秒更新行情数据"""
    service = MarketService()
    service.update_all_prices()

@celery_app.task
def generate_daily_review():
    """每日盘后生成复盘报告"""
    service = MarketService()
    service.generate_review_report()

# 配置定时任务
celery_app.conf.beat_schedule = {
    "update_market_data": {
        "task": "app.tasks.market_tasks.update_market_data",
        "schedule": 3.0,  # 每3秒
    },
    "generate_daily_review": {
        "task": "app.tasks.market_tasks.generate_daily_review",
        "schedule": "cron(hour=15, minute=30)",  # 每天15:30
    },
}
```

### 2.8 测试要求

**编写单元测试和集成测试**:

```python
# tests/test_api.py
import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_get_limit_up_stocks():
    """测试获取涨停股票接口"""
    response = client.get("/api/v1/market/limitup")
    assert response.status_code == 200
    data = response.json()
    assert "code" in data
    assert "data" in data

def test_create_order():
    """测试创建订单接口"""
    order_data = {
        "stock_code": "000001",
        "stock_name": "平安银行",
        "trade_type": "buy",
        "volume": 100,
        "price": 12.58
    }
    response = client.post("/api/v1/trade/order", json=order_data)
    assert response.status_code == 200
```

---

## 三、前后端协作规范

### 3.1 接口联调

1. **先定义接口文档**: 使用 Swagger/OpenAPI 定义接口
2. **Mock 数据先行**: 前端先用 Mock 数据开发，不依赖后端
3. **接口版本管理**: URL 中包含版本号 `/api/v1/`
4. **错误码统一**: 前后端协商定义统一的错误码体系

### 3.2 数据同步

**实时数据推送**:
- WebSocket 用于实时行情、交易提醒
- HTTP 轮询用于非实时数据（用户数据、历史记录）

**数据一致性**:
- 前端显示数据时显示"最后更新时间"
- 关键操作（下单）前再次确认最新数据
- 使用乐观锁处理并发（version 字段）

### 3.3 部署规范

**环境划分**:
- `development`: 本地开发环境
- `testing`: 测试环境
- `staging`: 预发布环境
- `production`: 生产环境

**配置管理**:
```python
# app/config.py
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # 数据库
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = ""
    mysql_db: str = "trade_sentry"
    
    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    
    # JWT
    jwt_secret: str = "your-secret-key"
    jwt_expire_minutes: int = 60 * 24  # 24小时
    
    class Config:
        env_file = ".env"

settings = Settings()
```

---

## 四、质量检查清单

### 4.1 前端检查项

- [ ] 所有页面使用统一的 Element Plus 组件风格
- [ ] 深色主题正确实现，文字对比度符合 WCAG 标准
- [ ] 涨跌颜色使用 A 股市场红涨绿跌标准
- [ ] 所有按钮和可点击元素有悬停效果
- [ ] 实时数据更新不卡顿，无明显延迟
- [ ] 移动端适配良好，无横向滚动条
- [ ] 图标使用 SVG，不使用 emoji
- [ ] 价格数字使用等宽字体，便于对齐
- [ ] 所有表单有验证和错误提示
- [ ] 无 console.error 和警告信息

### 4.2 后端检查项

- [ ] 所有 API 返回统一格式的响应
- [ ] 数据库连接使用连接池
- [ ] SQL 查询有适当的索引
- [ ] 敏感数据（密码）加密存储
- [ ] API 有适当的权限验证
- [ ] WebSocket 连接有断线重连机制
- [ ] 定时任务有异常处理和日志记录
- [ ] 单元测试覆盖率 > 80%
- [ ] API 文档完整，包含所有参数说明
- [ ] 日志记录完善，便于问题排查

### 4.3 联调检查项

- [ ] 前后端接口完全对齐
- [ ] WebSocket 实时推送正常工作
- [ ] 登录状态跨页面保持
- [ ] 错误提示友好，用户能理解
- [ ] 大数据量（1000+股票）加载不卡顿
- [ ] 高并发场景下系统稳定
- [ ] 容错处理完善，单个接口失败不影响整体

---

## 五、开发流程

### 5.1 开发顺序

**第一阶段：基础框架（2周）**
1. 搭建 Vue3 + Vite 前端项目
2. 搭建 FastAPI 后端项目
3. 设计数据库 Schema
4. 配置 Element Plus 主题

**第二阶段：行情模块（2周）**
1. 接入新浪财经、东方财富 API
2. 实现涨停股票监控页面
3. 实现板块排行功能
4. 实现 WebSocket 实时推送

**第三阶段：交易模块（2周）**
1. 实现手动交易面板
2. 实现持仓管理功能
3. 实现风控提醒系统
4. 实现交易记录功能

**第四阶段：辅助功能（1周）**
1. 实现智能选股功能
2. 实现龙虎榜分析
3. 实现复盘报告
4. 实现情绪周期分析

**第五阶段：优化完善（1周）**
1. 性能优化
2. UI 细节调整
3. Bug 修复
4. 文档完善

### 5.2 提交规范

**Git Commit 规范**:
```
feat: 添加涨停股票监控功能
fix: 修复持仓显示错误
docs: 更新 API 文档
style: 调整表格样式
refactor: 重构行情数据获取逻辑
test: 添加风控模块单元测试
chore: 更新依赖包
```

**分支管理**:
- `master`: 主分支，稳定版本
- `develop`: 开发分支
- `feature/*`: 功能分支
- `hotfix/*`: 紧急修复分支

---

## 六、系统登录账号

账号：admin

密码：123456



**文档版本**: v1.0  
**最后更新**: 2026-02-07  
**编写人**: AI Assistant  

如有疑问，请参考：
- Vue3 文档: https://vuejs.org/
- Element Plus 文档: https://element-plus.org/
- FastAPI 文档: https://fastapi.tiangolo.com/
- ui-ux-pro-max 技能: 项目目录下 `skills/ui-ux-pro-max/`
