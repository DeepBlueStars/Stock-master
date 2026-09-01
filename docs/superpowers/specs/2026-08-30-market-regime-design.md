---
comet_change: market-regime
role: technical-design
canonical_spec: openspec
archived-with: 2026-09-01-market-regime
status: final
---

# market-regime 技术设计

## Context

data-foundation 已采集主要宽基指数历史日线（`index_daily_quotes`，OHLCV）与优质池个股数据。本 change 在其上构建「市场温度计」：收盘后自动判定市场处于牛/熊/震荡/风险，并给出大资金仓位建议。StockAI 后端为 Java/Spring Boot，已有 AI（qwen-max via Spring AI）与飞书推送（FeishuNotificationSender）。

## Goals / Non-Goals

**Goals:**
- 收盘后自动判定四态并给出仓位建议
- 量化规则可复现，AI 仅做总结
- 复用现有飞书推送与 AI 能力
- 补齐指数估值分位数据（`index_valuation`）

**Non-Goals:**
- 不做盘中实时判定
- 不做个股择时
- 不做市场宽度（涨跌家数）指标

## 架构（跨模块）

```
etf-service (Python)               StockAI (Java)              StockAI-Console (Vue)
├─ valuation_service.py   ──写──▶  index_valuation
└─ tasks/ 定时采集估值
                                    MarketRegimeService ◀──读── index_daily_quotes + index_valuation
                                    MarketRegimeRecord ──写──▶ market_regime_records
                                    MarketRegimeController ◀──查询──▶ 温度计页面
                                    MarketRegimeTask (15:30 cron)
                                    AI 总结 (qwen-max) + 飞书推送
```

## 数据流

```
定时任务(15:30) → MarketRegimeService
  → 读 index_daily_quotes（均线/成交额/波动率）
  → 读 index_valuation（估值分位）
  → 量化规则打分 → 四态 + 仓位建议
  → AI 生成报告（量化分数注入 prompt）
  → 写 market_regime_records
  → 飞书推送（可选）
```

## 量化判定逻辑

四个指标（从 MongoDB 读取历史数据计算）：

| 指标 | 计算 | 判定方向 |
|------|------|---------|
| 均线趋势 | MA20/MA60/MA250 排列 | 多头→牛、空头→熊、纠缠→震荡 |
| 成交额 | 近5日均量 vs 60日均量 | 放量/缩量 |
| 波动率 | 20日年化波动率 | 高波动→风险 |
| 估值分位 | PE/PB 历史分位 | 高位→风险 |

**四态打分制**（确定性规则，阈值抽到配置）：

- 牛：均线多头 + 量能温和 + 波动率低 + 估值分位中低
- 熊：均线空头 + 缩量 + 估值分位低但趋势向下
- 震荡：均线纠缠 + 波动率中位
- 风险：高波动率 + 放量下跌 + 估值分位高位（独立风险维度，可与牛/熊叠加）

## 仓位建议映射

| 状态 | 建议 |
|------|------|
| 牛 | 激进（高仓位） |
| 震荡 | 中性（中等仓位） |
| 熊 | 防御（低仓位） |
| 风险 | 空仓/防御 |

## 数据模型

**index_valuation（etf-service 写，新集合）**：`index_code, trade_date, pe, pb, pe_percentile, pb_percentile, data_source, updated_at`

**market_regime_records（Java 写，新集合）**：`date, regime, position_advice, indicators(快照), report, created_at`

## AI 总结

- 复用 Spring AI ChatClient（模型可配置，`application.yml` 中支持 deepseek/qwen，后续可扩展其他模型，不硬编码单一模型）
- 结构化 prompt 注入量化分数与指标 → 生成自然语言报告
- AI 失败降级：仍输出量化结论（report 为量化摘要），不影响判定结果

## 定时任务

- 收盘后 cron（沿用现有 monitor cron 模式：`0 30 15 * * ?`）

## 错误处理与降级

- 指数/估值数据缺失 → 判定前校验数据完整性，缺失时降级（仅用可用指标）并记录日志
- AI 失败 → 仍输出量化结论
- 飞书推送失败 → 不影响记录生成

## 测试策略

- Python：估值采集映射、分位计算
- Java：四态判定、仓位映射、AI 降级（mock AI）、记录读写
- 集成：从 MongoDB 读指数日线+估值 → 判定 → 记录

## 风险与权衡

- [量化阈值需回测校准] → 阈值抽到配置，便于调参
- [跨模块（Python+Java+Vue）] → 估值采集在 Python，判定在 Java，接口通过 MongoDB 解耦
- [估值数据源不稳定] → 采集失败保留旧数据，判定降级
