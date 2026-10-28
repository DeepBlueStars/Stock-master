## 1. 统一页面骨架与数据层

- [x] 1.1 新建 `StockAI-Console/src/components/MarketOverview.vue`，搭建「标题/交易日/刷新」头部、顶部摘要条、市场温度与选股双栏、热点方向双栏、历史记录折叠区的骨架；完成后文件存在且 `npm run build` 不因模板语法报错
- [x] 1.2 在 `MarketOverview.vue` 中实现 `/market-regime/latest`、`/market-regime/history?limit=30`、`/screening/latest`、`/hotspot/latest` 的并行请求（`Promise.allSettled`），为四个区块分别维护 loading、error 与数据状态；完成后代码检查确认没有串行 await 且单接口失败只影响自身区块
- [x] 1.3 实现市场温度区块：状态徽标与颜色语义、仓位建议、交易日、MA20/MA60/MA250、量比、波动率、PE/PB 分位、AI 报告；完成后对照规格「保留原有信息完整性」逐项核对字段均已渲染
- [x] 1.4 实现选股区块：AI 综合报告与降级标记、均衡/价值/成长三个 Tab、Top 20 表格（综合分、四因子、长期价值评级、入选理由）；完成后确认三个 Tab 可切换且表格字段齐全
- [x] 1.5 实现热点方向区块：行业 Top 10 与概念 Top 10 双栏表格，包含热度、股票数、涨停数、单日/5 日均涨幅、炒作风险标签与代表股；完成后确认两类榜单字段齐全
- [x] 1.6 实现历史记录区块：最近 30 条市场状态的历史表格，包含日期、状态、仓位建议、报告摘要，并放入默认展开的折叠面板；完成后确认表格按时间倒序展示

## 2. 路由、导航与旧页面清理

- [x] 2.1 在 `StockAI-Console/src/router/index.js` 中新增 `/market` 路由指向 `MarketOverview.vue`，并将 `/regime`、`/screener` 改为 `redirect: '/market'`；完成后运行构建并检查路由配置含三条记录且重定向目标正确
- [x] 2.2 修改 `StockAI-Console/src/App.vue` 侧边导航，删除「温度计」「选股」两个菜单项，新增单个「市场」入口（`index="/market"`）；完成后确认导航项数量减少且默认选中逻辑使用 `route.path`
- [x] 2.3 删除 `StockAI-Console/src/components/MarketRegime.vue` 与 `StockAI-Console/src/components/StockScreener.vue`，并全局搜索确认无组件引用残留；完成后搜索 `MarketRegime`、`StockScreener` 仅在变更文档或历史记录中出现

## 3. 响应式排版与视觉一致性

- [x] 3.1 页面容器改为 `width: 100%` 且不设窄 `max-width`，使用 `el-row/el-col` 的 `:xs="24" :lg="8"/"16"` 实现主区宽屏双栏，热点方向 `:lg="24" :xl="12"`（常规宽屏上下排列、超宽屏双栏）；完成后通过浏览器宽度断点（1920/1440/1024/768）检查无横向滚动
- [x] 3.2 统一卡片、表格与标签样式：表格使用 `size="small"`、设置最大高度与溢出提示，状态/风险颜色沿用既有语义，并补充深色模式下的局部样式；完成后在深色模式开关下检查文本与背景对比度
- [x] 3.3 为每个区块补充加载态、空态与错误态文案，摘要条在无数据时显示 `-`；完成后模拟接口失败或空数据，确认其他区块不受影响

## 4. 集成验证

- [x] 4.1 在 `StockAI-Console` 目录执行 `npm run build`，确认构建成功且无新增警告（如有警告需记录原因）
- [x] 4.2 启动开发服务器，人工访问 `/market`、`/regime`、`/screener`，确认统一页面展示、旧路径均跳转到 `/market`、导航只有一个「市场」入口
- [x] 4.3 对照 `specs/market-dashboard/spec.md` 的全部 Scenario 做逐条核对，确认宽屏/窄屏排版、深色模式、空态与数据完整性均符合
