# 验证报告：持仓止盈止损监控（position-monitoring-alerts）

> 验证日期：2026-08-30
> 验证模式：full（任务 19 > 3，变更文件 45 > 8）
> 产物语言：zh-CN

## Summary

| 维度 | 状态 |
| --- | --- |
| Completeness | 19/19 任务完成；delta spec 7 个 Requirement 均实现 |
| Correctness | 全部验收场景有测试覆盖，后端测试与前端构建通过 |
| Coherence | 实现符合 Design Doc；1 个 WARNING（配置凭据）、2 个 SUGGESTION |

## 检查项

### 1. tasks.md 全部任务已完成

- [x] 19/19 全部勾选，与实现、提交记录一致（`git log` 中每个任务均有对应提交）。

### 2. 实现符合 design.md 高层设计决策

- [x] 新增 `position_monitor` / `notification` 集合与 PO、Repository。
- [x] 判定逻辑：价格/收益率 × 止盈/止损 四模式，`compareTo` 比较。
- [x] 去重状态机 `lastTriggerDirection`（NONE/TAKE_PROFIT/STOP_LOSS），穿越安全区间后重新提醒。
- [x] `MonitorTask` 定时任务（`@Scheduled`，cron 可配置，默认 15:30）。
- [x] `NotificationSender` 接口 + `InAppNotificationSender` 站内实现。
- [x] REST 端点：`/monitor/save|list|delete|enable`、`/notification/list|read`，统一 `Result<T>` 包装、POST + JSON。
- [x] 前端：`Dashboard.vue` 持仓列表监控入口与表单、`App.vue` 提醒铃铛（未读徽标）与抽屉。
- 注：change 级 `design.md` Non-Goals 仍写「本期不接入飞书」，但飞书通道为后续范围扩张（proposal.md、delta spec、Design Doc 均已记录）。属文档未同步，见 SUGGESTION-2。

### 3. 实现符合 Design Doc（docs/superpowers/specs/2026-08-29-position-monitoring-alerts-design.md）

- [x] `MonitorService`：规则 CRUD（upsert、默认 enabled=true、lastTriggerDirection=NONE）、价格源分支（股票 `marketQuotesRepository.findByCode`、ETF `etfDailyQuotesRepository.findTopBySymbolOrderByUpdatedAtDesc`，symbol 以 1/5 开头）、收益率基于 `avgCostPrice`、现价缺失/均价为 0 跳过。
- [x] `FeishuNotificationSender`：`@ConditionalOnProperty(feishu.enabled=true)` 门控、`RestClient` 发送、可选 HMAC-SHA256 加签、失败吞异常不抛。
- [x] `MonitorService.notify()` 注入 `List<NotificationSender>` 多渠道循环。
- [x] 提醒文案格式 `【止盈提醒】股票名(代码) 最新收盘价 X 已触发止盈`。
- [x] 边界条件：单任务单规则最多一条提醒、价格缺失跳过、删除/停用后不再遍历。

### 4. 能力规格场景全部通过

| Scenario | 覆盖证据 |
| --- | --- |
| 新增价格型止盈止损规则 | `MonitorServiceTest.saveCreatesEnabledRuleWithNoneDirection` |
| 修改并停用规则 | `save`（upsert 覆盖）+ `enableUpdatesRule` |
| 价格模式触达止盈 | `priceTakeProfitTriggersWhenCloseAboveTarget` |
| 收益率模式触达止损 | `rateStopLossTriggersWhenRateBelowTarget` |
| 收盘价触达阈值（定时判定） | `checkRule`/`checkAll` 判定路径 + ETF 用例 `etfUsesEtfDailyQuotesPriceSource` |
| 触达产生站内提醒 | `InAppNotificationSender` 写入 `notification` 集合；`MonitorServiceTest` 各触发用例 `verify(notificationSender).send(any())` |
| 价格穿越回安全区间后再次触达 | `sameDirectionDoesNotDuplicateAndResetsAfterReturnToSafeZone` |
| 查询未读提醒 | `NotificationServiceTest`（`list` 按时间倒序） |
| 标记提醒为已读 | `NotificationServiceTest`（`markRead`） |
| 启用飞书推送后触达 | `notifyInvokesAllSenders`（多通道）；`FeishuNotificationSenderTest.buildPayload*` |
| 飞书推送失败不影响站内提醒 | `FeishuNotificationSenderTest.sendSwallowsHttpError`；`send()` 内 catch 全部异常 |

### 5. proposal.md 目标已满足

- [x] 监控规则管理（新增/修改/删除/启停）与价格/收益率双模式。
- [x] 定时基于日线收盘价判定触达并生成站内提醒（未读）。
- [x] 可选飞书推送（全局 webhook 配置启用，失败不影响站内提醒）。
- [x] 前端持仓列表入口与提醒展示。

### 6. delta spec 与 Design Doc 无矛盾

- [x] delta spec 含飞书 Requirement；Design Doc 有「飞书通知通道」章节，二者一致。
- 说明：change 级 `design.md` 的 Non-Goals 文本未随飞书范围扩张同步，但不构成 delta spec 与 Design Doc 的矛盾。

### 7. Design Doc 可定位

- [x] `docs/superpowers/specs/2026-08-29-position-monitoring-alerts-design.md` 存在且与当前 change 关联（`comet_change: position-monitoring-alerts`）。

## 构建与测试证据

| 命令 | 退出码 | 结果 |
| --- | --- | --- |
| `mvn -q test`（StockAI 后端） | 0 | 全部测试通过（含 MonitorService / NotificationService / FeishuNotificationSender / InAppNotificationSender） |
| `npm run build`（StockAI-Console 前端） | 0 | 2083 modules transformed，构建成功 |

## Issues

### WARNING（需用户决策）

- **W-1 飞书 webhook 凭据硬编码且启用**：[application.yml](file:///d:/WorkSpace/MainProject/StockAI-master/StockAI/src/main/resources/application.yml) 中 `feishu.enabled: true`、`webhook-url` 为真实地址（手动端到端验证时配置）。属当前 change 的 dirty 改动。AGENTS.md 禁止硬编码敏感信息；webhook 属凭据，入库存在泄露风险。
  - **决策（2026-08-30，用户确认）**：选择「恢复默认配置」——`enabled: false`、`webhook-url: ""`，真实地址由运行时配置提供。该修复经 `verify-fail` 回到 build 阶段执行。

### SUGGESTION

- **S-1 无关改动混入本 change 提交**：`etf-service/app/database/mongo.py` 的本地连接配置（localhost）随 docs 提交 `4c46f60` 入库，与本 change 无关。已提交、非 dirty，建议后续独立处理（回退或单独提交）。
  - **处置（2026-08-30）**：接受为已知遗留，不在本 change 处理，不影响本 change 验证结论。
- **S-2 change 级 design.md 未同步飞书范围**：Non-Goals 仍写「本期不接入飞书」。Design Doc 与 delta spec 已覆盖，建议归档时在 design.md 补充范围扩张说明。
  - **处置（2026-08-30）**：接受；Design Doc 与 delta spec 已完整记录飞书通道，verify 阶段不修改 design.md，交由 archive 阶段同步。

## W-1 修复闭环确认（2026-08-30）

- application.yml 已恢复默认配置（`enabled: false`、`webhook-url: ""`、`secret: ""`），与 HEAD 一致（clean）。
- 修复后 `mvn -q test` 退出码 0 全部通过；飞书 `@ConditionalOnProperty` 门控在默认关闭下不创建 bean，不影响现有测试。
- W-1 已解决。

## Final Assessment

实现完整、测试与构建通过、场景覆盖充分。W-1（飞书凭据入库）已按用户决策修复并闭环；S-1/S-2 已记录处置。**验证通过，可进入 archive。**
