package com.xian.stockai.po;

import java.math.BigDecimal;

public class RegimeIndicators {
    private BigDecimal ma20;
    private BigDecimal ma60;
    private BigDecimal ma250;
    private BigDecimal volumeRatio;
    private BigDecimal volatility;
    private BigDecimal latestClose;
    private BigDecimal pePercentile;
    private BigDecimal pbPercentile;
    private String benchmarkIndex;
    /** 这次判定实际用到的最后一根日线日期，用来核对「判定是否吃到了当天数据」。 */
    private String sourceTradeDate;

    public BigDecimal getMa20() { return ma20; }
    public void setMa20(BigDecimal ma20) { this.ma20 = ma20; }
    public BigDecimal getMa60() { return ma60; }
    public void setMa60(BigDecimal ma60) { this.ma60 = ma60; }
    public BigDecimal getMa250() { return ma250; }
    public void setMa250(BigDecimal ma250) { this.ma250 = ma250; }
    public BigDecimal getVolumeRatio() { return volumeRatio; }
    public void setVolumeRatio(BigDecimal volumeRatio) { this.volumeRatio = volumeRatio; }
    public BigDecimal getVolatility() { return volatility; }
    public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }
    public BigDecimal getLatestClose() { return latestClose; }
    public void setLatestClose(BigDecimal latestClose) { this.latestClose = latestClose; }
    public BigDecimal getPePercentile() { return pePercentile; }
    public void setPePercentile(BigDecimal pePercentile) { this.pePercentile = pePercentile; }
    public BigDecimal getPbPercentile() { return pbPercentile; }
    public void setPbPercentile(BigDecimal pbPercentile) { this.pbPercentile = pbPercentile; }
    public String getBenchmarkIndex() { return benchmarkIndex; }
    public void setBenchmarkIndex(String benchmarkIndex) { this.benchmarkIndex = benchmarkIndex; }
    public String getSourceTradeDate() { return sourceTradeDate; }
    public void setSourceTradeDate(String sourceTradeDate) { this.sourceTradeDate = sourceTradeDate; }
}
