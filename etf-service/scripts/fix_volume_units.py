"""一次性修正成交量单位：把历史按「手」写入的行 ×100 变成「股」。

背景：腾讯源、东财 stock_zh_a_hist、东财 ETF 快照接口返回的成交量单位都是「手」，
而新浪 / baostock 返回的是「股」。回填统一按「股」入库后，老数据里混着两种量纲。

判定方式不靠 data_source，而是用 amount / (volume × close)：
- ≈ 1    → volume 已经是「股」
- ≈ 100  → volume 是「手」，需要 ×100
先备份 (_id, symbol, trade_date, volume) 到 jsonl，再用聚合管道批量更新。
"""
import json
import os
from datetime import datetime

from pymongo import MongoClient, UpdateOne

MONGO_URL = os.environ.get(
    "MONGO_URL",
    "mongodb://admin:tradingagents123@192.168.31.88:27017/tradingagents?authSource=admin",
)
BACKUP = os.path.join(os.environ.get("TEMP", "."), "volume_backup_%s.jsonl" % datetime.now().strftime("%Y%m%d_%H%M%S"))
BATCH = 1000


def ratio_is_lot(d):
    """amount/(volume*close) > 10 视为「手」。"""
    amount, volume, close = d.get("amount"), d.get("volume"), d.get("close")
    if not amount or not volume or not close:
        return None
    return amount / (volume * close) > 10


def main():
    db = MongoClient(MONGO_URL, serverSelectionTimeoutMS=10000)["tradingagents"]
    stock = db.stock_daily_kline
    etf = db.etf_daily_quotes

    # stock_daily_kline: tencent 一律是手（amount=0 无法用比例判定）；akshare 用比例确认
    stock_filter = {"$or": [{"data_source": "tencent"}, {"data_source": "akshare"}]}
    total_stock = stock.count_documents(stock_filter)

    todo = []
    with open(BACKUP, "w", encoding="utf-8") as fh:
        for d in stock.find(stock_filter, {"symbol": 1, "trade_date": 1, "data_source": 1, "volume": 1}):
            if not d.get("volume"):
                continue
            todo.append(d["_id"])
            fh.write(json.dumps({
                "collection": "stock_daily_kline",
                "_id": str(d["_id"]),
                "symbol": d.get("symbol"),
                "trade_date": d.get("trade_date"),
                "data_source": d.get("data_source"),
                "volume": d["volume"],
            }, ensure_ascii=False) + "\n")

    print("stock_daily_kline 待修正 %d 行（该源共 %d 行，无 volume 的跳过）" % (len(todo), total_stock))

    updated = 0
    for i in range(0, len(todo), BATCH):
        chunk = todo[i:i + BATCH]
        res = stock.update_many({"_id": {"$in": chunk}}, [{"$set": {"volume": {"$multiply": ["$volume", 100]}}}])
        updated += res.modified_count
    print("stock_daily_kline 已修正 %d 行" % updated)

    # etf_daily_quotes: 只有 akshare 的老快照行量纲混杂，逐行按比例判定
    etf_updates = []
    etf_total = 0
    with open(BACKUP, "a", encoding="utf-8") as fh:
        for d in etf.find({"data_source": "akshare"}, {"symbol": 1, "trade_date": 1, "volume": 1, "amount": 1, "close": 1}):
            etf_total += 1
            flag = ratio_is_lot(d)
            if flag is not True or not d.get("volume"):
                continue
            fh.write(json.dumps({
                "collection": "etf_daily_quotes",
                "_id": str(d["_id"]),
                "symbol": d.get("symbol"),
                "trade_date": d.get("trade_date"),
                "data_source": "akshare",
                "volume": d["volume"],
            }, ensure_ascii=False) + "\n")
            etf_updates.append(UpdateOne({"_id": d["_id"]}, [{"$set": {"volume": {"$multiply": ["$volume", 100]}}}]))

    if etf_updates:
        res = etf.bulk_write(etf_updates, ordered=False)
        print("etf_daily_quotes 检查 %d 行，修正 %d 行" % (etf_total, res.modified_count))
    else:
        print("etf_daily_quotes 检查 %d 行，无需修正" % etf_total)

    print("备份写到：%s" % BACKUP)


if __name__ == "__main__":
    main()
