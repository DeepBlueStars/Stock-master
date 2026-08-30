---
change: market-regime
design-doc: docs/superpowers/specs/2026-08-30-market-regime-design.md
base-ref: b311f2205475f2002561d5f63d20c4c28231aba1
---

# 市场温度计（market-regime）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收盘后自动判定市场四态（牛/熊/震荡/风险）并给出大资金仓位建议，写库、AI 总结、飞书推送，前端温度计页展示当前状态与历史。

**Architecture:** 跨三端。etf-service（Python）通过 akshare 采集宽基指数 PE/PB 历史并计算历史分位，写入 `index_valuation` 集合；StockAI（Java）收盘后定时从 MongoDB 读 `index_daily_quotes`（均线/量能/波动率）与 `index_valuation`（估值分位），用确定性规则判定四态并映射仓位建议，AI 生成报告后写 `market_regime_records` 并飞书推送；StockAI-Console（Vue）新增温度计页展示当前状态与历史列表。三端通过 MongoDB 集合解耦。

**Tech Stack:** Python 3 + akshare + pymongo + APScheduler（etf-service）；Java 21 + Spring Boot 3.5 + Spring Data MongoDB + Spring AI 1.0.3（ChatClient）+ fastjson2（StockAI）；Vue 3 + Vite + Element Plus + axios（StockAI-Console）。

**Spec:** docs/superpowers/specs/2026-08-30-market-regime-design.md

## Global Constraints

- 集合命名与字段严格按设计文档：`index_valuation`（Python 写）：`index_code, trade_date, pe, pb, pe_percentile, pb_percentile, data_source, updated_at`；`market_regime_records`（Java 写）：`date, regime, position_advice, indicators, report, created_at`。
- 四态枚举字符串固定：`BULL`/`BEAR`/`SIDEWAYS`/`RISK`；仓位建议字符串固定：`AGGRESSIVE`/`NEUTRAL`/`DEFENSIVE`/`CASH`（四态与建议一一对应）。
- 判定阈值抽到配置（`application.yml` 的 `market-regime.thresholds.*`），默认：波动率高阈值 `0.30`、估值高位阈值 `0.80`、基准指数 `sh000300`。
- 定时任务 cron 沿用 monitor 模式：`0 30 15 * * ?`（工作日收盘后 15:30）。
- 复用现有能力：Spring AI `ChatClient`（qwen-max/deepseek 走 `spring.ai.openai` 配置）、`FeishuNotificationSender`（`@ConditionalOnProperty(prefix="feishu", name="enabled", havingValue="true")`）。
- 编码规范：UTF-8 / LF；Python 4 空格缩进；Java 2 空格缩进 + Lombok 手写 getter/setter 风格（与现有 PO 一致）；统一 `Result<T>` 包装 Java 返回值。
- 不扩展范围：不做盘中实时判定、不做个股择时、不做市场宽度指标。

---

### Task 1: etf-service — 指数估值 schema、Mongo 集合与采集服务（PE/PB + 历史分位）

**Files:**
- Create: `etf-service/app/services/valuation_service.py`
- Modify: `etf-service/app/normalize/schema.py`
- Modify: `etf-service/app/datasource/akshare_source.py`
- Modify: `etf-service/app/database/mongo.py`
- Test: `etf-service/tests/test_valuation_service.py`

**Interfaces:**
- Consumes: `etf-service/app/datasource/akshare_source.py` 的 `AkshareSource`（`self.ak`）、`etf-service/app/database/mongo.py` 的 `mongo_client`、`etf-service/app/normalize/schema.py` 的 `to_date`/`to_float`/`row_get`。
- Produces（后续任务依赖）:
  - `compute_percentile(history: List[float], value: float) -> float`（在 `schema.py`）。
  - `IndexValuation` dataclass（字段 `index_code, trade_date, pe, pb, pe_percentile, pb_percentile, data_source, updated_at`，含 `to_dict()`）。
  - `mongo_client.upsert_index_valuation(data: Dict[str, Any]) -> bool`。
  - `ValuationService.VALUATION_INDEX_NAMES: Dict[str, str]`（index_code → akshare 中文名，含 `sh000300/sh000905/sz399006/sh000001`）。
  - `valuation_service.fetch_index_valuation(index_code: str) -> int`、`valuation_service.update_all_index_valuation() -> int`。
  - `AkshareSource.fetch_index_valuation_history(index_name: str, indicator: str) -> Optional[List[dict]]`（返回 `[{"trade_date": "YYYY-MM-DD", "pe"|"pb": float}]`）。

- [x] **Step 1: 写失败测试（分位计算 + 采集写入）**

创建 `etf-service/tests/test_valuation_service.py`：

```python
from app.services.valuation_service import ValuationService
from app.normalize.schema import compute_percentile


def test_compute_percentile():
    hist = [10.0, 20.0, 15.0]
    assert compute_percentile(hist, 15.0) == round(1 / 3, 4)
    assert compute_percentile(hist, 10.0) == 0.0
    assert compute_percentile(hist, 20.0) == round(2 / 3, 4)
    assert compute_percentile([], 10.0) == 0.0


class _FakeAk:
    def fetch_index_valuation_history(self, index_name, indicator):
        if indicator == "pe":
            return [
                {"trade_date": "2024-01-02", "pe": 10.0},
                {"trade_date": "2024-01-03", "pe": 20.0},
                {"trade_date": "2024-01-04", "pe": 15.0},
            ]
        return [
            {"trade_date": "2024-01-02", "pb": 1.0},
            {"trade_date": "2024-01-03", "pb": 2.0},
            {"trade_date": "2024-01-04", "pb": 1.5},
        ]


class _FakeMongo:
    def __init__(self):
        self.written = []

    def upsert_index_valuation(self, data):
        self.written.append(data)
        return True


def test_fetch_index_valuation_writes_and_computes_percentile():
    svc = ValuationService(ak=_FakeAk(), mongo=_FakeMongo())
    n = svc.fetch_index_valuation("sh000300")
    assert n == 3
    by_date = {r["trade_date"]: r for r in svc.mongo.written}
    assert by_date["2024-01-04"]["pe_percentile"] == round(1 / 3, 4)
    assert by_date["2024-01-04"]["pb_percentile"] == round(1 / 3, 4)
    assert by_date["2024-01-02"]["index_code"] == "sh000300"
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service && python -m pytest tests/test_valuation_service.py -v`
Expected: FAIL（`ModuleNotFoundError: app.services.valuation_service` 或 `ImportError: compute_percentile`）

- [x] **Step 3: 在 schema.py 新增 IndexValuation dataclass 与 compute_percentile**

在 `etf-service/app/normalize/schema.py` 末尾追加：

```python
from typing import List  # 若顶部未导入 List 则补上


@dataclass
class IndexValuation:
    index_code: str
    trade_date: str
    pe: float
    pb: float
    pe_percentile: float
    pb_percentile: float
    data_source: str
    updated_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict:
        return asdict(self)


def compute_percentile(history: List[float], value: float) -> float:
    """value 在 history 中的历史分位（0.0~1.0，= 严格小于 value 的数量 / 总数）。"""
    if not history:
        return 0.0
    valid = [v for v in history if v is not None]
    if not valid:
        return 0.0
    less = sum(1 for v in valid if v < value)
    return round(less / len(valid), 4)
```

- [x] **Step 4: 在 mongo.py 新增 index_valuation 集合与 upsert 方法**

在 `etf-service/app/database/mongo.py` 的 `__init__` 中（`self.index_constituents` 之后）加：

```python
        self.index_valuation: Collection = self.db["index_valuation"]
```

在类中（`upsert_index_constituent` 之后）加：

```python
    def upsert_index_valuation(self, data: Dict[str, Any]) -> bool:
        """更新或插入指数估值（去重键 index_code + trade_date）。"""
        try:
            self.index_valuation.update_one(
                {"index_code": data["index_code"], "trade_date": data["trade_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新指数估值失败: {e}")
            return False
```

- [x] **Step 5: 在 akshare_source.py 新增指数估值历史采集方法**

在 `etf-service/app/datasource/akshare_source.py` 的 `AkshareSource` 类中（`fetch_industry_concept` 之后）加：

```python
    def fetch_index_valuation_history(self, index_name: str, indicator: str) -> Optional[List[dict]]:
        """拉取指数 PE/PB 历史序列（乐咕乐股），indicator 为 'pe' 或 'pb'。

        返回 [{trade_date, pe|pb}]。接口列名随 akshare 版本可能变化，
        使用 row_get 候选列名做防御映射；接口不可用时备选
        ak.index_value_hist_funddb(symbol=index_name, indicator='市盈率'|'市净率')。
        """
        try:
            if indicator == "pe":
                df = self.ak.stock_index_pe_lg(symbol=index_name)
                col_candidates = ("市盈率", "平均市盈率", "滚动市盈率", "pe")
            else:
                df = self.ak.stock_index_pb_lg(symbol=index_name)
                col_candidates = ("市净率", "平均市净率", "pb")
        except Exception as e:  # noqa: BLE001
            logger.warning("指数 %s %s 估值获取失败: %s", index_name, indicator, e)
            return None
        if df is None or df.empty:
            return None
        out = []
        for _, r in df.iterrows():
            date = to_date(row_get(r, "日期", "date", "trade_date"))
            value = to_float(row_get(r, *col_candidates))
            if not date:
                continue
            out.append({"trade_date": date, indicator: value})
        return out
```

- [x] **Step 6: 新建 valuation_service.py**

创建 `etf-service/app/services/valuation_service.py`：

```python
import logging
from datetime import datetime
from typing import Dict, List, Optional

from ..datasource.akshare_source import AkshareSource
from ..database.mongo import mongo_client
from ..normalize.schema import IndexValuation, compute_percentile

logger = logging.getLogger(__name__)


class ValuationService:
    VALUATION_INDEX_NAMES: Dict[str, str] = {
        "sh000300": "沪深300",
        "sh000905": "中证500",
        "sz399006": "创业板指",
        "sh000001": "上证指数",
    }

    def __init__(self, ak=None, mongo=None):
        self.ak = ak or AkshareSource().ak
        self.mongo = mongo or mongo_client

    def fetch_index_valuation(self, index_code: str) -> int:
        """采集单个指数 PE/PB 历史、逐日计算历史分位并写入 index_valuation，返回写入条数。"""
        name = self.VALUATION_INDEX_NAMES.get(index_code)
        if not name:
            logger.warning("未配置指数 %s 的估值名称", index_code)
            return 0
        pe_rows = self.ak.fetch_index_valuation_history(name, "pe")
        pb_rows = self.ak.fetch_index_valuation_history(name, "pb")
        pe_map = {r["trade_date"]: r["pe"] for r in (pe_rows or [])}
        pb_map = {r["trade_date"]: r["pb"] for r in (pb_rows or [])}
        dates = sorted(set(pe_map) | set(pb_map))
        pe_history: List[float] = []
        pb_history: List[float] = []
        count = 0
        for d in dates:
            pe = pe_map.get(d)
            pb = pb_map.get(d)
            if pe is not None:
                pe_history.append(pe)
            if pb is not None:
                pb_history.append(pb)
            record = IndexValuation(
                index_code=index_code,
                trade_date=d,
                pe=pe if pe is not None else 0.0,
                pb=pb if pb is not None else 0.0,
                pe_percentile=compute_percentile(pe_history, pe) if pe is not None else 0.0,
                pb_percentile=compute_percentile(pb_history, pb) if pb is not None else 0.0,
                data_source="akshare",
                updated_at=datetime.now(),
            ).to_dict()
            if self.mongo.upsert_index_valuation(record):
                count += 1
        logger.info("指数 %s 估值采集完成，写入 %s 条", index_code, count)
        return count

    def update_all_index_valuation(self) -> int:
        total = 0
        for code in self.VALUATION_INDEX_NAMES:
            total += self.fetch_index_valuation(code)
        logger.info("指数估值采集完成，共写入 %s 条", total)
        return total


valuation_service = ValuationService()
```

- [x] **Step 7: 运行测试确认通过**

Run: `cd etf-service && python -m pytest tests/test_valuation_service.py -v`
Expected: PASS（3 个用例全部通过）

- [x] **Step 8: 提交**

```bash
git add etf-service/app/services/valuation_service.py etf-service/app/normalize/schema.py etf-service/app/datasource/akshare_source.py etf-service/app/database/mongo.py etf-service/tests/test_valuation_service.py
git commit -m "feat(etf-service): 采集宽基指数 PE/PB 估值并计算历史分位写入 index_valuation"
```

---

### Task 2: etf-service — 指数估值定时更新任务

**Files:**
- Modify: `etf-service/app/tasks/etf_task.py`
- Test: `etf-service/tests/test_data_tasks.py`（追加用例）

**Interfaces:**
- Consumes: `valuation_service.update_all_index_valuation() -> int`（Task 1）。
- Produces: `update_index_valuation()`（供 `start_scheduler` 注册的定时任务函数）。

- [x] **Step 1: 写失败测试**

在 `etf-service/tests/test_data_tasks.py` 末尾追加（用 patch 验证调度函数调用了 service 并返回计数）：

```python
from unittest.mock import patch


def test_update_index_valuation_calls_service():
    with patch("app.tasks.etf_task.valuation_service") as svc:
        svc.update_all_index_valuation.return_value = 42
        from app.tasks.etf_task import update_index_valuation
        update_index_valuation()
        svc.update_all_index_valuation.assert_called_once()
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service && python -m pytest tests/test_data_tasks.py::test_update_index_valuation_calls_service -v`
Expected: FAIL（`ImportError: cannot import name 'update_index_valuation'`）

- [x] **Step 3: 在 etf_task.py 新增定时任务函数并注册**

在 `etf-service/app/tasks/etf_task.py` 顶部 import 区加：

```python
from ..services.valuation_service import valuation_service
```

在 `update_stock_industry_concept` 之外（`start_scheduler` 之前）加：

```python
def update_index_valuation():
    """指数估值（PE/PB 及其历史分位）收盘后更新。"""
    logger.info("开始更新指数估值")
    try:
        n = valuation_service.update_all_index_valuation()
        logger.info("指数估值更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新指数估值失败: %s", e, exc_info=True)
```

在 `start_scheduler()` 中（`index_service.update_index_daily` 的 add_job 之后）加：

```python
            scheduler.add_job(
                func=update_index_valuation,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_index_valuation",
                name="交易日15:30后更新指数估值",
                replace_existing=True,
            )
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service && python -m pytest tests/test_data_tasks.py -v`
Expected: PASS（原有用例 + 新用例全部通过）

- [x] **Step 5: 提交**

```bash
git add etf-service/app/tasks/etf_task.py etf-service/tests/test_data_tasks.py
git commit -m "feat(etf-service): 注册指数估值收盘后定时更新任务"
```

---

### Task 3: StockAI — 新增 PO 与 Repository（指数日线/估值/市场状态记录）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/po/IndexDailyQuotes.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/IndexValuation.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/RegimeIndicators.java`
- Create: `StockAI/src/main/java/com/xian/stockai/po/MarketRegimeRecord.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/IndexDailyQuotesRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/IndexValuationRepository.java`
- Create: `StockAI/src/main/java/com/xian/stockai/dao/MarketRegimeRecordRepository.java`

**Interfaces:**
- Consumes: 无（纯数据层）。
- Produces（后续任务依赖）:
  - `IndexDailyQuotes`：`getIndexCode()/getTradeDate()/getClose()/getVolume()`。
  - `IndexValuation`：`getPePercentile()/getPbPercentile()`。
  - `RegimeIndicators`：`getMa20()/getMa60()/getMa250()/getVolumeRatio()/getVolatility()/getLatestClose()/getPePercentile()/getPbPercentile()/getBenchmarkIndex()`（及对应 setter）。
  - `MarketRegimeRecord`：`getDate()/getRegime()/getPositionAdvice()/getIndicators()/getReport()/getCreatedAt()`（及 setter）。
  - `IndexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc(String) -> List<IndexDailyQuotes>`。
  - `IndexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc(String) -> IndexValuation`。
  - `MarketRegimeRecordRepository.findTopByOrderByDateDesc() -> MarketRegimeRecord`、`findAllByOrderByDateDesc(Pageable) -> List<MarketRegimeRecord>`。

- [x] **Step 1: 创建 IndexDailyQuotes PO**

创建 `StockAI/src/main/java/com/xian/stockai/po/IndexDailyQuotes.java`（风格对齐现有 `EtfDailyQuotes`，字段对应 `index_daily_quotes` 集合）：

```java
package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Document(collection = "index_daily_quotes")
public class IndexDailyQuotes {
    @Id
    private String id;

    @Field(name = "index_code")
    private String indexCode;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "open")
    private BigDecimal open;

    @Field(name = "high")
    private BigDecimal high;

    @Field(name = "low")
    private BigDecimal low;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "volume")
    private BigDecimal volume;

    @Field(name = "amount")
    private BigDecimal amount;

    @Field(name = "data_source")
    private String dataSource;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
}
```

- [x] **Step 2: 创建 IndexValuation PO**

创建 `StockAI/src/main/java/com/xian/stockai/po/IndexValuation.java`：

```java
package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Document(collection = "index_valuation")
public class IndexValuation {
    @Id
    private String id;

    @Field(name = "index_code")
    private String indexCode;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "pe")
    private BigDecimal pe;

    @Field(name = "pb")
    private BigDecimal pb;

    @Field(name = "pe_percentile")
    private BigDecimal pePercentile;

    @Field(name = "pb_percentile")
    private BigDecimal pbPercentile;

    @Field(name = "data_source")
    private String dataSource;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getPePercentile() { return pePercentile; }
    public void setPePercentile(BigDecimal pePercentile) { this.pePercentile = pePercentile; }
    public BigDecimal getPbPercentile() { return pbPercentile; }
    public void setPbPercentile(BigDecimal pbPercentile) { this.pbPercentile = pbPercentile; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
}
```

- [x] **Step 3: 创建 RegimeIndicators 内嵌类**

创建 `StockAI/src/main/java/com/xian/stockai/po/RegimeIndicators.java`：

```java
package com.xian.stockai.po;

import java.math.BigDecimal;

public class RegimeIndicators {
    private BigDecimal ma20;
    private BigDecimal ma60;
    private BigDecimal ma250;
    private BigDecimal volumeRatio;
    private BigDecimal volatility;
    private BigDecimal latestClose;
    private BigDecimal pePercentile;
    private BigDecimal pbPercentile;
    private String benchmarkIndex;

    public BigDecimal getMa20() { return ma20; }
    public void setMa20(BigDecimal ma20) { this.ma20 = ma20; }
    public BigDecimal getMa60() { return ma60; }
    public void setMa60(BigDecimal ma60) { this.ma60 = ma60; }
    public BigDecimal getMa250() { return ma250; }
    public void setMa250(BigDecimal ma250) { this.ma250 = ma250; }
    public BigDecimal getVolumeRatio() { return volumeRatio; }
    public void setVolumeRatio(BigDecimal volumeRatio) { this.volumeRatio = volumeRatio; }
    public BigDecimal getVolatility() { return volatility; }
    public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }
    public BigDecimal getLatestClose() { return latestClose; }
    public void setLatestClose(BigDecimal latestClose) { this.latestClose = latestClose; }
    public BigDecimal getPePercentile() { return pePercentile; }
    public void setPePercentile(BigDecimal pePercentile) { this.pePercentile = pePercentile; }
    public BigDecimal getPbPercentile() { return pbPercentile; }
    public void setPbPercentile(BigDecimal pbPercentile) { this.pbPercentile = pbPercentile; }
    public String getBenchmarkIndex() { return benchmarkIndex; }
    public void setBenchmarkIndex(String benchmarkIndex) { this.benchmarkIndex = benchmarkIndex; }
}
```

- [x] **Step 4: 创建 MarketRegimeRecord PO**

创建 `StockAI/src/main/java/com/xian/stockai/po/MarketRegimeRecord.java`（对齐现有 `PositionMonitor` 风格）：

```java
package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "market_regime_records")
public class MarketRegimeRecord {
    @Id
    private String id;

    @Field(name = "date")
    @Indexed(unique = true)
    private String date;

    @Field(name = "regime")
    private String regime;

    @Field(name = "position_advice")
    private String positionAdvice;

    @Field(name = "indicators")
    private RegimeIndicators indicators;

    @Field(name = "report")
    private String report;

    @Field(name = "created_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public String getPositionAdvice() { return positionAdvice; }
    public void setPositionAdvice(String positionAdvice) { this.positionAdvice = positionAdvice; }
    public RegimeIndicators getIndicators() { return indicators; }
    public void setIndicators(RegimeIndicators indicators) { this.indicators = indicators; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [x] **Step 5: 创建三个 Repository 接口**

创建 `StockAI/src/main/java/com/xian/stockai/dao/IndexDailyQuotesRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.IndexDailyQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IndexDailyQuotesRepository extends MongoRepository<IndexDailyQuotes, String> {
    List<IndexDailyQuotes> findTop250ByIndexCodeOrderByTradeDateDesc(String indexCode);
}
```

创建 `StockAI/src/main/java/com/xian/stockai/dao/IndexValuationRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.IndexValuation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IndexValuationRepository extends MongoRepository<IndexValuation, String> {
    IndexValuation findTopByIndexCodeOrderByTradeDateDesc(String indexCode);
}
```

创建 `StockAI/src/main/java/com/xian/stockai/dao/MarketRegimeRecordRepository.java`：

```java
package com.xian.stockai.dao;

import com.xian.stockai.po.MarketRegimeRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MarketRegimeRecordRepository extends MongoRepository<MarketRegimeRecord, String> {
    MarketRegimeRecord findTopByOrderByDateDesc();

    List<MarketRegimeRecord> findAllByOrderByDateDesc(Pageable pageable);
}
```

- [x] **Step 6: 编译验证**

Run: `cd StockAI && mvn -q compile`
Expected: BUILD SUCCESS（若无全局 mvn，改用 IDE 编译，或 `mvnw` 若存在；本项目仅含 `.mvn/wrapper`，用系统 `mvn`）

- [x] **Step 7: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/po/IndexDailyQuotes.java StockAI/src/main/java/com/xian/stockai/po/IndexValuation.java StockAI/src/main/java/com/xian/stockai/po/RegimeIndicators.java StockAI/src/main/java/com/xian/stockai/po/MarketRegimeRecord.java StockAI/src/main/java/com/xian/stockai/dao/IndexDailyQuotesRepository.java StockAI/src/main/java/com/xian/stockai/dao/IndexValuationRepository.java StockAI/src/main/java/com/xian/stockai/dao/MarketRegimeRecordRepository.java
git commit -m "feat(stockai): 新增指数日线/估值/市场状态记录的 PO 与 Repository"
```

---

### Task 4: StockAI — 量化规则判定与仓位建议映射（纯函数）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/RegimeRule.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/RegimeRuleTest.java`

**Interfaces:**
- Consumes: `RegimeIndicators`（Task 3）。
- Produces（Task 5/6 依赖）:
  - 常量：`RegimeRule.REGIME_BULL/REGIME_BEAR/REGIME_SIDEWAYS/REGIME_RISK`、`RegimeRule.ADVICE_AGGRESSIVE/ADVICE_NEUTRAL/ADVICE_DEFENSIVE/ADVICE_CASH`。
  - `RegimeRule.determineRegime(RegimeIndicators ind, BigDecimal volatilityHigh, BigDecimal valuationHigh) -> String`。
  - `RegimeRule.mapPositionAdvice(String regime) -> String`。

- [x] **Step 1: 写失败测试**

创建 `StockAI/src/test/java/com/xian/stockai/service/RegimeRuleTest.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.po.RegimeIndicators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegimeRuleTest {

    private static final BigDecimal VOL_HIGH = new BigDecimal("0.30");
    private static final BigDecimal VAL_HIGH = new BigDecimal("0.80");

    private RegimeIndicators base() {
        RegimeIndicators ind = new RegimeIndicators();
        ind.setMa20(new BigDecimal("3200"));
        ind.setMa60(new BigDecimal("3100"));
        ind.setMa250(new BigDecimal("3000"));
        ind.setVolatility(new BigDecimal("0.15"));
        ind.setLatestClose(new BigDecimal("3300"));
        ind.setPePercentile(new BigDecimal("0.50"));
        ind.setPbPercentile(new BigDecimal("0.40"));
        return ind;
    }

    @Test
    void bullTrendWithLowVolAndMidValuationYieldsBull() {
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(base(), VOL_HIGH, VAL_HIGH));
    }

    @Test
    void bearTrendYieldsBear() {
        RegimeIndicators ind = base();
        ind.setMa20(new BigDecimal("2900"));
        ind.setMa60(new BigDecimal("3000"));
        ind.setMa250(new BigDecimal("3100"));
        assertEquals(RegimeRule.REGIME_BEAR,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void tangledMAsYieldSideways() {
        RegimeIndicators ind = base();
        ind.setMa20(new BigDecimal("3100"));
        ind.setMa60(new BigDecimal("3200"));
        ind.setMa250(new BigDecimal("3000"));
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void highVolFallingAndHighValuationYieldsRisk() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        ind.setLatestClose(new BigDecimal("3100"));
        ind.setPePercentile(new BigDecimal("0.90"));
        ind.setPbPercentile(new BigDecimal("0.85"));
        assertEquals(RegimeRule.REGIME_RISK,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void bullTrendWithHighVolDowngradesToSideways() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void positionAdviceMapsOneToOne() {
        assertEquals(RegimeRule.ADVICE_AGGRESSIVE, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_BULL));
        assertEquals(RegimeRule.ADVICE_NEUTRAL, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_SIDEWAYS));
        assertEquals(RegimeRule.ADVICE_DEFENSIVE, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_BEAR));
        assertEquals(RegimeRule.ADVICE_CASH, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_RISK));
    }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd StockAI && mvn test -Dtest=RegimeRuleTest`
Expected: FAIL（编译错误：`cannot find symbol RegimeRule`）

- [x] **Step 3: 创建 RegimeRule 纯函数类**

创建 `StockAI/src/main/java/com/xian/stockai/service/RegimeRule.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.po.RegimeIndicators;

import java.math.BigDecimal;

public final class RegimeRule {
    public static final String REGIME_BULL = "BULL";
    public static final String REGIME_BEAR = "BEAR";
    public static final String REGIME_SIDEWAYS = "SIDEWAYS";
    public static final String REGIME_RISK = "RISK";

    public static final String ADVICE_AGGRESSIVE = "AGGRESSIVE";
    public static final String ADVICE_NEUTRAL = "NEUTRAL";
    public static final String ADVICE_DEFENSIVE = "DEFENSIVE";
    public static final String ADVICE_CASH = "CASH";

    private RegimeRule() {
    }

    public static String determineRegime(RegimeIndicators ind, BigDecimal volatilityHigh, BigDecimal valuationHigh) {
        boolean bullTrend = ind.getMa20().compareTo(ind.getMa60()) > 0
                && ind.getMa60().compareTo(ind.getMa250()) > 0;
        boolean bearTrend = ind.getMa20().compareTo(ind.getMa60()) < 0
                && ind.getMa60().compareTo(ind.getMa250()) < 0;
        boolean highVol = ind.getVolatility().compareTo(volatilityHigh) > 0;
        BigDecimal valuationPct = ind.getPePercentile().max(ind.getPbPercentile());
        boolean highValuation = valuationPct.compareTo(valuationHigh) > 0;
        boolean falling = ind.getLatestClose().compareTo(ind.getMa20()) < 0;

        if (highVol && falling && highValuation) {
            return REGIME_RISK;
        }
        if (bullTrend) {
            return (!highVol && !highValuation) ? REGIME_BULL : REGIME_SIDEWAYS;
        }
        if (bearTrend) {
            return REGIME_BEAR;
        }
        return REGIME_SIDEWAYS;
    }

    public static String mapPositionAdvice(String regime) {
        return switch (regime) {
            case REGIME_BULL -> ADVICE_AGGRESSIVE;
            case REGIME_BEAR -> ADVICE_DEFENSIVE;
            case REGIME_RISK -> ADVICE_CASH;
            default -> ADVICE_NEUTRAL;
        };
    }
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd StockAI && mvn test -Dtest=RegimeRuleTest`
Expected: PASS（6 个用例全部通过）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/RegimeRule.java StockAI/src/test/java/com/xian/stockai/service/RegimeRuleTest.java
git commit -m "feat(stockai): 实现市场四态量化规则判定与仓位建议映射"
```

---

### Task 5: StockAI — MarketRegimeService 指标计算与记录串联（含飞书推送）

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/service/MarketRegimeService.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/MarketRegimeServiceTest.java`

**Interfaces:**
- Consumes: `RegimeRule`（Task 4）、三个 Repository + PO（Task 3）、`ChatClient`（Spring AI，本任务仅构造器注入，实际调用在 Task 6）、`ObjectProvider<FeishuNotificationSender>`。
- Produces（Task 6/7 依赖）:
  - `MarketRegimeService.calculateIndicators(String indexCode) -> RegimeIndicators`（数据不足返回 null）。
  - `MarketRegimeService.evaluateAndSave() -> Result<MarketRegimeRecord>`（report 暂用 `buildFallbackReport`）。
  - `MarketRegimeService.latest() -> Result<MarketRegimeRecord>`、`MarketRegimeService.history(int limit) -> Result<List<MarketRegimeRecord>>`。

> 本任务先把 `evaluateAndSave` 的 report 固定为 `buildFallbackReport`（量化摘要），Task 6 再接入 ChatClient 与 AI 降级。两个任务修改同一文件，属计划内的正常演进。

- [x] **Step 1: 写失败测试**

创建 `StockAI/src/test/java/com/xian/stockai/service/MarketRegimeServiceTest.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.IndexDailyQuotesRepository;
import com.xian.stockai.dao.IndexValuationRepository;
import com.xian.stockai.dao.MarketRegimeRecordRepository;
import com.xian.stockai.po.IndexDailyQuotes;
import com.xian.stockai.po.IndexValuation;
import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.po.RegimeIndicators;
import com.xian.stockai.service.notify.FeishuNotificationSender;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketRegimeServiceTest {

    @Mock
    private IndexDailyQuotesRepository indexDailyQuotesRepository;
    @Mock
    private IndexValuationRepository indexValuationRepository;
    @Mock
    private MarketRegimeRecordRepository marketRegimeRecordRepository;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ObjectProvider<FeishuNotificationSender> feishuProvider;

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService(
                indexDailyQuotesRepository, indexValuationRepository, marketRegimeRecordRepository,
                chatClient, feishuProvider, "sh000300",
                new BigDecimal("0.30"), new BigDecimal("0.80"));
    }

    private IndexDailyQuotes quote(String close, String volume) {
        IndexDailyQuotes q = new IndexDailyQuotes();
        q.setClose(new BigDecimal(close));
        q.setVolume(new BigDecimal(volume));
        return q;
    }

    private List<IndexDailyQuotes> trendingQuotes() {
        List<IndexDailyQuotes> list = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            list.add(quote(String.valueOf(3000 + i), "1000000"));
        }
        return list;
    }

    @Test
    void calculateIndicatorsComputesMaAndRatio() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        IndexValuation val = new IndexValuation();
        val.setPePercentile(new BigDecimal("0.60"));
        val.setPbPercentile(new BigDecimal("0.55"));
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(val);

        RegimeIndicators ind = service.calculateIndicators("sh000300");

        assertNotNull(ind);
        // 递增收盘价：MA20 > MA60 > MA250
        assertTrue(ind.getMa20().compareTo(ind.getMa60()) > 0);
        assertTrue(ind.getMa60().compareTo(ind.getMa250()) > 0);
        assertEquals(0, new BigDecimal("0.60").compareTo(ind.getPePercentile()));
        assertEquals(0, new BigDecimal("0.55").compareTo(ind.getPbPercentile()));
    }

    @Test
    void calculateIndicatorsReturnsNullWhenInsufficient() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(List.of(quote("3000", "1000000")));
        assertNull(service.calculateIndicators("sh000300"));
    }

    @Test
    void evaluateAndSaveWritesRecordAndPushesFeishu() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(null);
        FeishuNotificationSender sender = mock(FeishuNotificationSender.class);
        when(feishuProvider.getIfAvailable()).thenReturn(sender);

        Result<MarketRegimeRecord> result = service.evaluateAndSave();

        assertTrue(result.checkSuccess());
        assertEquals(RegimeRule.REGIME_BULL, result.getData().getRegime());
        assertEquals(RegimeRule.ADVICE_AGGRESSIVE, result.getData().getPositionAdvice());
        assertTrue(result.getData().getReport().contains("量化结论"));
        verify(marketRegimeRecordRepository).save(any(MarketRegimeRecord.class));
        verify(sender).send(any());
    }

    @Test
    void evaluateAndSaveSkipsPushWhenFeishuDisabled() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(null);
        when(feishuProvider.getIfAvailable()).thenReturn(null);

        Result<MarketRegimeRecord> result = service.evaluateAndSave();

        assertTrue(result.checkSuccess());
        verify(marketRegimeRecordRepository).save(any(MarketRegimeRecord.class));
    }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd StockAI && mvn test -Dtest=MarketRegimeServiceTest`
Expected: FAIL（`cannot find symbol MarketRegimeService`）

- [x] **Step 3: 创建 MarketRegimeService**

创建 `StockAI/src/main/java/com/xian/stockai/service/MarketRegimeService.java`：

```java
package com.xian.stockai.service;

import com.xian.stockai.dao.IndexDailyQuotesRepository;
import com.xian.stockai.dao.IndexValuationRepository;
import com.xian.stockai.dao.MarketRegimeRecordRepository;
import com.xian.stockai.po.IndexDailyQuotes;
import com.xian.stockai.po.IndexValuation;
import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.po.Notification;
import com.xian.stockai.po.RegimeIndicators;
import com.xian.stockai.service.notify.FeishuNotificationSender;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class MarketRegimeService {

    private final IndexDailyQuotesRepository indexDailyQuotesRepository;
    private final IndexValuationRepository indexValuationRepository;
    private final MarketRegimeRecordRepository marketRegimeRecordRepository;
    private final ChatClient chatClient;
    private final ObjectProvider<FeishuNotificationSender> feishuProvider;
    private final String benchmarkIndex;
    private final BigDecimal volatilityHigh;
    private final BigDecimal valuationHigh;

    public MarketRegimeService(
            IndexDailyQuotesRepository indexDailyQuotesRepository,
            IndexValuationRepository indexValuationRepository,
            MarketRegimeRecordRepository marketRegimeRecordRepository,
            ChatClient chatClient,
            ObjectProvider<FeishuNotificationSender> feishuProvider,
            @Value("${market-regime.benchmark-index:sh000300}") String benchmarkIndex,
            @Value("${market-regime.thresholds.volatility-high:0.30}") BigDecimal volatilityHigh,
            @Value("${market-regime.thresholds.valuation-high:0.80}") BigDecimal valuationHigh) {
        this.indexDailyQuotesRepository = indexDailyQuotesRepository;
        this.indexValuationRepository = indexValuationRepository;
        this.marketRegimeRecordRepository = marketRegimeRecordRepository;
        this.chatClient = chatClient;
        this.feishuProvider = feishuProvider;
        this.benchmarkIndex = benchmarkIndex;
        this.volatilityHigh = volatilityHigh;
        this.valuationHigh = valuationHigh;
    }

    public RegimeIndicators calculateIndicators(String indexCode) {
        List<IndexDailyQuotes> recent = indexDailyQuotesRepository
                .findTop250ByIndexCodeOrderByTradeDateDesc(indexCode);
        if (recent == null || recent.size() < 20) {
            log.warn("基准指数 {} 日线不足 20 条，无法判定", indexCode);
            return null;
        }
        List<IndexDailyQuotes> asc = new ArrayList<>(recent);
        Collections.reverse(asc);

        RegimeIndicators ind = new RegimeIndicators();
        ind.setBenchmarkIndex(indexCode);
        ind.setMa20(ma(asc, 20));
        ind.setMa60(ma(asc, 60));
        ind.setMa250(ma(asc, 250));
        ind.setLatestClose(asc.get(asc.size() - 1).getClose());
        ind.setVolumeRatio(volumeRatio(asc));
        ind.setVolatility(volatility(asc));

        IndexValuation val = indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc(indexCode);
        if (val != null) {
            ind.setPePercentile(val.getPePercentile());
            ind.setPbPercentile(val.getPbPercentile());
        } else {
            ind.setPePercentile(BigDecimal.ZERO);
            ind.setPbPercentile(BigDecimal.ZERO);
        }
        return ind;
    }

    public String buildFallbackReport(String regime, String positionAdvice, RegimeIndicators ind) {
        return String.format(
                "[量化结论] 市场状态：%s；仓位建议：%s。基准指数 %s，MA20=%.2f，量比=%.2f，波动率=%.2f%%，PE分位=%.2f，PB分位=%.2f。",
                regime, positionAdvice, ind.getBenchmarkIndex(), ind.getMa20(),
                ind.getVolumeRatio(), ind.getVolatility().multiply(BigDecimal.valueOf(100)),
                ind.getPePercentile(), ind.getPbPercentile());
    }

    public Result<MarketRegimeRecord> evaluateAndSave() {
        RegimeIndicators ind = calculateIndicators(benchmarkIndex);
        if (ind == null) {
            return Result.fail("基准指数数据不足，无法判定");
        }
        String regime = RegimeRule.determineRegime(ind, volatilityHigh, valuationHigh);
        String advice = RegimeRule.mapPositionAdvice(regime);
        String report = buildFallbackReport(regime, advice, ind);

        MarketRegimeRecord record = new MarketRegimeRecord();
        record.setDate(LocalDate.now().toString());
        record.setRegime(regime);
        record.setPositionAdvice(advice);
        record.setIndicators(ind);
        record.setReport(report);
        record.setCreatedAt(LocalDateTime.now());
        marketRegimeRecordRepository.save(record);
        pushFeishu(record);
        return Result.success(record);
    }

    private void pushFeishu(MarketRegimeRecord record) {
        try {
            FeishuNotificationSender sender = feishuProvider.getIfAvailable();
            if (sender == null) {
                log.info("飞书未启用，跳过市场温度计日报推送");
                return;
            }
            Notification n = new Notification();
            n.setType("MARKET_REGIME");
            n.setContent(record.getReport());
            n.setRead(false);
            n.setCreatedAt(LocalDateTime.now());
            sender.send(n);
        } catch (Exception e) {
            log.error("飞书日报推送失败，不影响记录生成: {}", e.getMessage());
        }
    }

    public Result<MarketRegimeRecord> latest() {
        return Result.successIfNotNull(marketRegimeRecordRepository.findTopByOrderByDateDesc(), "暂无市场状态记录");
    }

    public Result<List<MarketRegimeRecord>> history(int limit) {
        return Result.success(marketRegimeRecordRepository
                .findAllByOrderByDateDesc(PageRequest.of(0, Math.max(1, limit))));
    }

    private BigDecimal ma(List<IndexDailyQuotes> asc, int window) {
        int n = Math.min(window, asc.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = asc.size() - n; i < asc.size(); i++) {
            sum = sum.add(asc.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal avgVolume(List<IndexDailyQuotes> asc, int window) {
        int n = Math.min(window, asc.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = asc.size() - n; i < asc.size(); i++) {
            sum = sum.add(asc.get(i).getVolume());
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal volumeRatio(List<IndexDailyQuotes> asc) {
        BigDecimal avg5 = avgVolume(asc, 5);
        BigDecimal avg60 = avgVolume(asc, 60);
        if (avg60.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return avg5.divide(avg60, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal volatility(List<IndexDailyQuotes> asc) {
        List<BigDecimal> returns = new ArrayList<>();
        int start = Math.max(1, asc.size() - 20);
        for (int i = start; i < asc.size(); i++) {
            BigDecimal prev = asc.get(i - 1).getClose();
            if (prev.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(asc.get(i).getClose().subtract(prev).divide(prev, 6, RoundingMode.HALF_UP));
        }
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            BigDecimal d = r.subtract(mean);
            variance = variance.add(d.multiply(d));
        }
        variance = variance.divide(BigDecimal.valueOf(returns.size() - 1), 8, RoundingMode.HALF_UP);
        BigDecimal std = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        return std.multiply(BigDecimal.valueOf(Math.sqrt(252))).setScale(4, RoundingMode.HALF_UP);
    }
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd StockAI && mvn test -Dtest=MarketRegimeServiceTest`
Expected: PASS（4 个用例全部通过）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/MarketRegimeService.java StockAI/src/test/java/com/xian/stockai/service/MarketRegimeServiceTest.java
git commit -m "feat(stockai): 实现市场状态指标计算、记录串联与飞书日报推送"
```

---

### Task 6: StockAI — AI 总结与失败降级

**Files:**
- Modify: `StockAI/src/main/java/com/xian/stockai/service/MarketRegimeService.java`
- Test: `StockAI/src/test/java/com/xian/stockai/service/MarketRegimeServiceTest.java`（追加用例）

**Interfaces:**
- Consumes: `ChatClient`（构造器已注入，Task 5）、`RegimeIndicators`（Task 3）。
- Produces:
  - `MarketRegimeService.generateReport(String regime, String positionAdvice, RegimeIndicators ind) -> String`。
  - `evaluateAndSave()` 行为变更：优先 `generateReport`，AI 异常时降级到 `buildFallbackReport`（记录仍写入，判定结果不变）。

- [x] **Step 1: 追加失败测试（AI 失败降级）**

在 `MarketRegimeServiceTest.java` 追加：

```java
    @Test
    void evaluateAndSaveFallsBackWhenAiFails() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(null);
        when(feishuProvider.getIfAvailable()).thenReturn(null);

        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("AI down"));

        Result<MarketRegimeRecord> result = service.evaluateAndSave();

        assertTrue(result.checkSuccess());
        assertTrue(result.getData().getReport().contains("量化结论"));
        verify(marketRegimeRecordRepository).save(any(MarketRegimeRecord.class));
    }
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd StockAI && mvn test -Dtest=MarketRegimeServiceTest`
Expected: FAIL（当前 `evaluateAndSave` 未调用 `generateReport`，AI mock 未被触发，但断言 `report` 仍含"量化结论"会 PASS；此用例主要验证不因 AI 异常中断——若需严格 TDD，先实现 `generateReport` 后再跑）

- [x] **Step 3: 在 MarketRegimeService 新增 generateReport 并接入降级**

在 `buildFallbackReport` 之后加：

```java
    public String generateReport(String regime, String positionAdvice, RegimeIndicators ind) {
        String prompt = String.format(
                "你是市场温度计助手。根据以下量化判定结果，用中文输出一段不超过100字的市场日报，"
                        + "必须包含「结论」「依据」「仓位建议」三部分。\n"
                        + "市场状态：%s\n仓位建议：%s\n"
                        + "基准指数：%s\nMA20=%.2f MA60=%.2f MA250=%.2f 最新收盘=%.2f\n"
                        + "量比(5日/60日)=%.2f 20日年化波动率=%.2f%%\nPE分位=%.2f PB分位=%.2f",
                regime, positionAdvice, ind.getBenchmarkIndex(),
                ind.getMa20(), ind.getMa60(), ind.getMa250(), ind.getLatestClose(),
                ind.getVolumeRatio(), ind.getVolatility().multiply(BigDecimal.valueOf(100)),
                ind.getPePercentile(), ind.getPbPercentile());
        return chatClient.prompt().user(prompt).call().content();
    }
```

将 `evaluateAndSave` 中 `String report = buildFallbackReport(regime, advice, ind);` 替换为：

```java
        String report;
        try {
            report = generateReport(regime, advice, ind);
        } catch (Exception e) {
            log.error("AI 总结失败，降级为量化结论: {}", e.getMessage());
            report = buildFallbackReport(regime, advice, ind);
        }
```

- [x] **Step 4: 运行全部测试确认通过**

Run: `cd StockAI && mvn test -Dtest=MarketRegimeServiceTest`
Expected: PASS（5 个用例全部通过）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/service/MarketRegimeService.java StockAI/src/test/java/com/xian/stockai/service/MarketRegimeServiceTest.java
git commit -m "feat(stockai): 注入量化指标生成 AI 总结报告，AI 失败降级为量化结论"
```

---

### Task 7: StockAI — Controller、定时任务与配置

**Files:**
- Create: `StockAI/src/main/java/com/xian/stockai/controller/MarketRegimeController.java`
- Create: `StockAI/src/main/java/com/xian/stockai/task/MarketRegimeTask.java`
- Modify: `StockAI/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `MarketRegimeService`（Task 5/6）：`evaluateAndSave()`、`latest()`、`history(int)`。
- Produces:
  - HTTP：`POST /market-regime/evaluate`、`POST /market-regime/latest`、`POST /market-regime/history?limit=30`（均返回 `Result<T>`）。
  - 定时：`@Scheduled(cron = "${market-regime.cron:0 30 15 * * ?}")` 触发 `evaluateAndSave`。

- [x] **Step 1: 创建 MarketRegimeController**

创建 `StockAI/src/main/java/com/xian/stockai/controller/MarketRegimeController.java`：

```java
package com.xian.stockai.controller;

import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.service.MarketRegimeService;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/market-regime")
public class MarketRegimeController {
    private final MarketRegimeService marketRegimeService;

    @PostMapping("/evaluate")
    public Result<MarketRegimeRecord> evaluate() {
        return marketRegimeService.evaluateAndSave();
    }

    @PostMapping("/latest")
    public Result<MarketRegimeRecord> latest() {
        return marketRegimeService.latest();
    }

    @PostMapping("/history")
    public Result<List<MarketRegimeRecord>> history(@RequestParam(defaultValue = "30") int limit) {
        return marketRegimeService.history(limit);
    }
}
```

- [x] **Step 2: 创建 MarketRegimeTask**

创建 `StockAI/src/main/java/com/xian/stockai/task/MarketRegimeTask.java`（对齐现有 `MonitorTask`）：

```java
package com.xian.stockai.task;

import com.xian.stockai.service.MarketRegimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketRegimeTask {
    private final MarketRegimeService marketRegimeService;

    @Scheduled(cron = "${market-regime.cron:0 30 15 * * ?}")
    public void evaluateDaily() {
        log.info("开始市场温度计收盘判定");
        marketRegimeService.evaluateAndSave();
        log.info("市场温度计收盘判定结束");
    }
}
```

- [x] **Step 3: 在 application.yml 新增 market-regime 配置**

在 `StockAI/src/main/resources/application.yml` 末尾（`feishu:` 之后）追加：

```yaml
# 市场温度计：收盘后判定定时任务与量化阈值
market-regime:
  cron: "0 30 15 * * ?"
  benchmark-index: "sh000300"
  thresholds:
    volatility-high: 0.30
    valuation-high: 0.80
```

- [x] **Step 4: 编译与测试验证**

Run: `cd StockAI && mvn -q compile && mvn test -Dtest=MarketRegimeServiceTest,RegimeRuleTest`
Expected: BUILD SUCCESS + 全部测试 PASS（`@Value` 默认值确保不依赖真实配置即可启动单测）

- [x] **Step 5: 提交**

```bash
git add StockAI/src/main/java/com/xian/stockai/controller/MarketRegimeController.java StockAI/src/main/java/com/xian/stockai/task/MarketRegimeTask.java StockAI/src/main/resources/application.yml
git commit -m "feat(stockai): 新增市场温度计查询接口与收盘定时任务及配置"
```

---

### Task 8: StockAI-Console — 温度计页面（当前状态与仓位建议）

**Files:**
- Create: `StockAI-Console/src/components/MarketRegime.vue`
- Modify: `StockAI-Console/src/router/index.js`
- Modify: `StockAI-Console/src/App.vue`

**Interfaces:**
- Consumes: `POST /market-regime/latest`（返回 `Result<MarketRegimeRecord>`，字段 `regime/positionAdvice/indicators/report/date`）、`config.baseUrl`。
- Produces: 路由 `/regime`（菜单名「温度计」）。

- [x] **Step 1: 创建 MarketRegime.vue**

创建 `StockAI-Console/src/components/MarketRegime.vue`：

```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import config from '../config/config'

const BASE_URL = config.baseUrl
const loading = ref(false)
const record = ref(null)

const REGIME_LABEL = {
  BULL: { text: '牛', color: '#ef4444', advice: '激进（高仓位）' },
  BEAR: { text: '熊', color: '#10b981', advice: '防御（低仓位）' },
  SIDEWAYS: { text: '震荡', color: '#f59e0b', advice: '中性（中等仓位）' },
  RISK: { text: '风险', color: '#8b5cf6', advice: '空仓/防御' }
}

const regimeInfo = computed(() => {
  if (!record.value) return null
  return REGIME_LABEL[record.value.regime] || { text: record.value.regime, color: '#6b7280', advice: '-' }
})

async function fetchLatest() {
  loading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/market-regime/latest`)
    if (response.data && response.data.state === 0) {
      record.value = response.data.data
    } else {
      record.value = null
      ElMessage.info('暂无市场状态记录')
    }
  } catch (error) {
    console.error('获取市场状态失败:', error)
    ElMessage.error('获取市场状态失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(fetchLatest)
</script>

<template>
  <div class="regime-page" v-loading="loading">
    <el-card shadow="hover" class="regime-card">
      <template #header>
        <h2 class="regime-title">市场温度计</h2>
      </template>
      <template v-if="record">
        <div class="regime-main">
          <div class="regime-badge" :style="{ backgroundColor: regimeInfo.color }">
            {{ regimeInfo.text }}
          </div>
          <div class="regime-advice">{{ regimeInfo.advice }}</div>
          <div class="regime-date">{{ record.date }}</div>
        </div>
        <el-divider />
        <div class="report-box">{{ record.report }}</div>
        <el-divider />
        <div class="indicators" v-if="record.indicators">
          <div class="indicator-item">MA20：{{ record.indicators.ma20 }}</div>
          <div class="indicator-item">MA60：{{ record.indicators.ma60 }}</div>
          <div class="indicator-item">MA250：{{ record.indicators.ma250 }}</div>
          <div class="indicator-item">量比：{{ record.indicators.volumeRatio }}</div>
          <div class="indicator-item">波动率：{{ (record.indicators.volatility * 100).toFixed(2) }}%</div>
          <div class="indicator-item">PE分位：{{ record.indicators.pePercentile }}</div>
          <div class="indicator-item">PB分位：{{ record.indicators.pbPercentile }}</div>
        </div>
      </template>
      <el-empty v-else description="暂无市场状态记录" />
    </el-card>
  </div>
</template>

<style scoped>
.regime-page {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
}
.regime-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}
.regime-main {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}
.regime-badge {
  color: #fff;
  font-size: 1.5rem;
  font-weight: 700;
  padding: 12px 28px;
  border-radius: 12px;
}
.regime-advice {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
}
.regime-date {
  margin-left: auto;
  color: #6b7280;
}
.report-box {
  background-color: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  color: #374151;
  line-height: 1.6;
}
.indicators {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
}
.indicator-item {
  color: #4b5563;
  font-size: 0.875rem;
}
</style>
```

- [x] **Step 2: 注册路由**

在 `StockAI-Console/src/router/index.js` 中，import 区加：

```js
import MarketRegime from '../components/MarketRegime.vue'
```

在 `routes` 数组的 `Config` 路由之后加：

```js
    {
      path: '/regime',
      name: '温度计',
      component: MarketRegime
    }
```

- [x] **Step 3: 在侧边栏新增菜单项**

在 `StockAI-Console/src/App.vue` 的 `el-menu` 中（`配置` 菜单项之后）加：

```html
          <el-menu-item index="/regime">
            <el-icon><Setting /></el-icon>
            <span>温度计</span>
          </el-menu-item>
```

> 注：`Setting` 图标已在 `App.vue` 顶部 import 列表中存在，直接复用；若想用温度计专属图标可换 `Odometer`，需在 import 中补充。

- [x] **Step 4: 构建验证**

Run: `cd StockAI-Console && npm run build`
Expected: 构建成功无报错。

- [x] **Step 5: 手动验证（可选，需后端已启动并写入记录）**

Run: `cd StockAI-Console && npm run dev`，访问 `http://localhost:8889/regime`
Expected: 展示当前四态徽章 + 仓位建议 + 报告 + 指标。

- [x] **Step 6: 提交**

```bash
git add StockAI-Console/src/components/MarketRegime.vue StockAI-Console/src/router/index.js StockAI-Console/src/App.vue
git commit -m "feat(console): 新增市场温度计页面展示当前状态与仓位建议"
```

---

### Task 9: StockAI-Console — 历史状态记录查询与展示

**Files:**
- Modify: `StockAI-Console/src/components/MarketRegime.vue`

**Interfaces:**
- Consumes: `POST /market-regime/history?limit=30`（返回 `Result<List<MarketRegimeRecord>>`，按 `date` 倒序）。

- [x] **Step 1: 在 MarketRegime.vue 追加历史列表逻辑与模板**

在 `<script setup>` 中（`record` 定义之后）追加：

```js
const history = ref([])
const historyLoading = ref(false)

async function fetchHistory() {
  historyLoading.value = true
  try {
    const response = await axios.post(`${BASE_URL}/market-regime/history?limit=30`)
    if (response.data && response.data.state === 0 && Array.isArray(response.data.data)) {
      history.value = response.data.data
    } else {
      history.value = []
    }
  } catch (error) {
    console.error('获取历史状态失败:', error)
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

function regimeText(regime) {
  return (REGIME_LABEL[regime] || { text: regime }).text
}
```

将 `onMounted(fetchLatest)` 改为：

```js
onMounted(() => {
  fetchLatest()
  fetchHistory()
})
```

在模板 `</el-card>` 之前（`el-empty` 之后）追加历史表格：

```html
    <el-card shadow="hover" class="history-card" v-loading="historyLoading">
      <template #header>
        <h2 class="regime-title">历史记录</h2>
      </template>
      <el-table :data="history" stripe>
        <el-table-column prop="date" label="日期" />
        <el-table-column label="状态">
          <template #default="scope">{{ regimeText(scope.row.regime) }}</template>
        </el-table-column>
        <el-table-column prop="positionAdvice" label="仓位建议" />
        <el-table-column prop="report" label="报告" show-overflow-tooltip />
      </el-table>
    </el-card>
```

> 注意：上面的历史表格需放在当前状态 `el-card` 之外、最外层 `.regime-page` 容器之内（当前模板中 `el-card` 是 `regime-page` 的唯一子元素，实现时把两个 `el-card` 并列放入 `.regime-page`）。

在 `<style scoped>` 追加：

```css
.history-card {
  margin-top: 1.5rem;
}
```

- [x] **Step 2: 构建验证**

Run: `cd StockAI-Console && npm run build`
Expected: 构建成功无报错。

- [x] **Step 3: 手动验证（可选）**

Run: `cd StockAI-Console && npm run dev`，访问 `http://localhost:8889/regime`
Expected: 底部历史表格按 `date` 倒序展示记录。

- [x] **Step 4: 提交**

```bash
git add StockAI-Console/src/components/MarketRegime.vue
git commit -m "feat(console): 温度计页新增历史状态记录倒序查询与展示"
```

---

## Self-Review 记录

- **Spec 覆盖**：1.1/1.2 → Task 1/2；2.1/2.2 → Task 4/5；3.1 → Task 3/5；4.1/4.2 → Task 6；5.1/5.2 → Task 5/7；6.1/6.2 → Task 8/9。设计文档四态打分制、仓位映射表、AI 降级、飞书失败不影响记录、cron `0 30 15 * * ?`、阈值抽配置均已落地。
- **占位符扫描**：无 TBD/TODO；所有代码步骤含完整代码。
- **类型一致性**：`RegimeIndicators` 字段、`determineRegime(ind, volatilityHigh, valuationHigh)`、`mapPositionAdvice(regime)`、`calculateIndicators(indexCode)`、`evaluateAndSave()`、`latest()`、`history(limit)`、repository 方法签名跨任务一致；Python 侧 `compute_percentile(history, value)`、`IndexValuation` 字段与 Java `IndexValuation` PO 对齐（snake_case 映射）。

PLAN_PATH: docs/superpowers/plans/2026-08-30-market-regime.md
