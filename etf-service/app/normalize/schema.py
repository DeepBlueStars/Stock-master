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
