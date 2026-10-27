package com.xian.stockai.vo;

import com.xian.stockai.po.TradePlanSummary;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 交易计划周报返回体（含全文）
 */
public class TradePlanSummaryRes implements Serializable {

    private String weekStart;
    private String weekEnd;
    private LocalDateTime generatedAt;
    private String content;
    private String status;
    private String fallbackReason;

    public static TradePlanSummaryRes from(TradePlanSummary summary) {
        if (summary == null) {
            return null;
        }
        TradePlanSummaryRes res = new TradePlanSummaryRes();
        res.weekStart = summary.getWeekStart();
        res.weekEnd = summary.getWeekEnd();
        res.generatedAt = summary.getGeneratedAt();
        res.content = summary.getContent();
        res.status = summary.getStatus();
        res.fallbackReason = summary.getFallbackReason();
        return res;
    }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }
    public String getWeekEnd() { return weekEnd; }
    public void setWeekEnd(String weekEnd) { this.weekEnd = weekEnd; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
}
