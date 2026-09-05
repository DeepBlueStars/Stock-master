package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 风格榜单条目（screening_results.value/growth/balanced 嵌套文档）
 */
public class BoardEntry {
    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;

    @Field(name = "total_score")
    private BigDecimal totalScore;

    @Field(name = "valuation")
    private Integer valuation;

    @Field(name = "growth")
    private Integer growth;

    @Field(name = "quality")
    private Integer quality;

    @Field(name = "momentum")
    private Integer momentum;

    @Field(name = "long_term_rating")
    private String longTermRating;

    @Field(name = "reason")
    private String reason;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public Integer getValuation() { return valuation; }
    public void setValuation(Integer valuation) { this.valuation = valuation; }
    public Integer getGrowth() { return growth; }
    public void setGrowth(Integer growth) { this.growth = growth; }
    public Integer getQuality() { return quality; }
    public void setQuality(Integer quality) { this.quality = quality; }
    public Integer getMomentum() { return momentum; }
    public void setMomentum(Integer momentum) { this.momentum = momentum; }
    public String getLongTermRating() { return longTermRating; }
    public void setLongTermRating(String longTermRating) { this.longTermRating = longTermRating; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
