package com.xian.stockai.service;

import com.xian.stockai.po.RegimeIndicators;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 市场状态判定规则。
 *
 * <p>方向只看一个东西：<b>收盘价与年线（MA250）的位置</b>，并且带迟滞：
 * 站上年线 2% 以上才算多头，跌破 2% 以下才算空头，中间<b>维持上一次的状态</b>。
 * 2003–2026 沪深300 回测（同一仓位口径）：
 *
 * <pre>
 * 方案                年化    回撤    夏普   切换/年
 * 三层对齐(旧)       +6.9%  -49.8%  0.52     7.7
 * 年线               +8.1%  -48.7%  0.56     4.5
 * 年线+迟滞2%(现)    +8.2%  -49.2%  0.56     1.4
 * </pre>
 *
 * <p>波动率和估值只在<b>极端组合</b>（高波动 + 跌破20日线 + 放量 + 高估值）里触发 RISK，
 * 不再用来给多头降档——单看波动率或估值降档只有坏处。
 *
 * <p>定位提醒：这套规则是<b>仓位刹车</b>，不是择时信号。它在 2008/2015 这类趋势性下跌里
 * 降低回撤有效，在震荡市里几乎不产生超额，不要拿它做买点判断。
 */
public final class RegimeRule {
    public static final String REGIME_BULL = "BULL";
    public static final String REGIME_BEAR = "BEAR";
    public static final String REGIME_SIDEWAYS = "SIDEWAYS";
    public static final String REGIME_RISK = "RISK";

    public static final String ADVICE_AGGRESSIVE = "AGGRESSIVE";
    public static final String ADVICE_NEUTRAL = "NEUTRAL";
    public static final String ADVICE_DEFENSIVE = "DEFENSIVE";
    public static final String ADVICE_CASH = "CASH";

    /** 量比阈值：近5日均量/60日均量 > 1.0 视为放量。 */
    private static final BigDecimal VOLUME_RATIO_HIGH = BigDecimal.ONE;

    /** 迟滞带：站上年线 2% 判多、跌破 2% 判空，带内维持上一次状态。 */
    private static final BigDecimal MA250_BAND = new BigDecimal("0.02");

    private RegimeRule() {
    }

    /** 无历史状态时的判定（首次运行）。 */
    public static String determineRegime(RegimeIndicators ind, BigDecimal volatilityHigh, BigDecimal valuationHigh) {
        return determineRegime(ind, volatilityHigh, valuationHigh, null);
    }

    /**
     * @param previousRegime 上一次判定的状态，用于迟滞；传 null 表示没有历史
     */
    public static String determineRegime(RegimeIndicators ind,
                                         BigDecimal volatilityHigh,
                                         BigDecimal valuationHigh,
                                         String previousRegime) {
        BigDecimal volatility = nz(ind.getVolatility());
        BigDecimal valuationPct = nz(ind.getPePercentile()).max(nz(ind.getPbPercentile()));
        BigDecimal close = nz(ind.getLatestClose());
        BigDecimal ma20 = nz(ind.getMa20());

        boolean highVol = volatility.compareTo(volatilityHigh) > 0;
        boolean highValuation = valuationPct.compareTo(valuationHigh) > 0;
        boolean falling = ma20.signum() > 0 && close.compareTo(ma20) < 0;
        boolean heavyVolume = nz(ind.getVolumeRatio()).compareTo(VOLUME_RATIO_HIGH) > 0;

        if (highVol && falling && heavyVolume && highValuation) {
            return REGIME_RISK;
        }

        BigDecimal ma250 = ind.getMa250();
        if (ma250 == null || ma250.signum() <= 0 || close.signum() <= 0) {
            return REGIME_SIDEWAYS;
        }
        BigDecimal deviation = close.subtract(ma250).divide(ma250, 6, RoundingMode.HALF_UP);
        if (deviation.compareTo(MA250_BAND) >= 0) {
            return REGIME_BULL;
        }
        if (deviation.compareTo(MA250_BAND.negate()) <= 0) {
            return REGIME_BEAR;
        }
        // 贴着年线：不产生新结论，维持上一次的多空；没有历史就是震荡
        return REGIME_BULL.equals(previousRegime) || REGIME_BEAR.equals(previousRegime)
                ? previousRegime
                : REGIME_SIDEWAYS;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
