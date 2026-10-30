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
    assert "update_stock_daily_incremental" in jobs
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

    job = next(j for j in jobs if j["id"] == "update_stock_daily_incremental")
    assert job["misfire_grace_time"] == 12 * 3600
    assert job["coalesce"] is True


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
