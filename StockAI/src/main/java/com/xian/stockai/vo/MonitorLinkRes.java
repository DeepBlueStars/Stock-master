package com.xian.stockai.vo;

import com.xian.stockai.po.PositionMonitor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author xian
 * @description 交易计划联动止盈止损提醒的写入结果（含变更前后对比）
 */
public class MonitorLinkRes implements Serializable {

    private Before before;
    private After after;

    public MonitorLinkRes() {
    }

    public MonitorLinkRes(Before before, After after) {
        this.before = before;
        this.after = after;
    }

    public Before getBefore() { return before; }
    public void setBefore(Before before) { this.before = before; }
    public After getAfter() { return after; }
    public void setAfter(After after) { this.after = after; }

    /**
     * 写入前的提醒规则快照
     */
    public static class Before implements Serializable {
        private String takeProfitType;
        private BigDecimal takeProfitValue;
        private String stopLossType;
        private BigDecimal stopLossValue;
        private Boolean enabled;

        public static Before from(PositionMonitor monitor) {
            Before before = new Before();
            if (monitor == null) {
                before.enabled = null;
                return before;
            }
            before.takeProfitType = monitor.getTakeProfitType();
            before.takeProfitValue = monitor.getTakeProfitValue();
            before.stopLossType = monitor.getStopLossType();
            before.stopLossValue = monitor.getStopLossValue();
            before.enabled = monitor.getEnabled();
            return before;
        }

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
    }

    /**
     * 写入后的提醒规则快照
     */
    public static class After implements Serializable {
        private String takeProfitType;
        private BigDecimal takeProfitValue;
        private String stopLossType;
        private BigDecimal stopLossValue;
        private Boolean enabled;

        public static After from(PositionMonitor monitor) {
            After after = new After();
            after.takeProfitType = monitor.getTakeProfitType();
            after.takeProfitValue = monitor.getTakeProfitValue();
            after.stopLossType = monitor.getStopLossType();
            after.stopLossValue = monitor.getStopLossValue();
            after.enabled = monitor.getEnabled();
            return after;
        }

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
    }
}
