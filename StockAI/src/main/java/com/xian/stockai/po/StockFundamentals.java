package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 个股财务指标（只读，etf-service 写入）
 */
@Document(collection = "stock_fundamentals")
public class StockFundamentals {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "report_date")
    private String reportDate;

    @Field(name = "pe")
    private BigDecimal pe;

    @Field(name = "pb")
    private BigDecimal pb;

    @Field(name = "roe")
    private BigDecimal roe;

    @Field(name = "revenue_yoy")
    private BigDecimal revenueYoy;

    @Field(name = "profit_yoy")
    private BigDecimal profitYoy;

    @Field(name = "gross_margin")
    private BigDecimal grossMargin;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getRoe() { return roe; }
    public void setRoe(BigDecimal roe) { this.roe = roe; }
    public BigDecimal getRevenueYoy() { return revenueYoy; }
    public void setRevenueYoy(BigDecimal revenueYoy) { this.revenueYoy = revenueYoy; }
    public BigDecimal getProfitYoy() { return profitYoy; }
    public void setProfitYoy(BigDecimal profitYoy) { this.profitYoy = profitYoy; }
    public BigDecimal getGrossMargin() { return grossMargin; }
    public void setGrossMargin(BigDecimal grossMargin) { this.grossMargin = grossMargin; }
}
