---
comet_change: position-monitoring-alerts
role: technical-design
canonical_spec: openspec
---

# 持仓止盈止损监控 — 技术设计

## Context

本设计是对 OpenSpec `design.md`（高层方案）的深度细化。目标见 `proposal.md`，需求见 delta spec `specs/position-monitoring/spec.md`。技术栈：Java 21 / Spring Boot 3.5 / Spring Data MongoDB；前端 Vue 3 / Vite / Element Plus。判定基于现有日线收盘价（`market_quotes.close` 或 `etf_daily_quotes.close`）。

## 架构与模块划分

后端（`com.xian.stockai`）：

| 模块 | 文件 | 职责 |
| --- | --- | --- |
| PO | `po/PositionMonitor.java` | 监控规则集合映射 |
| PO | `po/Notification.java` | 站内提醒集合映射 |
| DAO | `dao/PositionMonitorRepository.java` | 规则查询/保存 |
| DAO | `dao/NotificationRepository.java` | 提醒查询/更新 |
| 服务 | `service/MonitorService.java` | 规则 CRUD + 触达判定 |
| 通知 | `service/notify/NotificationSender.java` | 通知通道抽象 |
| 通知 | `service/notify/InAppNotificationSender.java` | 站内提醒实现 |
| 通知 | `service/notify/FeishuNotificationSender.java` | 飞书机器人推送实现 |
| 定时 | `task/MonitorTask.java` | 每日定时判定 |
| 接口 | `controller/MonitorController.java` | 规则与提醒端点 |

前端（`StockAI-Console/src`）：

- `components/Dashboard.vue`：持仓列表「操作」列新增「监控」按钮与止盈/止损设置弹窗。
- `App.vue`：顶部导航新增提醒铃铛（未读徽标）。
- 新增提醒消息列表弹窗/抽屉（已读/未读、标记已读）。

## 数据模型

### `position_monitor` 集合（`PositionMonitor`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | String | 主键 |
| `userId` | String | 用户 ID（索引） |
| `symbol` | String | 股票/ETF 代码（索引） |
| `stockName` | String | 股票名称 |
| `type` | String | 股票类型（沿用 Position.type） |
| `takeProfitType` | String | `PRICE` / `RATE`，可为空 |
| `takeProfitValue` | BigDecimal | 止盈阈值；RATE 时为小数（如 0.10=10%） |
| `stopLossType` | String | `PRICE` / `RATE`，可为空 |
| `stopLossValue` | BigDecimal | 止损阈值；RATE 时为负小数（如 -0.08=-8%） |
| `enabled` | Boolean | 是否启用，默认 true |
| `lastTriggerDirection` | String | `NONE` / `TAKE_PROFIT` / `STOP_LOSS`，用于去重 |
| `createdAt` / `updatedAt` | LocalDateTime | 时间戳 |

唯一约束：`(userId, symbol)`，采用 upsert。

### `notification` 集合（`Notification`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | String | 主键 |
| `userId` | String | 归属用户（索引） |
| `type` | String | `TAKE_PROFIT` / `STOP_LOSS` |
| `symbol` | String | 触发股票代码 |
| `stockName` | String | 股票名称 |
| `content` | String | 提醒文案 |
| `read` | Boolean | 是否已读，默认 false |
| `createdAt` | LocalDateTime | 创建时间 |

## 判定算法

现价：股票取 `marketQuotesRepository.findByCode(symbol).getClose()`，ETF（symbol 以 `1`/`5` 开头）取 `etfDailyQuotesRepository.findTopBySymbolOrderByUpdatedAtDesc(symbol).getClose()`。现价缺失或 `<= 0` 时跳过该规则。

收益率：`returnRate = (close - avgCostPrice) / avgCostPrice`（`avgCostPrice` 来自 `positionRepository.findByUserIdAndSymbol`；无持仓或均价为空则跳过）。

命中判定（阈值均为 BigDecimal，比较用 `compareTo`）：

- 止盈命中：`takeProfitType=PRICE` 时 `close >= takeProfitValue`；`RATE` 时 `returnRate >= takeProfitValue`。
- 止损命中：`stopLossType=PRICE` 时 `close <= stopLossValue`；`RATE` 时 `returnRate <= stopLossValue`。

去重状态机（每规则持有 `lastTriggerDirection`）：

1. 若当前止盈命中且 `lastTriggerDirection != TAKE_PROFIT` → 生成止盈提醒，置 `TAKE_PROFIT`。
2. 否则若当前止损命中且 `lastTriggerDirection != STOP_LOSS` → 生成止损提醒，置 `STOP_LOSS`。
3. 若止盈止损均未命中且 `lastTriggerDirection != NONE` → 置 `NONE`（回到安全区间）。

由此实现「持续监控但避免同一方向连续重复提醒」，并支持穿越回安全区间后再次提醒。

## REST 接口（沿用 `Result<T>` 包装，POST + JSON body）

| 方法/路径 | 请求体 | 说明 |
| --- | --- | --- |
| POST `/monitor/save` | `{userId, symbol, takeProfitType, takeProfitValue, stopLossType, stopLossValue}` | 创建/更新规则（upsert） |
| POST `/monitor/list` | `{userId}` | 查询该用户全部规则 |
| POST `/monitor/delete` | `{userId, symbol}` | 删除规则 |
| POST `/monitor/enable` | `{userId, symbol, enabled}` | 启停规则 |
| POST `/notification/list` | `{userId}` | 查询提醒（时间倒序） |
| POST `/notification/read` | `{userId, ids: [...]}` | 标记已读 |

## 定时任务

`MonitorTask.checkAll()` 使用 `@Scheduled(cron = "0 30 15 * * ?")`（工作日收盘后 15:30，可通过 `application.yml` 配置化）。流程：查询全部 `enabled=true` 的规则 → 逐条取现价与持仓均价 → 按上述状态机判定 → 需要提醒时调用 `notificationSender.send(...)` → 持久化 `lastTriggerDirection` 与 `updatedAt`。

## 通知通道抽象

`NotificationSender` 定义 `void send(Notification notification)`；`InAppNotificationSender` 写入 `notification` 集合；`FeishuNotificationSender` 推送到飞书自定义机器人 webhook。二者均实现同一接口，由配置选择启用，不改动判定逻辑。

`MonitorService` 改为注入 `List<NotificationSender>`（Spring 收集全部实现 bean），在 `notify()` 中遍历调用每个 sender。飞书 sender 通过 `@ConditionalOnProperty(prefix = "feishu", name = "enabled", havingValue = "true")` 门控：未启用时该 bean 不存在，`List` 中仅剩站内实现。

## 飞书通知通道

### 配置（application.yml）

```yaml
feishu:
  enabled: false            # 默认关闭，避免未配置时误发
  webhook-url: ""           # 飞书自定义机器人 webhook 地址
  secret: ""                # 可选加签密钥，空 = 不加签
```

### `FeishuNotificationSender`

- 构造注入 `webhook-url` 与 `secret`（`@Value`）。
- `send(Notification)` 组装飞书文本消息：`{"msg_type":"text","content":{"text": notification.getContent()}}`。
- 加签：`secret` 非空时，计算 `timestamp`（秒级）与 `sign = base64(HMAC-SHA256(timestamp + "\n" + secret, key=secret))`，一并放入 payload。
- 发送：Spring 6 内置 `RestClient` 向 `webhook-url` POST，读取响应 `code`；`code != 0` 记为告警日志。
- 失败处理：所有异常与飞书非成功码均在 `send()` 内捕获并记日志，不向上抛出，确保不影响站内提醒与去重状态更新。

### 消息内容

复用 `Notification.content` 文本（如「【止盈提醒】贵州茅台(600519) 最新收盘价 1800.00 已触发止盈」），不额外格式化。

## 边界条件与异常处理

- 现价/持仓均价缺失：跳过该规则，不生成提醒（记 debug 日志）。
- 均价为 0（收益率模式分母为 0）：跳过，不除零。
- 规则删除/停用后：定时任务不再遍历；历史提醒保留独立生命周期。
- 同一规则同一次任务内只最多生成一条提醒（方向唯一）。
- 大宗/停牌等导致的现价为空：跳过，避免误报。

## 测试策略

- 单元测试（`MonitorServiceTest`）：四种判定模式各自命中/未命中；去重状态机全状态迁移；注入 `List<NotificationSender>` 验证触达时所有 sender 均被调用。
- 单元测试（`FeishuNotificationSenderTest`）：mock `RestClient`，验证 payload 结构、加签正确、飞书返回非成功码或异常时吞掉不抛。
- 接口测试：规则 save/list/delete/enable、提醒 list/read。
- 手动端到端：设置规则 → 构造触达现价 → 触发 `MonitorTask` → 前端看到未读提醒并标记已读，飞书群收到机器人消息。

## 风险与取舍

- [日线收盘价无法反映盘中极值] → 明确本期为收盘价判定；价格源收敛为独立方法，接入实时行情可替换。
- [定时任务单实例假设] → 若未来多实例部署，需引入分布式锁；本期单实例，不做。
- [飞书推送为全局单 webhook] → 所有用户提醒推送到同一飞书群；若需按用户隔离，后续再加用户级 webhook 配置。本期按全局配置实现。
- [飞书发送无重试] → 失败仅记日志，可能丢提醒；站内提醒始终保留，可作为兜底，本期不做重试队列。