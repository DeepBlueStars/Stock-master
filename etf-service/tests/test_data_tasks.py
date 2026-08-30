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
