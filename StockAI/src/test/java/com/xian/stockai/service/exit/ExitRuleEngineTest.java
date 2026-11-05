package com.xian.stockai.service.exit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitRuleEngineTest {

    private static final ExitRuleEngine.Params PARAMS = new ExitRuleEngine.Params(
            new BigDecimal("0.30"), new BigDecimal("0.15"), new BigDecimal("0.10"),
            new BigDecimal("0.30"), new BigDecimal("0.3333"),
            new BigDecimal("0.60"), new BigDecimal("0.3333"));

    private static ExitRuleEngine.Input input(String cost, String close, String peak, String hardStopRate,
                                              int holding, int original, boolean armed, boolean tp1, boolean tp2) {
        return new ExitRuleEngine.Input(new BigDecimal(cost), new BigDecimal(close),
                peak == null ? null : new BigDecimal(peak), new BigDecimal(hardStopRate), null,
                holding, original, armed, tp1, tp2, PARAMS);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "期望 " + expected + "，实际 " + actual);
    }

    @Test
    void hardStopFiresAtExactlyTheCostLine() {
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "75", "100", "0.25", 700, 700,
                false, false, false));

        assertEquals(1, result.signals().size());
        ExitRuleEngine.Signal signal = result.signals().get(0);
        assertEquals("L1", signal.level());
        assertEquals("HARD_STOP", signal.code());
        assertEquals(700, signal.suggestQuantity());
        assertMoney("75", signal.triggerPrice());
    }

    @Test
    void hardStopDoesNotFireOneYuanAboveTheLine() {
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "76", "100", "0.25", 700, 700,
                false, false, false));

        assertTrue(result.signals().isEmpty());
    }

    @Test
    void fallingBackFromPlusTwentyPercentDoesNotArmTrailingStop() {
        // 涨到 +20% 又跌回来：没武装，就只受 −25% 硬止损保护，不该出现移动止盈信号
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "98", "120", "0.25", 700, 700,
                false, false, false));

        assertTrue(result.signals().isEmpty());
        assertFalse(result.trailArmed());
    }

    @Test
    void armedTrailingStopLocksTenPercentWithBreakevenFloor() {
        // +30% 武装、回撤 15% → 130 × 0.85 = 110.5，高于保本线 110，取 110.5
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "110", "130", "0.25", 700, 700,
                false, false, false));

        assertTrue(result.trailArmed());
        assertMoney("110.5", result.trailFloor());
        assertEquals(1, result.signals().size());
        assertEquals("L2", result.signals().get(0).level());
        assertEquals("TRAILING_STOP", result.signals().get(0).code());
    }

    @Test
    void trailingFloorNeverDropsBelowBreakeven() {
        // 武装后只涨到 +31%，回撤 15% 只剩 111.35，仍高于保本线 110；但若最高价只到 +30%，
        // 0.85 × 130 = 110.5，保本线 110 不起作用——这里验证 floor 取两者较高者
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "120", "130", "0.25", 700, 700,
                true, false, false));

        assertMoney("110.5", result.trailFloor());
    }

    @Test
    void trailingStopSellsAboutHalfOfTheHolding() {
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "109", "130", "0.25", 700, 700,
                true, false, false));

        ExitRuleEngine.Signal signal = result.signals().get(0);
        assertEquals("TRAILING_STOP", signal.code());
        assertEquals(300, signal.suggestQuantity());
    }

    @Test
    void takeProfitOneSellsOneThirdOfTheOriginalPosition() {
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "131", "131", "0.25", 700, 700,
                false, false, false));

        assertEquals(1, result.signals().size());
        ExitRuleEngine.Signal signal = result.signals().get(0);
        assertEquals("L4", signal.level());
        assertEquals("TAKE_PROFIT_1", signal.code());
        assertEquals(200, signal.suggestQuantity());
        assertTrue(result.tp1Done());
    }

    @Test
    void takeProfitTwoFiresOnlyAfterTheFirstTranche() {
        ExitRuleEngine.Result first = ExitRuleEngine.evaluate(input("100", "161", "161", "0.25", 700, 700,
                false, false, false));
        assertEquals("TAKE_PROFIT_1", first.signals().get(0).code());

        ExitRuleEngine.Result second = ExitRuleEngine.evaluate(input("100", "161", "161", "0.25", 700, 700,
                true, true, false));
        assertEquals("TAKE_PROFIT_2", second.signals().get(0).code());
        assertTrue(second.tp2Done());
    }

    @Test
    void takeProfitNeverRepeatsOnceDone() {
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "200", "200", "0.25", 500, 700,
                true, true, true));

        assertTrue(result.signals().isEmpty());
    }

    @Test
    void hardStopWinsOverTakeProfitWhenBothWouldApply() {
        // 成本 100、现价 74：硬止损必触发；即使曾涨到 +80% 也不该再提示止盈
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "74", "180", "0.25", 700, 700,
                true, false, false));

        assertEquals(1, result.signals().size());
        assertEquals("HARD_STOP", result.signals().get(0).code());
    }

    @Test
    void armingStaysLockedEvenIfCurrentPriceIsBelowThePeak() {
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input("100", "125", "130", "0.25", 700, 700,
                true, false, false));

        assertTrue(result.trailArmed());
    }

    @Test
    void missingCostOrPriceProducesNoSignal() {
        ExitRuleEngine.Result noCost = ExitRuleEngine.evaluate(input("0", "10", "10", "0.25", 100, 100,
                false, false, false));
        ExitRuleEngine.Result noPrice = ExitRuleEngine.evaluate(input("10", "0", "10", "0.25", 100, 100,
                false, false, false));

        assertTrue(noCost.signals().isEmpty());
        assertTrue(noPrice.signals().isEmpty());
    }

    @Test
    void addBufferAnchorKeepsTrailingStopNearTheOldCost() {
        // 加仓把均价抬到 115，但缓冲期内移动止盈仍以加仓前的 100 为保本锚
        ExitRuleEngine.Input buffered = new ExitRuleEngine.Input(
                new BigDecimal("115"), new BigDecimal("120"), new BigDecimal("130"),
                new BigDecimal("0.25"), new BigDecimal("100"), 700, 700,
                true, false, false, PARAMS);

        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(buffered);

        // max(130 × 0.85, 100 × 1.10) = 110.5；若用 115 做锚，保本线会变成 126.5 并误触发
        assertMoney("110.5", result.trailFloor());
        assertTrue(result.signals().isEmpty());
    }
}
