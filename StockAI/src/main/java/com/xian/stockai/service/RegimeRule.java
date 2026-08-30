package com.xian.stockai.service;

import com.xian.stockai.po.RegimeIndicators;

import java.math.BigDecimal;

public final class RegimeRule {
    public static final String REGIME_BULL = "BULL";
    public static final String REGIME_BEAR = "BEAR";
    public static final String REGIME_SIDEWAYS = "SIDEWAYS";
    public static final String REGIME_RISK = "RISK";

    public static final String ADVICE_AGGRESSIVE = "AGGRESSIVE";
    public static final String ADVICE_NEUTRAL = "NEUTRAL";
    public static final String ADVICE_DEFENSIVE = "DEFENSIVE";
    public static final String ADVICE_CASH = "CASH";

    private RegimeRule() {
    }

    public static String determineRegime(RegimeIndicators ind, BigDecimal volatilityHigh, BigDecimal valuationHigh) {
        boolean bullTrend = ind.getMa20().compareTo(ind.getMa60()) > 0
                && ind.getMa60().compareTo(ind.getMa250()) > 0;
        boolean bearTrend = ind.getMa20().compareTo(ind.getMa60()) < 0
                && ind.getMa60().compareTo(ind.getMa250()) < 0;
        boolean highVol = ind.getVolatility().compareTo(volatilityHigh) > 0;
        BigDecimal valuationPct = ind.getPePercentile().max(ind.getPbPercentile());
        boolean highValuation = valuationPct.compareTo(valuationHigh) > 0;
        boolean falling = ind.getLatestClose().compareTo(ind.getMa20()) < 0;

        if (highVol && falling && highValuation) {
            return REGIME_RISK;
        }
        if (bullTrend) {
            return (!highVol && !highValuation) ? REGIME_BULL : REGIME_SIDEWAYS;
        }
        if (bearTrend) {
            return REGIME_BEAR;
        }
        return REGIME_SIDEWAYS;
    }

    public static String mapPositionAdvice(String regime) {
        return switch (regime) {
            case REGIME_BULL -> ADVICE_AGGRESSIVE;
            case REGIME_BEAR -> ADVICE_DEFENSIVE;
            case REGIME_RISK -> ADVICE_CASH;
            default -> ADVICE_NEUTRAL;
        };
    }
}
