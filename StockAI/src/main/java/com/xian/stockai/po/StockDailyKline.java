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

    @Field(name = "open")
    private BigDecimal open;

    @Field(name = "high")
    private BigDecimal high;

    @Field(name = "low")
    private BigDecimal low;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "amount")
    private BigDecimal amount;

    /**
     * 除权还原因子：还原价 = 原始价 × adjFactor。送转股日之前小于 1、之后为 1，
     * 由 etf-service 的除权检测写入；缺失时按 1 处理。
     */
    @Field(name = "adj_factor")
    private BigDecimal adjFactor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
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
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getAdjFactor() { return adjFactor; }
    public void setAdjFactor(BigDecimal adjFactor) { this.adjFactor = adjFactor; }
}
