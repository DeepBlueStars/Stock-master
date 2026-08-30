# 任务最终修复报告

## 背景

Code-review 发现：`FeishuNotificationSender.send()` 的 `catch (Exception e)` 块中，`log.error(..., e)` 会把完整 throwable 打印到日志。飞书 webhook URL 内嵌 bot 访问令牌，`RestClient.retrieve()` 失败时异常 message 会携带完整请求 URL，从而把令牌泄露到日志，违反"不记录密钥/凭证"意图。

## 修改内容

文件：`StockAI/src/main/java/com/xian/stockai/service/notify/FeishuNotificationSender.java`

仅改动 `send()` 的 catch 块，将 `log.error(..., e)`（打印完整 throwable + 堆栈）改为只打印异常类名：

```java
} catch (Exception e) {
    log.error("飞书推送异常: userId={}, symbol={}, error={}",
            notification.getUserId(), notification.getSymbol(), e.getClass().getSimpleName());
}
```

未改动其它任何行为：`send()` 其余逻辑、`buildPayload()`、`sign()`、构造函数、`@ConditionalOnProperty` 门控均保持原样。

## 验证结果

- 聚焦测试：`mvn -q test -Dtest=FeishuNotificationSenderTest` — BUILD SUCCESS（日志行现为 `error=InternalServerError`，无堆栈）。
- 全量测试：`mvn -q test` — BUILD SUCCESS（exit code 0）。

## 提交

- `fix: 飞书推送异常日志不再打印 webhook URL 令牌`
