package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 持仓止盈止损监控规则
 */
@Document(collection = "position_monitor")
public class PositionMonitor implements Serializable {
    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    @Field(name = "symbol")
    @Indexed
    private String symbol;

    @Field(name = "stock_name")
    private String stockName;

    @Field(name = "type")
    private String type;

    @Field(name = "take_profit_type")
    private String takeProfitType;

    @Field(name = "take_profit_value")
    private BigDecimal takeProfitValue;

    @Field(name = "stop_loss_type")
    private String stopLossType;

    @Field(name = "stop_loss_value")
    private BigDecimal stopLossValue;

    @Field(name = "enabled")
    private Boolean enabled;

    @Field(name = "last_trigger_direction")
    private String lastTriggerDirection;

    @Field(name = "created_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTakeProfitType() { return takeProfitType; }
    public void setTakeProfitType(String takeProfitType) { this.takeProfitType = takeProfitType; }
    public BigDecimal getTakeProfitValue() { return takeProfitValue; }
    public void setTakeProfitValue(BigDecimal takeProfitValue) { this.takeProfitValue = takeProfitValue; }
    public String getStopLossType() { return stopLossType; }
    public void setStopLossType(String stopLossType) { this.stopLossType = stopLossType; }
    public BigDecimal getStopLossValue() { return stopLossValue; }
    public void setStopLossValue(BigDecimal stopLossValue) { this.stopLossValue = stopLossValue; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getLastTriggerDirection() { return lastTriggerDirection; }
    public void setLastTriggerDirection(String lastTriggerDirection) { this.lastTriggerDirection = lastTriggerDirection; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}