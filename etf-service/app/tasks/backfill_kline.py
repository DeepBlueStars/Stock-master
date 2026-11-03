"""日线历史回填命令行入口。

用法（在 etf-service 目录下运行）：

    # 先空跑 5 只看效果，不写库
    python -m app.tasks.backfill_kline --kind etf --limit 5 --dry-run

    # 全量回填全部 ETF（约 1720 只，限速 1.2s/只，预计 35 分钟）
    python -m app.tasks.backfill_kline --kind etf --ensure-index

    # 顺带清掉周末快照写进去的假交易日（先不加 --prune-orphans 看看有多少）
    python -m app.tasks.backfill_kline --kind etf --full --ensure-index --prune-orphans

    # 只回填指定标的
    python -m app.tasks.backfill_kline --kind etf --symbols 515180,512480
    python -m app.tasks.backfill_kline --kind stock --symbols 600519,000001

    # 强制全量重下（覆盖已有行）
    python -m app.tasks.backfill_kline --kind etf --full --sleep 1.5

脚本可安全重复执行：按 symbol + trade_date upsert，不会产生重复行；
重复跑时默认只补「库里最新交易日之后」的缺口。
"""

import argparse
import logging
import sys

from ..services.kline_backfill_service import (
    KIND_ETF,
    KIND_STOCK,
    KlineBackfillService,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="回填 ETF / 个股历史日线到 MongoDB")
    parser.add_argument("--kind", choices=[KIND_ETF, KIND_STOCK], default=KIND_ETF,
                        help="回填品种：etf（默认）或 stock")
    parser.add_argument("--symbols", default="", help="只回填这些代码，逗号分隔")
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 只（0 表示不限）")
    parser.add_argument("--full", action="store_true", help="全量重下，忽略库中已有进度")
    parser.add_argument("--dry-run", action="store_true", help="只取数不写库，用于检查数据质量")
    parser.add_argument("--sleep", type=float, default=1.2, help="每只之间的间隔秒数（默认 1.2）")
    parser.add_argument("--start", default="2019-01-01", help="全量回填的起始日期")
    parser.add_argument("--max-failures", type=int, default=5,
                        help="连续失败多少只后中止（默认 5）")
    parser.add_argument("--ensure-index", action="store_true",
                        help="回填前自动创建 (symbol, trade_date) 索引（缺索引时写入会慢 100 倍）")
    parser.add_argument("--prune-orphans", action="store_true",
                        help="全量模式下删除库中「不属于真实交易日」的行（周末快照产生的假交易日），默认只统计不删")
    parser.add_argument("--progress-every", type=int, default=20, help="每 N 只打印一次进度")
    return parser


def main(argv=None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    )
    args = build_parser().parse_args(argv)
    symbols = [s.strip() for s in args.symbols.split(",") if s.strip()]

    service = KlineBackfillService(
        sleep_seconds=args.sleep,
        max_consecutive_failures=args.max_failures,
        default_start=args.start,
        progress_every=args.progress_every,
    )
    if not service.mongo.ping():
        logging.error("MongoDB 不可达或认证失败，已中止")
        return 2

    stats = service.run(
        kind=args.kind,
        symbols=symbols or None,
        limit=args.limit or None,
        full=args.full,
        dry_run=args.dry_run,
        ensure_index=args.ensure_index,
        prune_orphans=args.prune_orphans,
    )
    print(("空跑（未写库）：" if args.dry_run else "") + stats.summary())
    if stats.failed_symbols:
        print("失败标的：" + ",".join(stats.failed_symbols[:50]))
    return 0 if stats.failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
