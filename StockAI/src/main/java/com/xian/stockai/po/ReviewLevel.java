package com.xian.stockai.po;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 一只持仓的复核线：报告里给出的止盈/止损/复核价位，以及需要复核的时间点。
 * <p>
 * 这些数只用来「到点提醒人去复核」，不等于系统买入卖出纪律；系统不会因为价格穿过这些线自动下单。
 * 一个用户的一只票只会有一份，按 user_id + symbol 唯一。
 */
@Document(collection = "review_level")
@CompoundIndex(name = "idx_review_user_symbol", def = "{'user_id': 1, 'symbol': 1}", unique = true)
@Getter
@Setter
public class ReviewLevel {

    /**
     * 止盈一档
     */
    public static final String CODE_TAKE_PROFIT_1 = "TAKE_PROFIT_1";
    /**
     * 止盈二档
     */
    public static final String CODE_TAKE_PROFIT_2 = "TAKE_PROFIT_2";
    /**
     * 移动止盈的触发/回撤线
     */
    public static final String CODE_TRAILING = "TRAILING";
    /**
     * 价格复核线：到了先重读逻辑，不自动卖
     */
    public static final String CODE_REVIEW = "REVIEW";
    /**
     * 硬风险线
     */
    public static final String CODE_HARD_STOP = "HARD_STOP";
    /**
     * 再平衡/加仓价位
     */
    public static final String CODE_REBALANCE = "REBALANCE";

    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "stock_name")
    private String stockName;

    /**
     * 这些复核线来自哪份报告
     */
    @Field(name = "report_id")
    private String reportId;

    @Field(name = "report_date")
    private LocalDate reportDate;

    /**
     * 价位清单：止盈档、复核线、硬风险线、再平衡价
     */
    @Field(name = "price_levels")
    private List<PriceLevel> priceLevels = new ArrayList<>();

    /**
     * 时间点清单：什么时候该看什么
     */
    @Field(name = "time_reviews")
    private List<TimeReview> timeReviews = new ArrayList<>();

    /**
     * 论文止损条件（文字），触发任意一条就要重读买入逻辑
     */
    @Field(name = "thesis_stops")
    private List<String> thesisStops = new ArrayList<>();

    @Field(name = "note")
    private String note;

    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    public static class PriceLevel {

        /**
         * TAKE_PROFIT_1 / TAKE_PROFIT_2 / TRAILING / REVIEW / HARD_STOP / REBALANCE
         */
        @Field(name = "code")
        private String code;

        /**
         * 展示用名称，如「止盈一档」「价格复核线」
         */
        @Field(name = "label")
        private String label;

        @Field(name = "price")
        private BigDecimal price;

        /**
         * 到价后做什么，如「清仓」「减 200-300 股」「重读论文」
         */
        @Field(name = "action")
        private String action;

        @Field(name = "note")
        private String note;
    }

    @Getter
    @Setter
    public static class TimeReview {

        @Field(name = "date")
        private LocalDate date;

        /**
         * 展示用名称，如「东鹏 Q4 业绩预告」
         */
        @Field(name = "label")
        private String label;

        /**
         * 到点要回答的问题
         */
        @Field(name = "question")
        private String question;

        @Field(name = "note")
        private String note;
    }
}
