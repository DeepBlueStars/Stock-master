# Task 1 报告：数据模型 PO（PositionMonitor、Notification）

## 状态

DONE

## 实现文件

1. `StockAI/src/main/java/com/xian/stockai/po/PositionMonitor.java` — `position_monitor` 集合映射（止盈止损监控规则）
2. `StockAI/src/main/java/com/xian/stockai/po/Notification.java` — `notification` 集合映射（站内提醒）

两者均按计划第 78–266 行逐字转写：PO 使用显式 getter/setter（非 Lombok `@Data`），`@Document(collection=...)` + `@Field(name=...)` + `@Indexed`，日期字段 `@JSONField` + `@JsonFormat`；布尔字段 getter 为 `getEnabled()` / `getRead()`。

## 编译验证

- 命令：在 `StockAI` 目录执行 `mvn -q compile`（项目无 Maven Wrapper，`./mvnw` 不存在，改用系统 Maven 3.6.3 + JDK 21，与 wrapper 等价）。
- 结果：退出码 0，无编译错误，BUILD SUCCESS。

## 提交

- 提交哈希：`e3a0e68fcd8fa079f39f49841b2dfe8ea3bbaf8b`（短哈希 `e3a0e68`）
- 提交信息：`feat: 新增持仓止盈止损监控规则与站内提醒 PO`
- 仅 add 上述两个 PO 文件，未纳入工作区其他无关改动。