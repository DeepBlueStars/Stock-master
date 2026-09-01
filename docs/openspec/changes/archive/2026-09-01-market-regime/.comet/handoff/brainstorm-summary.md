# Brainstorm Summary

- Change: market-regime
- Date: 2026-08-30

## 确认的技术方案

- 技术栈：Python(etf-service) 补估值采集 + Java(StockAI) 判定与报告 + Vue(前端) 展示
- 数据缺口：估值分位由 etf-service 采集（`index_valuation` 集合），市场宽度不做
- 四指标：均线趋势、成交额、波动率、估值分位
- 四态：牛/熊/震荡/风险（风险为独立维度，可与牛熊叠加）
- 仓位建议：牛→激进、震荡→中性、熊→防御、风险→空仓
- AI 仅做总结，量化结论不依赖 AI；AI 失败仍输出量化结论
- 飞书推送复用现有能力；定时任务收盘后 15:30

## 关键取舍与风险

- 跨模块（Python+Java+Vue）：估值采集在 Python，判定在 Java，通过 MongoDB 解耦
- 市场宽度暂不做：减少数据依赖，后续可补
- 量化阈值需回测校准：抽到配置

## 测试策略

- Python：估值采集映射、分位计算
- Java：四态判定、仓位映射、AI 降级（mock AI）、记录读写
- 集成：从 MongoDB 读指数日线+估值 → 判定 → 记录

## Spec Patch

- 移除「市场宽度」指标（原 spec「综合均线、成交额、估值分位、波动率与市场宽度」→ 删除市场宽度）
- 新增「估值分位指标数据来源」需求（从 `index_valuation` 集合读取）
