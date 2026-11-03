package com.xian.stockai.po;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * @author xian
 * @description ETF 日行情（只读，etf-service 写入）
 * 注意：trade_date 在库中是 "yyyy-MM-dd" 字符串；open/high/low 在部分交易日被上游写成 0，
 * 使用方需自行做有效性校验（见 KlineProvider）。
 */
@Document(collection = "etf_daily_quotes")
public class EtfDailyQuotes {
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

    @Field(name = "volume")
    private BigDecimal volume;

    @Field(name = "amount")
    private BigDecimal amount;

    @Field(name = "pct_change")
    private BigDecimal pctChange;

    @Field(name = "data_source")
    private String dataSource;

    /**
     * 份额折算还原因子：还原价 = 原始价 × adjFactor。折算日之前小于 1、之后为 1，
     * 由 etf-service 的折算检测写入；缺失时按 1 处理。
     */
    @Field(name = "adj_factor")
    private BigDecimal adjFactor;

    @Field(name = "updated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(String tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPctChange() {
        return pctChange;
    }

    public void setPctChange(BigDecimal pctChange) {
        this.pctChange = pctChange;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal getAdjFactor() {
        return adjFactor;
    }

    public void setAdjFactor(BigDecimal adjFactor) {
        this.adjFactor = adjFactor;
    }
}
