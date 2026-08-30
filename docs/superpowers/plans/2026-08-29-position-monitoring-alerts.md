---
change: position-monitoring-alerts
design-doc: docs/superpowers/specs/2026-08-29-position-monitoring-alerts-design.md
base-ref: 65c06986ee86257c466e257fe568d97622f2b3a1
archived-with: 2026-08-30-position-monitoring-alerts
---

# 持仓止盈止损监控 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 StockAI 持仓股增加止盈止损监控规则与站内提醒能力，基于日线收盘价定时判定触达并生成/查看/已读提醒。

**Architecture:** 后端（Java 21 / Spring Boot 3.5 / Spring Data MongoDB）新增 `position_monitor` 与 `notification` 两个集合，`MonitorService` 负责规则 CRUD 与触达判定（复用 `market_quotes` / `etf_daily_quotes` 现价源与 `position.avg_cost_price`），`NotificationSender` 接口隔离通知通道（本期仅 `InAppNotificationSender`），`MonitorTask` 用 `@Scheduled` 每交易日收盘后扫描启用规则。前端（Vue 3 / Vite / Element Plus）在 `Dashboard.vue` 持仓列表加「监控」入口与止盈止损表单，在 `App.vue` 顶部加提醒铃铛（未读徽标）与提醒抽屉。

**Tech Stack:** Java 21、Spring Boot 3.5.7、Spring Data MongoDB、Lombok、fastjson2、JUnit 5 + Mockito（`spring-boot-starter-test` 内置）；Vue 3.5、Element Plus 2.11、axios、dayjs。

**Spec:**
- 技术设计：`docs/superpowers/specs/2026-08-29-position-monitoring-alerts-design.md`
- 任务边界：`docs/openspec/changes/position-monitoring-alerts/tasks.md`
- 需求规格：`docs/openspec/changes/position-monitoring-alerts/specs/position-monitoring/spec.md`

## Global Constraints

以下约束逐条来自 design.md / spec.md，所有任务隐式包含：

- 后端包根：`com.xian.stockai`；PO 用显式 getter/setter（非 Lombok `@Data`），`@Document(collection=...)` + `@Field(name=...)` + `@Indexed` + 日期 `@JSONField`/`@JsonFormat`。
- 接口统一返回 `Result<T>`（`com.xian.stockai.vo.Result`），成功 `state=0`，失败 `state=1`；Controller 全为 `@PostMapping` + JSON body。
- 唯一约束 `(userId, symbol)`，规则采用 upsert；新增规则默认 `enabled=true`、`lastTriggerDirection=NONE`。
- 阈值类型仅 `PRICE` / `RATE`；去重方向仅 `NONE` / `TAKE_PROFIT` / `STOP_LOSS`；通知类型 `TAKE_PROFIT` / `STOP_LOSS`。
- 现价判定：symbol 以 `1` 或 `5` 开头走 `etfDailyQuotesRepository.findTopBySymbolOrderByUpdatedAtDesc`，否则走 `marketQuotesRepository.findByCode`；现价缺失或 `<=0` 跳过该规则（记 debug 日志）。
- 收益率模式：`(close - avgCostPrice) / avgCostPrice`，`avgCostPrice` 为空或 0 时不触发（不除零）。
- 所有 BigDecimal 比较用 `compareTo`，除法用 `RoundingMode.HALF_UP`。
- 前端 API 地址取自 `src/config/config.js` 的 `baseUrl`；用户 ID 沿用现有硬编码 `6928fca14f206ada1b7a9269`。
- 飞书推送可选启用：全局 webhook（`feishu.enabled` 默认 `false`），通过 `@ConditionalOnProperty` 门控 `FeishuNotificationSender` bean；推送失败仅记日志，不影响站内提醒。
- 不新增盘中行情、不做自动下单。

---

## 文件结构

**后端新增（`StockAI/src/main/java/com/xian/stockai/`）：**

| 文件 | 职责 |
| --- | --- |
| `po/PositionMonitor.java` | `position_monitor` 集合映射（规则） |
| `po/Notification.java` | `notification` 集合映射（站内提醒） |
| `dao/PositionMonitorRepository.java` | 规则查询/保存 |
| `dao/NotificationRepository.java` | 提醒查询/更新 |
| `service/notify/NotificationSender.java` | 通知通道接口 |
| `service/notify/InAppNotificationSender.java` | 站内提醒实现（写 `notification`） |
| `service/notify/FeishuNotificationSender.java` | 飞书机器人推送实现（`RestClient` + 可选加签，`@ConditionalOnProperty` 门控） |
| `service/MonitorService.java` | 规则 CRUD + 触达判定 + 去重 + `checkAll` |
| `service/NotificationService.java` | 提醒列表/标记已读 |
| `task/MonitorTask.java` | 每日定时判定 |
| `controller/MonitorController.java` | `/monitor/*` 端点 |
| `controller/NotificationController.java` | `/notification/*` 端点 |
| `vo/MonitorSaveRequest.java` / `MonitorEnableRequest.java` / `MonitorDeleteRequest.java` / `NotificationReadRequest.java` | 请求体 |

**后端测试新增（`StockAI/src/test/java/com/xian/stockai/`）：**

| 文件 | 覆盖 |
| --- | --- |
| `service/MonitorServiceTest.java` | CRUD + 四模式判定 + 去重 + ETF 价格源 |
| `service/NotificationServiceTest.java` | 列表/标记已读 |
| `service/notify/InAppNotificationSenderTest.java` | 写库为未读 |
| `service/notify/FeishuNotificationSenderTest.java` | payload 结构、加签、失败吞异常 |

**后端修改：**

- `src/main/resources/application.yml`：新增 `monitor.cron` 配置项。
- `src/main/resources/application.yml`：新增 `feishu.enabled/webhook-url/secret` 配置项。
- `service/MonitorService.java`：`NotificationSender` 单注入改为 `List<NotificationSender>` 多渠道循环。

**前端修改（`StockAI-Console/src/`）：**

| 文件 | 职责 |
| --- | --- |
| `components/Dashboard.vue` | 持仓列表操作列新增「监控」按钮 + 止盈止损设置弹窗 |
| `App.vue` | 顶部铃铛（未读徽标）+ 提醒抽屉（列表/标记已读） |

---

## Task 1: 数据模型 PO（PositionMonitor、Notification）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/PositionMonitor.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/Notification.java`

**Interfaces:**
- Produces:
  - `PositionMonitor` 字段：`id:String`、`userId:String`、`symbol:String`、`stockName:String`、`type:String`、`takeProfitType:String`、`takeProfitValue:BigDecimal`、`stopLossType:String`、`stopLossValue:BigDecimal`、`enabled:Boolean`、`lastTriggerDirection:String`、`createdAt:LocalDateTime`、`updatedAt:LocalDateTime`（`getX()/setX()`）。
  - `Notification` 字段：`id:String`、`userId:String`、`type:String`、`symbol:String`、`stockName:String`、`content:String`、`read:Boolean`、`createdAt:LocalDateTime`（`getX()/setX()`；布尔字段 getter 用 `getRead()`）。

- [x] **Step 1: 创建 `PositionMonitor` PO（模仿 `po/Position.java` 的写法）**

`StockAI/src/main/java/com/xian/stockai/po/PositionMonitor.java`：

```java
package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 持仓止盈止损监控规则
 */
@Document(collection = "position_monitor")
public class PositionMonitor implements Serializable {
    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    @Field(name = "symbol")
    @Indexed
    private String symbol;

    @Field(name = "stock_name")
    private String stockName;

    @Field(name = "type")
    private String type;

    @Field(name = "take_profit_type")
    private String takeProfitType;

    @Field(name = "take_profit_value")
    private BigDecimal takeProfitValue;

    @Field(name = "stop_loss_type")
    private String stopLossType;

    @Field(name = "stop_loss_value")
    private BigDecimal stopLossValue;

    @Field(name = "enabled")
    private Boolean enabled;

    @Field(name = "last_trigger_direction")
    private String lastTriggerDirection;

    @Field(name = "created_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTakeProfitType() { return takeProfitType; }
    public void setTakeProfitType(String takeProfitType) { this.takeProfitType = takeProfitType; }
    public BigDecimal getTakeProfitValue() { return takeProfitValue; }
    public void setTakeProfitValue(BigDecimal takeProfitValue) { this.takeProfitValue = takeProfitValue; }
    public String getStopLossType() { return stopLossType; }
    public void setStopLossType(String stopLossType) { this.stopLossType = stopLossType; }
    public BigDecimal getStopLossValue() { return stopLossValue; }
    public void setStopLossValue(BigDecimal stopLossValue) { this.stopLossValue = stopLossValue; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getLastTriggerDirection() { return lastTriggerDirection; }
    public void setLastTriggerDirection(String lastTriggerDirection) { this.lastTriggerDirection = lastTriggerDirection; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [x] **Step 2: 创建 `Notification` PO**

`StockAI/src/main/java/com/xian/stockai/po/Notification.java`：

```java
package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 站内提醒
 */
@Document(collection = "notification")
public class Notification implements Serializable {
    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    @Field(name = "type")
    private String type;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "stock_name")
    private String stockName;

    @Field(name = "content")
    private String content;

    @Field(name = "read")
    private Boolean read;

    @Field(name = "created_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [x] **Step 3: 编译验证**

Run: 在 `d:\WorkSpace\MainProject\StockAI-master\StockAI` 执行 `./mvnw -q compile`
Expected: BUILD SUCCESS（无编译错误）。

- [x] **Step 4: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/PositionMonitor.java StockAI/src/main/java/com/xian/stockai/po/Notification.java
git commit -m "feat: 新增持仓止盈止损监控规则与站内提醒 PO"
```

---

## Task 2: Repository 层（PositionMonitorRepository、NotificationRepository）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/dao/PositionMonitorRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/NotificationRepository.java`

**Interfaces:**
- Consumes: `PositionMonitor`、`Notification`（Task 1）
- Produces:
  - `PositionMonitorRepository` 方法：`findByUserIdAndSymbol(String,String):PositionMonitor`、`findByUserId(String):List<PositionMonitor>`、`findByEnabledTrue():List<PositionMonitor>`
  - `NotificationRepository` 方法：`findByUserIdOrderByCreatedAtDesc(String):List<Notification>`、`findByUserIdAndIdIn(String,Collection<String>):List<Notification>`

- [x] **Step 1: 创建 `PositionMonitorRepository`**

`StockAI/src/main/java/com/xian/stockai/dao/PositionMonitorRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.PositionMonitor;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PositionMonitorRepository extends MongoRepository<PositionMonitor, String> {
    PositionMonitor findByUserIdAndSymbol(String userId, String symbol);

    List<PositionMonitor> findByUserId(String userId);

    List<PositionMonitor> findByEnabledTrue();
}
```

- [x] **Step 2: 创建 `NotificationRepository`**

`StockAI/src/main/java/com/xian/stockai/dao/NotificationRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserIdAndIdIn(String userId, Collection<String> ids);
}
```

- [x] **Step 3: 编译验证**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS。

- [x] **Step 4: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/dao/PositionMonitorRepository.java StockAI/src/main/java/com/xian/stockai/dao/NotificationRepository.java
git commit -m "feat: 新增监控规则与站内提醒 Repository"
```

---

## Task 3: 通知通道抽象（NotificationSender + InAppNotificationSender）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/notify/NotificationSender.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/notify/InAppNotificationSender.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/notify/InAppNotificationSenderTest.java`

**Interfaces:**
- Consumes: `Notification`（Task 1）、`NotificationRepository`（Task 2）
- Produces: `NotificationSender` 接口方法 `void send(Notification notification)`（Task 4/5 注入使用）

- [x] **Step 1: 写失败测试 `InAppNotificationSenderTest`**

`StockAI/src/test/java/com/xian/stockai/service/notify/InAppNotificationSenderTest.java`：

```java
package com.xian.stockai.service.notify;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppNotificationSenderTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private InAppNotificationSender sender;

    @Test
    void sendPersistsNotification() {
        Notification n = new Notification();
        n.setRead(false);

        sender.send(n);

        verify(notificationRepository).save(n);
    }
}
```

- [x] **Step 2: 运行测试确认失败（编译失败即红）**

Run: `./mvnw -q test -Dtest=InAppNotificationSenderTest`
Expected: 编译失败（`InAppNotificationSender` 不存在）。

- [x] **Step 3: 实现接口与实现类**

`StockAI/src/main/java/com/xian/stockai/service/notify/NotificationSender.java`：

```java
package com.xian.stockai.service.notify;

import com.xian.stockai.po.Notification;

public interface NotificationSender {
    void send(Notification notification);
}
```

`StockAI/src/main/java/com/xian/stockai/service/notify/InAppNotificationSender.java`：

```java
package com.xian.stockai.service.notify;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InAppNotificationSender implements NotificationSender {
    private final NotificationRepository notificationRepository;

    public InAppNotificationSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void send(Notification notification) {
        notificationRepository.save(notification);
        log.debug("生成站内提醒: userId={}, type={}, symbol={}",
                notification.getUserId(), notification.getType(), notification.getSymbol());
    }
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `./mvnw -q test -Dtest=InAppNotificationSenderTest`
Expected: BUILD SUCCESS（1 test passed）。

- [x] **Step 5: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/notify/NotificationSender.java StockAI/src/main/java/com/xian/stockai/service/notify/InAppNotificationSender.java StockAI/src/test/java/com/xian/stockai/service/notify/InAppNotificationSenderTest.java
git commit -m "feat: 新增通知通道接口与站内提醒实现"
```

---

## Task 4: MonitorService 规则 CRUD（save/list/delete/enable）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/vo/MonitorSaveRequest.java`
- Create: `StockAI/src/main/java/com/xian/stockai/vo/MonitorEnableRequest.java`
- Create: `StockAI/src/main/java/com/xian/stockai/vo/MonitorDeleteRequest.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/MonitorService.java`（本轮仅 CRUD + 完整构造器）
- Test: `StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java`（CRUD 部分用例）

**Interfaces:**
- Consumes: `PositionMonitor`、`Notification`、`Position`、`PositionMonitorRepository`、`PositionRepository`、`MarketQuotesRepository`、`EtfDailyQuotesRepository`、`NotificationSender`
- Produces（MonitorService 全部公开方法的最终签名，Task 5 补齐 `checkAll`/`checkRule`）：
  - `Result<PositionMonitor> save(MonitorSaveRequest req)`
  - `Result<List<PositionMonitor>> list(String userId)`
  - `Result<Void> delete(String userId, String symbol)`
  - `Result<Void> enable(String userId, String symbol, boolean enabled)`
  - 常量：`TYPE_PRICE="PRICE"`、`TYPE_RATE="RATE"`、`DIR_NONE="NONE"`、`DIR_TAKE_PROFIT="TAKE_PROFIT"`、`DIR_STOP_LOSS="STOP_LOSS"`

- [x] **Step 1: 创建三个请求 VO**

`MonitorSaveRequest.java`：

```java
package com.xian.stockai.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonitorSaveRequest {
    private String userId;
    private String symbol;
    private String takeProfitType;
    private BigDecimal takeProfitValue;
    private String stopLossType;
    private BigDecimal stopLossValue;
}
```

`MonitorEnableRequest.java`：

```java
package com.xian.stockai.vo;

import lombok.Data;

@Data
public class MonitorEnableRequest {
    private String userId;
    private String symbol;
    private Boolean enabled;
}
```

`MonitorDeleteRequest.java`：

```java
package com.xian.stockai.vo;

import lombok.Data;

@Data
public class MonitorDeleteRequest {
    private String userId;
    private String symbol;
}
```

- [x] **Step 2: 写失败测试（CRUD 用例）**

`StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java`（本轮先写出完整类，含 Task 5 的判定用例，但 Step 3 只实现 CRUD，判定的 4 个用例会因 `checkRule` 不存在而编译失败，属于预期红）：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.MarketQuotes;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.vo.MonitorSaveRequest;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private PositionMonitorRepository positionMonitorRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private MarketQuotesRepository marketQuotesRepository;
    @Mock
    private EtfDailyQuotesRepository etfDailyQuotesRepository;
    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private MonitorService monitorService;

    private static final String USER_ID = "u1";
    private static final String SYMBOL = "000001";

    private PositionMonitor priceTpRule() {
        PositionMonitor rule = new PositionMonitor();
        rule.setId("r1");
        rule.setUserId(USER_ID);
        rule.setSymbol(SYMBOL);
        rule.setStockName("平安银行");
        rule.setTakeProfitType(MonitorService.TYPE_PRICE);
        rule.setTakeProfitValue(new BigDecimal("15.00"));
        rule.setLastTriggerDirection(MonitorService.DIR_NONE);
        return rule;
    }

    private Position position(BigDecimal avgCostPrice) {
        Position p = new Position();
        p.setUserId(USER_ID);
        p.setSymbol(SYMBOL);
        p.setStockName("平安银行");
        p.setType("科技");
        p.setAvgCostPrice(avgCostPrice);
        return p;
    }

    private MarketQuotes stockQuote(String close) {
        MarketQuotes q = new MarketQuotes();
        q.setCode(SYMBOL);
        q.setClose(new BigDecimal(close));
        return q;
    }

    @Test
    void saveCreatesEnabledRuleWithNoneDirection() {
        MonitorSaveRequest req = new MonitorSaveRequest();
        req.setUserId(USER_ID);
        req.setSymbol(SYMBOL);
        req.setTakeProfitType(MonitorService.TYPE_PRICE);
        req.setTakeProfitValue(new BigDecimal("15.00"));

        when(positionMonitorRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(null);
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        Result<PositionMonitor> result = monitorService.save(req);

        assertTrue(result.checkSuccess());
        assertTrue(result.getData().getEnabled());
        assertEquals(MonitorService.DIR_NONE, result.getData().getLastTriggerDirection());
        assertEquals("平安银行", result.getData().getStockName());
        verify(positionMonitorRepository).save(any(PositionMonitor.class));
    }

    @Test
    void saveRejectsRuleWithoutThresholds() {
        MonitorSaveRequest req = new MonitorSaveRequest();
        req.setUserId(USER_ID);
        req.setSymbol(SYMBOL);

        Result<PositionMonitor> result = monitorService.save(req);

        assertFalse(result.checkSuccess());
        verify(positionMonitorRepository, never()).save(any());
    }

    @Test
    void enableUpdatesRule() {
        PositionMonitor existing = priceTpRule();
        existing.setEnabled(true);
        when(positionMonitorRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(existing);

        Result<Void> result = monitorService.enable(USER_ID, SYMBOL, false);

        assertTrue(result.checkSuccess());
        assertFalse(existing.getEnabled());
        verify(positionMonitorRepository).save(existing);
    }

    @Test
    void deleteRemovesRule() {
        PositionMonitor existing = priceTpRule();
        when(positionMonitorRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(existing);

        Result<Void> result = monitorService.delete(USER_ID, SYMBOL);

        assertTrue(result.checkSuccess());
        verify(positionMonitorRepository).delete(existing);
    }

    // === Task 5 的判定/去重用例（本轮编译报错属预期，Task 5 实现 checkRule 后转绿） ===

    @Test
    void priceTakeProfitTriggersWhenCloseAboveTarget() {
        PositionMonitor rule = priceTpRule();
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_TAKE_PROFIT, rule.getLastTriggerDirection());
        verify(positionMonitorRepository).save(rule);
    }

    @Test
    void rateTakeProfitTriggersWhenRateAboveTarget() {
        PositionMonitor rule = priceTpRule();
        rule.setTakeProfitType(MonitorService.TYPE_RATE);
        rule.setTakeProfitValue(new BigDecimal("0.10"));
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("11.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_TAKE_PROFIT, rule.getLastTriggerDirection());
    }

    @Test
    void priceStopLossTriggersWhenCloseBelowTarget() {
        PositionMonitor rule = priceTpRule();
        rule.setTakeProfitType(null);
        rule.setTakeProfitValue(null);
        rule.setStopLossType(MonitorService.TYPE_PRICE);
        rule.setStopLossValue(new BigDecimal("9.00"));
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("8.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_STOP_LOSS, rule.getLastTriggerDirection());
    }

    @Test
    void rateStopLossTriggersWhenRateBelowTarget() {
        PositionMonitor rule = priceTpRule();
        rule.setTakeProfitType(null);
        rule.setTakeProfitValue(null);
        rule.setStopLossType(MonitorService.TYPE_RATE);
        rule.setStopLossValue(new BigDecimal("-0.08"));
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("9.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_STOP_LOSS, rule.getLastTriggerDirection());
    }

    @Test
    void sameDirectionDoesNotDuplicateAndResetsAfterReturnToSafeZone() {
        PositionMonitor rule = priceTpRule();
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);
        monitorService.checkRule(rule);
        verify(notificationSender, times(1)).send(any());

        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("14.00"));
        monitorService.checkRule(rule);
        assertEquals(MonitorService.DIR_NONE, rule.getLastTriggerDirection());

        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        monitorService.checkRule(rule);
        verify(notificationSender, times(2)).send(any());
    }

    @Test
    void etfUsesEtfDailyQuotesPriceSource() {
        PositionMonitor rule = priceTpRule();
        rule.setSymbol("510300");
        rule.setTakeProfitValue(new BigDecimal("4.20"));
        EtfDailyQuotes etf = new EtfDailyQuotes();
        etf.setSymbol("510300");
        etf.setClose(new BigDecimal("4.50"));
        when(etfDailyQuotesRepository.findTopBySymbolOrderByUpdatedAtDesc("510300")).thenReturn(etf);
        when(positionRepository.findByUserIdAndSymbol(USER_ID, "510300")).thenReturn(position(new BigDecimal("4.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_TAKE_PROFIT, rule.getLastTriggerDirection());
    }
}
```

- [x] **Step 3: 实现 `MonitorService`（CRUD + 完整构造器；判定方法本轮先置为占位，Task 5 补齐）**

`StockAI/src/main/java/com/xian/stockai/service/MonitorService.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.vo.MonitorSaveRequest;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class MonitorService {
    public static final String DIR_NONE = "NONE";
    public static final String DIR_TAKE_PROFIT = "TAKE_PROFIT";
    public static final String DIR_STOP_LOSS = "STOP_LOSS";
    public static final String TYPE_PRICE = "PRICE";
    public static final String TYPE_RATE = "RATE";

    private final PositionMonitorRepository positionMonitorRepository;
    private final PositionRepository positionRepository;
    private final MarketQuotesRepository marketQuotesRepository;
    private final EtfDailyQuotesRepository etfDailyQuotesRepository;
    private final NotificationSender notificationSender;

    public MonitorService(PositionMonitorRepository positionMonitorRepository,
                          PositionRepository positionRepository,
                          MarketQuotesRepository marketQuotesRepository,
                          EtfDailyQuotesRepository etfDailyQuotesRepository,
                          NotificationSender notificationSender) {
        this.positionMonitorRepository = positionMonitorRepository;
        this.positionRepository = positionRepository;
        this.marketQuotesRepository = marketQuotesRepository;
        this.etfDailyQuotesRepository = etfDailyQuotesRepository;
        this.notificationSender = notificationSender;
    }

    public Result<PositionMonitor> save(MonitorSaveRequest req) {
        if (req.getUserId() == null || req.getUserId().isEmpty()) {
            return Result.fail("用户ID不能为空");
        }
        if (req.getSymbol() == null || req.getSymbol().isEmpty()) {
            return Result.fail("股票代码不能为空");
        }
        if (req.getTakeProfitType() != null && !req.getTakeProfitType().isEmpty()
                && !isValidType(req.getTakeProfitType())) {
            return Result.fail("止盈类型无效");
        }
        if (req.getStopLossType() != null && !req.getStopLossType().isEmpty()
                && !isValidType(req.getStopLossType())) {
            return Result.fail("止损类型无效");
        }
        boolean hasTakeProfit = req.getTakeProfitType() != null && !req.getTakeProfitType().isEmpty()
                && req.getTakeProfitValue() != null;
        boolean hasStopLoss = req.getStopLossType() != null && !req.getStopLossType().isEmpty()
                && req.getStopLossValue() != null;
        if (!hasTakeProfit && !hasStopLoss) {
            return Result.fail("止盈或止损至少设置一项");
        }

        PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(req.getUserId(), req.getSymbol());
        if (monitor == null) {
            monitor = new PositionMonitor();
            monitor.setUserId(req.getUserId());
            monitor.setSymbol(req.getSymbol());
            monitor.setEnabled(true);
            monitor.setLastTriggerDirection(DIR_NONE);
            monitor.setCreatedAt(LocalDateTime.now());
        }
        monitor.setTakeProfitType(req.getTakeProfitType());
        monitor.setTakeProfitValue(req.getTakeProfitValue());
        monitor.setStopLossType(req.getStopLossType());
        monitor.setStopLossValue(req.getStopLossValue());

        Position position = positionRepository.findByUserIdAndSymbol(req.getUserId(), req.getSymbol());
        if (position != null) {
            monitor.setStockName(position.getStockName());
            monitor.setType(position.getType());
        }
        monitor.setUpdatedAt(LocalDateTime.now());
        positionMonitorRepository.save(monitor);
        return Result.success(monitor);
    }

    public Result<List<PositionMonitor>> list(String userId) {
        return Result.success(positionMonitorRepository.findByUserId(userId));
    }

    public Result<Void> delete(String userId, String symbol) {
        PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(userId, symbol);
        if (monitor == null) {
            return Result.fail("监控规则不存在");
        }
        positionMonitorRepository.delete(monitor);
        return Result.success();
    }

    public Result<Void> enable(String userId, String symbol, boolean enabled) {
        PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(userId, symbol);
        if (monitor == null) {
            return Result.fail("监控规则不存在");
        }
        monitor.setEnabled(enabled);
        monitor.setUpdatedAt(LocalDateTime.now());
        positionMonitorRepository.save(monitor);
        return Result.success();
    }

    private boolean isValidType(String type) {
        return TYPE_PRICE.equals(type) || TYPE_RATE.equals(type);
    }
}
```

- [x] **Step 4: 运行 CRUD 相关测试（红：判定用例仍失败）**

Run: `./mvnw -q test -Dtest=MonitorServiceTest`
Expected: `saveCreatesEnabledRuleWithNoneDirection`、`saveRejectsRuleWithoutThresholds`、`enableUpdatesRule`、`deleteRemovesRule` 通过；`checkRule` 相关用例编译失败——这是预期，Task 5 补齐后转绿。

> 注：为满足「每个 Task 独立可测」，本轮 4 个判定用例会因 `checkRule` 缺失而编译失败，属正常中间态。若希望本轮即可 `test-compile` 通过，可临时将 4 个判定用例注释掉，Task 5 再放开。

- [x] **Step 5: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/vo/MonitorSaveRequest.java StockAI/src/main/java/com/xian/stockai/vo/MonitorEnableRequest.java StockAI/src/main/java/com/xian/stockai/vo/MonitorDeleteRequest.java StockAI/src/main/java/com/xian/stockai/service/MonitorService.java StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java
git commit -m "feat: MonitorService 规则 CRUD 与请求体 VO"
```

---

## Task 5: 价格/收益率判定 + 去重 + 通知触发（checkRule/checkAll）

**Files:**
- Modify: `StockAI/src/main/java/com/xian/stockai/service/MonitorService.java`（追加 `checkAll`/`checkRule`/`getCurrentPrice`/`getReturnRate`/判定与通知私有方法）
- Test: `StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java`（Task 4 已写好判定用例，本轮转绿）

**Interfaces:**
- Consumes: Task 4 的 MonitorService 构造器与常量
- Produces:
  - `void checkAll()`（Task 6 调用）
  - `void checkRule(PositionMonitor rule)`
  - `BigDecimal getCurrentPrice(String symbol)`

- [x] **Step 1: 运行测试确认判定用例失败**

Run: `./mvnw -q test -Dtest=MonitorServiceTest`
Expected: `priceTakeProfitTriggersWhenCloseAboveTarget` 等 4 个用例编译失败（`checkRule` 不存在）。

- [x] **Step 2: 在 `MonitorService` 追加判定实现（追加到 `isValidType` 之后、类结束前）**

```java
    public void checkAll() {
        List<PositionMonitor> rules = positionMonitorRepository.findByEnabledTrue();
        for (PositionMonitor rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                log.error("监控规则{}判定失败: {}", rule.getId(), e.getMessage(), e);
            }
        }
    }

    public void checkRule(PositionMonitor rule) {
        BigDecimal close = getCurrentPrice(rule.getSymbol());
        if (close == null || close.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("规则{}对应标的{}现价缺失，跳过", rule.getId(), rule.getSymbol());
            return;
        }
        BigDecimal returnRate = getReturnRate(rule.getUserId(), rule.getSymbol(), close);

        if (isTakeProfitHit(rule, close, returnRate)) {
            if (!DIR_TAKE_PROFIT.equals(rule.getLastTriggerDirection())) {
                notify(rule, DIR_TAKE_PROFIT, close);
                rule.setLastTriggerDirection(DIR_TAKE_PROFIT);
                rule.setUpdatedAt(LocalDateTime.now());
                positionMonitorRepository.save(rule);
            }
        } else if (isStopLossHit(rule, close, returnRate)) {
            if (!DIR_STOP_LOSS.equals(rule.getLastTriggerDirection())) {
                notify(rule, DIR_STOP_LOSS, close);
                rule.setLastTriggerDirection(DIR_STOP_LOSS);
                rule.setUpdatedAt(LocalDateTime.now());
                positionMonitorRepository.save(rule);
            }
        } else {
            if (!DIR_NONE.equals(rule.getLastTriggerDirection())) {
                rule.setLastTriggerDirection(DIR_NONE);
                rule.setUpdatedAt(LocalDateTime.now());
                positionMonitorRepository.save(rule);
            }
        }
    }

    public BigDecimal getCurrentPrice(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        if (symbol.startsWith("1") || symbol.startsWith("5")) {
            com.xian.stockai.po.EtfDailyQuotes etf =
                    etfDailyQuotesRepository.findTopBySymbolOrderByUpdatedAtDesc(symbol);
            return etf == null ? null : etf.getClose();
        }
        com.xian.stockai.po.MarketQuotes quotes = marketQuotesRepository.findByCode(symbol);
        return quotes == null ? null : quotes.getClose();
    }

    private BigDecimal getReturnRate(String userId, String symbol, BigDecimal close) {
        Position position = positionRepository.findByUserIdAndSymbol(userId, symbol);
        if (position == null || position.getAvgCostPrice() == null
                || position.getAvgCostPrice().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return close.subtract(position.getAvgCostPrice())
                .divide(position.getAvgCostPrice(), 4, java.math.RoundingMode.HALF_UP);
    }

    private boolean isTakeProfitHit(PositionMonitor rule, BigDecimal close, BigDecimal returnRate) {
        if (rule.getTakeProfitType() == null || rule.getTakeProfitValue() == null) {
            return false;
        }
        if (TYPE_PRICE.equals(rule.getTakeProfitType())) {
            return close.compareTo(rule.getTakeProfitValue()) >= 0;
        }
        if (TYPE_RATE.equals(rule.getTakeProfitType())) {
            return returnRate != null && returnRate.compareTo(rule.getTakeProfitValue()) >= 0;
        }
        return false;
    }

    private boolean isStopLossHit(PositionMonitor rule, BigDecimal close, BigDecimal returnRate) {
        if (rule.getStopLossType() == null || rule.getStopLossValue() == null) {
            return false;
        }
        if (TYPE_PRICE.equals(rule.getStopLossType())) {
            return close.compareTo(rule.getStopLossValue()) <= 0;
        }
        if (TYPE_RATE.equals(rule.getStopLossType())) {
            return returnRate != null && returnRate.compareTo(rule.getStopLossValue()) <= 0;
        }
        return false;
    }

    private void notify(PositionMonitor rule, String direction, BigDecimal close) {
        com.xian.stockai.po.Notification notification = new com.xian.stockai.po.Notification();
        notification.setUserId(rule.getUserId());
        notification.setType(direction);
        notification.setSymbol(rule.getSymbol());
        notification.setStockName(rule.getStockName());
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        String label = DIR_TAKE_PROFIT.equals(direction) ? "止盈" : "止损";
        notification.setContent(String.format("【%s提醒】%s(%s) 最新收盘价 %s 已触发%s",
                label, rule.getStockName(), rule.getSymbol(), close.toPlainString(), label));
        notificationSender.send(notification);
    }
```

同时更新该类顶部 import，新增：

```java
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.MarketQuotes;
import com.xian.stockai.po.Notification;
import java.math.RoundingMode;
```

（将判断方法里的全限定名 `com.xian.stockai.po.EtfDailyQuotes` 等替换为短名：`EtfDailyQuotes`、`MarketQuotes`、`Notification`，`java.math.RoundingMode.HALF_UP` 替换为 `RoundingMode.HALF_UP`。）

- [x] **Step 3: 运行测试确认全部通过**

Run: `./mvnw -q test -Dtest=MonitorServiceTest`
Expected: 8 个测试全部通过（4 CRUD + 4 判定/去重 + ETF）。

- [x] **Step 4: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/MonitorService.java StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java
git commit -m "feat: 实现止盈止损四模式判定与去重状态机"
```

---

## Task 6: 定时判定任务 MonitorTask

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/task/MonitorTask.java`
- Modify: `StockAI/src/main/resources/application.yml`（新增 `monitor.cron`）

**Interfaces:**
- Consumes: `MonitorService.checkAll()`（Task 5）

- [x] **Step 1: 创建 `MonitorTask`**

`StockAI/src/main/java/com/xian/stockai/task/MonitorTask.java`：

```java
package com.xian.stockai.task;

import com.xian.stockai.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorTask {
    private final MonitorService monitorService;

    @Scheduled(cron = "${monitor.cron:0 30 15 * * ?}")
    public void checkAll() {
        log.info("开始持仓止盈止损监控判定");
        monitorService.checkAll();
        log.info("持仓止盈止损监控判定结束");
    }
}
```

- [x] **Step 2: 在 `application.yml` 末尾追加配置**

```yaml
# 止盈止损监控定时任务 cron（工作日收盘后 15:30）
monitor:
  cron: "0 30 15 * * ?"
```

- [x] **Step 3: 编译验证**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS（`@EnableScheduling` 已在 `StockAiApplication` 上，无需额外启用）。

- [x] **Step 4: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/task/MonitorTask.java StockAI/src/main/resources/application.yml
git commit -m "feat: 新增收盘后止盈止损定时判定任务"
```

---

## Task 7: MonitorController 端点

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/controller/MonitorController.java`

**Interfaces:**
- Consumes: `MonitorService`（Task 4/5）、`UserRequest`（现有）、三个请求 VO（Task 4）
- Produces:
  - `POST /monitor/save` → `Result<PositionMonitor>`
  - `POST /monitor/list` → `Result<List<PositionMonitor>>`
  - `POST /monitor/delete` → `Result<Void>`
  - `POST /monitor/enable` → `Result<Void>`

- [x] **Step 1: 创建 `MonitorController`（模仿 `controller/StockController.java`）**

`StockAI/src/main/java/com/xian/stockai/controller/MonitorController.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.MonitorService;
import com.xian.stockai.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/monitor")
@Slf4j
public class MonitorController {
    private final MonitorService monitorService;

    @PostMapping("/save")
    public Result<PositionMonitor> save(@RequestBody MonitorSaveRequest req) {
        return monitorService.save(req);
    }

    @PostMapping("/list")
    public Result<List<PositionMonitor>> list(@RequestBody UserRequest req) {
        return monitorService.list(req.getUserId());
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody MonitorDeleteRequest req) {
        return monitorService.delete(req.getUserId(), req.getSymbol());
    }

    @PostMapping("/enable")
    public Result<Void> enable(@RequestBody MonitorEnableRequest req) {
        return monitorService.enable(req.getUserId(), req.getSymbol(), Boolean.TRUE.equals(req.getEnabled()));
    }
}
```

- [x] **Step 2: 编译验证**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS。

- [x] **Step 3: 冒烟（可选，启动服务后）**

```bash
curl -X POST http://localhost:8888/monitor/save -H "Content-Type: application/json" -d "{\"userId\":\"6928fca14f206ada1b7a9269\",\"symbol\":\"000001\",\"takeProfitType\":\"PRICE\",\"takeProfitValue\":15.00}"
```
Expected: 返回 `{"state":0,...,"data":{...}}`。

- [x] **Step 4: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/controller/MonitorController.java
git commit -m "feat: 新增监控规则 Controller 端点"
```

---

## Task 8: NotificationService + NotificationController 端点

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/vo/NotificationReadRequest.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/NotificationService.java`
- Create: `StockAI/src/main/java/com/xian/stockai/controller/NotificationController.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/NotificationServiceTest.java`

**Interfaces:**
- Consumes: `NotificationRepository`（Task 2）、`Notification`（Task 1）、`UserRequest`（现有）
- Produces:
  - `Result<List<Notification>> NotificationService.list(String userId)`
  - `Result<Void> NotificationService.markRead(String userId, List<String> ids)`
  - `POST /notification/list`、`POST /notification/read`

- [x] **Step 1: 创建 `NotificationReadRequest` VO**

```java
package com.xian.stockai.vo;

import lombok.Data;

import java.util.List;

@Data
public class NotificationReadRequest {
    private String userId;
    private List<String> ids;
}
```

- [x] **Step 2: 写失败测试 `NotificationServiceTest`**

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void listReturnsNotifications() {
        List<Notification> list = List.of(new Notification());
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(list);

        Result<List<Notification>> result = notificationService.list("u1");

        assertTrue(result.checkSuccess());
        assertEquals(list, result.getData());
    }

    @Test
    void markReadUpdatesReadFlag() {
        Notification n = new Notification();
        n.setId("n1");
        n.setRead(false);
        when(notificationRepository.findByUserIdAndIdIn("u1", List.of("n1"))).thenReturn(List.of(n));

        Result<Void> result = notificationService.markRead("u1", List.of("n1"));

        assertTrue(result.checkSuccess());
        assertTrue(n.getRead());
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void markReadRejectsEmptyIds() {
        Result<Void> result = notificationService.markRead("u1", List.of());

        assertFalse(result.checkSuccess());
    }
}
```

- [x] **Step 3: 运行确认失败**

Run: `./mvnw -q test -Dtest=NotificationServiceTest`
Expected: 编译失败（`NotificationService` 不存在）。

- [x] **Step 4: 实现 `NotificationService` 与 `NotificationController`**

`StockAI/src/main/java/com/xian/stockai/service/NotificationService.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Result<List<Notification>> list(String userId) {
        return Result.success(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    public Result<Void> markRead(String userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail("请选择要标记的提醒");
        }
        List<Notification> notifications = notificationRepository.findByUserIdAndIdIn(userId, ids);
        for (Notification notification : notifications) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(notifications);
        return Result.success();
    }
}
```

`StockAI/src/main/java/com/xian/stockai/controller/NotificationController.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.Notification;
import com.xian.stockai.service.NotificationService;
import com.xian.stockai.vo.NotificationReadRequest;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/notification")
@Slf4j
public class NotificationController {
    private final NotificationService notificationService;

    @PostMapping("/list")
    public Result<List<Notification>> list(@RequestBody UserRequest req) {
        return notificationService.list(req.getUserId());
    }

    @PostMapping("/read")
    public Result<Void> read(@RequestBody NotificationReadRequest req) {
        return notificationService.markRead(req.getUserId(), req.getIds());
    }
}
```

- [x] **Step 5: 运行测试确认通过**

Run: `./mvnw -q test -Dtest=NotificationServiceTest`
Expected: 3 个测试全部通过。

- [x] **Step 6: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/vo/NotificationReadRequest.java StockAI/src/main/java/com/xian/stockai/service/NotificationService.java StockAI/src/main/java/com/xian/stockai/controller/NotificationController.java StockAI/src/test/java/com/xian/stockai/service/NotificationServiceTest.java
git commit -m "feat: 新增站内提醒列表与标记已读端点"
```

---

## Task 9: 前端持仓列表监控设置入口 + 止盈止损表单（Dashboard.vue）

**Files:**
- Modify: `StockAI-Console/src/components/Dashboard.vue`

**Interfaces:**
- Consumes: 后端 `POST /monitor/save`、`POST /monitor/list`（Task 7）、`POST /monitor/delete`；`config.baseUrl`（现有）
- Produces: 前端「监控」按钮与设置弹窗

- [x] **Step 1: 在 `Dashboard.vue` `<script setup>` 顶部，将 `Bell` 加入图标导入**

将：

```js
import { Top, Bottom, Plus, Minus, Delete, TrendCharts, Document, Histogram } from '@element-plus/icons-vue';
```

改为：

```js
import { Top, Bottom, Plus, Minus, Delete, TrendCharts, Document, Histogram, Bell } from '@element-plus/icons-vue';
```

- [x] **Step 2: 在「响应式数据」区（`const stockInfoVisible = ref(false);` 之后）新增监控弹窗状态与表单**

```js
// 监控设置弹窗
const showMonitorModal = ref(false);
const savingMonitor = ref(false);
const monitorForm = ref({
  userId: '6928fca14f206ada1b7a9269',
  symbol: '',
  stockName: '',
  takeProfitType: '',
  takeProfitValue: null,
  stopLossType: '',
  stopLossValue: null
});
```

- [x] **Step 3: 在 `</script>` 之前（`openReport` 函数之后）新增监控相关函数**

```js
// 打开监控设置弹窗（回显该股票已有规则）
async function openMonitorDialog(row) {
  monitorForm.value = {
    userId: '6928fca14f206ada1b7a9269',
    symbol: row.symbol,
    stockName: row.stockName,
    takeProfitType: '',
    takeProfitValue: null,
    stopLossType: '',
    stopLossValue: null
  };
  try {
    const response = await axios.post(`${BASE_URL}/monitor/list`, {
      userId: monitorForm.value.userId
    });
    if (response.data && response.data.state === 0 && Array.isArray(response.data.data)) {
      const existing = response.data.data.find(r => r.symbol === row.symbol);
      if (existing) {
        monitorForm.value.takeProfitType = existing.takeProfitType || '';
        monitorForm.value.takeProfitValue = existing.takeProfitValue ?? null;
        monitorForm.value.stopLossType = existing.stopLossType || '';
        monitorForm.value.stopLossValue = existing.stopLossValue ?? null;
      }
    }
    showMonitorModal.value = true;
  } catch (error) {
    ElMessage.error('获取监控规则失败');
    console.error('获取监控规则失败:', error);
  }
}

// 校验并保存监控规则
async function submitMonitor() {
  const hasTp = monitorForm.value.takeProfitType && monitorForm.value.takeProfitValue != null;
  const hasSl = monitorForm.value.stopLossType && monitorForm.value.stopLossValue != null;
  if (!hasTp && !hasSl) {
    ElMessage.warning('止盈或止损至少设置一项');
    return;
  }
  savingMonitor.value = true;
  try {
    const response = await axios.post(`${BASE_URL}/monitor/save`, {
      userId: monitorForm.value.userId,
      symbol: monitorForm.value.symbol,
      takeProfitType: hasTp ? monitorForm.value.takeProfitType : '',
      takeProfitValue: hasTp ? monitorForm.value.takeProfitValue : null,
      stopLossType: hasSl ? monitorForm.value.stopLossType : '',
      stopLossValue: hasSl ? monitorForm.value.stopLossValue : null
    });
    if (response.data && response.data.state === 0) {
      ElMessage.success('监控规则已保存');
      showMonitorModal.value = false;
    } else {
      ElMessage.error(response.data?.message || '保存监控规则失败');
    }
  } catch (error) {
    ElMessage.error('保存监控规则失败');
    console.error('保存监控规则失败:', error);
  } finally {
    savingMonitor.value = false;
  }
}

// 停用/删除监控规则
async function disableMonitor() {
  try {
    const response = await axios.post(`${BASE_URL}/monitor/delete`, {
      userId: monitorForm.value.userId,
      symbol: monitorForm.value.symbol
    });
    if (response.data && response.data.state === 0) {
      ElMessage.success('监控规则已删除');
      showMonitorModal.value = false;
    } else {
      ElMessage.error(response.data?.message || '删除失败');
    }
  } catch (error) {
    ElMessage.error('删除失败');
    console.error('删除监控规则失败:', error);
  }
}
```

- [x] **Step 4: 在持仓表格「操作」列（`label="操作" width="140"` 的 `<el-table-column>` 内）新增「监控」按钮，并将列宽改为 `200`**

将：

```html
<el-table-column label="操作" width="140">
```

改为：

```html
<el-table-column label="操作" width="200">
```

并在该列模板中 `查看报告` 按钮之后、`</template>` 之前插入：

```html
<el-button type="success" size="small" @click="openMonitorDialog(scope.row)" title="止盈止损监控">
  <el-icon><Bell /></el-icon>
</el-button>
```

- [x] **Step 5: 在 `</template>` 末尾（`page-footer` 之后、根 `<div>` 结束前）新增监控弹窗**

```html
<!-- 监控设置弹窗 -->
<el-dialog
  v-model="showMonitorModal"
  title="止盈止损监控设置"
  width="500px"
  center
  destroy-on-close
>
  <div class="monitor-stock-info">
    <span class="monitor-stock-name">{{ monitorForm.stockName }}（{{ monitorForm.symbol }}）</span>
  </div>
  <el-form label-width="110px">
    <el-form-item label="止盈方式">
      <el-select v-model="monitorForm.takeProfitType" placeholder="不设置">
        <el-option label="价格" value="PRICE" />
        <el-option label="收益率" value="RATE" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="monitorForm.takeProfitType" label="止盈阈值">
      <el-input-number
        v-model="monitorForm.takeProfitValue"
        :min="0.0001"
        :precision="4"
        :step="0.01"
        class="w-full"
      />
    </el-form-item>
    <el-form-item label="止损方式">
      <el-select v-model="monitorForm.stopLossType" placeholder="不设置">
        <el-option label="价格" value="PRICE" />
        <el-option label="收益率" value="RATE" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="monitorForm.stopLossType" label="止损阈值">
      <el-input-number
        v-model="monitorForm.stopLossValue"
        :precision="4"
        :step="0.01"
        class="w-full"
      />
    </el-form-item>
    <el-alert
      title="收益率型：止盈填正小数（如 0.10 表示 +10%），止损填负小数（如 -0.08 表示 -8%）"
      type="info"
      :closable="false"
      class="mt-2"
    />
  </el-form>
  <template #footer>
    <el-button type="danger" plain @click="disableMonitor">删除规则</el-button>
    <el-button @click="showMonitorModal = false">取消</el-button>
    <el-button type="primary" @click="submitMonitor" :loading="savingMonitor">保存</el-button>
  </template>
</el-dialog>
```

- [x] **Step 6: 在 `<style scoped>` 末尾追加两段样式**

```css
.monitor-stock-info {
  margin-bottom: 16px;
  padding: 10px 12px;
  background-color: #f5f7fa;
  border-radius: 6px;
}
.monitor-stock-name {
  font-weight: 600;
  color: #1f2937;
}
```

- [x] **Step 7: 手动验证**

1. `cd StockAI-Console && npm run dev`，访问 `/stock`。
2. 点击持仓列表某行「监控」按钮，弹出「止盈止损监控设置」。
3. 设置价格止盈、保存，重新打开该行应回显已保存规则。
4. 再次打开点击「删除规则」，应删除成功。

- [x] **Step 8: Commit**

```bash
git add StockAI-Console/src/components/Dashboard.vue
git commit -m "feat: 持仓列表新增止盈止损监控设置入口与表单"
```

---

## Task 10: 前端站内提醒铃铛 + 列表 + 标记已读（App.vue）

**Files:**
- Modify: `StockAI-Console/src/App.vue`

**Interfaces:**
- Consumes: 后端 `POST /notification/list`、`POST /notification/read`（Task 8）；`config.baseUrl`（现有）

- [x] **Step 1: 替换 `<script setup>` 顶部，引入 axios/config 与响应式 API**

将：

```js
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Menu as MenuIcon, Document, Setting, Bell, User } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const isDarkMode = ref(false)
```

替换为：

```js
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Menu as MenuIcon, Document, Setting, Bell, User } from '@element-plus/icons-vue'
import axios from 'axios'
import config from './config/config'

const BASE_URL = config.baseUrl
const USER_ID = '6928fca14f206ada1b7a9269'

const router = useRouter()
const route = useRoute()
const isDarkMode = ref(false)

// 站内提醒
const notifications = ref([])
const showNotificationDrawer = ref(false)
const loadingNotifications = ref(false)
const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)
```

- [x] **Step 2: 在 `toggleDarkMode` 函数之后新增提醒相关函数**

```js
async function fetchNotifications() {
  loadingNotifications.value = true
  try {
    const response = await axios.post(`${BASE_URL}/notification/list`, { userId: USER_ID })
    if (response.data && response.data.state === 0 && Array.isArray(response.data.data)) {
      notifications.value = response.data.data
    }
  } catch (error) {
    console.error('获取站内提醒失败:', error)
  } finally {
    loadingNotifications.value = false
  }
}

function openNotifications() {
  showNotificationDrawer.value = true
  fetchNotifications()
}

async function markOneRead(id) {
  try {
    const response = await axios.post(`${BASE_URL}/notification/read`, { userId: USER_ID, ids: [id] })
    if (response.data && response.data.state === 0) {
      const n = notifications.value.find(item => item.id === id)
      if (n) n.read = true
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

async function markAllRead() {
  const ids = notifications.value.filter(n => !n.read).map(n => n.id)
  if (ids.length === 0) return
  try {
    const response = await axios.post(`${BASE_URL}/notification/read`, { userId: USER_ID, ids })
    if (response.data && response.data.state === 0) {
      notifications.value.forEach(n => { n.read = true })
    }
  } catch (error) {
    console.error('标记全部已读失败:', error)
  }
}

onMounted(fetchNotifications)
```

- [x] **Step 3: 在模板顶部导航栏中，将原铃铛 `el-dropdown`（「无新消息」块）替换为带徽标的铃铛**

将：

```html
        <el-dropdown>
          <span class="el-dropdown-link header-action">
            <el-icon><Bell /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>无新消息</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
```

替换为：

```html
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
          <span class="el-dropdown-link header-action" @click="openNotifications">
            <el-icon><Bell /></el-icon>
          </span>
        </el-badge>
```

- [x] **Step 4: 在根 `<div class="common-layout">` 结束前（`</el-container>` 之后、根 `</div>` 之前）新增提醒抽屉**

```html
    <!-- 站内提醒抽屉 -->
    <el-drawer v-model="showNotificationDrawer" title="站内提醒" size="420px">
      <div v-loading="loadingNotifications" class="notification-list">
        <el-empty v-if="!loadingNotifications && notifications.length === 0" description="暂无提醒" />
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notification-item"
          :class="{ 'notification-unread': !n.read }"
        >
          <div class="notification-head">
            <el-tag :type="n.type === 'TAKE_PROFIT' ? 'success' : 'danger'" size="small">
              {{ n.type === 'TAKE_PROFIT' ? '止盈' : '止损' }}
            </el-tag>
            <span class="notification-stock">{{ n.stockName }}（{{ n.symbol }}）</span>
          </div>
          <div class="notification-content">{{ n.content }}</div>
          <div class="notification-foot">
            <span class="notification-time">{{ n.createdAt }}</span>
            <el-button v-if="!n.read" type="primary" link size="small" @click="markOneRead(n.id)">
              标记已读
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="markAllRead" :disabled="unreadCount === 0">全部已读</el-button>
      </template>
    </el-drawer>
```

- [x] **Step 5: 在 `<style scoped>` 末尾追加通知列表样式**

```css
.notification-list {
  min-height: 200px;
}
.notification-item {
  padding: 12px;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 6px;
}
.notification-unread {
  background-color: #eff6ff;
}
.notification-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.notification-stock {
  font-weight: 600;
  color: #1f2937;
}
.notification-content {
  font-size: 13px;
  color: #374151;
  margin-bottom: 6px;
}
.notification-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.notification-time {
  font-size: 12px;
  color: #9ca3af;
}
```

- [x] **Step 6: 手动验证**

1. `npm run dev`，顶部铃铛应显示未读徽标数（无提醒时为隐藏）。
2. 点击铃铛打开抽屉，展示提醒列表（未读高亮）。
3. 点击「标记已读」或「全部已读」，徽标数减少、列表刷新。

- [x] **Step 7: Commit**

```bash
git add StockAI-Console/src/App.vue
git commit -m "feat: 顶部导航新增站内提醒铃铛与消息抽屉"
```

---

## Task 11: 后端全量测试 + 前端构建 + 端到端联调

**Files:** 无新增（验证与修复）

- [x] **Step 1: 后端全量测试**

Run: 在 `d:\WorkSpace\MainProject\StockAI-master\StockAI` 执行 `./mvnw test`
Expected: 全部测试（含 `MonitorServiceTest`、`NotificationServiceTest`、`InAppNotificationSenderTest`、`StockAiApplicationTests`）通过，BUILD SUCCESS。

- [x] **Step 2: 前端构建**

Run: 在 `d:\WorkSpace\MainProject\StockAI-master\StockAI-Console` 执行 `npm run build`
Expected: 构建成功，无语法错误。

- [x] **Step 3: 端到端联调**

1. 启动后端 `./mvnw spring-boot:run`（MongoDB 本地 `localhost:27017`）。
2. 启动前端 `npm run dev`。
3. 访问 `/stock`，为某持仓股设置价格型止盈规则（阈值设为低于当前价，易触发）。
4. 手动触发一次判定：调用 `curl -X POST http://localhost:8888/monitor/save` 保存规则后，临时将 `application.yml` 的 `monitor.cron` 改为近期时间等待任务，或直接临时在 `StockController` 增加一次手动触发入口验证；确认 `notification` 集合出现未读提醒。
5. 前端刷新/打开铃铛，看到未读徽标与提醒内容；标记已读，徽标消除。

> 说明：若不便等待 cron，可通过在 `MonitorController` 中临时暴露一个仅联调用、验证后移除（或保留为 `POST /monitor/checkNow` 的调试端点）来触发 `monitorService.checkAll()`；注：tasks.md 未要求该端点，联调结束请移除，避免扩大范围。

- [x] **Step 4: 修复联调发现的问题后重复 Step 1/2**

- [x] **Step 5: Commit（如有修复）**

```bash
git add -A && git commit -m "fix: 联调修复止盈止损监控问题"
```

---

## Task 12: 飞书配置 + FeishuNotificationSender（tasks.md 6.1 / 6.2）

**Files:**
- Modify: `StockAI/src/main/resources/application.yml`（新增 `feishu.*`）
- Create: `StockAI/src/main/java/com/xian/stockai/service/notify/FeishuNotificationSender.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/notify/FeishuNotificationSenderTest.java`

**Interfaces:**
- Consumes: `Notification`（Task 1）、`NotificationSender`（Task 3）
- Produces: `FeishuNotificationSender implements NotificationSender`（`@ConditionalOnProperty(prefix="feishu", name="enabled", havingValue="true")` 门控；`buildPayload` / `sign` 为包级可见，供测试）

- [x] **Step 1: 在 `application.yml` 末尾追加配置**

```yaml
# 飞书自定义机器人推送（默认关闭）
feishu:
  enabled: false
  webhook-url: ""
  secret: ""
```

- [x] **Step 2: 写失败测试 `FeishuNotificationSenderTest`**

`StockAI/src/test/java/com/xian/stockai/service/notify/FeishuNotificationSenderTest.java`：

```java
package com.xian.stockai.service.notify;

import com.xian.stockai.po.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class FeishuNotificationSenderTest {

    private static final String WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/test";

    private FeishuNotificationSender sender(String secret) {
        return new FeishuNotificationSender(RestClient.builder(), WEBHOOK_URL, secret);
    }

    private Notification notification() {
        Notification n = new Notification();
        n.setUserId("u1");
        n.setSymbol("000001");
        n.setStockName("平安银行");
        n.setContent("【止盈提醒】平安银行(000001) 最新收盘价 15.00 已触发止盈");
        return n;
    }

    @Test
    void buildPayloadUsesTextMessageWithoutSignature() {
        Map<String, Object> payload = sender("").buildPayload(notification());
        assertEquals("text", payload.get("msg_type"));
        Map<?, ?> content = (Map<?, ?>) payload.get("content");
        assertEquals("【止盈提醒】平安银行(000001) 最新收盘价 15.00 已触发止盈", content.get("text"));
        assertNull(payload.get("timestamp"));
        assertNull(payload.get("sign"));
    }

    @Test
    void buildPayloadAddsSignatureWhenSecretPresent() {
        Map<String, Object> payload = sender("test-secret").buildPayload(notification());
        assertNotNull(payload.get("timestamp"));
        assertNotNull(payload.get("sign"));
        assertFalse(payload.get("sign").toString().isEmpty());
    }

    @Test
    void signMatchesHmacSha256Base64() {
        long timestamp = 1700000000L;
        String expected = hmacSha256Base64(timestamp + "\n" + "test-secret", "test-secret");
        assertEquals(expected, sender("test-secret").sign(timestamp, "test-secret"));
    }

    @Test
    void sendSwallowsHttpError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());
        FeishuNotificationSender sender = new FeishuNotificationSender(builder, WEBHOOK_URL, "");
        assertDoesNotThrow(() -> sender.send(notification()));
    }

    private String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [x] **Step 3: 运行测试确认失败（编译失败即红）**

Run: `./mvnw -q test -Dtest=FeishuNotificationSenderTest`
Expected: 编译失败（`FeishuNotificationSender` 不存在）。

- [x] **Step 4: 实现 `FeishuNotificationSender`**

`StockAI/src/main/java/com/xian/stockai/service/notify/FeishuNotificationSender.java`：

```java
package com.xian.stockai.service.notify;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xian.stockai.po.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @author xian
 * @description 飞书自定义机器人推送
 */
@Component
@ConditionalOnProperty(prefix = "feishu", name = "enabled", havingValue = "true")
@Slf4j
public class FeishuNotificationSender implements NotificationSender {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String secret;

    public FeishuNotificationSender(RestClient.Builder restClientBuilder,
                                    @Value("${feishu.webhook-url:}") String webhookUrl,
                                    @Value("${feishu.secret:}") String secret) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
        this.secret = secret;
    }

    @Override
    public void send(Notification notification) {
        try {
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                log.warn("飞书 webhook-url 未配置，跳过推送");
                return;
            }
            Map<String, Object> payload = buildPayload(notification);
            String response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .body(String.class);
            JSONObject node = JSON.parseObject(response);
            Integer code = node.getInteger("code");
            if (code == null || code != 0) {
                log.warn("飞书推送失败: code={}, msg={}", code, node.getString("msg"));
            } else {
                log.debug("飞书推送成功: userId={}, symbol={}", notification.getUserId(), notification.getSymbol());
            }
        } catch (Exception e) {
            log.error("飞书推送异常: userId={}, symbol={}", notification.getUserId(), notification.getSymbol(), e);
        }
    }

    Map<String, Object> buildPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msg_type", "text");
        Map<String, String> content = new HashMap<>();
        content.put("text", notification.getContent());
        payload.put("content", content);
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis() / 1000;
            payload.put("timestamp", timestamp);
            payload.put("sign", sign(timestamp, secret));
        }
        return payload;
    }

    String sign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new IllegalStateException("飞书加签失败", e);
        }
    }
}
```

- [x] **Step 5: 运行测试确认通过**

Run: `./mvnw -q test -Dtest=FeishuNotificationSenderTest`
Expected: 4 个测试全部通过。

- [x] **Step 6: Commit**

```bash
git add StockAI/src/main/resources/application.yml StockAI/src/main/java/com/xian/stockai/service/notify/FeishuNotificationSender.java StockAI/src/test/java/com/xian/stockai/service/notify/FeishuNotificationSenderTest.java
git commit -m "feat: 新增飞书自定义机器人推送通道"
```

---

## Task 13: MonitorService 多渠道接线 + 多通道单测（tasks.md 6.3 / 6.4）

**Files:**
- Modify: `StockAI/src/main/java/com/xian/stockai/service/MonitorService.java`（单 `NotificationSender` → `List<NotificationSender>`）
- Modify: `StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java`（改为手动构造注入 List + 新增多通道用例）

**Interfaces:**
- Consumes: `NotificationSender`（Task 3）、`FeishuNotificationSender`（Task 12，条件存在）
- Produces: `MonitorService` 构造器签名变为 `List<NotificationSender>`；`notify()` 遍历所有 sender

- [x] **Step 1: 改 `MonitorService` 注入与 `notify()`**

1. 字段 `private final NotificationSender notificationSender;` → `private final List<NotificationSender> notificationSenders;`
2. 构造器参数 `NotificationSender notificationSender` → `List<NotificationSender> notificationSenders`，赋值同步改名。
3. 顶部 import 新增 `java.util.List`。
4. `notify()` 末尾 `notificationSender.send(notification);` → 循环：

```java
        for (NotificationSender sender : notificationSenders) {
            sender.send(notification);
        }
```

- [x] **Step 2: 更新 `MonitorServiceTest`**

1. 删除 `@InjectMocks private MonitorService monitorService;`，改为手动构造（`@BeforeEach`）：

```java
    @BeforeEach
    void setUp() {
        monitorService = new MonitorService(
                positionMonitorRepository, positionRepository, marketQuotesRepository,
                etfDailyQuotesRepository, List.of(notificationSender));
    }
```

（保留现有 `@Mock ... NotificationSender notificationSender;` 字段与 `monitorService` 字段；顶部 import 新增 `java.util.List` 与 `org.junit.jupiter.api.BeforeEach`。）

2. 新增多通道用例（放在类末尾）：

```java
    @Test
    void notifyInvokesAllSenders() {
        NotificationSender sender2 = mock(NotificationSender.class);
        MonitorService multiService = new MonitorService(
                positionMonitorRepository, positionRepository, marketQuotesRepository,
                etfDailyQuotesRepository, List.of(notificationSender, sender2));
        PositionMonitor rule = priceTpRule();
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        multiService.checkRule(rule);

        verify(notificationSender).send(any());
        verify(sender2).send(any());
    }
```

- [x] **Step 3: 运行测试确认通过**

Run: `./mvnw -q test -Dtest=MonitorServiceTest`
Expected: 原 8 个用例 + `notifyInvokesAllSenders` 全部通过（9 个）。

- [x] **Step 4: 后端全量测试**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS（含 `FeishuNotificationSenderTest`、`MonitorServiceTest`、`NotificationServiceTest`、`InAppNotificationSenderTest`）。

- [x] **Step 5: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/MonitorService.java StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java
git commit -m "feat: MonitorService 改为 List<NotificationSender> 多渠道发送"
```

---

## Task 14: 端到端联调飞书推送（tasks.md 6.5）

**Files:** 无新增（验证与修复）

- [x] **Step 1: 后端全量测试 + 前端构建**

Run: 在 `StockAI` 执行 `./mvnw test`；在 `StockAI-Console` 执行 `npm run build`
Expected: 均成功。

- [x] **Step 2: 端到端联调（真实 webhook）**

1. 在飞书群添加自定义机器人，取得 webhook URL（可选加签密钥）。
2. `application.yml` 设 `feishu.enabled: true`、`webhook-url` 与 `secret`。
3. 启动后端 `./mvnw spring-boot:run`，构造触达现价并触发判定。
4. 验证：飞书群收到机器人文本消息，且 `notification` 集合仍生成站内提醒（两者互不影响）。

- [x] **Step 3: 修复联调发现的问题后重复 Step 1**

- [x] **Step 4: Commit（如有修复）**

```bash
git add -A && git commit -m "fix: 联调修复飞书推送问题"
```

---

## Self-Review 记录（执行时忽略，仅供核对）

- 覆盖核对：tasks.md 1.1/1.2 → Task 1；1.3 → Task 2；2.1 → Task 4；2.2 → Task 5；2.3 → Task 3；2.4 → Task 5；2.5 → Task 6；3.1 → Task 7；3.2 → Task 8；4.1 → Task 9；4.2 → Task 10；5.1/5.2 → Task 11。全部覆盖，无扩展范围。
- 类型一致性：`MonitorService` 公开方法签名在 Task 4 定义、Task 5 补齐，Controller（Task 7）与 Task（Task 6）引用一致；常量名称统一为 `TYPE_PRICE/TYPE_RATE/DIR_NONE/DIR_TAKE_PROFIT/DIR_STOP_LOSS`。
- 占位符扫描：无 TBD/TODO/「类似 Task N」等占位，所有代码块为实际内容。
