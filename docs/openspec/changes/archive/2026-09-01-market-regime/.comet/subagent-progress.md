# Subagent Progress — market-regime

## 配置

- build_mode: subagent-driven-development
- tdd_mode: tdd
- review_mode: standard
- language: zh-CN
- plan: docs/superpowers/plans/2026-08-30-market-regime.md
- design-doc: docs/superpowers/specs/2026-08-30-market-regime-design.md

## 任务清单（9 个）

1. etf-service 估值 schema + Mongo 集合 + akshare PE/PB 采集
2. etf-service 估值定时更新任务
3. StockAI Java PO + Repository（market_regime_records）
4. StockAI Java 量化判定 + 仓位映射纯函数
5. StockAI Java 指标计算 + 记录串联 + 飞书推送
6. StockAI Java AI 总结 + 降级
7. StockAI Java Controller + 定时任务 + 配置
8. StockAI-Console Vue 温度计页面
9. StockAI-Console Vue 历史记录查询展示

## 当前进度

- 当前任务: 全部完成
- 阶段: final-review

## Task 完成记录

### Task 1（tasks.md 1.1）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: 14041f4
- RED: `ModuleNotFoundError: No module named 'app.services.valuation_service'`
- GREEN: 2 passed（test_valuation_service.py），合并 44 passed
- 变更文件: services/valuation_service.py、tests/test_valuation_service.py、normalize/schema.py、database/mongo.py、datasource/akshare_source.py
- 风险信号: 未命中（valuation_service.py 的 Optional 导入未使用，属计划原文，保留）

### Task 2（tasks.md 1.2）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: 991cba6
- RED: `AttributeError: module 'app.tasks.etf_task' has no attribute 'valuation_service'`
- GREEN: 4 passed（test_data_tasks.py），合并 45 passed
- 变更文件: tasks/etf_task.py、tests/test_data_tasks.py
- 风险信号: 未命中

### Task 3（tasks.md 3.1）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: a94ee60
- RED: 编译失败（找不到符号 IndexDailyQuotes/IndexValuation/MarketRegimeRecord）
- GREEN: 37 passed（17 新增 + 20 既有）
- 变更文件: po/IndexDailyQuotes、IndexValuation、RegimeIndicators、MarketRegimeRecord；dao/3 个 Repository；2 个测试类
- 风险信号: 未命中

### Task 4（tasks.md 2.1 + 2.2）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: ba6d2f6
- RED: 编译失败（找不到符号 RegimeRule）
- GREEN: 43 passed（6 新增）
- 变更文件: service/RegimeRule.java、service/RegimeRuleTest.java
- 风险信号: 未命中（四态优先级 风险>牛>熊>震荡；阈值抽参数传入）

### Task 5（tasks.md 5.2）
- 状态: DONE_WITH_CONCERNS（已裁决，无需 per-task reviewer）
- 提交: c2ba607
- RED: 编译失败（找不到符号 MarketRegimeService）
- GREEN: 47 passed（4 新增）
- 变更文件: service/MarketRegimeService.java、service/MarketRegimeServiceTest.java
- 风险信号: 计划测试 mock 数据顺序与降序查询契约相反
- Ruling: 计划 Task 5 测试 mock 升序生成，与仓库 findTop250...OrderByTradeDateDesc 降序契约相反导致 reverse 后误判熊市；implementer 修正 mock 为降序，生产代码按计划原文实现，未受影响，不派 per-task reviewer。

### Task 6（tasks.md 4.1 + 4.2）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: 8bb678d
- RED: UnnecessaryStubbingException（AI mock 桩未被使用，证明降级特性缺失）
- GREEN: 48 passed（1 新增）
- 变更文件: service/MarketRegimeService.java、service/MarketRegimeServiceTest.java
- 风险信号: 未命中（AI 模型不硬编码，通过 ChatClient 注入；降级回退 buildFallbackReport）

### Task 7（tasks.md 5.1）
- 状态: DONE_WITH_CONCERNS（已裁决，无需 per-task reviewer）
- 提交: c3df259
- RED: 编译失败（找不到符号 MarketRegimeController/MarketRegimeTask）
- GREEN: 54 passed（6 新增）
- 变更文件: controller/MarketRegimeController.java、task/MarketRegimeTask.java、2 个测试类、application.yml
- 风险信号: 硬编码 API key（历史遗留）+ application.yml 带入既有 provider 切换改动
- Ruling: application.yml 硬编码 deepseek/qwen key 为历史遗留（非本 change 引入）；Task 7 提交 application.yml 时带入了既有 provider 切换改动，属轻微范围泄漏，key 本就在仓库中无新增泄露。不派 per-task reviewer，交用户评估是否需清理 key。

### Task 8（tasks.md 6.1）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: 43a4bb3
- 验证: npm run build 成功（2085 modules，exit 0）
- 变更文件: components/MarketRegime.vue、router/index.js、App.vue
- 风险信号: 未命中（chunk>500kB 与 npm cache 均为环境/既有问题）

### Task 9（tasks.md 6.2）
- 状态: DONE（review_mode standard，未命中风险信号，跳过 per-task reviewer）
- 提交: 9d2f241
- 验证: npm run build 成功（2085 modules，exit 0）
- 变更文件: components/MarketRegime.vue
- 风险信号: 未命中（前端无测试框架，计划以 build 为验收标准）

## 关键约定

- AI 模型可配置（application.yml 支持 deepseek/qwen，后续可扩展），不硬编码单一模型
- 飞书推送用 ObjectProvider<FeishuNotificationSender> 注入（避免飞书关闭时产生站内提醒）
- Java 用系统 mvn（无 mvnw wrapper）
- akshare 指数估值用 stock_index_pe_lg / stock_index_pb_lg（乐咕乐股），列名用 row_get 候选列名防御
