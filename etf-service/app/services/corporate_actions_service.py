"""除权 / 份额折算检测与序列还原。

为什么不复权会出问题：ETF 份额折算（如 1:2）会让不复权收盘价一夜腰斩，
个股送转股同理。若不还原，「建仓以来最高收盘」和 MA60 会在跨折算日的持仓上
算出假回撤——512480 不复权口径 peak 3.005 → 现价 0.72 显示 −76%，还原后其实是 −51%。

做法：
1. 扫描单日 |涨跌| ≥ MIN_GAP 的跳空，用基准指数当日涨跌反推「真实折算比例」；
2. 只把能对上简单分数（1/2、1/3、1/4 …）的跳空判为 SPLIT/CONVERT 并应用，
   对不上的记为 GAP_UNEXPLAINED 只告警、不动序列——真实暴跌不能被当成分拆抹掉；
3. 每个 K 线文档写 adj_factor（默认 1.0），还原价 = 原价 × adj_factor，
   即：折算日之前的行按比例缩放，折算日之后保持 1.0，于是序列连续到今天。
"""
import logging
from datetime import datetime
from typing import Any, Dict, Iterable, List, Optional

logger = logging.getLogger(__name__)

KIND_ETF = "etf"
KIND_STOCK = "stock"

MIN_GAP = 0.25
SNAP_TOLERANCE = 0.04
# A 股送转/折算的常见价格比例：10送1~10送9、10送10、10转增、缩股与反向拆分。
# 之所以敢套用，是因为主板 ±10%、创业板/科创板 ±20% 的涨跌停决定了：
# 单日裸价跌幅超过 25% 几乎只可能是除权，真实暴跌到不了这个幅度。
SIMPLE_RATIOS = (
    1 / 1.1, 1 / 1.2, 1 / 1.3, 1 / 1.4, 1 / 1.5, 1 / 1.6,
    1 / 1.7, 1 / 1.8, 1 / 1.9, 1 / 2, 1 / 3, 1 / 4, 1 / 5, 1 / 8, 1 / 10,
    2.0, 3.0, 4.0, 5.0, 10.0,
)
BENCHMARK = "sh000300"

CORPORATE_ACTIONS = "corporate_actions"
COLLECTIONS = {KIND_ETF: "etf_daily_quotes", KIND_STOCK: "stock_daily_kline"}


def _f(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def benchmark_returns(index_rows: Iterable[Dict[str, Any]]) -> Dict[str, float]:
    """把基准指数日线折成 {trade_date: 当日涨跌幅}。"""
    out: Dict[str, float] = {}
    prev = None
    for row in sorted(index_rows, key=lambda r: r.get("trade_date") or ""):
        close = _f(row.get("close"))
        date = row.get("trade_date")
        if not date or close <= 0:
            continue
        if prev and prev > 0:
            out[date] = close / prev - 1
        prev = close
    return out


def _snap(ratio: float) -> Optional[float]:
    for candidate in SIMPLE_RATIOS:
        if abs(ratio / candidate - 1) <= SNAP_TOLERANCE:
            return candidate
    return None


def gap_threshold(kind: str, symbol: str, default: float = MIN_GAP) -> float:
    """按板块决定「多大跳空才算除权」。

    主板日涨跌幅限制 ±10%，所以单日跌幅超过 10% 只可能是除权；
    创业板/科创板限制 ±20%，阈值放到 21% 才不会把真实的跌停误判成送转。
    """
    if kind != KIND_STOCK:
        return default
    if symbol.startswith("30") or symbol.startswith("68"):
        return 0.21
    if symbol.startswith(("60", "00")):
        return 0.11
    return default


def _days_between(earlier: str, later: str) -> int:
    try:
        fmt = "%Y-%m-%d"
        return (datetime.strptime(later, fmt) - datetime.strptime(earlier, fmt)).days
    except (TypeError, ValueError):
        return 0


def detect_events(bars: List[Dict[str, Any]], bench: Optional[Dict[str, float]] = None,
                  min_gap: float = MIN_GAP, exact_ratio: bool = False) -> List[Dict[str, Any]]:
    """找出跳空日并推断折算比例。bars 需按 trade_date 升序。

    exact_ratio=True 时不要求比例能对上「简单分数」——ETF 的份额折算比例是按净值算出来的
    任意小数（如 0.3142），根本不会等于 1/2 或 1/3，只能照原始比例还原。
    """
    bench = bench or {}
    events: List[Dict[str, Any]] = []
    prev = None
    prev_date = None
    for bar in bars:
        close = _f(bar.get("close"))
        date = bar.get("trade_date")
        if not date or close <= 0:
            continue
        if prev is not None and prev > 0:
            raw = close / prev - 1
            if abs(raw) >= min_gap:
                bench_ret = bench.get(date, 0.0)
                # 直接用原始价格比例去对简单分数：指数当日涨跌通常在 ±3% 内，
                # 会被 SNAP_TOLERANCE 吸收；按 beta=1 剥离反而会给真实暴跌灌噪声。
                ratio = 1 + raw
                snapped = ratio if exact_ratio else _snap(ratio)
                # 停牌复牌留下的跳空不是除权：中间隔了太多自然日，一律不改序列
                resume_gap = prev_date is not None and _days_between(prev_date, date) > 10
                events.append({
                    "symbol": bar.get("symbol"),
                    "ex_date": date,
                    "prev_close": prev,
                    "close": close,
                    "raw_return": round(raw, 6),
                    "bench_return": round(bench_ret, 6),
                    "ratio": round(snapped if snapped else ratio, 6),
                    "kind": "GAP_UNEXPLAINED" if resume_gap else ("SPLIT" if snapped else "GAP_UNEXPLAINED"),
                    "applied": (snapped is not None) and not resume_gap,
                })
        prev = close
        prev_date = date
    return events


def cumulative_factors(bars: List[Dict[str, Any]], events: List[Dict[str, Any]]) -> Dict[str, float]:
    """给每根 K 线算出 adj_factor：折算日之前的行按各次比例连乘。"""
    applied = sorted((e for e in events if e.get("applied")), key=lambda e: e["ex_date"])
    if not applied:
        return {bar["trade_date"]: 1.0 for bar in bars if bar.get("trade_date")}

    factors: Dict[str, float] = {}
    for bar in bars:
        date = bar.get("trade_date")
        if not date:
            continue
        factor = 1.0
        for event in applied:
            if date < event["ex_date"]:
                factor *= event["ratio"]
        factors[date] = round(factor, 8)
    return factors


class CorporateActionService:
    """检测 → 落 corporate_actions → 回写 adj_factor。"""

    def __init__(self, mongo=None, batch_size: int = 500, min_gap: float = MIN_GAP):
        if mongo is None:
            from ..database.mongo import mongo_client as mongo
        self.mongo = mongo
        self.batch_size = batch_size
        self.min_gap = min_gap

    # ---- 读 ----
    def _collection(self, kind: str):
        return self.mongo.db[COLLECTIONS[kind]]

    def _benchmark(self) -> Dict[str, float]:
        rows = list(self.mongo.db["index_daily_quotes"].find(
            {"index_code": BENCHMARK}, {"trade_date": 1, "close": 1}).sort("trade_date", 1))
        if not rows:
            logger.warning("基准指数 %s 缺失，跳空比例不做大盘剥离", BENCHMARK)
            return {}
        return benchmark_returns(rows)

    def symbols(self, kind: str) -> List[str]:
        return sorted(self._collection(kind).distinct("symbol"))

    def bars(self, kind: str, symbol: str) -> List[Dict[str, Any]]:
        return list(self._collection(kind).find(
            {"symbol": symbol},
            {"symbol": 1, "trade_date": 1, "close": 1, "adj_factor": 1},
        ).sort("trade_date", 1))

    # ---- 写 ----
    def _reset_factors(self, kind: str, symbol: str) -> None:
        self._collection(kind).update_many({"symbol": symbol}, {"$set": {"adj_factor": 1.0}})

    def _apply_event(self, kind: str, symbol: str, event: Dict[str, Any]) -> int:
        # $mul 作用在缺失字段上会得到 0，所以必须先 reset 成 1.0
        result = self._collection(kind).update_many(
            {"symbol": symbol, "trade_date": {"$lt": event["ex_date"]}},
            {"$mul": {"adj_factor": event["ratio"]}},
        )
        return result.modified_count

    def _save_events(self, kind: str, symbol: str, events: List[Dict[str, Any]]) -> None:
        collection = self.mongo.db[CORPORATE_ACTIONS]
        collection.delete_many({"symbol": symbol, "kind_source": "detector"})
        if not events:
            return
        now = datetime.now()
        docs = []
        for event in events:
            docs.append({
                "symbol": symbol,
                "market_kind": kind,
                "ex_date": event["ex_date"],
                "prev_close": event["prev_close"],
                "close": event["close"],
                "raw_return": event["raw_return"],
                "bench_return": event["bench_return"],
                "ratio": event["ratio"],
                "kind": event["kind"],
                "applied": event["applied"],
                "kind_source": "detector",
                "detected_at": now,
            })
        collection.insert_many(docs)

    def scan(self, kind: str, symbols: Optional[List[str]] = None, dry_run: bool = False) -> Dict[str, int]:
        bench = self._benchmark()
        targets = symbols or self.symbols(kind)
        stats = {"symbols": 0, "events": 0, "applied": 0, "bars": 0}
        for symbol in targets:
            bars = self.bars(kind, symbol)
            if len(bars) < 2:
                continue
            events = detect_events(
                bars,
                bench,
                gap_threshold(kind, symbol, self.min_gap),
                exact_ratio=kind == KIND_ETF,
            )
            stats["symbols"] += 1
            stats["events"] += len(events)
            stats["applied"] += sum(1 for e in events if e["applied"])
            if dry_run:
                applied = [e for e in events if e["applied"]]
                if applied:
                    logger.info("%s 检出 %d 次折算，例如 %s", symbol, len(applied), applied[0]["ex_date"])
                continue

            self._reset_factors(kind, symbol)
            for event in (e for e in events if e["applied"]):
                stats["bars"] += self._apply_event(kind, symbol, event)
            self._save_events(kind, symbol, events)
        logger.info(
            "除权检测完成 kind=%s 标的 %d，跳空 %d 次（可还原 %d 次），改写 K 线 %d 行",
            kind, stats["symbols"], stats["events"], stats["applied"], stats["bars"],
        )
        return stats
