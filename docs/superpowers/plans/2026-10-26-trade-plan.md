---
change: trade-plan
design-doc: docs/superpowers/specs/2026-10-26-trade-plan-design.md
base-ref: df0af979f32006c05e555d0604be00c5133d1709
---

# 交易计划（trade-plan）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 买入后自动生成可解释的交易计划（止盈止损/批次/补仓/卖出），一键联动止盈止损提醒，并在每周六生成交易计划周报与前端展示。

**Architecture:** 后端新增 `service/tradeplan` 包（指标计算 → 计划计算 → 计划服务），计划落 MongoDB `trade_plans`；周报由定时任务组装数据 + ChatClient 按两个 prompt 资源生成，落 `trade_plan_summary`；前端新增 `/trade-plan` 页面（顶部周报折叠区块 + 逐仓卡片）。

**Tech Stack:** Java 21 / Spring Boot 3.5 / Spring Data MongoDB / Spring AI ChatClient / JUnit5 + Mockito；Vue 3 + Element Plus + axios。

**Spec:** `docs/superpowers/specs/2026-10-26-trade-plan-design.md`

## Global Constraints

- 产物与提交信息使用中文；代码注释与日志使用中文。
- 交易计划生成失败不得影响买入结果（`StockService.buy` 内 try-catch，仅 `log.error`）。
- 所有价格保留 2 位小数（`RoundingMode.HALF_UP`），比例保留 4 位小数。
- 参数默认值：`atr-period=14`、`stop-atr-multiplier=2.0`、`risk-reward=2.0`、`support-resistance-window=20`、`high-volatility-threshold=0.03`、`mid-volatility-threshold=0.02`、`min-kline-count=15`、`degraded-stop-loss-rate=0.05`、`degraded-take-profit-rate=0.10`、`summary-retention-weeks=12`、`summary-cron=0 0 8 ? * SAT`。
- 单股仅保留一条当前计划（`user_id + symbol` 唯一），重复买入覆盖。
- 周报章节顺序不可变：一、组合概览；二、本周买卖回顾；三、当前持仓交易计划；四、行业分布；五、风险与关注；六、下周关注方向；七、下周行动清单；八、免责声明。
- 不新增第三方依赖（前端 Markdown 渲染用组件内轻量函数，不引入 marked 等库）。
- 所有新增接口沿用 `Result<T>` 包装与 `POST` + `@RequestBody` 风格。

---

## 文件结构

| 文件 | 责任 |
|---|---|
| `StockAI/src/main/java/com/xian/stockai/config/TradePlanProperties.java` | 交易计划参数配置 |
| `StockAI/src/main/java/com/xian/stockai/po/StockDailyKline.java`（改） | 补 `open/high/low` 只读映射 |
| `StockAI/src/main/java/com/xian/stockai/service/tradeplan/IndicatorCalculator.java` | ATR / 支撑 / 阻力 |
| `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanCalculator.java` | 计划字段与解释文本计算 |
| `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanService.java` | 生成/刷新/查询/联动 |
| `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanSummaryDataAssembler.java` | 周报数据组装（含交易回放） |
| `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanSummaryService.java` | 周报生成/校验/降级/存储 |
| `StockAI/src/main/java/com/xian/stockai/task/TradePlanSummaryTask.java` | 周六 08:00 定时任务 |
| `StockAI/src/main/java/com/xian/stockai/po/TradePlan.java` | `trade_plans` 文档 |
| `StockAI/src/main/java/com/xian/stockai/po/TradePlanSummary.java` | `trade_plan_summary` 文档 |
| `StockAI/src/main/java/com/xian/stockai/dao/TradePlanRepository.java` | 计划仓储 |
| `StockAI/src/main/java/com/xian/stockai/dao/TradePlanSummaryRepository.java` | 周报仓储 |
| `StockAI/src/main/java/com/xian/stockai/vo/TradePlanRes.java` 等 4 个 VO | 接口出入参 |
| `StockAI/src/main/java/com/xian/stockai/controller/TradePlanController.java` | 6 个接口 |
| `StockAI/src/main/resources/prompts/trade-plan-weekly-report.md` | 周报约束 prompt |
| `StockAI/src/main/resources/prompts/trade-plan-weekly-template.md` | 周报模板 prompt |
| `StockAI-Console/src/components/TradePlan.vue` | 交易计划页 |
| `StockAI-Console/src/components/TradePlan/WeeklySummaryBlock.vue` | 顶部周报区块 |
| `StockAI-Console/src/components/TradePlan/TradePlanCard.vue` | 逐仓卡片 |
| `StockAI-Console/src/router/index.js`、`src/App.vue`（改） | 路由与菜单 |

---

### Task 1: ATR 与关键位计算（tasks 1.1 / 1.2）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/config/TradePlanProperties.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/po/StockDailyKline.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/tradeplan/IndicatorCalculator.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/tradeplan/IndicatorCalculatorTest.java`

**Interfaces:**
- Produces: `IndicatorCalculator#atr(List<StockDailyKline> ascKlines, int period) -> BigDecimal|null`、`support(...)->BigDecimal|null`、`resistance(...)->BigDecimal|null`；`TradePlanProperties` 全部 getter。

- [x] **Step 1: 写失败测试**

```java
package com.xian.stockai.service.tradeplan;

import com.xian.stockai.po.StockDailyKline;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorCalculatorTest {

    private final IndicatorCalculator calculator = new IndicatorCalculator();

    private StockDailyKline kline(String date, String high, String low, String close) {
        StockDailyKline k = new StockDailyKline();
        k.setTradeDate(date);
        k.setHigh(new BigDecimal(high));
        k.setLow(new BigDecimal(low));
        k.setClose(new BigDecimal(close));
        return k;
    }

    @Test
    void atrMatchesWilderRecursion() {
        // 16 根K线：前14根 high-low=2.00 且跳空为0 → 初始 ATR=2.000000
        // 第15、16 根 TR=4.00 → ATR15=(2*13+4)/14=2.142857，ATR16=(2.142857*13+4)/14=2.275510
        List<StockDailyKline> klines = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String close = (i < 14) ? "10.00" : "12.00";
            klines.add(kline("2026-01-" + String.format("%02d", i + 1), "11.00", "9.00", close));
        }
        BigDecimal atr = calculator.atr(klines, 14);
        assertNotNull(atr);
        assertEquals(0, new BigDecimal("2.275510").compareTo(atr));
    }

    @Test
    void atrReturnsNullWhenKlinesInsufficient() {
        List<StockDailyKline> klines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            klines.add(kline("2026-01-" + String.format("%02d", i + 1), "11.00", "9.00", "10.00"));
        }
        assertNull(calculator.atr(klines, 14));
    }

    @Test
    void supportAndResistanceUseRecentWindow() {
        List<StockDailyKline> klines = new ArrayList<>();
        klines.add(kline("2026-01-01", "100.00", "50.00", "90.00")); // 窗口外，不参与
        for (int i = 0; i < 20; i++) {
            klines.add(kline("2026-02-" + String.format("%02d", i + 1), "12.00", "10.00", "11.00"));
        }
        klines.add(kline("2026-02-21", "15.00", "11.00", "14.00"));
        assertEquals(0, new BigDecimal("10.00").compareTo(calculator.support(klines, 20)));
        assertEquals(0, new BigDecimal("15.00").compareTo(calculator.resistance(klines, 20)));
    }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=IndicatorCalculatorTest`
Expected: 编译失败（`IndicatorCalculator` / `StockDailyKline#getHigh` 不存在）

- [x] **Step 3: 实现**

`TradePlanProperties`（仿 `ScreeningProperties`，`@Component` + `@ConfigurationProperties(prefix = "trade-plan")`）：

```java
private int atrPeriod = 14;
private BigDecimal stopAtrMultiplier = new BigDecimal("2.0");
private BigDecimal riskReward = new BigDecimal("2.0");
private int supportResistanceWindow = 20;
private BigDecimal highVolatilityThreshold = new BigDecimal("0.03");
private BigDecimal midVolatilityThreshold = new BigDecimal("0.02");
private int minKlineCount = 15;
private BigDecimal degradedStopLossRate = new BigDecimal("0.05");
private BigDecimal degradedTakeProfitRate = new BigDecimal("0.10");
private int summaryRetentionWeeks = 12;
```
（含全部 getter/setter）

`StockDailyKline` 追加：

```java
@Field(name = "open")
private BigDecimal open;

@Field(name = "high")
private BigDecimal high;

@Field(name = "low")
private BigDecimal low;
```
（含 getter/setter）

`IndicatorCalculator`（`@Component`，纯计算）：

```java
public BigDecimal atr(List<StockDailyKline> ascKlines, int period) {
    if (ascKlines == null || ascKlines.size() < period + 1) {
        return null;
    }
    List<BigDecimal> trs = new ArrayList<>();
    for (int i = 1; i < ascKlines.size(); i++) {
        StockDailyKline cur = ascKlines.get(i);
        StockDailyKline prev = ascKlines.get(i - 1);
        if (cur.getHigh() == null || cur.getLow() == null || prev.getClose() == null) {
            continue;
        }
        BigDecimal tr = cur.getHigh().subtract(cur.getLow());
        BigDecimal hc = cur.getHigh().subtract(prev.getClose()).abs();
        BigDecimal lc = cur.getLow().subtract(prev.getClose()).abs();
        trs.add(tr.max(hc).max(lc));
    }
    if (trs.size() < period) {
        return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (int i = 0; i < period; i++) {
        sum = sum.add(trs.get(i));
    }
    BigDecimal atr = sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    for (int i = period; i < trs.size(); i++) {
        atr = atr.multiply(BigDecimal.valueOf(period - 1)).add(trs.get(i))
                .divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }
    return atr;
}

public BigDecimal support(List<StockDailyKline> ascKlines, int window) {
    return extreme(ascKlines, window, StockDailyKline::getLow, Comparator.naturalOrder());
}

public BigDecimal resistance(List<StockDailyKline> ascKlines, int window) {
    return extreme(ascKlines, window, StockDailyKline::getHigh, Comparator.reverseOrder());
}

private BigDecimal extreme(List<StockDailyKline> ascKlines, int window,
                           Function<StockDailyKline, BigDecimal> getter,
                           Comparator<BigDecimal> comparator) {
    if (ascKlines == null || ascKlines.isEmpty()) {
        return null;
    }
    int from = Math.max(0, ascKlines.size() - window);
    return ascKlines.subList(from, ascKlines.size()).stream()
            .map(getter).filter(Objects::nonNull).min(comparator).orElse(null);
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=IndicatorCalculatorTest`
Expected: PASS（3 个测试）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/config/TradePlanProperties.java StockAI/src/main/java/com/xian/stockai/po/StockDailyKline.java StockAI/src/main/java/com/xian/stockai/service/tradeplan/IndicatorCalculator.java StockAI/src/test/java/com/xian/stockai/service/tradeplan/IndicatorCalculatorTest.java
git commit -m "feat(trade-plan): 新增 ATR 与支撑阻力计算及参数配置"
```

---

### Task 2: 计划文档与仓储（tasks 3.1 前半）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/TradePlan.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/TradePlanRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/vo/TradePlanRes.java`
- Test: `StockAI/src/test/java/com/xian/stockai/po/TradePlanPoTest.java`

**Interfaces:**
- Produces: `TradePlan`（字段见设计文档 4.1）、`TradePlanRepository#findByUserIdAndSymbol(String,String)->Optional<TradePlan>`、`#findByUserId(String)->List<TradePlan>`；`TradePlanRes` 为返回 VO（`from(TradePlan)` 静态方法）。

- [x] **Step 1: 写失败测试**

```java
package com.xian.stockai.po;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.junit.jupiter.api.Assertions.*;

class TradePlanPoTest {

    @Test
    void mapsToTradePlansCollection() {
        Document doc = TradePlan.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("trade_plans", doc.collection());
    }

    @Test
    void summaryMapsToExpectedCollection() {
        Document doc = TradePlanSummary.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("trade_plan_summary", doc.collection());
    }
}
```

- [x] **Step 2: 运行确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanPoTest`
Expected: 编译失败（类不存在）

- [x] **Step 3: 实现**

`TradePlan` 使用 `@Document(collection = "trade_plans")` + `@CompoundIndex(name = "uk_user_symbol", def = "{'user_id': 1, 'symbol': 1}", unique = true)`，字段与 `@Field` 命名严格按设计文档 4.1；`buy_batches` 为 `List<TradePlanBatch>`（内部静态类：`ratio`、`price`、`amount`）；`add_position_rule` 为内部静态类 `AddPositionRule`（`trigger_price`、`ratio`、`condition_text`）；`sell_strategy` 为内部静态类 `SellStrategy`（`take_profit_action`、`stop_loss_action`、`resistance_note`）。

`TradePlanSummary` 使用 `@Document(collection = "trade_plan_summary")` + 唯一复合索引 `user_id + week_start`，字段：`weekStart`、`weekEnd`、`generatedAt`、`content`、`status`、`fallbackReason`、`createdAt`、`updatedAt`。

`TradePlanRepository`：

```java
public interface TradePlanRepository extends MongoRepository<TradePlan, String> {
    Optional<TradePlan> findByUserIdAndSymbol(String userId, String symbol);

    List<TradePlan> findByUserId(String userId);
}
```

`TradePlanRes`：直接镜像 `TradePlan` 的展示字段（`symbol`、`stockName`、`quantity`、`avgCost`、`lastClose`、`floatPnl`、`floatPnlRate`、`industry`、`atr14`、`atrRatio`、`support20d`、`resistance20d`、`stopLossPrice`、`takeProfitPrice`、`stopBasis`、`targetBasis`、`buyMode`、`buyBatches`、`buyBasis`、`addPositionRule`、`sellStrategy`、`degraded`、`degradedReason`、`monitorLinked`、`generatedAt`），提供 `public static TradePlanRes from(TradePlan plan)`。

- [x] **Step 4: 运行确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanPoTest`
Expected: PASS（2 个测试）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/TradePlan.java StockAI/src/main/java/com/xian/stockai/po/TradePlanSummary.java StockAI/src/main/java/com/xian/stockai/dao/TradePlanRepository.java StockAI/src/main/java/com/xian/stockai/dao/TradePlanSummaryRepository.java StockAI/src/main/java/com/xian/stockai/vo/TradePlanRes.java StockAI/src/test/java/com/xian/stockai/po/TradePlanPoTest.java
git commit -m "feat(trade-plan): 新增交易计划与周报文档及仓储"
```

（`TradePlanSummaryRepository` 同时创建：`Optional<TradePlanSummary> findTopByUserIdOrderByWeekStartDesc(String userId)`、`List<TradePlanSummary> findTop12ByUserIdOrderByWeekStartDesc(String userId)`、`Optional<TradePlanSummary> findByUserIdAndWeekStart(String userId, String weekStart)`、`List<TradePlanSummary> findByUserIdOrderByWeekStartAsc(String userId)`）

---

### Task 3: 计划计算（tasks 2.1 / 2.2 / 2.3）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanCalculator.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanCalculatorTest.java`

**Interfaces:**
- Consumes: `IndicatorCalculator#atr/support/resistance`、`TradePlanProperties`
- Produces: `TradePlanCalculator#calculate(CashContext cash, Position position, String industry, List<StockDailyKline> ascKlines, BigDecimal lastClose) -> TradePlan`；内部静态类 `CashContext(BigDecimal availableCash, String cashBasis)`

- [x] **Step 1: 写失败测试**

```java
class TradePlanCalculatorTest {

    private final TradePlanProperties properties = new TradePlanProperties();
    private final TradePlanCalculator calculator = new TradePlanCalculator(new IndicatorCalculator(), properties);

    private Position position(String symbol, String qty, String avgCost) {
        Position p = new Position();
        p.setUserId("u1");
        p.setSymbol(symbol);
        p.setStockName("测试股");
        p.setTotalQuantity(Integer.valueOf(qty));
        p.setAvgCostPrice(new BigDecimal(avgCost));
        p.setTotalCost(new BigDecimal(avgCost).multiply(new BigDecimal(qty)));
        return p;
    }

    // 构造 20 根稳定K线：high 11.00 / low 9.00 / close 10.00 → ATR=2.00
    private List<StockDailyKline> klines() { ... }  // 见 Task 1 测试的构造方式

    @Test
    void stopLossAndTakeProfitFollowAtrAndRiskReward() {
        TradePlan plan = calculator.calculate(new CashContext(new BigDecimal("10000"), "REMAINING_CASH"),
                position("600519", "100", "10.00"), "白酒", klines(), new BigDecimal("10.00"));
        // 止损 = 10.00 - 2*2.00 = 6.00；risk=4.00；止盈 = 10.00 + 2*4.00 = 18.00
        assertEquals(0, new BigDecimal("6.00").compareTo(plan.getStopLossPrice()));
        assertEquals(0, new BigDecimal("18.00").compareTo(plan.getTakeProfitPrice()));
        assertFalse(plan.getDegraded());
        assertEquals("BATCHED", plan.getBuyMode()); // ATR/现价=20% ≥ 3% → 3 批
        assertEquals(3, plan.getBuyBatches().size());
        assertEquals(0, new BigDecimal("10000").compareTo(
                plan.getBuyBatches().get(0).getAmount()));
    }

    @Test
    void lowVolatilityUsesLumpSum() {
        List<StockDailyKline> klines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            StockDailyKline k = new StockDailyKline();
            k.setTradeDate("2026-03-" + String.format("%02d", i + 1));
            k.setHigh(new BigDecimal("10.06"));
            k.setLow(new BigDecimal("9.94"));
            k.setClose(new BigDecimal("10.00"));
            klines.add(k);
        }
        TradePlan plan = calculator.calculate(new CashContext(null, "UNKNOWN"),
                position("600519", "100", "10.00"), "白酒", klines, new BigDecimal("10.00"));
        assertEquals("LUMP_SUM", plan.getBuyMode());  // ATR/现价≈0.6% < 2%
        assertTrue(plan.getBuyBatches().isEmpty());
        assertNull(plan.getBuyBatches().isEmpty() ? null : plan.getBuyBatches().get(0).getAmount());
        assertEquals("UNKNOWN", plan.getCashBasis());
    }

    @Test
    void degradedWhenKlinesInsufficient() {
        Position p = position("600519", "100", "10.00");
        TradePlan plan = calculator.calculate(new CashContext(null, "UNKNOWN"), p, "白酒",
                new ArrayList<>(), new BigDecimal("10.00"));
        assertTrue(plan.getDegraded());
        assertEquals(0, new BigDecimal("9.50").compareTo(plan.getStopLossPrice()));
        assertEquals(0, new BigDecimal("11.00").compareTo(plan.getTakeProfitPrice()));
        assertNotNull(plan.getDegradedReason());
    }
}
```

- [x] **Step 2: 运行确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanCalculatorTest`
Expected: 编译失败（`TradePlanCalculator` 不存在）

- [x] **Step 3: 实现**

核心逻辑（按设计 3.3~3.6）：

```java
public TradePlan calculate(CashContext cash, Position position, String industry,
                           List<StockDailyKline> ascKlines, BigDecimal lastClose) {
    TradePlan plan = new TradePlan();
    plan.setUserId(position.getUserId());
    plan.setSymbol(position.getSymbol());
    plan.setStockName(position.getStockName());
    plan.setIndustry(industry);
    plan.setQuantity(position.getTotalQuantity());
    plan.setAvgCost(position.getAvgCostPrice());
    plan.setLastClose(lastClose);
    plan.setAsOfDate(lastKlineDate(ascKlines));
    plan.setGeneratedAt(LocalDateTime.now());
    plan.setUpdatedAt(LocalDateTime.now());
    plan.setCashBasis(cash.cashBasis());
    plan.setAvailableCash(cash.availableCash());
    plan.setMonitorLinked(false);

    BigDecimal atr = indicator.atr(ascKlines, properties.getAtrPeriod());
    if (atr == null || ascKlines.size() < properties.getMinKlineCount() || lastClose == null) {
        applyDegraded(plan, "历史K线不足或现价缺失，降级为固定百分比");
        return plan;
    }
    // 止损/止盈
    BigDecimal stop = position.getAvgCostPrice()
            .subtract(properties.getStopAtrMultiplier().multiply(atr))
            .setScale(2, RoundingMode.HALF_UP);
    if (stop.compareTo(BigDecimal.ZERO) <= 0) {
        applyDegraded(plan, "ATR 过大导致止损价非正，降级为固定百分比");
        return plan;
    }
    BigDecimal risk = position.getAvgCostPrice().subtract(stop);
    BigDecimal target = position.getAvgCostPrice()
            .add(properties.getRiskReward().multiply(risk)).setScale(2, RoundingMode.HALF_UP);
    plan.setAtr14(atr.setScale(4, RoundingMode.HALF_UP));
    plan.setAtrRatio(atr.divide(lastClose, 4, RoundingMode.HALF_UP));
    plan.setSupport20d(indicator.support(ascKlines, properties.getSupportResistanceWindow()));
    plan.setResistance20d(indicator.resistance(ascKlines, properties.getSupportResistanceWindow()));
    plan.setStopLossPrice(stop);
    plan.setTakeProfitPrice(target);
    plan.setStopBasis(String.format("成本 %s − %s×ATR %s", avgCost, multiplier, atr4));
    plan.setTargetBasis(String.format("成本 %s + %s×风险 %s", avgCost, riskReward, risk2));
    plan.setBuyMode/buyBatches/buyBasis  // 分档 + 触发价（第二批 -0.5ATR，第三批 -1.0ATR，不低于 support20d）
    plan.setAddPositionRule(...)          // 回落支撑且 lastClose>stop 才补仓，ratio=0.3
    plan.setSellStrategy(...)             // 止盈减仓50% / 跌破止损清仓 / 接近阻力提示
    return plan;
}
```

`applyDegraded`：`stopLossPrice = avgCost×(1-degradedStopLossRate)`、`takeProfitPrice = avgCost×(1+degradedTakeProfitRate)`（2 位小数）、`degraded=true`、`degradedReason=reason`、`buyMode=LUMP_SUM`、`buyBatches=空列表`。

- [x] **Step 4: 运行确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanCalculatorTest`
Expected: PASS（3 个测试）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanCalculator.java StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanCalculatorTest.java
git commit -m "feat(trade-plan): 实现止盈止损、批次、补仓与卖出策略计算"
```

---

### Task 4: 计划生成服务与买入联动（tasks 3.1 后半）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanService.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/service/StockService.java:53-118`（`buy` 末尾）
- Modify: `StockAI/src/main/java/com/xian/stockai/service/StockService.java:33-51`（构造器追加依赖）
- Test: `StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanServiceTest.java`

**Interfaces:**
- Consumes: `TradePlanCalculator`、`TradePlanRepository`、`PositionRepository`、`StockDailyKlineRepository`、`MarketQuotesRepository`、`EtfDailyQuotesRepository`、`StockIndustryConceptRepository`、`UsersRepository`
- Produces: `TradePlanService#refreshPlan(String userId, String symbol)`、`#list(String userId) -> Result<List<TradePlanRes>>`

- [x] **Step 1: 写失败测试**（Mockito）

```java
@ExtendWith(MockitoExtension.class)
class TradePlanServiceTest {

    @Mock PositionRepository positionRepository;
    @Mock StockDailyKlineRepository klineRepository;
    @Mock MarketQuotesRepository marketQuotesRepository;
    @Mock EtfDailyQuotesRepository etfDailyQuotesRepository;
    @Mock StockIndustryConceptRepository industryConceptRepository;
    @Mock UsersRepository usersRepository;
    @Mock TradePlanRepository tradePlanRepository;
    @Mock TradePlanCalculator calculator;
    TradePlanService service;

    @BeforeEach
    void setUp() {
        service = new TradePlanService(positionRepository, klineRepository, marketQuotesRepository,
                etfDailyQuotesRepository, industryConceptRepository, usersRepository,
                tradePlanRepository, calculator);
    }

    @Test
    void refreshOverwritesExistingPlan() {
        Position position = position("600519", "100", "10.00");
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position);
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.empty());
        TradePlan generated = new TradePlan();
        generated.setSymbol("600519");
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(generated);

        service.refreshPlan("u1", "600519");

        verify(tradePlanRepository).save(generated);
    }

    @Test
    void listGeneratesMissingPlansIdempotently() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", "100", "10.00")));
        when(tradePlanRepository.findByUserId("u1")).thenReturn(List.of());
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(new TradePlan());
        when(positionRepository.findByUserIdAndSymbol(anyString(), anyString()))
                .thenReturn(position("600519", "100", "10.00"));

        Result<List<TradePlanRes>> result = service.list("u1");

        assertTrue(result.checkSuccess());
        assertEquals(1, result.getData().size());
    }
}
```

- [x] **Step 2: 运行确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanServiceTest`
Expected: 编译失败（`TradePlanService` 不存在）

- [x] **Step 3: 实现**

`refreshPlan`：查持仓（无则日志返回）→ 取升序K线（`findTop21BySymbolOrderByTradeDateDesc` 后反转，取最近 21 根保证 20 根窗口 + 1 根用于 TR）→ 现价（ETF 走 `etfDailyQuotesRepository`，个股走 `marketQuotesRepository`，无则降级）→ 行业（`industryConceptRepository.findBySymbol`）→ 现金（`usersRepository.findById` 的 cash，`buyMethod` 无法回溯时以 `REMAINING_CASH` 表示有 cash、null 表示 UNKNOWN）→ `calculator.calculate(...)` → 若已存在计划沿用 `monitorLinked` → `save`。

`list`：`positionRepository.findByUserId` + `tradePlanRepository.findByUserId` 组合；缺计划者调用 `refreshPlan` 后重新读取；只返回 `totalQuantity > 0` 的持仓。

`StockService.buy` 末尾（在 `return result.setMessage(...)` 前）：

```java
try {
    tradePlanService.refreshPlan(stockTrade.getUserId(), stockTrade.getSymbol());
} catch (Exception e) {
    log.error("生成交易计划失败: userId={}, symbol={}", stockTrade.getUserId(), stockTrade.getSymbol(), e);
}
```

构造器追加 `TradePlanService tradePlanService` 参数并赋值。

- [x] **Step 4: 运行确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanServiceTest,StockServiceTest`
Expected: PASS

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanService.java StockAI/src/main/java/com/xian/stockai/service/StockService.java StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanServiceTest.java
git commit -m "feat(trade-plan): 买入后自动生成交易计划并支持按需补生成"
```

---

### Task 5: 一键联动止盈止损提醒（tasks 4.1）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/vo/MonitorLinkRes.java`
- Create: `StockAI/src/main/java/com/xian/stockai/vo/TradePlanSymbolReq.java`
- Create: `StockAI/src/main/java/com/xian/stockai/controller/TradePlanController.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanService.java`（新增 `linkMonitor`）
- Test: `StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanLinkMonitorTest.java`
- Test: `StockAI/src/test/java/com/xian/stockai/controller/TradePlanControllerTest.java`

**Interfaces:**
- Produces: `TradePlanService#linkMonitor(String userId, String symbol) -> Result<MonitorLinkRes>`；`TradePlanController` 暴露 `POST /trade-plan/list`、`/trade-plan/link-monitor`

- [x] **Step 1: 写失败测试**

```java
@ExtendWith(MockitoExtension.class)
class TradePlanLinkMonitorTest {

    @Test
    void linkMonitorOverwritesExistingRuleAndKeepsEnabled() {
        // plan: stopLoss 6.00 / takeProfit 18.00
        // 已存在 monitor: PRICE 7.00 / PRICE 20.00, enabled=false
        // 期望：写入 6.00 / 18.00，enabled 仍为 false，返回 before/after
        assertEquals(0, new BigDecimal("6.00").compareTo(monitor.getStopLossValue()));
        assertFalse(monitor.getEnabled());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.getData().getBefore().getTakeProfitValue()));
        assertEquals(0, new BigDecimal("18.00").compareTo(result.getData().getAfter().getTakeProfitValue()));
    }

    @Test
    void linkMonitorCreatesAndEnablesWhenAbsent() {
        // monitor 不存在 → 新建、enabled=true、type=PRICE、monitor_linked=true
    }
}
```

- [x] **Step 2: 运行确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanLinkMonitorTest`
Expected: 编译失败

- [x] **Step 3: 实现**

```java
public Result<MonitorLinkRes> linkMonitor(String userId, String symbol) {
    TradePlan plan = tradePlanRepository.findByUserIdAndSymbol(userId, symbol).orElse(null);
    if (plan == null) {
        return Result.fail("交易计划不存在，请先刷新计划");
    }
    PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(userId, symbol);
    MonitorLinkRes.Before before = MonitorLinkRes.Before.from(monitor);
    if (monitor == null) {
        monitor = new PositionMonitor();
        monitor.setUserId(userId);
        monitor.setSymbol(symbol);
        monitor.setStockName(plan.getStockName());
        monitor.setEnabled(true);
        monitor.setLastTriggerDirection(MonitorService.DIR_NONE);
        monitor.setCreatedAt(LocalDateTime.now());
    }
    monitor.setTakeProfitType(MonitorService.TYPE_PRICE);
    monitor.setTakeProfitValue(plan.getTakeProfitPrice());
    monitor.setStopLossType(MonitorService.TYPE_PRICE);
    monitor.setStopLossValue(plan.getStopLossPrice());
    monitor.setUpdatedAt(LocalDateTime.now());
    positionMonitorRepository.save(monitor);
    plan.setMonitorLinked(true);
    plan.setUpdatedAt(LocalDateTime.now());
    tradePlanRepository.save(plan);
    return Result.success(new MonitorLinkRes(before, MonitorLinkRes.After.from(monitor)));
}
```

`TradePlanController`（`@RequiredArgsConstructor` + `@RestController` + `@RequestMapping("/trade-plan")`）：`/list` 接 `UserRequest`；`/link-monitor` 接 `TradePlanSymbolReq`；其余 4 个周报接口在 Task 7 补齐。

- [x] **Step 4: 运行确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanLinkMonitorTest,TradePlanControllerTest`
Expected: PASS

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/vo/MonitorLinkRes.java StockAI/src/main/java/com/xian/stockai/vo/TradePlanSymbolReq.java StockAI/src/main/java/com/xian/stockai/controller/TradePlanController.java StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanService.java StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanLinkMonitorTest.java StockAI/src/test/java/com/xian/stockai/controller/TradePlanControllerTest.java
git commit -m "feat(trade-plan): 支持一键写入止盈止损提醒并返回变更对比"
```

---

### Task 6: 周报数据组装与 prompt 资源（tasks 5.1 前半）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanSummaryDataAssembler.java`
- Create: `StockAI/src/main/resources/prompts/trade-plan-weekly-report.md`
- Create: `StockAI/src/main/resources/prompts/trade-plan-weekly-template.md`
- Test: `StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanSummaryDataAssemblerTest.java`

**Interfaces:**
- Consumes: `TradePlanRepository`、`PositionRepository`、`StockTradeRepository`、`StockIndustryConceptRepository`、`HotspotResultRepository`、`ScreeningResultRepository`、`UsersRepository`
- Produces: `TradePlanSummaryDataAssembler#assemble(String userId, LocalDate weekStart, LocalDate weekEnd) -> Map<String, Object>`（键与模板第 1 节字段一致）

- [x] **Step 1: 写失败测试**

```java
@Test
void replayComputesRealizedPnlForClearedPosition() {
    // 买入 100 股 10.00（actual 1000），再买入 100 股 12.00（actual 1200），卖出 200 股 15.00
    // 加权成本 11.00 → realized = (15-11)*200 = 800
    Map<String, Object> cleared = assembler.replayCleared(positions, trades);
    assertEquals(0, new BigDecimal("800").compareTo((BigDecimal) cleared.get("600519").get("realizedPnl")));
}

@Test
void replayMarksMissingCostWhenTradeHistoryIncomplete() {
    // 仅一条 SELL 记录（无买入）→ costBasis=null、标记「成本数据缺失」
}

@Test
void industryDistributionSortsByCostRatioAndCountsUnclassified() {
    // 两只 1000 元白酒 + 一只 500 元未分类 → 白酒 80.00%、未分类 1 只
}
```

- [x] **Step 2: 运行确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanSummaryDataAssemblerTest`
Expected: 编译失败

- [x] **Step 3: 实现**

回放算法（`replayCleared`）：

```java
LocalDateTime now = LocalDateTime.now();
for (StockTrade t : tradesSortedByCreatedAt) {
    if ("BUY".equalsIgnoreCase(t.getSide())) {
        qty = qty.add(BigDecimal.valueOf(t.getQuantity()));
        cost = cost.add(t.getActualAmount() == null ? t.getAmount() : t.getActualAmount());
    } else if ("SELL".equalsIgnoreCase(t.getSide())) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0) { insufficient = true; break; }
        BigDecimal perShare = cost.divide(qty, 6, RoundingMode.HALF_UP);
        realized = realized.add(t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity()))
                .subtract(perShare.multiply(BigDecimal.valueOf(t.getQuantity()))));
        cost = cost.subtract(perShare.multiply(BigDecimal.valueOf(t.getQuantity())));
        qty = qty.subtract(BigDecimal.valueOf(t.getQuantity()));
    }
}
```

`assemble` 输出 `summary`、`weekTrades`、`clearedPositions`、`plans`（复用 `TradePlanRes.from`）、`industryDistribution`、`focusIndustries`（`hotspotResultRepository.findTopByOrderByTradeDateDesc()` 的 `industries` 前 5）、`focusStocks`（`screeningResultRepository.findTopByOrderByTradeDateDesc()` 三榜 Top3）、`generatedAt`。

prompt 资源：`trade-plan-weekly-report.md` 内容取自 `docs/openspec/changes/trade-plan/.comet/handoff/ai-summary-prompt.md` 的「角色 / 输入 / 输出模板 / 硬性约束 / 降级与校验」；`trade-plan-weekly-template.md` 取自 `ai-summary-template.md` 第 2 节模板（去掉审核说明）。两份文件头部不得含 YAML frontmatter。

- [x] **Step 4: 运行确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanSummaryDataAssemblerTest`
Expected: PASS（3 个测试）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanSummaryDataAssembler.java StockAI/src/main/resources/prompts/trade-plan-weekly-report.md StockAI/src/main/resources/prompts/trade-plan-weekly-template.md StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanSummaryDataAssemblerTest.java
git commit -m "feat(trade-plan): 组装周报数据并新增周报 prompt 与模板资源"
```

---

### Task 7: 周报生成、定时任务与接口（tasks 5.1 后半）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanSummaryService.java`
- Create: `StockAI/src/main/java/com/xian/stockai/task/TradePlanSummaryTask.java`
- Create: `StockAI/src/main/java/com/xian/stockai/vo/TradePlanSummaryRes.java`、`TradePlanSummaryBriefRes.java`、`TradePlanSummaryDetailReq.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/controller/TradePlanController.java`（补 4 个周报接口）
- Test: `StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanSummaryServiceTest.java`
- Test: `StockAI/src/test/java/com/xian/stockai/task/TradePlanSummaryTaskTest.java`

**Interfaces:**
- Produces: `TradePlanSummaryService#generateWeekly(String userId) -> TradePlanSummary`、`#latest(String userId)->Result<TradePlanSummaryRes>`、`#history(String userId)->Result<List<TradePlanSummaryBriefRes>>`、`#detail(String userId, String weekStart)->Result<TradePlanSummaryRes>`

- [x] **Step 1: 写失败测试**

```java
@Test
void fallsBackToQuantTextWhenChatClientThrows() {
    when(chatClient.prompt()).thenThrow(new RuntimeException("timeout"));
    TradePlanSummary summary = service.generateWeekly("u1");
    assertEquals("fallback", summary.getStatus());
    assertTrue(summary.getContent().contains("## 八、免责声明"));
    assertNotNull(summary.getFallbackReason());
}

@Test
void fallsBackWhenStructureIncomplete() {
    // ChatClient 返回缺少「## 八、免责声明」的文本 → status=fallback，content 为量化文本
}

@Test
void upsertsSameWeekSummary() {
    // 同一 weekStart 二次生成 → save 只调用一次且 weekStart 相同
}

@Test
void taskContinuesWhenSingleUserFails() {
    // 两个用户，第一个抛异常 → 第二个仍生成；task 返回失败数 1
}
```

- [x] **Step 2: 运行确认失败**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanSummaryServiceTest,TradePlanSummaryTaskTest`
Expected: 编译失败

- [x] **Step 3: 实现**

```java
@Service
public class TradePlanSummaryService {
    @Value("classpath:/prompts/trade-plan-weekly-report.md")
    private Resource reportPrompt;

    @Value("classpath:/prompts/trade-plan-weekly-template.md")
    private Resource reportTemplate;

    public TradePlanSummary generateWeekly(String userId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        Map<String, Object> data = assembler.assemble(userId, weekStart, LocalDate.now());
        String content;
        String status = "success";
        String reason = null;
        try {
            String user = reportTemplate.getContentAsString(StandardCharsets.UTF_8)
                    + "\n数据：\n" + JSON.toJSONString(data);
            content = chatClient.prompt()
                    .system(reportPrompt.getContentAsString(StandardCharsets.UTF_8))
                    .user(user).call().content();
            if (!isStructurallyValid(content, data)) {
                throw new IllegalStateException("周报结构校验失败");
            }
        } catch (Exception e) {
            content = buildFallback(data);
            status = "fallback";
            reason = e.getMessage();
        }
        TradePlanSummary summary = repository.findByUserIdAndWeekStart(userId, weekStart.toString())
                .orElseGet(TradePlanSummary::new);
        summary.setUserId(userId); ...; repository.save(summary);
        trimToRetention(userId);   // 保留最近 12 期
        return summary;
    }
}
```

`isStructurallyValid`：内容必须包含 `## 一、` 至 `## 八、` 八个标题、`免责声明`、且 `plans` 中每只 `stockName` 都出现。

`buildFallback`：按模板顺序输出固定句式与数值（组合概览、买卖回顾、逐仓要点、行业分布、风险、关注方向、行动清单「本周无新增操作建议，维持既有计划」、免责声明原文）。

`TradePlanSummaryTask`：

```java
@Component
@Slf4j
public class TradePlanSummaryTask {
    @Scheduled(cron = "${trade-plan.summary-cron:0 0 8 ? * SAT}")
    public int generateAll() {
        List<String> userIds = positionRepository.findDistinctActiveUserIds();
        int failed = 0;
        for (String userId : userIds) {
            try {
                summaryService.generateWeekly(userId);
            } catch (Exception e) {
                failed++;
                log.error("生成周报失败: userId={}", userId, e);
            }
        }
        log.info("周报生成完成: 用户 {} 个，失败 {} 个", userIds.size(), failed);
        return failed;
    }
}
```
（`PositionRepository` 追加 `@Query(value = "{ 'total_quantity': { $gt: 0 } }", fields = "{ 'user_id': 1 }") List<String> findDistinctActiveUserIds()` 或等价派生查询）

`TradePlanController` 补：`/summary/latest`、`/summary/list`、`/summary/detail`、`/summary/generate`（均接 `UserRequest` 或 `TradePlanSummaryDetailReq`）。

`application.yml` 追加：

```yaml
# 交易计划：ATR 与周报参数
trade-plan:
  atr-period: 14
  stop-atr-multiplier: 2.0
  risk-reward: 2.0
  support-resistance-window: 20
  high-volatility-threshold: 0.03
  mid-volatility-threshold: 0.02
  min-kline-count: 15
  degraded-stop-loss-rate: 0.05
  degraded-take-profit-rate: 0.10
  summary-retention-weeks: 12
  summary-cron: "0 0 8 ? * SAT"
```

- [x] **Step 4: 运行确认通过**

Run: `cd StockAI; ./mvnw test -Dtest=TradePlanSummaryServiceTest,TradePlanSummaryTaskTest`
Expected: PASS

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/tradeplan/TradePlanSummaryService.java StockAI/src/main/java/com/xian/stockai/task/TradePlanSummaryTask.java StockAI/src/main/java/com/xian/stockai/vo/TradePlanSummaryRes.java StockAI/src/main/java/com/xian/stockai/vo/TradePlanSummaryBriefRes.java StockAI/src/main/java/com/xian/stockai/vo/TradePlanSummaryDetailReq.java StockAI/src/main/java/com/xian/stockai/controller/TradePlanController.java StockAI/src/main/resources/application.yml StockAI/src/test/java/com/xian/stockai/service/tradeplan/TradePlanSummaryServiceTest.java StockAI/src/test/java/com/xian/stockai/task/TradePlanSummaryTaskTest.java
git commit -m "feat(trade-plan): 每周六生成交易计划周报并支持历史查询"
```

---

### Task 8: 前端交易计划页（tasks 5.2）

**Files:**
- Create: `StockAI-Console/src/components/TradePlan.vue`
- Create: `StockAI-Console/src/components/TradePlan/WeeklySummaryBlock.vue`
- Create: `StockAI-Console/src/components/TradePlan/TradePlanCard.vue`
- Modify: `StockAI-Console/src/router/index.js`（新增路由）
- Modify: `StockAI-Console/src/App.vue:138-141`（菜单新增「交易计划」）
- Test: 无自动化测试，按 Step 4 人工验证清单执行

**Interfaces:**
- Consumes: `POST /trade-plan/list`、`/trade-plan/link-monitor`、`/trade-plan/summary/latest`、`/summary/list`、`/summary/detail`、`/summary/generate`
- Produces: 两个子组件 props/emits：`WeeklySummaryBlock`（内部自取数据）、`TradePlanCard`（`props: plan`，`emit('linked')`）

- [x] **Step 1: 组件骨架与路由**

`TradePlan.vue`：`<script setup>` 内 `userId` 取自 localStorage（与 `Dashboard.vue` 现有取值方式一致）；`onMounted` 调 `/trade-plan/list`；布局 = `<WeeklySummaryBlock />` + `<el-row :gutter="16">` 卡片栅格。

`WeeklySummaryBlock.vue` 关键实现（Markdown 轻量渲染，禁止新增依赖）：

```js
function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderMarkdown(md) {
  return escapeHtml(md)
    .replace(/^### (.*)$/gm, '<h4>$1</h4>')
    .replace(/^## (.*)$/gm, '<h3>$1</h3>')
    .replace(/^# (.*)$/gm, '<h2>$1</h2>')
    .replace(/^- (.*)$/gm, '<li>$1</li>')
    .replace(/(<li>[\s\S]*?<\/li>)/g, '<ul>$1</ul>')
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/^([^<].*)$/gm, '<p>$1</p>')
}
```

模板：标题行 `本周周报` + `生成时间：{{ generatedAt }}` + 按钮「展开/收起」「历史」；折叠时容器 `max-height: 240px; overflow: hidden`；展开时 `max-height: none`，下方内容自然下移；历史 `el-dialog width="900px"`，左栏 `el-menu` 列表（`v-for` 最近 12 期），右栏 `v-html="renderMarkdown(selected.content)"`。

- [x] **Step 2: 卡片与联动按钮**

`TradePlanCard.vue`：`el-card` 内分段展示 `plan` 字段；`degraded` 时头部 `el-tag type="warning">计划降级</el-tag>` 并展示 `degradedReason`；按钮：

```js
async function linkMonitor() {
  const res = await axios.post(`${BASE_URL}/trade-plan/link-monitor`,
    { userId: props.plan.userId, symbol: props.plan.symbol })
  if (res.data.state !== 0) { ElMessage.error(res.data.message); return }
  const { before, after } = res.data.data
  ElMessage.success(`已写入：止盈 ${before.takeProfitValue ?? '—'} → ${after.takeProfitValue}，止损 ${before.stopLossValue ?? '—'} → ${after.stopLossValue}`)
  emit('linked')
}
```

- [x] **Step 3: 路由与菜单**

`router/index.js`：

```js
import TradePlan from '../components/TradePlan.vue'
...
{ path: '/trade-plan', name: '交易计划', component: TradePlan }
```

`App.vue` 在「选股」菜单项后追加：

```vue
<el-menu-item index="/trade-plan">
  <el-icon><Notebook /></el-icon>
  <template #title>交易计划</template>
</el-menu-item>
```

- [x] **Step 4: 构建与人工验证**

Run: `cd StockAI-Console; npm run build`
Expected: 构建成功，无 ESLint/编译错误

人工验证清单（记入验证报告）：
1. 打开 `/trade-plan`，顶部周报默认折叠且显示生成时间
2. 点「展开」→ 全文显示，下方卡片整体下移；点「收起」恢复
3. 点「历史」→ 弹窗左侧列出最近 12 期，右侧切换显示对应全文
4. 点卡片「写入止盈止损提醒」→ 提示 before/after，`/monitor/list` 可查到新值
5. 降级标的显示「计划降级」标签与原因
6. 无持仓/无周报时显示空态

- [x] **Step 5: 提交**

```bash
git add StockAI-Console/src/components/TradePlan.vue StockAI-Console/src/components/TradePlan/WeeklySummaryBlock.vue StockAI-Console/src/components/TradePlan/TradePlanCard.vue StockAI-Console/src/router/index.js StockAI-Console/src/App.vue
git commit -m "feat(trade-plan): 新增交易计划页与周报折叠展示"
```

---

## 验证与收尾

- [x] 后端全量测试：`cd StockAI; ./mvnw test`
- [x] 后端构建：`cd StockAI; ./mvnw clean package -DskipTests`
- [x] 前端构建：`cd StockAI-Console; npm run build`
- [x] 勾选 `docs/openspec/changes/trade-plan/tasks.md` 全部任务
- [x] 代码审查（按 `review_mode` 执行）
- [x] `comet guard trade-plan build --apply`

