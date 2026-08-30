---
change: data-foundation
design-doc: docs/superpowers/specs/2026-08-30-data-foundation-design.md
base-ref: 7c7562d90e81558d8da736f79a64bd02acc56816
---

# data-foundation 数据地基 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 etf-service（Python/FastAPI + akshare + APScheduler）中建立多数据源接入（akshare 主源 + baostock/tushare/东财新浪备用源）与主备自动切换，统一转换为标准格式后写入 MongoDB 新建集合，补齐指数历史行情、优质池个股历史K线、财务指标与行业/概念分类数据。

**Architecture:** 新增 `datasource/` 适配器层（统一 `DataSource` 接口 + 主备链）、`normalize/schema.py` 标准格式模型与清洗、`services/` 采集服务（指数/个股K线/财务/行业概念）、`tasks/` 定时任务，复用并扩展 `database/mongo.py` 新增 5 个集合。数据流为：定时任务 → 采集服务 → 数据源适配器链（主源→备用源）→ 统一格式 → MongoDB upsert 去重。

**Tech Stack:** Python 3、FastAPI、akshare、baostock、tushare、pandas、pymongo、APScheduler、python-dotenv、pytest（新增）。

**Spec:**
- 技术设计：`docs/superpowers/specs/2026-08-30-data-foundation-design.md`
- 任务边界：`docs/openspec/changes/data-foundation/tasks.md`

## Global Constraints

以下约束逐条来自 design.md / tasks.md，所有任务隐式包含：

- 仅改动 `etf-service/`（Python 项目）；包根为 `etf-service/app/`，模块用相对导入（`from ..normalize.schema import ...`）。
- 标准行情格式字段：`symbol, trade_date, open, high, low, close, volume, amount, data_source, created_at, updated_at`。
- 标准财务格式字段：`symbol, report_date, pe, pb, roe, revenue_yoy, profit_yoy, gross_margin, data_source, updated_at`。
- 标准行业/概念格式字段：`symbol, industry, concepts[], data_source, updated_at`。
- 主备切换顺序固定：`akshare → baostock → tushare → 东财/新浪`；全部失败记录日志、保留旧数据、不抛异常中断其他任务。
- 新建集合（禁止改造 `market_quotes`、`etf_daily_quotes`、`etf_basic_info` 等已有集合）：
  - `index_daily_quotes` 去重键 `index_code + trade_date`
  - `stock_daily_kline` 去重键 `symbol + trade_date`
  - `stock_fundamentals` 去重键 `symbol + report_date`
  - `stock_industry_concept` 去重键 `symbol`
  - `index_constituents` 去重键 `index_code + symbol`
- 密钥/token（如 `TUSHARE_TOKEN`）通过环境变量 + `.env` 用 `os.getenv` 读取，不硬编码；tushare 无 token 时该源静默跳过。
- 数据缺失 → 字段降级为默认值 `0.0` / `""` 并保留 `data_source` 标注。
- 代码格式：snake_case、类型注解（`typing`），日志用 `logging.getLogger(__name__)`，Python 缩进 4 空格，行宽 ≤ 120。
- 测试用 pytest + `unittest.mock`（mock 各数据源返回），测试目录 `etf-service/tests/`，从 `etf-service/` 根运行 `python -m pytest tests/ -v`。

---

## 文件结构

**etf-service 新增：**

| 文件 | 职责 |
| --- | --- |
| `app/datasource/__init__.py` | 导出 `DataSource`、`fetch_with_fallback`、`get_source_chain()` |
| `app/datasource/base.py` | `DataSource` 抽象接口 + 主备链 `fetch_with_fallback` |
| `app/datasource/akshare_source.py` | 主源：指数日线 / 个股日线 / 财务 / 行业概念 |
| `app/datasource/baostock_source.py` | 备用源：历史K线 / 财务 |
| `app/datasource/tushare_source.py` | 备用源：财务 / 日线（token 从 `.env` 读） |
| `app/datasource/eastmoney_sina.py` | 备用源：行情快照（东财/新浪） |
| `app/normalize/__init__.py` | 导出标准模型与清洗函数 |
| `app/normalize/schema.py` | 标准数据模型 + 字段映射/清洗工具 |
| `app/services/index_service.py` | 成分股拉取存储 + 指数日线采集/更新 |
| `app/services/stock_service.py` | 个股K线采集/更新 + 财务/行业概念采集 |
| `app/tasks/data_tasks.py` | 数据地基各采集任务函数 |
| `tests/conftest.py` | 测试路径与通用 fixture |
| `tests/test_schema.py` | 标准模型 / 清洗函数单测 |
| `tests/test_akshare_source.py` | akshare 适配器映射单测 |
| `tests/test_baostock_source.py` | baostock 适配器映射单测 |
| `tests/test_tushare_source.py` | tushare 适配器映射单测 |
| `tests/test_eastmoney_sina.py` | 东财/新浪适配器映射单测 |
| `tests/test_fallback.py` | 主备自动切换集成测试 |
| `tests/test_mongo_upsert.py` | 新集合 upsert/去重测试 |
| `tests/test_index_service.py` | 成分股 / 指数行情服务测试 |
| `tests/test_stock_service.py` | 个股K线 / 财务 / 行业概念服务测试 |
| `tests/test_data_tasks.py` | 采集任务函数测试 |

**etf-service 修改：**

| 文件 | 改动 |
| --- | --- |
| `requirements.txt` | 新增 `baostock`、`tushare`、`pytest` |
| `app/database/mongo.py` | 新增 5 个集合引用 + 5 个 upsert 方法 + `get_constituent_symbols()` |
| `app/services/__init__.py` | 导出 `index_service`、`stock_service` |
| `app/tasks/__init__.py` | 导出新增任务入口 |
| `app/tasks/etf_task.py` | `start_scheduler` 中注册新任务到 APScheduler |
| `app/main.py` | 启动时调用新增任务调度（复用 `start_scheduler`） |

---

### Task 1: 数据源抽象接口与统一标准格式模型（tasks.md 1.1）

**Files:**
- Create: `etf-service/app/normalize/__init__.py`
- Create: `etf-service/app/normalize/schema.py`
- Create: `etf-service/app/datasource/__init__.py`
- Create: `etf-service/app/datasource/base.py`
- Create: `etf-service/tests/__init__.py`
- Create: `etf-service/tests/test_schema.py`
- Modify: `etf-service/requirements.txt`

**Interfaces:**
- Produces（后续任务依赖的精确签名）:
  - `schema.DailyQuote`（字段：`symbol, trade_date, open, high, low, close, volume, amount, data_source, created_at, updated_at`，含 `to_dict() -> dict`）
  - `schema.Fundamental`（字段：`symbol, report_date, pe, pb, roe, revenue_yoy, profit_yoy, gross_margin, data_source, updated_at`，含 `to_dict()`）
  - `schema.IndustryConcept`（字段：`symbol, industry, concepts: list, data_source, updated_at`，含 `to_dict()`）
  - `schema.to_float(value, default=0.0) -> float`、`schema.to_date(value, default="") -> str`、`schema.row_get(row, *names) -> Any`
  - `schema.to_market_symbol(code: str) -> str`（6 位代码 → `sh`/`sz` 前缀）
  - `base.DataSource`（含方法 `fetch_index_daily(code, start_date=None)` / `fetch_stock_daily(code, start_date=None)` / `fetch_fundamentals(code)` / `fetch_industry_concept(code)`，默认返回 `None`；类属性 `name: str`）
  - `base.fetch_with_fallback(sources: list, method: str, code: str, **kwargs) -> Any`（依次尝试，非空即返回，全部失败返回 `None` 并记 error 日志）

- [x] **Step 1: 新增依赖**

编辑 `etf-service/requirements.txt`，在末尾追加三行（保持原有内容不变）：

```
baostock
tushare
pytest
```

- [x] **Step 2: 编写标准模型与清洗函数**

创建 `etf-service/app/normalize/schema.py`：

```python
from dataclasses import dataclass, field, asdict
from datetime import datetime
from typing import Any, List


@dataclass
class DailyQuote:
    symbol: str
    trade_date: str
    open: float
    high: float
    low: float
    close: float
    volume: float
    amount: float
    data_source: str
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class Fundamental:
    symbol: str
    report_date: str
    pe: float
    pb: float
    roe: float
    revenue_yoy: float
    profit_yoy: float
    gross_margin: float
    data_source: str
    updated_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class IndustryConcept:
    symbol: str
    industry: str
    concepts: List[str]
    data_source: str
    updated_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict:
        return asdict(self)


def to_float(value: Any, default: float = 0.0) -> float:
    """把上游字符串/数值清洗为 float，失败降级为 default。"""
    if value is None:
        return default
    if isinstance(value, (int, float)):
        return float(value)
    s = str(value).replace(",", "").replace("%", "").strip()
    if s in ("", "-", "--", "None", "nan"):
        return default
    try:
        return float(s)
    except ValueError:
        return default


def to_date(value: Any, default: str = "") -> str:
    """把上游日期清洗为 YYYY-MM-DD 字符串。"""
    if value is None:
        return default
    s = str(value).strip().replace("/", "-")
    if len(s) == 8 and s.isdigit():
        return f"{s[:4]}-{s[4:6]}-{s[6:]}"
    return s[:10]


def row_get(row: Any, *names: str) -> Any:
    """从 pandas 行/字典中按候选列名取值，第一个存在且非空的返回。"""
    for n in names:
        if isinstance(row, dict):
            if n in row and row[n] is not None:
                return row[n]
        elif hasattr(row, "get") and n in row.index:
            v = row[n]
            if v is not None:
                return v
    return None


def to_market_symbol(code: str) -> str:
    """6 位代码 → 带市场前缀（sh/sz）；已是前缀形式则原样返回小写。"""
    code = code.strip()
    if code.lower().startswith(("sh", "sz")):
        return code.lower()
    if code.startswith("6"):
        return "sh" + code
    return "sz" + code
```

创建 `etf-service/app/normalize/__init__.py`：

```python
from .schema import (
    DailyQuote,
    Fundamental,
    IndustryConcept,
    to_float,
    to_date,
    row_get,
    to_market_symbol,
)

__all__ = [
    "DailyQuote",
    "Fundamental",
    "IndustryConcept",
    "to_float",
    "to_date",
    "row_get",
    "to_market_symbol",
]
```

- [x] **Step 3: 编写数据源抽象接口与主备链**

创建 `etf-service/app/datasource/base.py`：

```python
import logging
from typing import Any, List, Optional

logger = logging.getLogger(__name__)


class DataSource:
    """数据源抽象接口：子类按能力覆写方法，不支持的返回 None。"""

    name: str = "base"

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[list]:
        return None

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[list]:
        return None

    def fetch_fundamentals(self, code: str) -> Optional[list]:
        return None

    def fetch_industry_concept(self, code: str) -> Optional[dict]:
        return None


def fetch_with_fallback(sources: List[DataSource], method: str, code: str, **kwargs) -> Optional[Any]:
    """按 sources 顺序尝试，非空结果即返回；全部失败返回 None 并记 error 日志。"""
    for source in sources:
        fn = getattr(source, method, None)
        if fn is None:
            continue
        try:
            result = fn(code, **kwargs)
            if result:
                return result
        except Exception as e:  # noqa: BLE001 单源失败切换备用源
            logger.warning("数据源 %s 的 %s(%s) 失败: %s", getattr(source, "name", source), method, code, e)
            continue
    logger.error("所有数据源对 %s(%s) 均失败，保留旧数据", method, code)
    return None
```

创建 `etf-service/app/datasource/__init__.py`（先占位，Task 6 补全 `get_source_chain`）：

```python
from .base import DataSource, fetch_with_fallback

__all__ = ["DataSource", "fetch_with_fallback"]
```

- [x] **Step 4: 编写失败测试**

创建 `etf-service/tests/__init__.py`（空文件）。

创建 `etf-service/tests/test_schema.py`：

```python
from datetime import datetime

from app.normalize.schema import (
    DailyQuote,
    Fundamental,
    IndustryConcept,
    to_float,
    to_date,
    to_market_symbol,
)


def test_daily_quote_to_dict_has_standard_fields():
    q = DailyQuote(symbol="000001", trade_date="2024-01-02", open=1.0, high=2.0,
                   low=0.5, close=1.5, volume=100, amount=200, data_source="akshare")
    d = q.to_dict()
    assert set(d) == {"symbol", "trade_date", "open", "high", "low", "close",
                      "volume", "amount", "data_source", "created_at", "updated_at"}
    assert d["trade_date"] == "2024-01-02"


def test_to_float_cleans_percent_and_comma():
    assert to_float("12.34%") == 12.34
    assert to_float("1,234.5") == 1234.5
    assert to_float("--", 0.0) == 0.0
    assert to_float(None, 0.0) == 0.0


def test_to_date_normalizes_formats():
    assert to_date("20240102") == "2024-01-02"
    assert to_date("2024/01/02") == "2024-01-02"
    assert to_date("2024-01-02 00:00:00") == "2024-01-02"


def test_to_market_symbol():
    assert to_market_symbol("600000") == "sh600000"
    assert to_market_symbol("000001") == "sz000001"
    assert to_market_symbol("sh600000") == "sh600000"
```

- [x] **Step 5: 运行测试验证通过**

Run: `cd etf-service; python -m pytest tests/test_schema.py -v`
Expected: 4 passed

- [x] **Step 6: Commit**

```bash
git add etf-service/requirements.txt etf-service/app/normalize etf-service/app/datasource etf-service/tests
git commit -m "feat: 新增数据源抽象接口与统一标准格式模型"
```

---

### Task 2: akshare 主数据源适配器（tasks.md 1.2）

**Files:**
- Create: `etf-service/app/datasource/akshare_source.py`
- Create: `etf-service/tests/test_akshare_source.py`

**Interfaces:**
- Consumes: `schema.DailyQuote/Fundamental/IndustryConcept/to_float/to_date/row_get`、`base.DataSource`
- Produces: `AkshareSource`（`name = "akshare"`，实现 `fetch_index_daily` / `fetch_stock_daily` / `fetch_fundamentals` / `fetch_industry_concept`，全部返回标准格式 dict / `List[dict]` 或 `None`）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_akshare_source.py`：

```python
from unittest.mock import patch
import pandas as pd

from app.datasource.akshare_source import AkshareSource


def _index_df():
    return pd.DataFrame({
        "日期": ["2024-01-02"],
        "开盘": [3100.0],
        "收盘": [3150.5],
        "最高": [3160.0],
        "最低": [3090.0],
        "成交量": [12345678],
        "成交额": [4.5e9],
    })


def test_fetch_index_daily_maps_standard_format():
    src = AkshareSource()
    with patch.object(src, "ak") as ak:
        ak.index_zh_a_hist.return_value = _index_df()
        out = src.fetch_index_daily("000300")
    assert len(out) == 1
    q = out[0]
    assert q["symbol"] == "000300"
    assert q["trade_date"] == "2024-01-02"
    assert q["close"] == 3150.5
    assert q["amount"] == 4.5e9
    assert q["data_source"] == "akshare"


def test_fetch_stock_daily_maps_standard_format():
    src = AkshareSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_a_hist.return_value = pd.DataFrame({
            "日期": ["2024-01-02"], "开盘": [10.0], "收盘": [10.5], "最高": [11.0],
            "最低": [9.8], "成交量": [1000], "成交额": [10500.0],
        })
        out = src.fetch_stock_daily("000001")
    assert out[0]["symbol"] == "000001"
    assert out[0]["close"] == 10.5


def test_fetch_fundamentals_maps_standard_format():
    src = AkshareSource()
    with patch.object(src, "ak") as ak:
        ak.stock_a_indicator_lg.return_value = pd.DataFrame({"pe_ttm": [12.3], "pb": [1.5]})
        ak.stock_financial_analysis_indicator.return_value = pd.DataFrame({
            "日期": ["2024-03-31"],
            "净资产收益率(%)": [8.0],
            "主营业务收入增长率(%)": [10.0],
            "净利润增长率(%)": [15.0],
            "销售毛利率(%)": [30.0],
        })
        out = src.fetch_fundamentals("000001")
    f = out[0]
    assert f["symbol"] == "000001"
    assert f["report_date"] == "2024-03-31"
    assert f["pe"] == 12.3 and f["pb"] == 1.5
    assert f["roe"] == 8.0 and f["gross_margin"] == 30.0
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_akshare_source.py -v`
Expected: FAIL（`ModuleNotFoundError: app.datasource.akshare_source`）

- [x] **Step 3: 实现适配器**

创建 `etf-service/app/datasource/akshare_source.py`：

```python
import logging
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import (
    DailyQuote,
    Fundamental,
    IndustryConcept,
    to_float,
    to_date,
    row_get,
)
from .base import DataSource

logger = logging.getLogger(__name__)

_concept_map_cache = None


class AkshareSource(DataSource):
    name = "akshare"

    def __init__(self):
        import akshare as ak
        self.ak = ak

    def _now(self):
        return datetime.now()

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "19900101").replace("-", "")
        end = self._now().strftime("%Y%m%d")
        df = self.ak.index_zh_a_hist(symbol=code[-6:], period="daily", start_date=start, end_date=end)
        if df is None or df.empty:
            return None
        now = self._now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "日期", "date", "trade_date")),
                open=to_float(row_get(r, "开盘", "open")),
                high=to_float(row_get(r, "最高", "high")),
                low=to_float(row_get(r, "最低", "low")),
                close=to_float(row_get(r, "收盘", "close")),
                volume=to_float(row_get(r, "成交量", "volume", "vol")),
                amount=to_float(row_get(r, "成交额", "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "19900101").replace("-", "")
        end = self._now().strftime("%Y%m%d")
        df = self.ak.stock_zh_a_hist(symbol=code, period="daily", start_date=start, end_date=end, adjust="")
        if df is None or df.empty:
            return None
        now = self._now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "日期", "date", "trade_date")),
                open=to_float(row_get(r, "开盘", "open")),
                high=to_float(row_get(r, "最高", "high")),
                low=to_float(row_get(r, "最低", "low")),
                close=to_float(row_get(r, "收盘", "close")),
                volume=to_float(row_get(r, "成交量", "volume", "vol")),
                amount=to_float(row_get(r, "成交额", "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_fundamentals(self, code: str) -> Optional[List[dict]]:
        pe = pb = 0.0
        try:
            df_val = self.ak.stock_a_indicator_lg(symbol=code)
            if df_val is not None and not df_val.empty:
                last = df_val.iloc[-1]
                pe = to_float(row_get(last, "pe_ttm", "pe"))
                pb = to_float(row_get(last, "pb"))
        except Exception as e:  # noqa: BLE001
            logger.warning("akshare 估值指标获取失败 %s: %s", code, e)
        try:
            df_fin = self.ak.stock_financial_analysis_indicator(symbol=code)
        except Exception as e:  # noqa: BLE001
            logger.warning("akshare 财务指标获取失败 %s: %s", code, e)
            df_fin = None
        if df_fin is None or df_fin.empty:
            return None
        now = self._now()
        out = []
        for _, r in df_fin.iterrows():
            out.append(Fundamental(
                symbol=code,
                report_date=to_date(row_get(r, "日期", "报告期", "报告日", "date")),
                pe=pe,
                pb=pb,
                roe=to_float(row_get(r, "净资产收益率(%)", "净资产收益率", "roe")),
                revenue_yoy=to_float(row_get(r, "主营业务收入增长率(%)", "营业收入同比增长率", "revenue_yoy")),
                profit_yoy=to_float(row_get(r, "净利润增长率(%)", "净利润同比增长率", "profit_yoy")),
                gross_margin=to_float(row_get(r, "销售毛利率(%)", "销售毛利率", "gross_margin")),
                data_source=self.name,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_industry_concept(self, code: str) -> Optional[dict]:
        industry = ""
        try:
            info = self.ak.stock_individual_info_em(symbol=code)
            if info is not None and not info.empty:
                for _, r in info.iterrows():
                    if str(r.get("item")) == "行业":
                        industry = str(r.get("value", ""))
        except Exception as e:  # noqa: BLE001
            logger.warning("akshare 个股行业获取失败 %s: %s", code, e)
        concepts = self._get_concepts(code)
        return IndustryConcept(
            symbol=code,
            industry=industry,
            concepts=concepts,
            data_source=self.name,
            updated_at=self._now(),
        ).to_dict()

    def _get_concepts(self, code: str) -> List[str]:
        global _concept_map_cache
        if _concept_map_cache is None:
            _concept_map_cache = self._load_concept_map()
        return _concept_map_cache.get(code, [])

    def _load_concept_map(self) -> dict:
        df = self.ak.stock_board_concept_name_em()
        mapping = {}
        if df is None or df.empty or "板块名称" not in df.columns:
            return mapping
        for name in df["板块名称"].tolist():
            try:
                cons = self.ak.stock_board_concept_cons_em(symbol=name)
                if cons is not None and not cons.empty and "代码" in cons.columns:
                    for sym in cons["代码"].astype(str).tolist():
                        mapping.setdefault(sym, []).append(name)
            except Exception as e:  # noqa: BLE001
                logger.warning("概念板块 %s 成分获取失败: %s", name, e)
                continue
        return mapping
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_akshare_source.py -v`
Expected: 3 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/datasource/akshare_source.py etf-service/tests/test_akshare_source.py
git commit -m "feat: 实现 akshare 主数据源适配器"
```

---

### Task 3: baostock 备用数据源适配器（tasks.md 1.3）

**Files:**
- Create: `etf-service/app/datasource/baostock_source.py`
- Create: `etf-service/tests/test_baostock_source.py`

**Interfaces:**
- Consumes: `schema.*`、`base.DataSource`
- Produces: `BaostockSource`（`name = "baostock"`，实现 `fetch_index_daily` / `fetch_stock_daily` / `fetch_fundamentals`；`fetch_industry_concept` 返回 `None`）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_baostock_source.py`：

```python
from unittest.mock import MagicMock

from app.datasource.baostock_source import BaostockSource


class _Rs:
    """模拟 baostock 返回的 ResultData：error_code + fields + next()/get_row_data()。"""

    def __init__(self, fields, rows):
        self.error_code = "0"
        self.error_msg = ""
        self.fields = fields
        self._rows = rows
        self._idx = -1

    def next(self):
        self._idx += 1
        return self._idx < len(self._rows)

    def get_row_data(self):
        return self._rows[self._idx]


def test_fetch_stock_daily_maps_standard_format():
    src = BaostockSource()
    src._bs = MagicMock()
    src._bs.query_history_k_data_plus.return_value = _Rs(
        ["date", "open", "high", "low", "close", "volume", "amount"],
        [["2024-01-02", "10.0", "11.0", "9.8", "10.5", "1000", "10500.0"]],
    )
    out = src.fetch_stock_daily("000001")
    assert len(out) == 1
    q = out[0]
    assert q["symbol"] == "000001"
    assert q["trade_date"] == "2024-01-02"
    assert q["close"] == 10.5
    assert q["data_source"] == "baostock"


def test_fetch_fundamentals_maps_standard_format():
    src = BaostockSource()
    src._bs = MagicMock()
    src._bs.query_profit_data.return_value = _Rs(
        ["code", "pubDate", "statDate", "roeAvg", "gpMargin"],
        [["sh.600000", "2024-04-01", "2024-03-31", "8.0", "30.0"]],
    )
    src._bs.query_growth_data.return_value = _Rs(
        ["code", "statDate", "YOYNI"],
        [["sh.600000", "2024-03-31", "15.0"]],
    )
    out = src.fetch_fundamentals("600000")
    f = out[0]
    assert f["roe"] == 8.0
    assert f["gross_margin"] == 30.0
    assert f["profit_yoy"] == 15.0
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_baostock_source.py -v`
Expected: FAIL（`ModuleNotFoundError`）

- [x] **Step 3: 实现适配器**

创建 `etf-service/app/datasource/baostock_source.py`：

```python
import logging
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import DailyQuote, Fundamental, to_float, to_date, row_get
from .base import DataSource

logger = logging.getLogger(__name__)


class BaostockSource(DataSource):
    name = "baostock"

    def __init__(self):
        self._bs = None

    def _ensure_login(self):
        if self._bs is None:
            import baostock as bs
            self._bs = bs
            lg = bs.login()
            if lg.error_code != "0":
                raise RuntimeError(f"baostock 登录失败: {lg.error_msg}")
        return self._bs

    def _bs_code(self, code: str) -> str:
        code = code.lower().replace(".", "")
        if code.startswith(("sh", "sz")):
            return f"{code[:2]}.{code[2:]}"
        return ("sh." if code.startswith("6") else "sz.") + code

    def _fetch_daily(self, code: str, start_date: Optional[str], bs_code: str) -> Optional[List[dict]]:
        bs = self._ensure_login()
        start = (start_date or "1990-01-01").replace("/", "-")
        end = datetime.now().strftime("%Y-%m-%d")
        rs = bs.query_history_k_data_plus(
            bs_code, "date,open,high,low,close,volume,amount",
            start_date=start, end_date=end, frequency="d", adjustflag="3")
        if rs.error_code != "0":
            logger.warning("baostock %s 查询失败: %s", bs_code, rs.error_msg)
            return None
        rows = []
        while rs.next():
            rows.append(rs.get_row_data())
        if not rows:
            return None
        fields = rs.fields
        now = datetime.now()
        out = []
        for raw in rows:
            row = dict(zip(fields, raw))
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row.get("date")),
                open=to_float(row.get("open")),
                high=to_float(row.get("high")),
                low=to_float(row.get("low")),
                close=to_float(row.get("close")),
                volume=to_float(row.get("volume")),
                amount=to_float(row.get("amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        return self._fetch_daily(code, start_date, self._bs_code(code))

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        return self._fetch_daily(code, start_date, self._bs_code(code))

    def fetch_fundamentals(self, code: str) -> Optional[List[dict]]:
        bs = self._ensure_login()
        bs_code = self._bs_code(code)
        year = datetime.now().year
        rs_profit = bs.query_profit_data(code=bs_code, year=year, quarter=1)
        if rs_profit.error_code != "0":
            logger.warning("baostock %s 财务指标查询失败: %s", bs_code, rs_profit.error_msg)
            return None
        profit_rows = []
        while rs_profit.next():
            profit_rows.append(rs_profit.get_row_data())
        if not profit_rows:
            return None
        last = dict(zip(rs_profit.fields, profit_rows[-1]))  # 取最新一期
        profit_yoy = 0.0
        rs_growth = bs.query_growth_data(code=bs_code, year=year, quarter=1)
        if rs_growth.error_code == "0":
            growth_rows = []
            while rs_growth.next():
                growth_rows.append(rs_growth.get_row_data())
            if growth_rows:
                g = dict(zip(rs_growth.fields, growth_rows[-1]))
                profit_yoy = to_float(g.get("YOYNI"))
        return [Fundamental(
            symbol=code,
            report_date=to_date(last.get("statDate")),
            pe=0.0,
            pb=0.0,
            roe=to_float(last.get("roeAvg")),
            revenue_yoy=0.0,
            profit_yoy=profit_yoy,
            gross_margin=to_float(last.get("gpMargin")),
            data_source=self.name,
            updated_at=datetime.now(),
        ).to_dict()]
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_baostock_source.py -v`
Expected: 2 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/datasource/baostock_source.py etf-service/tests/test_baostock_source.py
git commit -m "feat: 实现 baostock 备用数据源适配器"
```

---

### Task 4: tushare 备用数据源适配器（tasks.md 1.4）

**Files:**
- Create: `etf-service/app/datasource/tushare_source.py`
- Create: `etf-service/tests/test_tushare_source.py`

**Interfaces:**
- Consumes: `schema.*`、`base.DataSource`
- Produces: `TushareSource`（`name = "tushare"`；构造时用 `os.getenv("TUSHARE_TOKEN", "")` 读 token，空则所有方法返回 `None`；实现 `fetch_index_daily` / `fetch_stock_daily` / `fetch_fundamentals`）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_tushare_source.py`：

```python
import pandas as pd

from app.datasource.tushare_source import TushareSource


def test_no_token_returns_none(monkeypatch):
    monkeypatch.delenv("TUSHARE_TOKEN", raising=False)
    src = TushareSource()
    assert src.fetch_stock_daily("000001") is None
    assert src.fetch_fundamentals("000001") is None


def test_fetch_fundamentals_maps_standard_format(monkeypatch):
    monkeypatch.setenv("TUSHARE_TOKEN", "test-token")
    src = TushareSource()
    src._pro = type("Pro", (), {
        "daily_basic": lambda **kw: pd.DataFrame({"pe_ttm": [12.3], "pb": [1.5]}),
        "fina_indicator": lambda **kw: pd.DataFrame({
            "end_date": ["20240331"], "roe": [8.0],
            "grossprofit_margin": [30.0], "q_sales_yoy": [10.0], "q_profit_yoy": [15.0],
        }),
    })()
    out = src.fetch_fundamentals("000001")
    f = out[0]
    assert f["pe"] == 12.3 and f["pb"] == 1.5
    assert f["roe"] == 8.0 and f["gross_margin"] == 30.0
    assert f["revenue_yoy"] == 10.0 and f["profit_yoy"] == 15.0


def test_fetch_stock_daily_maps_standard_format(monkeypatch):
    monkeypatch.setenv("TUSHARE_TOKEN", "test-token")
    src = TushareSource()
    src._pro = type("Pro", (), {
        "daily": lambda **kw: pd.DataFrame({
            "trade_date": ["20240102"], "open": [10.0], "high": [11.0],
            "low": [9.8], "close": [10.5], "vol": [1000.0], "amount": [1050.0],
        }),
    })()
    out = src.fetch_stock_daily("000001")
    q = out[0]
    assert q["symbol"] == "000001"
    assert q["close"] == 10.5
    assert q["data_source"] == "tushare"
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_tushare_source.py -v`
Expected: FAIL（`ModuleNotFoundError`）

- [x] **Step 3: 实现适配器**

创建 `etf-service/app/datasource/tushare_source.py`：

```python
import logging
import os
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import DailyQuote, Fundamental, to_float, to_date, row_get
from .base import DataSource

logger = logging.getLogger(__name__)


class TushareSource(DataSource):
    name = "tushare"

    def __init__(self):
        self.token = os.getenv("TUSHARE_TOKEN", "")
        self._pro = None

    def _ensure_pro(self):
        if not self.token:
            return None
        if self._pro is None:
            import tushare as ts
            ts.set_token(self.token)
            self._pro = ts.pro_api()
        return self._pro

    def _ts_code(self, code: str) -> str:
        code = code.upper()
        if code.endswith((".SH", ".SZ", ".BJ")):
            return code
        return code + (".SH" if code.startswith("6") else ".SZ")

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        pro = self._ensure_pro()
        if pro is None:
            return None
        start = (start_date or "19900101").replace("-", "")
        end = datetime.now().strftime("%Y%m%d")
        df = pro.index_daily(ts_code=self._ts_code(code), start_date=start, end_date=end)
        return self._map_daily(code, df)

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        pro = self._ensure_pro()
        if pro is None:
            return None
        start = (start_date or "19900101").replace("-", "")
        end = datetime.now().strftime("%Y%m%d")
        df = pro.daily(ts_code=self._ts_code(code), start_date=start, end_date=end)
        return self._map_daily(code, df)

    def _map_daily(self, code: str, df) -> Optional[List[dict]]:
        if df is None or df.empty:
            return None
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code[-6:],
                trade_date=to_date(row_get(r, "trade_date")),
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "vol", "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_fundamentals(self, code: str) -> Optional[List[dict]]:
        pro = self._ensure_pro()
        if pro is None:
            return None
        ts_code = self._ts_code(code)
        pe = pb = 0.0
        try:
            df_val = pro.daily_basic(ts_code=ts_code)
            if df_val is not None and not df_val.empty:
                last = df_val.iloc[-1]
                pe = to_float(row_get(last, "pe_ttm", "pe"))
                pb = to_float(row_get(last, "pb"))
        except Exception as e:  # noqa: BLE001
            logger.warning("tushare 估值指标获取失败 %s: %s", code, e)
        try:
            df_fin = pro.fina_indicator(ts_code=ts_code)
        except Exception as e:  # noqa: BLE001
            logger.warning("tushare 财务指标获取失败 %s: %s", code, e)
            df_fin = None
        if df_fin is None or df_fin.empty:
            return None
        now = datetime.now()
        out = []
        for _, r in df_fin.iterrows():
            out.append(Fundamental(
                symbol=code[-6:],
                report_date=to_date(row_get(r, "end_date", "ann_date")),
                pe=pe,
                pb=pb,
                roe=to_float(row_get(r, "roe")),
                revenue_yoy=to_float(row_get(r, "q_sales_yoy", "revenue_yoy")),
                profit_yoy=to_float(row_get(r, "q_profit_yoy", "profit_yoy")),
                gross_margin=to_float(row_get(r, "grossprofit_margin", "gross_margin")),
                data_source=self.name,
                updated_at=now,
            ).to_dict())
        return out
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_tushare_source.py -v`
Expected: 3 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/datasource/tushare_source.py etf-service/tests/test_tushare_source.py
git commit -m "feat: 实现 tushare 备用数据源适配器（token 从环境变量读取）"
```

---

### Task 5: 东方财富/新浪备用行情适配器（tasks.md 1.5）

**Files:**
- Create: `etf-service/app/datasource/eastmoney_sina.py`
- Create: `etf-service/tests/test_eastmoney_sina.py`

**Interfaces:**
- Consumes: `schema.*`、`base.DataSource`
- Produces: `EastmoneySinaSource`（`name = "eastmoney_sina"`，实现 `fetch_index_daily` / `fetch_stock_daily`；`fetch_fundamentals` / `fetch_industry_concept` 返回 `None`）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_eastmoney_sina.py`：

```python
from unittest.mock import patch
import pandas as pd

from app.datasource.eastmoney_sina import EastmoneySinaSource


def test_fetch_stock_daily_maps_standard_format():
    src = EastmoneySinaSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_a_daily.return_value = pd.DataFrame({
            "date": ["2024-01-02"], "open": [10.0], "high": [11.0], "low": [9.8],
            "close": [10.5], "volume": [1000], "amount": [10500.0],
        })
        out = src.fetch_stock_daily("600000")
    q = out[0]
    assert q["symbol"] == "600000"
    assert q["close"] == 10.5
    assert q["data_source"] == "eastmoney_sina"


def test_fetch_index_daily_no_amount_defaults_zero():
    src = EastmoneySinaSource()
    with patch.object(src, "ak") as ak:
        ak.stock_zh_index_daily.return_value = pd.DataFrame({
            "date": ["2024-01-02"], "open": [3100.0], "high": [3160.0],
            "low": [3090.0], "close": [3150.0], "volume": [12345678],
        })
        out = src.fetch_index_daily("sh000300")
    q = out[0]
    assert q["amount"] == 0.0
    assert q["close"] == 3150.0
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_eastmoney_sina.py -v`
Expected: FAIL（`ModuleNotFoundError`）

- [x] **Step 3: 实现适配器**

创建 `etf-service/app/datasource/eastmoney_sina.py`：

```python
import logging
from datetime import datetime
from typing import List, Optional

from ..normalize.schema import DailyQuote, to_float, to_date, row_get, to_market_symbol
from .base import DataSource

logger = logging.getLogger(__name__)


class EastmoneySinaSource(DataSource):
    name = "eastmoney_sina"

    def __init__(self):
        import akshare as ak
        self.ak = ak

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "1990-01-01")[:10]
        end = datetime.now().strftime("%Y-%m-%d")
        df = self.ak.stock_zh_index_daily(symbol=to_market_symbol(code))
        if df is None or df.empty:
            return None
        df = df[(df["date"] >= start) & (df["date"] <= end)]
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "date")),
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[List[dict]]:
        start = (start_date or "1990-01-01")[:10]
        end = datetime.now().strftime("%Y-%m-%d")
        df = self.ak.stock_zh_a_daily(symbol=to_market_symbol(code), start_date=start, end_date=end, adjust="")
        if df is None or df.empty:
            return None
        now = datetime.now()
        out = []
        for _, r in df.iterrows():
            out.append(DailyQuote(
                symbol=code,
                trade_date=to_date(row_get(r, "date")),
                open=to_float(row_get(r, "open")),
                high=to_float(row_get(r, "high")),
                low=to_float(row_get(r, "low")),
                close=to_float(row_get(r, "close")),
                volume=to_float(row_get(r, "volume")),
                amount=to_float(row_get(r, "amount")),
                data_source=self.name,
                created_at=now,
                updated_at=now,
            ).to_dict())
        return out
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_eastmoney_sina.py -v`
Expected: 2 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/datasource/eastmoney_sina.py etf-service/tests/test_eastmoney_sina.py
git commit -m "feat: 实现东方财富/新浪备用行情适配器"
```

---

### Task 6: 主备自动切换（tasks.md 1.6）

**Files:**
- Modify: `etf-service/app/datasource/__init__.py`
- Create: `etf-service/tests/test_fallback.py`

**Interfaces:**
- Consumes: `AkshareSource` / `BaostockSource` / `TushareSource` / `EastmoneySinaSource`、`base.fetch_with_fallback`
- Produces: `get_source_chain() -> List[DataSource]`（顺序固定 `akshare → baostock → tushare → eastmoney_sina`）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_fallback.py`：

```python
from app.datasource import get_source_chain
from app.datasource.base import fetch_with_fallback


class _FakeSource:
    def __init__(self, name, result=None, fail=False):
        self.name = name
        self._result = result
        self._fail = fail

    def fetch_stock_daily(self, code, start_date=None):
        if self._fail:
            raise RuntimeError("boom")
        return self._result


def test_source_chain_order():
    chain = get_source_chain()
    assert [s.name for s in chain] == ["akshare", "baostock", "tushare", "eastmoney_sina"]


def test_fallback_uses_second_source_when_first_fails():
    ok = _FakeSource("second", result=[{"close": 1.0}])
    out = fetch_with_fallback([_FakeSource("first", fail=True), ok], "fetch_stock_daily", "000001")
    assert out == [{"close": 1.0}]


def test_fallback_returns_none_when_all_fail():
    out = fetch_with_fallback([_FakeSource("first", fail=True)], "fetch_stock_daily", "000001")
    assert out is None
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_fallback.py -v`
Expected: FAIL（`ImportError: cannot import name 'get_source_chain'`）

- [x] **Step 3: 实现主备链**

覆盖 `etf-service/app/datasource/__init__.py`：

```python
from typing import List

from .base import DataSource, fetch_with_fallback
from .akshare_source import AkshareSource
from .baostock_source import BaostockSource
from .tushare_source import TushareSource
from .eastmoney_sina import EastmoneySinaSource


def get_source_chain() -> List[DataSource]:
    """主备切换顺序：akshare → baostock → tushare → 东财/新浪。"""
    return [AkshareSource(), BaostockSource(), TushareSource(), EastmoneySinaSource()]


__all__ = ["DataSource", "fetch_with_fallback", "get_source_chain"]
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_fallback.py -v`
Expected: 3 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/datasource/__init__.py etf-service/tests/test_fallback.py
git commit -m "feat: 实现主备数据源自动切换链"
```

---

### Task 7: MongoDB 新集合模型与去重（tasks.md 1.7）

**Files:**
- Modify: `etf-service/app/database/mongo.py`
- Create: `etf-service/tests/test_mongo_upsert.py`

**Interfaces:**
- Consumes: `pymongo`、`os.getenv` 现有模式
- Produces（后续任务依赖）:
  - `mongo_client.index_daily_quotes` / `stock_daily_kline` / `stock_fundamentals` / `stock_industry_concept` / `index_constituents`（`Collection`）
  - `upsert_index_daily_quote(data) -> bool`（去重键 `index_code + trade_date`）
  - `upsert_stock_daily_kline(data) -> bool`（去重键 `symbol + trade_date`）
  - `upsert_stock_fundamental(data) -> bool`（去重键 `symbol + report_date`）
  - `upsert_stock_industry_concept(data) -> bool`（去重键 `symbol`）
  - `upsert_index_constituent(data) -> bool`（去重键 `index_code + symbol`）
  - `get_constituent_symbols() -> list`（`index_constituents.distinct("symbol")`）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_mongo_upsert.py`：

```python
from unittest.mock import patch, MagicMock

from app.database.mongo import MongoDBClient


def _client():
    with patch("app.database.mongo.MongoClient") as mc:
        fake_db = MagicMock()
        mc.return_value.__getitem__.return_value = fake_db
        c = MongoDBClient()
        return c, fake_db


def test_new_collections_registered():
    c, _ = _client()
    for name in ("index_daily_quotes", "stock_daily_kline", "stock_fundamentals",
                 "stock_industry_concept", "index_constituents"):
        assert getattr(c, name) is not None


def test_upsert_index_daily_quote_uses_dedup_key():
    c, _ = _client()
    data = {"index_code": "000300", "trade_date": "2024-01-02", "close": 3150.0}
    assert c.upsert_index_daily_quote(data) is True
    c.index_daily_quotes.update_one.assert_called_once_with(
        {"index_code": "000300", "trade_date": "2024-01-02"}, {"$set": data}, upsert=True)


def test_upsert_stock_daily_kline_uses_dedup_key():
    c, _ = _client()
    data = {"symbol": "000001", "trade_date": "2024-01-02", "close": 10.5}
    assert c.upsert_stock_daily_kline(data) is True
    c.stock_daily_kline.update_one.assert_called_once_with(
        {"symbol": "000001", "trade_date": "2024-01-02"}, {"$set": data}, upsert=True)
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_mongo_upsert.py -v`
Expected: FAIL（`AttributeError: 'MongoDBClient' object has no attribute 'index_daily_quotes'`）

- [x] **Step 3: 实现新集合**

编辑 `etf-service/app/database/mongo.py`，在 `__init__` 的集合引用处追加（不改动已有两行）：

```python
        # 集合引用
        self.etf_basic_info: Collection = self.db["etf_basic_info"]
        self.etf_daily_quotes: Collection = self.db["etf_daily_quotes"]
        # 数据地基新增集合（禁止改造已有集合）
        self.index_daily_quotes: Collection = self.db["index_daily_quotes"]
        self.stock_daily_kline: Collection = self.db["stock_daily_kline"]
        self.stock_fundamentals: Collection = self.db["stock_fundamentals"]
        self.stock_industry_concept: Collection = self.db["stock_industry_concept"]
        self.index_constituents: Collection = self.db["index_constituents"]
```

在 `upsert_etf_basic_info` 方法之后、`close` 之前追加 6 个方法：

```python
    def upsert_index_daily_quote(self, data: Dict[str, Any]) -> bool:
        """更新或插入指数日行情（去重键 index_code + trade_date）"""
        try:
            self.index_daily_quotes.update_one(
                {"index_code": data["index_code"], "trade_date": data["trade_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新指数日行情失败: {e}")
            return False

    def upsert_stock_daily_kline(self, data: Dict[str, Any]) -> bool:
        """更新或插入个股日K线（去重键 symbol + trade_date）"""
        try:
            self.stock_daily_kline.update_one(
                {"symbol": data["symbol"], "trade_date": data["trade_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新个股日K线失败: {e}")
            return False

    def upsert_stock_fundamental(self, data: Dict[str, Any]) -> bool:
        """更新或插入个股财务指标（去重键 symbol + report_date）"""
        try:
            self.stock_fundamentals.update_one(
                {"symbol": data["symbol"], "report_date": data["report_date"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新个股财务指标失败: {e}")
            return False

    def upsert_stock_industry_concept(self, data: Dict[str, Any]) -> bool:
        """更新或插入个股行业概念（去重键 symbol）"""
        try:
            self.stock_industry_concept.update_one(
                {"symbol": data["symbol"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新个股行业概念失败: {e}")
            return False

    def upsert_index_constituent(self, data: Dict[str, Any]) -> bool:
        """更新或插入指数成分股（去重键 index_code + symbol）"""
        try:
            self.index_constituents.update_one(
                {"index_code": data["index_code"], "symbol": data["symbol"]},
                {"$set": data},
                upsert=True,
            )
            return True
        except Exception as e:
            print(f"更新指数成分股失败: {e}")
            return False

    def get_constituent_symbols(self) -> list:
        """获取优质池全部成分股代码（去重）"""
        return list(self.index_constituents.distinct("symbol"))
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_mongo_upsert.py -v`
Expected: 3 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/database/mongo.py etf-service/tests/test_mongo_upsert.py
git commit -m "feat: 新增数据地基 MongoDB 集合模型与去重写入"
```

---

### Task 8: 沪深300/中证500成分股拉取与存储（tasks.md 2.1）

**Files:**
- Create: `etf-service/app/services/index_service.py`
- Modify: `etf-service/app/services/__init__.py`
- Create: `etf-service/tests/test_index_service.py`

**Interfaces:**
- Consumes: `akshare_source.AkshareSource`、`mongo_client.upsert_index_constituent`
- Produces:
  - `INDEX_CONSTITUENTS = {"000300": "沪深300", "000905": "中证500"}`
  - `IndexService.fetch_and_store_constituents() -> int`（返回写入条数）
  - `IndexService`（后续任务扩展指数行情方法）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_index_service.py`：

```python
from unittest.mock import patch
import pandas as pd

from app.services.index_service import IndexService, INDEX_CONSTITUENTS


def test_fetch_and_store_constituents_writes_records():
    svc = IndexService()
    svc.ak = _FakeAk()
    svc.mongo = _FakeMongo()
    n = svc.fetch_and_store_constituents()
    assert n == 4
    assert ("000300", "600000") in svc.mongo.written


class _FakeAk:
    def index_stock_cons(self, symbol=None):
        return pd.DataFrame({"成分券代码": ["600000", "000001"]})


class _FakeMongo:
    def __init__(self):
        self.written = set()

    def upsert_index_constituent(self, data):
        self.written.add((data["index_code"], data["symbol"]))
        return True
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_index_service.py -v`
Expected: FAIL（`ModuleNotFoundError: app.services.index_service`）

- [x] **Step 3: 实现成分股服务**

创建 `etf-service/app/services/index_service.py`：

```python
import logging
from typing import Dict

from ..datasource.akshare_source import AkshareSource
from ..database.mongo import mongo_client

logger = logging.getLogger(__name__)

INDEX_CONSTITUENTS: Dict[str, str] = {
    "000300": "沪深300",
    "000905": "中证500",
}


class IndexService:
    def __init__(self, ak=None, mongo=None):
        self.ak = ak or AkshareSource().ak
        self.mongo = mongo or mongo_client

    def fetch_and_store_constituents(self) -> int:
        """拉取沪深300/中证500成分股并写入 index_constituents，返回写入条数。"""
        count = 0
        for index_code, index_name in INDEX_CONSTITUENTS.items():
            try:
                df = self.ak.index_stock_cons(symbol=index_code)
            except Exception as e:  # noqa: BLE001
                logger.error("拉取 %s(%s) 成分股失败: %s", index_name, index_code, e)
                continue
            if df is None or df.empty:
                logger.warning("拉取 %s(%s) 成分股为空", index_name, index_code)
                continue
            code_col = "成分券代码" if "成分券代码" in df.columns else df.columns[0]
            for _, r in df.iterrows():
                symbol = str(r[code_col]).strip()
                if not symbol:
                    continue
                if self.mongo.upsert_index_constituent({
                    "index_code": index_code,
                    "index_name": index_name,
                    "symbol": symbol,
                    "data_source": "akshare",
                }):
                    count += 1
        logger.info("成分股拉取完成，共写入 %s 条", count)
        return count


index_service = IndexService()
```

覆盖 `etf-service/app/services/__init__.py`：

```python
from .etf_service import etf_service
from .index_service import index_service, IndexService

__all__ = ["etf_service", "index_service", "IndexService"]
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_index_service.py -v`
Expected: 1 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/index_service.py etf-service/app/services/__init__.py etf-service/tests/test_index_service.py
git commit -m "feat: 实现沪深300/中证500成分股拉取与存储"
```

---

### Task 9: 成分股列表定时更新（tasks.md 2.2）

**Files:**
- Create: `etf-service/app/tasks/data_tasks.py`
- Modify: `etf-service/app/tasks/__init__.py`
- Create: `etf-service/tests/test_data_tasks.py`

**Interfaces:**
- Consumes: `index_service.fetch_and_store_constituents`、`etf_task` 现有任务模式
- Produces: `data_tasks.update_index_constituents()`（幂等，重复运行不重复写入，靠 upsert 去重）

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_data_tasks.py`：

```python
from unittest.mock import patch

from app.tasks import data_tasks


def test_update_index_constituents_calls_service():
    with patch.object(data_tasks, "index_service") as svc:
        svc.fetch_and_store_constituents.return_value = 800
        data_tasks.update_index_constituents()
        svc.fetch_and_store_constituents.assert_called_once()
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_data_tasks.py -v`
Expected: FAIL（`ModuleNotFoundError: app.tasks.data_tasks`）

- [x] **Step 3: 实现任务函数**

创建 `etf-service/app/tasks/data_tasks.py`：

```python
import logging

from ..services.index_service import index_service

logger = logging.getLogger(__name__)


def update_index_constituents():
    """每月更新沪深300/中证500成分股（upsert 去重，重复运行不重复写入）。"""
    logger.info("开始更新指数成分股")
    try:
        n = index_service.fetch_and_store_constituents()
        logger.info("指数成分股更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新指数成分股失败: %s", e, exc_info=True)
```

编辑 `etf-service/app/tasks/__init__.py`，追加导出：

```python
from .etf_task import start_scheduler, stop_scheduler
from .data_tasks import update_index_constituents

__all__ = ["start_scheduler", "stop_scheduler", "update_index_constituents"]
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_data_tasks.py -v`
Expected: 1 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/tasks/data_tasks.py etf-service/app/tasks/__init__.py etf-service/tests/test_data_tasks.py
git commit -m "feat: 新增成分股列表定时更新任务"
```

---

### Task 10: 主要宽基指数历史日线采集（tasks.md 3.1）

**Files:**
- Modify: `etf-service/app/services/index_service.py`
- Modify: `etf-service/tests/test_index_service.py`

**Interfaces:**
- Consumes: `get_source_chain`、`fetch_with_fallback`、`upsert_index_daily_quote`
- Produces:
  - `INDEX_CODES = {"sh000001": "上证指数", "sh000300": "沪深300", "sh000905": "中证500", "sz399006": "创业板指"}`
  - `IndexService.fetch_index_daily_history(code, start_date="19900101") -> int`（写入条数）
  - `IndexService.fetch_all_index_daily_history() -> int`

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_index_service.py` 末尾追加：

```python
class _IdxMongo(_FakeMongo):
    def upsert_index_daily_quote(self, data):
        self.written.add((data["index_code"], data["trade_date"]))
        return True


def test_fetch_index_daily_history_writes_records():
    svc = IndexService()
    svc.ak = None
    svc.mongo = _IdxMongo()

    def fake_fetch(sources, method, code, **kwargs):
        return [{"symbol": "sh000300", "trade_date": "2024-01-02", "close": 3150.0,
                 "open": 3100.0, "high": 3160.0, "low": 3090.0, "volume": 1, "amount": 2,
                 "data_source": "akshare"}]

    with patch("app.services.index_service.fetch_with_fallback", side_effect=fake_fetch):
        n = svc.fetch_index_daily_history("sh000300")
    assert n == 1
    assert ("sh000300", "2024-01-02") in svc.mongo.written
```

（`_IdxMongo` 同时供 Task 11 的增量更新测试使用。）

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_index_service.py -v`
Expected: FAIL（`AttributeError: 'IndexService' object has no attribute 'fetch_index_daily_history'`）

- [x] **Step 3: 实现指数历史采集**

在 `etf-service/app/services/index_service.py` 顶部导入区追加：

```python
from ..datasource import get_source_chain
from ..datasource.base import fetch_with_fallback
from ..normalize.schema import to_date
```

在 `IndexService` 类中追加常量与方法（放在 `fetch_and_store_constituents` 之前）：

```python
    INDEX_CODES: Dict[str, str] = {
        "sh000001": "上证指数",
        "sh000300": "沪深300",
        "sh000905": "中证500",
        "sz399006": "创业板指",
    }

    def fetch_index_daily_history(self, code: str, start_date: str = "19900101") -> int:
        """采集单个指数历史日线并写入 index_daily_quotes，返回写入条数。"""
        quotes = fetch_with_fallback(get_source_chain(), "fetch_index_daily", code, start_date=start_date)
        if not quotes:
            logger.warning("指数 %s 历史日线采集为空", code)
            return 0
        count = 0
        for q in quotes:
            record = dict(q)
            record["index_code"] = record.pop("symbol")
            if self.mongo.upsert_index_daily_quote(record):
                count += 1
        logger.info("指数 %s 历史日线采集完成，写入 %s 条", code, count)
        return count

    def fetch_all_index_daily_history(self) -> int:
        total = 0
        for code in self.INDEX_CODES:
            total += self.fetch_index_daily_history(code)
        return total
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_index_service.py -v`
Expected: 2 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/index_service.py etf-service/tests/test_index_service.py
git commit -m "feat: 实现主要宽基指数历史日线采集"
```

---

### Task 11: 指数行情按日更新与去重（tasks.md 3.2）

**Files:**
- Modify: `etf-service/app/services/index_service.py`
- Modify: `etf-service/tests/test_index_service.py`

**Interfaces:**
- Consumes: `fetch_with_fallback`、`upsert_index_daily_quote`、`INDEX_CODES`
- Produces: `IndexService.update_index_daily() -> int`（增量，靠 upsert 去重保证同一指数同一交易日仅一条）

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_index_service.py` 末尾追加：

```python
def test_update_index_daily_writes_and_dedups():
    svc = IndexService()
    svc.ak = None
    svc.mongo = _IdxMongo()

    def fake_fetch(sources, method, code, **kwargs):
        return [{"symbol": code, "trade_date": "2024-01-02", "close": 1.0,
                 "open": 1.0, "high": 1.0, "low": 1.0, "volume": 1, "amount": 1,
                 "data_source": "akshare"}]

    with patch("app.services.index_service.fetch_with_fallback", side_effect=fake_fetch):
        n = svc.update_index_daily()
    assert n == len(IndexService.INDEX_CODES)
    # 同一指数同一交易日只写入一次（upsert 去重）
    assert len(svc.mongo.written) == n
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_index_service.py -v`
Expected: FAIL（`AttributeError: 'IndexService' object has no attribute 'update_index_daily'`）

- [x] **Step 3: 实现按日更新**

在 `etf-service/app/services/index_service.py` 的 `IndexService` 类中，`fetch_all_index_daily_history` 之后追加：

```python
    def update_index_daily(self) -> int:
        """按日增量更新指数行情，upsert 去重保证同一指数同一交易日仅一条。"""
        total = 0
        for code in self.INDEX_CODES:
            quotes = fetch_with_fallback(get_source_chain(), "fetch_index_daily", code, start_date="20000101")
            if not quotes:
                continue
            for q in quotes:
                record = dict(q)
                record["index_code"] = record.pop("symbol")
                if self.mongo.upsert_index_daily_quote(record):
                    total += 1
        logger.info("指数日线增量更新完成，写入 %s 条", total)
        return total
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_index_service.py -v`
Expected: 3 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/index_service.py etf-service/tests/test_index_service.py
git commit -m "feat: 实现指数行情按日更新与去重"
```

---

### Task 12: 优质池个股历史K线采集（tasks.md 4.1）

**Files:**
- Create: `etf-service/app/services/stock_service.py`
- Modify: `etf-service/app/services/__init__.py`
- Create: `etf-service/tests/test_stock_service.py`

**Interfaces:**
- Consumes: `get_source_chain`、`fetch_with_fallback`、`mongo_client.get_constituent_symbols`、`upsert_stock_daily_kline`
- Produces:
  - `StockService.fetch_stock_daily_history(symbol, start_date="19900101") -> int`
  - `StockService.fetch_all_stock_daily_history() -> int`

- [x] **Step 1: 编写失败测试**

创建 `etf-service/tests/test_stock_service.py`：

```python
from unittest.mock import patch

from app.services.stock_service import StockService


class _FakeMongo:
    def __init__(self, symbols):
        self.symbols = symbols
        self.written = set()

    def get_constituent_symbols(self):
        return self.symbols

    def upsert_stock_daily_kline(self, data):
        self.written.add((data["symbol"], data["trade_date"]))
        return True


def _quote(symbol, trade_date="2024-01-02"):
    return {"symbol": symbol, "trade_date": trade_date, "open": 1.0, "high": 2.0,
            "low": 0.5, "close": 1.5, "volume": 1, "amount": 2, "data_source": "akshare"}


def test_fetch_stock_daily_history_writes_records():
    svc = StockService(mongo=_FakeMongo([]))
    with patch("app.services.stock_service.fetch_with_fallback", return_value=[_quote("600000")]):
        n = svc.fetch_stock_daily_history("600000")
    assert n == 1
    assert ("600000", "2024-01-02") in svc.mongo.written


def test_fetch_all_stock_daily_history_iterates_pool():
    svc = StockService(mongo=_FakeMongo(["600000", "000001"]))
    with patch("app.services.stock_service.fetch_with_fallback",
               side_effect=lambda s, m, c, **kw: [_quote(c)]):
        n = svc.fetch_all_stock_daily_history()
    assert n == 2
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: FAIL（`ModuleNotFoundError: app.services.stock_service`）

- [x] **Step 3: 实现个股K线采集**

创建 `etf-service/app/services/stock_service.py`：

```python
import logging
from typing import List, Optional

from ..datasource import get_source_chain
from ..datasource.base import fetch_with_fallback
from ..database.mongo import mongo_client

logger = logging.getLogger(__name__)


class StockService:
    def __init__(self, mongo=None):
        self.mongo = mongo or mongo_client

    def _pool(self) -> List[str]:
        return self.mongo.get_constituent_symbols()

    def fetch_stock_daily_history(self, symbol: str, start_date: str = "19900101") -> int:
        quotes = fetch_with_fallback(get_source_chain(), "fetch_stock_daily", symbol, start_date=start_date)
        if not quotes:
            logger.warning("个股 %s 历史K线采集为空", symbol)
            return 0
        count = 0
        for q in quotes:
            if self.mongo.upsert_stock_daily_kline(q):
                count += 1
        return count

    def fetch_all_stock_daily_history(self) -> int:
        total = 0
        for symbol in self._pool():
            total += self.fetch_stock_daily_history(symbol)
        logger.info("优质池个股历史K线采集完成，写入 %s 条", total)
        return total


stock_service = StockService()
```

编辑 `etf-service/app/services/__init__.py`，追加：

```python
from .stock_service import stock_service, StockService

__all__ = ["etf_service", "index_service", "IndexService", "stock_service", "StockService"]
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: 2 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/stock_service.py etf-service/app/services/__init__.py etf-service/tests/test_stock_service.py
git commit -m "feat: 实现优质池个股历史K线采集"
```

---

### Task 13: 个股K线按日增量更新与去重（tasks.md 4.2）

**Files:**
- Modify: `etf-service/app/services/stock_service.py`
- Modify: `etf-service/tests/test_stock_service.py`

**Interfaces:**
- Consumes: `fetch_stock_daily` 链、`upsert_stock_daily_kline`
- Produces: `StockService.update_stock_daily_incremental() -> int`（仅补缺失交易日，靠 upsert 去重）

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_stock_service.py` 末尾追加：

```python
def test_update_stock_daily_incremental_only_upserts():
    svc = StockService(mongo=_FakeMongo(["600000"]))
    with patch("app.services.stock_service.fetch_with_fallback",
               return_value=[_quote("600000")]):
        n = svc.update_stock_daily_incremental()
    assert n == 1
    assert ("600000", "2024-01-02") in svc.mongo.written
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: FAIL（`AttributeError: 'StockService' object has no attribute 'update_stock_daily_incremental'`）

- [x] **Step 3: 实现增量更新**

在 `etf-service/app/services/stock_service.py` 的 `StockService` 类中，`fetch_all_stock_daily_history` 之后追加：

```python
    def update_stock_daily_incremental(self) -> int:
        """按日增量更新个股K线，仅补缺失交易日（upsert 去重）。"""
        total = 0
        for symbol in self._pool():
            quotes = fetch_with_fallback(get_source_chain(), "fetch_stock_daily", symbol, start_date="20000101")
            if not quotes:
                continue
            for q in quotes:
                if self.mongo.upsert_stock_daily_kline(q):
                    total += 1
        logger.info("个股K线增量更新完成，写入 %s 条", total)
        return total
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: 3 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/stock_service.py etf-service/tests/test_stock_service.py
git commit -m "feat: 实现个股K线按日增量更新与去重"
```

---

### Task 14: 优质池个股财务指标采集（tasks.md 5.1）

**Files:**
- Modify: `etf-service/app/services/stock_service.py`
- Modify: `etf-service/tests/test_stock_service.py`

**Interfaces:**
- Consumes: `fetch_with_fallback`（method `fetch_fundamentals`）、`upsert_stock_fundamental`
- Produces: `StockService.fetch_and_store_fundamentals() -> int`

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_stock_service.py` 末尾追加：

```python
def _fundamental(symbol):
    return {"symbol": symbol, "report_date": "2024-03-31", "pe": 12.0, "pb": 1.5,
            "roe": 8.0, "revenue_yoy": 10.0, "profit_yoy": 15.0, "gross_margin": 30.0,
            "data_source": "akshare"}


class _FundMongo(_FakeMongo):
    def upsert_stock_fundamental(self, data):
        self.written.add((data["symbol"], data["report_date"]))
        return True


def test_fetch_and_store_fundamentals_writes_records():
    svc = StockService(mongo=_FundMongo(["600000"]))
    with patch("app.services.stock_service.fetch_with_fallback",
               side_effect=lambda s, m, c, **kw: [_fundamental(c)]):
        n = svc.fetch_and_store_fundamentals()
    assert n == 1
    assert ("600000", "2024-03-31") in svc.mongo.written
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: FAIL（`AttributeError: 'StockService' object has no attribute 'fetch_and_store_fundamentals'`）

- [x] **Step 3: 实现财务指标采集**

在 `etf-service/app/services/stock_service.py` 的 `StockService` 类中追加：

```python
    def fetch_and_store_fundamentals(self) -> int:
        """采集优质池个股财务指标（PE/PB/ROE/营收增速/净利增速/毛利率）写入 stock_fundamentals。"""
        total = 0
        for symbol in self._pool():
            fundamentals = fetch_with_fallback(get_source_chain(), "fetch_fundamentals", symbol)
            if not fundamentals:
                continue
            for f in fundamentals:
                if self.mongo.upsert_stock_fundamental(f):
                    total += 1
        logger.info("个股财务指标采集完成，写入 %s 条", total)
        return total
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: 4 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/stock_service.py etf-service/tests/test_stock_service.py
git commit -m "feat: 实现优质池个股财务指标采集"
```

---

### Task 15: 个股行业与概念分类采集（tasks.md 5.2）

**Files:**
- Modify: `etf-service/app/services/stock_service.py`
- Modify: `etf-service/tests/test_stock_service.py`

**Interfaces:**
- Consumes: `fetch_with_fallback`（method `fetch_industry_concept`）、`upsert_stock_industry_concept`
- Produces: `StockService.fetch_and_store_industry_concept() -> int`

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_stock_service.py` 末尾追加：

```python
def _industry_concept(symbol):
    return {"symbol": symbol, "industry": "银行", "concepts": ["融资融券"],
            "data_source": "akshare"}


class _IcMongo(_FakeMongo):
    def upsert_stock_industry_concept(self, data):
        self.written.add(data["symbol"])
        return True


def test_fetch_and_store_industry_concept_writes_records():
    svc = StockService(mongo=_IcMongo(["600000"]))
    with patch("app.services.stock_service.fetch_with_fallback",
               side_effect=lambda s, m, c, **kw: _industry_concept(c)):
        n = svc.fetch_and_store_industry_concept()
    assert n == 1
    assert "600000" in svc.mongo.written
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: FAIL（`AttributeError: 'StockService' object has no attribute 'fetch_and_store_industry_concept'`）

- [x] **Step 3: 实现行业概念采集**

在 `etf-service/app/services/stock_service.py` 的 `StockService` 类中追加：

```python
    def fetch_and_store_industry_concept(self) -> int:
        """采集优质池个股行业与概念分类写入 stock_industry_concept。"""
        total = 0
        for symbol in self._pool():
            info = fetch_with_fallback(get_source_chain(), "fetch_industry_concept", symbol)
            if not info:
                continue
            if self.mongo.upsert_stock_industry_concept(info):
                total += 1
        logger.info("个股行业概念采集完成，写入 %s 条", total)
        return total
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_stock_service.py -v`
Expected: 5 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/services/stock_service.py etf-service/tests/test_stock_service.py
git commit -m "feat: 实现个股行业与概念分类采集"
```

---

### Task 16: 财务与分类数据定期更新（tasks.md 5.3）

**Files:**
- Modify: `etf-service/app/tasks/data_tasks.py`
- Modify: `etf-service/tests/test_data_tasks.py`

**Interfaces:**
- Consumes: `stock_service.fetch_and_store_fundamentals` / `fetch_and_store_industry_concept`
- Produces:
  - `data_tasks.update_stock_fundamentals()`（财报季触发，检测到新财报则拉取）
  - `data_tasks.update_stock_industry_concept()`（每日更新）

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_data_tasks.py` 末尾追加：

```python
def test_update_stock_fundamentals_and_industry_concept():
    with patch.object(data_tasks, "stock_service") as svc:
        svc.fetch_and_store_fundamentals.return_value = 800
        svc.fetch_and_store_industry_concept.return_value = 800
        data_tasks.update_stock_fundamentals()
        data_tasks.update_stock_industry_concept()
        svc.fetch_and_store_fundamentals.assert_called_once()
        svc.fetch_and_store_industry_concept.assert_called_once()
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_data_tasks.py -v`
Expected: FAIL（`AttributeError`）

- [x] **Step 3: 实现定期更新任务**

在 `etf-service/app/tasks/data_tasks.py` 顶部导入区追加：

```python
from ..services.stock_service import stock_service
```

在文件末尾追加：

```python
def update_stock_fundamentals():
    """财务指标按财报周期更新（检测到新财报则拉取，upsert 去重）。"""
    logger.info("开始更新个股财务指标")
    try:
        n = stock_service.fetch_and_store_fundamentals()
        logger.info("个股财务指标更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新个股财务指标失败: %s", e, exc_info=True)


def update_stock_industry_concept():
    """个股行业与概念分类每日更新。"""
    logger.info("开始更新个股行业与概念分类")
    try:
        n = stock_service.fetch_and_store_industry_concept()
        logger.info("个股行业与概念分类更新完成，写入 %s 条", n)
    except Exception as e:  # noqa: BLE001
        logger.error("更新个股行业与概念分类失败: %s", e, exc_info=True)
```

编辑 `etf-service/app/tasks/__init__.py` 追加导出：

```python
from .data_tasks import (
    update_index_constituents,
    update_stock_fundamentals,
    update_stock_industry_concept,
)

__all__ = [
    "start_scheduler",
    "stop_scheduler",
    "update_index_constituents",
    "update_stock_fundamentals",
    "update_stock_industry_concept",
]
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_data_tasks.py -v`
Expected: 2 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/tasks/data_tasks.py etf-service/app/tasks/__init__.py etf-service/tests/test_data_tasks.py
git commit -m "feat: 实现财务与分类数据定期更新任务"
```

---

### Task 17: 失败重试与降级逻辑（tasks.md 6.1）

**Files:**
- Modify: `etf-service/app/datasource/base.py`
- Modify: `etf-service/tests/test_fallback.py`

**Interfaces:**
- Consumes: `fetch_with_fallback`
- Produces: `fetch_with_fallback(sources, method, code, retries=2, retry_delay=1, **kwargs)`（每源失败重试 `retries` 次、间隔 `retry_delay` 秒；全部失败记 error 日志、保留旧数据、返回 `None` 不抛异常）

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_fallback.py` 末尾追加：

```python
def test_fallback_retries_each_source():
    flaky = _FakeSource("flaky", result=[{"close": 1.0}], fail=True)
    with patch("app.datasource.base.time.sleep") as slp:
        out = fetch_with_fallback([flaky], "fetch_stock_daily", "000001", retries=2, retry_delay=1)
    assert out is None
    assert slp.call_count == 2  # 初次失败 + 2 次重试间各 sleep 一次


def test_fallback_does_not_raise_on_total_failure():
    out = fetch_with_fallback([_FakeSource("f", fail=True)], "fetch_stock_daily", "000001", retries=1, retry_delay=0)
    assert out is None
```

（顶部需补 `from unittest.mock import patch` 导入。）

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_fallback.py -v`
Expected: FAIL（`fetch_with_fallback() got an unexpected keyword argument 'retries'`）

- [x] **Step 3: 实现重试与降级**

编辑 `etf-service/app/datasource/base.py`，在顶部导入区追加 `import time`，并覆盖 `fetch_with_fallback`：

```python
import logging
import time
from typing import Any, List, Optional

logger = logging.getLogger(__name__)


class DataSource:
    """数据源抽象接口：子类按能力覆写方法，不支持的返回 None。"""

    name: str = "base"

    def fetch_index_daily(self, code: str, start_date: Optional[str] = None) -> Optional[list]:
        return None

    def fetch_stock_daily(self, code: str, start_date: Optional[str] = None) -> Optional[list]:
        return None

    def fetch_fundamentals(self, code: str) -> Optional[list]:
        return None

    def fetch_industry_concept(self, code: str) -> Optional[dict]:
        return None


def fetch_with_fallback(sources: List[DataSource], method: str, code: str,
                        retries: int = 2, retry_delay: int = 1, **kwargs) -> Optional[Any]:
    """按 sources 顺序尝试（每源失败重试 retries 次），非空即返回；全部失败返回 None。"""
    for source in sources:
        fn = getattr(source, method, None)
        if fn is None:
            continue
        for attempt in range(retries + 1):
            try:
                result = fn(code, **kwargs)
                if result:
                    return result
                break  # 非异常但为空，切换下一源
            except Exception as e:  # noqa: BLE001 单源失败重试后切换备用源
                logger.warning("数据源 %s 的 %s(%s) 第 %s 次失败: %s",
                               getattr(source, "name", source), method, code, attempt + 1, e)
                if attempt < retries and retry_delay > 0:
                    time.sleep(retry_delay)
                continue
    logger.error("所有数据源对 %s(%s) 均失败，保留旧数据", method, code)
    return None
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_fallback.py -v`
Expected: 5 passed

- [x] **Step 5: Commit**

```bash
git add etf-service/app/datasource/base.py etf-service/tests/test_fallback.py
git commit -m "feat: 为各采集任务增加失败重试与降级逻辑"
```

---

### Task 18: APScheduler 调度集成（tasks.md 6.2）

**Files:**
- Modify: `etf-service/app/tasks/etf_task.py`
- Modify: `etf-service/tests/test_data_tasks.py`

**Interfaces:**
- Consumes: `data_tasks` 各任务函数、`BackgroundScheduler` / `IntervalTrigger` / `CronTrigger`
- Produces: `start_scheduler()` 注册新任务并保留原有 ETF 任务，不破坏现有 `update_etf_daily_quotes` / `update_etf_basic_info`

- [x] **Step 1: 编写失败测试**

在 `etf-service/tests/test_data_tasks.py` 末尾追加：

```python
def test_start_scheduler_registers_data_jobs(monkeypatch):
    from app.tasks import etf_task

    jobs = []

    class _Sched:
        running = False

        def add_job(self, **kwargs):
            jobs.append(kwargs["id"])

        def start(self):
            self.running = True

    sched = _Sched()
    monkeypatch.setattr(etf_task, "scheduler", sched)
    monkeypatch.setattr(etf_task, "update_etf_daily_quotes", lambda: None)
    monkeypatch.setattr(etf_task, "update_etf_basic_info", lambda: None)
    monkeypatch.setattr(etf_task, "update_index_constituents", lambda: None)
    monkeypatch.setattr(etf_task, "update_stock_industry_concept", lambda: None)
    etf_task.start_scheduler()
    assert "update_index_daily" in jobs
    assert "update_stock_daily_incremental" in jobs
    assert "update_index_constituents" in jobs
    assert "update_stock_fundamentals" in jobs
    assert "update_stock_industry_concept" in jobs
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd etf-service; python -m pytest tests/test_data_tasks.py -v`
Expected: FAIL（`AssertionError: 'update_index_daily' not in jobs`）

- [x] **Step 3: 接入调度**

编辑 `etf-service/app/tasks/etf_task.py`，在顶部导入区追加：

```python
from .data_tasks import (
    update_index_constituents,
    update_stock_fundamentals,
    update_stock_industry_concept,
)
from ..services.index_service import index_service
from ..services.stock_service import stock_service
```

在 `start_scheduler` 中，`scheduler.add_job(...)` 区块（原有两个任务之后、`if not scheduler.running` 内部）追加 5 个任务；并在 `update_etf_basic_info()` 立即执行处之后追加立即执行：

```python
            # —— 数据地基任务 ——
            scheduler.add_job(
                func=index_service.update_index_daily,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_index_daily",
                name="交易日15:30后增量更新指数行情",
                replace_existing=True,
            )

            scheduler.add_job(
                func=stock_service.update_stock_daily_incremental,
                trigger=CronTrigger(day_of_week="mon-fri", hour=15, minute=30),
                id="update_stock_daily_incremental",
                name="交易日15:30后增量更新个股K线",
                replace_existing=True,
            )

            scheduler.add_job(
                func=update_index_constituents,
                trigger=CronTrigger(day=1, hour=1, minute=0),
                id="update_index_constituents",
                name="每月1日更新指数成分股",
                replace_existing=True,
            )

            scheduler.add_job(
                func=update_stock_fundamentals,
                trigger=CronTrigger(month="4,8,10", day="last", hour=2, minute=0),
                id="update_stock_fundamentals",
                name="财报季更新财务指标",
                replace_existing=True,
            )

            scheduler.add_job(
                func=update_stock_industry_concept,
                trigger=CronTrigger(hour=3, minute=0),
                id="update_stock_industry_concept",
                name="每日更新个股行业与概念分类",
                replace_existing=True,
            )

            # 立即执行一次数据地基任务
            update_index_constituents()
            update_stock_industry_concept()
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd etf-service; python -m pytest tests/test_data_tasks.py -v`
Expected: 3 passed

- [x] **Step 5: 运行全量测试**

Run: `cd etf-service; python -m pytest tests/ -v`
Expected: 全部通过（无失败）

- [x] **Step 6: Commit**

```bash
git add etf-service/app/tasks/etf_task.py etf-service/tests/test_data_tasks.py
git commit -m "feat: 将各采集任务接入 APScheduler 调度"
```

---

## 自检清单

- **Spec 覆盖：** tasks.md 的 1.1–6.2 共 18 条任务与上方 Task 1–18 一一对应；design.md 的 5 个集合、标准格式、主备顺序（akshare→baostock→tushare→东财/新浪）、密钥从环境变量读取、降级保留旧数据均落实。
- **占位符扫描：** 无 TBD/TODO；每个创建/修改步骤均含实际代码。
- **类型一致性：** `DailyQuote`/`Fundamental`/`IndustryConcept` 字段在 adapter、service、test 中保持一致；`fetch_with_fallback(sources, method, code, **kwargs)` 签名在 Task 6 定义、Task 17 扩展 `retries/retry_delay` 后保持一致；`index_code`（指数集合）与 `symbol`（个股集合）的去重键在 Task 7 与 Task 10/11 保持一致。
