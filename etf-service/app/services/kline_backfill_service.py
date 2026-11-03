"""日线历史回填：把 ETF / 个股的历史K线一次性补齐到 MongoDB。

背景：现有 etf_daily_quotes 是用「实时快照」一天天攒出来的（150 根封顶，
且东财改版后 open/high/low 被写成 0），个股 stock_daily_kline 只有 153 只。
止盈止损要用 MA60 / 移动止盈最高价，必须先把历史补上。

设计要点：
- 增量优先：每只标的先查库里最新交易日，只补之后的缺口；--full 才全量重下。
- 多源兜底：ETF 优先新浪（东财被 IP 级封禁时新浪仍可用），再退回东财。
- 限速 + 熔断：逐只请求之间 sleep；连续 N 只彻底失败就中止，避免把封禁越打越重。
- 脏数据清洗：close<=0 的整行丢弃；high/low 为 0 时用 open/close 补齐。
"""

import logging
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import List, Optional

from ..database.mongo import mongo_client
from ..normalize.schema import to_date

logger = logging.getLogger(__name__)

KIND_ETF = "etf"
KIND_STOCK = "stock"

ETF_SYMBOL_PREFIXES = ("1", "5")


def is_etf_symbol(symbol: str) -> bool:
    """与 Java 侧 KlineProvider.isEtf 保持一致：1/5 开头视为 ETF。

    持仓表和交易计划表里股票、ETF 混存，按品种回填时必须先分流，
    否则会拿 stock_zh_a_daily 去查 ETF 代码，必然失败。
    """
    return symbol.startswith(ETF_SYMBOL_PREFIXES)


@dataclass
class BackfillStats:
    """一轮回填的结果汇总。"""

    total: int = 0
    ok: int = 0
    skipped: int = 0
    failed: int = 0
    rows: int = 0
    orphan_rows: int = 0
    pruned: int = 0
    elapsed_seconds: float = 0.0
    failed_symbols: List[str] = field(default_factory=list)

    def summary(self) -> str:
        extra = ""
        if self.orphan_rows or self.pruned:
            extra = f"，非交易日脏数据 {self.orphan_rows} 行（已清理 {self.pruned} 行）"
        return (
            f"共 {self.total} 只：成功 {self.ok}，已是最新 {self.skipped}，"
            f"失败 {self.failed}，写入 {self.rows} 行{extra}，耗时 {self.elapsed_seconds:.1f}s"
        )


class KlineBackfillService:
    """按标的循环回填历史日线，可安全重复执行（按 symbol+trade_date upsert）。"""

    def __init__(
        self,
        mongo=None,
        sources: Optional[list] = None,
        sleep_seconds: float = 1.2,
        batch_size: int = 500,
        max_consecutive_failures: int = 5,
        default_start: str = "2019-01-01",
        progress_every: int = 20,
        incremental_overlap_days: int = 5,
    ):
        self.mongo = mongo or mongo_client
        self.sources = sources
        self.sleep_seconds = sleep_seconds
        self.batch_size = batch_size
        self.max_consecutive_failures = max_consecutive_failures
        self.default_start = default_start
        self.progress_every = progress_every
        self.incremental_overlap_days = incremental_overlap_days

    # ---------- 数据源 ----------

    def source_chain(self, kind: str) -> list:
        """返回该品种的数据源尝试顺序。"""
        if self.sources is not None:
            return self.sources
        from ..datasource.akshare_source import AkshareSource
        from ..datasource.eastmoney_sina import EastmoneySinaSource

        if kind == KIND_ETF:
            # 新浪不依赖东财域名，实测在东财被封时仍可用，因此放第一位
            return [EastmoneySinaSource(), AkshareSource()]

        from ..datasource.tencent_source import TencentSource

        return [EastmoneySinaSource(), AkshareSource(), TencentSource()]

    # ---------- 标的全集 ----------

    def symbols(self, kind: str, symbols: Optional[List[str]] = None) -> List[str]:
        if symbols:
            return list(symbols)
        if kind == KIND_ETF:
            return self.mongo.get_etf_symbols()
        # 个股范围：指数成分股（沪深300/中证500）+ 已有K线 + 持仓 + 交易计划
        # 持仓/交易计划里可能混着 ETF，按品种分流掉
        found = set(self.mongo.stock_daily_kline.distinct("symbol"))
        symbol_sources = ("index_constituents", "position", "trade_plans")
        for coll_name in symbol_sources:
            try:
                coll = self.mongo.db[coll_name]
                found.update(d.get("symbol") for d in coll.find({}, {"symbol": 1}) if d.get("symbol"))
            except Exception as e:  # noqa: BLE001 集合不存在时忽略
                logger.warning("读取 %s 中的代码失败，跳过: %s", coll_name, e)
        return sorted(s for s in found if s and not is_etf_symbol(s))

    def latest_trade_date(self, kind: str, symbol: str) -> Optional[str]:
        if kind == KIND_ETF:
            return self.mongo.get_latest_etf_trade_date(symbol)
        return self.mongo.get_latest_trade_date(symbol)

    # ---------- 索引 ----------

    def _collection(self, kind: str):
        return self.mongo.etf_daily_quotes if kind == KIND_ETF else self.mongo.stock_daily_kline

    def has_symbol_date_index(self, kind: str) -> bool:
        """库里是否有 (symbol, trade_date) 索引——没有的话 upsert 会退化成全表扫描。"""
        try:
            return any(k != "_id_" for k in self._collection(kind).index_information())
        except Exception:  # noqa: BLE001 取不到索引信息时按「有」处理，不阻断流程
            return True

    def ensure_indexes(self, kind: str) -> str:
        """建 (symbol, trade_date) 索引；已存在则静默复用。"""
        name = self._collection(kind).create_index(
            [("symbol", 1), ("trade_date", 1)], name="symbol_trade_date"
        )
        logger.info("已确保索引 %s.symbol_trade_date", self._collection(kind).name)
        return name

    # ---------- 取数与清洗 ----------

    def fetch(self, kind: str, symbol: str, start_date: str) -> Optional[list]:
        """按顺序尝试各数据源；全部抛异常返回 None，全部为空返回 []。"""
        method = "fetch_etf_daily" if kind == KIND_ETF else "fetch_stock_daily"
        last_error: Optional[Exception] = None
        for source in self.source_chain(kind):
            fn = getattr(source, method, None)
            if fn is None:
                continue
            try:
                rows = fn(symbol, start_date=start_date)
            except Exception as e:  # noqa: BLE001 单源失败切换到下一源
                last_error = e
                logger.warning("数据源 %s 取 %s 日线失败: %s", getattr(source, "name", source), symbol, e)
                continue
            if rows:
                logger.debug("%s 命中数据源 %s，%s 行", symbol, getattr(source, "name", source), len(rows))
                return rows
        if last_error is not None:
            return None
        return []

    @staticmethod
    def clean_rows(rows: list, today: Optional[str] = None) -> list:
        """丢弃无效行、补齐缺失的 OHLC，并按交易日去重（保留最后一条）。"""
        today = today or datetime.now().strftime("%Y-%m-%d")
        by_date = {}
        for r in rows or []:
            trade_date = to_date(r.get("trade_date"))
            if not trade_date or trade_date > today:
                continue
            close = float(r.get("close") or 0)
            if close <= 0:
                continue
            open_ = float(r.get("open") or 0) or close
            high = float(r.get("high") or 0) or max(open_, close)
            low = float(r.get("low") or 0) or min(open_, close)
            r = dict(r)
            r.update(
                trade_date=trade_date,
                open=open_,
                high=max(high, open_, close),
                low=min(low, open_, close),
                close=close,
                volume=float(r.get("volume") or 0),
                amount=float(r.get("amount") or 0),
            )
            by_date[trade_date] = r
        return [by_date[d] for d in sorted(by_date)]

    def _write(self, kind: str, rows: list) -> int:
        if kind == KIND_ETF:
            return self.mongo.bulk_upsert_etf_daily_quotes(rows)
        return self.mongo.bulk_upsert_stock_daily_kline(rows)

    def _save(self, kind: str, rows: list) -> int:
        written = 0
        for i in range(0, len(rows), self.batch_size):
            written += self._write(kind, rows[i:i + self.batch_size])
        return written

    # ---------- 非交易日脏数据 ----------

    def orphan_trade_dates(self, kind: str, symbol: str, valid_dates: set) -> list:
        """库里 trade_date 不属于真实交易日的行。

        判定：不在本次拉取到的交易日集合里，且满足其一 ——
        该日是周末，或落在本次拉取的时间窗口内。
        窗口外的正常交易日（例如只回填 2019 年以后、库里还有 2010 年的老数据）不算脏数据。
        """
        valid = set(valid_dates)
        if not valid:
            return []
        low, high = min(valid), max(valid)
        existing = {
            d.get("trade_date")
            for d in self._collection(kind).find({"symbol": symbol}, {"trade_date": 1})
        }

        def _is_orphan(trade_date: str) -> bool:
            if trade_date in valid:
                return False
            try:
                is_weekend = datetime.strptime(trade_date, "%Y-%m-%d").weekday() >= 5
            except ValueError:
                return False
            return is_weekend or low <= trade_date <= high

        return sorted(d for d in existing if d and _is_orphan(d))

    def prune_orphans(self, kind: str, symbol: str, dates: list) -> int:
        """删除指定的非交易日行；dates 为空时不做任何事。"""
        if not dates:
            return 0
        result = self._collection(kind).delete_many({"symbol": symbol, "trade_date": {"$in": dates}})
        return result.deleted_count

    # ---------- 主流程 ----------

    def _next_start(self, latest: Optional[str]) -> Optional[str]:
        """从库内最新日期往前回溯 overlap 天开始拉，保证当日/近期行能被真实收盘价覆盖。

        盘中实时快照会先插一行当日数据（open/high/low 可能是盘中值甚至 0），
        如果增量严格从「最新日 + 1」开始，当天就永远补不上，那行坏数据会一直留着。
        回溯 `incremental_overlap_days` 天后必须 ≥ 默认起点。
        """
        if not latest:
            return self.default_start
        try:
            latest_day = datetime.strptime(latest, "%Y-%m-%d")
        except ValueError:
            return self.default_start
        start_day = latest_day - timedelta(days=self.incremental_overlap_days)
        start = start_day.strftime("%Y-%m-%d")
        return max(start, self.default_start)

    def run(
        self,
        kind: str = KIND_ETF,
        symbols: Optional[List[str]] = None,
        limit: Optional[int] = None,
        full: bool = False,
        dry_run: bool = False,
        ensure_index: bool = False,
        prune_orphans: bool = False,
    ) -> BackfillStats:
        started = time.time()
        if ensure_index:
            self.ensure_indexes(kind)
        elif not self.has_symbol_date_index(kind):
            logger.warning(
                "%s 缺少 (symbol, trade_date) 索引，逐行 upsert 会退化为全表扫描（约 100ms/行）；"
                "如需自动创建请加 --ensure-index", kind,
            )
        targets = self.symbols(kind, symbols)
        if limit:
            targets = targets[:limit]
        stats = BackfillStats(total=len(targets))
        logger.info("开始回填 %s 日线：%s 只，%s，限速 %.1fs/只",
                    kind, len(targets), "全量" if full else "增量", self.sleep_seconds)

        consecutive_failures = 0
        for index, symbol in enumerate(targets, 1):
            latest = None if full else self.latest_trade_date(kind, symbol)
            start_date = self.default_start if full else self._next_start(latest)
            if start_date is None:
                stats.skipped += 1
            else:
                rows = self.fetch(kind, symbol, start_date)
                if rows is None:
                    stats.failed += 1
                    stats.failed_symbols.append(symbol)
                    consecutive_failures += 1
                    logger.warning("[%s/%s] %s 全部数据源失败（连续第 %s 次）",
                                   index, len(targets), symbol, consecutive_failures)
                    if consecutive_failures >= self.max_consecutive_failures:
                        logger.error("连续 %s 只失败，疑似被封禁，提前中止回填", consecutive_failures)
                        break
                else:
                    consecutive_failures = 0
                    cleaned = self.clean_rows(rows)
                    if not cleaned:
                        stats.skipped += 1
                    else:
                        if not dry_run:
                            stats.rows += self._save(kind, cleaned)
                        else:
                            stats.rows += len(cleaned)
                        stats.ok += 1
                        if full:
                            orphans = self.orphan_trade_dates(
                                kind, symbol, {r["trade_date"] for r in cleaned}
                            )
                            if orphans:
                                stats.orphan_rows += len(orphans)
                                if prune_orphans and not dry_run:
                                    stats.pruned += self.prune_orphans(kind, symbol, orphans)

            if index % self.progress_every == 0:
                logger.info("进度 %s/%s，已写入 %s 行，失败 %s 只",
                            index, len(targets), stats.rows, stats.failed)
            if index < len(targets) and self.sleep_seconds > 0:
                time.sleep(self.sleep_seconds)

        stats.elapsed_seconds = time.time() - started
        logger.info("回填结束：%s", stats.summary())
        return stats


kline_backfill_service = KlineBackfillService()
