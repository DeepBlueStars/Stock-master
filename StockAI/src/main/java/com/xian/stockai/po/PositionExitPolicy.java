package com.xian.stockai.po;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 卖出纪律（持仓退出策略）。一只持仓一条记录，保存 L1/L2/L4 需要的全部状态。
 * <p>
 * 状态类字段（trailArmed / trailPeakClose / tp1Done / tp2Done）由判定任务每天回写，
 * 不能只靠「现价 vs 成本」重新推算——否则加仓一次就会把已经武装的移动止盈打回未武装，
 * 或者把已经减过的止盈档位重复触发。
 */
@Document(collection = "position_exit_policy")
@CompoundIndex(name = "uk_exit_policy_user_symbol", def = "{'user_id': 1, 'symbol': 1}", unique = true)
@Getter
@Setter
public class PositionExitPolicy {

    @Id
    private String id;

    @Field(name = "user_id")
    private String userId;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "stock_name")
    private String stockName;

    /**
     * 是否参与每日判定
     */
    @Field(name = "enabled")
    private Boolean enabled;

    /**
     * 是否 ETF，建策略时按代码前缀判定并固化，避免后续口径变化
     */
    @Field(name = "etf")
    private Boolean etf;

    /**
     * L1 硬止损比例，0.25 表示跌到成本的 75% 清仓
     */
    @Field(name = "hard_stop_rate")
    private BigDecimal hardStopRate;

    /**
     * L2 移动止盈：浮盈达到该比例才武装
     */
    @Field(name = "trailing_arm_return")
    private BigDecimal trailingArmReturn;

    /**
     * L2 移动止盈回撤宽度
     */
    @Field(name = "trailing_rate")
    private BigDecimal trailingRate;

    /**
     * 保本线浮盈，武装后止损不低于「成本 × (1 + 保本线)」
     */
    @Field(name = "breakeven_return")
    private BigDecimal breakevenReturn;

    /**
     * L4 第一档止盈：浮盈比例与减仓比例
     */
    @Field(name = "take_profit_1_return")
    private BigDecimal takeProfit1Return;

    @Field(name = "take_profit_1_fraction")
    private BigDecimal takeProfit1Fraction;

    /**
     * L4 第二档止盈
     */
    @Field(name = "take_profit_2_return")
    private BigDecimal takeProfit2Return;

    @Field(name = "take_profit_2_fraction")
    private BigDecimal takeProfit2Fraction;

    /**
     * 加仓后多少个交易日内不重置 L2 状态
     */
    @Field(name = "add_buffer_trade_days")
    private Integer addBufferTradeDays;

    /**
     * L2 是否已武装（浮盈曾达到 trailingArmReturn）
     */
    @Field(name = "trail_armed")
    private Boolean trailArmed;

    /**
     * 建仓以来最高收盘价（已复权），只增不减
     */
    @Field(name = "trail_peak_close")
    private BigDecimal trailPeakClose;

    /**
     * 当前移动止盈价位，仅用于展示与审计，判定时会重算
     */
    @Field(name = "trail_floor_price")
    private BigDecimal trailFloorPrice;

    /**
     * 第一档 / 第二档止盈是否已经执行过（触发即锁存，加仓不回退）
     */
    @Field(name = "tp1_done")
    private Boolean tp1Done;

    @Field(name = "tp2_done")
    private Boolean tp2Done;

    /**
     * 本轮建仓的初始数量，分批止盈按它的比例算
     */
    @Field(name = "original_quantity")
    private Integer originalQuantity;

    /**
     * 本轮建仓的起始日期（yyyy-MM-dd），用于取「建仓以来最高价」
     */
    @Field(name = "entry_date")
    private String entryDate;

    /**
     * 最近一次加仓的成交时间与加仓前均价，用于加仓缓冲期
     */
    @Field(name = "last_add_trade_time")
    private LocalDateTime lastAddTradeTime;

    @Field(name = "pre_add_avg_cost")
    private BigDecimal preAddAvgCost;

    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
