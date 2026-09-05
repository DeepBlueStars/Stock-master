package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author xian
 * @description 选股榜单与 AI 报告（每日一文档，trade_date 唯一）
 */
@Document(collection = "screening_results")
public class ScreeningResult {
    @Id
    private String id;

    @Field(name = "trade_date")
    @Indexed(unique = true)
    private String tradeDate;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    @Field(name = "value")
    private List<BoardEntry> valueBoard;

    @Field(name = "growth")
    private List<BoardEntry> growthBoard;

    @Field(name = "balanced")
    private List<BoardEntry> balancedBoard;

    @Field(name = "ai_report")
    private String aiReport;

    @Field(name = "ai_report_status")
    private String aiReportStatus;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<BoardEntry> getValueBoard() { return valueBoard; }
    public void setValueBoard(List<BoardEntry> valueBoard) { this.valueBoard = valueBoard; }
    public List<BoardEntry> getGrowthBoard() { return growthBoard; }
    public void setGrowthBoard(List<BoardEntry> growthBoard) { this.growthBoard = growthBoard; }
    public List<BoardEntry> getBalancedBoard() { return balancedBoard; }
    public void setBalancedBoard(List<BoardEntry> balancedBoard) { this.balancedBoard = balancedBoard; }
    public String getAiReport() { return aiReport; }
    public void setAiReport(String aiReport) { this.aiReport = aiReport; }
    public String getAiReportStatus() { return aiReportStatus; }
    public void setAiReportStatus(String aiReportStatus) { this.aiReportStatus = aiReportStatus; }
}
