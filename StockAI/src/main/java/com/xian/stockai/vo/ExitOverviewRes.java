package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 「今日决策」一屏所需的全部数据：持仓纪律线 + 待处理信号 + 上次判定信息。
 * <p>
 * 只有读，不写库、不跑判定——判定由 17:30 的任务负责，这里只是把已算好的状态摆出来。
 * 这样页面刷新再多次，也不会因为「顺手重算」把信号或止盈档位状态搅乱。
 */
@Getter
@Setter
public class ExitOverviewRes {

    /**
     * 判定基准交易日（持仓日线里最新的那个）
     */
    private String tradeDate;

    /**
     * 最近一次判定的开始时间
     */
    private LocalDateTime evaluatedAt;

    /**
     * 最近一次判定的结果统计
     */
    private int evaluated;

    private int triggered;

    private int skipped;

    /**
     * 今天已经确认过的信号条数（页面用来显示「已处理 N 条」）
     */
    private int handledToday;

    private List<Holding> holdings = new ArrayList<>();

    private List<Signal> signals = new ArrayList<>();

    /**
     * 一条持仓的纪律线。stop 取「硬止损」与「已武装的移动止盈价」中更高的那个，
     * takeProfit 是「下一档还没打到的止盈价」，两档都打完了就是 null。
     */
    @Getter
    @Setter
    public static class Holding {

        private String symbol;

        private String name;

        private Integer quantity;

        private BigDecimal price;

        private BigDecimal cost;

        private BigDecimal stop;

        private BigDecimal takeProfit;

        /**
         * 浮动盈亏比例，0.32 表示浮盈 32%
         */
        private BigDecimal returnPct;

        /**
         * 止损价距现价的比例，−0.12 表示还要再跌 12% 才碰到止损
         */
        private BigDecimal distToStop;

        private BigDecimal distToTakeProfit;

        /**
         * ok / warn（接近或已触及止损）/ tp1（有止盈信号待处理）/ nodata
         */
        private String status;

        private Boolean enabled;

        /**
         * 停用、数据未更新等说明，正常为 null
         */
        private String note;
    }

    /**
     * 待用户处理的一条信号
     */
    @Getter
    @Setter
    public static class Signal {

        private String id;

        private String symbol;

        private String name;

        /**
         * L1 / L2 / L4
         */
        private String level;

        /**
         * HARD_STOP / TRAILING_STOP / TAKE_PROFIT_1 / TAKE_PROFIT_2
         */
        private String code;

        /**
         * 展示用短标签：硬止损 / 移动止盈 / 止盈一档 / 止盈二档
         */
        private String tag;

        /**
         * up 表示止盈（红），down 表示止损（绿）
         */
        private String tone;

        private BigDecimal triggerPrice;

        private BigDecimal close;

        private BigDecimal returnPct;

        private String action;

        private String invalidate;

        private String reason;

        private String status;
    }
}
