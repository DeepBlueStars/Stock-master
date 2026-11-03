"""回填服务单元测试：不连数据库、不连网络。"""

from datetime import datetime, timedelta

from app.services.kline_backfill_service import (
    KIND_ETF,
    KIND_STOCK,
    KlineBackfillService,
    is_etf_symbol,
)


class _FakeCollection:
    def __init__(self, docs=None, distinct_values=None, name="etf_daily_quotes", indexes=None):
        self.docs = docs or []
        self._distinct = distinct_values or []
        self.name = name
        self._indexes = dict(indexes) if indexes else {"_id_": {}}

    def distinct(self, field):
        return self._distinct

    def find(self, query=None, projection=None):
        return iter(self.docs)

    def index_information(self):
        return dict(self._indexes)

    def create_index(self, keys, name=None):
        self._indexes[name or "custom"] = {"key": keys}
        return name or "custom"

    def delete_many(self, query):
        dates = set((query.get("trade_date") or {}).get("$in", []))
        before = len(self.docs)
        self.docs = [d for d in self.docs if d.get("trade_date") not in dates]
        return _DeleteResult(before - len(self.docs))


class _DeleteResult:
    def __init__(self, deleted_count):
        self.deleted_count = deleted_count


class _FakeDB:
    def __init__(self, collections=None):
        self._collections = collections or {}

    def __getitem__(self, name):
        return self._collections.get(name, _FakeCollection())


class _FakeMongo:
    def __init__(self, etf_symbols=None, latest=None, stock_symbols=None, positions=None):
        self._etf_symbols = etf_symbols or []
        self._latest = latest or {}
        self._stock_symbols = stock_symbols or []
        self.written = []
        self.stock_daily_kline = _FakeCollection(
            distinct_values=self._stock_symbols, name="stock_daily_kline"
        )
        self.etf_daily_quotes = _FakeCollection(name="etf_daily_quotes")
        self.db = _FakeDB({
            "position": _FakeCollection(positions or []),
            "trade_plans": _FakeCollection(),
        })

    def get_etf_symbols(self):
        return list(self._etf_symbols)

    def get_latest_etf_trade_date(self, symbol):
        return self._latest.get(symbol)

    def get_latest_trade_date(self, symbol):
        return self._latest.get(symbol)

    def bulk_upsert_etf_daily_quotes(self, records):
        self.written.extend(records)
        return len(records)

    def bulk_upsert_stock_daily_kline(self, records):
        self.written.extend(records)
        return len(records)


class _FakeSource:
    def __init__(self, name, rows=None, error=None):
        self.name = name
        self._rows = rows
        self._error = error
        self.calls = []

    def fetch_etf_daily(self, code, start_date=None):
        self.calls.append((code, start_date))
        if self._error:
            raise self._error
        return self._rows

    def fetch_stock_daily(self, code, start_date=None):
        return self.fetch_etf_daily(code, start_date=start_date)


def _row(date, close=1.0, open_=None, high=None, low=None):
    from app.normalize.schema import DailyQuote

    now = datetime(2026, 11, 1)
    return DailyQuote(
        symbol="515180", trade_date=date,
        open=open_ if open_ is not None else close,
        high=high if high is not None else close,
        low=low if low is not None else close,
        close=close, volume=1, amount=1, data_source="fake",
        created_at=now, updated_at=now,
    ).to_dict()


def test_clean_rows_drops_invalid_and_fills_missing_ohlc():
    rows = [
        _row("2026-10-29", close=0),                     # 收盘价无效 → 丢弃
        _row("2026-10-30", close=1.383, open_=0, high=0, low=0),  # OHLC 缺失 → 用收盘价补
        _row("2026-10-30", close=1.383, open_=1.374, high=1.392, low=1.362),  # 同日重复 → 后者胜出
    ]
    cleaned = KlineBackfillService.clean_rows(rows, today="2026-11-01")

    assert len(cleaned) == 1
    row = cleaned[0]
    assert row["close"] == 1.383
    assert row["open"] == 1.374 and row["high"] == 1.392 and row["low"] == 1.362


def test_clean_rows_enforces_low_le_close_le_high():
    cleaned = KlineBackfillService.clean_rows(
        [_row("2026-10-30", close=1.383, open_=1.4, high=1.2, low=1.5)], today="2026-11-01"
    )
    row = cleaned[0]
    assert row["low"] <= row["close"] <= row["high"]


def test_clean_rows_drops_future_dates():
    cleaned = KlineBackfillService.clean_rows(
        [_row("2030-01-01", close=1.0)], today="2026-11-01"
    )
    assert cleaned == []


def test_next_start_backs_off_to_overwrite_recent_rows():
    """增量要回溯几天，才能覆盖快照先插进去的当日行。"""
    service = KlineBackfillService(mongo=_FakeMongo(), sleep_seconds=0, incremental_overlap_days=5)
    assert service._next_start("2026-10-29") == "2026-10-24"


def test_next_start_never_goes_before_default_start():
    service = KlineBackfillService(mongo=_FakeMongo(), sleep_seconds=0, incremental_overlap_days=30)
    assert service._next_start("2019-01-10") == service.default_start


def test_run_still_refetches_when_latest_is_today():
    today = datetime.now().strftime("%Y-%m-%d")
    mongo = _FakeMongo(etf_symbols=["515180"], latest={"515180": today})
    source = _FakeSource("sina", rows=[_row(today)])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    stats = service.run(kind=KIND_ETF)

    assert stats.ok == 1
    assert source.calls == [("515180", service._next_start(today))]


def test_run_writes_cleaned_rows_and_counts_stats():
    mongo = _FakeMongo(etf_symbols=["515180"], latest={"515180": "2026-10-28"})
    source = _FakeSource("sina", rows=[_row("2026-10-29"), _row("2026-10-30", close=1.383)])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    stats = service.run(kind=KIND_ETF)

    assert stats.total == 1 and stats.ok == 1 and stats.rows == 2
    assert [r["trade_date"] for r in mongo.written] == ["2026-10-29", "2026-10-30"]
    assert source.calls == [("515180", service._next_start("2026-10-28"))]


def test_run_dry_run_does_not_write():
    mongo = _FakeMongo(etf_symbols=["515180"])
    source = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    stats = service.run(kind=KIND_ETF, dry_run=True)

    assert stats.rows == 1
    assert mongo.written == []


def test_run_falls_back_to_next_source_when_first_raises():
    mongo = _FakeMongo(etf_symbols=["515180"])
    broken = _FakeSource("em", error=ConnectionError("blocked"))
    good = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[broken, good], sleep_seconds=0)

    stats = service.run(kind=KIND_ETF)

    assert stats.ok == 1 and stats.failed == 0
    assert broken.calls and good.calls


def test_run_stops_after_max_consecutive_failures():
    mongo = _FakeMongo(etf_symbols=["A", "B", "C", "D"])
    broken = _FakeSource("em", error=ConnectionError("blocked"))
    service = KlineBackfillService(
        mongo=mongo, sources=[broken], sleep_seconds=0, max_consecutive_failures=2
    )

    stats = service.run(kind=KIND_ETF)

    assert stats.failed == 2
    assert len(broken.calls) == 2
    assert stats.failed_symbols == ["A", "B"]


def test_stock_symbols_include_positions():
    mongo = _FakeMongo(stock_symbols=["000001"], positions=[{"symbol": "600519"}, {"symbol": "000001"}])
    service = KlineBackfillService(mongo=mongo, sleep_seconds=0)
    assert service.symbols(KIND_STOCK) == ["000001", "600519"]


def test_stock_symbols_include_index_constituents():
    mongo = _FakeMongo(stock_symbols=["000001"])
    mongo.db._collections["index_constituents"] = _FakeCollection(
        docs=[{"symbol": "600519"}, {"symbol": "000001"}]
    )
    service = KlineBackfillService(mongo=mongo, sleep_seconds=0)
    assert service.symbols(KIND_STOCK) == ["000001", "600519"]


def test_stock_symbols_exclude_etf_from_positions():
    mongo = _FakeMongo(
        stock_symbols=["000001"],
        positions=[{"symbol": "600519"}, {"symbol": "515180"}, {"symbol": "159919"}],
    )
    service = KlineBackfillService(mongo=mongo, sleep_seconds=0)
    # 持仓表里混着 ETF，按品种回填个股时必须滤掉，否则会拿股票接口去查 ETF
    assert service.symbols(KIND_STOCK) == ["000001", "600519"]


def test_is_etf_symbol_matches_java_convention():
    assert is_etf_symbol("515180") is True
    assert is_etf_symbol("159919") is True
    assert is_etf_symbol("600519") is False
    assert is_etf_symbol("000001") is False


def test_batching_splits_large_payloads():
    mongo = _FakeMongo()
    service = KlineBackfillService(mongo=mongo, sleep_seconds=0, batch_size=2)
    written = service._save(KIND_ETF, [_row(f"2026-10-{d:02d}") for d in range(1, 6)])
    assert written == 5
    assert len(mongo.written) == 5


def test_run_warns_when_symbol_date_index_missing(caplog):
    mongo = _FakeMongo(etf_symbols=["515180"])
    source = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    with caplog.at_level("WARNING"):
        service.run(kind=KIND_ETF)

    assert any("索引" in r.getMessage() for r in caplog.records)


def test_ensure_index_creates_symbol_date_index():
    mongo = _FakeMongo(etf_symbols=["515180"])
    source = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    service.run(kind=KIND_ETF, ensure_index=True)

    assert "symbol_trade_date" in mongo.etf_daily_quotes.index_information()


def test_no_index_warning_when_index_exists(caplog):
    mongo = _FakeMongo(etf_symbols=["515180"])
    mongo.etf_daily_quotes._indexes["symbol_trade_date"] = {"key": [("symbol", 1)]}
    source = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    with caplog.at_level("WARNING"):
        service.run(kind=KIND_ETF)

    assert not any("索引" in r.getMessage() for r in caplog.records)


def test_orphan_trade_dates_flags_non_trading_days():
    mongo = _FakeMongo()
    mongo.etf_daily_quotes.docs = [
        {"symbol": "515180", "trade_date": "2026-10-30"},
        {"symbol": "515180", "trade_date": "2026-10-31"},  # 周六
    ]
    service = KlineBackfillService(mongo=mongo, sleep_seconds=0)

    orphans = service.orphan_trade_dates(KIND_ETF, "515180", {"2026-10-30"})

    assert orphans == ["2026-10-31"]


def test_orphan_trade_dates_ignores_history_before_window():
    mongo = _FakeMongo()
    mongo.etf_daily_quotes.docs = [
        {"symbol": "515180", "trade_date": "2010-03-01"},  # 窗口外的正常交易日，不是脏数据
        {"symbol": "515180", "trade_date": "2026-10-30"},
    ]
    service = KlineBackfillService(mongo=mongo, sleep_seconds=0)

    orphans = service.orphan_trade_dates(KIND_ETF, "515180", {"2026-10-30"})

    assert orphans == []


def test_full_run_purges_orphans_when_requested():
    mongo = _FakeMongo(etf_symbols=["515180"])
    mongo.etf_daily_quotes.docs = [{"symbol": "515180", "trade_date": "2026-10-31"}]
    source = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    stats = service.run(kind=KIND_ETF, full=True, prune_orphans=True)

    assert stats.orphan_rows == 1 and stats.pruned == 1
    assert [d["trade_date"] for d in mongo.etf_daily_quotes.docs] == []


def test_full_run_only_reports_orphans_by_default():
    mongo = _FakeMongo(etf_symbols=["515180"])
    mongo.etf_daily_quotes.docs = [{"symbol": "515180", "trade_date": "2026-10-31"}]
    source = _FakeSource("sina", rows=[_row("2026-10-30")])
    service = KlineBackfillService(mongo=mongo, sources=[source], sleep_seconds=0)

    stats = service.run(kind=KIND_ETF, full=True)

    assert stats.orphan_rows == 1 and stats.pruned == 0
    assert len(mongo.etf_daily_quotes.docs) == 1
