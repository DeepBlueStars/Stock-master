from unittest.mock import patch

from app.tasks import data_tasks


def test_update_index_constituents_calls_service():
    with patch.object(data_tasks, "index_service") as svc:
        svc.fetch_and_store_constituents.return_value = 800
        data_tasks.update_index_constituents()
        svc.fetch_and_store_constituents.assert_called_once()
