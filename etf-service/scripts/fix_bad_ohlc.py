"""修掉日线表里不可用的价格行。

两类脏数据都来自早期「实时快照落库」阶段：
- `close <= 0`：整行没有价格，无法修复，直接删。
- `high/low <= 0` 而 `close > 0`：快照源的兜底源没有 OHLC 字段，被 `.get(..., 0)` 写成 0，
  这里用当日收盘价补齐，等价于 Java 侧 `KlineProvider.sanitize` 的读时兜底，
  但落库修一次可以让所有下游（ATR/MA、图表）都吃到干净数据。

用法：
    python -m scripts.fix_bad_ohlc --dry-run
    python -m scripts.fix_bad_ohlc
"""

from __future__ import annotations

import argparse
import logging
import sys
from typing import Any, Dict, List

from pymongo import UpdateOne

from app.database.mongo import MongoDBClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

COLLECTIONS = ("etf_daily_quotes", "stock_daily_kline")


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="修复日线表里的零价格行")
    parser.add_argument("--dry-run", action="store_true", help="只统计不写入")
    args = parser.parse_args(argv)

    client = MongoDBClient()
    total_deleted = 0
    total_repaired = 0

    for name in COLLECTIONS:
        coll = client.db[name]
        empty = coll.count_documents({"close": {"$lte": 0}})
        broken = coll.count_documents({"close": {"$gt": 0}, "$or": [{"high": {"$lte": 0}}, {"low": {"$lte": 0}}]})
        logger.info("%s: 无价格行 %d、零振幅行 %d", name, empty, broken)

        if args.dry_run or (empty == 0 and broken == 0):
            total_deleted += empty
            total_repaired += broken
            continue

        if empty:
            result = coll.delete_many({"close": {"$lte": 0}})
            total_deleted += result.deleted_count

        ops = []
        for doc in coll.find(
            {"close": {"$gt": 0}, "$or": [{"high": {"$lte": 0}}, {"low": {"$lte": 0}}]},
            {"close": 1, "open": 1},
        ):
            close = doc["close"]
            open_price = doc.get("open") or close
            ops.append(UpdateOne({"_id": doc["_id"]}, {"$set": {"open": open_price, "high": close, "low": close}}))
        if ops:
            result = coll.bulk_write(ops, ordered=False)
            total_repaired += result.modified_count

    logger.info(
        "完成：删除 %d 行、修复 %d 行%s",
        total_deleted,
        total_repaired,
        "（dry-run，未写入）" if args.dry_run else "",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
