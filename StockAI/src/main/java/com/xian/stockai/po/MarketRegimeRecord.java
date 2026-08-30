package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "market_regime_records")
public class MarketRegimeRecord {
    @Id
    private String id;

    @Field(name = "date")
    @Indexed(unique = true)
    private String date;

    @Field(name = "regime")
    private String regime;

    @Field(name = "position_advice")
    private String positionAdvice;

    @Field(name = "indicators")
    private RegimeIndicators indicators;

    @Field(name = "report")
    private String report;

    @Field(name = "created_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public String getPositionAdvice() { return positionAdvice; }
    public void setPositionAdvice(String positionAdvice) { this.positionAdvice = positionAdvice; }
    public RegimeIndicators getIndicators() { return indicators; }
    public void setIndicators(RegimeIndicators indicators) { this.indicators = indicators; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
