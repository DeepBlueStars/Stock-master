package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 交易计划周报（每用户每周一期）
 */
@Document(collection = "trade_plan_summary")
@CompoundIndex(name = "uk_summary_user_week", def = "{'user_id': 1, 'week_start': 1}", unique = true)
public class TradePlanSummary implements Serializable {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FALLBACK = "fallback";

    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    /**
     * 周期起始日（周一，yyyy-MM-dd）
     */
    @Field(name = "week_start")
    private String weekStart;

    /**
     * 周期结束日（周六，yyyy-MM-dd）
     */
    @Field(name = "week_end")
    private String weekEnd;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    /**
     * 周报全文（Markdown）
     */
    @Field(name = "content")
    private String content;

    /**
     * success / fallback
     */
    @Field(name = "status")
    private String status;

    @Field(name = "fallback_reason")
    private String fallbackReason;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
