# TradeSentry 打板龙头战法交易系统 - 开发需求说明文档

> **版本**: v1.1  
> **更新日期**: 2026-02-07  
> **更新内容**: 仅使用免费API，仅支持手动交易  
> **项目代号**: TradeSentry  
> **目标**: 实现打板+龙头战法手动交易系统  
> **技术栈**: Vue3 + Python + MySQL + MongoDB + Redis  
> **文档日期**: 2026-02-07

---

## 一、项目概述

### 1.1 项目背景
基于《打板+龙头战法操作手册》开发一套专业的高频短线交易系统，支持从行情监控、智能选股、手动交易到复盘分析的全流程辅助工具。

### 1.2 核心目标
- ✅ 实时监控全市场涨停股票
- ✅ 智能识别龙头股和板块效应
- ✅ 支持手动打板交易（仅人工确认后下单）
- ✅ 严格执行风控规则（-6%止损、仓位管理）
- ✅ 自动记录交易并生成复盘报告

### 1.3 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                     前端层 (Vue3)                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ 行情监控  │ │ 交易面板  │ │ 复盘分析  │ │ 系统配置  │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└────────────────────┬────────────────────────────────────────┘
                     │ WebSocket / HTTP
┌────────────────────▼────────────────────────────────────────┐
│                     后端层 (Python FastAPI)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ 行情服务  │ │ 交易引擎  │ │ 风控中心  │ │ 选股引擎  │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                     数据层                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │  MySQL   │ │ MongoDB  │ │  Redis   │ │ 行情API  │        │
│  │ (结构化) │ │ (非结构) │ │ (缓存)   │ │ (第三方) │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、数据库设计规划

### 2.1 MySQL - 结构化数据存储

**核心表设计**:

```sql
-- 1. 用户表 (users)
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    total_balance DECIMAL(15,2) DEFAULT 10000.00,  -- 总资金
    available_balance DECIMAL(15,2) DEFAULT 10000.00,  -- 可用资金
    frozen_balance DECIMAL(15,2) DEFAULT 0.00,  -- 冻结资金
    risk_level ENUM('low', 'medium', 'high') DEFAULT 'high',
    max_position_count INT DEFAULT 3,  -- 最大持仓数量
    stop_loss_rate DECIMAL(5,2) DEFAULT -6.00,  -- 止损线 -6%
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 持仓表 (positions)
CREATE TABLE positions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(50) NOT NULL,
    position_type ENUM('long', 'short') DEFAULT 'long',
    total_volume INT NOT NULL,  -- 总持仓量
    available_volume INT NOT NULL,  -- 可卖数量
    avg_cost DECIMAL(10,4) NOT NULL,  -- 平均成本
    current_price DECIMAL(10,4),  -- 当前价格
    market_value DECIMAL(15,2),  -- 市值
    profit_loss DECIMAL(15,2),  -- 盈亏金额
    profit_loss_rate DECIMAL(8,4),  -- 盈亏比例
    buy_date DATE NOT NULL,
    strategy ENUM('first_board', 'second_board', 'third_board', 'dragon_head', 'bounce_back') NOT NULL,
    status ENUM('holding', 'closed') DEFAULT 'holding',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_stock (user_id, stock_code),
    INDEX idx_status (status)
);

-- 3. 交易记录表 (trade_records)
CREATE TABLE trade_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(50) NOT NULL,
    trade_type ENUM('buy', 'sell') NOT NULL,
    trade_mode ENUM('manual') NOT NULL,  -- 仅支持手动交易
    order_type ENUM('market', 'limit') NOT NULL,  -- 市价/限价
    volume INT NOT NULL,
    price DECIMAL(10,4) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    commission DECIMAL(10,2) DEFAULT 0,  -- 佣金
    tax DECIMAL(10,2) DEFAULT 0,  -- 印花税
    total_cost DECIMAL(15,2) NOT NULL,  -- 总成本
    strategy ENUM('sweep_board', 'queue_board', 'dragon_second_wave') NOT NULL,
    board_type ENUM('first', 'second', 'third', 'high') DEFAULT 'first',
    trade_date DATE NOT NULL,
    trade_time TIME NOT NULL,
    status ENUM('pending', 'filled', 'partial_filled', 'cancelled', 'failed') DEFAULT 'pending',
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_date (user_id, trade_date),
    INDEX idx_stock_date (stock_code, trade_date)
);

-- 4. 风控记录表 (risk_logs)
CREATE TABLE risk_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    risk_type ENUM('stop_loss', 'position_limit', 'market_fuse', 'board_failure', 'manual_stop') NOT NULL,
    stock_code VARCHAR(10),
    trigger_price DECIMAL(10,4),
    trigger_condition TEXT NOT NULL,
    action_taken TEXT NOT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_time (user_id, executed_at)
);

-- 5. 系统配置表 (system_config)
CREATE TABLE system_config (
    id INT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 6. 交易时段配置 (trading_sessions)
CREATE TABLE trading_sessions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    session_name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    session_type ENUM('pre_market', 'morning', 'afternoon', 'after_hours') NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);
```

### 2.2 MongoDB - 非结构化数据存储

**Collections设计**:

```javascript
// 1. 实时行情数据 (market_data)
{
    _id: ObjectId,
    timestamp: ISODate(),
    stock_code: "000001",
    stock_name: "平安银行",
    current_price: 12.58,
    open_price: 12.30,
    high_price: 12.80,
    low_price: 12.20,
    prev_close: 12.30,
    volume: 1258000,
    amount: 15800000,
    bid_ask: {
        bid1: {price: 12.57, volume: 1000},
        ask1: {price: 12.58, volume: 2000}
    },
    indicators: {
        change_rate: 2.28,  // 涨跌幅
        turnover_rate: 3.5,  // 换手率
        volume_ratio: 4.2   // 量比
    },
    is_limit_up: true,  // 是否涨停
    limit_up_price: 12.80,
    limit_down_price: 11.07,
    board_type: "first",  // 首板/二板/三板
    sector: "银行",
    created_at: ISODate()
}

// 2. 涨停股票池 (limit_up_stocks)
{
    _id: ObjectId,
    date: "2026-02-07",
    stocks: [
        {
            stock_code: "000001",
            stock_name: "平安银行",
            board_type: "first",  // 首板
            limit_up_time: "09:35:28",  // 涨停时间
            open_count: 0,  // 炸板次数
            sector: "银行",
            sector_strength: 8.5,  // 板块强度 0-10
            is_dragon_head: false,
            volume_ratio: 5.2,
            turnover_rate: 8.5,
            market_cap: 2500000000,  // 流通市值
            reason: "业绩预增",
            tags: ["首板", "业绩"],
            ai_score: 8.2  // AI评分
        }
    ],
    updated_at: ISODate()
}

// 3. 板块数据 (sectors)
{
    _id: ObjectId,
    timestamp: ISODate(),
    sector_name: "人工智能",
    sector_code: "AI001",
    change_rate: 5.8,
    leading_stock: "000938",  // 龙头股
    stocks_count: 150,
    limit_up_count: 15,  // 涨停家数
    avg_turnover: 8.5,
    strength_score: 9.2,  // 强度评分
    top_stocks: ["000938", "000063", "000977"],
    created_at: ISODate()
}

// 4. 龙虎榜数据 (dragon_tiger_list)
{
    _id: ObjectId,
    date: "2026-02-07",
    stock_code: "000001",
    stock_name: "平安银行",
    data_type: "buy",  // buy/sell
    details: [
        {
            seat_name: "华泰证券深圳益田路",
            seat_type: "retail",  // retail/institution/hot_money
            buy_amount: 15800000,
            sell_amount: 5200000,
            net_amount: 10600000,
            is_famous: true,
            famous_name: "章盟主"
        }
    ],
    summary: {
        total_buy: 52000000,
        total_sell: 38000000,
        institution_buy: 12000000,
        institution_sell: 8000000
    }
}

// 5. 交易日志 (trade_logs)
{
    _id: ObjectId,
    timestamp: ISODate(),
    user_id: 1,
    log_type: "order_placed",  // order_placed/order_filled/risk_triggered
    stock_code: "000001",
    message: "打板买入平安银行500股",
    metadata: {
        order_id: "ORD20260207001",
        price: 12.80,
        volume: 500,
        strategy: "sweep_board"
    }
}

// 6. 情绪周期数据 (sentiment_cycle)
{
    _id: ObjectId,
    date: "2026-02-07",
    timestamp: ISODate(),
    up_count: 2800,  // 上涨家数
    down_count: 1200,  // 下跌家数
    limit_up_count: 85,  // 涨停家数
    limit_down_count: 5,  // 跌停家数
    max_board_height: 6,  // 最高连板数
    sentiment_score: 7.5,  // 情绪评分 0-10
    cycle_phase: "high_tide",  // 冰点/复苏/高潮/退潮
    market_temperature: "hot"  // cold/warm/hot
}
```

### 2.3 Redis - 缓存和实时数据

**Key设计**:

```
# 1. 实时价格缓存 (TTL: 5秒)
market:price:{stock_code} -> "{current_price}:{timestamp}"

# 2. 涨停股票列表 (Hash)
limitup:today -> {stock_code: board_type}

# 3. 用户持仓缓存 (Hash)
user:{user_id}:positions -> {stock_code: json_position_data}

# 4. 交易信号队列 (List)
signals:buy -> [signal1, signal2, ...]
signals:sell -> [signal1, signal2, ...]

# 5. 风控触发标志 (String)
risk:stoploss:{user_id}:{stock_code} -> "triggered"

# 6. WebSocket连接管理 (Set)
ws:connections:{channel} -> [conn_id1, conn_id2, ...]

# 7. 系统状态 (String)
system:trading_mode -> "manual"
system:market_status -> "open/closed"

# 8. 用户会话 (Hash)
session:{token} -> {user_id, login_time, permissions}

# 9. 行情订阅 (Set)
subscriptions:market:{stock_code} -> [user_id1, user_id2, ...]

# 10. 交易频率限制 (String with expiration)
rate_limit:trade:{user_id} -> "count:timestamp"
```

---

## 三、功能模块详细需求

### 3.1 行情监控模块

#### 3.1.1 实时行情数据接入

**功能描述**: 
- 接入第三方免费行情API（新浪、东方财富、腾讯财经等）
- 实时推送股票价格、成交量、买卖盘等数据
- 支持沪深A股全市场监控

**技术需求**:
```python
# 数据接入频率
- 价格数据: 每3秒推送一次
- 成交明细: 实时推送
- 盘口数据: 每5秒推送一次
- 板块数据: 每30秒推送一次
```

**数据字段**:
- 股票代码、名称、当前价、开盘价、最高价、最低价
- 涨跌幅、换手率、量比、成交量、成交额
- 买卖五档盘口
- 涨停价、跌停价

#### 3.1.2 涨停股票监控

**功能需求**:
1. **自动识别涨停股票**
   - 实时监控所有股票是否达到涨停价
   - 区分首板、二板、三板、高位板
   - 记录涨停时间、封单金额、炸板次数

2. **涨停强度计算**
   ```
   强度评分 = (封单金额 / 流通市值) * 100 * 时间系数
   - 时间系数: 早盘涨停=1.5，午盘=1.2，尾盘=1.0
   - 炸板扣分: 每次炸板-10分
   ```

3. **炸板预警**
   - 监控封单金额变化（每分钟减少>30%预警）
   - 监控成交额异常（放量>前日150%）
   - 推送炸板预警通知

#### 3.1.3 板块效应监控

**功能需求**:
1. **实时板块排名**
   - 按涨跌幅排序
   - 显示板块内涨停家数
   - 显示板块龙头

2. **板块强度评分**
   ```
   板块强度 = (涨停家数 / 总家数) * 50 + 龙头股高度 * 10 + 成交额增长率 * 20
   评分范围: 0-10分
   - 0-3分: 弱势，不参与
   - 3-6分: 一般，谨慎参与
   - 6-8分: 强势，积极参与
   - 8-10分: 超强，重点参与
   ```

3. **板块联动分析**
   - 识别领涨板块和跟风板块
   - 显示板块内个股关联度
   - 板块资金流向监控

#### 3.1.4 龙头股识别

**识别算法**:
```python
def identify_dragon_head(stock, sector):
    score = 0
    
    # 1. 连板高度 (30分)
    if stock['consecutive_boards'] >= 3:
        score += 30
    elif stock['consecutive_boards'] == 2:
        score += 20
    else:
        score += 10
    
    # 2. 涨停时间 (20分)
    if stock['limit_up_time'] < '10:00:00':
        score += 20
    elif stock['limit_up_time'] < '13:00:00':
        score += 15
    else:
        score += 10
    
    # 3. 封单强度 (20分)
    seal_ratio = stock['seal_amount'] / stock['market_cap']
    if seal_ratio > 0.01:  # 封单>1%流通市值
        score += 20
    elif seal_ratio > 0.005:
        score += 15
    else:
        score += 10
    
    # 4. 板块地位 (20分)
    if stock['is_sector_leader']:
        score += 20
    elif stock['is_sector_pioneer']:
        score += 15
    
    # 5. 成交额 (10分)
    if 300000000 < stock['amount'] < 2000000000:
        score += 10
    
    return score >= 70  # 70分以上认定为龙头
```

---

### 3.2 智能选股模块

#### 3.2.1 量化选股引擎

**选股条件配置**:

| 条件项 | 默认值 | 可调范围 | 说明 |
|--------|--------|----------|------|
| 股票类型 | 非ST | 非ST/ST | 是否包含ST股 |
| 板块 | 全部 | 单选/多选 | 指定板块筛选 |
| 流通市值 | 30-200亿 | 10-500亿 | 流通市值范围 |
| 股价 | 2-50元 | 1-100元 | 股价范围 |
| 换手率 | >3% | 0-30% | 昨日换手率 |
| 量比 | >3 | 0-20 | 集合竞价量比 |
| 净利润 | >0 | -∞ to +∞ | 净利润要求 |
| 营收 | >1亿 | 0-100亿 | 营收要求 |

**选股模式**:

1. **首板筛选模式**
   ```
   条件:
   - 今日首次涨停
   - 昨日无涨停记录
   - 成交额3-20亿
   - 换手率>3%
   - 量比>3
   - 突破左侧压力位
   
   排序: 涨停时间早优先，封单金额大优先
   ```

2. **二板筛选模式**
   ```
   条件:
   - 昨日首板，今日二板
   - 昨日成交额3-20亿
   - 今日高开3-6%
   - 板块内排名前三
   
   排序: 高开幅度适中(4-5%)，封单强
   ```

3. **三板筛选模式**
   ```
   条件:
   - 连续三板
   - 有板块效应支撑
   - 非一字板（有换手）
   - 板块龙头地位明确
   
   风险: 高位板风险大，谨慎参与
   ```

4. **龙头股第二波模式**
   ```
   条件:
   - 历史最高连板>=4板
   - 断板3-7天
   - 回调幅度15-30%
   - 缩量企稳（成交量<峰值30%）
   - 重新涨停（二波启动）
   ```

#### 3.2.2 AI评分系统

**评分维度**:
```python
def calculate_ai_score(stock):
    score = 0
    
    # 1. 技术面 (25分)
    tech_score = 0
    if stock['breakout']:
        tech_score += 10
    if stock['volume_increase'] > 1.5:
        tech_score += 10
    if stock['price_trend'] == 'up':
        tech_score += 5
    score += tech_score
    
    # 2. 基本面 (20分)
    fund_score = 0
    if stock['pe_ratio'] < 30:
        fund_score += 10
    if stock['revenue_growth'] > 0.2:
        fund_score += 10
    score += fund_score
    
    # 3. 资金面 (25分)
    capital_score = 0
    if stock['main_force_inflow'] > 0:
        capital_score += 15
    if stock['retail_outflow'] > 0:
        capital_score += 10
    score += capital_score
    
    # 4. 情绪面 (20分)
    senti_score = 0
    if stock['sector_strength'] > 7:
        senti_score += 10
    if stock['market_sentiment'] == 'high':
        senti_score += 10
    score += senti_score
    
    # 5. 龙虎榜 (10分)
    dragon_score = 0
    if stock['has_hot_money']:
        dragon_score += 10
    score += dragon_score
    
    return score  # 0-100分
```

**评分结果应用**:
- 90-100分: 强烈推荐，优先打板
- 80-89分: 推荐，可以打板
- 70-79分: 一般，谨慎参与
- <70分: 不推荐

#### 3.2.3 龙虎榜分析

**功能需求**:
1. **游资追踪**
   - 知名游资名单管理（章盟主、赵老哥等）
   - 游资席位买入/卖出监控
   - 游资操作风格分析

2. **席位类型识别**
   ```
   席位类型:
   - 机构席位: 买入=利好，持续买入=强烈看好
   - 游资席位: 跟随知名游资操作
   - 散户席位: 拉萨天团等，避免接盘
   - 量化席位: 注意砸盘风险
   ```

3. **龙虎榜选股信号**
   - 机构大买: 次日高开概率高
   - 游资合力: 多家游资共同买入，成功率高
   - 散户接盘: 拉萨席位主导，次日低开概率高

---

### 3.3 交易执行模块

#### 3.3.1 打板交易系统

**交易模式**:

1. **扫板模式（主动买入）**
   ```
   触发条件:
   - 股价涨至8-9%
   - 大单持续买入
   - 即将触及涨停价
   
   操作:
   - 以卖一价直接买入
   - 预设买入仓位
   - 确认买入后记录
   ```

2. **排板模式（排队等待）**
   ```
   触发条件:
   - 股票刚封板
   - 封单金额>1000万
   - 封单量持续增长
   
   操作:
   - 以涨停价挂单
   - 排队等待成交
   - 可随时撤单
   ```

3. **回封板模式**
   ```
   触发条件:
   - 股票炸板后再次封板
   - 回封时间<5分钟
   - 封单强度比之前更强
   
   操作:
   - 回封瞬间买入或排板
   - 风险相对较低
   ```

**订单管理**:
- 支持限价单、市价单
- 支持部分成交处理
- 支持撤单、改单
- 订单状态实时推送

#### 3.3.2 交易提醒与辅助

**系统不提供自动交易功能**，仅提供以下辅助功能帮助用户决策：

**交易提醒**:
```
功能:
1. 系统识别到符合条件的打板机会
2. 推送交易提醒通知（显示股票信息、评分、风险）
3. 用户收到提醒后，手动进入交易面板操作
4. 用户可以设置是否接收某类提醒

提醒类型:
- 首板机会提醒
- 二板机会提醒  
- 龙头股二波提醒
- 炸板预警提醒
- 止盈止损提醒
```

**注意**: 所有交易操作必须由用户手动确认并执行，系统不会自动下单。

#### 3.3.3 卖出策略提醒

**四阶段卖出策略提醒**:

系统根据操作手册的四阶段卖出法，在对应时间点推送卖出提醒，用户收到提醒后需**手动执行**卖出操作：

```
阶段提醒:
1. 09:30 开盘提醒 - 建议卖出50%（高开时）
2. 09:48-11:28 午盘前提醒 - 小市值建议清仓，大市值建议减仓
3. 13:18 午后提醒 - 未封板股票建议清仓
4. 14:48 尾盘提醒 - 非涨停股票建议强制清仓

提醒内容示例:
"[卖出提醒] 您的持仓股 000001 平安银行 当前价格12.58元，建议卖出50%（500股）"
```

**条件提醒设置**:
用户可设置以下条件，当条件触发时系统推送提醒：
- 止盈提醒: 达到+5%, +8%, +10%时提醒
- 止损提醒: 达到-6%时提醒（强烈建议设置）
- 回落提醒: 从高点回落-3%时提醒
- 炸板提醒: 持仓股炸板时立即提醒

**重要提示**: 所有卖出操作必须由用户手动确认并执行，系统仅提供提醒服务。

#### 3.3.4 做T功能

**正T（先买后卖）**:
```
触发条件:
- 持仓股早盘低开-3%以下
- 板块整体强势
- 有反弹迹象

操作:
- 以低价买入
- 设置+3%以上卖出原仓位
- 收盘持仓数量不变
```

**反T（先卖后买）**:
```
触发条件:
- 持仓股高开+5%以上
- 有冲高回落迹象

操作:
- 先卖出持仓
- 设置-2%以下买回
- 收盘持仓数量不变
```

---

### 3.4 风控管理模块

#### 3.4.1 个股风控

**止损提醒系统**:
```python
class StopLossReminder:
    def __init__(self, user_id):
        self.user_id = user_id
        self.stop_loss_rate = self.get_user_stop_loss_rate()  # -6%
    
    def check_stop_loss(self, position):
        current_loss_rate = (position.current_price - position.avg_cost) / position.avg_cost * 100
        
        if current_loss_rate <= self.stop_loss_rate:
            # 触发止损提醒
            self.send_stop_loss_reminder(position)
            return {
                'triggered': True,
                'stock_code': position.stock_code,
                'loss_rate': current_loss_rate,
                'action': '建议立即卖出'
            }
        return {'triggered': False}
    
    def send_stop_loss_reminder(self, position):
        # 发送止损提醒通知
        notification = {
            'type': 'stop_loss',
            'stock_code': position.stock_code,
            'stock_name': position.stock_name,
            'current_price': position.current_price,
            'avg_cost': position.avg_cost,
            'loss_rate': self.stop_loss_rate,
            'message': f'止损提醒：{position.stock_name}({position.stock_code}) 已跌破止损线{self.stop_loss_rate}%，建议立即卖出！',
            'timestamp': datetime.now()
        }
        self.push_notification(self.user_id, notification)
        
        # 记录风控日志
        self.log_risk_event(
            risk_type='stop_loss_reminder',
            stock_code=position.stock_code,
            trigger_price=position.current_price,
            loss_rate=self.stop_loss_rate
        )
```

**风控提醒规则**:
1. **硬性止损提醒**: -6%立即推送止损提醒（用户需手动执行）
2. **时间止损提醒**: 买入后3天未盈利，次日开盘前推送卖出建议
3. **逻辑止损提醒**: 龙头股地位丧失，推送减仓建议

**重要提示**: 系统仅提供风控提醒，所有卖出操作必须由用户手动确认并执行。

#### 3.4.2 仓位风控

**仓位管理规则**:
```python
class PositionManager:
    def __init__(self, user_id):
        self.user_id = user_id
        self.total_balance = self.get_user_balance()
        self.max_positions = self.get_user_max_positions()  # 最大持仓数
        self.single_position_ratio = 0.5  # 单票最大50%
    
    def can_open_position(self, stock_code, amount):
        # 1. 检查持仓数量
        current_positions = self.get_current_positions()
        if len(current_positions) >= self.max_positions:
            return False, f"已达到最大持仓数{self.max_positions}"
        
        # 2. 检查是否已持有该股票
        if stock_code in [p.stock_code for p in current_positions]:
            return False, "已持有该股票，不允许重复持仓"
        
        # 3. 检查单票仓位
        if amount > self.total_balance * self.single_position_ratio:
            return False, f"单票仓位不能超过{self.single_position_ratio*100}%"
        
        # 4. 检查可用资金
        available = self.get_available_balance()
        if amount > available:
            return False, "可用资金不足"
        
        return True, "可以开仓"
```

#### 3.4.3 系统性风控

**市场极端行情提醒机制**:
```python
class MarketExtremeReminder:
    def check_market_extreme(self):
        # 获取市场数据
        market_data = self.get_market_data()
        
        # 极端行情条件1: 板块平均跌幅>6%
        sector_avg_change = market_data['sector_avg_change']
        if sector_avg_change < -6:
            return self.send_extreme_reminder('sector_drop', sector_avg_change)
        
        # 极端行情条件2: 跌停家数>100家
        limit_down_count = market_data['limit_down_count']
        if limit_down_count > 100:
            return self.send_extreme_reminder('mass_limit_down', limit_down_count)
        
        # 极端行情条件3: 涨停家数<50家且跌停>30家
        limit_up_count = market_data['limit_up_count']
        if limit_up_count < 50 and limit_down_count > 30:
            return self.send_extreme_reminder('market_cold', None)
        
        return {'extreme': False}
    
    def send_extreme_reminder(self, extreme_type, value):
        # 发送市场极端行情提醒
        messages = {
            'sector_drop': f'市场极端提醒：板块平均跌幅{value}%，建议停止买入，考虑减仓！',
            'mass_limit_down': f'市场极端提醒：跌停家数{value}家，市场恐慌情绪严重，建议清仓观望！',
            'market_cold': '市场极端提醒：市场冰点，涨停家数不足50家且跌停超过30家，建议空仓！'
        }
        
        notification = {
            'type': 'market_extreme',
            'extreme_type': extreme_type,
            'value': value,
            'message': messages.get(extreme_type, '市场出现极端行情，请谨慎操作'),
            'suggestion': '建议：暂停买入操作，考虑减仓或清仓',
            'timestamp': datetime.now()
        }
        
        # 发送通知给所有在线用户
        self.broadcast_notification(notification)
        
        return {'extreme': True, 'type': extreme_type, 'value': value}
```

**炸板提醒**:
```
规则:
- 记录当日炸板次数
- 炸板达到2次，推送"建议暂停当日新开仓"提醒
- 炸板达到3次，推送"建议次日停止交易"提醒
- 所有提醒仅供参考，用户自行决定是否执行
```

**重要提示**: 系统仅提供市场极端行情提醒，不会自动停止交易或清仓，所有操作必须由用户手动确认并执行。

#### 3.4.4 特殊风控

**价格过滤**:
- 禁止交易<2元股票
- 禁止交易>50元股票
- 禁止交易ST股票
- 禁止交易科创板股票（688开头）

**时间限制**:
- 14:50后禁止开新仓
- 11:30-13:00休市期间不交易
- 非交易日禁止交易

**频率限制**:
```
限制规则:
- 每分钟最多下5笔订单
- 每日最多交易10只股票
- 同一股票30秒内不能重复下单
```

---

### 3.5 复盘分析模块

#### 3.5.1 每日自动复盘

**复盘报告生成**:
```python
class DailyReviewReport:
    def generate_report(self, date):
        report = {
            'date': date,
            'summary': {},
            'trades': [],
            'statistics': {},
            'recommendations': []
        }
        
        # 1. 市场概况
        report['summary']['market'] = {
            'index_change': self.get_index_change(),
            'up_count': self.get_up_count(),
            'down_count': self.get_down_count(),
            'limit_up_count': self.get_limit_up_count(),
            'limit_down_count': self.get_limit_down_count(),
            'sentiment': self.get_market_sentiment()
        }
        
        # 2. 涨停分析
        report['summary']['limit_up_analysis'] = {
            'first_board': self.count_board_type('first'),
            'second_board': self.count_board_type('second'),
            'third_board': self.count_board_type('third'),
            'high_board': self.count_board_type('high'),
            'avg_limit_up_time': self.calculate_avg_limit_up_time(),
            'max_board_height': self.get_max_board_height()
        }
        
        # 3. 板块分析
        report['summary']['sectors'] = self.analyze_sectors()
        
        # 4. 龙虎榜分析
        report['summary']['dragon_tiger'] = self.analyze_dragon_tiger()
        
        # 5. 个人交易分析
        report['trades'] = self.get_user_trades(date)
        report['statistics'] = self.calculate_statistics(date)
        
        # 6. 明日建议
        report['recommendations'] = self.generate_recommendations()
        
        return report
```

**复盘内容**:
1. **市场概况**: 指数涨跌、涨跌家数、涨停跌停数
2. **涨停分析**: 首板/二板/三板数量、平均涨停时间、最高连板
3. **板块分析**: 热点板块、板块强度、龙头股
4. **龙虎榜**: 游资动向、机构介入、散户行为
5. **个人交易**: 成交记录、盈亏分析、策略执行
6. **情绪周期**: 市场温度、情绪评分、周期阶段

#### 3.5.2 龙虎榜分析工具

**游资追踪功能**:
- 知名游资买卖记录查询
- 游资操作风格统计（胜率、平均收益、持仓时间）
- 游资偏好板块和股票类型
- 游资联动分析（哪些游资经常一起操作）

**席位分析**:
- 机构席位买入/卖出统计
- 散户席位（拉萨天团）监控
- 量化席位识别

#### 3.5.3 情绪周期分析

**情绪指标计算**:
```python
def calculate_sentiment_index(date):
    sentiment = 0
    
    # 1. 涨跌家数比 (权重30%)
    up_count = get_up_count(date)
    down_count = get_down_count(date)
    ratio_score = (up_count / (up_count + down_count)) * 30
    sentiment += ratio_score
    
    # 2. 涨停跌停比 (权重25%)
    limit_up = get_limit_up_count(date)
    limit_down = get_limit_down_count(date)
    if limit_down == 0:
        limit_score = 25
    else:
        limit_score = min((limit_up / limit_down) * 5, 25)
    sentiment += limit_score
    
    # 3. 连板高度 (权重20%)
    max_height = get_max_board_height(date)
    height_score = min(max_height * 3, 20)
    sentiment += height_score
    
    # 4. 炸板率 (权重15%)
    opened_count = get_opened_board_count(date)
    total_limit_up = get_limit_up_count(date)
    if total_limit_up > 0:
        open_rate = opened_count / total_limit_up
        open_score = (1 - open_rate) * 15
        sentiment += open_score
    
    # 5. 赚钱效应 (权重10%)
    avg_profit = get_average_profit_rate(date)
    profit_score = max(0, min(avg_profit * 5, 10))
    sentiment += profit_score
    
    return sentiment  # 0-100分
```

**周期阶段判断**:
- **冰点期**: 情绪指数<30，上涨家数<1000，建议空仓
- **复苏期**: 情绪指数30-50，上涨家数1000-2000，小仓位试错
- **高潮期**: 情绪指数50-80，上涨家数>3000，积极打板
- **退潮期**: 情绪指数从高位回落，建议收缩战线

#### 3.5.4 策略回测

**回测功能**:
- 选择策略（首板/二板/龙头）
- 选择回测时间段
- 设置初始资金
- 运行回测
- 生成回测报告（收益率、最大回撤、胜率、盈亏比）

**回测报告**:
```
回测结果:
- 回测区间: 2025-01-01 至 2025-12-31
- 初始资金: 10,000元
- 期末资金: 15,680元
- 总收益率: 56.8%
- 年化收益: 56.8%
- 最大回撤: -18.5%
- 胜率: 65.3%
- 盈亏比: 1.8:1
- 交易次数: 120次
- 平均持仓天数: 1.2天
```

---

### 3.6 用户管理模块

#### 3.6.1 用户认证与权限

**功能需求**:
- 用户注册/登录/登出
- JWT Token认证
- 密码加密存储（bcrypt）
- 密码重置功能
- 用户权限管理（管理员/普通用户）

**安全机制**:
- 登录失败次数限制（5次失败后锁定30分钟）
- IP白名单（可选）
- 双因素认证（可选）

#### 3.6.2 资金管理

**功能需求**:
- 资金账户查询（总资产、可用资金、冻结资金）
- 充值/提现记录（对接第三方支付）
- 资金变动明细
- 盈亏统计（日/周/月/年）

#### 3.6.3 个人设置

**可配置项**:
- 总资金设置
- 单票仓位上限（默认50%）
- 止损线设置（默认-6%）
- 最大持仓数（默认3只）
- 交易提醒设置（买入/卖出提醒）
- 通知设置（邮件/短信/APP推送）

---

### 3.7 行情数据服务模块

#### 3.7.1 行情数据接入

**数据源**（仅使用免费API）:
- 新浪财经API（实时行情、盘口数据）
- 东方财富API（行情数据、板块数据）
- 腾讯财经API（行情数据）
- 新浪财经个股页面爬虫（龙虎榜数据）

**数据类型**:
- 实时行情（价格、成交量、盘口）
- 历史K线数据
- 成交明细
- 板块数据
- 龙虎榜数据

#### 3.7.2 数据存储与缓存

**存储策略**:
```
实时数据（Redis）:
- 最新价格: 过期时间5秒
- 盘口数据: 过期时间10秒
- 涨停列表: 过期时间60秒

历史数据（MongoDB）:
- 分钟级数据: 保留3个月
- 日K数据: 永久保存
- 成交明细: 保留1个月

结构化数据（MySQL）:
- 交易记录: 永久保存
- 用户数据: 永久保存
- 系统配置: 永久保存
```

#### 3.7.3 WebSocket实时推送

**推送内容**:
- 股票价格变动
- 涨停炸板提醒
- 交易成交通知
- 风控触发通知
- 系统消息

**推送频率**:
- 价格数据: 每3秒
- 盘口数据: 每5秒
- 板块数据: 每30秒
- 成交明细: 实时

---

### 3.8 系统管理模块

#### 3.8.1 系统监控

**监控指标**:
- 服务器CPU/内存使用率
- 数据库连接数
- Redis内存使用情况
- API响应时间
- 交易延迟
- WebSocket连接数

#### 3.8.2 日志管理

**日志类型**:
- 系统日志（INFO/WARNING/ERROR）
- 交易日志（所有交易操作）
- 用户行为日志
- 风控日志
- 行情数据日志

**日志存储**:
- 最近7天日志存储在本地
- 历史日志归档到MongoDB
- 错误日志实时告警

#### 3.8.3 系统配置

**可配置项**:
- 交易时段设置
- 市场状态（开/关）
- 交易手续费率
- 数据源配置
- 风控参数调整
- 通知服务配置

---

## 四、前端界面设计规划

### 4.1 页面结构

```
/layout
├── /Header（顶部导航）
│   ├── Logo
│   ├── 市场状态指示器（开盘/休市/收盘）
│   ├── 资金概览（总资产/可用/盈亏）
│   ├── 通知中心
│   └── 用户菜单
│
├── /Sidebar（侧边栏导航）
│   ├── 行情监控
│   ├── 智能选股
│   ├── 交易面板
│   ├── 持仓管理
│   ├── 复盘分析
│   ├── 龙虎榜
│   ├── 系统设置
│   └── 退出登录
│
└── /MainContent（主内容区）
    ├── 各功能页面
    └── 弹窗、通知等
```

### 4.2 主要页面设计

#### 4.2.1 行情监控页面

**布局**:
```
┌─────────────────────────────────────────────────────┐
│ 市场概览卡片（指数涨跌、涨跌家数、涨停跌停数）          │
├─────────────────────────────────────────────────────┤
│ ┌──────────────────┐  ┌──────────────────────────┐  │
│ │  涨停股票列表     │  │      板块排行             │  │
│ │  - 首板/二板/三板 │  │  - 板块名称               │  │
│ │  - 涨停时间      │  │  - 涨跌幅                 │  │
│ │  - 封单金额      │  │  - 涨停家数               │  │
│ │  - 强度评分      │  │  - 板块龙头               │  │
│ │  - AI评分        │  │  - 强度评分               │  │
│ │  - 操作按钮      │  │                           │  │
│ └──────────────────┘  └──────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│ 炸板预警列表（实时更新）                              │
└─────────────────────────────────────────────────────┘
```

**功能**:
- 实时推送涨停股票
- 按板类型筛选（首板/二板/三板）
- 按板块筛选
- 快速跳转交易按钮（跳转到手动交易面板）
- 炸板预警提醒

#### 4.2.2 智能选股页面

**布局**:
```
┌─────────────────────────────────────────────────────┐
│ 选股条件配置面板                                      │
│ - 板块选择          - 市值范围                       │
│ - 股价范围          - 换手率                         │
│ - 量比             - 基本面筛选                      │
├─────────────────────────────────────────────────────┤
│ 选股模式选择（首板/二板/三板/龙头二波）               │
├─────────────────────────────────────────────────────┤
│ 选股结果列表                                         │
│ - 股票代码/名称      - 当前价格                      │
│ - 涨跌幅            - AI评分                         │
│ - 板块             - 推荐理由                        │
│ - 操作按钮                                         │
└─────────────────────────────────────────────────────┘
```

**功能**:
- 自定义选股条件
- AI评分展示
- 一键添加自选股
- 快速交易跳转按钮
- 选股结果导出

#### 4.2.3 交易面板页面

**布局**:
```
┌─────────────────────────────────────────────────────┐
│ 交易模式选择（手动/半自动/全自动）                    │
├─────────────────────────────────────────────────────┤
│ ┌──────────────────────┐  ┌──────────────────────┐  │
│ │     买入面板          │  │      卖出面板         │  │
│ │  - 股票代码输入       │  │  - 持仓股票列表       │  │
│ │  - 价格输入          │  │  - 可卖数量           │  │
│ │  - 数量输入          │  │  - 卖出价格           │  │
│ │  - 扫板/排板选择     │  │  - 卖出数量           │  │
│ │  - 买入按钮          │  │  - 卖出按钮           │  │
│ └──────────────────────┘  └──────────────────────┘  │
├─────────────────────────────────────────────────────┤
│ 订单列表（待成交/已成交/已撤销）                      │
├─────────────────────────────────────────────────────┤
│ 快速交易按钮（一键清仓/一键撤单）                     │
└─────────────────────────────────────────────────────┘
```

**功能**:
- 手动下单（限价/市价）
- 扫板/排板模式切换
- 实时订单状态更新
- 一键撤单
- 一键清仓

#### 4.2.4 持仓管理页面

**布局**:
```
┌─────────────────────────────────────────────────────┐
│ 持仓概览卡片                                         │
│ - 总持仓市值    - 今日盈亏    - 总盈亏               │
├─────────────────────────────────────────────────────┤
│ 持仓列表                                            │
│ - 股票代码/名称  - 持仓数量  - 成本价  - 现价        │
│ - 盈亏金额     - 盈亏比例  - 操作建议               │
├─────────────────────────────────────────────────────┤
│ 卖出条件单设置                                       │
│ - 止盈价格    - 止损价格    - 回落卖出               │
└─────────────────────────────────────────────────────┘
```

**功能**:
- 实时盈亏计算
- 设置止盈止损
- 快速卖出
- 做T操作
- 持仓分析

#### 4.2.5 复盘分析页面

**布局**:
```
┌─────────────────────────────────────────────────────┐
│ 日期选择器                                          │
├─────────────────────────────────────────────────────┤
│ 市场复盘报告                                        │
│ - 市场概况     - 涨停分析    - 板块排行              │
│ - 龙虎榜       - 情绪周期    - 个人交易              │
├─────────────────────────────────────────────────────┤
│ 个人交易统计                                        │
│ - 成交记录     - 盈亏分析    - 策略执行              │
│ - 收益率曲线   - 胜率统计                            │
├─────────────────────────────────────────────────────┤
│ 策略回测工具                                        │
│ - 策略选择     - 回测参数    - 回测结果              │
└─────────────────────────────────────────────────────┘
```

**功能**:
- 自动生成每日复盘报告
- 交易记录查询
- 盈亏统计分析
- 策略回测
- 报告导出

### 4.3 组件库规划

**通用组件**:
- `StockCard`: 股票信息卡片（显示股票代码、名称、价格、涨跌幅）
- `LimitUpBadge`: 涨停标识（首板/二板/三板颜色区分）
- `ScoreBadge`: 评分标签（AI评分、板块强度）
- `BoardTable`: 涨停股票表格（带排序、筛选）
- `SectorChart`: 板块强度图表
- `TradeForm`: 交易表单
- `PositionCard`: 持仓卡片
- `RiskAlert`: 风控告警弹窗
- `Notification`: 通知消息

**业务组件**:
- `MarketOverview`: 市场概览面板
- `LimitUpMonitor`: 涨停监控面板
- `SectorRanking`: 板块排行面板
- `StockScreener`: 选股器
- `TradePanel`: 交易面板
- `PositionManager`: 持仓管理
- `ReviewReport`: 复盘报告
- `DragonTigerList`: 龙虎榜列表

---

## 五、后端API设计

### 5.1 API架构

**技术栈**: Python FastAPI + WebSocket

**API版本**: /api/v1/

**认证方式**: JWT Bearer Token

### 5.2 接口列表

#### 5.2.1 用户认证

```
POST   /api/v1/auth/register          # 用户注册
POST   /api/v1/auth/login             # 用户登录
POST   /api/v1/auth/logout            # 用户登出
POST   /api/v1/auth/refresh           # 刷新Token
POST   /api/v1/auth/password/reset    # 密码重置
GET    /api/v1/auth/profile           # 获取用户信息
PUT    /api/v1/auth/profile           # 更新用户信息
```

#### 5.2.2 行情数据

```
GET    /api/v1/market/stocks          # 获取股票列表
GET    /api/v1/market/stock/{code}    # 获取单只股票详情
GET    /api/v1/market/limitup         # 获取涨停股票列表
GET    /api/v1/market/sectors         # 获取板块列表
GET    /api/v1/market/sector/{id}     # 获取板块详情
GET    /api/v1/market/dragon_tiger    # 获取龙虎榜数据
WS     /api/v1/market/ws              # WebSocket实时行情
```

#### 5.2.3 智能选股

```
GET    /api/v1/screener/presets       # 获取预设选股条件
POST   /api/v1/screener/scan          # 执行选股
GET    /api/v1/screener/results       # 获取选股结果
POST   /api/v1/screener/custom        # 保存自定义选股条件
GET    /api/v1/screener/ai_score      # 获取AI评分
```

#### 5.2.4 交易执行

```
POST   /api/v1/trade/order            # 下单
DELETE /api/v1/trade/order/{id}       # 撤单
PUT    /api/v1/trade/order/{id}       # 改单
GET    /api/v1/trade/orders           # 查询订单列表
GET    /api/v1/trade/order/{id}       # 查询订单详情
POST   /api/v1/trade/batch            # 批量下单
POST   /api/v1/trade/cancel_all       # 一键撤单
```

#### 5.2.5 持仓管理

```
GET    /api/v1/positions              # 获取持仓列表
GET    /api/v1/position/{id}          # 获取持仓详情
POST   /api/v1/position/close_all     # 一键清仓
POST   /api/v1/position/close/{id}    # 平仓指定持仓
PUT    /api/v1/position/stop_loss     # 设置止损
PUT    /api/v1/position/take_profit   # 设置止盈
```

#### 5.2.6 复盘分析

```
GET    /api/v1/review/daily           # 获取每日复盘报告
GET    /api/v1/review/trades          # 获取交易记录
GET    /api/v1/review/statistics      # 获取统计数据
POST   /api/v1/review/backtest        # 执行策略回测
GET    /api/v1/review/sentiment       # 获取情绪周期数据
```

#### 5.2.7 资金管理

```
GET    /api/v1/account/balance        # 获取资金余额
GET    /api/v1/account/transactions   # 获取资金流水
GET    /api/v1/account/profit_loss    # 获取盈亏统计
```

#### 5.2.8 系统管理

```
GET    /api/v1/system/config          # 获取系统配置
PUT    /api/v1/system/config          # 更新系统配置
GET    /api/v1/system/status          # 获取系统状态
GET    /api/v1/system/logs            # 获取系统日志
```

### 5.3 WebSocket事件

```javascript
// 客户端订阅事件
ws.emit('subscribe', { channel: 'market' })
ws.emit('subscribe', { channel: 'stock:000001' })

// 服务器推送事件
ws.on('price_update', (data) => { ... })      // 价格更新
ws.on('limit_up_alert', (data) => { ... })    // 涨停提醒
ws.on('board_broken', (data) => { ... })      // 炸板提醒
ws.on('order_filled', (data) => { ... })      // 订单成交
ws.on('risk_triggered', (data) => { ... })    // 风控触发
ws.on('market_fuse', (data) => { ... })       // 熔断通知
```

---

## 六、系统非功能需求

### 6.1 性能要求

| 指标 | 目标值 | 说明 |
|------|--------|------|
| API响应时间 | <200ms | 95%的请求在200ms内返回 |
| WebSocket延迟 | <100ms | 行情数据推送延迟<100ms |
| 并发连接数 | >1000 | 支持1000+用户同时在线 |
| 行情刷新频率 | 3秒 | 价格数据每3秒更新 |
| 交易执行延迟 | <500ms | 下单到交易所<500ms |
| 系统可用性 | 99.9% | 全年停机时间<8.76小时 |

### 6.2 安全要求

- **数据传输**: 全站HTTPS加密
- **密码安全**: bcrypt加密存储，支持强密码策略
- **SQL注入**: 使用ORM，参数化查询
- **XSS防护**: 输入过滤，输出转义
- **CSRF防护**: Token验证
- **API限流**: 每分钟最多100次请求
- **敏感操作**: 重要操作需要二次确认
- **日志审计**: 所有操作留痕，支持审计

### 6.3 可扩展性

- **微服务架构**: 支持后续拆分为独立服务
- **数据库分库分表**: 支持水平扩展
- **缓存集群**: Redis集群支持
- **消息队列**: 引入RabbitMQ/Kafka处理高并发
- **容器化部署**: Docker + Kubernetes

### 6.4 监控告警

**监控指标**:
- 服务器资源（CPU、内存、磁盘、网络）
- 应用性能（QPS、响应时间、错误率）
- 业务指标（交易量、成功率、风控触发次数）
- 数据库性能（连接数、慢查询）

**告警规则**:
- CPU使用率>80%持续5分钟
- 内存使用率>85%持续5分钟
- API错误率>5%持续3分钟
- 交易成功率<90%持续5分钟
- 数据库连接数>80%持续3分钟

---

## 七、开发计划

### 7.1 开发阶段划分

**第一阶段：基础框架（2周）**
- [ ] 搭建Vue3前端框架
- [ ] 搭建Python后端框架
- [ ] 设计数据库Schema
- [ ] 配置Redis缓存
- [ ] 搭建开发环境

**第二阶段：行情监控（2周）**
- [ ] 接入行情数据源
- [ ] 开发实时行情服务
- [ ] 开发涨停股票监控
- [ ] 开发板块监控
- [ ] 实现WebSocket推送

**第三阶段：智能选股（2周）**
- [ ] 开发选股引擎
- [ ] 实现AI评分算法
- [ ] 开发选股界面
- [ ] 接入龙虎榜数据

**第四阶段：交易执行（3周）**
- [ ] 开发订单管理系统
- [ ] 实现打板交易逻辑
- [ ] 开发交易界面
- [ ] 开发交易记录功能（模拟交易，记录用户手动操作）

**第五阶段：风控管理（2周）**
- [ ] 实现止损系统
- [ ] 实现仓位管理
- [ ] 实现熔断机制
- [ ] 开发风控告警

**第六阶段：复盘分析（2周）**
- [ ] 开发自动复盘
- [ ] 实现龙虎榜分析
- [ ] 开发情绪周期分析
- [ ] 实现策略回测

**第七阶段：用户系统（1周）**
- [ ] 开发用户认证
- [ ] 开发资金管理
- [ ] 开发个人设置

**第八阶段：测试优化（2周）**
- [ ] 单元测试
- [ ] 集成测试
- [ ] 性能优化
- [ ] 安全加固
- [ ] Bug修复

**总计：约16周（4个月）**

### 7.2 技术选型总结

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 前端 | Vue3 | 3.3+ | UI框架 |
| 前端 | TypeScript | 5.0+ | 类型安全 |
| 前端 | Element Plus | 2.3+ | UI组件库 |
| 前端 | Pinia | 2.1+ | 状态管理 |
| 前端 | ECharts | 5.4+ | 图表库 |
| 后端 | Python | 3.10+ | 编程语言 |
| 后端 | FastAPI | 0.100+ | Web框架 |
| 后端 | SQLAlchemy | 2.0+ | ORM框架 |
| 后端 | Celery | 5.3+ | 异步任务 |
| 后端 | WebSockets | 11.0+ | 实时通信 |
| 数据库 | MySQL | 8.0+ | 结构化数据 |
| 数据库 | MongoDB | 6.0+ | 非结构化数据 |
| 缓存 | Redis | 7.0+ | 缓存、消息队列 |
| 部署 | Docker | 24.0+ | 容器化 |
| 部署 | Nginx | 1.24+ | 反向代理 |

---

## 八、风险与挑战

### 8.1 技术风险

1. **行情数据延迟**
   - 免费数据源存在一定延迟（3-5秒）
   - 解决方案：多数据源聚合，使用WebSocket实时推送优化体验

2. **交易接口限制**
   - 券商API限制较多
   - 解决方案：先使用模拟交易，逐步对接实盘

3. **高并发处理**
   - 行情推送量大
   - 解决方案：使用消息队列、负载均衡

### 8.2 业务风险

1. **策略失效**
   - 市场环境变化可能导致策略失效
   - 应对措施：持续优化策略，多策略组合

2. **极端行情**
   - 系统性风险可能导致大额亏损
   - 应对措施：完善熔断机制，设置单日最大亏损限额

3. **操作风险**
   - 用户误操作可能造成损失
   - 应对措施：重要操作二次确认，操作留痕

### 8.3 合规风险

1. **监管政策**
   - 自动化交易可能受限
   - 应对措施：关注监管政策，合规经营

2. **数据安全**
   - 用户交易数据敏感
   - 应对措施：数据加密，访问控制，定期审计

---

## 九、附录

### 9.1 术语表

| 术语 | 说明 |
|------|------|
| 打板 | 在股票涨停前买入 |
| 扫板 | 主动以卖一价买入 |
| 排板 | 以涨停价挂单排队 |
| 炸板 | 涨停后打开涨停板 |
| 首板 | 股票当日首次涨停 |
| 二板 | 股票连续第二日涨停 |
| 三板 | 股票连续第三日涨停 |
| 龙头股 | 板块内最强、涨幅最大的股票 |
| 板块效应 | 同板块多只股票同时上涨 |
| 龙虎榜 | 每日涨幅前列的股票买卖席位 |
| 做T | 日内高抛低吸摊低成本 |
| 熔断 | 市场剧烈波动时暂停交易 |

### 9.2 外部接口文档（免费API）

- **新浪行情API**: 
  - 实时行情: `https://hq.sinajs.cn/list=sh601006`
  - 盘口数据: `https://hq.sinajs.cn/?list=sh601006`
  
- **东方财富API**:
  - 行情数据: `https://push2.eastmoney.com/api/qt/stock/get`
  - 板块数据: `https://push2.eastmoney.com/api/qt/clist/get`
  
- **腾讯财经API**:
  - 行情数据: `https://qt.gtimg.cn/q=sh601006`
  
- **新浪财经龙虎榜**:
  - 爬虫地址: `https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/lhb/index.phtml`

**注意**: 以上均为免费公开接口，使用时请遵守相关网站的服务条款，控制请求频率，避免对服务器造成压力。
- **券商交易API**: 联系券商申请

### 9.3 参考资源

- **Vue3文档**: https://vuejs.org/
- **FastAPI文档**: https://fastapi.tiangolo.com/
- **SQLAlchemy文档**: https://docs.sqlalchemy.org/
- **Redis文档**: https://redis.io/documentation
- **MongoDB文档**: https://docs.mongodb.com/

---

**文档版本**: v1.1  
**编写人**: AI Assistant  
**审核状态**: 待审核

---

## 重要声明

### 系统定位

**TradeSentry 是一个交易辅助工具，而非自动交易系统。**

本系统严格遵循以下原则：

1. **仅使用免费行情API**
   - 系统使用新浪财经、东方财富、腾讯财经等免费公开API
   - 不涉及任何收费数据源或付费接口
   - 数据仅供参考，不构成投资建议

2. **仅支持手动交易**
   - 系统不提供任何自动下单功能
   - 所有买入/卖出操作必须由用户手动确认并执行
   - 系统仅提供行情监控、智能提醒和交易辅助功能
   - "一键"功能是指快速跳转或批量操作界面，而非自动执行

3. **风险提示**
   - 打板+龙头战法是极高风险的交易策略
   - 系统提供的风控提醒仅供参考，不构成操作建议
   - 用户应根据自身风险承受能力独立做出投资决策
   - 过往业绩不代表未来收益，投资有风险，入市需谨慎

4. **合规声明**
   - 本系统仅供学习交流使用
   - 不构成任何投资建议或承诺
   - 用户应遵守相关法律法规和券商交易规则
   - 严禁利用本系统进行违法违规交易活动

### 使用建议

- 首次使用建议先用模拟资金练习
- 充分理解打板策略的风险后再进行实盘操作
- 设置合理的止损线并严格执行
- 不要投入超过承受能力范围的资金

---

## 修改记录

| 版本 | 日期 | 修改内容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-02-07 | 初始版本 | AI Assistant |
| v1.1 | 2026-02-07 | 1. 修改行情API为仅使用免费API<br>2. 将自动交易改为仅支持手动交易 | AI Assistant |
