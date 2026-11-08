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
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 交易计划（单股仅保留一条当前计划）
 */
@Document(collection = "trade_plans")
@CompoundIndex(name = "uk_trade_plan_user_symbol", def = "{'user_id': 1, 'symbol': 1}", unique = true)
public class TradePlan implements Serializable {

    public static final String BUY_MODE_BATCHED = "BATCHED";
    public static final String BUY_MODE_LUMP_SUM = "LUMP_SUM";
    public static final String CASH_BASIS_REMAINING = "REMAINING_CASH";
    public static final String CASH_BASIS_UNKNOWN = "UNKNOWN";

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

    @Field(name = "industry")
    private String industry;

    @Field(name = "quantity")
    private Integer quantity;

    @Field(name = "avg_cost")
    private BigDecimal avgCost;

    @Field(name = "last_close")
    private BigDecimal lastClose;

    @Field(name = "as_of_date")
    private String asOfDate;

    @Field(name = "atr14")
    private BigDecimal atr14;

    @Field(name = "atr_ratio")
    private BigDecimal atrRatio;

    @Field(name = "support_20d")
    private BigDecimal support20d;

    @Field(name = "resistance_20d")
    private BigDecimal resistance20d;

    @Field(name = "stop_loss_price")
    private BigDecimal stopLossPrice;

    @Field(name = "take_profit_price")
    private BigDecimal takeProfitPrice;

    @Field(name = "stop_basis")
    private String stopBasis;

    @Field(name = "target_basis")
    private String targetBasis;

    @Field(name = "buy_mode")
    private String buyMode;

    @Field(name = "buy_batches")
    private List<TradePlanBatch> buyBatches = new ArrayList<>();

    @Field(name = "buy_basis")
    private String buyBasis;

    @Field(name = "add_position_rule")
    private AddPositionRule addPositionRule;

    @Field(name = "sell_strategy")
    private SellStrategy sellStrategy;

    @Field(name = "available_cash")
    private BigDecimal availableCash;

    @Field(name = "cash_basis")
    private String cashBasis;

    @Field(name = "degraded")
    private Boolean degraded;

    @Field(name = "degraded_reason")
    private String degradedReason;

    @Field(name = "monitor_linked")
    private Boolean monitorLinked;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

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
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
    public BigDecimal getLastClose() { return lastClose; }
    public void setLastClose(BigDecimal lastClose) { this.lastClose = lastClose; }
    public String getAsOfDate() { return asOfDate; }
    public void setAsOfDate(String asOfDate) { this.asOfDate = asOfDate; }
    public BigDecimal getAtr14() { return atr14; }
    public void setAtr14(BigDecimal atr14) { this.atr14 = atr14; }
    public BigDecimal getAtrRatio() { return atrRatio; }
    public void setAtrRatio(BigDecimal atrRatio) { this.atrRatio = atrRatio; }
    public BigDecimal getSupport20d() { return support20d; }
    public void setSupport20d(BigDecimal support20d) { this.support20d = support20d; }
    public BigDecimal getResistance20d() { return resistance20d; }
    public void setResistance20d(BigDecimal resistance20d) { this.resistance20d = resistance20d; }
    public BigDecimal getStopLossPrice() { return stopLossPrice; }
    public void setStopLossPrice(BigDecimal stopLossPrice) { this.stopLossPrice = stopLossPrice; }
    public BigDecimal getTakeProfitPrice() { return takeProfitPrice; }
    public void setTakeProfitPrice(BigDecimal takeProfitPrice) { this.takeProfitPrice = takeProfitPrice; }
    public String getStopBasis() { return stopBasis; }
    public void setStopBasis(String stopBasis) { this.stopBasis = stopBasis; }
    public String getTargetBasis() { return targetBasis; }
    public void setTargetBasis(String targetBasis) { this.targetBasis = targetBasis; }
    public String getBuyMode() { return buyMode; }
    public void setBuyMode(String buyMode) { this.buyMode = buyMode; }
    public List<TradePlanBatch> getBuyBatches() { return buyBatches; }
    public void setBuyBatches(List<TradePlanBatch> buyBatches) { this.buyBatches = buyBatches; }
    public String getBuyBasis() { return buyBasis; }
    public void setBuyBasis(String buyBasis) { this.buyBasis = buyBasis; }
    public AddPositionRule getAddPositionRule() { return addPositionRule; }
    public void setAddPositionRule(AddPositionRule addPositionRule) { this.addPositionRule = addPositionRule; }
    public SellStrategy getSellStrategy() { return sellStrategy; }
    public void setSellStrategy(SellStrategy sellStrategy) { this.sellStrategy = sellStrategy; }
    public BigDecimal getAvailableCash() { return availableCash; }
    public void setAvailableCash(BigDecimal availableCash) { this.availableCash = availableCash; }
    public String getCashBasis() { return cashBasis; }
    public void setCashBasis(String cashBasis) { this.cashBasis = cashBasis; }
    public Boolean getDegraded() { return degraded; }
    public void setDegraded(Boolean degraded) { this.degraded = degraded; }
    public String getDegradedReason() { return degradedReason; }
    public void setDegradedReason(String degradedReason) { this.degradedReason = degradedReason; }
    public Boolean getMonitorLinked() { return monitorLinked; }
    public void setMonitorLinked(Boolean monitorLinked) { this.monitorLinked = monitorLinked; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 分批买入批次明细
     */
    public static class TradePlanBatch implements Serializable {
        /**
         * 该批占可用资金（或占总仓位）的比例
         */
        @Field(name = "ratio")
        private BigDecimal ratio;

        /**
         * 触发价
         */
        @Field(name = "price")
        private BigDecimal price;

        /**
         * 计划金额，可用资金未知时为 null
         */
        @Field(name = "amount")
        private BigDecimal amount;

        /**
         * 是否已买入（用户手动确认，系统不猜）
         */
        @Field(name = "bought")
        private Boolean bought;

        /**
         * 确认买入的时间
         */
        @Field(name = "bought_at")
        @JSONField(format = "yyyy-MM-dd HH:mm:ss")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime boughtAt;

        public TradePlanBatch() {
        }

        public TradePlanBatch(BigDecimal ratio, BigDecimal price, BigDecimal amount) {
            this.ratio = ratio;
            this.price = price;
            this.amount = amount;
        }

        public BigDecimal getRatio() { return ratio; }
        public void setRatio(BigDecimal ratio) { this.ratio = ratio; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Boolean getBought() { return bought; }
        public void setBought(Boolean bought) { this.bought = bought; }
        public LocalDateTime getBoughtAt() { return boughtAt; }
        public void setBoughtAt(LocalDateTime boughtAt) { this.boughtAt = boughtAt; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    /**
     * 补仓规则
     */
    public static class AddPositionRule implements Serializable {
        @Field(name = "trigger_price")
        private BigDecimal triggerPrice;

        @Field(name = "ratio")
        private BigDecimal ratio;

        @Field(name = "condition_text")
        private String conditionText;

        public AddPositionRule() {
        }

        public AddPositionRule(BigDecimal triggerPrice, BigDecimal ratio, String conditionText) {
            this.triggerPrice = triggerPrice;
            this.ratio = ratio;
            this.conditionText = conditionText;
        }

        public BigDecimal getTriggerPrice() { return triggerPrice; }
        public void setTriggerPrice(BigDecimal triggerPrice) { this.triggerPrice = triggerPrice; }
        public BigDecimal getRatio() { return ratio; }
        public void setRatio(BigDecimal ratio) { this.ratio = ratio; }
        public String getConditionText() { return conditionText; }
        public void setConditionText(String conditionText) { this.conditionText = conditionText; }
    }

    /**
     * 卖出策略
     */
    public static class SellStrategy implements Serializable {
        @Field(name = "take_profit_action")
        private String takeProfitAction;

        @Field(name = "stop_loss_action")
        private String stopLossAction;

        @Field(name = "resistance_note")
        private String resistanceNote;

        public SellStrategy() {
        }

        public SellStrategy(String takeProfitAction, String stopLossAction, String resistanceNote) {
            this.takeProfitAction = takeProfitAction;
            this.stopLossAction = stopLossAction;
            this.resistanceNote = resistanceNote;
        }

        public String getTakeProfitAction() { return takeProfitAction; }
        public void setTakeProfitAction(String takeProfitAction) { this.takeProfitAction = takeProfitAction; }
        public String getStopLossAction() { return stopLossAction; }
        public void setStopLossAction(String stopLossAction) { this.stopLossAction = stopLossAction; }
        public String getResistanceNote() { return resistanceNote; }
        public void setResistanceNote(String resistanceNote) { this.resistanceNote = resistanceNote; }
    }
}
