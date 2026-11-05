package com.xian.stockai.service.exit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 卖出纪律的纯函数判定核心：输入成本、收盘价、建仓以来最高价与策略状态，输出今日信号与新状态。
 * <p>
 * 三条规则：
 * <ul>
 *   <li>L1 硬止损：收盘跌到「成本 × (1 − hardStopRate)」以下，全部清仓。这是唯一以成本为锚的规则。</li>
 *   <li>L2 移动止盈：浮盈曾达到 trailingArmReturn 即武装（锁存，不因加仓回退），
 *       此后跌破「最高价 × (1 − trailingRate)」与「成本 × (1 + breakevenReturn)」两者的较高者减半。</li>
 *   <li>L4 分批止盈：浮盈到第一档卖 initial 的 1/3，第二档再卖 1/3，剩 1/3 留给长期持有。</li>
 * </ul>
 * 之所以把「武装」做成锁存状态，是因为原式 {@code max(极值 × 0.85, 成本)} 在刚建仓时
 * 极值等于成本，止损会悄悄变成 −15%，把用户定的 −25% 覆盖掉；只有先武装才不会误伤。
 * <p>
 * 本类不碰数据库、不打日志，方便穷举边界用例。
 */
public final class ExitRuleEngine {

    public static final String LEVEL_L1 = "L1";
    public static final String LEVEL_L2 = "L2";
    public static final String LEVEL_L4 = "L4";

    public static final String CODE_HARD_STOP = "HARD_STOP";
    public static final String CODE_TRAILING_STOP = "TRAILING_STOP";
    public static final String CODE_TAKE_PROFIT_1 = "TAKE_PROFIT_1";
    public static final String CODE_TAKE_PROFIT_2 = "TAKE_PROFIT_2";

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int LOT = 100;

    private ExitRuleEngine() {
    }

    /**
     * 策略参数
     */
    public record Params(BigDecimal trailingArmReturn,
                         BigDecimal trailingRate,
                         BigDecimal breakevenReturn,
                         BigDecimal takeProfit1Return,
                         BigDecimal takeProfit1Fraction,
                         BigDecimal takeProfit2Return,
                         BigDecimal takeProfit2Fraction) {
    }

    /**
     * 单只持仓的判定输入
     *
     * @param avgCost          持仓均价（加仓后是新均价）
     * @param close            最新复权收盘价
     * @param peakClose        建仓以来最高复权收盘价
     * @param hardStopRate     硬止损比例
     * @param trailAnchorCost  移动止盈的锚定成本（加仓缓冲期内传加仓前均价，否则同 avgCost）
     * @param holdingQuantity  当前持仓数量
     * @param originalQuantity 本轮建仓的初始数量
     * @param trailArmed       上期是否已武装
     * @param tp1Done          第一档是否已执行
     * @param tp2Done          第二档是否已执行
     */
    public record Input(BigDecimal avgCost,
                        BigDecimal close,
                        BigDecimal peakClose,
                        BigDecimal hardStopRate,
                        BigDecimal trailAnchorCost,
                        int holdingQuantity,
                        int originalQuantity,
                        boolean trailArmed,
                        boolean tp1Done,
                        boolean tp2Done,
                        Params params) {
    }

    /**
     * 单条信号
     */
    public record Signal(String level,
                         String code,
                         BigDecimal triggerPrice,
                         int suggestQuantity,
                         String action,
                         String reason,
                         String invalidate) {
    }

    /**
     * 判定结果：信号 + 需要回写的状态
     */
    public record Result(List<Signal> signals,
                         boolean trailArmed,
                         BigDecimal trailFloor,
                         BigDecimal peakClose,
                         boolean tp1Done,
                         boolean tp2Done) {
    }

    public static Result evaluate(Input in) {
        BigDecimal cost = in.avgCost();
        BigDecimal close = in.close();
        if (!positive(cost) || !positive(close)) {
            return new Result(List.of(), in.trailArmed(), null, in.peakClose(), in.tp1Done(), in.tp2Done());
        }

        Params p = in.params();
        BigDecimal peak = in.peakClose() == null ? close : in.peakClose().max(close);
        BigDecimal peakReturn = ratio(peak, cost);
        boolean armed = in.trailArmed() || peakReturn.compareTo(p.trailingArmReturn()) >= 0;

        BigDecimal anchor = positive(in.trailAnchorCost()) ? in.trailAnchorCost() : cost;
        BigDecimal floor = null;
        if (armed) {
            BigDecimal byPeak = peak.multiply(ONE.subtract(p.trailingRate()));
            BigDecimal byBreakeven = anchor.multiply(ONE.add(p.breakevenReturn()));
            floor = byPeak.max(byBreakeven);
        }

        BigDecimal hardStopPrice = cost.multiply(ONE.subtract(in.hardStopRate()));
        List<Signal> signals = new ArrayList<>();
        boolean tp1Done = in.tp1Done();
        boolean tp2Done = in.tp2Done();

        if (close.compareTo(hardStopPrice) <= 0) {
            signals.add(new Signal(LEVEL_L1, CODE_HARD_STOP, hardStopPrice, in.holdingQuantity(),
                    String.format("全部卖出 %d 股", in.holdingQuantity()),
                    String.format("收盘 %.3f 已跌破硬止损价 %.3f（成本 %.3f 的 %.0f%%）",
                            close.doubleValue(), hardStopPrice.doubleValue(), cost.doubleValue(),
                            ONE.subtract(in.hardStopRate()).doubleValue() * 100),
                    String.format("收盘连续 2 个交易日回到 %.3f 上方则视为误触，可再评估", hardStopPrice.doubleValue())));
        } else if (floor != null && close.compareTo(floor) <= 0) {
            int half = Math.max(LOT, roundLot(in.holdingQuantity() / 2));
            signals.add(new Signal(LEVEL_L2, CODE_TRAILING_STOP, floor, Math.min(half, in.holdingQuantity()),
                    String.format("减仓 %d 股（约一半）", Math.min(half, in.holdingQuantity())),
                    String.format("收盘 %.3f 跌破移动止盈价 %.3f（最高价 %.3f 回撤 %.0f%%）",
                            close.doubleValue(), floor.doubleValue(), peak.doubleValue(),
                            p.trailingRate().doubleValue() * 100),
                    "最高价不再上行且连续 2 日收复该价位即视为噪音"));
        } else if (!tp1Done && close.compareTo(cost.multiply(ONE.add(p.takeProfit1Return()))) >= 0) {
            int qty = takeProfitQuantity(in);
            tp1Done = true;
            signals.add(new Signal(LEVEL_L4, CODE_TAKE_PROFIT_1,
                    cost.multiply(ONE.add(p.takeProfit1Return())), qty,
                    String.format("减仓 %d 股（第一档）", qty),
                    String.format("收盘 %.3f 达到第一档止盈价 %.3f（成本 +%.0f%%）",
                            close.doubleValue(), cost.multiply(ONE.add(p.takeProfit1Return())).doubleValue(),
                            p.takeProfit1Return().doubleValue() * 100),
                    "跌破移动止盈价后由 L2 接管，本档不再重复提示"));
        } else if (tp1Done && !tp2Done && close.compareTo(cost.multiply(ONE.add(p.takeProfit2Return()))) >= 0) {
            int qty = takeProfitQuantity(in);
            tp2Done = true;
            signals.add(new Signal(LEVEL_L4, CODE_TAKE_PROFIT_2,
                    cost.multiply(ONE.add(p.takeProfit2Return())), qty,
                    String.format("减仓 %d 股（第二档）", qty),
                    String.format("收盘 %.3f 达到第二档止盈价 %.3f（成本 +%.0f%%）",
                            close.doubleValue(), cost.multiply(ONE.add(p.takeProfit2Return())).doubleValue(),
                            p.takeProfit2Return().doubleValue() * 100),
                    "剩余仓位交给 L2 移动止盈与长期持有"));
        }

        return new Result(signals, armed, floor, peak, tp1Done, tp2Done);
    }

    private static int takeProfitQuantity(Input in) {
        int original = in.originalQuantity() > 0 ? in.originalQuantity() : in.holdingQuantity();
        BigDecimal fraction = in.params().takeProfit1Fraction();
        int qty = (int) Math.floor(original * fraction.doubleValue());
        qty = roundLot(qty);
        return Math.max(LOT, Math.min(qty, in.holdingQuantity()));
    }

    private static int roundLot(int quantity) {
        return quantity / LOT * LOT;
    }

    private static BigDecimal ratio(BigDecimal value, BigDecimal base) {
        return value.divide(base, 6, RoundingMode.HALF_UP).subtract(ONE);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
