package com.xian.stockai.vo;

import com.xian.stockai.po.UserFavorites;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 关注池列表项：关注档案 + 服务端现算的行情与派生标签。
 * <p>
 * 前端不再自己算「还差多少」「是否进入买点」，口径统一在这里，
 * 避免和复核线、决策台各算一套。
 */
@Getter
@Setter
public class FocusStockView {

    public static final String FLAG_NEAR_BUY = "NEAR_BUY";
    public static final String FLAG_IN_BUY_ZONE = "IN_BUY_ZONE";
    public static final String FLAG_ABOVE_MAX = "ABOVE_MAX";
    public static final String FLAG_STALE = "STALE";

    /**
     * 关注档案；只有股票本身时为空
     */
    private UserFavorites.FocusProfile profile;

    /**
     * 关注池里的股票代码，没有关注档案时也有值
     */
    private String symbol;

    private String stockName;

    private String market;

    /**
     * 加入关注池的时间
     */
    private LocalDateTime addedAt;

    private List<String> tags = new ArrayList<>();

    private String notes;

    /**
     * 最新收盘价
     */
    private BigDecimal price;

    /**
     * 相对前一交易日的涨跌幅（%）
     */
    private BigDecimal changePct;

    /**
     * 最新收盘价对应的交易日
     */
    private String priceDate;

    /**
     * (第一档上沿 - 现价) / 现价 * 100；负数表示还要跌多少才能到买点
     */
    private BigDecimal distanceToTier1Pct;

    /**
     * NEAR_BUY / IN_BUY_ZONE / ABOVE_MAX / STALE
     */
    private List<String> derivedFlags = new ArrayList<>();

    /**
     * 报告距今天数，用于展示「报告已 N 天未更新」
     */
    private Integer staleDays;
}
