package com.xian.stockai.po;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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
 * @description 关注池：一个用户一份文档，favorites 是池子里的股票；有报告的股票在 focus 里挂关注档案。
 * <p>
 * 不再单独建集合：个股报告的关注信息就是关注池里某只股票的一个属性。
 * 这里只读写 focus 块，favorites 的其余字段（标签、备注、提醒价）原样保留，不覆盖其他端写入的数据。
 */
@Document(collection = "user_favorites")
@Getter
@Setter
public class UserFavorites {

    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    @Field(name = "favorites")
    private List<FavoriteStock> favorites = new ArrayList<>();

    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    public static class FavoriteStock {

        @Field(name = "stock_code")
        private String stockCode;

        @Field(name = "stock_name")
        private String stockName;

        @Field(name = "market")
        private String market;

        @Field(name = "added_at")
        private LocalDateTime addedAt;

        @Field(name = "tags")
        private List<String> tags = new ArrayList<>();

        @Field(name = "notes")
        private String notes;

        @Field(name = "alert_price_high")
        private BigDecimal alertPriceHigh;

        @Field(name = "alert_price_low")
        private BigDecimal alertPriceLow;

        /**
         * 关注档案：报告 §10 / §12 / §14 的摘要，没有报告时为空
         */
        @Field(name = "focus")
        private FocusProfile focus;
    }

    @Getter
    @Setter
    public static class FocusProfile {

        public static final String STATUS_WATCHING = "WATCHING";
        public static final String STATUS_TRIGGERED = "TRIGGERED";
        public static final String STATUS_HOLDING = "HOLDING";
        public static final String STATUS_PAUSED = "PAUSED";
        public static final String STATUS_DROPPED = "DROPPED";
        public static final String STATUS_CLOSED = "CLOSED";

        public static final String TIER_WAITING = "WAITING";
        public static final String TIER_TRIGGERED = "TRIGGERED";
        public static final String TIER_DONE = "DONE";
        public static final String TIER_CANCELLED = "CANCELLED";

        public static final String TIMELINE_REPORT_UPDATE = "REPORT_UPDATE";
        public static final String TIMELINE_STATUS_CHANGE = "STATUS_CHANGE";
        public static final String TIMELINE_TIER_TRIGGERED = "TIER_TRIGGERED";
        public static final String TIMELINE_NOTE = "NOTE";

        @Field(name = "symbol")
        private String symbol;

        /**
         * 仅用于接口入参，不落库
         */
        @Transient
        private String userId;

        @Field(name = "stock_name")
        private String stockName;

        /**
         * 来源报告 research_report.id
         */
        @Field(name = "report_id")
        private String reportId;

        /**
         * 报告的数据日，不是写入日；用来判断档案是否过期
         */
        @Field(name = "report_date")
        private LocalDate reportDate;

        @Field(name = "rating")
        private String rating;

        /**
         * OPENCLAW / MANUAL
         */
        @Field(name = "source")
        private String source;

        /**
         * WATCHING / TRIGGERED / HOLDING / PAUSED / DROPPED / CLOSED
         */
        @Field(name = "status")
        private String status;

        /**
         * 状态原因，暂停/放弃必填
         */
        @Field(name = "status_note")
        private String statusNote;

        @Field(name = "status_changed_at")
        private LocalDateTime statusChangedAt;

        /**
         * 已建仓时关联的持仓代码，通常与 symbol 相同
         */
        @Field(name = "position_symbol")
        private String positionSymbol;

        /**
         * 一句话立场，如「观察，不买入」
         */
        @Field(name = "stance")
        private String stance;

        /**
         * 报告的一句话结论，如「等价格，不等故事」
         */
        @Field(name = "one_line")
        private String oneLine;

        /**
         * 一句话生意定义
         */
        @Field(name = "core_thesis")
        private String coreThesis;

        @Field(name = "buy_plan")
        private BuyPlan buyPlan;

        @Field(name = "sell_discipline")
        private SellDiscipline sellDiscipline;

        @Field(name = "monitor_items")
        private List<MonitorItem> monitorItems = new ArrayList<>();

        @Field(name = "decision_memo")
        private DecisionMemo decisionMemo;

        /**
         * 服务端追加，最多保留 50 条
         */
        @Field(name = "timeline")
        private List<TimelineItem> timeline = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class BuyPlan {

        /**
         * 以上不追
         */
        @Field(name = "max_buy_price")
        private BigDecimal maxBuyPrice;

        @Field(name = "max_shares")
        private Integer maxShares;

        @Field(name = "max_weight_pct")
        private BigDecimal maxWeightPct;

        @Field(name = "holding_period")
        private String holdingPeriod;

        @Field(name = "rules")
        private List<String> rules = new ArrayList<>();

        @Field(name = "tiers")
        private List<BuyTier> tiers = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class BuyTier {

        @Field(name = "tier")
        private Integer tier;

        @Field(name = "label")
        private String label;

        @Field(name = "price_low")
        private BigDecimal priceLow;

        @Field(name = "price_high")
        private BigDecimal priceHigh;

        @Field(name = "shares")
        private Integer shares;

        @Field(name = "amount_low")
        private BigDecimal amountLow;

        @Field(name = "amount_high")
        private BigDecimal amountHigh;

        @Field(name = "weight_pct")
        private BigDecimal weightPct;

        @Field(name = "condition")
        private String condition;

        /**
         * WAITING / TRIGGERED / DONE / CANCELLED
         */
        @Field(name = "status")
        private String status;

        @Field(name = "note")
        private String note;
    }

    @Getter
    @Setter
    public static class SellDiscipline {

        @Field(name = "targets")
        private List<TargetTier> targets = new ArrayList<>();

        /**
         * 基本面证伪条件
         */
        @Field(name = "invalidation")
        private List<String> invalidation = new ArrayList<>();

        @Field(name = "stops")
        private StopRules stops;

        @Field(name = "add_signals")
        private List<String> addSignals = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class TargetTier {

        @Field(name = "price_low")
        private BigDecimal priceLow;

        @Field(name = "price_high")
        private BigDecimal priceHigh;

        @Field(name = "valuation")
        private String valuation;

        @Field(name = "action")
        private String action;
    }

    @Getter
    @Setter
    public static class StopRules {

        @Field(name = "fundamental")
        private String fundamental;

        @Field(name = "price_stop")
        private String priceStop;

        @Field(name = "portfolio_stop")
        private String portfolioStop;

        @Field(name = "systematic_exception")
        private String systematicException;
    }

    @Getter
    @Setter
    public static class MonitorItem {

        @Field(name = "key")
        private String key;

        @Field(name = "metric")
        private String metric;

        @Field(name = "current_value")
        private String currentValue;

        @Field(name = "threshold")
        private String threshold;

        @Field(name = "action")
        private String action;

        @Field(name = "previous_value")
        private String previousValue;

        @Field(name = "trend")
        private String trend;

        /**
         * 有明确日期的才填，填了自动同步到监测日历
         */
        @Field(name = "event_date")
        private LocalDate eventDate;

        @Field(name = "cadence")
        private String cadence;

        @Field(name = "importance")
        private String importance;
    }

    @Getter
    @Setter
    public static class DecisionMemo {

        @Field(name = "dimensions")
        private List<Dimension> dimensions = new ArrayList<>();

        @Field(name = "empty_position")
        private String emptyPosition;

        @Field(name = "holder")
        private String holder;

        @Field(name = "sell_signals")
        private List<String> sellSignals = new ArrayList<>();

        @Field(name = "add_signals")
        private List<String> addSignals = new ArrayList<>();

        @Field(name = "one_line")
        private String oneLine;
    }

    @Getter
    @Setter
    public static class Dimension {

        @Field(name = "name")
        private String name;

        @Field(name = "conclusion")
        private String conclusion;

        @Field(name = "confidence")
        private String confidence;
    }

    @Getter
    @Setter
    public static class TimelineItem {

        @Field(name = "at")
        private LocalDateTime at;

        /**
         * REPORT_UPDATE / STATUS_CHANGE / TIER_TRIGGERED / NOTE
         */
        @Field(name = "type")
        private String type;

        @Field(name = "text")
        private String text;
    }
}
