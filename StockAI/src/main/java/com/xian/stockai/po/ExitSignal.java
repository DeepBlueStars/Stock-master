package com.xian.stockai.po;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 卖出信号。每命中一条判定规则生成一条，状态机 ARMED -> TRIGGERED -> ACKED / RECOVERED。
 * <p>
 * 系统只负责「该卖了」的判断与记录，不自动下单；用户在别处卖出后把信号标记为已处理。
 */
@Document(collection = "exit_signals")
@CompoundIndex(name = "idx_exit_signal_user_symbol", def = "{'user_id': 1, 'symbol': 1, 'created_at': -1}")
@Getter
@Setter
public class ExitSignal {

    /**
     * 已触发，等待用户处理
     */
    public static final String STATUS_TRIGGERED = "TRIGGERED";
    /**
     * 用户已确认（可能已卖出，也可能决定继续持有）
     */
    public static final String STATUS_ACKED = "ACKED";
    /**
     * 条件消失，信号自动作废
     */
    public static final String STATUS_RECOVERED = "RECOVERED";

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
     * 判定层级：L1 / L2 / L4
     */
    @Field(name = "level")
    private String level;

    /**
     * 信号码：HARD_STOP / TRAILING_STOP / TAKE_PROFIT_1 / TAKE_PROFIT_2
     */
    @Field(name = "code")
    private String code;

    @Field(name = "status")
    @Indexed
    private String status;

    /**
     * 数据基准交易日（判定用的那根日线）
     */
    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "close")
    private BigDecimal close;

    @Field(name = "avg_cost")
    private BigDecimal avgCost;

    /**
     * 触发时的浮动盈亏比例，0.32 表示浮盈 32%
     */
    @Field(name = "return_pct")
    private BigDecimal returnPct;

    /**
     * 参考触发价（止损价 / 止盈价 / 移动止盈价）
     */
    @Field(name = "trigger_price")
    private BigDecimal triggerPrice;

    /**
     * 建议动作文本，如「全部卖出 700 股」
     */
    @Field(name = "action")
    private String action;

    /**
     * 建议卖出数量，仅供参考
     */
    @Field(name = "suggest_quantity")
    private Integer suggestQuantity;

    /**
     * 触发依据
     */
    @Field(name = "reason")
    private String reason;

    /**
     * 失效条件：什么情况下这条信号不再成立
     */
    @Field(name = "invalidate")
    private String invalidate;

    @Field(name = "created_at")
    @Indexed
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;

    @Field(name = "acked_at")
    private LocalDateTime ackedAt;

    @Field(name = "note")
    private String note;
}
