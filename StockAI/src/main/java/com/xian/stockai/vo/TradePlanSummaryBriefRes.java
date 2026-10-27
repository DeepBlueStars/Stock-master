package com.xian.stockai.vo;

import com.xian.stockai.po.TradePlanSummary;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 交易计划周报列表项（不含全文）
 */
public class TradePlanSummaryBriefRes implements Serializable {

    private String weekStart;
    private String weekEnd;
    private LocalDateTime generatedAt;
    private String status;

    public static TradePlanSummaryBriefRes from(TradePlanSummary summary) {
        TradePlanSummaryBriefRes res = new TradePlanSummaryBriefRes();
        res.weekStart = summary.getWeekStart();
        res.weekEnd = summary.getWeekEnd();
        res.generatedAt = summary.getGeneratedAt();
        res.status = summary.getStatus();
        return res;
    }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }
    public String getWeekEnd() { return weekEnd; }
    public void setWeekEnd(String weekEnd) { this.weekEnd = weekEnd; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
