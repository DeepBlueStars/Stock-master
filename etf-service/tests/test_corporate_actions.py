"""除权检测与序列还原的单测。"""

from types import SimpleNamespace

from app.services.corporate_actions_service import (
    KIND_ETF,
    KIND_STOCK,
    CorporateActionService,
    benchmark_returns,
    cumulative_factors,
    detect_events,
    gap_threshold,
)


def _bars(prices, start_day=1):
    return [
        {"symbol": "512480", "trade_date": "2026-01-%02d" % (start_day + i), "close": p}
        for i, p in enumerate(prices)
    ]


class _Cursor:
    def __init__(self, docs):
        self.docs = list(docs)

    def sort(self, key, direction=1):
        self.docs.sort(key=lambda d: d.get(key) or "", reverse=direction < 0)
        return self

    def __iter__(self):
        return iter(self.docs)


class _Collection:
    def __init__(self, docs=None):
        self.docs = [dict(d) for d in (docs or [])]

    @staticmethod
    def _match(doc, query):
        for key, expected in (query or {}).items():
            if isinstance(expected, dict):
                bound = expected.get("$lt")
                value = doc.get(key)
                if bound is not None and not (value is not None and value < bound):
                    return False
            elif doc.get(key) != expected:
                return False
        return True

    def find(self, query=None, projection=None):
        return _Cursor([d for d in self.docs if self._match(d, query)])

    def distinct(self, key):
        return sorted({d.get(key) for d in self.docs if d.get(key)})

    def update_many(self, query, update):
        touched = 0
        for doc in self.docs:
            if not self._match(doc, query):
                continue
            if "$set" in update:
                doc.update(update["$set"])
            for key, factor in (update.get("$mul") or {}).items():
                doc[key] = (doc.get(key) or 0) * factor
            touched += 1
        return SimpleNamespace(modified_count=touched)

    def delete_many(self, query):
        self.docs = [d for d in self.docs if not self._match(d, query)]
        return SimpleNamespace(deleted_count=0)

    def insert_many(self, docs):
        self.docs.extend(dict(d) for d in docs)


class _DB:
    def __init__(self, collections):
        self._collections = collections

    def __getitem__(self, name):
        return self._collections.setdefault(name, _Collection())


class _Mongo:
    def __init__(self, collections):
        self.db = _DB(collections)


def test_benchmark_returns_computes_daily_change():
    rows = [
        {"trade_date": "2026-01-01", "close": 100.0},
        {"trade_date": "2026-01-02", "close": 101.0},
        {"trade_date": "2026-01-03", "close": 99.0},
    ]

    result = benchmark_returns(rows)

    assert round(result["2026-01-02"], 6) == 0.01
    assert round(result["2026-01-03"], 6) == round(99 / 101 - 1, 6)


def test_detect_events_flags_half_price_as_split():
    bars = _bars([1.00, 1.02, 0.51])

    events = detect_events(bars, bench={})

    assert len(events) == 1
    event = events[0]
    assert event["ex_date"] == "2026-01-03"
    assert event["kind"] == "SPLIT"
    assert event["ratio"] == 0.5
    assert event["applied"] is True


def test_gap_threshold_follows_price_limit_rules():
    assert gap_threshold(KIND_STOCK, "600519") == 0.11
    assert gap_threshold(KIND_STOCK, "000001") == 0.11
    assert gap_threshold(KIND_STOCK, "300750") == 0.21
    assert gap_threshold(KIND_STOCK, "688981") == 0.21
    assert gap_threshold(KIND_ETF, "515180") == 0.25


def test_detect_events_uses_exact_ratio_for_etf_splits():
    # 份额折算比例按净值算，是 0.3142 这种任意小数，不可能对上 1/2 或 1/3
    bars = _bars([3.348, 1.052])

    loose = detect_events(bars, bench={}, exact_ratio=True)
    strict = detect_events(bars, bench={})

    assert loose[0]["kind"] == "SPLIT"
    assert loose[0]["applied"] is True
    assert abs(loose[0]["ratio"] - 0.314217) < 1e-6
    assert strict[0]["kind"] == "GAP_UNEXPLAINED"


def test_detect_events_recognizes_a_share_bonus_share_ratios():
    # 10送4 → 价格比例 1/1.4 ≈ 0.714；10送5 → 1/1.5 ≈ 0.667（都跨过 25% 阈值）
    ten_for_four = detect_events(_bars([10.00, 7.14]), bench={})
    ten_for_five = detect_events(_bars([10.00, 6.67]), bench={})

    assert ten_for_four[0]["kind"] == "SPLIT"
    assert abs(ten_for_four[0]["ratio"] - 1 / 1.4) < 0.001
    assert ten_for_five[0]["kind"] == "SPLIT"
    assert abs(ten_for_five[0]["ratio"] - 1 / 1.5) < 0.001


def test_detect_events_does_not_treat_a_resume_gap_as_split():
    # 停牌 19 天后复牌暴跌 32%：比例能对上 10送2，但这是真实亏损，不能还原掉
    bars = [
        {"symbol": "000001", "trade_date": "2026-01-01", "close": 10.00},
        {"symbol": "000001", "trade_date": "2026-01-20", "close": 6.80},
    ]

    events = detect_events(bars, bench={})

    assert len(events) == 1
    assert events[0]["kind"] == "GAP_UNEXPLAINED"
    assert events[0]["applied"] is False


def test_detect_events_does_not_silently_absorb_a_real_crash():
    # 连续交易日里跌 65%（0.35 对不上任何简单分数）→ 只告警不改序列
    bars = _bars([1.00, 0.35])

    events = detect_events(bars, bench={})

    assert len(events) == 1
    assert events[0]["kind"] == "GAP_UNEXPLAINED"
    assert events[0]["applied"] is False


def test_detect_events_tolerates_a_moving_benchmark():
    # 指数当日跌 4%，标的 50% 跳空：原始比例 0.5 直接对上简单分数，不被大盘噪声带偏
    bars = _bars([1.00, 0.50])
    bars[1]["trade_date"] = "2026-01-02"

    events = detect_events(bars, bench={"2026-01-02": -0.04})

    assert events[0]["kind"] == "SPLIT"
    assert events[0]["ratio"] == 0.5
    assert events[0]["bench_return"] == -0.04


def test_detect_events_ignores_small_moves():
    bars = _bars([1.00, 1.05, 1.02])

    assert detect_events(bars, bench={}) == []


def test_cumulative_factors_only_scales_before_ex_date():
    bars = _bars([1.00, 1.02, 0.51, 0.52])
    events = detect_events(bars, bench={})

    factors = cumulative_factors(bars, events)

    assert factors["2026-01-01"] == 0.5
    assert factors["2026-01-02"] == 0.5
    assert factors["2026-01-03"] == 1.0
    assert factors["2026-01-04"] == 1.0


def test_cumulative_factors_compound_multiple_splits():
    bars = _bars([4.00, 2.00, 1.00])
    events = [
        {"ex_date": "2026-01-02", "ratio": 0.5, "applied": True},
        {"ex_date": "2026-01-03", "ratio": 0.5, "applied": True},
    ]

    factors = cumulative_factors(bars, events)

    assert factors["2026-01-01"] == 0.25
    assert factors["2026-01-02"] == 0.5
    assert factors["2026-01-03"] == 1.0


def test_cumulative_factors_defaults_to_one_without_events():
    bars = _bars([1.00, 1.01])

    assert cumulative_factors(bars, []) == {"2026-01-01": 1.0, "2026-01-02": 1.0}


def test_scan_writes_factors_and_is_idempotent():
    bars = _bars([1.00, 1.02, 0.51, 0.53])
    mongo = _Mongo({
        "etf_daily_quotes": _Collection(bars),
        "index_daily_quotes": _Collection([
            {"index_code": "sh000300", "trade_date": "2026-01-01", "close": 4000.0},
            {"index_code": "sh000300", "trade_date": "2026-01-02", "close": 4000.0},
            {"index_code": "sh000300", "trade_date": "2026-01-03", "close": 4000.0},
            {"index_code": "sh000300", "trade_date": "2026-01-04", "close": 4000.0},
        ]),
    })
    service = CorporateActionService(mongo=mongo)

    first = service.scan(KIND_ETF, dry_run=False)
    factors_once = {d["trade_date"]: d["adj_factor"] for d in mongo.db["etf_daily_quotes"].docs}
    second = service.scan(KIND_ETF, dry_run=False)
    factors_twice = {d["trade_date"]: d["adj_factor"] for d in mongo.db["etf_daily_quotes"].docs}

    assert first["applied"] == 1
    assert first["bars"] == 2
    assert factors_once == factors_twice
    assert factors_twice["2026-01-01"] == 0.5
    assert factors_twice["2026-01-03"] == 1.0
    assert second["symbols"] == 1


def test_scan_dry_run_does_not_touch_data():
    bars = _bars([1.00, 0.51])
    mongo = _Mongo({"etf_daily_quotes": _Collection(bars), "index_daily_quotes": _Collection()})
    service = CorporateActionService(mongo=mongo)

    stats = service.scan(KIND_ETF, dry_run=True)

    assert stats["applied"] == 1
    assert stats["bars"] == 0
    assert all("adj_factor" not in d for d in mongo.db["etf_daily_quotes"].docs)


def test_scan_splits_by_kind():
    mongo = _Mongo({
        "etf_daily_quotes": _Collection(_bars([1.00, 0.51])),
        "stock_daily_kline": _Collection([{"symbol": "600519", "trade_date": "2026-01-01", "close": 100.0}]),
        "index_daily_quotes": _Collection(),
    })
    service = CorporateActionService(mongo=mongo)

    assert service.symbols(KIND_ETF) == ["512480"]
    assert service.symbols(KIND_STOCK) == ["600519"]
