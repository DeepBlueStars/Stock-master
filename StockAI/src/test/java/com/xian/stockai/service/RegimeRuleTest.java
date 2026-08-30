package com.xian.stockai.service;

import com.xian.stockai.po.RegimeIndicators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegimeRuleTest {

    private static final BigDecimal VOL_HIGH = new BigDecimal("0.30");
    private static final BigDecimal VAL_HIGH = new BigDecimal("0.80");

    private RegimeIndicators base() {
        RegimeIndicators ind = new RegimeIndicators();
        ind.setMa20(new BigDecimal("3200"));
        ind.setMa60(new BigDecimal("3100"));
        ind.setMa250(new BigDecimal("3000"));
        ind.setVolatility(new BigDecimal("0.15"));
        ind.setLatestClose(new BigDecimal("3300"));
        ind.setPePercentile(new BigDecimal("0.50"));
        ind.setPbPercentile(new BigDecimal("0.40"));
        return ind;
    }

    @Test
    void bullTrendWithLowVolAndMidValuationYieldsBull() {
        assertEquals(RegimeRule.REGIME_BULL,
                RegimeRule.determineRegime(base(), VOL_HIGH, VAL_HIGH));
    }

    @Test
    void bearTrendYieldsBear() {
        RegimeIndicators ind = base();
        ind.setMa20(new BigDecimal("2900"));
        ind.setMa60(new BigDecimal("3000"));
        ind.setMa250(new BigDecimal("3100"));
        assertEquals(RegimeRule.REGIME_BEAR,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void tangledMAsYieldSideways() {
        RegimeIndicators ind = base();
        ind.setMa20(new BigDecimal("3100"));
        ind.setMa60(new BigDecimal("3200"));
        ind.setMa250(new BigDecimal("3000"));
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void highVolFallingAndHighValuationYieldsRisk() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        ind.setLatestClose(new BigDecimal("3100"));
        ind.setPePercentile(new BigDecimal("0.90"));
        ind.setPbPercentile(new BigDecimal("0.85"));
        assertEquals(RegimeRule.REGIME_RISK,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void bullTrendWithHighVolDowngradesToSideways() {
        RegimeIndicators ind = base();
        ind.setVolatility(new BigDecimal("0.45"));
        assertEquals(RegimeRule.REGIME_SIDEWAYS,
                RegimeRule.determineRegime(ind, VOL_HIGH, VAL_HIGH));
    }

    @Test
    void positionAdviceMapsOneToOne() {
        assertEquals(RegimeRule.ADVICE_AGGRESSIVE, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_BULL));
        assertEquals(RegimeRule.ADVICE_NEUTRAL, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_SIDEWAYS));
        assertEquals(RegimeRule.ADVICE_DEFENSIVE, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_BEAR));
        assertEquals(RegimeRule.ADVICE_CASH, RegimeRule.mapPositionAdvice(RegimeRule.REGIME_RISK));
    }
}
