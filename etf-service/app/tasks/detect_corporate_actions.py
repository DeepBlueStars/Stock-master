"""除权 / 份额折算检测命令行入口。

用法（在 etf-service 目录下运行）：

    # 先空跑，只报告不写库
    python -m app.tasks.detect_corporate_actions --kind etf --dry-run

    # 只看某几只
    python -m app.tasks.detect_corporate_actions --kind etf --symbols 512480,518880

    # 正式写 corporate_actions + 回写 adj_factor
    python -m app.tasks.detect_corporate_actions --kind etf
    python -m app.tasks.detect_corporate_actions --kind stock

脚本可重复执行：每次先把这个 symbol 的 adj_factor 重置为 1.0 再重放，
所以不会越跑越小；corporate_actions 里同一 symbol 的旧检测结果会被替换。
"""

import argparse
import logging
import sys

from ..services.corporate_actions_service import (
    KIND_ETF,
    KIND_STOCK,
    CorporateActionService,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="检测 ETF 折算 / 个股除权并还原价格序列")
    parser.add_argument("--kind", choices=[KIND_ETF, KIND_STOCK], default=KIND_ETF,
                        help="检测品种：etf（默认）或 stock")
    parser.add_argument("--symbols", default="", help="只检测这些代码，逗号分隔")
    parser.add_argument("--min-gap", type=float, default=0.25,
                        help="判定跳空的单日涨跌幅阈值（默认 0.25）")
    parser.add_argument("--dry-run", action="store_true", help="只报告不写库")
    return parser


def main(argv=None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
    args = build_parser().parse_args(argv)
    symbols = [s.strip() for s in args.symbols.split(",") if s.strip()] or None

    service = CorporateActionService(min_gap=args.min_gap)
    stats = service.scan(args.kind, symbols=symbols, dry_run=args.dry_run)
    prefix = "[dry-run] " if args.dry_run else ""
    print(
        f"{prefix}{args.kind}: 标的 {stats['symbols']}，跳空 {stats['events']} 次，"
        f"可还原 {stats['applied']} 次，改写 K 线 {stats['bars']} 行"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
