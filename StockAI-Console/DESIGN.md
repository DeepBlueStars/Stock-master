# StockAI Console · 设计约定

一句话：这是一个**投资台账**——钱在纸上被分配、核对、签字。没有漂浮的卡片，只有一张纸、几条发丝线、一组右对齐的等宽数字，和一个像印章的强调色。

改任何页面前先读这一页，避免又退回「AI 生成后台」那种默认长相。

---

## 1. 颜色

定义在 `src/styles/tokens.css`，只在 `:root` 和 `.dark` 里各写一次。组件里**不写十六进制**，一律走变量。

| 变量 | 浅色 | 用法 |
| --- | --- | --- |
| `--canvas` | `#ECEBE7` | 页面底色（暖灰，不是 slate） |
| `--surface` | `#FFFFFF` | 纸面：卡片、弹窗、输入框 |
| `--surface-sunken` | `#F7F6F2` | 纸面里的凹陷区（左侧调整栏、表头） |
| `--ink` / `--ink-2` / `--ink-3` / `--ink-4` | `#14171A` / `#4B5157` / `#858B92` / `#A9AEB4` | 正文 → 次要 → 说明 → 装饰 |
| `--rule` / `--rule-strong` | `#E5E3DD` / `#CFCDC6` | 发丝线、分隔线 |
| `--accent` / `--accent-ink` | `#2F5D50` / `#234A40` | **唯一**强调色（松绿），只给主按钮、当前导航、选中态 |
| `--danger` / `--warn` | `#A4372C` / `#8A5A12` | 删除、报错 / 提示、未达标 |
| `--data-1…4` | `#1F3B4D` `#4E7C8A` `#B0802C` `#8F8A7C` | 资产类别：股票 / 债券 / 黄金 / 现金 |

**不要**再用 Element 默认的 `#409EFF` / `#67C23A` / `#E6A23C` / `#F56C6C`：它们和 A 股「红涨绿跌」的语义打架。

涨跌用 `#B3382C`（涨）和 `#2F6B4F`（跌）——低饱和，落在纸面上不刺眼。

## 2. 字体与数字

- 字体栈：`Inter → HarmonyOS Sans SC → PingFang SC → Microsoft YaHei UI → system-ui`。
- 字号只有六档：`11 / 12 / 13 / 15 / 17 / 21`（另有 28 给极端标题）。11px 只用于全大写标签、刻度、表头。
- **所有数字**加 `.num`（`tabular-nums`）。金额、比例、时间一律右对齐，位数才对得齐。
- 小数位固定：金额两位、比例一位。

## 3. 骨架

```html
<div class="page">                     <!-- max-width 1600（var(--page-max)）+ 32px 内边距 -->
  <div class="page-head">
    <div>
      <h1 class="page-title">总览</h1>
      <p class="page-sub">一句话说明这一页是干什么的</p>
    </div>
    <div class="page-meta">…</div>     <!-- 右侧的日期 / 计数 -->
  </div>

  <section class="paper">              <!-- 一张纸：1px rule + 16px 圆角 + 单层阴影 -->
    <div class="pane-head">
      <h2 class="pane-title">资产比例</h2>
      <span class="pane-note">说明文字</span>
    </div>
    …
  </section>
</div>
```

- 顶栏 56px，左侧 204px 导航栏，右侧 `stage` 滚动。
- 四个主页面（总览 / 决策台 / 股票 / 市场）统一用 `--page-max` 这张纸的宽度与 `32px` 左右内边距，切换页面时纸边不跳。`配置` 页是窄表单，单独收窄到 760px。
- **禁止卡片套卡片**。一张纸内部的分区靠 `1px` 横线和底色深浅，不靠第二层边框和阴影。
- 圆角只有三档：`16`（纸）/ `8`（控件、弹层）/ `4`（标签、小按钮）。

## 4. 组件纪律

- 表格：原生 `<table class="ledger">` 或 Element 表格 + 全局覆盖，表头 11px 灰色、无底色、下边一条 `--rule-strong`，行高 ≥ 40px。
- 空态：不要 3D 纸箱插画（`el-empty__image` 已全局隐藏）。一句 13px 说明 + 一个动作按钮。
- 图标：只用 `@element-plus/icons-vue` 的线性图标，14–16px，颜色 `--ink-3`。**不用 emoji 当图标**。
- 按钮：主操作一个 `type="primary"`；其余默认样式。按下 `scale(0.97)`。
- 行内操作（编辑 / 删除）：默认 `opacity: 0`，行 hover 或 focus-within 才出现。

## 5. 动效

克制到只剩三处，全部尊重 `prefers-reduced-motion`：

1. **比例带铺开**（`AllocationRibbon.vue`）：挂载后 `clip-path: inset(0 100% 0 0) → inset(0)`，900ms，`cubic-bezier(0.22,1,0.36,1)`。整页只此一次。
2. **路由切换**：`fade` + 6px 上移，260ms。
3. **微交互**：滑杆 knob hover `scale(1.45)`，按钮按下 `scale(0.97)`，行 hover 底色 `2.5% ink`。

不要：无限循环动画、`animationDelay: Math.random()`、`elasticOut`、渐变文字、彩色光晕。

## 6. 反面清单（改代码时自查）

- 渐变文字标题
- emoji 当图标
- 卡片里再套卡片
- 标题上方的 eyebrow / kicker 小字
- 「大数字 + 小标签」的 hero metric 三连块（要表达同类数据就用一条横排 + 发丝线分隔）
- 大于 1px 的彩色左边框
- 硬阴影、零偏移彩色光晕

改完跑一次机械检测：

```
C:\Users\xian\.agents\skills\impeccable\scripts\impeccable.cmd detect --json src/**/*.vue
```

## 7. 已知取舍

- 后端未启动时，金额全为 0，页面会显示「还没拿到持仓金额」的说明行——这是有意的，不要用假数据填。
