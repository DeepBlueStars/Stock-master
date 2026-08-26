package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @author xian
 * @description 股票基础信息
 */
@Document(collection = "stock_basic_info")
public class StockBasicInfo {
    @Id
    private String id;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
