package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @author xian
 * @description 指数成分股（优质池，只读，etf-service 写入）
 */
@Document(collection = "index_constituents")
public class IndexConstituents {
    @Id
    private String id;

    @Field(name = "index_code")
    private String indexCode;

    @Field(name = "index_name")
    private String indexName;

    @Field(name = "symbol")
    private String symbol;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
