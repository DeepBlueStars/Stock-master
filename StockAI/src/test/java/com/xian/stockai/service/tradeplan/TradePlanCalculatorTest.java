package com.xian.stockai.service.tradeplan;

import com.xian.stockai.config.TradePlanProperties;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.TradePlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradePlanCalculatorTest {

    private final TradePlanProperties properties = new TradePlanProperties();
    private final TradePlanCalculator calculator =
            new TradePlanCalculator(new IndicatorCalculator(), properties);

    private Position position(String symbol, int quantity, String avgCost) {
        Position p = new Position();
        p.setUserId("u1");
        p.setSymbol(symbol);
        p.setStockName("测试股");
        p.setTotalQuantity(quantity);
        p.setAvgCostPrice(new BigDecimal(avgCost));
        p.setTotalCost(new BigDecimal(avgCost).multiply(BigDecimal.valueOf(quantity)));
        return p;
    }

    private List<StockDailyKline> klines(String high, String low, String close, int count) {
        List<StockDailyKline> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StockDailyKline k = new StockDailyKline();
            k.setTradeDate("2026-03-" + String.format("%02d", i + 1));
            k.setHigh(new BigDecimal(high));
            k.setLow(new BigDecimal(low));
            k.setClose(new BigDecimal(close));
            klines.add(k);
        }
        return klines;
    }

    @Test
    void stopLossAndTakeProfitFollowAtrAndRiskReward() {
        TradePlan plan = calculator.calculate(
                new TradePlanCalculator.CashContext(new BigDecimal("10000"), TradePlan.CASH_BASIS_REMAINING),
                position("600519", 100, "10.00"), "白酒",
                klines("11.00", "9.00", "10.00", 20), new BigDecimal("10.00"));

        // ATR=2.00；止损=10.00-2*2.00=6.00；风险=4.00；止盈=10.00+2*4.00=18.00
        assertEquals(0, new BigDecimal("6.00").compareTo(plan.getStopLossPrice()));
        assertEquals(0, new BigDecimal("18.00").compareTo(plan.getTakeProfitPrice()));
        assertEquals(0, new BigDecimal("9.00").compareTo(plan.getSupport20d()));
        assertEquals(0, new BigDecimal("11.00").compareTo(plan.getResistance20d()));
        assertFalse(Boolean.TRUE.equals(plan.getDegraded()));
        assertEquals(TradePlan.BUY_MODE_BATCHED, plan.getBuyMode());
        assertEquals(3, plan.getBuyBatches().size());
        assertEquals(0, new BigDecimal("5000.00").compareTo(plan.getBuyBatches().get(0).getAmount()));
        // 第二批 = 10.00-0.5*2.00 = 9.00；第三批 = 8.00 低于支撑 9.00 → 取支撑
        assertEquals(0, new BigDecimal("9.00").compareTo(plan.getBuyBatches().get(1).getPrice()));
        assertEquals(0, new BigDecimal("9.00").compareTo(plan.getBuyBatches().get(2).getPrice()));
        assertNotNull(plan.getAddPositionRule());
        assertEquals(0, new BigDecimal("0.3").compareTo(plan.getAddPositionRule().getRatio()));
        assertNotNull(plan.getSellStrategy());
        assertTrue(plan.getSellStrategy().getTakeProfitAction().contains("18.00"));
        assertTrue(plan.getSellStrategy().getStopLossAction().contains("6.00"));
    }

    @Test
    void midVolatilityUsesTwoBatches() {
        // ATR=0.20，现价 10.00 → 2.00%，落入中波动档 → 2 批（60/40）
        TradePlan plan = calculator.calculate(
                new TradePlanCalculator.CashContext(null, TradePlan.CASH_BASIS_UNKNOWN),
                position("600519", 100, "10.00"), "白酒",
                klines("10.16", "9.96", "10.00", 20), new BigDecimal("10.00"));

        assertEquals(TradePlan.BUY_MODE_BATCHED, plan.getBuyMode());
        assertEquals(2, plan.getBuyBatches().size());
        assertEquals(0, new BigDecimal("0.60").compareTo(plan.getBuyBatches().get(0).getRatio()));
        assertNull(plan.getBuyBatches().get(0).getAmount());
        assertEquals(TradePlan.CASH_BASIS_UNKNOWN, plan.getCashBasis());
    }

    @Test
    void lowVolatilityUsesLumpSum() {
        // ATR=0.12，现价 10.00 → 1.20% < 2.00% → 一次性
        TradePlan plan = calculator.calculate(
                new TradePlanCalculator.CashContext(null, TradePlan.CASH_BASIS_UNKNOWN),
                position("600519", 100, "10.00"), "白酒",
                klines("10.06", "9.94", "10.00", 20), new BigDecimal("10.00"));

        assertEquals(TradePlan.BUY_MODE_LUMP_SUM, plan.getBuyMode());
        assertTrue(plan.getBuyBatches().isEmpty());
        assertEquals(TradePlan.CASH_BASIS_UNKNOWN, plan.getCashBasis());
    }

    @Test
    void degradedWhenKlinesInsufficient() {
        TradePlan plan = calculator.calculate(
                new TradePlanCalculator.CashContext(null, TradePlan.CASH_BASIS_UNKNOWN),
                position("600519", 100, "10.00"), "白酒",
                new ArrayList<>(), new BigDecimal("10.00"));

        assertTrue(Boolean.TRUE.equals(plan.getDegraded()));
        assertEquals(0, new BigDecimal("9.50").compareTo(plan.getStopLossPrice()));
        assertEquals(0, new BigDecimal("11.00").compareTo(plan.getTakeProfitPrice()));
        assertNotNull(plan.getDegradedReason());
        assertEquals(TradePlan.BUY_MODE_LUMP_SUM, plan.getBuyMode());
        assertTrue(plan.getBuyBatches().isEmpty());
    }
}
