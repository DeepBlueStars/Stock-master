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
