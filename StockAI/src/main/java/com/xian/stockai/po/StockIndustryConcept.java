package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * @author xian
 * @description 个股行业与概念分类（只读，etf-service 写入）
 */
@Document(collection = "stock_industry_concept")
public class StockIndustryConcept {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "industry")
    private String industry;

    @Field(name = "concepts")
    private List<String> concepts;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public List<String> getConcepts() { return concepts; }
    public void setConcepts(List<String> concepts) { this.concepts = concepts; }
}
