package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 股票基础信息
 */

@Document(collection = "market_quotes")
public class MarketQuotes {
    @Id
    private String id;

    @Field(name = "code")
    private String code;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "pct_chg")
    private BigDecimal pctChg;

    @Field(name = "updated_at")
    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getPctChg() {
        return pctChg;
    }

    public void setPctChg(BigDecimal pctChg) {
        this.pctChg = pctChg;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
