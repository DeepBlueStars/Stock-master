package com.xian.stockai.po;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 热点方向代表股
 */
public class HotspotStock {
    @Field(name = "symbol")
    private String symbol;

    @Field(name = "name")
    private String name;

    @Field(name = "daily_change")
    private BigDecimal dailyChange;

    @Field(name = "five_day_change")
    private BigDecimal fiveDayChange;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getDailyChange() { return dailyChange; }
    public void setDailyChange(BigDecimal dailyChange) { this.dailyChange = dailyChange; }
    public BigDecimal getFiveDayChange() { return fiveDayChange; }
    public void setFiveDayChange(BigDecimal fiveDayChange) { this.fiveDayChange = fiveDayChange; }
}
