# Comet Design Handoff

- Change: market-dashboard-merge
- Phase: design
- Mode: compact
- Context hash: ccec5d917aff5146724efdc05b34b4ab9a0366f62340ce96a2c86d7e2d243e45

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## docs/openspec/changes/market-dashboard-merge/proposal.md

- Source: docs/openspec/changes/market-dashboard-merge/proposal.md
- Lines: 1-30
- SHA256: 6d662dd625edc7f547e3bc3db77eb1a328508171f81971720da301c3387fa1f1

```md
## Why

StockAI-Console 的「温度计」与「选股」两个页面各自只承载少量卡片，却都限制了最大宽度（900px / 1100px），在常见的 1440px 及以上屏幕上两侧留白明显；同时「当前市场状态与仓位建议」和「候选股与热点方向」本属于同一决策链路，用户需要在两个菜单项之间来回切换，信息割裂、单页密度偏低。现在将两者合并为一个高密度市场页面，可以在不新增后端接口的前提下提升信息利用率和决策效率。

## What Changes

- 新增统一的「市场」页面（路由 `/market`），在同一屏内组合市场温度计、选股榜单、热点方向与历史记录。
- 重新设计页面排版：顶部状态与关键指标摘要条，中部左「市场温度」/右「选股榜单」双栏，下部热点方向双栏，底部历史记录；宽屏充分利用横向空间，窄屏自动堆叠。
- 复用现有接口：`/market-regime/latest`、`/market-regime/history`、`/screening/latest`、`/hotspot/latest`，后端不改动。
- 侧边导航由「温度计」「选股」两个入口合并为「市场」一个入口。
- **BREAKING（前端路由）**：旧路径 `/regime`、`/screener` 保留为自动跳转到 `/market` 的兼容重定向，避免已有书签/外链失效。
- 删除旧的 `MarketRegime.vue`、`StockScreener.vue` 组件，避免重复实现和死代码。
- 各数据区块独立请求、独立加载态与空态：任一接口失败不阻塞其他区块展示。

## Capabilities

### New Capabilities

- `market-dashboard`: 控制台「市场」页面的组合展示行为，包括单页信息分区、双栏响应式排版、旧路由兼容重定向、各数据区块的独立加载/空态/错误降级。

### Modified Capabilities

<!-- 无：本次仅调整控制台页面组合与排版，market-regime 与 stock-screening 的服务端行为需求不变。 -->

## Impact

- 前端代码：`StockAI-Console/src/App.vue`（侧边导航）、`StockAI-Console/src/router/index.js`（路由与重定向）、新增 `StockAI-Console/src/components/MarketOverview.vue`、删除 `MarketRegime.vue` 与 `StockScreener.vue`。
- 后端接口：无变更，继续使用现有 `MarketRegimeController`、`ScreeningController`、`HotspotController`。
- 依赖：无新增依赖，沿用 Vue 3、Element Plus、axios。
- 验证方式：`StockAI-Console` 下执行 `npm run build`，并人工检查 `/market`、`/regime`、`/screener` 三个入口。

```

## docs/openspec/changes/market-dashboard-merge/design.md

- Source: docs/openspec/changes/market-dashboard-merge/design.md
- Lines: 1-101
- SHA256: 3b9eb2ebca3e566e61318c98a8ab83e140e907b3da2348d908d021f0534b8516

[TRUNCATED]

```md
## Context

参见 `proposal.md` 的 Why。当前两个页面各自只有一个窄容器：

- `MarketRegime.vue`：`max-width: 900px`，单卡片（状态徽标 + 报告 + 指标）+ 历史表格。
- `StockScreener.vue`：`max-width: 1100px`，AI 报告卡片 + 三风格榜单卡片 + 热点卡片。

二者的数据均来自后端「收盘后任务写库、前端只读最新记录」的模式，接口互不依赖。控制台主内容区 `App.vue` 已提供 260px 侧栏、可滚动主区、Element Plus 卡片样式和深色模式基础样式。

## Goals / Non-Goals

**Goals:**

- 用一个页面、一次进入承载完整的「市场环境 → 选股 → 热点 → 历史」决策信息。
- 宽屏下消除两侧大面积留白，通过摘要条 + 双栏布局提升首屏信息量。
- 保持数据请求相互独立，单接口失败不影响其他区块。
- 旧路由可用、导航入口收敛为一个，删除旧组件避免重复实现。

**Non-Goals:**

- 不修改后端接口、数据结构、定时任务或 AI 生成逻辑。
- 不新增图表、实时推送或新的筛选交互。
- 不重构 `Dashboard.vue`、`TradePlan.vue` 等其他页面。
- 不引入新的前端依赖或测试框架。

## Decisions

### 1. 新建单一页面组件 `MarketOverview.vue`，删除两个旧组件

合并后的页面是一个新的组合视图，旧的 `MarketRegime.vue` 与 `StockScreener.vue` 不再被引用。保留它们会形成两套并存实现，容易产生样式与逻辑漂移，因此直接删除；旧路径通过路由重定向保留兼容。

替代方案：保留旧组件并让新页面嵌入它们。否决原因：两个组件的根容器各自带 `max-width` 与卡片间距，嵌套后仍会留白，且需要双向适配样式，收益低于重写。

### 2. 排版：顶部摘要条 + 主体双栏 + 热点双栏 + 历史折叠

页面结构（自上而下）：

```
┌──────────────────────────────────────────────────────────────┐
│ 市场  [交易日] [刷新]                                         │
├──────────────────────────────────────────────────────────────┤
│ 状态摘要：市场状态 | 仓位建议 | 最高综合分 | 候选股数 | 热点数 │
├───────────────────────────────┬──────────────────────────────┤
│ 市场温度（左，8/24）           │ 选股榜单（右，16/24）         │
│ 徽标/建议/日期                │ AI 报告                       │
│ 指标网格（MA/量比/波动/分位）  │ 三风格 Tabs + Top20 表格      │
│ AI 报告（可滚动）              │                              │
├───────────────────────────────┴──────────────────────────────┤
│ 热点方向：行业 Top10（左 12/24） | 概念 Top10（右 12/24）      │
├──────────────────────────────────────────────────────────────┤
│ 历史记录（折叠面板，默认展开）：最近 30 条市场状态             │
└──────────────────────────────────────────────────────────────┘
```

- 使用 `el-row :gutter="16"` + `el-col :xs="24" :lg="8"` / `:lg="16"` 实现响应式：`lg`（≥1200px）双栏，`md` 及以下自动堆叠。
- 页面容器不再设置 `max-width`，改为 `width: 100%`；通过 `App.vue` 的 `padding` 控制边距。
- 摘要条使用紧凑指标卡（label + value），在无数据时显示 `-`，避免出现空白条。
- 历史记录用 `el-collapse` 收纳，默认展开，桌面端不额外占用首屏过多高度，窄屏可折叠。

替代方案：仅把两张卡片并排。否决原因：原卡片内部仍是纵向长列表，并排后表格过窄、可读性差；因此左侧放指标与报告，右侧放宽表格。

### 3. 数据获取：`Promise.allSettled` + 分区状态

- 四个请求：`/market-regime/latest`、`/market-regime/history?limit=30`、`/screening/latest`、`/hotspot/latest`。
- 统一封装 `postApi(path, params)`，返回 `response.data`；按 `state === 0` 判定成功。
- 每个区块维护独立的 `loading`、`error` 与数据状态；`refreshAll()` 使用 `Promise.allSettled` 并行请求，任一失败只更新对应区块错误信息。
- 失败时使用 `ElMessage` 提示一次，区块内保留空态/错误文案，不清空其他已加载数据。

替代方案：串行 `await`。否决原因：四个接口互不依赖，串行会拉长首屏等待时间。

### 4. 路由与导航

- 新增路由 `{ path: '/market', name: '市场', component: MarketOverview }`。
- 旧路径使用 `{ path: '/regime', redirect: '/market' }` 与 `{ path: '/screener', redirect: '/market' }`；使用 `redirect` 而非 `alias`，保证地址栏统一为 `/market`。
- `App.vue` 删除「温度计」「选股」两个 `el-menu-item`，新增一个「市场」项，图标沿用 `TrendCharts`，`index="/market"`。

替代方案：保留两个菜单项分别指向 `/market` 的不同锚点。否决原因：仍会让用户误以为存在两个页面，且锚点在长页面中体验不稳定。

### 5. 视觉与深色模式


```

Full source: docs/openspec/changes/market-dashboard-merge/design.md

## docs/openspec/changes/market-dashboard-merge/tasks.md

- Source: docs/openspec/changes/market-dashboard-merge/tasks.md
- Lines: 1-26
- SHA256: 890d7f412c7801452120af6e61303d0063ecc089a38843e3f19f0253d02f3819

```md
## 1. 统一页面骨架与数据层

- [ ] 1.1 新建 `StockAI-Console/src/components/MarketOverview.vue`，搭建「标题/交易日/刷新」头部、顶部摘要条、市场温度与选股双栏、热点方向双栏、历史记录折叠区的骨架；完成后文件存在且 `npm run build` 不因模板语法报错
- [ ] 1.2 在 `MarketOverview.vue` 中实现 `/market-regime/latest`、`/market-regime/history?limit=30`、`/screening/latest`、`/hotspot/latest` 的并行请求（`Promise.allSettled`），为四个区块分别维护 loading、error 与数据状态；完成后代码检查确认没有串行 await 且单接口失败只影响自身区块
- [ ] 1.3 实现市场温度区块：状态徽标与颜色语义、仓位建议、交易日、MA20/MA60/MA250、量比、波动率、PE/PB 分位、AI 报告；完成后对照规格「保留原有信息完整性」逐项核对字段均已渲染
- [ ] 1.4 实现选股区块：AI 综合报告与降级标记、均衡/价值/成长三个 Tab、Top 20 表格（综合分、四因子、长期价值评级、入选理由）；完成后确认三个 Tab 可切换且表格字段齐全
- [ ] 1.5 实现热点方向区块：行业 Top 10 与概念 Top 10 双栏表格，包含热度、股票数、涨停数、单日/5 日均涨幅、炒作风险标签与代表股；完成后确认两类榜单字段齐全
- [ ] 1.6 实现历史记录区块：最近 30 条市场状态的历史表格，包含日期、状态、仓位建议、报告摘要，并放入默认展开的折叠面板；完成后确认表格按时间倒序展示

## 2. 路由、导航与旧页面清理

- [ ] 2.1 在 `StockAI-Console/src/router/index.js` 中新增 `/market` 路由指向 `MarketOverview.vue`，并将 `/regime`、`/screener` 改为 `redirect: '/market'`；完成后运行构建并检查路由配置含三条记录且重定向目标正确
- [ ] 2.2 修改 `StockAI-Console/src/App.vue` 侧边导航，删除「温度计」「选股」两个菜单项，新增单个「市场」入口（`index="/market"`）；完成后确认导航项数量减少且默认选中逻辑使用 `route.path`
- [ ] 2.3 删除 `StockAI-Console/src/components/MarketRegime.vue` 与 `StockAI-Console/src/components/StockScreener.vue`，并全局搜索确认无组件引用残留；完成后搜索 `MarketRegime`、`StockScreener` 仅在变更文档或历史记录中出现

## 3. 响应式排版与视觉一致性

- [ ] 3.1 页面容器改为 `width: 100%` 且不设窄 `max-width`，使用 `el-row/el-col` 的 `:xs="24" :lg="8"/"16"` 与热点 `:lg="12"` 实现宽屏双栏、窄屏堆叠；完成后通过浏览器宽度断点（1440/1024/768）检查无横向滚动
- [ ] 3.2 统一卡片、表格与标签样式：表格使用 `size="small"`、设置最大高度与溢出提示，状态/风险颜色沿用既有语义，并补充深色模式下的局部样式；完成后在深色模式开关下检查文本与背景对比度
- [ ] 3.3 为每个区块补充加载态、空态与错误态文案，摘要条在无数据时显示 `-`；完成后模拟接口失败或空数据，确认其他区块不受影响

## 4. 集成验证

- [ ] 4.1 在 `StockAI-Console` 目录执行 `npm run build`，确认构建成功且无新增警告（如有警告需记录原因）
- [ ] 4.2 启动开发服务器，人工访问 `/market`、`/regime`、`/screener`，确认统一页面展示、旧路径均跳转到 `/market`、导航只有一个「市场」入口
- [ ] 4.3 对照 `specs/market-dashboard/spec.md` 的全部 Scenario 做逐条核对，确认宽屏/窄屏排版、深色模式、空态与数据完整性均符合

```

## docs/openspec/changes/market-dashboard-merge/specs/market-dashboard/spec.md

- Source: docs/openspec/changes/market-dashboard-merge/specs/market-dashboard/spec.md
- Lines: 1-109
- SHA256: 75536a2d82c620f627fce186654ddbe6d335eb59ead2fc674a75ac38d1b536e8

[TRUNCATED]

```md
## Purpose

定义 StockAI-Console「市场」页面的对外可见行为：把原本分散在市场温度计与选股两个页面的信息组合到同一页面，并通过高密度、响应式的排版让宽屏空间得到充分利用，同时保持旧入口可用。

## ADDED Requirements

### Requirement: 统一的市场页面

控制台 SHALL 提供单一「市场」页面，在同一页面内同时展示市场状态与仓位建议、选股榜单、热点方向和历史记录，且侧边导航 MUST 只保留一个对应入口。

#### Scenario: 从导航进入市场页面

- **WHEN** 用户在侧边导航点击「市场」
- **THEN** 系统打开统一市场页面，并在同一屏内展示市场温度、选股榜单和热点方向区块

#### Scenario: 单一导航入口

- **WHEN** 用户查看侧边导航
- **THEN** 导航中不再同时出现「温度计」和「选股」两个独立入口，只出现「市场」

### Requirement: 高密度响应式排版

市场页面 SHALL 在宽屏下使用横向分区提高信息密度，MUST NOT 以固定的窄内容宽度在页面两侧留下大面积空白；在窄屏下各分区 MUST 自动堆叠且不出现横向滚动。

#### Scenario: 宽屏充分利用横向空间

- **WHEN** 浏览器可视宽度不小于 1200px
- **THEN** 页面内容宽度随主内容区自适应铺满，市场温度与选股榜单以左右双栏展示，热点方向以左右双栏展示

#### Scenario: 窄屏自动堆叠

- **WHEN** 浏览器可视宽度小于 992px
- **THEN** 市场温度、选股榜单、热点方向和历史记录按纵向顺序堆叠，表格可横向滚动且页面本身不产生横向滚动

#### Scenario: 顶部摘要信息

- **WHEN** 市场状态与选股结果均已加载
- **THEN** 页面顶部摘要区同时展示当日市场状态、仓位建议、基准交易日、最高综合分及候选股数量

### Requirement: 旧路由兼容

系统 SHALL 保留旧路径 `/regime` 与 `/screener` 作为兼容入口，访问任一旧路径时 MUST 自动跳转到统一市场页面，且不得出现空白页或 404。

#### Scenario: 访问旧温度计路径

- **WHEN** 用户访问 `/regime`
- **THEN** 浏览器地址最终变为 `/market` 并展示统一市场页面

#### Scenario: 访问旧选股路径

- **WHEN** 用户访问 `/screener`
- **THEN** 浏览器地址最终变为 `/market` 并展示统一市场页面

### Requirement: 区块独立加载与降级

每个数据区块 SHALL 独立请求并独立呈现加载态、空态和错误提示；任一接口失败 MUST NOT 清空或阻塞其他已成功加载的区块。

#### Scenario: 单个接口失败

- **WHEN** 选股接口请求失败而市场状态接口成功
- **THEN** 市场温度区块正常展示数据，选股区块展示错误提示或空态，页面其他区块仍可用

#### Scenario: 暂无数据

- **WHEN** 某个接口返回成功但无最新记录
- **THEN** 对应区块展示明确的空态说明，而不是空白区域

#### Scenario: 手动刷新

- **WHEN** 用户点击页面刷新操作
- **THEN** 系统重新请求全部市场数据区块，并在请求期间显示加载状态

### Requirement: 保留原有信息完整性

统一页面 MUST 完整保留原两个页面的信息：市场状态（含状态、仓位建议、三项均线、量比、波动率、PE/PB 分位、AI 报告与日期）、选股（含 AI 综合报告、均衡/价值/成长三类榜单、综合分与四因子分、长期价值评级、入选理由）、热点方向（行业与概念 Top 10、热度、涨停数、涨幅、炒作风险与代表股）以及最近 30 条市场状态历史。

#### Scenario: 市场状态信息完整

- **WHEN** 市场状态记录存在
- **THEN** 页面展示状态徽标、仓位建议、交易日、AI 报告以及全部量化指标

```

Full source: docs/openspec/changes/market-dashboard-merge/specs/market-dashboard/spec.md
