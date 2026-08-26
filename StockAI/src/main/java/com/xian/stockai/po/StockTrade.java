package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 交易记录
 */
@Document(collection = "stock_trade")
// 复合索引
//@CompoundIndex(def = "{'user_id': 1, 'symbol': 1}")
public class StockTrade implements Serializable {
    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    /**
     * 股票代码
     */
    @Indexed
    private String symbol;

    /**
     * 股票名称
     */
    @Field(name = "stock_name")
    private String stockName;

    /**
     * 交易方向
     */
    private String side;

    /**
     * 交易数量
     */
    private Integer quantity;

    /**
     * 交易价格
     */
    private BigDecimal price;

    /**
     * 交易总金额
     */
    private BigDecimal amount;

    /**
     * 实际发生金额
     */
    @Field(name = "actual_amount")
    private BigDecimal actualAmount;

    /**
     * 交易时间
     */
    @Field(name = "trade_time")
    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tradeTime;

    @Field(name = "created_at")
    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    /**
     * 佣金
     */
    @Field(name = "commission")
    private BigDecimal commission;

    /**
     * 过户费
     */
    @Field(name = "transfer_fee")
    private BigDecimal transferFee;

    /**
     * 印花税
     */
    @Field(name = "stamp_tax")
    private BigDecimal stampTax;

    /**
     * 股票类型
     */
    @Field(name = "type")
    private String type;
    
    /**
     * 买入方式
     */
    @Field(name = "buy_method")
    private String buyMethod;
    
    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(LocalDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public BigDecimal getTransferFee() {
        return transferFee;
    }

    public void setTransferFee(BigDecimal transferFee) {
        this.transferFee = transferFee;
    }

    public BigDecimal getStampTax() {
        return stampTax;
    }

    public void setStampTax(BigDecimal stampTax) {
        this.stampTax = stampTax;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBuyMethod() {
        return buyMethod;
    }

    public void setBuyMethod(String buyMethod) {
        this.buyMethod = buyMethod;
    }
}