package com.xian.stockai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 交易计划配置（ATR 参数、批次档位、周报参数）
 */
@Component
@ConfigurationProperties(prefix = "trade-plan")
public class TradePlanProperties {

    /**
     * ATR 周期
     */
    private int atrPeriod = 14;

    /**
     * 止损 ATR 倍数
     */
    private BigDecimal stopAtrMultiplier = new BigDecimal("2.0");

    /**
     * 风险回报比
     */
    private BigDecimal riskReward = new BigDecimal("2.0");

    /**
     * 支撑阻力回看窗口
     */
    private int supportResistanceWindow = 20;

    /**
     * 高波动阈值（ATR/现价）
     */
    private BigDecimal highVolatilityThreshold = new BigDecimal("0.03");

    /**
     * 中波动阈值（ATR/现价）
     */
    private BigDecimal midVolatilityThreshold = new BigDecimal("0.02");

    /**
     * 生成计划所需最少K线数量
     */
    private int minKlineCount = 15;

    /**
     * 降级止损比例
     */
    private BigDecimal degradedStopLossRate = new BigDecimal("0.05");

    /**
     * 降级止盈比例
     */
    private BigDecimal degradedTakeProfitRate = new BigDecimal("0.10");

    /**
     * 周报保留期数
     */
    private int summaryRetentionWeeks = 12;

    /**
     * 周报生成 cron
     */
    private String summaryCron = "0 0 8 ? * SAT";

    public int getAtrPeriod() {
        return atrPeriod;
    }

    public void setAtrPeriod(int atrPeriod) {
        this.atrPeriod = atrPeriod;
    }

    public BigDecimal getStopAtrMultiplier() {
        return stopAtrMultiplier;
    }

    public void setStopAtrMultiplier(BigDecimal stopAtrMultiplier) {
        this.stopAtrMultiplier = stopAtrMultiplier;
    }

    public BigDecimal getRiskReward() {
        return riskReward;
    }

    public void setRiskReward(BigDecimal riskReward) {
        this.riskReward = riskReward;
    }

    public int getSupportResistanceWindow() {
        return supportResistanceWindow;
    }

    public void setSupportResistanceWindow(int supportResistanceWindow) {
        this.supportResistanceWindow = supportResistanceWindow;
    }

    public BigDecimal getHighVolatilityThreshold() {
        return highVolatilityThreshold;
    }

    public void setHighVolatilityThreshold(BigDecimal highVolatilityThreshold) {
        this.highVolatilityThreshold = highVolatilityThreshold;
    }

    public BigDecimal getMidVolatilityThreshold() {
        return midVolatilityThreshold;
    }

    public void setMidVolatilityThreshold(BigDecimal midVolatilityThreshold) {
        this.midVolatilityThreshold = midVolatilityThreshold;
    }

    public int getMinKlineCount() {
        return minKlineCount;
    }

    public void setMinKlineCount(int minKlineCount) {
        this.minKlineCount = minKlineCount;
    }

    public BigDecimal getDegradedStopLossRate() {
        return degradedStopLossRate;
    }

    public void setDegradedStopLossRate(BigDecimal degradedStopLossRate) {
        this.degradedStopLossRate = degradedStopLossRate;
    }

    public BigDecimal getDegradedTakeProfitRate() {
        return degradedTakeProfitRate;
    }

    public void setDegradedTakeProfitRate(BigDecimal degradedTakeProfitRate) {
        this.degradedTakeProfitRate = degradedTakeProfitRate;
    }

    public int getSummaryRetentionWeeks() {
        return summaryRetentionWeeks;
    }

    public void setSummaryRetentionWeeks(int summaryRetentionWeeks) {
        this.summaryRetentionWeeks = summaryRetentionWeeks;
    }

    public String getSummaryCron() {
        return summaryCron;
    }

    public void setSummaryCron(String summaryCron) {
        this.summaryCron = summaryCron;
    }
}
