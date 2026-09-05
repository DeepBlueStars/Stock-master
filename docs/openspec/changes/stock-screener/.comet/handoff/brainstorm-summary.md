# Brainstorm Summary

- Change: stock-screener
- Date: 2026-09-01

## 确认的技术方案

- 榜单形态：多风格榜（价值型/成长型/均衡型三套权重各出 Top 20）
- 热点窗口：单日 + 5日双窗口（5日热度 60% + 单日热度 40%）
- AI 总结：每运行一份综合报告（榜单解读 + 热点解读 + 炒作风险提示），失败降级纯量化文本
- 存储架构：方案 A —— 全池因子得分每日一份全量快照（`screening_scores`，每日一文档内嵌全池得分数组），榜单由快照按权重派生存入 `screening_results`；支持历史回测校准权重
- 因子算法：池内横截面百分位 0~100；估值（PE/PB 反向，PE≤0 得 0）、成长（营收/净利增速等权）、质量（ROE 60%+毛利率 40%）、动量（20 日收益率）；权重外置 application.yml（价值 40/30/20/10、成长 10/20/40/30、均衡 25/25/30/20）
- 长期价值评级：质量 60%+成长 40% → ≥65 高 / 45~65 中 / <45 低 / 缺失 未知
- 热点炒作标注：方向成分股质量分中位数 <40 且热度 ≥60 → 纯炒作风险高；≥60 → 有基本面支撑；其余中性
- 新增只读 PO：StockFundamentals / StockIndustryConcept / StockDailyKline / IndexConstituents；新集合：screening_scores / screening_results / hotspot_results（trade_date 唯一索引）
- 模块：ScreeningService / HotspotService / ScreeningReportService / ScreeningTask（cron 18:00 MON-FRI）/ ScreeningController（/screening/latest|history|run、/hotspot/latest）
- 前端：StockScreener.vue + 路由 /screener + App.vue 菜单「选股」；AI 报告卡片 + 三风格榜 tabs + 行业/概念热点卡片（炒作风险 el-tag）

## 关键取舍与风险

- [财务数据滞后] → 取最近可用 report_date（≤1年），结果标注 data_date
- [权重主观] → 权重外置 yml；全量快照支持离线回测调参
- [涨停家数口径] → 仅基于优质池约 800 只，结果标注口径
- [因子缺失] → 置 null 按剩余因子重归一化；缺失 ≥2 不入榜
- [概念股重叠致热点重复] → 行业/概念分榜展示，接受重叠
- [AI 不可用] → 降级纯量化文本，榜单与热点不受影响
- [存储增长] → 每日 3 个文档（快照约 200KB），可接受；后续可加 TTL

## 测试策略

- 单测：百分位方向与 PE 为负、缺失重归一化、三风格权重合成、长期评级阈值、热点聚合与炒作标注阈值、AI 降级
- 集成：mvn test 全绿；前端 npm run build 通过

## Spec Patch

无（多风格榜、双窗口热点均为 delta spec 中“生成候选股列表”“识别市场热点方向”的实现细化，未改变需求范围）
