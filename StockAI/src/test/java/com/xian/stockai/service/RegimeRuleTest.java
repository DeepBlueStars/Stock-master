package com.xian.stockai.service;

import com.xian.stockai.po.RegimeIndicators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 方向判据：收盘价与年线（MA250）的位置，带 ±2% 迟滞。
 * 波动率/估值只在极端组合里触发 RISK。
 */
class RegimeRuleTest {

    private static final BigDecimal VOL_HIGH = new BigDecimal("0.30");
    private static final BigDecimal VAL_HIGH = new BigDecimal("0.80");

    /** 收盘 3300、年线 3000 → 高于年线 10%。 */
    private RegimeIndicators base() {
        RegimeIndicators ind = new RegimeIndicators();
        ind.setMa20(new BigDecimal("3200"));
        ind.setMa60(new BigDecimal("3100"));
        ind.setMa250(new BigDecimal("3000"));
        ind.setVolatility(new BigDecimal("0.15"));
        ind.setLatestClose(new BigDecimal("3300"));
        ind.setPePercentile(new BigDecimal("0.50"));
        ind.setPbPercentile(new BigDecimal("0.40"));
        ind.setVolumeRatio(new BigDecimal("0.90"));
        return ind;
    }

    @Test
    void closeWellAboveAnnualLineYieldsBull() {
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(base(), VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void closeWellBelowAnnualLineYieldsBear() {
        RegimeIndicators ind = base();
        ind.setLatestClose(new BigDecimal("2500"));
        assertEquals(RegimeRule.REGIME_BEAR,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void insideBandHoldsPreviousBull() {
        RegimeIndicators ind = base();
        ind.setLatestClose(new BigDecimal("3030")); // +1%，落在迟滞带内
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, RegimeRule.REGIME_BULL));
    }

    @Test
    void insideBandHoldsPreviousBear() {
        RegimeIndicators ind = base();
        ind.setLatestClose(new BigDecimal("2970")); // −1%
        assertEquals(RegimeRule.REGIME_BEAR,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, RegimeRule.REGIME_BEAR));
    }

    @Test
    void insideBandWithoutHistoryYieldsSideways() {
        RegimeIndicators ind = base();
        ind.setLatestClose(new BigDecimal("3030"));
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void insideBandAfterRiskYieldsSideways() {
        RegimeIndicators ind = base();
        ind.setLatestClose(new BigDecimal("2970"));
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, RegimeRule.REGIME_RISK));
    }

    @Test
    void shortMAsNoLongerVetoTheAnnualLine() {
        // 旧规则要求 MA20>MA60>MA250 三层对齐，纠缠就判震荡；现在短均线不参与方向判定。
        RegimeIndicators ind = base();
        ind.setMa20(new BigDecimal("3100"));
        ind.setMa60(new BigDecimal("3150"));
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void highVolatilityAloneNoLongerDowngradesBull() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void highValuationAloneNoLongerDowngradesBull() {
        RegimeIndicators ind = base();
        ind.setPePercentile(new BigDecimal("0.95"));
        ind.setPbPercentile(new BigDecimal("0.90"));
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void extremeCombinationYieldsRisk() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        ind.setLatestClose(new BigDecimal("3100"));
        ind.setPePercentile(new BigDecimal("0.90"));
        ind.setPbPercentile(new BigDecimal("0.85"));
        ind.setVolumeRatio(new BigDecimal("1.50"));
        assertEquals(RegimeRule.REGIME_RISK,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, RegimeRule.REGIME_BULL));
    }

    @Test
    void extremeWithoutHeavyVolumeIsNotRisk() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        ind.setLatestClose(new BigDecimal("3100"));
        ind.setPePercentile(new BigDecimal("0.90"));
        ind.setPbPercentile(new BigDecimal("0.85"));
        ind.setVolumeRatio(new BigDecimal("0.50"));
        // 不放量就不算恐慌：3100 高于年线 3.3%，仍是多头
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, null));
    }

    @Test
    void missingAnnualLineYieldsSideways() {
        RegimeIndicators ind = base();
        ind.setMa250(null);
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH, RegimeRule.REGIME_BULL));
    }

    @Test
    void positionAdviceMapsOneToOne() {
        assertEquals(RegimeRule.ADVICE_AGGRESSIVE, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_BULL));
        assertEquals(RegimeRule.ADVICE_NEUTRAL, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_SIDEWAYS));
        assertEquals(RegimeRule.ADVICE_DEFENSIVE, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_BEAR));
        assertEquals(RegimeRule.ADVICE_CASH, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_RISK));
    }
}
