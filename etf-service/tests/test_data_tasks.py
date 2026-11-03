from unittest.mock import patch

import pytest

from app.tasks import data_tasks


def test_update_index_constituents_calls_service(monkeypatch):
    from app.tasks import data_tasks

    monkeypatch.setattr(data_tasks.mongo_client, "ping", lambda: True)
    with patch.object(data_tasks, "index_service") as svc:
        svc.fetch_and_store_constituents.return_value = 800
        data_tasks.update_index_constituents()
        svc.fetch_and_store_constituents.assert_called_once()


def test_update_index_constituents_skips_when_mongo_unreachable(monkeypatch):
    from app.tasks import data_tasks

    monkeypatch.setattr(data_tasks.mongo_client, "ping", lambda: False)
    with patch.object(data_tasks, "index_service") as svc:
        data_tasks.update_index_constituents()
        svc.fetch_and_store_constituents.assert_not_called()


def test_update_stock_fundamentals_and_industry_concept(monkeypatch):
    from app.tasks import data_tasks

    monkeypatch.setattr(data_tasks.mongo_client, "ping", lambda: True)
    with patch.object(data_tasks, "stock_service") as svc:
        svc.fetch_and_store_fundamentals.return_value = 800
        svc.fetch_and_store_industry_concept.return_value = 800
        data_tasks.update_stock_fundamentals()
        data_tasks.update_stock_industry_concept()
        svc.fetch_and_store_fundamentals.assert_called_once()
        svc.fetch_and_store_industry_concept.assert_called_once()


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
    monkeypatch.setattr(etf_task, "_update_etf_daily_quotes_once", lambda: None)
    monkeypatch.setattr(etf_task, "_update_etf_basic_info_once", lambda: None)
    monkeypatch.setattr(etf_task, "update_index_constituents", lambda: None)
    monkeypatch.setattr(etf_task, "update_stock_industry_concept", lambda: None)
    etf_task.start_scheduler()
    assert "update_index_daily" in jobs
    assert "update_stock_daily_from_market_quotes" in jobs
    assert "update_stock_daily_incremental" not in jobs
    assert "update_index_constituents" in jobs
    assert "update_stock_fundamentals" in jobs
    assert "update_stock_industry_concept" in jobs


def test_update_index_valuation_calls_service(monkeypatch):
    with patch("app.tasks.etf_task.valuation_service") as svc:
        from app.tasks import etf_task
        monkeypatch.setattr(etf_task.mongo_client, "ping", lambda: True)
        svc.update_all_index_valuation.return_value = 42
        etf_task.update_index_valuation()
        svc.update_all_index_valuation.assert_called_once()


def test_run_with_retry_retries_transient_failures():
    from app.tasks import etf_task

    attempts = {"n": 0}

    def flaky():
        attempts["n"] += 1
        if attempts["n"] < 3:
            raise RuntimeError("mongo refused")
        return 1

    assert etf_task.run_with_retry("测试任务", flaky, attempts=3, delay=0) is True
    assert attempts["n"] == 3


def test_run_with_retry_returns_false_when_attempts_exhausted():
    from app.tasks import etf_task

    attempts = {"n": 0}

    def always_fail():
        attempts["n"] += 1
        raise RuntimeError("mongo refused")

    assert etf_task.run_with_retry("测试任务", always_fail, attempts=3, delay=0) is False
    assert attempts["n"] == 3


def test_start_scheduler_sets_retry_friendly_job_options(monkeypatch):
    from app.tasks import etf_task

    jobs = []

    class _Sched:
        running = False

        def add_job(self, **kwargs):
            jobs.append(kwargs)

        def start(self):
            self.running = True

    monkeypatch.setattr(etf_task, "scheduler", _Sched())
    monkeypatch.setattr(etf_task, "_update_etf_daily_quotes_once", lambda: None)
    monkeypatch.setattr(etf_task, "_update_etf_basic_info_once", lambda: None)
    monkeypatch.setattr(etf_task, "update_index_constituents", lambda: None)
    monkeypatch.setattr(etf_task, "update_stock_industry_concept", lambda: None)
    etf_task.start_scheduler()

    job = next(j for j in jobs if j["id"] == "update_stock_daily_from_market_quotes")
    assert job["misfire_grace_time"] == 12 * 3600
    assert job["coalesce"] is True
    assert "hour='16'" in str(job["trigger"])


def test_update_stock_daily_from_market_quotes_calls_service(monkeypatch):
    from app.tasks import etf_task

    monkeypatch.setattr(etf_task.mongo_client, "ping", lambda: True)
    with patch.object(etf_task, "stock_service") as svc:
        svc.sync_stock_daily_from_market_quotes.return_value = 5576
        etf_task.update_stock_daily_from_market_quotes()
        svc.sync_stock_daily_from_market_quotes.assert_called_once()


def test_scheduler_uses_asia_shanghai_timezone():
    from app.tasks import etf_task

    assert str(etf_task.scheduler.timezone) == "Asia/Shanghai"


def test_start_scheduler_schedules_startup_tasks_without_blocking(monkeypatch):
    from app.tasks import etf_task

    events = []
    calls = []

    class _Sched:
        running = False

        def add_job(self, **kwargs):
            events.append(("add", kwargs["id"]))

        def start(self):
            events.append(("start",))
            self.running = True

    monkeypatch.setattr(etf_task, "scheduler", _Sched())
    monkeypatch.setattr(etf_task, "_update_etf_daily_quotes_once", lambda: calls.append("etf"))
    monkeypatch.setattr(etf_task, "_update_etf_basic_info_once", lambda: calls.append("basic"))
    monkeypatch.setattr(etf_task, "update_index_constituents", lambda: calls.append("constituents"))
    monkeypatch.setattr(etf_task, "update_stock_industry_concept", lambda: calls.append("industry"))

    etf_task.start_scheduler()

    assert calls == []
    startup_ids = {
        "startup_update_etf_daily_quotes",
        "startup_update_etf_basic_info",
        "startup_update_index_constituents",
        "startup_update_stock_industry_concept",
    }
    added = {event[1] for event in events if event[0] == "add"}
    assert startup_ids <= added
    start_index = events.index(("start",))
    for job_id in startup_ids:
        assert events.index(("add", job_id)) > start_index


def test_ensure_mongo_raises_when_ping_fails(monkeypatch):
    from app.tasks import etf_task

    monkeypatch.setattr(etf_task.mongo_client, "ping", lambda: False)
    with pytest.raises(RuntimeError):
        etf_task._ensure_mongo()


def test_start_scheduler_registers_daily_kline_incremental_at_1620(monkeypatch):
    from app.tasks import etf_task

    jobs = {}

    class _Sched:
        running = False

        def add_job(self, **kwargs):
            jobs[kwargs["id"]] = kwargs

        def start(self):
            self.running = True

    monkeypatch.setattr(etf_task, "scheduler", _Sched())
    etf_task.start_scheduler()

    job = jobs["update_kline_incremental"]
    trigger = str(job["trigger"])
    assert job["func"] is etf_task.update_kline_incremental
    assert "day_of_week='mon-fri'" in trigger
    assert "hour='16'" in trigger
    assert "minute='20'" in trigger

    sweep = jobs["update_kline_weekly_sweep"]
    sweep_trigger = str(sweep["trigger"])
    assert sweep["func"] is etf_task.update_kline_weekly_sweep
    assert "day_of_week='sat'" in sweep_trigger
    assert "hour='2'" in sweep_trigger


def test_update_kline_incremental_only_covers_focus_pool(monkeypatch):
    from types import SimpleNamespace

    from app.tasks import etf_task

    calls = []
    focus = {"etf": ["515180"], "stock": ["600519", "000001"]}

    class _FakeBackfill:
        def __init__(self, **kwargs):
            self.kwargs = kwargs

        def focus_symbols(self, kind):
            return focus[kind]

        def run(self, kind, **kwargs):
            calls.append((kind, kwargs))
            return SimpleNamespace(total=len(kwargs["symbols"]), ok=2, skipped=0, failed=0, rows=10)

    monkeypatch.setattr(etf_task, "_ensure_mongo", lambda: None)
    monkeypatch.setattr(etf_task, "KlineBackfillService", _FakeBackfill)

    etf_task._update_kline_incremental_once()

    assert calls == [
        ("etf", {"symbols": ["515180"], "ensure_index": True}),
        ("stock", {"symbols": ["600519", "000001"], "ensure_index": True}),
    ]


def test_update_kline_incremental_skips_empty_focus_pool(monkeypatch):
    from types import SimpleNamespace

    from app.tasks import etf_task

    calls = []

    class _FakeBackfill:
        def __init__(self, **kwargs):
            pass

        def focus_symbols(self, kind):
            return []

        def run(self, kind, **kwargs):
            calls.append(kind)
            return SimpleNamespace(total=0, ok=0, skipped=0, failed=0, rows=0)

    monkeypatch.setattr(etf_task, "_ensure_mongo", lambda: None)
    monkeypatch.setattr(etf_task, "KlineBackfillService", _FakeBackfill)

    etf_task._update_kline_incremental_once()

    assert calls == []


def test_update_kline_incremental_raises_when_every_symbol_fails(monkeypatch):
    from types import SimpleNamespace

    from app.tasks import etf_task

    class _AllFailed:
        def __init__(self, **kwargs):
            pass

        def focus_symbols(self, kind):
            return ["515180"]

        def run(self, kind, **kwargs):
            return SimpleNamespace(total=2, ok=0, skipped=0, failed=2, rows=0)

    monkeypatch.setattr(etf_task, "_ensure_mongo", lambda: None)
    monkeypatch.setattr(etf_task, "KlineBackfillService", _AllFailed)

    with pytest.raises(RuntimeError):
        etf_task._update_kline_incremental_once()


def test_snapshot_skips_weekend(monkeypatch):
    from datetime import datetime as real_datetime

    from app.tasks import etf_task

    class _SaturdayDatetime(real_datetime):
        @classmethod
        def now(cls, tz=None):
            return real_datetime(2026, 10, 31, 10, 0)  # 周六

    monkeypatch.setattr(etf_task, "datetime", _SaturdayDatetime)
    monkeypatch.setattr(
        etf_task, "_ensure_mongo", lambda: pytest.fail("周末不应写 ETF 日行情快照")
    )

    etf_task._update_etf_daily_quotes_once()
