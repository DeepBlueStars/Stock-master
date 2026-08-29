## 1. 后端数据模型

- [x] 1.1 新增 `PositionMonitor` PO（字段与 design.md 一致），验证：编译通过且字段映射符合 design
- [x] 1.2 新增 `Notification` PO，验证：编译通过
- [ ] 1.3 新增 `PositionMonitorRepository` 与 `NotificationRepository`，验证：接口编译通过且查询方法符合 service 需求

## 2. 后端监控与提醒逻辑

- [ ] 2.1 实现 `MonitorService` 的规则新增/修改/删除/启停，验证：单元测试覆盖保存与停用
- [ ] 2.2 实现价格/收益率止盈止损判定（复用股票 `market_quotes` / ETF `etf_daily_quotes` 价格源），验证：单元测试覆盖四种模式
- [ ] 2.3 实现 `NotificationSender` 接口与 `InAppNotificationSender`（写入 notification 集合），验证：单元测试触发后生成未读提醒
- [ ] 2.4 实现去重逻辑（`lastTriggerDirection`），验证：单元测试证明同一方向不重复提醒、穿越回安全区间后再次提醒
- [ ] 2.5 实现 `@Scheduled` 定时判定任务，验证：手动触发后仅对满足条件的启用规则生成提醒

## 3. 后端接口

- [ ] 3.1 新增监控规则 Controller 端点（新增/修改/删除/查询），验证：Swagger 或 curl 返回 `Result` 正常
- [ ] 3.2 新增站内提醒 Controller 端点（列表/标记已读），验证：curl 返回列表与已读状态更新正常

## 4. 前端（StockAI-Console）

- [ ] 4.1 持仓列表新增监控设置入口与止盈止损表单，验证：页面可设置并回显规则
- [ ] 4.2 新增站内提醒展示（未读提示 + 消息列表 + 标记已读），验证：页面展示提醒并可标记已读

## 5. 联调验证

- [ ] 5.1 后端编译与测试通过，验证：在 StockAI 目录运行 `./mvnw test` 全部通过
- [ ] 5.2 前后端端到端联调：设置规则 → 模拟收盘价触达 → 前端看到未读提醒，验证：手工端到端流程通过