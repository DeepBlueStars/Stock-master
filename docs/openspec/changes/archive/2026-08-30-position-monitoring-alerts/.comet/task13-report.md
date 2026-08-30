# Task 13 报告：MonitorService 多渠道接线 + 多通道单测

## 状态
DONE

## 任务目标
将 `MonitorService` 的单一 `NotificationSender` 注入改为 `List<NotificationSender>` 多渠道循环发送，使站内提醒与（条件启用的）飞书推送同时生效；并新增多通道单测 `notifyInvokesAllSenders`。

## 变更内容

### 生产代码 `StockAI/src/main/java/com/xian/stockai/service/MonitorService.java`
1. 字段 `private final NotificationSender notificationSender;` → `private final List<NotificationSender> notificationSenders;`
2. 构造器参数 `NotificationSender notificationSender` → `List<NotificationSender> notificationSenders`，赋值同步改名。
3. `java.util.List` 已在原 import 中（第 21 行），无需新增。
4. `notify()` 末尾 `notificationSender.send(notification);` → 循环：
   ```java
   for (NotificationSender sender : notificationSenders) {
       sender.send(notification);
   }
   ```

### 测试代码 `StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java`
1. 删除 `@InjectMocks private MonitorService monitorService;`，改为普通字段 + `@BeforeEach void setUp()` 手动构造 `new MonitorService(..., List.of(notificationSender))`。
2. import 调整：移除 `org.mockito.InjectMocks`；新增 `org.junit.jupiter.api.BeforeEach` 与 `java.util.List`。
3. 类末尾新增 `notifyInvokesAllSenders` 用例：创建第二个 mock `sender2`，用 `List.of(notificationSender, sender2)` 构造第二个 `MonitorService`，触发止盈后 `verify(notificationSender).send(any())` + `verify(sender2).send(any())`。

## TDD 证据

### RED（先改测试，编译失败）
命令：`cd D:/WorkSpace/MainProject/StockAI-master/StockAI && mvn -q test -Dtest=MonitorServiceTest`

输出（关键行，编码为 GBK 乱码）：
```
[ERROR] COMPILATION ERROR :
[ERROR] .../MonitorServiceTest.java:[45,26] 不兼容的类型: 实际参数...java.util.List<E> 无法转换为 com.xian.stockai.service.notify.NotificationSender
[ERROR] .../MonitorServiceTest.java:[236,39] 不兼容的类型: ...java.util.List<E> 无法转换为 com.xian.stockai.service.notify.NotificationSender
[ERROR] Failed to execute goal ...:testCompile ... Compilation failure
```
第 45 行为 `setUp()` 中的 `List.of(notificationSender)`，第 236 行为 `notifyInvokesAllSenders` 中的 `List.of(notificationSender, sender2)`，证明构造器仍只接受单一 `NotificationSender`，属预期红。

### GREEN（改生产代码，全部通过）
命令：`cd D:/WorkSpace/MainProject/StockAI-master/StockAI && mvn test -Dtest=MonitorServiceTest`
```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in com.xian.stockai.service.MonitorServiceTest
[INFO] BUILD SUCCESS
```

### 全量测试
命令：`cd D:/WorkSpace/MainProject/StockAI-master/StockAI && mvn test`
```
MonitorServiceTest           Tests run: 11
NotificationServiceTest      Tests run: 3
FeishuNotificationSenderTest Tests run: 4
InAppNotificationSenderTest  Tests run: 1
StockAiApplicationTests      Tests run: 1
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
（FeishuNotificationSenderTest 期间出现一条 `ERROR ... 飞书推送异常` 日志，为 `sendSwallowsHttpError` 用例故意触发并吞掉异常，属预期，非测试失败。）

## 文件变更
- `StockAI/src/main/java/com/xian/stockai/service/MonitorService.java`
- `StockAI/src/test/java/com/xian/stockai/service/MonitorServiceTest.java`

## 提交
- `5a5c90a` feat: MonitorService 改为 List<NotificationSender> 多渠道发送

## 自查结论
- 构造器签名变更（第 5 参数 `NotificationSender` → `List<NotificationSender>`）与计划一致；`notify()` 循环遍历所有 sender。
- 测试由 `@InjectMocks` 改为手动构造，注入 `List.of(...)` 与生产代码签名对齐。
- 测试数量为 11（原 10 + 新增 1），与任务说明一致（计划中"原 8 个"为过期注释，忽略）。
- 未触碰计划范围外的文件。
- 风险信号：公共 API 契约变更（构造器签名）命中；非跨模块（仅 MonitorService 内部）；diff 31 insertions / 6 deletions（< 200 行）。
