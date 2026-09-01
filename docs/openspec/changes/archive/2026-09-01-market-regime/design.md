## Context

data-foundation 已提供主要宽基指数历史行情与估值数据。本 change 在其上构建市场状态判定能力。StockAI 后端为 Java/Spring Boot，已有 AI（qwen-max via Spring AI）与飞书推送（FeishuNotificationSender）。

## Goals / Non-Goals

**Goals:**
- 收盘后自动判定牛/熊/震荡/风险并给出仓位建议
- 量化规则可复现，AI 仅做总结
- 复用现有飞书推送与 AI 能力

**Non-Goals:**
- 不做盘中实时判定
- 不做个股择时

## Decisions

### 决策1：判定引擎
- **选择**：确定性量化规则打分（均线/成交额/估值分位/波动率/市场宽度）→ 映射到四态，AI 仅基于分数与指标生成自然语言总结
- **理由**：稳定可复现，避免大模型直接判定的不稳定与不可复现

### 决策2：状态判定结果存储
- **选择**：新增 MongoDB 集合 `market_regime_records`，每日一条，含状态、仓位建议、指标快照、AI 报告文本
- **理由**：便于历史回溯与前端查询

### 决策3：AI 总结方式
- **选择**：复用 Spring AI ChatClient，用结构化 prompt 将量化分数与指标注入，生成报告
- **理由**：复用现有 AI 配置，避免新增大模型依赖

## Risks / Trade-offs

- [量化规则阈值需要历史回测校准] → 阈值抽到配置，便于后续调参
- [AI 报告可能不稳定] → AI 仅做总结，量化结论不依赖 AI，AI 失败时仍输出量化结论
- [指数/估值数据缺失] → 判定前校验数据完整性，缺失时降级并记录日志
