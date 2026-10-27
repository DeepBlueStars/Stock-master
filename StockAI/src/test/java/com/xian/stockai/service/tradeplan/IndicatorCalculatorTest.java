package com.xian.stockai.service.tradeplan;

import com.xian.stockai.po.StockDailyKline;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IndicatorCalculatorTest {

    private final IndicatorCalculator calculator = new IndicatorCalculator();

    private StockDailyKline kline(String date, String high, String low, String close) {
        StockDailyKline k = new StockDailyKline();
        k.setTradeDate(date);
        k.setHigh(new BigDecimal(high));
        k.setLow(new BigDecimal(low));
        k.setClose(new BigDecimal(close));
        return k;
    }

    @Test
    void atrMatchesWilderRecursion() {
        // 前 14 根 TR=2.00 → 初始 ATR=2.000000；
        // 第 15、16 根 TR=4.00 → ATR15=(2*13+4)/14=2.142857，ATR16=(2.142857*13+4)/14=2.275510
        List<StockDailyKline> klines = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            if (i < 14) {
                klines.add(kline("2026-01-" + String.format("%02d", i + 1), "11.00", "9.00", "10.00"));
            } else {
                klines.add(kline("2026-01-" + String.format("%02d", i + 1), "13.00", "9.00", "12.00"));
            }
        }
        BigDecimal atr = calculator.atr(klines, 14);
        assertNotNull(atr);
        assertEquals(0, new BigDecimal("2.275510").compareTo(atr));
    }

    @Test
    void atrReturnsNullWhenKlinesInsufficient() {
        List<StockDailyKline> klines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            klines.add(kline("2026-01-" + String.format("%02d", i + 1), "11.00", "9.00", "10.00"));
        }
        assertNull(calculator.atr(klines, 14));
    }

    @Test
    void supportAndResistanceUseRecentWindow() {
        List<StockDailyKline> klines = new ArrayList<>();
        klines.add(kline("2026-01-01", "100.00", "50.00", "90.00"));
        for (int i = 0; i < 20; i++) {
            klines.add(kline("2026-02-" + String.format("%02d", i + 1), "12.00", "10.00", "11.00"));
        }
        klines.add(kline("2026-02-21", "15.00", "11.00", "14.00"));
        assertEquals(0, new BigDecimal("10.00").compareTo(calculator.support(klines, 20)));
        assertEquals(0, new BigDecimal("15.00").compareTo(calculator.resistance(klines, 20)));
    }
}
