# Brainstorm Summary

- Change: data-foundation
- Date: 2026-08-30

## 确认的技术方案

- 代码位置：沿用 etf-service（Python + FastAPI + APScheduler），新增 `datasource` / `normalize` / `services` / `tasks` 模块
- 数据源：akshare 主源 + baostock / tushare / 东方财富 / 新浪财经备用源，adapter 模式统一接口
- 统一格式：各数据源在适配器内做字段映射与清洗，统一转换为标准格式后入库
- 新集合：`index_daily_quotes`、`stock_daily_kline`、`stock_fundamentals`、`stock_industry_concept`、`index_constituents`（禁止改造已有集合）
- 密钥：tushare token 等通过 `.env` + `os.getenv` 读取（参考 etf-service 现有模式）
- 调度：日线 15:30 后增量；成分股每月；财务财报季；行业/概念每日
- 去重：行情 `symbol + trade_date`，财务 `symbol + report_date`，upsert 语义

## 关键取舍与风险

- 扩展 etf-service 而非新建服务：复用 MongoDB 写入与 APScheduler 模式，成本低；单机个人工具隔离收益不明显
- 多源备份：降低单点故障风险；代价是需维护多个适配器
- 统一格式层：屏蔽各数据源字段口径差异，切换数据源不影响上层消费方

## 测试策略

- 单元测试：字段映射/清洗、统一格式转换、去重逻辑
- 集成测试：mock 各数据源返回，验证主备自动切换
- 冒烟测试：真实拉取单只股票/指数验证全链路

## Spec Patch

无
