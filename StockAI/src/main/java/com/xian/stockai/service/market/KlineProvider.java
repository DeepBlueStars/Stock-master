package com.xian.stockai.service.market;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.StockDailyKline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author xian
 * @description 日线统一入口：个股读 stock_daily_kline，ETF 读 etf_daily_quotes 并转换成同一结构。
 * <p>
 * 背景：ETF 的日线只在 etf_daily_quotes 里（stock_daily_kline 仅覆盖部分个股），
 * 原先的上层代码只查 stock_daily_kline，导致 ETF 永远拿不到历史数据而被迫降级。
 * <p>
 * 另外 etf_daily_quotes 的 open/high/low 会被上游写成 0，这里统一做有效性修正：
 * 缺失或非正时用当日收盘价补齐，保证 low &lt;= close &lt;= high，ATR 才会得到合理值
 * （此时代数上退化为「收盘价日均振幅」，比直接降级成固定百分比更贴近真实波动）。
 */
@Component
@Slf4j
public class KlineProvider {

    /**
     * 默认取多少根日线：够 250 日均线、14 日 ATR 与 60 日均线判据
     */
    public static final int DEFAULT_LIMIT = 250;

    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final EtfDailyQuotesRepository etfDailyQuotesRepository;

    public KlineProvider(StockDailyKlineRepository stockDailyKlineRepository,
                         EtfDailyQuotesRepository etfDailyQuotesRepository) {
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.etfDailyQuotesRepository = etfDailyQuotesRepository;
    }

    /**
     * ETF 代码判定，与 MonitorService 现有口径保持一致：1/5 开头视为 ETF/基金。
     */
    public static boolean isEtf(String symbol) {
        return symbol != null && !symbol.isEmpty()
                && (symbol.startsWith("1") || symbol.startsWith("5"));
    }

    /**
     * 取最近 DEFAULT_LIMIT 根日线，按交易日升序返回。
     */
    public List<StockDailyKline> ascending(String symbol) {
        return ascending(symbol, DEFAULT_LIMIT);
    }

    /**
     * 取最近 limit 根日线，按交易日升序返回。
     *
     * @param symbol 标的代码
     * @param limit  取多少根，超过 250 按 250 处理
     * @return 升序日线；无数据返回空列表，不返回 null
     */
    public List<StockDailyKline> ascending(String symbol, int limit) {
        if (symbol == null || symbol.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }
        int size = Math.min(limit, DEFAULT_LIMIT);
        List<StockDailyKline> desc = isEtf(symbol) ? etfKlines(symbol) : stockKlines(symbol);
        if (desc == null || desc.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockDailyKline> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        if (asc.size() > size) {
            asc = new ArrayList<>(asc.subList(asc.size() - size, asc.size()));
        }
        return asc;
    }

    private List<StockDailyKline> stockKlines(String symbol) {
        List<StockDailyKline> klines = stockDailyKlineRepository.findTop250BySymbolOrderByTradeDateDesc(symbol);
        if (klines == null || klines.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockDailyKline> result = new ArrayList<>(klines.size());
        for (StockDailyKline kline : klines) {
            StockDailyKline sanitized = sanitize(kline.getSymbol(), kline.getTradeDate(), kline.getOpen(),
                    kline.getHigh(), kline.getLow(), kline.getClose(), kline.getAmount());
            if (sanitized != null) {
                result.add(sanitized);
            }
        }
        return result;
    }

    private List<StockDailyKline> etfKlines(String symbol) {
        List<EtfDailyQuotes> quotes = etfDailyQuotesRepository.findTop250BySymbolOrderByTradeDateDesc(symbol);
        if (quotes == null || quotes.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockDailyKline> result = new ArrayList<>(quotes.size());
        int missingOhlc = 0;
        for (EtfDailyQuotes quote : quotes) {
            if (!isPositive(quote.getHigh()) || !isPositive(quote.getLow())) {
                missingOhlc++;
            }
            StockDailyKline kline = sanitize(quote.getSymbol(), quote.getTradeDate(), quote.getOpen(),
                    quote.getHigh(), quote.getLow(), quote.getClose(), quote.getAmount());
            if (kline != null) {
                result.add(kline);
            }
        }
        if (missingOhlc > 0) {
            log.warn("ETF 日线缺少有效的最高/最低价，已用收盘价补齐: symbol={}, 缺失 {} / {} 根",
                    symbol, missingOhlc, quotes.size());
        }
        return result;
    }

    private StockDailyKline sanitize(String symbol, String tradeDate, BigDecimal open, BigDecimal high,
                                     BigDecimal low, BigDecimal close, BigDecimal amount) {
        if (symbol == null || tradeDate == null || !isPositive(close)) {
            return null;
        }
        BigDecimal fixedOpen = isPositive(open) ? open : close;
        BigDecimal fixedHigh = isPositive(high) ? high : close;
        BigDecimal fixedLow = isPositive(low) ? low : close;
        // 保证 low <= close <= high，避免上游写脏导致 ATR 为 0 或为负
        if (fixedHigh.compareTo(close) < 0) {
            fixedHigh = close;
        }
        if (fixedLow.compareTo(close) > 0) {
            fixedLow = close;
        }
        if (fixedHigh.compareTo(fixedLow) < 0) {
            fixedHigh = fixedLow;
        }
        StockDailyKline kline = new StockDailyKline();
        kline.setSymbol(symbol);
        kline.setTradeDate(tradeDate);
        kline.setOpen(fixedOpen);
        kline.setHigh(fixedHigh);
        kline.setLow(fixedLow);
        kline.setClose(close);
        kline.setAmount(amount);
        return kline;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
