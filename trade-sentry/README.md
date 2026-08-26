# TradeSentry 打板龙头战法交易系统

> 基于Vue3 + FastAPI + MySQL/MongoDB/Redis开发的股票交易辅助系统

## 📁 项目结构

```
trade-sentry/
├── frontend/          # Vue3前端项目
│   ├── src/
│   │   ├── api/      # API请求封装
│   │   ├── components/# Vue组件
│   │   ├── layouts/  # 布局组件
│   │   ├── pages/    # 页面组件
│   │   ├── router/   # 路由配置
│   │   ├── stores/   # Pinia状态管理
│   │   ├── styles/   # SCSS样式
│   │   ├── types/    # TypeScript类型
│   │   └── views/    # 视图页面
│   ├── package.json
│   ├── vite.config.ts
│   └── tsconfig.json
│
├── backend/           # FastAPI后端项目
│   ├── app/
│   │   ├── config.py     # 配置管理
│   │   ├── database.py   # 数据库连接
│   │   ├── main.py       # 应用入口
│   │   ├── models/       # 数据库模型
│   │   ├── routers/      # API路由
│   │   ├── schemas/      # Pydantic模型
│   │   ├── services/     # 业务逻辑
│   │   ├── tasks/        # 定时任务
│   │   └── utils/        # 工具函数
│   ├── data_fetcher/     # 行情数据获取
│   ├── tests/            # 测试代码
│   ├── requirements.txt
│   └── .env.example
│
└── docs/              # 项目文档
    ├── 打板龙头战法操作手册.md
    ├── TradeSentry-开发需求说明文档.md
    └── AGENT.MD
```

## 🚀 快速开始

### 环境要求

- **Node.js**: v18+
- **Python**: v3.10+
- **MySQL**: v8.0+
- **MongoDB**: v6.0+
- **Redis**: v7.0+

### 1. 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 开发模式启动
npm run dev

# 构建生产环境
npm run build
```

前端访问地址: http://localhost:3000

### 2. 后端启动

```bash
cd backend

# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件，配置数据库等信息

# 启动服务
python app/main.py
```

后端API地址: http://localhost:8000
API文档: http://localhost:8000/docs

## 📊 功能模块

### 已实现

- ✅ **设计系统**: ui-ux-pro-max技能生成，深色金融风格
- ✅ **前端框架**: Vue3 + TypeScript + Element Plus + Pinia
- ✅ **后端框架**: FastAPI + SQLAlchemy + JWT认证
- ✅ **数据库模型**: User, Position, TradeRecord, RiskLog
- ✅ **API路由**: 
  - 认证 (注册/登录/用户信息)
  - 行情 (涨停股票/板块排行/AI评分)
  - 交易 (创建订单/撤单/订单列表)
  - 持仓 (持仓列表/平仓/统计)
  - 复盘 (每日复盘/交易历史/策略回测)

### 待开发

- 🚧 行情数据获取模块 (新浪财经/东方财富API)
- 🚧 WebSocket实时推送
- 🚧 智能选股算法
- 🚧 风控提醒系统
- 🚧 前端业务页面完善

## 🎨 技术栈

### 前端
- **框架**: Vue3 + Composition API
- **语言**: TypeScript
- **UI库**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router
- **图表**: ECharts
- **样式**: SCSS
- **构建**: Vite

### 后端
- **框架**: FastAPI
- **语言**: Python 3.10+
- **数据库**: MySQL + SQLAlchemy
- **缓存**: Redis
- **文档**: MongoDB
- **认证**: JWT + OAuth2
- **任务**: Celery

## 📡 API接口

### 认证相关
- `POST /api/v1/auth/register` - 用户注册
- `POST /api/v1/auth/login` - 用户登录
- `GET /api/v1/auth/profile` - 获取用户信息
- `PUT /api/v1/auth/profile` - 更新用户信息

### 行情相关
- `GET /api/v1/market/limitup` - 获取涨停股票
- `GET /api/v1/market/sectors` - 获取板块列表
- `POST /api/v1/market/screener/scan` - 智能选股
- `GET /api/v1/market/screener/ai_score` - 获取AI评分
- `GET /api/v1/market/dragon_tiger` - 获取龙虎榜
- `GET /api/v1/market/sentiment` - 获取市场情绪

### 交易相关
- `POST /api/v1/trade/order` - 创建订单
- `DELETE /api/v1/trade/order/{id}` - 撤销订单
- `POST /api/v1/trade/cancel_all` - 一键撤单
- `GET /api/v1/trade/orders` - 获取订单列表

### 持仓相关
- `GET /api/v1/position/list` - 获取持仓列表
- `POST /api/v1/position/close/{id}` - 平仓
- `POST /api/v1/position/close_all` - 一键清仓
- `GET /api/v1/position/balance` - 获取资金信息
- `GET /api/v1/position/statistics` - 获取持仓统计

### 复盘相关
- `GET /api/v1/review/daily` - 每日复盘
- `GET /api/v1/review/trades` - 交易历史
- `GET /api/v1/review/statistics` - 交易统计
- `GET /api/v1/review/backtest` - 策略回测

## ⚠️ 免责声明

**本系统仅供学习交流使用，不构成任何投资建议。**

- 仅使用免费行情API，数据可能存在延迟
- 仅支持手动交易，不提供自动交易功能
- 打板策略风险极高，请谨慎使用
- 投资有风险，入市需谨慎

## 📝 文档

- [打板龙头战法操作手册](打板龙头战法操作手册.md)
- [开发需求说明文档](TradeSentry-开发需求说明文档.md)
- [开发要求文档](AGENT.MD)

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 许可证

MIT License

---

**TradeSentry** - 专业的打板龙头战法交易辅助系统
