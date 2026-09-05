---
change: stock-screener
design-doc: docs/superpowers/specs/2026-09-01-stock-screener-design.md
base-ref: 0b0e80302676ab7af625945bbe27012bf6c846e6
---

# stock-screener 选股引擎实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在优质池（沪深300+中证500）内实现可复现的多因子打分、三风格 Top 20 候选榜、长期价值评级、热点方向识别（含炒作风险标注）、AI 综合报告（失败降级）与每日定时任务，并在前端新增选股展示页。

**Architecture:** 后端复用现有分层（po/dao/service/task/controller + `Result<T>` 包装 + POST 接口）。`ScreeningService` 负责因子计算、风格榜单与全量快照入库（`screening_scores`）；`HotspotService` 从快照读取质量分，聚合行业/概念量价生成 `hotspot_results`；`ScreeningReportService` 复用 `CommonConfiguration` 的 `ChatClient` 生成 AI 报告并降级；`ScreeningTask` 做编排（cron + `/screening/run` 复用同一入口）。前端新增单页 `StockScreener.vue`，同时消费 `/screening/latest` 与 `/hotspot/latest`。

**Tech Stack:** Java 21 + Spring Boot 3.5 + MongoDB（Spring Data MongoDB）+ Spring AI ChatClient；Vue 3 + Element Plus + axios。

**Spec:** `docs/superpowers/specs/2026-09-01-stock-screener-design.md`（本计划所有算法参数均来自该设计文档；执行者须同时阅读 spec）

## Global Constraints

- 只覆盖 `docs/openspec/changes/stock-screener/tasks.md` 列出的 9 个子任务，不扩展范围（不做全 A 股覆盖、盘中实时选股、买卖时点推荐、权重自动优化）。
- 后端代码位于 `StockAI/`，包 `com.xian.stockai`，遵循现有 po/dao/service/task/controller/vo 结构；接口统一 `POST` + `Result<T>`（`Result.successIfNotNull(data, failMsg)` 用于"暂无数据"）。
- 前端代码位于 `StockAI-Console/`，页面组件在 `src/components/`，路由 `src/router/index.js`，菜单硬编码在 `src/App.vue`，axios 直连 `config.baseUrl`，响应判断 `state === 0`。
- 数值精度：MongoDB 中数值由 etf-service（Python float→double）写入，Java PO 一律用 `BigDecimal` 映射；比率类字段 scale 6（`RoundingMode.HALF_UP`），综合分 scale 2，热度分/因子分为 0~100 整数。
- 数据源 schema（已核实 etf-service `app/normalize/schema.py`）：`stock_fundamentals`（symbol/report_date/pe/pb/roe/revenue_yoy/profit_yoy/gross_margin）、`stock_industry_concept`（symbol/industry/concepts[]）、`stock_daily_kline`（symbol/trade_date/open/high/low/close/volume/amount）、`index_constituents`（index_code/index_name/symbol）。四个集合均**无股票名称字段**，名称从现有 `stock_basic_info` 集合（`StockBasicInfoRepository.findBySymbol`）读取，取不到时降级用 symbol 本身。
- 因子百分位定义（全计划统一）：`percentileDesc(values, v) = round(count(values < v) / (n - 1) * 100)`（正向，值大分高）；`percentileAsc(values, v) = round(count(values > v) / (n - 1) * 100)`（反向，值小分高）；n ≤ 1 时返回 50；重复值得相同分。
- 测试命令：后端 `cd StockAI && ./mvnw test`（Windows PowerShell 用 `.\mvnw.cmd test "-Dtest=XxxTest"`）；前端 `cd StockAI-Console && npm run build`。
- 单元测试范式：JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`），mock Repository 与 ChatClient 链式调用（参照 `MarketRegimeServiceTest`）；Controller 用 `MockMvcBuilders.standaloneSetup`。
- 每个任务完成即 `git commit`，提交信息遵循 `feat:`/`test:` 前缀（AGENTS.md 规范）。
- 涉及"20 日收益率"的实现口径：需要 21 个收盘点（当日 + 前 20 个交易日），`momentumReturn = latestClose / close20BarsAgo - 1`；K 线不足 21 条 → 动量因子 null（对设计文档"K 线不足 20 个交易日 → null"的严格实现）。

## 任务映射（tasks.md 覆盖表）

| tasks.md 条目 | 计划任务 |
|---|---|
| 1.1 因子计算（可复现） | Task 1（数据层）+ Task 2（因子引擎） |
| 1.2 综合评分合成 + 候选榜排序/理由 | Task 3 |
| 2.1 长期价值评级 | Task 4 |
| 3.1 热点聚合 + 热度排序 | Task 5 |
| 3.2 炒作风险标注 | Task 6 |
| 4.1 AI 总结报告 | Task 7 |
| 4.2 定时任务 | Task 8 |
| 5.1 选股结果展示页 | Task 9 |
| 5.2 热点方向展示 | Task 10 |

---

### Task 1: 选股只读数据层（PO + Repository）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/StockFundamentals.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/StockIndustryConcept.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/StockDailyKline.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/IndexConstituents.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/StockFundamentalsRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/StockIndustryConceptRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/StockDailyKlineRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/IndexConstituentsRepository.java`
- Test: `StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java`
- Test: `StockAI/src/test/java/com/xian/stockai/dao/ScreenerRepositoriesTest.java`

**Interfaces:**
- Consumes: 无（首个任务）。
- Produces: `StockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc(String symbol) -> StockFundamentals`；`StockIndustryConceptRepository.findBySymbol(String symbol) -> StockIndustryConcept`；`StockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc(String symbol) -> List<StockDailyKline>` 与 `existsByTradeDate(String tradeDate) -> boolean`；`IndexConstituentsRepository.findAll()`（继承自 MongoRepository）。PO 属性名（camelCase）与 `@Field`（snake_case）见下方代码，后续任务依赖这些精确字段。

**说明:** `StockDailyKline` 只映射本特性用到的字段（symbol/trade_date/close/amount），MongoDB 无 schema，其余字段不映射不影响读取。参照 `IndexDailyQuotes`/`MarketRegimePoTest` 范式。

- [x] **Step 1: 写失败的 PO/Repository 测试**

创建 `StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java`：

```java
package com.xian.stockai.po;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenerPoTest {

    private static String fieldName(Class<?> clazz, String property) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(property).getAnnotation(Field.class);
        return field == null ? null : field.name();
    }

    @Test
    void stockFundamentalsMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = StockFundamentals.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("stock_fundamentals", doc.collection());
        assertEquals("symbol", fieldName(StockFundamentals.class, "symbol"));
        assertEquals("report_date", fieldName(StockFundamentals.class, "reportDate"));
        assertEquals("pe", fieldName(StockFundamentals.class, "pe"));
        assertEquals("pb", fieldName(StockFundamentals.class, "pb"));
        assertEquals("roe", fieldName(StockFundamentals.class, "roe"));
        assertEquals("revenue_yoy", fieldName(StockFundamentals.class, "revenueYoy"));
        assertEquals("profit_yoy", fieldName(StockFundamentals.class, "profitYoy"));
        assertEquals("gross_margin", fieldName(StockFundamentals.class, "grossMargin"));
    }

    @Test
    void stockFundamentalsExposesGettersAndSetters() {
        StockFundamentals po = new StockFundamentals();
        po.setSymbol("600519");
        po.setReportDate("2026-06-30");
        po.setPe(new BigDecimal("30.5"));
        po.setPb(new BigDecimal("8.2"));
        po.setRoe(new BigDecimal("25.3"));
        po.setRevenueYoy(new BigDecimal("15.1"));
        po.setProfitYoy(new BigDecimal("20.2"));
        po.setGrossMargin(new BigDecimal("91.0"));
        assertEquals("600519", po.getSymbol());
        assertEquals("2026-06-30", po.getReportDate());
        assertEquals(new BigDecimal("30.5"), po.getPe());
        assertEquals(new BigDecimal("8.2"), po.getPb());
        assertEquals(new BigDecimal("25.3"), po.getRoe());
        assertEquals(new BigDecimal("15.1"), po.getRevenueYoy());
        assertEquals(new BigDecimal("20.2"), po.getProfitYoy());
        assertEquals(new BigDecimal("91.0"), po.getGrossMargin());
    }

    @Test
    void stockIndustryConceptMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = StockIndustryConcept.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("stock_industry_concept", doc.collection());
        assertEquals("symbol", fieldName(StockIndustryConcept.class, "symbol"));
        assertEquals("industry", fieldName(StockIndustryConcept.class, "industry"));
        assertEquals("concepts", fieldName(StockIndustryConcept.class, "concepts"));
    }

    @Test
    void stockIndustryConceptExposesGettersAndSetters() {
        StockIndustryConcept po = new StockIndustryConcept();
        po.setSymbol("600519");
        po.setIndustry("白酒");
        po.setConcepts(List.of("消费", "高端制造"));
        assertEquals("600519", po.getSymbol());
        assertEquals("白酒", po.getIndustry());
        assertEquals(2, po.getConcepts().size());
    }

    @Test
    void stockDailyKlineMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = StockDailyKline.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("stock_daily_kline", doc.collection());
        assertEquals("symbol", fieldName(StockDailyKline.class, "symbol"));
        assertEquals("trade_date", fieldName(StockDailyKline.class, "tradeDate"));
        assertEquals("close", fieldName(StockDailyKline.class, "close"));
        assertEquals("amount", fieldName(StockDailyKline.class, "amount"));
    }

    @Test
    void indexConstituentsMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = IndexConstituents.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("index_constituents", doc.collection());
        assertEquals("index_code", fieldName(IndexConstituents.class, "indexCode"));
        assertEquals("index_name", fieldName(IndexConstituents.class, "indexName"));
        assertEquals("symbol", fieldName(IndexConstituents.class, "symbol"));
    }
}
```

创建 `StockAI/src/test/java/com/xian/stockai/dao/ScreenerRepositoriesTest.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFundamentals;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.po.IndexConstituents;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenerRepositoriesTest {

    @Test
    void allRepositoriesExtendMongoRepository() {
        assertTrue(MongoRepository.class.isAssignableFrom(StockFundamentalsRepository.class));
        assertTrue(MongoRepository.class.isAssignableFrom(StockIndustryConceptRepository.class));
        assertTrue(MongoRepository.class.isAssignableFrom(StockDailyKlineRepository.class));
        assertTrue(MongoRepository.class.isAssignableFrom(IndexConstituentsRepository.class));
    }

    @Test
    void stockFundamentalsRepositoryDeclaresFindTopBySymbol() throws NoSuchMethodException {
        Method method = StockFundamentalsRepository.class
                .getMethod("findTopBySymbolOrderByReportDateDesc", String.class);
        assertEquals(StockFundamentals.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test
    void stockIndustryConceptRepositoryDeclaresFindBySymbol() throws NoSuchMethodException {
        Method method = StockIndustryConceptRepository.class.getMethod("findBySymbol", String.class);
        assertEquals(StockIndustryConcept.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void stockDailyKlineRepositoryDeclaresFindTop21AndExists() throws NoSuchMethodException {
        Method findTop21 = StockDailyKlineRepository.class
                .getMethod("findTop21BySymbolOrderByTradeDateDesc", String.class);
        assertEquals(List.class, findTop21.getReturnType());
        assertEquals(1, findTop21.getParameterCount());

        Method exists = StockDailyKlineRepository.class.getMethod("existsByTradeDate", String.class);
        assertEquals(boolean.class, exists.getReturnType());
        assertEquals(1, exists.getParameterCount());
        assertEquals(String.class, exists.getParameterTypes()[0]);
    }
}
```

- [x] **Step 2: 运行测试确认失败（编译错误）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreenerPoTest,ScreenerRepositoriesTest`
Expected: COMPILATION ERROR（`StockFundamentals` 等类不存在）

- [x] **Step 3: 创建 4 个 PO**

`StockAI/src/main/java/com/xian/stockai/po/StockFundamentals.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 个股财务指标（只读，etf-service 写入）
 */
@Document(collection = "stock_fundamentals")
public class StockFundamentals {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "report_date")
    private String reportDate;

    @Field(name = "pe")
    private BigDecimal pe;

    @Field(name = "pb")
    private BigDecimal pb;

    @Field(name = "roe")
    private BigDecimal roe;

    @Field(name = "revenue_yoy")
    private BigDecimal revenueYoy;

    @Field(name = "profit_yoy")
    private BigDecimal profitYoy;

    @Field(name = "gross_margin")
    private BigDecimal grossMargin;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getRoe() { return roe; }
    public void setRoe(BigDecimal roe) { this.roe = roe; }
    public BigDecimal getRevenueYoy() { return revenueYoy; }
    public void setRevenueYoy(BigDecimal revenueYoy) { this.revenueYoy = revenueYoy; }
    public BigDecimal getProfitYoy() { return profitYoy; }
    public void setProfitYoy(BigDecimal profitYoy) { this.profitYoy = profitYoy; }
    public BigDecimal getGrossMargin() { return grossMargin; }
    public void setGrossMargin(BigDecimal grossMargin) { this.grossMargin = grossMargin; }
}
```

`StockAI/src/main/java/com/xian/stockai/po/StockIndustryConcept.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * @author xian
 * @description 个股行业与概念分类（只读，etf-service 写入）
 */
@Document(collection = "stock_industry_concept")
public class StockIndustryConcept {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "industry")
    private String industry;

    @Field(name = "concepts")
    private List<String> concepts;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public List<String> getConcepts() { return concepts; }
    public void setConcepts(List<String> concepts) { this.concepts = concepts; }
}
```

`StockAI/src/main/java/com/xian/stockai/po/StockDailyKline.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 个股日K线（只读，etf-service 写入；仅映射选股用到的字段）
 */
@Document(collection = "stock_daily_kline")
public class StockDailyKline {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "amount")
    private BigDecimal amount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
```

`StockAI/src/main/java/com/xian/stockai/po/IndexConstituents.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @author xian
 * @description 指数成分股（优质池，只读，etf-service 写入）
 */
@Document(collection = "index_constituents")
public class IndexConstituents {
    @Id
    private String id;

    @Field(name = "index_code")
    private String indexCode;

    @Field(name = "index_name")
    private String indexName;

    @Field(name = "symbol")
    private String symbol;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
```

- [x] **Step 4: 创建 4 个 Repository**

`StockAI/src/main/java/com/xian/stockai/dao/StockFundamentalsRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.StockFundamentals;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockFundamentalsRepository extends MongoRepository<StockFundamentals, String> {
    StockFundamentals findTopBySymbolOrderByReportDateDesc(String symbol);
}
```

`StockAI/src/main/java/com/xian/stockai/dao/StockIndustryConceptRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.StockIndustryConcept;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockIndustryConceptRepository extends MongoRepository<StockIndustryConcept, String> {
    StockIndustryConcept findBySymbol(String symbol);
}
```

`StockAI/src/main/java/com/xian/stockai/dao/StockDailyKlineRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.StockDailyKline;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockDailyKlineRepository extends MongoRepository<StockDailyKline, String> {
    List<StockDailyKline> findTop21BySymbolOrderByTradeDateDesc(String symbol);

    boolean existsByTradeDate(String tradeDate);
}
```

`StockAI/src/main/java/com/xian/stockai/dao/IndexConstituentsRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.IndexConstituents;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IndexConstituentsRepository extends MongoRepository<IndexConstituents, String> {
}
```

- [x] **Step 5: 运行测试确认通过**

Run: `cd StockAI && ./mvnw test -Dtest=ScreenerPoTest,ScreenerRepositoriesTest`
Expected: PASS（全部用例绿色）

- [x] **Step 6: 运行全量测试确认无回归**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 7: Commit**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/StockFundamentals.java \
        StockAI/src/main/java/com/xian/stockai/po/StockIndustryConcept.java \
        StockAI/src/main/java/com/xian/stockai/po/StockDailyKline.java \
        StockAI/src/main/java/com/xian/stockai/po/IndexConstituents.java \
        StockAI/src/main/java/com/xian/stockai/dao/StockFundamentalsRepository.java \
        StockAI/src/main/java/com/xian/stockai/dao/StockIndustryConceptRepository.java \
        StockAI/src/main/java/com/xian/stockai/dao/StockDailyKlineRepository.java \
        StockAI/src/main/java/com/xian/stockai/dao/IndexConstituentsRepository.java \
        StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java \
        StockAI/src/test/java/com/xian/stockai/dao/ScreenerRepositoriesTest.java
git commit -m "feat: 新增选股引擎只读数据层 PO 与 Repository"
```

---

### Task 2: 四因子计算引擎（1.1 因子计算）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/StockFactor.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 4 个 Repository 及 PO（`findTopBySymbolOrderByReportDateDesc`、`findTop21BySymbolOrderByTradeDateDesc`、`findAll`、`StockBasicInfoRepository.findBySymbol`/`findAll`）。
- Produces:
  - `ScreeningService.calculateFactors(String tradeDate) -> List<StockFactor>`（读库 + 因子计算，不落库）
  - `ScreeningService.percentileDesc(List<BigDecimal> values, BigDecimal target) -> int`（public static，Task 5 复用）
  - `ScreeningService.percentileAsc(List<BigDecimal> values, BigDecimal target) -> int`（public static）
  - `ScreeningService.STYLE_VALUE = "value"` / `STYLE_GROWTH = "growth"` / `STYLE_BALANCED = "balanced"` 常量
  - `StockFactor` 全部字段（见 Step 3，Task 3/4/5/7 依赖）

**因子规则（来自设计文档，全部在本任务实现）：**
- 估值 valuation = PE 反向百分位 × 50% + PB 反向百分位 × 50%；**PE/PB ≤ 0（含数据源缺失填 0）→ 该子分 0**。
- 成长 growth = revenue_yoy 正向百分位 × 50% + profit_yoy 正向百分位 × 50%。
- 质量 quality = roe 正向百分位 × 60% + gross_margin 正向百分位 × 40%。
- 动量 momentum = 20 日收益率正向百分位 × 100%。
- 财务取最近 report_date（限 1 年内，基准 tradeDate）；无记录或超期 → valuation/growth/quality 全 null、dataDate null。
- K 线不足 21 条 → momentum null。
- 缺失因子 ≥ 2 → dataIncomplete = true。
- 名称：`stock_basic_info` 有则用，无则降级为 symbol。

- [x] **Step 1: 写失败的因子计算测试（Task 2）**

创建 `StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.StockBasicInfoRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockFundamentalsRepository;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.StockBasicInfo;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockFundamentals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    @Mock
    private IndexConstituentsRepository indexConstituentsRepository;
    @Mock
    private StockFundamentalsRepository stockFundamentalsRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private StockBasicInfoRepository stockBasicInfoRepository;

    private ScreeningService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningService(indexConstituentsRepository, stockFundamentalsRepository,
                stockDailyKlineRepository, stockBasicInfoRepository);
    }

    private IndexConstituents constituent(String symbol) {
        IndexConstituents c = new IndexConstituents();
        c.setSymbol(symbol);
        return c;
    }

    private StockBasicInfo basic(String symbol, String name) {
        StockBasicInfo b = new StockBasicInfo();
        b.setSymbol(symbol);
        b.setName(name);
        return b;
    }

    private StockFundamentals fund(String reportDate, String pe, String pb, String roe,
                                   String revenueYoy, String profitYoy, String grossMargin) {
        StockFundamentals f = new StockFundamentals();
        f.setReportDate(reportDate);
        f.setPe(new BigDecimal(pe));
        f.setPb(new BigDecimal(pb));
        f.setRoe(new BigDecimal(roe));
        f.setRevenueYoy(new BigDecimal(revenueYoy));
        f.setProfitYoy(new BigDecimal(profitYoy));
        f.setGrossMargin(new BigDecimal(grossMargin));
        return f;
    }

    /** 生成 count 条降序 K 线（index 0 = 最新 close=latestClose，index count-1 = 最旧 close=oldestClose，等差）。 */
    private List<StockDailyKline> descendingKlines(String symbol, int count,
                                                   String oldestClose, String latestClose, String amount) {
        BigDecimal oldest = new BigDecimal(oldestClose);
        BigDecimal latest = new BigDecimal(latestClose);
        BigDecimal step = latest.subtract(oldest)
                .divide(BigDecimal.valueOf(count - 1), 6, RoundingMode.HALF_UP);
        List<StockDailyKline> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StockDailyKline k = new StockDailyKline();
            k.setSymbol(symbol);
            k.setTradeDate(LocalDate.of(2026, 9, 1).minusDays(i).toString());
            k.setClose(latest.subtract(step.multiply(BigDecimal.valueOf(i))));
            k.setAmount(new BigDecimal(amount));
            list.add(k);
        }
        return list;
    }

    private void mockStandardPool() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of(
                constituent("A"), constituent("B"), constituent("C"),
                constituent("D"), constituent("E")));
        when(stockBasicInfoRepository.findAll()).thenReturn(List.of(
                basic("A", "甲公司"), basic("B", "乙公司"), basic("C", "丙公司"),
                basic("D", "丁公司"), basic("E", "戊公司")));
        // A：低 PE 高 ROE；B：高 PE 低 ROE；C：PE 为 -5（≤0）
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("A"))
                .thenReturn(fund("2026-06-30", "8", "1", "25", "30", "40", "50"));
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("B"))
                .thenReturn(fund("2026-06-30", "20", "3", "10", "10", "5", "20"));
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("C"))
                .thenReturn(fund("2026-06-30", "-5", "2", "15", "20", "10", "30"));
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("D"))
                .thenReturn(fund("2026-06-30", "15", "2", "18", "15", "12", "35"));
        // E：财务超 1 年
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("E"))
                .thenReturn(fund("2024-01-01", "10", "2", "20", "20", "15", "40"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("A"))
                .thenReturn(descendingKlines("A", 21, "10", "12", "100000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("B"))
                .thenReturn(descendingKlines("B", 21, "10", "10", "100000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("C"))
                .thenReturn(descendingKlines("C", 21, "10", "11", "100000"));
        // D：K 线不足 21 条
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("D"))
                .thenReturn(descendingKlines("D", 10, "10", "11", "100000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("E"))
                .thenReturn(descendingKlines("E", 21, "10", "11", "100000"));
    }

    private Map<String, StockFactor> factorsBySymbol(String tradeDate) {
        return service.calculateFactors(tradeDate).stream()
                .collect(Collectors.toMap(StockFactor::getSymbol, Function.identity()));
    }

    @Test
    void highRoeEarnsHigherQualityScore() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        // ROE 有效值 [25,10,15,18]（A/B/C/D）：正向百分位 A=100、B=0、C=33、D=67
        assertEquals(100, map.get("A").getQuality());
        assertEquals(0, map.get("B").getQuality());
        assertEquals(33, map.get("C").getQuality());
    }

    @Test
    void lowPeEarnsHigherValuationScoreAndNonPositivePeScoresZero() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        // PE>0 参与反向百分位 [8,20,15]：A(8)=100、B(20)=0；C 的 PE=-5 ≤0 → PE 子分 0
        assertEquals(100, map.get("A").getValuation());
        assertEquals(0, map.get("B").getValuation());
        // C: PE 子分 0，PB=2 在 [1,3,2,2] 反向百分位=33 → valuation = 0*0.5 + 33*0.5 = 16.5 → 17
        assertEquals(17, map.get("C").getValuation());
        assertEquals(0, map.get("C").getPePct());
    }

    @Test
    void momentumIsNullWhenKlinesInsufficient() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        assertNull(map.get("D").getMomentum());
        assertNull(map.get("D").getMomentumReturn());
        // D 仅缺动量（缺 1 个），不算 dataIncomplete
        assertFalse(Boolean.TRUE.equals(map.get("D").getDataIncomplete()));
    }

    @Test
    void momentumReturnComputedFrom21Bars() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        // A: 最新 12 / 20 日前 10 - 1 = 0.2
        assertEquals(0, new BigDecimal("0.2").compareTo(map.get("A").getMomentumReturn()));
        // 动量百分位：A=0.2 > C/E=0.1 > B=0 → A=100
        assertEquals(100, map.get("A").getMomentum());
    }

    @Test
    void staleFundamentalsYieldNullFactorsAndIncomplete() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        StockFactor e = map.get("E");
        assertNull(e.getValuation());
        assertNull(e.getGrowth());
        assertNull(e.getQuality());
        assertNull(e.getDataDate());
        assertTrue(Boolean.TRUE.equals(e.getDataIncomplete()));
    }

    @Test
    void freshFundamentalsRecordDataDateAndRawValues() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        StockFactor a = map.get("A");
        assertEquals("2026-06-30", a.getDataDate());
        assertEquals(new BigDecimal("8"), a.getPe());
        assertEquals(new BigDecimal("25"), a.getRoe());
        assertEquals(100, a.getRoePct());
        assertFalse(Boolean.TRUE.equals(a.getDataIncomplete()));
    }

    @Test
    void emptyPoolReturnsEmptyList() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of());
        assertTrue(service.calculateFactors("2026-09-01").isEmpty());
    }
}
```

- [x] **Step 2: 运行测试确认失败（Task 2）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningServiceTest`
Expected: COMPILATION ERROR（`StockFactor`/`ScreeningService` 不存在）

- [x] **Step 3: 创建 StockFactor PO（Task 2）**

创建 `StockAI/src/main/java/com/xian/stockai/po/StockFactor.java`（嵌套文档，同 `RegimeIndicators` 范式，无 `@Document`；字段含设计文档快照要求的原始值 + 因子分 + 理由所需百分位，`longTermRating` 在 Task 4 填充）：

```java
package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 个股四因子快照（screening_scores.stocks[] 嵌套文档）
 */
public class StockFactor {
    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;

    @Field(name = "pe")
    private BigDecimal pe;

    @Field(name = "pb")
    private BigDecimal pb;

    @Field(name = "roe")
    private BigDecimal roe;

    @Field(name = "gross_margin")
    private BigDecimal grossMargin;

    @Field(name = "revenue_yoy")
    private BigDecimal revenueYoy;

    @Field(name = "profit_yoy")
    private BigDecimal profitYoy;

    @Field(name = "momentum_return")
    private BigDecimal momentumReturn;

    @Field(name = "valuation")
    private Integer valuation;

    @Field(name = "growth")
    private Integer growth;

    @Field(name = "quality")
    private Integer quality;

    @Field(name = "momentum")
    private Integer momentum;

    @Field(name = "pe_pct")
    private Integer pePct;

    @Field(name = "roe_pct")
    private Integer roePct;

    @Field(name = "momentum_pct")
    private Integer momentumPct;

    @Field(name = "long_term_rating")
    private String longTermRating;

    @Field(name = "data_date")
    private String dataDate;

    @Field(name = "data_incomplete")
    private Boolean dataIncomplete;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getRoe() { return roe; }
    public void setRoe(BigDecimal roe) { this.roe = roe; }
    public BigDecimal getGrossMargin() { return grossMargin; }
    public void setGrossMargin(BigDecimal grossMargin) { this.grossMargin = grossMargin; }
    public BigDecimal getRevenueYoy() { return revenueYoy; }
    public void setRevenueYoy(BigDecimal revenueYoy) { this.revenueYoy = revenueYoy; }
    public BigDecimal getProfitYoy() { return profitYoy; }
    public void setProfitYoy(BigDecimal profitYoy) { this.profitYoy = profitYoy; }
    public BigDecimal getMomentumReturn() { return momentumReturn; }
    public void setMomentumReturn(BigDecimal momentumReturn) { this.momentumReturn = momentumReturn; }
    public Integer getValuation() { return valuation; }
    public void setValuation(Integer valuation) { this.valuation = valuation; }
    public Integer getGrowth() { return growth; }
    public void setGrowth(Integer growth) { this.growth = growth; }
    public Integer getQuality() { return quality; }
    public void setQuality(Integer quality) { this.quality = quality; }
    public Integer getMomentum() { return momentum; }
    public void setMomentum(Integer momentum) { this.momentum = momentum; }
    public Integer getPePct() { return pePct; }
    public void setPePct(Integer pePct) { this.pePct = pePct; }
    public Integer getRoePct() { return roePct; }
    public void setRoePct(Integer roePct) { this.roePct = roePct; }
    public Integer getMomentumPct() { return momentumPct; }
    public void setMomentumPct(Integer momentumPct) { this.momentumPct = momentumPct; }
    public String getLongTermRating() { return longTermRating; }
    public void setLongTermRating(String longTermRating) { this.longTermRating = longTermRating; }
    public String getDataDate() { return dataDate; }
    public void setDataDate(String dataDate) { this.dataDate = dataDate; }
    public Boolean getDataIncomplete() { return dataIncomplete; }
    public void setDataIncomplete(Boolean dataIncomplete) { this.dataIncomplete = dataIncomplete; }
}
```

- [x] **Step 4: 创建 ScreeningService（因子计算部分）（Task 2）**

创建 `StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.StockBasicInfoRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockFundamentalsRepository;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockFundamentals;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScreeningService {

    public static final String STYLE_VALUE = "value";
    public static final String STYLE_GROWTH = "growth";
    public static final String STYLE_BALANCED = "balanced";

    private static final int MOMENTUM_BARS = 21;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal VALUATION_PE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal VALUATION_PB_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal GROWTH_REVENUE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal GROWTH_PROFIT_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal QUALITY_ROE_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal QUALITY_GM_WEIGHT = new BigDecimal("0.4");

    private final IndexConstituentsRepository indexConstituentsRepository;
    private final StockFundamentalsRepository stockFundamentalsRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final StockBasicInfoRepository stockBasicInfoRepository;

    public ScreeningService(IndexConstituentsRepository indexConstituentsRepository,
                            StockFundamentalsRepository stockFundamentalsRepository,
                            StockDailyKlineRepository stockDailyKlineRepository,
                            StockBasicInfoRepository stockBasicInfoRepository) {
        this.indexConstituentsRepository = indexConstituentsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.stockBasicInfoRepository = stockBasicInfoRepository;
    }

    /**
     * 正向百分位：值越大得分越高，0~100 整数。n<=1 返回 50。
     */
    public static int percentileDesc(List<BigDecimal> values, BigDecimal target) {
        if (values == null || values.size() <= 1) {
            return 50;
        }
        long less = values.stream().filter(v -> v.compareTo(target) < 0).count();
        return roundToInt(less * 100.0 / (values.size() - 1));
    }

    /**
     * 反向百分位：值越小得分越高，0~100 整数。n<=1 返回 50。
     */
    public static int percentileAsc(List<BigDecimal> values, BigDecimal target) {
        if (values == null || values.size() <= 1) {
            return 50;
        }
        long greater = values.stream().filter(v -> v.compareTo(target) > 0).count();
        return roundToInt(greater * 100.0 / (values.size() - 1));
    }

    private static int roundToInt(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /**
     * 读取优质池 + 财务 + K线，计算四因子（横截面百分位，仅由当日数据决定，可复现）。
     */
    public List<StockFactor> calculateFactors(String tradeDate) {
        List<IndexConstituents> pool = indexConstituentsRepository.findAll();
        if (pool == null || pool.isEmpty()) {
            log.warn("优质池成分股为空，跳过因子计算");
            return new ArrayList<>();
        }
        Map<String, String> nameMap = stockBasicInfoRepository.findAll().stream()
                .filter(b -> b.getSymbol() != null && b.getName() != null)
                .collect(Collectors.toMap(StockBasicInfo::getSymbol, StockBasicInfo::getName, (a, b) -> a));

        LocalDate oneYearAgo = LocalDate.parse(tradeDate).minusYears(1);
        List<StockFactor> factors = new ArrayList<>();
        // 百分位输入集合
        List<BigDecimal> peValues = new ArrayList<>();
        List<BigDecimal> pbValues = new ArrayList<>();
        List<BigDecimal> roeValues = new ArrayList<>();
        List<BigDecimal> gmValues = new ArrayList<>();
        List<BigDecimal> revYoyValues = new ArrayList<>();
        List<BigDecimal> profitYoyValues = new ArrayList<>();
        List<BigDecimal> momentumReturns = new ArrayList<>();

        for (IndexConstituents c : pool) {
            String symbol = c.getSymbol();
            if (symbol == null || factors.stream().anyMatch(f -> symbol.equals(f.getSymbol()))) {
                continue; // 池内去重
            }
            StockFactor f = new StockFactor();
            f.setSymbol(symbol);
            f.setName(nameMap.getOrDefault(symbol, symbol));

            StockFundamentals fund = stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc(symbol);
            boolean fundUsable = fund != null && fund.getReportDate() != null
                    && !LocalDate.parse(fund.getReportDate()).isBefore(oneYearAgo);
            if (fundUsable) {
                f.setDataDate(fund.getReportDate());
                f.setPe(fund.getPe());
                f.setPb(fund.getPb());
                f.setRoe(fund.getRoe());
                f.setGrossMargin(fund.getGrossMargin());
                f.setRevenueYoy(fund.getRevenueYoy());
                f.setProfitYoy(fund.getProfitYoy());
                if (fund.getPe() != null && fund.getPe().compareTo(ZERO) > 0) {
                    peValues.add(fund.getPe());
                }
                if (fund.getPb() != null && fund.getPb().compareTo(ZERO) > 0) {
                    pbValues.add(fund.getPb());
                }
                if (fund.getRoe() != null) {
                    roeValues.add(fund.getRoe());
                }
                if (fund.getGrossMargin() != null) {
                    gmValues.add(fund.getGrossMargin());
                }
                if (fund.getRevenueYoy() != null) {
                    revYoyValues.add(fund.getRevenueYoy());
                }
                if (fund.getProfitYoy() != null) {
                    profitYoyValues.add(fund.getProfitYoy());
                }
            }

            List<StockDailyKline> klines =
                    stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc(symbol);
            if (klines != null && klines.size() >= MOMENTUM_BARS
                    && klines.get(0).getClose() != null && klines.get(MOMENTUM_BARS - 1).getClose() != null
                    && klines.get(MOMENTUM_BARS - 1).getClose().compareTo(ZERO) > 0) {
                BigDecimal latest = klines.get(0).getClose();
                BigDecimal oldest = klines.get(MOMENTUM_BARS - 1).getClose();
                f.setMomentumReturn(latest.subtract(oldest).divide(oldest, 6, RoundingMode.HALF_UP));
                momentumReturns.add(f.getMomentumReturn());
            }
            factors.add(f);
        }

        for (StockFactor f : factors) {
            boolean fundUsable = f.getDataDate() != null;
            if (fundUsable) {
                int pePct = (f.getPe() != null && f.getPe().compareTo(ZERO) > 0)
                        ? percentileAsc(peValues, f.getPe()) : 0;
                int pbPct = (f.getPb() != null && f.getPb().compareTo(ZERO) > 0)
                        ? percentileAsc(pbValues, f.getPb()) : 0;
                f.setPePct(pePct);
                f.setValuation(roundToInt(
                        VALUATION_PE_WEIGHT.multiply(BigDecimal.valueOf(pePct))
                                .add(VALUATION_PB_WEIGHT.multiply(BigDecimal.valueOf(pbPct)))
                                .doubleValue()));

                int revPct = f.getRevenueYoy() == null ? 0 : percentileDesc(revYoyValues, f.getRevenueYoy());
                int proPct = f.getProfitYoy() == null ? 0 : percentileDesc(profitYoyValues, f.getProfitYoy());
                f.setGrowth(roundToInt(
                        GROWTH_REVENUE_WEIGHT.multiply(BigDecimal.valueOf(revPct))
                                .add(GROWTH_PROFIT_WEIGHT.multiply(BigDecimal.valueOf(proPct)))
                                .doubleValue()));

                int roePct = f.getRoe() == null ? 0 : percentileDesc(roeValues, f.getRoe());
                int gmPct = f.getGrossMargin() == null ? 0 : percentileDesc(gmValues, f.getGrossMargin());
                f.setRoePct(roePct);
                f.setQuality(roundToInt(
                        QUALITY_ROE_WEIGHT.multiply(BigDecimal.valueOf(roePct))
                                .add(QUALITY_GM_WEIGHT.multiply(BigDecimal.valueOf(gmPct)))
                                .doubleValue()));
            }
            if (f.getMomentumReturn() != null) {
                f.setMomentumPct(percentileDesc(momentumReturns, f.getMomentumReturn()));
                f.setMomentum(f.getMomentumPct());
            }

            int missing = countMissing(f);
            f.setDataIncomplete(missing >= 2);
        }
        return factors;
    }

    private static int countMissing(StockFactor f) {
        int missing = 0;
        if (f.getValuation() == null) {
            missing++;
        }
        if (f.getGrowth() == null) {
            missing++;
        }
        if (f.getQuality() == null) {
            missing++;
        }
        if (f.getMomentum() == null) {
            missing++;
        }
        return missing;
    }
}
```

（注意：`countMissing`、`percentileDesc/Asc` 后续任务不再改动；`ScreeningService` 构造器在 Task 3/4 追加参数。）

- [x] **Step 5: 运行测试确认通过（Task 2）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningServiceTest`
Expected: PASS

- [x] **Step 6: 运行全量测试（Task 2）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 7: Commit（Task 2）**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/StockFactor.java \
        StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java \
        StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java
git commit -m "feat: 实现选股四因子横截面百分位计算引擎"
```

---

### Task 3: 综合评分加权合成与三风格榜单（1.2）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/config/ScreeningProperties.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/BoardEntry.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java`（新增 `buildBoard`/`weightedTotal`/`buildReason`，构造器追加 `ScreeningProperties`）
- Modify: `StockAI/src/main/resources/application.yml`（新增 `screening.weights`）
- Test: `StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java`（扩展）

**Interfaces:**
- Consumes: Task 2 的 `StockFactor`（因子分 + `dataIncomplete` + 百分位字段）。
- Produces:
  - `ScreeningProperties.weightsOf(String style) -> Map<String, BigDecimal>`（key: valuation/growth/quality/momentum；默认权重内置，yml `screening.weights.*` 可覆盖）
  - `ScreeningService.buildBoard(String style, List<StockFactor> factors) -> List<BoardEntry>`（综合分降序 Top 20，`dataIncomplete` 不入榜；总分含 reason）
  - `ScreeningService.buildReason(StockFactor f) -> String`（public static，Task 7 fallback 复用）
  - `BoardEntry` 字段：symbol/name/totalScore(BigDecimal, scale 2)/valuation/growth/quality/momentum/longTermRating/reason

**风格权重（设计文档默认值，同时写入 yml）：** value = 估值 40% / 成长 20% / 质量 30% / 动量 10%；growth = 10% / 40% / 20% / 30%；balanced = 25% / 25% / 30% / 20%。

- [x] **Step 1: 写失败的榜单测试（Task 3）**

在 `ScreeningServiceTest.java` 中追加（新增 import：`com.xian.stockai.config.ScreeningProperties`、`com.xian.stockai.po.BoardEntry`；`setUp` 中构造器追加 `new ScreeningProperties()`）：

```java
    // ==== Task 3: 综合评分与风格榜单 ====

    private StockFactor factor(String symbol, Integer val, Integer gro, Integer qua, Integer mon) {
        StockFactor f = new StockFactor();
        f.setSymbol(symbol);
        f.setName(symbol + "名称");
        f.setValuation(val);
        f.setGrowth(gro);
        f.setQuality(qua);
        f.setMomentum(mon);
        f.setDataIncomplete(false);
        return f;
    }

    @Test
    void buildBoardWeightsAndRanksByTotalScore() {
        List<StockFactor> factors = List.of(
                factor("A", 100, 100, 100, 100),
                factor("B", 60, 80, 70, 90),
                factor("C", null, 60, 80, 70)); // 缺估值 → 重归一化
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_VALUE, factors);
        // value 权重 [0.4,0.2,0.3,0.1]：A=100.00；B=0.4*60+0.2*80+0.3*70+0.1*90=70.00；
        // C 剔除估值后 (0.2*60+0.3*80+0.1*70)/0.6 = 43/0.6 = 71.67
        assertEquals(3, board.size());
        assertEquals("A", board.get(0).getSymbol());
        assertEquals("C", board.get(1).getSymbol());
        assertEquals("B", board.get(2).getSymbol());
        assertEquals(0, new BigDecimal("100.00").compareTo(board.get(0).getTotalScore()));
        assertEquals(0, new BigDecimal("71.67").compareTo(board.get(1).getTotalScore()));
        assertEquals(0, new BigDecimal("70.00").compareTo(board.get(2).getTotalScore()));
    }

    @Test
    void buildBoardGrowthStyleUsesGrowthWeights() {
        List<StockFactor> factors = List.of(
                factor("A", 100, 100, 100, 100),
                factor("B", 60, 80, 70, 90),
                factor("C", null, 60, 80, 70));
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_GROWTH, factors);
        // growth 权重 [0.1,0.4,0.2,0.3]：B=79.00；C=(0.4*60+0.2*80+0.3*70)/0.9=61/0.9=67.78
        assertEquals("A", board.get(0).getSymbol());
        assertEquals("B", board.get(1).getSymbol());
        assertEquals("C", board.get(2).getSymbol());
        assertEquals(0, new BigDecimal("79.00").compareTo(board.get(1).getTotalScore()));
        assertEquals(0, new BigDecimal("67.78").compareTo(board.get(2).getTotalScore()));
    }

    @Test
    void buildBoardExcludesIncompleteFactors() {
        StockFactor incomplete = factor("X", 90, 90, 90, 90);
        incomplete.setDataIncomplete(true);
        List<StockFactor> factors = List.of(incomplete, factor("A", 100, 100, 100, 100));
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_BALANCED, factors);
        assertEquals(1, board.size());
        assertEquals("A", board.get(0).getSymbol());
    }

    @Test
    void buildBoardCapsAtTopTwenty() {
        List<StockFactor> factors = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            factors.add(factor("S" + i, 100 - i, 100 - i, 100 - i, 100 - i));
        }
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_BALANCED, factors);
        assertEquals(20, board.size());
        assertEquals("S0", board.get(0).getSymbol());
        assertEquals("S19", board.get(19).getSymbol());
    }

    @Test
    void buildReasonMatchesDesignTemplate() {
        StockFactor f = factor("A", 100, 100, 100, 100);
        f.setRoePct(85);
        f.setPePct(82);
        f.setMomentumPct(70);
        assertEquals("ROE 池内前 15%，PE 低于 82% 成分股，20 日涨幅池内前 30%",
                ScreeningService.buildReason(f));
    }

    @Test
    void buildReasonSkipsNullParts() {
        StockFactor f = factor("B", 100, 100, 100, 100);
        f.setRoePct(60);
        // pePct、momentumPct 为 null（财务/K线缺失）时省略对应片段
        assertEquals("ROE 池内前 40%", ScreeningService.buildReason(f));
    }

    @Test
    void defaultWeightsMatchDesign() {
        ScreeningProperties props = new ScreeningProperties();
        assertEquals(new BigDecimal("0.40"), props.weightsOf("value").get("valuation"));
        assertEquals(new BigDecimal("0.20"), props.weightsOf("value").get("growth"));
        assertEquals(new BigDecimal("0.30"), props.weightsOf("value").get("quality"));
        assertEquals(new BigDecimal("0.10"), props.weightsOf("value").get("momentum"));
        assertEquals(new BigDecimal("0.40"), props.weightsOf("growth").get("growth"));
        assertEquals(new BigDecimal("0.25"), props.weightsOf("balanced").get("valuation"));
        assertEquals(new BigDecimal("0.30"), props.weightsOf("balanced").get("quality"));
    }
```

同时把 `setUp` 改为：

```java
    @BeforeEach
    void setUp() {
        service = new ScreeningService(indexConstituentsRepository, stockFundamentalsRepository,
                stockDailyKlineRepository, stockBasicInfoRepository, new ScreeningProperties());
    }
```

- [ ] **Step 2: 运行测试确认失败（Task 3）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningServiceTest`
Expected: COMPILATION ERROR（`ScreeningProperties`/`BoardEntry`/`buildBoard` 不存在）

- [x] **Step 3: 创建 ScreeningProperties 与 BoardEntry（Task 3）**

创建 `StockAI/src/main/java/com/xian/stockai/config/ScreeningProperties.java`：

```java
package com.xian.stockai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author xian
 * @description 选股引擎配置（风格权重外置 application.yml，缺省值内置）
 */
@Component
@ConfigurationProperties(prefix = "screening")
public class ScreeningProperties {

    private Map<String, Map<String, BigDecimal>> weights = defaultWeights();

    private static Map<String, Map<String, BigDecimal>> defaultWeights() {
        Map<String, Map<String, BigDecimal>> w = new LinkedHashMap<>();
        w.put("value", style("0.40", "0.20", "0.30", "0.10"));
        w.put("growth", style("0.10", "0.40", "0.20", "0.30"));
        w.put("balanced", style("0.25", "0.25", "0.30", "0.20"));
        return w;
    }

    private static Map<String, BigDecimal> style(String valuation, String growth,
                                                String quality, String momentum) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put("valuation", new BigDecimal(valuation));
        m.put("growth", new BigDecimal(growth));
        m.put("quality", new BigDecimal(quality));
        m.put("momentum", new BigDecimal(momentum));
        return m;
    }

    public Map<String, BigDecimal> weightsOf(String style) {
        Map<String, BigDecimal> w = weights.get(style);
        return w != null ? w : weights.get("balanced");
    }

    public Map<String, Map<String, BigDecimal>> getWeights() { return weights; }
    public void setWeights(Map<String, Map<String, BigDecimal>> weights) { this.weights = weights; }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/po/BoardEntry.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 风格榜单条目（screening_results.value/growth/balanced 嵌套文档）
 */
public class BoardEntry {
    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;

    @Field(name = "total_score")
    private BigDecimal totalScore;

    @Field(name = "valuation")
    private Integer valuation;

    @Field(name = "growth")
    private Integer growth;

    @Field(name = "quality")
    private Integer quality;

    @Field(name = "momentum")
    private Integer momentum;

    @Field(name = "long_term_rating")
    private String longTermRating;

    @Field(name = "reason")
    private String reason;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public Integer getValuation() { return valuation; }
    public void setValuation(Integer valuation) { this.valuation = valuation; }
    public Integer getGrowth() { return growth; }
    public void setGrowth(Integer growth) { this.growth = growth; }
    public Integer getQuality() { return quality; }
    public void setQuality(Integer quality) { this.quality = quality; }
    public Integer getMomentum() { return momentum; }
    public void setMomentum(Integer momentum) { this.momentum = momentum; }
    public String getLongTermRating() { return longTermRating; }
    public void setLongTermRating(String longTermRating) { this.longTermRating = longTermRating; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```

- [ ] **Step 4: 扩展 ScreeningService（Task 3）**

在 `ScreeningService.java` 中：
1. import 增加 `com.xian.stockai.config.ScreeningProperties`、`com.xian.stockai.po.BoardEntry`、`java.util.Comparator`。
2. 增加 `TOP_N` 常量与 `screeningProperties` 字段、构造器参数（放最后）：

```java
    private static final int TOP_N = 20;

    private final ScreeningProperties screeningProperties;

    public ScreeningService(IndexConstituentsRepository indexConstituentsRepository,
                            StockFundamentalsRepository stockFundamentalsRepository,
                            StockDailyKlineRepository stockDailyKlineRepository,
                            StockBasicInfoRepository stockBasicInfoRepository,
                            ScreeningProperties screeningProperties) {
        this.indexConstituentsRepository = indexConstituentsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.stockBasicInfoRepository = stockBasicInfoRepository;
        this.screeningProperties = screeningProperties;
    }
```

3. 追加榜单方法：

```java
    /**
     * 生成指定风格的 Top 20 候选榜：data_incomplete 不入榜；缺失因子按剩余权重重归一化。
     */
    public List<BoardEntry> buildBoard(String style, List<StockFactor> factors) {
        Map<String, BigDecimal> weights = screeningProperties.weightsOf(style);
        List<BoardEntry> entries = new ArrayList<>();
        for (StockFactor f : factors) {
            if (Boolean.TRUE.equals(f.getDataIncomplete())) {
                continue;
            }
            BigDecimal total = weightedTotal(weights, f);
            if (total == null) {
                continue;
            }
            BoardEntry e = new BoardEntry();
            e.setSymbol(f.getSymbol());
            e.setName(f.getName());
            e.setTotalScore(total);
            e.setValuation(f.getValuation());
            e.setGrowth(f.getGrowth());
            e.setQuality(f.getQuality());
            e.setMomentum(f.getMomentum());
            e.setLongTermRating(f.getLongTermRating());
            e.setReason(buildReason(f));
            entries.add(e);
        }
        entries.sort(Comparator.comparing(BoardEntry::getTotalScore).reversed()
                .thenComparing(BoardEntry::getSymbol));
        return entries.size() > TOP_N ? new ArrayList<>(entries.subList(0, TOP_N)) : entries;
    }

    private BigDecimal weightedTotal(Map<String, BigDecimal> weights, StockFactor f) {
        BigDecimal sum = ZERO;
        BigDecimal weightSum = ZERO;
        if (f.getValuation() != null) {
            sum = sum.add(weights.get("valuation").multiply(BigDecimal.valueOf(f.getValuation())));
            weightSum = weightSum.add(weights.get("valuation"));
        }
        if (f.getGrowth() != null) {
            sum = sum.add(weights.get("growth").multiply(BigDecimal.valueOf(f.getGrowth())));
            weightSum = weightSum.add(weights.get("growth"));
        }
        if (f.getQuality() != null) {
            sum = sum.add(weights.get("quality").multiply(BigDecimal.valueOf(f.getQuality())));
            weightSum = weightSum.add(weights.get("quality"));
        }
        if (f.getMomentum() != null) {
            sum = sum.add(weights.get("momentum").multiply(BigDecimal.valueOf(f.getMomentum())));
            weightSum = weightSum.add(weights.get("momentum"));
        }
        if (weightSum.compareTo(ZERO) <= 0) {
            return null;
        }
        return sum.divide(weightSum, 2, RoundingMode.HALF_UP);
    }

    /**
     * 模板化入选理由（纯量化文本，不用 AI，保证可复现）。
     */
    public static String buildReason(StockFactor f) {
        List<String> parts = new ArrayList<>();
        if (f.getRoePct() != null) {
            parts.add("ROE 池内前 " + (100 - f.getRoePct()) + "%");
        }
        if (f.getPePct() != null) {
            parts.add("PE 低于 " + f.getPePct() + "% 成分股");
        }
        if (f.getMomentumPct() != null) {
            parts.add("20 日涨幅池内前 " + (100 - f.getMomentumPct()) + "%");
        }
        return String.join("，", parts);
    }
```

- [x] **Step 5: application.yml 增加权重配置（Task 3）**

在 `StockAI/src/main/resources/application.yml` 末尾追加（与默认值一致，外置供调参）：

```yaml

# 选股引擎：三风格四因子权重（估值/成长/质量/动量）
screening:
  weights:
    value:
      valuation: 0.40
      growth: 0.20
      quality: 0.30
      momentum: 0.10
    growth:
      valuation: 0.10
      growth: 0.40
      quality: 0.20
      momentum: 0.30
    balanced:
      valuation: 0.25
      growth: 0.25
      quality: 0.30
      momentum: 0.20
```

- [ ] **Step 6: 运行测试确认通过（Task 3）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningServiceTest`
Expected: PASS（含 Task 2 既有用例）

- [ ] **Step 7: 运行全量测试（Task 3）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 8: Commit（Task 3）**

```bash
git add StockAI/src/main/java/com/xian/stockai/config/ScreeningProperties.java \
        StockAI/src/main/java/com/xian/stockai/po/BoardEntry.java \
        StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java \
        StockAI/src/main/resources/application.yml \
        StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java
git commit -m "feat: 实现三风格综合评分加权合成与 Top20 候选榜"
```

---

### Task 4: 长期价值评级与全量快照入库（2.1）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/ScreeningScoreRecord.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/ScreeningScoreRecordRepository.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java`（新增 `rateLongTerm`、`calculateAndSaveSnapshot`，构造器追加 `ScreeningScoreRecordRepository`）
- Test: `StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java`（扩展）、`StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java`（扩展）

**Interfaces:**
- Consumes: Task 2 `calculateFactors`、Task 3 `buildBoard` 无关（本任务独立）。
- Produces:
  - `ScreeningService.calculateAndSaveSnapshot(String tradeDate) -> List<StockFactor>`（计算因子 + 填充评级 + upsert `screening_scores`；Task 5 热点从该集合读质量分、Task 8 编排调用）
  - `ScreeningService.RATING_HIGH = "HIGH"` / `RATING_MEDIUM = "MEDIUM"` / `RATING_LOW = "LOW"` / `RATING_UNKNOWN = "UNKNOWN"` 常量
  - `ScreeningScoreRecordRepository.findByTradeDate(String) -> ScreeningScoreRecord`、`findTopByOrderByTradeDateDesc() -> ScreeningScoreRecord`

**评级规则（设计文档）：** 长期价值分 = 质量分 × 60% + 成长分 × 40%（任一缺失用另一方 100%；两者均缺失 → UNKNOWN）；≥65 → HIGH；45~65 → MEDIUM；<45 → LOW。

- [x] **Step 1: 写失败的测试（Task 4）**

在 `ScreenerPoTest.java` 追加：

```java
    @Test
    void screeningScoreRecordMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = ScreeningScoreRecord.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("screening_scores", doc.collection());
        assertEquals("trade_date", fieldName(ScreeningScoreRecord.class, "tradeDate"));
        assertEquals("generated_at", fieldName(ScreeningScoreRecord.class, "generatedAt"));
        assertEquals("stocks", fieldName(ScreeningScoreRecord.class, "stocks"));
        Indexed indexed = ScreeningScoreRecord.class.getDeclaredField("tradeDate")
                .getAnnotation(Indexed.class);
        assertNotNull(indexed);
        assertTrue(indexed.unique());
    }
```

（import 需补 `org.springframework.data.mongodb.core.index.Indexed` 已有。）

在 `ScreeningServiceTest.java` 追加（新增 `@Mock private ScreeningScoreRecordRepository screeningScoreRecordRepository;`，import `com.xian.stockai.dao.ScreeningScoreRecordRepository`、`com.xian.stockai.po.ScreeningScoreRecord`、`org.mockito.ArgumentCaptor`、`static org.mockito.Mockito.verify`；setUp 构造器再追加该 mock）：

```java
    // ==== Task 4: 长期评级与快照入库 ====

    @Test
    void rateLongTermThresholds() {
        // 65 边界：0.6*65+0.4*65 = 65 → HIGH
        assertEquals(ScreeningService.RATING_HIGH,
                ScreeningService.rateLongTerm(factor("A", null, 65, 65, null)));
        // 45 边界：0.6*45+0.4*45 = 45 → MEDIUM
        assertEquals(ScreeningService.RATING_MEDIUM,
                ScreeningService.rateLongTerm(factor("B", null, 45, 45, null)));
        // 低于 45：0.6*44+0.4*46 = 44.8 → LOW
        assertEquals(ScreeningService.RATING_LOW,
                ScreeningService.rateLongTerm(factor("C", null, 44, 46, null)));
        // 组合：0.6*60+0.4*30 = 48 → MEDIUM
        assertEquals(ScreeningService.RATING_MEDIUM,
                ScreeningService.rateLongTerm(factor("D", null, 30, 60, null)));
    }

    @Test
    void rateLongTermFallsBackToSingleFactor() {
        assertEquals(ScreeningService.RATING_HIGH,
                ScreeningService.rateLongTerm(factor("A", null, 70, null, null)));
        assertEquals(ScreeningService.RATING_LOW,
                ScreeningService.rateLongTerm(factor("B", null, null, 40, null)));
        assertEquals(ScreeningService.RATING_UNKNOWN,
                ScreeningService.rateLongTerm(factor("C", null, null, null, null)));
    }

    @Test
    void calculateAndSaveSnapshotFillsRatingsAndUpserts() {
        mockStandardPool();
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(null);
        List<StockFactor> factors = service.calculateAndSaveSnapshot("2026-09-01");
        ArgumentCaptor<ScreeningScoreRecord> captor =
                ArgumentCaptor.forClass(ScreeningScoreRecord.class);
        verify(screeningScoreRecordRepository).save(captor.capture());
        ScreeningScoreRecord saved = captor.getValue();
        assertEquals("2026-09-01", saved.getTradeDate());
        assertNotNull(saved.getGeneratedAt());
        assertEquals(5, saved.getStocks().size());
        Map<String, StockFactor> bySymbol = factors.stream()
                .collect(Collectors.toMap(StockFactor::getSymbol, Function.identity()));
        // A：quality=100、growth=100 → 100 → HIGH；B：quality=0、growth=0 → LOW；E：财务超期 → UNKNOWN
        assertEquals(ScreeningService.RATING_HIGH, bySymbol.get("A").getLongTermRating());
        assertEquals(ScreeningService.RATING_LOW, bySymbol.get("B").getLongTermRating());
        assertEquals(ScreeningService.RATING_UNKNOWN, bySymbol.get("E").getLongTermRating());
    }

    @Test
    void calculateAndSaveSnapshotReusesExistingIdOnSameDay() {
        mockStandardPool();
        ScreeningScoreRecord existing = new ScreeningScoreRecord();
        existing.setId("existing-id");
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(existing);
        service.calculateAndSaveSnapshot("2026-09-01");
        ArgumentCaptor<ScreeningScoreRecord> captor =
                ArgumentCaptor.forClass(ScreeningScoreRecord.class);
        verify(screeningScoreRecordRepository).save(captor.capture());
        assertEquals("existing-id", captor.getValue().getId());
    }
```

setUp 最终形态：

```java
    @BeforeEach
    void setUp() {
        service = new ScreeningService(indexConstituentsRepository, stockFundamentalsRepository,
                stockDailyKlineRepository, stockBasicInfoRepository, new ScreeningProperties(),
                screeningScoreRecordRepository);
    }
```

- [ ] **Step 2: 运行测试确认失败（Task 4）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningServiceTest,ScreenerPoTest`
Expected: COMPILATION ERROR（`ScreeningScoreRecord` 不存在）

- [x] **Step 3: 创建 ScreeningScoreRecord 与 Repository（Task 4）**

创建 `StockAI/src/main/java/com/xian/stockai/po/ScreeningScoreRecord.java`：

```java
package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author xian
 * @description 选股全量因子快照（每日一文档，trade_date 唯一）
 */
@Document(collection = "screening_scores")
public class ScreeningScoreRecord {
    @Id
    private String id;

    @Field(name = "trade_date")
    @Indexed(unique = true)
    private String tradeDate;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    @Field(name = "stocks")
    private List<StockFactor> stocks;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<StockFactor> getStocks() { return stocks; }
    public void setStocks(List<StockFactor> stocks) { this.stocks = stocks; }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/dao/ScreeningScoreRecordRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.ScreeningScoreRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScreeningScoreRecordRepository extends MongoRepository<ScreeningScoreRecord, String> {
    ScreeningScoreRecord findByTradeDate(String tradeDate);

    ScreeningScoreRecord findTopByOrderByTradeDateDesc();
}
```

- [x] **Step 4: 扩展 ScreeningService（评级 + 快照）（Task 4）**

在 `ScreeningService.java` 中：
1. import 增加 `com.xian.stockai.dao.ScreeningScoreRecordRepository`、`com.xian.stockai.po.ScreeningScoreRecord`、`java.time.LocalDateTime`。
2. 增加评级常量与 repository 字段，构造器追加参数（放最后）：

```java
    public static final String RATING_HIGH = "HIGH";
    public static final String RATING_MEDIUM = "MEDIUM";
    public static final String RATING_LOW = "LOW";
    public static final String RATING_UNKNOWN = "UNKNOWN";

    private static final BigDecimal LONG_TERM_QUALITY_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal LONG_TERM_GROWTH_WEIGHT = new BigDecimal("0.4");

    private final ScreeningScoreRecordRepository screeningScoreRecordRepository;

    public ScreeningService(IndexConstituentsRepository indexConstituentsRepository,
                            StockFundamentalsRepository stockFundamentalsRepository,
                            StockDailyKlineRepository stockDailyKlineRepository,
                            StockBasicInfoRepository stockBasicInfoRepository,
                            ScreeningProperties screeningProperties,
                            ScreeningScoreRecordRepository screeningScoreRecordRepository) {
        this.indexConstituentsRepository = indexConstituentsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.stockBasicInfoRepository = stockBasicInfoRepository;
        this.screeningProperties = screeningProperties;
        this.screeningScoreRecordRepository = screeningScoreRecordRepository;
    }
```

3. 追加方法：

```java
    /**
     * 长期价值评级：质量 60% + 成长 40%，任一缺失用另一方 100%，均缺失 → UNKNOWN。
     */
    public static String rateLongTerm(StockFactor f) {
        Integer q = f.getQuality();
        Integer g = f.getGrowth();
        if (q == null && g == null) {
            return RATING_UNKNOWN;
        }
        BigDecimal score;
        if (q == null) {
            score = BigDecimal.valueOf(g);
        } else if (g == null) {
            score = BigDecimal.valueOf(q);
        } else {
            score = BigDecimal.valueOf(q).multiply(LONG_TERM_QUALITY_WEIGHT)
                    .add(BigDecimal.valueOf(g).multiply(LONG_TERM_GROWTH_WEIGHT));
        }
        if (score.compareTo(new BigDecimal("65")) >= 0) {
            return RATING_HIGH;
        }
        if (score.compareTo(new BigDecimal("45")) >= 0) {
            return RATING_MEDIUM;
        }
        return RATING_LOW;
    }

    /**
     * 计算因子 + 填充长期评级 + 全量快照 upsert 入库（screening_scores）。
     */
    public List<StockFactor> calculateAndSaveSnapshot(String tradeDate) {
        List<StockFactor> factors = calculateFactors(tradeDate);
        for (StockFactor f : factors) {
            f.setLongTermRating(rateLongTerm(f));
        }
        ScreeningScoreRecord record = screeningScoreRecordRepository.findByTradeDate(tradeDate);
        if (record == null) {
            record = new ScreeningScoreRecord();
        }
        record.setTradeDate(tradeDate);
        record.setGeneratedAt(LocalDateTime.now());
        record.setStocks(factors);
        screeningScoreRecordRepository.save(record);
        log.info("选股快照已入库: tradeDate={}, 股票数={}", tradeDate, factors.size());
        return factors;
    }
```

- [ ] **Step 5: 运行测试确认通过（Task 4）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningServiceTest,ScreenerPoTest`
Expected: PASS

- [ ] **Step 6: 运行全量测试（Task 4）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 7: Commit（Task 4）**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/ScreeningScoreRecord.java \
        StockAI/src/main/java/com/xian/stockai/dao/ScreeningScoreRecordRepository.java \
        StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java \
        StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java \
        StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java
git commit -m "feat: 实现长期价值评级与选股全量快照入库"
```

---

### Task 5: 热点方向聚合与热度得分（3.1）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/HotspotStock.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/HotspotDirection.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/HotspotResult.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/HotspotResultRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/HotspotService.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/HotspotServiceTest.java`、`StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java`（扩展）

**Interfaces:**
- Consumes: `ScreeningScoreRecordRepository.findByTradeDate`（质量分）、`IndexConstituentsRepository.findAll`、`StockIndustryConceptRepository.findBySymbol`、`StockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc`、`ScreeningService.percentileDesc`（public static）。
- Produces:
  - `HotspotService.buildAndSave(String tradeDate) -> HotspotResult`（聚合 + 入库；无快照返回 null）
  - `HotspotService.latest() -> Result<HotspotResult>`（Task 8 Controller 用）
  - `HotspotDirection` / `HotspotStock` / `HotspotResult` 字段（见 Step 3；Task 6/7/10 依赖）

**热点算法（设计文档）：**
- 维度：行业（industry）与概念（concepts 展开）分别聚合；**成分股 <3 只的方向过滤**。
- 每方向指标：单日平均涨幅、单日成交额占全池比例、涨停家数（单日涨幅 ≥9.9%）、5 日平均累计涨幅、5 日平均成交额占比。
- 热度得分 = 5日热度 × 60% + 单日热度 × 40%；窗口内 = 涨幅分 × 40% + 成交额占比分 × 40% + 涨停家数分 × 20%（各指标跨方向百分位归一，行业/概念各自维度内独立归一）。
- 5 日涨停家数 = 方向内最近 5 个交易日中至少有一天涨幅 ≥9.9% 的股票数（按股去重）。
- 输出行业 Top 10 + 概念 Top 10，按热度降序，附代表股（按单日涨幅+5日累计涨幅之和降序取 3 只）。
- 个股 K 线不足 6 条（5 日窗口需要 6 个收盘点）→ 该股不参与热点聚合。
- 快照缺失（`findByTradeDate` 为 null）→ 返回 null 并记日志。

- [ ] **Step 1: 写失败的热点测试（Task 5）**

在 `ScreenerPoTest.java` 追加：

```java
    @Test
    void hotspotPosMapCollectionAndFields() throws NoSuchFieldException {
        Document doc = HotspotResult.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("hotspot_results", doc.collection());
        assertEquals("trade_date", fieldName(HotspotResult.class, "tradeDate"));
        assertEquals("generated_at", fieldName(HotspotResult.class, "generatedAt"));
        assertEquals("industries", fieldName(HotspotResult.class, "industries"));
        assertEquals("concepts", fieldName(HotspotResult.class, "concepts"));
        Indexed indexed = HotspotResult.class.getDeclaredField("tradeDate")
                .getAnnotation(Indexed.class);
        assertNotNull(indexed);
        assertTrue(indexed.unique());
        assertEquals("hot_score", fieldName(HotspotDirection.class, "hotScore"));
        assertEquals("daily_avg_change", fieldName(HotspotDirection.class, "dailyAvgChange"));
        assertEquals("daily_amount_ratio", fieldName(HotspotDirection.class, "dailyAmountRatio"));
        assertEquals("limit_up_count", fieldName(HotspotDirection.class, "limitUpCount"));
        assertEquals("five_day_avg_change", fieldName(HotspotDirection.class, "fiveDayAvgChange"));
        assertEquals("five_day_amount_ratio", fieldName(HotspotDirection.class, "fiveDayAmountRatio"));
        assertEquals("five_day_limit_up_count", fieldName(HotspotDirection.class, "fiveDayLimitUpCount"));
        assertEquals("stock_count", fieldName(HotspotDirection.class, "stockCount"));
        assertEquals("hype_label", fieldName(HotspotDirection.class, "hypeLabel"));
        assertEquals("hype_basis", fieldName(HotspotDirection.class, "hypeBasis"));
        assertEquals("top_stocks", fieldName(HotspotDirection.class, "topStocks"));
        assertEquals("daily_change", fieldName(HotspotStock.class, "dailyChange"));
        assertEquals("five_day_change", fieldName(HotspotStock.class, "fiveDayChange"));
    }
```

创建 `StockAI/src/test/java/com/xian/stockai/service/HotspotServiceTest.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.HotspotResultRepository;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.ScreeningScoreRecordRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.ScreeningScoreRecord;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockIndustryConcept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotspotServiceTest {

    @Mock
    private IndexConstituentsRepository indexConstituentsRepository;
    @Mock
    private StockIndustryConceptRepository stockIndustryConceptRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private ScreeningScoreRecordRepository screeningScoreRecordRepository;
    @Mock
    private HotspotResultRepository hotspotResultRepository;

    private HotspotService service;

    @BeforeEach
    void setUp() {
        service = new HotspotService(indexConstituentsRepository, stockIndustryConceptRepository,
                stockDailyKlineRepository, screeningScoreRecordRepository, hotspotResultRepository);
    }

    /** 6 条降序 K 线：最新 close=latest，其余 close=base，amount 恒定。 */
    private List<StockDailyKline> flatKlines(String symbol, String base, String latest, String amount) {
        List<StockDailyKline> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            StockDailyKline k = new StockDailyKline();
            k.setSymbol(symbol);
            k.setTradeDate(LocalDate.of(2026, 9, 1).minusDays(i).toString());
            k.setClose(i == 0 ? new BigDecimal(latest) : new BigDecimal(base));
            k.setAmount(new BigDecimal(amount));
            list.add(k);
        }
        return list;
    }

    private StockIndustryConcept industry(String symbol, String industry, List<String> concepts) {
        StockIndustryConcept ic = new StockIndustryConcept();
        ic.setSymbol(symbol);
        ic.setIndustry(industry);
        ic.setConcepts(concepts);
        return ic;
    }

    private StockFactor factor(String symbol, Integer quality) {
        StockFactor f = new StockFactor();
        f.setSymbol(symbol);
        f.setName(symbol + "名称");
        f.setQuality(quality);
        return f;
    }

    private void mockPool() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of(
                constituent("A"), constituent("B"), constituent("C"),
                constituent("D"), constituent("E"),
                constituent("F"), constituent("G"), constituent("H")));
        when(stockIndustryConceptRepository.findBySymbol("A"))
                .thenReturn(industry("A", "白酒", List.of("消费")));
        when(stockIndustryConceptRepository.findBySymbol("B"))
                .thenReturn(industry("B", "白酒", List.of("消费")));
        when(stockIndustryConceptRepository.findBySymbol("C"))
                .thenReturn(industry("C", "白酒", List.of("消费")));
        when(stockIndustryConceptRepository.findBySymbol("D"))
                .thenReturn(industry("D", "银行", List.of("科技")));
        when(stockIndustryConceptRepository.findBySymbol("E"))
                .thenReturn(industry("E", "银行", List.of("科技")));
        when(stockIndustryConceptRepository.findBySymbol("F"))
                .thenReturn(industry("F", "医药", List.of()));
        when(stockIndustryConceptRepository.findBySymbol("G"))
                .thenReturn(industry("G", "医药", List.of()));
        when(stockIndustryConceptRepository.findBySymbol("H"))
                .thenReturn(industry("H", "医药", List.of()));
        // 成交额：A=100000 B=50000 C=30000 D=20000 E=10000 F=150000 G=60000 H=40000（全池 460000）
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("A"))
                .thenReturn(flatKlines("A", "10", "11", "100000"));   // 涨停 0.1
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("B"))
                .thenReturn(flatKlines("B", "10", "10.5", "50000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("C"))
                .thenReturn(flatKlines("C", "10", "10.2", "30000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("D"))
                .thenReturn(flatKlines("D", "10", "10.1", "20000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("E"))
                .thenReturn(flatKlines("E", "10", "9.9", "10000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("F"))
                .thenReturn(flatKlines("F", "10", "12", "150000"));   // 涨停 0.2
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("G"))
                .thenReturn(flatKlines("G", "10", "10.5", "60000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("H"))
                .thenReturn(flatKlines("H", "10", "10.1", "40000"));

        ScreeningScoreRecord snapshot = new ScreeningScoreRecord();
        snapshot.setTradeDate("2026-09-01");
        snapshot.setStocks(List.of(
                factor("A", 50), factor("B", 70), factor("C", 90),
                factor("D", 60), factor("E", 30),
                factor("F", 30), factor("G", 35), factor("H", 38)));
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(snapshot);
    }

    private IndexConstituents constituent(String symbol) {
        IndexConstituents c = new IndexConstituents();
        c.setSymbol(symbol);
        return c;
    }

    @Test
    void buildAndSaveAggregatesIndustryAndFiltersSmallDirections() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        assertNotNull(result);
        // 行业维度：白酒(A,B,C)、医药(F,G,H) 保留；银行(D,E) 仅 2 只 → 过滤
        assertEquals(2, result.getIndustries().size());
        // 概念维度：消费(A,B,C) 保留；科技(D,E) 仅 2 只 → 过滤
        assertEquals(1, result.getConcepts().size());
        verify(hotspotResultRepository).save(result);
    }

    @Test
    void buildAndSaveComputesDirectionMetrics() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        HotspotDirection medicine = result.getIndustries().stream()
                .filter(d -> "医药".equals(d.getName())).findFirst().orElseThrow();
        // 医药：dailyAvgChange = (0.2+0.05+0.01)/3 = 0.086667
        assertEquals(0, new BigDecimal("0.086667").compareTo(medicine.getDailyAvgChange()));
        // 医药成交额占比 = 250000/460000 = 0.543478
        assertEquals(0, new BigDecimal("0.543478").compareTo(medicine.getDailyAmountRatio()));
        assertEquals(1, medicine.getLimitUpCount()); // F 涨停
        assertEquals(3, medicine.getStockCount());
        assertEquals(1, medicine.getFiveDayLimitUpCount()); // F 5日内有涨停
    }

    @Test
    void buildAndSaveRanksByHotScore() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        // 行业维度 2 个方向：医药各指标均高于白酒 → 医药热度 80、白酒 0
        HotspotDirection top = result.getIndustries().get(0);
        assertEquals("医药", top.getName());
        assertEquals(80, top.getHotScore());
        assertEquals(0, result.getIndustries().get(1).getHotScore());
    }

    @Test
    void buildAndSavePicksRepresentativeStocks() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        HotspotDirection medicine = result.getIndustries().stream()
                .filter(d -> "医药".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(3, medicine.getTopStocks().size());
        // 按 单日涨幅+5日涨幅 降序：F(0.4) > G(0.1) > H(0.02)
        assertEquals("F", medicine.getTopStocks().get(0).getSymbol());
        assertEquals("G", medicine.getTopStocks().get(1).getSymbol());
        assertEquals("H", medicine.getTopStocks().get(2).getSymbol());
    }

    @Test
    void buildAndSaveUpsertsByTradeDate() {
        mockPool();
        HotspotResult existing = new HotspotResult();
        existing.setId("existing-id");
        when(hotspotResultRepository.findByTradeDate("2026-09-01")).thenReturn(existing);
        HotspotResult result = service.buildAndSave("2026-09-01");
        ArgumentCaptor<HotspotResult> captor = ArgumentCaptor.forClass(HotspotResult.class);
        verify(hotspotResultRepository).save(captor.capture());
        assertEquals("existing-id", captor.getValue().getId());
        assertEquals("2026-09-01", result.getTradeDate());
    }

    @Test
    void buildAndSaveReturnsNullWhenSnapshotMissing() {
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(null);
        assertNull(service.buildAndSave("2026-09-01"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败（Task 5）**

Run: `cd StockAI && ./mvnw test -Dtest=HotspotServiceTest,ScreenerPoTest`
Expected: COMPILATION ERROR（`HotspotResult` 等不存在）

- [ ] **Step 3: 创建热点 PO 与 Repository（Task 5）**

创建 `StockAI/src/main/java/com/xian/stockai/po/HotspotStock.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 热点方向代表股
 */
public class HotspotStock {
    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;

    @Field(name = "daily_change")
    private BigDecimal dailyChange;

    @Field(name = "five_day_change")
    private BigDecimal fiveDayChange;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getDailyChange() { return dailyChange; }
    public void setDailyChange(BigDecimal dailyChange) { this.dailyChange = dailyChange; }
    public BigDecimal getFiveDayChange() { return fiveDayChange; }
    public void setFiveDayChange(BigDecimal fiveDayChange) { this.fiveDayChange = fiveDayChange; }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/po/HotspotDirection.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author xian
 * @description 热点方向（行业或概念）
 */
public class HotspotDirection {
    @Field(name = "name")
    private String name;

    @Field(name = "hot_score")
    private Integer hotScore;

    @Field(name = "daily_avg_change")
    private BigDecimal dailyAvgChange;

    @Field(name = "daily_amount_ratio")
    private BigDecimal dailyAmountRatio;

    @Field(name = "limit_up_count")
    private Integer limitUpCount;

    @Field(name = "five_day_avg_change")
    private BigDecimal fiveDayAvgChange;

    @Field(name = "five_day_amount_ratio")
    private BigDecimal fiveDayAmountRatio;

    @Field(name = "five_day_limit_up_count")
    private Integer fiveDayLimitUpCount;

    @Field(name = "stock_count")
    private Integer stockCount;

    @Field(name = "hype_label")
    private String hypeLabel;

    @Field(name = "hype_basis")
    private String hypeBasis;

    @Field(name = "top_stocks")
    private List<HotspotStock> topStocks;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getHotScore() { return hotScore; }
    public void setHotScore(Integer hotScore) { this.hotScore = hotScore; }
    public BigDecimal getDailyAvgChange() { return dailyAvgChange; }
    public void setDailyAvgChange(BigDecimal dailyAvgChange) { this.dailyAvgChange = dailyAvgChange; }
    public BigDecimal getDailyAmountRatio() { return dailyAmountRatio; }
    public void setDailyAmountRatio(BigDecimal dailyAmountRatio) { this.dailyAmountRatio = dailyAmountRatio; }
    public Integer getLimitUpCount() { return limitUpCount; }
    public void setLimitUpCount(Integer limitUpCount) { this.limitUpCount = limitUpCount; }
    public BigDecimal getFiveDayAvgChange() { return fiveDayAvgChange; }
    public void setFiveDayAvgChange(BigDecimal fiveDayAvgChange) { this.fiveDayAvgChange = fiveDayAvgChange; }
    public BigDecimal getFiveDayAmountRatio() { return fiveDayAmountRatio; }
    public void setFiveDayAmountRatio(BigDecimal fiveDayAmountRatio) { this.fiveDayAmountRatio = fiveDayAmountRatio; }
    public Integer getFiveDayLimitUpCount() { return fiveDayLimitUpCount; }
    public void setFiveDayLimitUpCount(Integer fiveDayLimitUpCount) { this.fiveDayLimitUpCount = fiveDayLimitUpCount; }
    public Integer getStockCount() { return stockCount; }
    public void setStockCount(Integer stockCount) { this.stockCount = stockCount; }
    public String getHypeLabel() { return hypeLabel; }
    public void setHypeLabel(String hypeLabel) { this.hypeLabel = hypeLabel; }
    public String getHypeBasis() { return hypeBasis; }
    public void setHypeBasis(String hypeBasis) { this.hypeBasis = hypeBasis; }
    public List<HotspotStock> getTopStocks() { return topStocks; }
    public void setTopStocks(List<HotspotStock> topStocks) { this.topStocks = topStocks; }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/po/HotspotResult.java`：

```java
package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author xian
 * @description 热点识别结果（每日一文档，trade_date 唯一）
 */
@Document(collection = "hotspot_results")
public class HotspotResult {
    @Id
    private String id;

    @Field(name = "trade_date")
    @Indexed(unique = true)
    private String tradeDate;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    @Field(name = "industries")
    private List<HotspotDirection> industries;

    @Field(name = "concepts")
    private List<HotspotDirection> concepts;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<HotspotDirection> getIndustries() { return industries; }
    public void setIndustries(List<HotspotDirection> industries) { this.industries = industries; }
    public List<HotspotDirection> getConcepts() { return concepts; }
    public void setConcepts(List<HotspotDirection> concepts) { this.concepts = concepts; }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/dao/HotspotResultRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.HotspotResult;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HotspotResultRepository extends MongoRepository<HotspotResult, String> {
    HotspotResult findByTradeDate(String tradeDate);

    HotspotResult findTopByOrderByTradeDateDesc();
}
```

- [ ] **Step 4: 创建 HotspotService（聚合 + 热度，炒作标注在 Task 6 接入）**

创建 `StockAI/src/main/java/com/xian/stockai/service/HotspotService.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.HotspotResultRepository;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.ScreeningScoreRecordRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.HotspotStock;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.ScreeningScoreRecord;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class HotspotService {

    private static final int MIN_DIRECTION_SIZE = 3;
    private static final int TOP_N = 10;
    private static final int REPRESENTATIVE_COUNT = 3;
    private static final int FIVE_DAY_WINDOW = 5;
    private static final BigDecimal LIMIT_UP_THRESHOLD = new BigDecimal("0.099");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal CHANGE_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal AMOUNT_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal LIMIT_UP_WEIGHT = new BigDecimal("0.2");
    private static final BigDecimal FIVE_DAY_HEAT_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal DAILY_HEAT_WEIGHT = new BigDecimal("0.4");

    private final IndexConstituentsRepository indexConstituentsRepository;
    private final StockIndustryConceptRepository stockIndustryConceptRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final ScreeningScoreRecordRepository screeningScoreRecordRepository;
    private final HotspotResultRepository hotspotResultRepository;

    /** 成分股量价统计（内存中间对象）。 */
    private static class MemberStats {
        String symbol;
        String name;
        Integer quality;
        BigDecimal dailyChange;
        BigDecimal fiveDayChange;
        BigDecimal amount;
        BigDecimal avgAmount5;
        int limitUpDays5;

        BigDecimal heatContribution() {
            return dailyChange.add(fiveDayChange);
        }
    }

    /** 方向聚合中间对象。 */
    private static class DirectionAgg {
        String name;
        List<MemberStats> members = new ArrayList<>();

        BigDecimal dailyAvgChange() {
            return avg(members, m -> m.dailyChange);
        }

        BigDecimal fiveDayAvgChange() {
            return avg(members, m -> m.fiveDayChange);
        }

        private BigDecimal avg(List<MemberStats> list, Function<MemberStats, BigDecimal> getter) {
            BigDecimal sum = ZERO;
            for (MemberStats m : list) {
                sum = sum.add(getter.apply(m));
            }
            return list.isEmpty() ? ZERO
                    : sum.divide(BigDecimal.valueOf(list.size()), 6, RoundingMode.HALF_UP);
        }
    }

    /**
     * 聚合行业/概念量价 → 热度得分 → Top10 → 入库（upsert）。无当日快照返回 null。
     */
    public HotspotResult buildAndSave(String tradeDate) {
        HotspotResult result = build(tradeDate);
        if (result == null) {
            return null;
        }
        HotspotResult existing = hotspotResultRepository.findByTradeDate(tradeDate);
        if (existing != null) {
            result.setId(existing.getId());
        }
        result.setGeneratedAt(LocalDateTime.now());
        return hotspotResultRepository.save(result);
    }

    public HotspotResult build(String tradeDate) {
        ScreeningScoreRecord snapshot = screeningScoreRecordRepository.findByTradeDate(tradeDate);
        if (snapshot == null || snapshot.getStocks() == null || snapshot.getStocks().isEmpty()) {
            log.warn("当日 {} 无选股快照，跳过热点识别", tradeDate);
            return null;
        }
        Map<String, Integer> qualityBySymbol = new HashMap<>();
        Map<String, String> nameBySymbol = new HashMap<>();
        for (StockFactor f : snapshot.getStocks()) {
            qualityBySymbol.put(f.getSymbol(), f.getQuality());
            nameBySymbol.put(f.getSymbol(), f.getName());
        }

        List<IndexConstituents> pool = indexConstituentsRepository.findAll();
        Map<String, MemberStats> statsBySymbol = new LinkedHashMap<>();
        for (IndexConstituents c : pool) {
            String symbol = c.getSymbol();
            if (symbol == null || statsBySymbol.containsKey(symbol)) {
                continue;
            }
            List<StockDailyKline> klines =
                    stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc(symbol);
            // 降序：get(0) 最新。5 日窗口需要 6 个收盘点
            if (klines == null || klines.size() < FIVE_DAY_WINDOW + 1) {
                continue;
            }
            BigDecimal latest = klines.get(0).getClose();
            BigDecimal prev = klines.get(1).getClose();
            BigDecimal fiveAgo = klines.get(FIVE_DAY_WINDOW).getClose();
            if (latest == null || prev == null || fiveAgo == null
                    || prev.compareTo(ZERO) <= 0 || fiveAgo.compareTo(ZERO) <= 0) {
                continue;
            }
            MemberStats m = new MemberStats();
            m.symbol = symbol;
            m.name = nameBySymbol.getOrDefault(symbol, symbol);
            m.quality = qualityBySymbol.get(symbol);
            m.dailyChange = latest.subtract(prev).divide(prev, 6, RoundingMode.HALF_UP);
            m.fiveDayChange = latest.subtract(fiveAgo).divide(fiveAgo, 6, RoundingMode.HALF_UP);
            m.amount = klines.get(0).getAmount() == null ? ZERO : klines.get(0).getAmount();
            BigDecimal amountSum = ZERO;
            for (int i = 0; i < FIVE_DAY_WINDOW; i++) {
                amountSum = amountSum.add(klines.get(i).getAmount() == null ? ZERO : klines.get(i).getAmount());
            }
            m.avgAmount5 = amountSum.divide(BigDecimal.valueOf(FIVE_DAY_WINDOW), 6, RoundingMode.HALF_UP);
            m.limitUpDays5 = countLimitUpDays(klines);
            statsBySymbol.put(symbol, m);
        }

        BigDecimal poolDailyAmount = ZERO;
        BigDecimal poolFiveDayAmount = ZERO;
        for (MemberStats m : statsBySymbol.values()) {
            poolDailyAmount = poolDailyAmount.add(m.amount);
            poolFiveDayAmount = poolFiveDayAmount.add(m.avgAmount5);
        }

        Map<String, DirectionAgg> byIndustry = new LinkedHashMap<>();
        Map<String, DirectionAgg> byConcept = new LinkedHashMap<>();
        for (MemberStats m : statsBySymbol.values()) {
            StockIndustryConcept ic = stockIndustryConceptRepository.findBySymbol(m.symbol);
            if (ic == null) {
                continue;
            }
            if (ic.getIndustry() != null && !ic.getIndustry().isBlank()) {
                byIndustry.computeIfAbsent(ic.getIndustry(), k -> new DirectionAgg()).members.add(m);
            }
            if (ic.getConcepts() != null) {
                for (String concept : ic.getConcepts()) {
                    if (concept != null && !concept.isBlank()) {
                        byConcept.computeIfAbsent(concept, k -> new DirectionAgg()).members.add(m);
                    }
                }
            }
        }

        byIndustry.values().removeIf(a -> a.members.size() < MIN_DIRECTION_SIZE);
        byConcept.values().removeIf(a -> a.members.size() < MIN_DIRECTION_SIZE);

        HotspotResult result = new HotspotResult();
        result.setTradeDate(tradeDate);
        result.setIndustries(toDirections(byIndustry, poolDailyAmount, poolFiveDayAmount));
        result.setConcepts(toDirections(byConcept, poolDailyAmount, poolFiveDayAmount));
        log.info("热点识别完成: tradeDate={}, 行业 {} 个, 概念 {} 个",
                tradeDate, result.getIndustries().size(), result.getConcepts().size());
        return result;
    }

    private int countLimitUpDays(List<StockDailyKline> klines) {
        int days = 0;
        for (int i = 0; i < FIVE_DAY_WINDOW; i++) {
            BigDecimal close = klines.get(i).getClose();
            BigDecimal base = klines.get(i + 1).getClose();
            if (close != null && base != null && base.compareTo(ZERO) > 0) {
                BigDecimal change = close.subtract(base).divide(base, 6, RoundingMode.HALF_UP);
                if (change.compareTo(LIMIT_UP_THRESHOLD) >= 0) {
                    days++;
                }
            }
        }
        return days;
    }

    private List<HotspotDirection> toDirections(Map<String, DirectionAgg> aggs,
                                                 BigDecimal poolDailyAmount,
                                                 BigDecimal poolFiveDayAmount) {
        List<HotspotDirection> directions = new ArrayList<>();
        if (aggs.isEmpty()) {
            return directions;
        }
        // 第一遍：聚合原始指标 + 代表股（raw 与 aggList 按索引平行对应）
        List<BigDecimal> dailyChanges = new ArrayList<>();
        List<BigDecimal> dailyRatios = new ArrayList<>();
        List<BigDecimal> limitUps = new ArrayList<>();
        List<BigDecimal> fiveDayChanges = new ArrayList<>();
        List<BigDecimal> fiveDayRatios = new ArrayList<>();
        List<BigDecimal> fiveDayLimitUps = new ArrayList<>();
        List<HotspotDirection> raw = new ArrayList<>();
        List<DirectionAgg> aggList = new ArrayList<>();
        for (Map.Entry<String, DirectionAgg> entry : aggs.entrySet()) {
            DirectionAgg agg = entry.getValue();
            HotspotDirection d = new HotspotDirection();
            d.setName(entry.getKey());
            d.setStockCount(agg.members.size());
            d.setDailyAvgChange(agg.dailyAvgChange());
            d.setFiveDayAvgChange(agg.fiveDayAvgChange());
            BigDecimal dailySum = ZERO;
            BigDecimal fiveDaySum = ZERO;
            int limitUpCount = 0;
            int fiveDayLimitUpCount = 0;
            for (MemberStats m : agg.members) {
                dailySum = dailySum.add(m.amount);
                fiveDaySum = fiveDaySum.add(m.avgAmount5);
                if (m.dailyChange.compareTo(LIMIT_UP_THRESHOLD) >= 0) {
                    limitUpCount++;
                }
                if (m.limitUpDays5 > 0) {
                    fiveDayLimitUpCount++;
                }
            }
            d.setLimitUpCount(limitUpCount);
            d.setFiveDayLimitUpCount(fiveDayLimitUpCount);
            d.setDailyAmountRatio(poolDailyAmount.compareTo(ZERO) > 0
                    ? dailySum.divide(poolDailyAmount, 6, RoundingMode.HALF_UP) : ZERO);
            d.setFiveDayAmountRatio(poolFiveDayAmount.compareTo(ZERO) > 0
                    ? fiveDaySum.divide(poolFiveDayAmount, 6, RoundingMode.HALF_UP) : ZERO);
            d.setTopStocks(topRepresentatives(agg));
            raw.add(d);
            aggList.add(agg);
            dailyChanges.add(d.getDailyAvgChange());
            dailyRatios.add(d.getDailyAmountRatio());
            limitUps.add(BigDecimal.valueOf(limitUpCount));
            fiveDayChanges.add(d.getFiveDayAvgChange());
            fiveDayRatios.add(d.getFiveDayAmountRatio());
            fiveDayLimitUps.add(BigDecimal.valueOf(fiveDayLimitUpCount));
        }
        // 第二遍：跨方向百分位 → 热度得分（Task 6 在 setHotScore 之后追加炒作标注）
        for (int i = 0; i < raw.size(); i++) {
            HotspotDirection d = raw.get(i);
            int dailyChangePct = ScreeningService.percentileDesc(dailyChanges, d.getDailyAvgChange());
            int dailyRatioPct = ScreeningService.percentileDesc(dailyRatios, d.getDailyAmountRatio());
            int limitUpPct = ScreeningService.percentileDesc(limitUps, BigDecimal.valueOf(d.getLimitUpCount()));
            BigDecimal dailyHeat = CHANGE_WEIGHT.multiply(BigDecimal.valueOf(dailyChangePct))
                    .add(AMOUNT_WEIGHT.multiply(BigDecimal.valueOf(dailyRatioPct)))
                    .add(LIMIT_UP_WEIGHT.multiply(BigDecimal.valueOf(limitUpPct)));
            int fiveChangePct = ScreeningService.percentileDesc(fiveDayChanges, d.getFiveDayAvgChange());
            int fiveRatioPct = ScreeningService.percentileDesc(fiveDayRatios, d.getFiveDayAmountRatio());
            int fiveLimitUpPct = ScreeningService.percentileDesc(fiveDayLimitUps,
                    BigDecimal.valueOf(d.getFiveDayLimitUpCount()));
            BigDecimal fiveHeat = CHANGE_WEIGHT.multiply(BigDecimal.valueOf(fiveChangePct))
                    .add(AMOUNT_WEIGHT.multiply(BigDecimal.valueOf(fiveRatioPct)))
                    .add(LIMIT_UP_WEIGHT.multiply(BigDecimal.valueOf(fiveLimitUpPct)));
            d.setHotScore(FIVE_DAY_HEAT_WEIGHT.multiply(fiveHeat)
                    .add(DAILY_HEAT_WEIGHT.multiply(dailyHeat))
                    .setScale(0, RoundingMode.HALF_UP).intValueExact());
        }
        raw.sort(Comparator.comparing(HotspotDirection::getHotScore).reversed()
                .thenComparing(HotspotDirection::getName));
        return raw.size() > TOP_N ? new ArrayList<>(raw.subList(0, TOP_N)) : raw;
    }

    /** 代表股：方向内按 单日涨幅+5日涨幅 之和降序取 3 只。 */
    private List<HotspotStock> topRepresentatives(DirectionAgg agg) {
        List<MemberStats> sorted = new ArrayList<>(agg.members);
        sorted.sort(Comparator.comparing(MemberStats::heatContribution).reversed()
                .thenComparing(m -> m.symbol));
        List<HotspotStock> topStocks = new ArrayList<>();
        for (int i = 0; i < Math.min(REPRESENTATIVE_COUNT, sorted.size()); i++) {
            MemberStats m = sorted.get(i);
            HotspotStock hs = new HotspotStock();
            hs.setSymbol(m.symbol);
            hs.setName(m.name);
            hs.setDailyChange(m.dailyChange);
            hs.setFiveDayChange(m.fiveDayChange);
            topStocks.add(hs);
        }
        return topStocks;
    }

    public Result<HotspotResult> latest() {
        return Result.successIfNotNull(hotspotResultRepository.findTopByOrderByTradeDateDesc(), "暂无热点结果");
    }
}
```

- [x] **Step 5: 运行测试确认通过（Task 5）**

Run: `cd StockAI && ./mvnw test -Dtest=HotspotServiceTest,ScreenerPoTest`
Expected: PASS

- [ ] **Step 6: 运行全量测试（Task 5）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 7: Commit（Task 5）**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/HotspotStock.java \
        StockAI/src/main/java/com/xian/stockai/po/HotspotDirection.java \
        StockAI/src/main/java/com/xian/stockai/po/HotspotResult.java \
        StockAI/src/main/java/com/xian/stockai/dao/HotspotResultRepository.java \
        StockAI/src/main/java/com/xian/stockai/service/HotspotService.java \
        StockAI/src/test/java/com/xian/stockai/service/HotspotServiceTest.java \
        StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java
git commit -m "feat: 实现行业/概念热点聚合与热度得分识别"
```

---

### Task 6: 炒作风险标注（3.2）

**Files:**
- Modify: `StockAI/src/main/java/com/xian/stockai/service/HotspotService.java`（新增 `hypeLabel`/`hypeBasis`/`medianQuality`，并在 `build` 中调用）
- Test: `StockAI/src/test/java/com/xian/stockai/service/HotspotServiceTest.java`（扩展）

**Interfaces:**
- Consumes: Task 5 的 `HotspotService.build`/`DirectionAgg`/`MemberStats`、质量分（快照）。
- Produces: `HotspotService.HYPE_SUPPORTED = "SUPPORTED"` / `HYPE_NEUTRAL = "NEUTRAL"` / `HYPE_RISK = "HYPE_RISK"` 常量（Task 7 AI 报告与 Task 10 前端依赖）；`HotspotDirection.hypeLabel`/`hypeBasis` 填充。

**标注规则（设计文档）：** 方向内质量因子中位数（null 质量分排除；偶数个取中间两数平均）：
- 中位数 <40 且热度 ≥60 → `HYPE_RISK`（纯炒作风险高）
- 中位数 ≥60 → `SUPPORTED`（有基本面支撑）
- 其余（含无质量数据）→ `NEUTRAL`（中性）
- `hypeBasis` 输出依据文本："质量中位数 X，热度得分 Y"。

- [ ] **Step 1: 写失败的测试（Task 6）**

在 `HotspotServiceTest.java` 追加：

```java
    // ==== Task 6: 炒作风险标注 ====

    @Test
    void hypeLabelRules() {
        // 中位数 ≥60 → SUPPORTED
        assertEquals(HotspotService.HYPE_SUPPORTED, HotspotService.hypeLabel(List.of(50, 70, 90), 80));
        // 中位数 <40 且热度 ≥60 → HYPE_RISK
        assertEquals(HotspotService.HYPE_RISK, HotspotService.hypeLabel(List.of(30, 35, 38), 80));
        // 中位数 <40 但热度 <60 → NEUTRAL
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(30, 35, 38), 50));
        // 中位数 40~60 → NEUTRAL；恰为 40 → NEUTRAL（<40 不成立）
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(45, 55), 70));
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(40), 80));
        // 空数据 → NEUTRAL；恰为 60 → SUPPORTED（≥60 边界）
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(), 80));
        assertEquals(HotspotService.HYPE_SUPPORTED, HotspotService.hypeLabel(List.of(60), 80));
        // null 质量分排除：[30, null, 35] → 有效 [30,35] 中位数 32.5 → HYPE_RISK（热度 80）
        assertEquals(HotspotService.HYPE_RISK,
                HotspotService.hypeLabel(java.util.Arrays.asList(30, null, 35), 80));
    }

    @Test
    void medianQualityHandlesOddAndEven() {
        assertEquals(0, new BigDecimal("35").compareTo(HotspotService.medianQuality(List.of(30, 35, 38))));
        assertEquals(0, new BigDecimal("32.5").compareTo(HotspotService.medianQuality(List.of(30, 35))));
        assertNull(HotspotService.medianQuality(List.of()));
        assertNull(HotspotService.medianQuality(java.util.Arrays.asList((Integer) null)));
    }

    @Test
    void hypeBasisTextIncludesMedianAndHotScore() {
        String basis = HotspotService.buildHypeBasis(new BigDecimal("32.5"), 80);
        assertEquals("质量中位数 32.5，热度得分 80", basis);
    }

    @Test
    void buildAnnotatesDirections() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        // 白酒：质量 [50,70,90] 中位数 70 ≥60 → SUPPORTED
        HotspotDirection liquor = result.getIndustries().stream()
                .filter(d -> "白酒".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(HotspotService.HYPE_SUPPORTED, liquor.getHypeLabel());
        assertEquals("质量中位数 70，热度得分 0", liquor.getHypeBasis());
        // 医药：质量 [30,35,38] 中位数 35 <40 且热度 80 ≥60 → HYPE_RISK
        HotspotDirection medicine = result.getIndustries().stream()
                .filter(d -> "医药".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(HotspotService.HYPE_RISK, medicine.getHypeLabel());
        assertEquals("质量中位数 35，热度得分 80", medicine.getHypeBasis());
        // 概念"消费"（A,B,C）同白酒 → SUPPORTED
        assertEquals(HotspotService.HYPE_SUPPORTED, result.getConcepts().get(0).getHypeLabel());
    }
}
```

- [ ] **Step 2: 运行测试确认失败（Task 6）**

Run: `cd StockAI && ./mvnw test -Dtest=HotspotServiceTest`
Expected: COMPILATION ERROR（`hypeLabel`/`medianQuality` 不存在）

- [ ] **Step 3: 在 HotspotService 中实现标注（Task 6）**

在 `HotspotService.java` 追加（类常量区追加三个 hype 常量；方法追加在 `toDirections` 之后）：

```java
    public static final String HYPE_SUPPORTED = "SUPPORTED";
    public static final String HYPE_NEUTRAL = "NEUTRAL";
    public static final String HYPE_RISK = "HYPE_RISK";

    /**
     * 炒作风险标注：质量中位数 <40 且热度 ≥60 → HYPE_RISK；≥60 → SUPPORTED；其余 NEUTRAL。
     */
    static String hypeLabel(List<Integer> qualities, int hotScore) {
        BigDecimal median = medianQuality(qualities);
        if (median == null) {
            return HYPE_NEUTRAL;
        }
        if (median.compareTo(new BigDecimal("40")) < 0 && hotScore >= 60) {
            return HYPE_RISK;
        }
        if (median.compareTo(new BigDecimal("60")) >= 0) {
            return HYPE_SUPPORTED;
        }
        return HYPE_NEUTRAL;
    }

    /** 质量分中位数（null 排除；偶数个取中间两数平均；无有效值返回 null）。 */
    static BigDecimal medianQuality(List<Integer> qualities) {
        List<Integer> valid = qualities.stream().filter(Objects::nonNull).sorted().toList();
        if (valid.isEmpty()) {
            return null;
        }
        int mid = valid.size() / 2;
        if (valid.size() % 2 == 1) {
            return BigDecimal.valueOf(valid.get(mid));
        }
        return BigDecimal.valueOf(valid.get(mid - 1)).add(BigDecimal.valueOf(valid.get(mid)))
                .divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
    }

    static String buildHypeBasis(BigDecimal median, int hotScore) {
        return "质量中位数 " + median + "，热度得分 " + hotScore;
    }
```

- [ ] **Step 4: 在 toDirections 第二遍循环中接入标注（Task 6）**

修改 `HotspotService.toDirections` 的第二遍循环（Task 5 已建立 `raw`/`aggList` 平行列表），在 `d.setHotScore(...);` 之后、循环体结束前追加：

```java
            List<Integer> qualities = new ArrayList<>();
            for (MemberStats m : aggList.get(i).members) {
                qualities.add(m.quality);
            }
            BigDecimal median = medianQuality(qualities);
            d.setHypeLabel(hypeLabel(qualities, d.getHotScore()));
            d.setHypeBasis(median == null
                    ? "无质量分数据，热度得分 " + d.getHotScore()
                    : buildHypeBasis(median, d.getHotScore()));
```

（循环变量 `i` 与 `aggList.get(i)` 给出该方向对应的 `DirectionAgg`，成员质量分从中提取。）

- [x] **Step 5: 运行测试确认通过（Task 6）**

Run: `cd StockAI && ./mvnw test -Dtest=HotspotServiceTest`
Expected: PASS

- [ ] **Step 6: 运行全量测试（Task 6）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 7: Commit（Task 6）**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/HotspotService.java \
        StockAI/src/test/java/com/xian/stockai/service/HotspotServiceTest.java
git commit -m "feat: 实现热点炒作风险三档标注与依据输出"
```

---

### Task 7: AI 综合报告与降级（4.1）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/ScreeningResult.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/ScreeningResultRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/service/ScreeningReportService.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/ScreeningReportServiceTest.java`、`StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java`（扩展）

**Interfaces:**
- Consumes: `ChatClient`（`CommonConfiguration` 已有 Bean）、`BoardEntry`（Task 3）、`HotspotDirection`/`HotspotResult`（Task 5/6）、`StockFactor`（Task 2）、`HotspotService.HYPE_*` 常量。
- Produces:
  - `ScreeningReportService.attachReport(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) -> void`（写入 `aiReport` + `aiReportStatus`；Task 8 编排调用）
  - `ScreeningReportService.STATUS_SUCCESS = "success"` / `STATUS_FALLBACK = "fallback"`
  - `ScreeningResult` 字段：tradeDate(unique)/generatedAt/valueBoard/growthBoard/balancedBoard/aiReport/aiReportStatus
  - `ScreeningResultRepository.findTopByOrderByTradeDateDesc()`、`findByTradeDate(String)`、`findAllByOrderByTradeDateDesc(Pageable)`（Task 8 latest/history 用）

**报告要求（设计文档）：** prompt 注入三风格榜 Top 20 精简表、热点 Top 10 及炒作标注、评分统计（池规模 + 因子均分）；失败 catch → `buildFallbackReport()` 纯量化文本（含评分依据与炒作风险提示），`ai_report_status=fallback`；榜单与热点不受影响。

- [ ] **Step 1: 写失败的测试（Task 7）**

在 `ScreenerPoTest.java` 追加：

```java
    @Test
    void screeningResultMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = ScreeningResult.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("screening_results", doc.collection());
        assertEquals("trade_date", fieldName(ScreeningResult.class, "tradeDate"));
        assertEquals("generated_at", fieldName(ScreeningResult.class, "generatedAt"));
        assertEquals("value", fieldName(ScreeningResult.class, "valueBoard"));
        assertEquals("growth", fieldName(ScreeningResult.class, "growthBoard"));
        assertEquals("balanced", fieldName(ScreeningResult.class, "balancedBoard"));
        assertEquals("ai_report", fieldName(ScreeningResult.class, "aiReport"));
        assertEquals("ai_report_status", fieldName(ScreeningResult.class, "aiReportStatus"));
        Indexed indexed = ScreeningResult.class.getDeclaredField("tradeDate")
                .getAnnotation(Indexed.class);
        assertNotNull(indexed);
        assertTrue(indexed.unique());
    }
```

创建 `StockAI/src/test/java/com/xian/stockai/service/ScreeningReportServiceTest.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.HotspotStock;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningReportServiceTest {

    @Mock
    private ChatClient chatClient;

    private ScreeningReportService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningReportService(chatClient);
    }

    private BoardEntry entry(String symbol, String name, String totalScore) {
        BoardEntry e = new BoardEntry();
        e.setSymbol(symbol);
        e.setName(name);
        e.setTotalScore(new BigDecimal(totalScore));
        e.setValuation(80);
        e.setGrowth(75);
        e.setQuality(90);
        e.setMomentum(60);
        e.setLongTermRating(ScreeningService.RATING_HIGH);
        e.setReason("ROE 池内前 15%");
        return e;
    }

    private ScreeningResult resultWithBoards() {
        ScreeningResult result = new ScreeningResult();
        result.setTradeDate("2026-09-01");
        result.setBalancedBoard(List.of(entry("600519", "贵州茅台", "95.50")));
        result.setValueBoard(List.of(entry("601318", "中国平安", "88.20")));
        result.setGrowthBoard(List.of(entry("300750", "宁德时代", "91.30")));
        return result;
    }

    private HotspotResult hotspotWithHypeRisk() {
        HotspotDirection medicine = new HotspotDirection();
        medicine.setName("医药");
        medicine.setHotScore(80);
        medicine.setHypeLabel(HotspotService.HYPE_RISK);
        medicine.setHypeBasis("质量中位数 35，热度得分 80");
        medicine.setStockCount(3);
        medicine.setLimitUpCount(1);
        medicine.setDailyAvgChange(new BigDecimal("0.086667"));
        medicine.setFiveDayAvgChange(new BigDecimal("0.086667"));
        medicine.setTopStocks(List.of());
        HotspotResult hotspot = new HotspotResult();
        hotspot.setTradeDate("2026-09-01");
        hotspot.setIndustries(List.of(medicine));
        hotspot.setConcepts(List.of());
        return hotspot;
    }

    private List<StockFactor> factors() {
        StockFactor f = new StockFactor();
        f.setSymbol("600519");
        f.setName("贵州茅台");
        f.setValuation(80);
        f.setGrowth(75);
        f.setQuality(90);
        f.setMomentum(60);
        f.setDataIncomplete(false);
        return List.of(f);
    }

    private void mockChatSuccess(String content) {
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(content);
    }

    @Test
    void attachReportUsesAiContentOnSuccess() {
        mockChatSuccess("AI 综合报告内容");
        ScreeningResult result = resultWithBoards();
        service.attachReport(result, hotspotWithHypeRisk(), factors());
        assertEquals("AI 综合报告内容", result.getAiReport());
        assertEquals(ScreeningReportService.STATUS_SUCCESS, result.getAiReportStatus());
    }

    @Test
    void attachReportFallsBackWhenAiFails() {
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("AI down"));

        ScreeningResult result = resultWithBoards();
        service.attachReport(result, hotspotWithHypeRisk(), factors());

        assertEquals(ScreeningReportService.STATUS_FALLBACK, result.getAiReportStatus());
        // 降级文本必须包含评分依据（分数）与炒作风险提示（标注）
        assertTrue(result.getAiReport().contains("[量化结论]"));
        assertTrue(result.getAiReport().contains("95.50"));
        assertTrue(result.getAiReport().contains("纯炒作风险高"));
    }

    @Test
    void promptContainsBoardsAndHotspotHype() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(captor.capture())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("ok");

        service.attachReport(resultWithBoards(), hotspotWithHypeRisk(), factors());

        String prompt = captor.getValue();
        assertTrue(prompt.contains("贵州茅台"));
        assertTrue(prompt.contains("纯炒作风险高"));
        assertTrue(prompt.contains("优质池共 1 只"));
    }

    @Test
    void fallbackHandlesEmptyInputs() {
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("AI down"));

        ScreeningResult empty = new ScreeningResult();
        service.attachReport(empty, null, List.of());
        assertEquals(ScreeningReportService.STATUS_FALLBACK, empty.getAiReportStatus());
        assertTrue(empty.getAiReport().contains("暂无"));
        assertTrue(empty.getAiReport().contains("[量化结论]"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败（Task 7）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningReportServiceTest,ScreenerPoTest`
Expected: COMPILATION ERROR（`ScreeningResult` 等不存在）

- [ ] **Step 3: 创建 ScreeningResult 与 Repository（Task 7）**

创建 `StockAI/src/main/java/com/xian/stockai/po/ScreeningResult.java`：

```java
package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author xian
 * @description 选股榜单与 AI 报告（每日一文档，trade_date 唯一）
 */
@Document(collection = "screening_results")
public class ScreeningResult {
    @Id
    private String id;

    @Field(name = "trade_date")
    @Indexed(unique = true)
    private String tradeDate;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    @Field(name = "value")
    private List<BoardEntry> valueBoard;

    @Field(name = "growth")
    private List<BoardEntry> growthBoard;

    @Field(name = "balanced")
    private List<BoardEntry> balancedBoard;

    @Field(name = "ai_report")
    private String aiReport;

    @Field(name = "ai_report_status")
    private String aiReportStatus;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<BoardEntry> getValueBoard() { return valueBoard; }
    public void setValueBoard(List<BoardEntry> valueBoard) { this.valueBoard = valueBoard; }
    public List<BoardEntry> getGrowthBoard() { return growthBoard; }
    public void setGrowthBoard(List<BoardEntry> growthBoard) { this.growthBoard = growthBoard; }
    public List<BoardEntry> getBalancedBoard() { return balancedBoard; }
    public void setBalancedBoard(List<BoardEntry> balancedBoard) { this.balancedBoard = balancedBoard; }
    public String getAiReport() { return aiReport; }
    public void setAiReport(String aiReport) { this.aiReport = aiReport; }
    public String getAiReportStatus() { return aiReportStatus; }
    public void setAiReportStatus(String aiReportStatus) { this.aiReportStatus = aiReportStatus; }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/dao/ScreeningResultRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.ScreeningResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ScreeningResultRepository extends MongoRepository<ScreeningResult, String> {
    ScreeningResult findTopByOrderByTradeDateDesc();

    ScreeningResult findByTradeDate(String tradeDate);

    List<ScreeningResult> findAllByOrderByTradeDateDesc(Pageable pageable);
}
```

- [ ] **Step 4: 创建 ScreeningReportService（Task 7）**

创建 `StockAI/src/main/java/com/xian/stockai/service/ScreeningReportService.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScreeningReportService {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FALLBACK = "fallback";

    private final ChatClient chatClient;

    public ScreeningReportService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 生成 AI 综合报告并写入 result（失败降级纯量化文本，不影响榜单与热点）。
     */
    public void attachReport(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) {
        try {
            result.setAiReport(generateReport(result, hotspot, factors));
            result.setAiReportStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            log.error("AI 综合报告生成失败，降级为量化文本: {}", e.getMessage());
            result.setAiReport(buildFallbackReport(result, hotspot, factors));
            result.setAiReportStatus(STATUS_FALLBACK);
        }
    }

    String generateReport(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) {
        String prompt = buildPrompt(result, hotspot, factors);
        return chatClient.prompt().user(prompt).call().content();
    }

    String buildPrompt(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是选股助手。根据以下量化选股与热点识别结果，用中文输出一份不超过300字的选股综合报告，")
                .append("必须包含「市场风格」「重点方向」「风险提示」三部分，风险提示中必须包含对纯炒作风险方向的提醒。\n");
        appendBoard(sb, "均衡型", result.getBalancedBoard());
        appendBoard(sb, "价值型", result.getValueBoard());
        appendBoard(sb, "成长型", result.getGrowthBoard());
        if (hotspot != null) {
            appendDirections(sb, "热点行业", hotspot.getIndustries());
            appendDirections(sb, "热点概念", hotspot.getConcepts());
        }
        int poolSize = factors == null ? 0 : factors.size();
        sb.append("【统计】优质池共 ").append(poolSize).append(" 只；因子均分：")
                .append("估值 ").append(avgFactor(factors, StockFactor::getValuation))
                .append("、成长 ").append(avgFactor(factors, StockFactor::getGrowth))
                .append("、质量 ").append(avgFactor(factors, StockFactor::getQuality))
                .append("、动量 ").append(avgFactor(factors, StockFactor::getMomentum));
        return sb.toString();
    }

    private void appendBoard(StringBuilder sb, String label, List<BoardEntry> board) {
        sb.append("【").append(label).append("Top20】");
        if (board == null || board.isEmpty()) {
            sb.append("暂无\n");
            return;
        }
        for (BoardEntry e : board) {
            sb.append(e.getName()).append("(").append(e.getSymbol())
                    .append(",综合").append(e.getTotalScore()).append(");");
        }
        sb.append("\n");
    }

    private void appendDirections(StringBuilder sb, String label, List<HotspotDirection> directions) {
        sb.append("【").append(label).append("Top10】");
        if (directions == null || directions.isEmpty()) {
            sb.append("暂无\n");
            return;
        }
        for (HotspotDirection d : directions) {
            sb.append(d.getName()).append("(热度").append(d.getHotScore())
                    .append(",").append(hypeText(d.getHypeLabel())).append(");");
        }
        sb.append("\n");
    }

    private String avgFactor(List<StockFactor> factors, Function<StockFactor, Integer> getter) {
        if (factors == null || factors.isEmpty()) {
            return "无数据";
        }
        List<Integer> values = factors.stream().map(getter).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return "无数据";
        }
        double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 纯量化降级文本（包含评分依据与炒作风险提示）。
     */
    public String buildFallbackReport(ScreeningResult result, HotspotResult hotspot,
                                      List<StockFactor> factors) {
        StringBuilder sb = new StringBuilder("[量化结论] ");
        sb.append("均衡型前三：").append(topThree(result.getBalancedBoard()));
        sb.append("；价值型前三：").append(topThree(result.getValueBoard()));
        sb.append("；成长型前三：").append(topThree(result.getGrowthBoard()));
        if (hotspot != null) {
            sb.append("。热点行业：").append(directionSummary(hotspot.getIndustries()));
            sb.append("；热点概念：").append(directionSummary(hotspot.getConcepts()));
        }
        int poolSize = factors == null ? 0 : factors.size();
        sb.append("。优质池共 ").append(poolSize).append(" 只，AI 不可用，以上为纯量化结论。");
        return sb.toString();
    }

    private String topThree(List<BoardEntry> board) {
        if (board == null || board.isEmpty()) {
            return "暂无";
        }
        return board.stream().limit(3)
                .map(e -> e.getName() + "(" + e.getTotalScore() + "分)")
                .collect(Collectors.joining("、"));
    }

    private String directionSummary(List<HotspotDirection> directions) {
        if (directions == null || directions.isEmpty()) {
            return "暂无";
        }
        return directions.stream().limit(3)
                .map(d -> d.getName() + "(热度" + d.getHotScore() + "，" + hypeText(d.getHypeLabel()) + ")")
                .collect(Collectors.joining("、"));
    }

    private String hypeText(String label) {
        if (HotspotService.HYPE_RISK.equals(label)) {
            return "纯炒作风险高";
        }
        if (HotspotService.HYPE_SUPPORTED.equals(label)) {
            return "有基本面支撑";
        }
        return "中性";
    }
}
```

- [x] **Step 5: 运行测试确认通过（Task 7）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningReportServiceTest,ScreenerPoTest`
Expected: PASS

- [ ] **Step 6: 运行全量测试（Task 7）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS

- [x] **Step 7: Commit（Task 7）**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/ScreeningResult.java \
        StockAI/src/main/java/com/xian/stockai/dao/ScreeningResultRepository.java \
        StockAI/src/main/java/com/xian/stockai/service/ScreeningReportService.java \
        StockAI/src/test/java/com/xian/stockai/service/ScreeningReportServiceTest.java \
        StockAI/src/test/java/com/xian/stockai/po/ScreenerPoTest.java
git commit -m "feat: 实现选股 AI 综合报告与失败降级量化文本"
```

---

### Task 8: 定时任务编排与 REST API（4.2）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/task/ScreeningTask.java`
- Create: `StockAI/src/main/java/com/xian/stockai/controller/ScreeningController.java`
- Create: `StockAI/src/main/java/com/xian/stockai/controller/HotspotController.java`
- Create: `StockAI/src/main/java/com/xian/stockai/vo/ScreeningHistoryRequest.java`
- Modify: `StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java`（新增 `latest()`/`history()`）
- Modify: `StockAI/src/main/resources/application.yml`（`screening.cron`）
- Test: `StockAI/src/test/java/com/xian/stockai/task/ScreeningTaskTest.java`、`StockAI/src/test/java/com/xian/stockai/controller/ScreeningControllerTest.java`、`StockAI/src/test/java/com/xian/stockai/controller/HotspotControllerTest.java`、`StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java`（扩展 latest/history）

**Interfaces:**
- Consumes: `ScreeningService.calculateAndSaveSnapshot`/`buildBoard`（Task 3/4）、`HotspotService.buildAndSave`/`latest`（Task 5）、`ScreeningReportService.attachReport`（Task 7）、`ScreeningResultRepository`、`StockDailyKlineRepository.existsByTradeDate`。
- Produces: `ScreeningTask.runScreening() -> Result<ScreeningResult>`（cron 入口与 `/screening/run` 复用）；REST 接口 `POST /screening/latest`、`POST /screening/history`（请求体 page/size）、`POST /screening/run`、`POST /hotspot/latest`（前端 Task 9/10 消费）。

**编排规则（设计文档）：** cron `${screening.cron:0 0 18 * * MON-FRI}`；当日 `stock_daily_kline` 无数据则记日志跳过；顺序：因子快照 → 榜单 → 热点 → AI 报告 → 入库；任一步骤异常记日志，不阻断已完成步骤产物入库（因子失败则整体终止）。

- [ ] **Step 1: 写失败的测试（Task 8）**

创建 `StockAI/src/test/java/com/xian/stockai/task/ScreeningTaskTest.java`：

```java
package com.xian.stockai.task;

import com.xian.stockai.dao.ScreeningResultRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.service.ScreeningReportService;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningTaskTest {

    @Mock
    private ScreeningService screeningService;
    @Mock
    private HotspotService hotspotService;
    @Mock
    private ScreeningReportService screeningReportService;
    @Mock
    private ScreeningResultRepository screeningResultRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;

    private ScreeningTask task;

    @BeforeEach
    void setUp() {
        task = new ScreeningTask(screeningService, hotspotService, screeningReportService,
                screeningResultRepository, stockDailyKlineRepository);
    }

    private void mockHappyPath() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(true);
        List<StockFactor> factors = List.of(new StockFactor());
        when(screeningService.calculateAndSaveSnapshot(anyString())).thenReturn(factors);
        when(screeningService.buildBoard(eq("value"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("growth"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("balanced"), any())).thenReturn(List.of());
    }

    @Test
    void runDailyUsesScreeningCron() throws Exception {
        Method method = ScreeningTask.class.getMethod("runDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals("${screening.cron:0 0 18 * * MON-FRI}", scheduled.cron());
    }

    @Test
    void runScreeningSkipsWhenNoKlineToday() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(false);
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
        verifyNoInteractions(screeningService);
        verifyNoInteractions(hotspotService);
        verifyNoInteractions(screeningReportService);
        verify(screeningResultRepository, never()).save(any());
    }

    @Test
    void runScreeningOrchestratesStepsInOrder() {
        mockHappyPath();
        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        InOrder inOrder = inOrder(screeningService, hotspotService, screeningReportService,
                screeningResultRepository);
        inOrder.verify(screeningService).calculateAndSaveSnapshot(anyString());
        inOrder.verify(screeningService).buildBoard(eq("value"), any());
        inOrder.verify(screeningService).buildBoard(eq("growth"), any());
        inOrder.verify(screeningService).buildBoard(eq("balanced"), any());
        inOrder.verify(hotspotService).buildAndSave(anyString());
        inOrder.verify(screeningReportService).attachReport(any(), any(), any());
        inOrder.verify(screeningResultRepository).save(any(ScreeningResult.class));
    }

    @Test
    void runScreeningFailsFastWhenSnapshotFails() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(true);
        when(screeningService.calculateAndSaveSnapshot(anyString()))
                .thenThrow(new RuntimeException("db down"));
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
        verifyNoInteractions(hotspotService);
        verify(screeningResultRepository, never()).save(any());
    }

    @Test
    void runScreeningContinuesWhenBoardFails() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(true);
        when(screeningService.calculateAndSaveSnapshot(anyString()))
                .thenReturn(List.of(new StockFactor()));
        when(screeningService.buildBoard(eq("value"), any()))
                .thenThrow(new RuntimeException("board boom"));
        when(screeningService.buildBoard(eq("growth"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("balanced"), any())).thenReturn(List.of());

        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        verify(hotspotService).buildAndSave(anyString());
        verify(screeningReportService).attachReport(any(), any(), any());
        verify(screeningResultRepository).save(any(ScreeningResult.class));
    }

    @Test
    void runScreeningContinuesWhenHotspotFails() {
        mockHappyPath();
        when(hotspotService.buildAndSave(anyString()))
                .thenThrow(new RuntimeException("hotspot boom"));
        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        verify(screeningReportService).attachReport(any(), any(), any());
        verify(screeningResultRepository).save(any(ScreeningResult.class));
    }
}
```

创建 `StockAI/src/test/java/com/xian/stockai/controller/ScreeningControllerTest.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.task.ScreeningTask;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScreeningControllerTest {

    @Mock
    private ScreeningService screeningService;
    @Mock
    private ScreeningTask screeningTask;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScreeningController(screeningService, screeningTask))
                .build();
    }

    @Test
    void latestReturnsServiceResult() throws Exception {
        ScreeningResult record = new ScreeningResult();
        record.setTradeDate("2026-09-01");
        when(screeningService.latest()).thenReturn(Result.success(record));

        mockMvc.perform(post("/screening/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-09-01"));

        verify(screeningService).latest();
    }

    @Test
    void latestFailsWhenNoData() throws Exception {
        when(screeningService.latest()).thenReturn(Result.fail("暂无选股结果"));
        mockMvc.perform(post("/screening/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(1))
                .andExpect(jsonPath("$.message").value("暂无选股结果"));
    }

    @Test
    void historyReadsPageAndSizeFromBody() throws Exception {
        when(screeningService.history(1, 5)).thenReturn(Result.success(List.of()));
        mockMvc.perform(post("/screening/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":1,\"size\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));
        verify(screeningService).history(1, 5);
    }

    @Test
    void historyUsesDefaultsWhenBodyMissing() throws Exception {
        when(screeningService.history(anyInt(), anyInt())).thenReturn(Result.success(List.of()));
        mockMvc.perform(post("/screening/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));
        verify(screeningService).history(1, 10);
    }

    @Test
    void runDelegatesToTask() throws Exception {
        ScreeningResult record = new ScreeningResult();
        record.setTradeDate("2026-09-01");
        when(screeningTask.runScreening()).thenReturn(Result.success(record));
        mockMvc.perform(post("/screening/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-09-01"));
        verify(screeningTask).runScreening();
    }
}
```

创建 `StockAI/src/test/java/com/xian/stockai/controller/HotspotControllerTest.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HotspotControllerTest {

    @Mock
    private HotspotService hotspotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HotspotController(hotspotService)).build();
    }

    @Test
    void latestReturnsServiceResult() throws Exception {
        HotspotResult record = new HotspotResult();
        record.setTradeDate("2026-09-01");
        when(hotspotService.latest()).thenReturn(Result.success(record));

        mockMvc.perform(post("/hotspot/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-09-01"));

        verify(hotspotService).latest();
    }

    @Test
    void latestFailsWhenNoData() throws Exception {
        when(hotspotService.latest()).thenReturn(Result.fail("暂无热点结果"));
        mockMvc.perform(post("/hotspot/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(1))
                .andExpect(jsonPath("$.message").value("暂无热点结果"));
    }
}
```

在 `ScreeningServiceTest.java` 追加（import 补 `com.xian.stockai.dao.ScreeningResultRepository`、`com.xian.stockai.po.ScreeningResult`、`org.springframework.data.domain.PageRequest`、`static org.mockito.Mockito.verify`；新增 `@Mock private ScreeningResultRepository screeningResultRepository;`，setUp 构造器再追加该 mock）：

```java
    // ==== Task 8: latest / history ====

    @Test
    void latestFailsWhenNoData() {
        when(screeningResultRepository.findTopByOrderByTradeDateDesc()).thenReturn(null);
        Result<ScreeningResult> result = service.latest();
        assertFalse(result.checkSuccess());
        assertEquals("暂无选股结果", result.getMessage());
    }

    @Test
    void historyUsesPageAndSize() {
        service.history(2, 5);
        verify(screeningResultRepository).findAllByOrderByTradeDateDesc(PageRequest.of(1, 5));
    }
```

setUp 最终形态：

```java
    @BeforeEach
    void setUp() {
        service = new ScreeningService(indexConstituentsRepository, stockFundamentalsRepository,
                stockDailyKlineRepository, stockBasicInfoRepository, new ScreeningProperties(),
                screeningScoreRecordRepository, screeningResultRepository);
    }
```

（import 补 `com.xian.stockai.vo.Result`。）

- [ ] **Step 2: 运行测试确认失败（Task 8）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningTaskTest,ScreeningControllerTest,HotspotControllerTest,ScreeningServiceTest`
Expected: COMPILATION ERROR（`ScreeningTask` 等不存在）

- [ ] **Step 3: 创建 ScreeningTask（Task 8）**

创建 `StockAI/src/main/java/com/xian/stockai/task/ScreeningTask.java`：

```java
package com.xian.stockai.task;

import com.xian.stockai.dao.ScreeningResultRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.service.ScreeningReportService;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScreeningTask {
    private final ScreeningService screeningService;
    private final HotspotService hotspotService;
    private final ScreeningReportService screeningReportService;
    private final ScreeningResultRepository screeningResultRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;

    @Scheduled(cron = "${screening.cron:0 0 18 * * MON-FRI}")
    public void runDaily() {
        log.info("开始选股引擎每日更新");
        runScreening();
        log.info("选股引擎每日更新结束");
    }

    /**
     * 全流程编排：因子快照 → 榜单 → 热点 → AI 报告 → 入库。
     * 任一步骤异常记日志，不阻断已完成步骤产物入库；因子失败则终止。
     */
    public Result<ScreeningResult> runScreening() {
        String tradeDate = LocalDate.now().toString();
        if (!stockDailyKlineRepository.existsByTradeDate(tradeDate)) {
            log.warn("当日 {} 无K线数据，跳过选股任务", tradeDate);
            return Result.fail("当日K线数据未就绪");
        }
        List<StockFactor> factors;
        try {
            factors = screeningService.calculateAndSaveSnapshot(tradeDate);
        } catch (Exception e) {
            log.error("选股因子快照失败: {}", e.getMessage(), e);
            return Result.fail("选股因子计算失败");
        }

        ScreeningResult result = screeningResultRepository.findByTradeDate(tradeDate);
        if (result == null) {
            result = new ScreeningResult();
        }
        result.setTradeDate(tradeDate);
        result.setGeneratedAt(LocalDateTime.now());
        try {
            result.setValueBoard(screeningService.buildBoard(ScreeningService.STYLE_VALUE, factors));
            result.setGrowthBoard(screeningService.buildBoard(ScreeningService.STYLE_GROWTH, factors));
            result.setBalancedBoard(screeningService.buildBoard(ScreeningService.STYLE_BALANCED, factors));
        } catch (Exception e) {
            log.error("风格榜单生成失败: {}", e.getMessage(), e);
        }
        HotspotResult hotspot = null;
        try {
            hotspot = hotspotService.buildAndSave(tradeDate);
        } catch (Exception e) {
            log.error("热点识别失败: {}", e.getMessage(), e);
        }
        try {
            screeningReportService.attachReport(result, hotspot, factors);
        } catch (Exception e) {
            log.error("AI综合报告生成失败: {}", e.getMessage(), e);
        }
        screeningResultRepository.save(result);
        log.info("选股结果已入库: tradeDate={}", tradeDate);
        return Result.success(result);
    }
}
```

- [ ] **Step 4: 创建请求 VO 与两个 Controller（Task 8）**

创建 `StockAI/src/main/java/com/xian/stockai/vo/ScreeningHistoryRequest.java`：

```java
package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ScreeningHistoryRequest {
    private Integer page;
    private Integer size;
}
```

创建 `StockAI/src/main/java/com/xian/stockai/controller/ScreeningController.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.task.ScreeningTask;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.ScreeningHistoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/screening")
public class ScreeningController {
    private final ScreeningService screeningService;
    private final ScreeningTask screeningTask;

    @PostMapping("/latest")
    public Result<ScreeningResult> latest() {
        return screeningService.latest();
    }

    @PostMapping("/history")
    public Result<List<ScreeningResult>> history(@RequestBody(required = false) ScreeningHistoryRequest req) {
        int page = (req == null || req.getPage() == null || req.getPage() < 1) ? 1 : req.getPage();
        int size = (req == null || req.getSize() == null || req.getSize() < 1) ? 10 : Math.min(req.getSize(), 100);
        return screeningService.history(page, size);
    }

    @PostMapping("/run")
    public Result<ScreeningResult> run() {
        return screeningTask.runScreening();
    }
}
```

创建 `StockAI/src/main/java/com/xian/stockai/controller/HotspotController.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/hotspot")
public class HotspotController {
    private final HotspotService hotspotService;

    @PostMapping("/latest")
    public Result<HotspotResult> latest() {
        return hotspotService.latest();
    }
}
```

- [ ] **Step 5: ScreeningService 增加 latest/history（构造器追加 repository）（Task 8）**

`ScreeningService.java`：
1. import 增加 `com.xian.stockai.dao.ScreeningResultRepository`、`com.xian.stockai.po.ScreeningResult`、`com.xian.stockai.vo.Result`、`org.springframework.data.domain.PageRequest`、`org.springframework.data.domain.Pageable`。
2. 构造器追加 `ScreeningResultRepository screeningResultRepository`（放最后）并加字段。
3. 追加方法：

```java
    public Result<ScreeningResult> latest() {
        return Result.successIfNotNull(screeningResultRepository.findTopByOrderByTradeDateDesc(), "暂无选股结果");
    }

    public Result<List<ScreeningResult>> history(int page, int size) {
        return Result.success(screeningResultRepository
                .findAllByOrderByTradeDateDesc(PageRequest.of(page - 1, size)));
    }
```

- [ ] **Step 6: application.yml 增加 cron（Task 8）**

在 `screening:` 配置块的 `weights:` 之前追加一行：

```yaml
screening:
  cron: "0 0 18 * * MON-FRI"
  weights:
    # （weights 内容保持不变）
```

- [ ] **Step 7: 运行测试确认通过（Task 8）**

Run: `cd StockAI && ./mvnw test -Dtest=ScreeningTaskTest,ScreeningControllerTest,HotspotControllerTest,ScreeningServiceTest`
Expected: PASS

- [ ] **Step 8: 运行全量测试（Task 8）**

Run: `cd StockAI && ./mvnw test`
Expected: PASS（含全部既有测试）

- [ ] **Step 9: Commit（Task 8）**

```bash
git add StockAI/src/main/java/com/xian/stockai/task/ScreeningTask.java \
        StockAI/src/main/java/com/xian/stockai/controller/ScreeningController.java \
        StockAI/src/main/java/com/xian/stockai/controller/HotspotController.java \
        StockAI/src/main/java/com/xian/stockai/vo/ScreeningHistoryRequest.java \
        StockAI/src/main/java/com/xian/stockai/service/ScreeningService.java \
        StockAI/src/main/resources/application.yml \
        StockAI/src/test/java/com/xian/stockai/task/ScreeningTaskTest.java \
        StockAI/src/test/java/com/xian/stockai/controller/ScreeningControllerTest.java \
        StockAI/src/test/java/com/xian/stockai/controller/HotspotControllerTest.java \
        StockAI/src/test/java/com/xian/stockai/service/ScreeningServiceTest.java
git commit -m "feat: 实现选股定时任务编排与 REST API"
```

---

### Task 9: 前端选股结果展示页（5.1）

**Files:**
- Create: `StockAI-Console/src/components/StockScreener.vue`
- Modify: `StockAI-Console/src/router/index.js`（新增 `/screener` 路由）
- Modify: `StockAI-Console/src/App.vue`（左侧菜单新增「选股」）

**Interfaces:**
- Consumes: `POST /screening/latest` → `Result<ScreeningResult>`；响应字段（camelCase，Jackson 序列化 Java 属性名）：`tradeDate`、`generatedAt`、`valueBoard`/`growthBoard`/`balancedBoard`（数组元素：`symbol`、`name`、`totalScore`、`valuation`、`growth`、`quality`、`momentum`、`longTermRating`、`reason`）、`aiReport`、`aiReportStatus`。
- Produces: 页面 `/screener`（Task 10 在同一页面追加热点区块）。

**说明:** 参照 `MarketRegime.vue` 风格（script setup + axios 直连 `config.baseUrl` + `state === 0` 判断 + Element Plus）。无数据时显示 `el-empty`（`Result.fail` → state !== 0）。

- [ ] **Step 1: 创建 StockScreener.vue（榜单版）（Task 9）**

创建 `StockAI-Console/src/components/StockScreener.vue`：

```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../config/config'

const BASE_URL = config.baseUrl
const loading = ref(false)
const result = ref(null)

const RATING_LABEL = { HIGH: '高', MEDIUM: '中', LOW: '低', UNKNOWN: '未知' }
const RATING_TYPE = { HIGH: 'success', MEDIUM: 'warning', LOW: 'danger', UNKNOWN: 'info' }

const activeBoard = ref('balancedBoard')

const boardTabs = computed(() => {
  if (!result.value) return []
  return [
    { key: 'balancedBoard', label: '均衡型' },
    { key: 'valueBoard', label: '价值型' },
    { key: 'growthBoard', label: '成长型' }
  ].map(t => ({ ...t, list: result.value[t.key] || [] }))
})

const currentBoard = computed(() =>
  boardTabs.value.find(t => t.key === activeBoard.value) || null
)

const ratingText = (rating) => RATING_LABEL[rating] || rating
const ratingType = (rating) => RATING_TYPE[rating] || 'info'

async function fetchLatest() {
  loading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/screening/latest`)
    if (response.data && response.data.state === 0) {
      result.value = response.data.data
    } else {
      result.value = null
      ElMessage.info('暂无选股结果')
    }
  } catch (error) {
    console.error('获取选股结果失败:', error)
    ElMessage.error('获取选股结果失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(fetchLatest)
</script>

<template>
  <div class="screener-page" v-loading="loading">
    <el-card shadow="hover" class="screener-card">
      <template #header>
        <div class="card-header">
          <h2 class="card-title">选股</h2>
          <span v-if="result" class="card-date">{{ result.tradeDate }}</span>
        </div>
      </template>
      <template v-if="result">
        <div class="report-box">
          <div class="report-head">
            <span class="report-title">AI 综合报告</span>
            <el-tag v-if="result.aiReportStatus === 'fallback'" type="warning" size="small">
              AI 不可用，已降级量化文本
            </el-tag>
          </div>
          <div class="report-content">{{ result.aiReport }}</div>
        </div>
        <el-divider />
        <el-tabs v-model="activeBoard">
          <el-tab-pane
            v-for="tab in boardTabs"
            :key="tab.key"
            :label="tab.label"
            :name="tab.key"
          />
        </el-tabs>
        <el-table v-if="currentBoard" :data="currentBoard.list" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="股票" min-width="140">
            <template #default="scope">
              <span class="stock-name">{{ scope.row.name }}</span>
              <span class="stock-symbol">{{ scope.row.symbol }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="综合分" width="90" sortable />
          <el-table-column prop="valuation" label="估值" width="70" />
          <el-table-column prop="growth" label="成长" width="70" />
          <el-table-column prop="quality" label="质量" width="70" />
          <el-table-column prop="momentum" label="动量" width="70" />
          <el-table-column label="长期价值" width="90">
            <template #default="scope">
              <el-tag :type="ratingType(scope.row.longTermRating)" size="small">
                {{ ratingText(scope.row.longTermRating) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="入选理由" min-width="260" show-overflow-tooltip />
        </el-table>
      </template>
      <el-empty v-else description="暂无选股结果" />
    </el-card>
  </div>
</template>

<style scoped>
.screener-page {
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}
.card-date {
  color: #6b7280;
}
.report-box {
  background-color: #f8fafc;
  padding: 16px;
  border-radius: 8px;
}
.report-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.report-title {
  font-weight: 600;
  color: #1f2937;
}
.report-content {
  color: #374151;
  line-height: 1.6;
  white-space: pre-wrap;
}
.stock-name {
  font-weight: 600;
  color: #1f2937;
  margin-right: 8px;
}
.stock-symbol {
  color: #6b7280;
  font-size: 0.8125rem;
}
</style>
```

- [ ] **Step 2: 注册路由（Task 9）**

修改 `StockAI-Console/src/router/index.js`：

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import AssetConfig from '../components/AssetConfig/AssetConfig.vue'
import Dashboard from '../components/Dashboard.vue'
import Config from '../components/Config.vue'
import MarketRegime from '../components/MarketRegime.vue'
import StockScreener from '../components/StockScreener.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: '总览',
      component: AssetConfig
    },
    {
      path: '/stock',
      name: '股票',
      component: Dashboard
    },
    {
      path: '/config',
      name: '配置',
      component: Config
    },
    {
      path: '/regime',
      name: '温度计',
      component: MarketRegime
    },
    {
      path: '/screener',
      name: '选股',
      component: StockScreener
    }
  ]
})

export default router
```

- [ ] **Step 3: App.vue 菜单新增「选股」（Task 9）**

修改 `StockAI-Console/src/App.vue`：
1. import 行追加图标（`TrendCharts`）：

```javascript
import { Menu as MenuIcon, Document, Setting, Bell, User, TrendCharts } from '@element-plus/icons-vue'
```

2. 在「温度计」菜单项之后追加：

```html
          <el-menu-item index="/screener">
            <el-icon><TrendCharts /></el-icon>
            <span>选股</span>
          </el-menu-item>
```

- [ ] **Step 4: 构建验证（Task 9）**

Run: `cd StockAI-Console && npm run build`
Expected: 构建成功（无编译错误）

- [ ] **Step 5: 手动核验（可选，需后端运行）（Task 9）**

启动后端（`cd StockAI && ./mvnw spring-boot:run`），浏览器打开前端 dev 服务器，访问 `/screener`：无数据时显示"暂无选股结果"；调用 `POST /screening/run` 生成数据后刷新可见榜单与 AI 报告。

- [ ] **Step 6: Commit（Task 9）**

```bash
git add StockAI-Console/src/components/StockScreener.vue \
        StockAI-Console/src/router/index.js \
        StockAI-Console/src/App.vue
git commit -m "feat: 新增选股结果展示页与路由菜单"
```

---

### Task 10: 前端热点方向展示（5.2）

**Files:**
- Modify: `StockAI-Console/src/components/StockScreener.vue`（追加热点区块）

**Interfaces:**
- Consumes: `POST /hotspot/latest` → `Result<HotspotResult>`；响应字段：`tradeDate`、`generatedAt`、`industries`/`concepts`（数组元素：`name`、`hotScore`、`dailyAvgChange`、`dailyAmountRatio`、`limitUpCount`、`fiveDayAvgChange`、`fiveDayAmountRatio`、`fiveDayLimitUpCount`、`stockCount`、`hypeLabel`、`hypeBasis`、`topStocks[{symbol,name,dailyChange,fiveDayChange}]`）。
- Produces: 页面 `/screener` 完整版（榜单 + 热点 + 炒作风险标注）。

- [ ] **Step 1: StockScreener.vue 追加热点数据获取（Task 10）**

在 `<script setup>` 中追加（`HYPE_LABEL`/`HYPE_TYPE` 常量、`hotspot`/`hotspotLoading` 状态、`fetchHotspot` 函数、`onMounted` 追加调用）：

```javascript
const hotspotLoading = ref(false)
const hotspot = ref(null)

const HYPE_LABEL = { SUPPORTED: '有基本面支撑', NEUTRAL: '中性', HYPE_RISK: '纯炒作风险高' }
const HYPE_TYPE = { SUPPORTED: 'success', NEUTRAL: 'info', HYPE_RISK: 'danger' }

const hypeText = (label) => HYPE_LABEL[label] || '中性'
const hypeType = (label) => HYPE_TYPE[label] || 'info'
const pctText = (value) => value == null ? '-' : ((value * 100).toFixed(2) + '%')

async function fetchHotspot() {
  hotspotLoading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/hotspot/latest`)
    if (response.data && response.data.state === 0) {
      hotspot.value = response.data.data
    } else {
      hotspot.value = null
    }
  } catch (error) {
    console.error('获取热点结果失败:', error)
    hotspot.value = null
  } finally {
    hotspotLoading.value = false
  }
}
```

`onMounted` 改为：

```javascript
onMounted(() => {
  fetchLatest()
  fetchHotspot()
})
```

- [ ] **Step 2: 模板追加热点区块（Task 10）**

在 `</el-card>` 之后（`</template>` 之前）追加：

```html
    <el-card shadow="hover" class="hotspot-card" v-loading="hotspotLoading">
      <template #header>
        <div class="card-header">
          <h2 class="card-title">热点方向</h2>
          <span v-if="hotspot" class="card-date">{{ hotspot.tradeDate }}（涨停家数仅优质池口径）</span>
        </div>
      </template>
      <template v-if="hotspot">
        <h3 class="section-title">行业 Top 10</h3>
        <el-table :data="hotspot.industries || []" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="name" label="行业" min-width="110" />
          <el-table-column prop="hotScore" label="热度" width="80" sortable />
          <el-table-column prop="stockCount" label="股票数" width="80" />
          <el-table-column prop="limitUpCount" label="涨停数" width="80" />
          <el-table-column label="单日均涨幅" width="110">
            <template #default="scope">{{ pctText(scope.row.dailyAvgChange) }}</template>
          </el-table-column>
          <el-table-column label="5日均涨幅" width="110">
            <template #default="scope">{{ pctText(scope.row.fiveDayAvgChange) }}</template>
          </el-table-column>
          <el-table-column label="炒作风险" width="130">
            <template #default="scope">
              <el-tooltip :content="scope.row.hypeBasis" placement="top">
                <el-tag :type="hypeType(scope.row.hypeLabel)" size="small">
                  {{ hypeText(scope.row.hypeLabel) }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="代表股" min-width="200">
            <template #default="scope">
              <span v-for="s in (scope.row.topStocks || [])" :key="s.symbol" class="top-stock">
                {{ s.name }}({{ pctText(s.dailyChange) }})
              </span>
            </template>
          </el-table-column>
        </el-table>
        <h3 class="section-title">概念 Top 10</h3>
        <el-table :data="hotspot.concepts || []" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="name" label="概念" min-width="110" />
          <el-table-column prop="hotScore" label="热度" width="80" sortable />
          <el-table-column prop="stockCount" label="股票数" width="80" />
          <el-table-column prop="limitUpCount" label="涨停数" width="80" />
          <el-table-column label="单日均涨幅" width="110">
            <template #default="scope">{{ pctText(scope.row.dailyAvgChange) }}</template>
          </el-table-column>
          <el-table-column label="5日均涨幅" width="110">
            <template #default="scope">{{ pctText(scope.row.fiveDayAvgChange) }}</template>
          </el-table-column>
          <el-table-column label="炒作风险" width="130">
            <template #default="scope">
              <el-tooltip :content="scope.row.hypeBasis" placement="top">
                <el-tag :type="hypeType(scope.row.hypeLabel)" size="small">
                  {{ hypeText(scope.row.hypeLabel) }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="代表股" min-width="200">
            <template #default="scope">
              <span v-for="s in (scope.row.topStocks || [])" :key="s.symbol" class="top-stock">
                {{ s.name }}({{ pctText(s.dailyChange) }})
              </span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无热点结果" />
    </el-card>
```

- [ ] **Step 3: 样式追加（Task 10）**

在 `<style scoped>` 末尾追加：

```css
.hotspot-card {
  margin-top: 1.5rem;
}
.section-title {
  font-size: 1rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0.5rem 0;
}
.top-stock {
  margin-right: 8px;
  color: #4b5563;
  font-size: 0.8125rem;
  white-space: nowrap;
}
```

- [ ] **Step 4: 构建验证（Task 10）**

Run: `cd StockAI-Console && npm run build`
Expected: 构建成功

- [ ] **Step 5: 手动核验（可选，需后端运行）（Task 10）**

后端运行并执行过 `/screening/run` 后，访问 `/screener`：热点区块展示行业/概念 Top 10、热度排序、炒作风险标注（hover 显示依据）与代表股。

- [ ] **Step 6: Commit（Task 10）**

```bash
git add StockAI-Console/src/components/StockScreener.vue
git commit -m "feat: 选股页新增热点方向与炒作风险展示"
```

---

## 集成验证（全部任务完成后）

- [ ] `cd StockAI && ./mvnw test` 全绿（单元测试覆盖：百分位方向、PE≤0 得 0 分、因子缺失重归一化、缺失 ≥2 不入榜、三风格权重、评级阈值边界 65/45、热点聚合与 <3 过滤、炒作三档标注、AI 失败降级、编排顺序与异常隔离、REST 接口）。
- [ ] `cd StockAI-Console && npm run build` 通过。
- [ ] 手动端到端（需要本地 MongoDB 已有 etf-service 数据）：启动后端 → `POST /screening/run` → `POST /screening/latest` 与 `POST /hotspot/latest` 返回 state=0 → 前端 `/screener` 页面展示榜单、理由、AI 报告、热点与炒作标注。
