package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 个股日K线（只读，etf-service 写入；仅映射选股用到的字段）
 */
@Document(collection = "stock_daily_kline")
public class StockDailyKline {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "amount")
    private BigDecimal amount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
