package com.xian.stockai.service.tradeplan;

import com.xian.stockai.po.StockDailyKline;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author xian
 * @description 交易计划指标计算（ATR 与关键支撑阻力位），纯计算无副作用
 */
@Component
public class IndicatorCalculator {

    private static final int ATR_SCALE = 6;

    /**
     * 计算 Wilder ATR，K线必须按交易日升序且包含 high/low/close。
     *
     * @param ascKlines 升序K线
     * @param period    ATR 周期
     * @return ATR，数据不足返回 null
     */
    public BigDecimal atr(List<StockDailyKline> ascKlines, int period) {
        if (ascKlines == null || period <= 0 || ascKlines.size() < period + 1) {
            return null;
        }
        List<BigDecimal> trueRanges = new ArrayList<>();
        for (int i = 1; i < ascKlines.size(); i++) {
            StockDailyKline current = ascKlines.get(i);
            StockDailyKline previous = ascKlines.get(i - 1);
            if (current.getHigh() == null || current.getLow() == null || previous.getClose() == null) {
                continue;
            }
            BigDecimal highLow = current.getHigh().subtract(current.getLow());
            BigDecimal highPrevClose = current.getHigh().subtract(previous.getClose()).abs();
            BigDecimal lowPrevClose = current.getLow().subtract(previous.getClose()).abs();
            trueRanges.add(highLow.max(highPrevClose).max(lowPrevClose));
        }
        if (trueRanges.size() < period) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(trueRanges.get(i));
        }
        BigDecimal atr = sum.divide(BigDecimal.valueOf(period), ATR_SCALE, RoundingMode.HALF_UP);
        for (int i = period; i < trueRanges.size(); i++) {
            atr = atr.multiply(BigDecimal.valueOf(period - 1)).add(trueRanges.get(i))
                    .divide(BigDecimal.valueOf(period), ATR_SCALE, RoundingMode.HALF_UP);
        }
        return atr;
    }

    /**
     * 近 window 根K线的最低点作为支撑位。
     */
    public BigDecimal support(List<StockDailyKline> ascKlines, int window) {
        return extreme(ascKlines, window, StockDailyKline::getLow, Comparator.naturalOrder());
    }

    /**
     * 近 window 根K线的最高点作为阻力位。
     */
    public BigDecimal resistance(List<StockDailyKline> ascKlines, int window) {
        return extreme(ascKlines, window, StockDailyKline::getHigh, Comparator.reverseOrder());
    }

    private BigDecimal extreme(List<StockDailyKline> ascKlines, int window,
                               Function<StockDailyKline, BigDecimal> getter,
                               Comparator<BigDecimal> comparator) {
        if (ascKlines == null || ascKlines.isEmpty() || window <= 0) {
            return null;
        }
        int from = Math.max(0, ascKlines.size() - window);
        return ascKlines.subList(from, ascKlines.size()).stream()
                .map(getter)
                .filter(Objects::nonNull)
                .min(comparator)
                .orElse(null);
    }
}
