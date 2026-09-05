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
 * @description 选股全量因子快照（每日一文档，trade_date 唯一）
 */
@Document(collection = "screening_scores")
public class ScreeningScoreRecord {
    @Id
    private String id;

    @Field(name = "trade_date")
    @Indexed(unique = true)
    private String tradeDate;

    @Field(name = "generated_at")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    @Field(name = "stocks")
    private List<StockFactor> stocks;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<StockFactor> getStocks() { return stocks; }
    public void setStocks(List<StockFactor> stocks) { this.stocks = stocks; }
}
