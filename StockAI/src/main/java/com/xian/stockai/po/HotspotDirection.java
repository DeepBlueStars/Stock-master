package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author xian
 * @description 热点方向（行业或概念）
 */
public class HotspotDirection {
    @Field(name = "name")
    private String name;

    @Field(name = "hot_score")
    private Integer hotScore;

    @Field(name = "daily_avg_change")
    private BigDecimal dailyAvgChange;

    @Field(name = "daily_amount_ratio")
    private BigDecimal dailyAmountRatio;

    @Field(name = "limit_up_count")
    private Integer limitUpCount;

    @Field(name = "five_day_avg_change")
    private BigDecimal fiveDayAvgChange;

    @Field(name = "five_day_amount_ratio")
    private BigDecimal fiveDayAmountRatio;

    @Field(name = "five_day_limit_up_count")
    private Integer fiveDayLimitUpCount;

    @Field(name = "stock_count")
    private Integer stockCount;

    @Field(name = "hype_label")
    private String hypeLabel;

    @Field(name = "hype_basis")
    private String hypeBasis;

    @Field(name = "top_stocks")
    private List<HotspotStock> topStocks;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getHotScore() { return hotScore; }
    public void setHotScore(Integer hotScore) { this.hotScore = hotScore; }
    public BigDecimal getDailyAvgChange() { return dailyAvgChange; }
    public void setDailyAvgChange(BigDecimal dailyAvgChange) { this.dailyAvgChange = dailyAvgChange; }
    public BigDecimal getDailyAmountRatio() { return dailyAmountRatio; }
    public void setDailyAmountRatio(BigDecimal dailyAmountRatio) { this.dailyAmountRatio = dailyAmountRatio; }
    public Integer getLimitUpCount() { return limitUpCount; }
    public void setLimitUpCount(Integer limitUpCount) { this.limitUpCount = limitUpCount; }
    public BigDecimal getFiveDayAvgChange() { return fiveDayAvgChange; }
    public void setFiveDayAvgChange(BigDecimal fiveDayAvgChange) { this.fiveDayAvgChange = fiveDayAvgChange; }
    public BigDecimal getFiveDayAmountRatio() { return fiveDayAmountRatio; }
    public void setFiveDayAmountRatio(BigDecimal fiveDayAmountRatio) { this.fiveDayAmountRatio = fiveDayAmountRatio; }
    public Integer getFiveDayLimitUpCount() { return fiveDayLimitUpCount; }
    public void setFiveDayLimitUpCount(Integer fiveDayLimitUpCount) { this.fiveDayLimitUpCount = fiveDayLimitUpCount; }
    public Integer getStockCount() { return stockCount; }
    public void setStockCount(Integer stockCount) { this.stockCount = stockCount; }
    public String getHypeLabel() { return hypeLabel; }
    public void setHypeLabel(String hypeLabel) { this.hypeLabel = hypeLabel; }
    public String getHypeBasis() { return hypeBasis; }
    public void setHypeBasis(String hypeBasis) { this.hypeBasis = hypeBasis; }
    public List<HotspotStock> getTopStocks() { return topStocks; }
    public void setTopStocks(List<HotspotStock> topStocks) { this.topStocks = topStocks; }
}
