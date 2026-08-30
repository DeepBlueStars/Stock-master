# Task 12 报告：飞书配置 + FeishuNotificationSender（tasks.md 6.1 / 6.2）

## 状态

DONE

## 提交

- `8d857ea feat: 新增飞书自定义机器人推送通道`

## 改动文件（严格限定范围）

1. 修改 `StockAI/src/main/resources/application.yml` — 末尾追加 `feishu:` 配置块（enabled: false / webhook-url: "" / secret: ""）。
2. 新建 `StockAI/src/main/java/com/xian/stockai/service/notify/FeishuNotificationSender.java` — 第二个 `NotificationSender` 实现，`@ConditionalOnProperty(prefix="feishu", name="enabled", havingValue="true")` 门控；`RestClient` + 可选加签（HMAC-SHA256 → Base64）；推送失败仅记日志不抛出。
3. 新建 `StockAI/src/test/java/com/xian/stockai/service/notify/FeishuNotificationSenderTest.java` — 4 个用例覆盖 payload 结构、加签、HMAC 比对、失败吞异常。

未触碰任何其他文件（包括仓库中已存在的 `etf-service/.../mongo.cpython-310.pyc` 改动）。

## TDD 证据

### RED（先写测试，确认编译失败）

命令：`cd D:/WorkSpace/MainProject/StockAI-master/StockAI && mvn -q test -Dtest=FeishuNotificationSenderTest`

相关输出（关键失败原因）：

```
[ERROR] COMPILATION ERROR :
[ERROR] .../FeishuNotificationSenderTest.java:[22,13] 找不到符号
  符号:   类 FeishuNotificationSender
  位置: 类 com.xian.stockai.service.notify.FeishuNotificationSenderTest
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.1:testCompile (default-testCompile) on project stockai-service: Compilation failure
```

结论：`FeishuNotificationSender` 尚不存在，测试编译失败，符合预期 RED。

### GREEN（实现后确认通过）

命令：`cd D:/WorkSpace/MainProject/StockAI-master/StockAI && mvn test -Dtest=FeishuNotificationSenderTest`

相关输出：

```
[INFO] Running com.xian.stockai.service.notify.FeishuNotificationSenderTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.609 s
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

（注：`sendSwallowsHttpError` 用例中控制台打印的 `ERROR ... 飞书推送异常` 堆栈来自 `send()` 内部 `log.error` 吞掉 HTTP 500 的预期行为，非测试失败。）

## 全量测试

命令：`cd D:/WorkSpace/MainProject/StockAI-master/StockAI && mvn test`

```
[INFO] Running com.xian.stockai.service.MonitorServiceTest        Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.xian.stockai.service.NotificationServiceTest    Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.xian.stockai.service.notify.FeishuNotificationSenderTest  Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.xian.stockai.service.notify.InAppNotificationSenderTest    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.xian.stockai.StockAiApplicationTests             Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 自检发现

- 实现完全照搬 plan 中 Task 12 提供的代码，未做任何偏离。
- `buildPayload` / `sign` 为包级可见（无 `public`），与测试同包，可正常访问，符合 plan 设计。
- 加签字符串 `timestamp + "\n" + secret` 与测试中 `hmacSha256Base64` 的构造一致，`signMatchesHmacSha256Base64` 通过。
- `FeishuNotificationSender` 仅在 `feishu.enabled=true` 时注册为 bean，默认 `enabled: false`，不影响现有 `InAppNotificationSender` 及 Spring 上下文装配（`StockAiApplicationTests` 通过）。
- 环境差异已按说明处理：无 `./mvnw`，改用 `mvn`；后端根目录 `StockAI` 下执行。
- 无新增依赖；`RestClient.Builder` 由 Spring Boot 自动配置，`MockRestServiceServer` 来自 `spring-test`（`spring-boot-starter-test` 内置）。

## 风险信号

- 安全敏感面（加解密/密钥凭证）：是 — 涉及飞书 webhook secret 的 HMAC-SHA256 加签与密钥配置项（但仅加签算法，无密钥硬编码，默认关闭）。
- 公共 API 契约变更：否（新增实现类，不改接口）。
- 跨模块改动：否（仅后端 notify 包 + application.yml）。
- diff > 200 行：否（178 insertions）。
