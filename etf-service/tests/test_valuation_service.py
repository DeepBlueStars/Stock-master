from app.services.valuation_service import ValuationService
from app.normalize.schema import compute_percentile


def test_compute_percentile():
    hist = [10.0, 20.0, 15.0]
    assert compute_percentile(hist, 15.0) == round(1 / 3, 4)
    assert compute_percentile(hist, 10.0) == 0.0
    assert compute_percentile(hist, 20.0) == round(2 / 3, 4)
    assert compute_percentile([], 10.0) == 0.0


class _FakeAk:
    def fetch_index_valuation_history(self, index_name, indicator):
        if indicator == "pe":
            return [
                {"trade_date": "2024-01-02", "pe": 10.0},
                {"trade_date": "2024-01-03", "pe": 20.0},
                {"trade_date": "2024-01-04", "pe": 15.0},
            ]
        return [
            {"trade_date": "2024-01-02", "pb": 1.0},
            {"trade_date": "2024-01-03", "pb": 2.0},
            {"trade_date": "2024-01-04", "pb": 1.5},
        ]


class _FakeMongo:
    def __init__(self):
        self.written = []

    def upsert_index_valuation(self, data):
        self.written.append(data)
        return True


def test_fetch_index_valuation_writes_and_computes_percentile():
    svc = ValuationService(ak=_FakeAk(), mongo=_FakeMongo())
    n = svc.fetch_index_valuation("sh000300")
    assert n == 3
    by_date = {r["trade_date"]: r for r in svc.mongo.written}
    assert by_date["2024-01-04"]["pe_percentile"] == round(1 / 3, 4)
    assert by_date["2024-01-04"]["pb_percentile"] == round(1 / 3, 4)
    assert by_date["2024-01-02"]["index_code"] == "sh000300"
