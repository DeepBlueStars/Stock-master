package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Document(collection = "index_daily_quotes")
public class IndexDailyQuotes {
    @Id
    private String id;

    @Field(name = "index_code")
    private String indexCode;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "open")
    private BigDecimal open;

    @Field(name = "high")
    private BigDecimal high;

    @Field(name = "low")
    private BigDecimal low;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "volume")
    private BigDecimal volume;

    @Field(name = "amount")
    private BigDecimal amount;

    @Field(name = "data_source")
    private String dataSource;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
}
