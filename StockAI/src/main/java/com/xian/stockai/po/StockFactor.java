package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 个股四因子快照（screening_scores.stocks[] 嵌套文档）
 */
public class StockFactor {
    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;

    @Field(name = "pe")
    private BigDecimal pe;

    @Field(name = "pb")
    private BigDecimal pb;

    @Field(name = "roe")
    private BigDecimal roe;

    @Field(name = "gross_margin")
    private BigDecimal grossMargin;

    @Field(name = "revenue_yoy")
    private BigDecimal revenueYoy;

    @Field(name = "profit_yoy")
    private BigDecimal profitYoy;

    @Field(name = "momentum_return")
    private BigDecimal momentumReturn;

    @Field(name = "valuation")
    private Integer valuation;

    @Field(name = "growth")
    private Integer growth;

    @Field(name = "quality")
    private Integer quality;

    @Field(name = "momentum")
    private Integer momentum;

    @Field(name = "pe_pct")
    private Integer pePct;

    @Field(name = "roe_pct")
    private Integer roePct;

    @Field(name = "momentum_pct")
    private Integer momentumPct;

    @Field(name = "long_term_rating")
    private String longTermRating;

    @Field(name = "data_date")
    private String dataDate;

    @Field(name = "data_incomplete")
    private Boolean dataIncomplete;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getRoe() { return roe; }
    public void setRoe(BigDecimal roe) { this.roe = roe; }
    public BigDecimal getGrossMargin() { return grossMargin; }
    public void setGrossMargin(BigDecimal grossMargin) { this.grossMargin = grossMargin; }
    public BigDecimal getRevenueYoy() { return revenueYoy; }
    public void setRevenueYoy(BigDecimal revenueYoy) { this.revenueYoy = revenueYoy; }
    public BigDecimal getProfitYoy() { return profitYoy; }
    public void setProfitYoy(BigDecimal profitYoy) { this.profitYoy = profitYoy; }
    public BigDecimal getMomentumReturn() { return momentumReturn; }
    public void setMomentumReturn(BigDecimal momentumReturn) { this.momentumReturn = momentumReturn; }
    public Integer getValuation() { return valuation; }
    public void setValuation(Integer valuation) { this.valuation = valuation; }
    public Integer getGrowth() { return growth; }
    public void setGrowth(Integer growth) { this.growth = growth; }
    public Integer getQuality() { return quality; }
    public void setQuality(Integer quality) { this.quality = quality; }
    public Integer getMomentum() { return momentum; }
    public void setMomentum(Integer momentum) { this.momentum = momentum; }
    public Integer getPePct() { return pePct; }
    public void setPePct(Integer pePct) { this.pePct = pePct; }
    public Integer getRoePct() { return roePct; }
    public void setRoePct(Integer roePct) { this.roePct = roePct; }
    public Integer getMomentumPct() { return momentumPct; }
    public void setMomentumPct(Integer momentumPct) { this.momentumPct = momentumPct; }
    public String getLongTermRating() { return longTermRating; }
    public void setLongTermRating(String longTermRating) { this.longTermRating = longTermRating; }
    public String getDataDate() { return dataDate; }
    public void setDataDate(String dataDate) { this.dataDate = dataDate; }
    public Boolean getDataIncomplete() { return dataIncomplete; }
    public void setDataIncomplete(Boolean dataIncomplete) { this.dataIncomplete = dataIncomplete; }
}
