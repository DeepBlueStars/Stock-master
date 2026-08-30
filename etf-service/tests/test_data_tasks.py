from unittest.mock import patch

from app.tasks import data_tasks


def test_update_index_constituents_calls_service():
    with patch.object(data_tasks, "index_service") as svc:
        svc.fetch_and_store_constituents.return_value = 800
        data_tasks.update_index_constituents()
        svc.fetch_and_store_constituents.assert_called_once()


def test_update_stock_fundamentals_and_industry_concept():
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


def test_update_index_valuation_calls_service():
    with patch("app.tasks.etf_task.valuation_service") as svc:
        svc.update_all_index_valuation.return_value = 42
        from app.tasks.etf_task import update_index_valuation
        update_index_valuation()
        svc.update_all_index_valuation.assert_called_once()
