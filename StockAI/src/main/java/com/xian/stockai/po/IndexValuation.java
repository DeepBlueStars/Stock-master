package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Document(collection = "index_valuation")
public class IndexValuation {
    @Id
    private String id;

    @Field(name = "index_code")
    private String indexCode;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "pe")
    private BigDecimal pe;

    @Field(name = "pb")
    private BigDecimal pb;

    @Field(name = "pe_percentile")
    private BigDecimal pePercentile;

    @Field(name = "pb_percentile")
    private BigDecimal pbPercentile;

    @Field(name = "data_source")
    private String dataSource;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getPe() { return pe; }
    public void setPe(BigDecimal pe) { this.pe = pe; }
    public BigDecimal getPb() { return pb; }
    public void setPb(BigDecimal pb) { this.pb = pb; }
    public BigDecimal getPePercentile() { return pePercentile; }
    public void setPePercentile(BigDecimal pePercentile) { this.pePercentile = pePercentile; }
    public BigDecimal getPbPercentile() { return pbPercentile; }
    public void setPbPercentile(BigDecimal pbPercentile) { this.pbPercentile = pbPercentile; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
}
