package com.xian.stockai.vo;

import com.xian.stockai.po.TradePlan;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 交易计划返回体（含派生盈亏与距离字段）
 */
public class TradePlanRes implements Serializable {

    private String userId;
    private String symbol;
    private String stockName;
    private String industry;
    private Integer quantity;
    private BigDecimal avgCost;
    private BigDecimal lastClose;
    private BigDecimal floatPnl;
    private BigDecimal floatPnlRate;
    private String asOfDate;
    private BigDecimal atr14;
    private BigDecimal atrRatio;
    private BigDecimal support20d;
    private BigDecimal resistance20d;
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;
    private BigDecimal distToStop;
    private BigDecimal distToTarget;
    private String stopBasis;
    private String targetBasis;
    private String buyMode;
    private List<TradePlan.TradePlanBatch> buyBatches = new ArrayList<>();
    private String buyBasis;
    private TradePlan.AddPositionRule addPositionRule;
    private TradePlan.SellStrategy sellStrategy;
    private BigDecimal availableCash;
    private String cashBasis;
    private Boolean degraded;
    private String degradedReason;
    private Boolean monitorLinked;
    private LocalDateTime generatedAt;

    public static TradePlanRes from(TradePlan plan) {
        TradePlanRes res = new TradePlanRes();
        res.userId = plan.getUserId();
        res.symbol = plan.getSymbol();
        res.stockName = plan.getStockName();
        res.industry = plan.getIndustry();
        res.quantity = plan.getQuantity();
        res.avgCost = plan.getAvgCost();
        res.lastClose = plan.getLastClose();
        res.floatPnl = calculateFloatPnl(plan);
        res.floatPnlRate = calculateFloatPnlRate(plan);
        res.asOfDate = plan.getAsOfDate();
        res.atr14 = plan.getAtr14();
        res.atrRatio = plan.getAtrRatio();
        res.support20d = plan.getSupport20d();
        res.resistance20d = plan.getResistance20d();
        res.stopLossPrice = plan.getStopLossPrice();
        res.takeProfitPrice = plan.getTakeProfitPrice();
        res.distToStop = calculateDistance(plan.getLastClose(), plan.getStopLossPrice());
        res.distToTarget = calculateDistance(plan.getLastClose(), plan.getTakeProfitPrice());
        res.stopBasis = plan.getStopBasis();
        res.targetBasis = plan.getTargetBasis();
        res.buyMode = plan.getBuyMode();
        res.buyBatches = plan.getBuyBatches() == null ? new ArrayList<>() : plan.getBuyBatches();
        res.buyBasis = plan.getBuyBasis();
        res.addPositionRule = plan.getAddPositionRule();
        res.sellStrategy = plan.getSellStrategy();
        res.availableCash = plan.getAvailableCash();
        res.cashBasis = plan.getCashBasis();
        res.degraded = Boolean.TRUE.equals(plan.getDegraded());
        res.degradedReason = plan.getDegradedReason();
        res.monitorLinked = Boolean.TRUE.equals(plan.getMonitorLinked());
        res.generatedAt = plan.getGeneratedAt();
        return res;
    }

    private static BigDecimal calculateFloatPnl(TradePlan plan) {
        if (plan.getLastClose() == null || plan.getAvgCost() == null || plan.getQuantity() == null) {
            return null;
        }
        return plan.getLastClose().subtract(plan.getAvgCost())
                .multiply(BigDecimal.valueOf(plan.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateFloatPnlRate(TradePlan plan) {
        if (plan.getLastClose() == null || plan.getAvgCost() == null
                || plan.getAvgCost().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return plan.getLastClose().subtract(plan.getAvgCost())
                .divide(plan.getAvgCost(), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateDistance(BigDecimal lastClose, BigDecimal target) {
        if (lastClose == null || target == null || lastClose.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return target.subtract(lastClose).divide(lastClose, 4, RoundingMode.HALF_UP);
    }

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
    public BigDecimal getFloatPnl() { return floatPnl; }
    public void setFloatPnl(BigDecimal floatPnl) { this.floatPnl = floatPnl; }
    public BigDecimal getFloatPnlRate() { return floatPnlRate; }
    public void setFloatPnlRate(BigDecimal floatPnlRate) { this.floatPnlRate = floatPnlRate; }
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
    public BigDecimal getDistToStop() { return distToStop; }
    public void setDistToStop(BigDecimal distToStop) { this.distToStop = distToStop; }
    public BigDecimal getDistToTarget() { return distToTarget; }
    public void setDistToTarget(BigDecimal distToTarget) { this.distToTarget = distToTarget; }
    public String getStopBasis() { return stopBasis; }
    public void setStopBasis(String stopBasis) { this.stopBasis = stopBasis; }
    public String getTargetBasis() { return targetBasis; }
    public void setTargetBasis(String targetBasis) { this.targetBasis = targetBasis; }
    public String getBuyMode() { return buyMode; }
    public void setBuyMode(String buyMode) { this.buyMode = buyMode; }
    public List<TradePlan.TradePlanBatch> getBuyBatches() { return buyBatches; }
    public void setBuyBatches(List<TradePlan.TradePlanBatch> buyBatches) { this.buyBatches = buyBatches; }
    public String getBuyBasis() { return buyBasis; }
    public void setBuyBasis(String buyBasis) { this.buyBasis = buyBasis; }
    public TradePlan.AddPositionRule getAddPositionRule() { return addPositionRule; }
    public void setAddPositionRule(TradePlan.AddPositionRule addPositionRule) { this.addPositionRule = addPositionRule; }
    public TradePlan.SellStrategy getSellStrategy() { return sellStrategy; }
    public void setSellStrategy(TradePlan.SellStrategy sellStrategy) { this.sellStrategy = sellStrategy; }
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
}
