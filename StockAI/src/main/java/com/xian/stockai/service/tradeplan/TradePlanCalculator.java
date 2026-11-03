package com.xian.stockai.service.tradeplan;

import com.xian.stockai.config.TradePlanProperties;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.TradePlan;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 交易计划计算：止盈止损、分批买入、补仓规则与卖出策略
 */
@Component
public class TradePlanCalculator {

    private static final int PRICE_SCALE = 2;
    private static final int RATIO_SCALE = 4;
    private static final BigDecimal ADD_POSITION_RATIO = new BigDecimal("0.3");
    private static final BigDecimal NEAR_RESISTANCE_RATE = new BigDecimal("0.02");

    private final IndicatorCalculator indicator;
    private final TradePlanProperties properties;

    public TradePlanCalculator(IndicatorCalculator indicator, TradePlanProperties properties) {
        this.indicator = indicator;
        this.properties = properties;
    }

    /**
     * 现金口径：可用资金与来源（REMAINING_CASH / UNKNOWN）
     */
    public record CashContext(BigDecimal availableCash, String cashBasis) {
    }

    /**
     * 生成单股交易计划。
     *
     * @param cash      现金口径
     * @param position  持仓
     * @param industry  行业
     * @param ascKlines 升序日K线
     * @param lastClose 最新收盘价
     * @return 交易计划
     */
    public TradePlan calculate(CashContext cash, Position position, String industry,
                               List<StockDailyKline> ascKlines, BigDecimal lastClose) {
        TradePlan plan = new TradePlan();
        plan.setUserId(position.getUserId());
        plan.setSymbol(position.getSymbol());
        plan.setStockName(position.getStockName());
        plan.setIndustry(industry);
        plan.setQuantity(position.getTotalQuantity());
        plan.setAvgCost(position.getAvgCostPrice());
        plan.setLastClose(lastClose);
        plan.setAsOfDate(lastKlineDate(ascKlines));
        plan.setGeneratedAt(java.time.LocalDateTime.now());
        plan.setUpdatedAt(plan.getGeneratedAt());
        plan.setCashBasis(cash == null || cash.cashBasis() == null
                ? TradePlan.CASH_BASIS_UNKNOWN : cash.cashBasis());
        plan.setAvailableCash(cash == null ? null : cash.availableCash());
        plan.setMonitorLinked(false);
        plan.setDegraded(false);

        BigDecimal atr = indicator.atr(ascKlines, properties.getAtrPeriod());
        if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0
                || ascKlines == null || ascKlines.size() < properties.getMinKlineCount()
                || lastClose == null || lastClose.compareTo(BigDecimal.ZERO) <= 0) {
            applyDegraded(plan, position, "历史K线不足或现价缺失，降级为固定百分比");
            return plan;
        }

        BigDecimal stopLoss = position.getAvgCostPrice()
                .subtract(properties.getStopAtrMultiplier().multiply(atr))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        if (stopLoss.compareTo(BigDecimal.ZERO) <= 0) {
            applyDegraded(plan, position, "ATR 过大导致止损价非正，降级为固定百分比");
            return plan;
        }
        BigDecimal risk = position.getAvgCostPrice().subtract(stopLoss);
        BigDecimal takeProfit = position.getAvgCostPrice()
                .add(properties.getRiskReward().multiply(risk))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        plan.setAtr14(atr.setScale(RATIO_SCALE, RoundingMode.HALF_UP));
        plan.setAtrRatio(atr.divide(lastClose, RATIO_SCALE, RoundingMode.HALF_UP));
        plan.setSupport20d(indicator.support(ascKlines, properties.getSupportResistanceWindow()));
        plan.setResistance20d(indicator.resistance(ascKlines, properties.getSupportResistanceWindow()));
        plan.setStopLossPrice(stopLoss);
        plan.setTakeProfitPrice(takeProfit);
        plan.setStopBasis(String.format("成本 %s − %s×ATR %s",
                position.getAvgCostPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                properties.getStopAtrMultiplier().stripTrailingZeros().toPlainString(),
                atr.setScale(RATIO_SCALE, RoundingMode.HALF_UP).toPlainString()));
        plan.setTargetBasis(String.format("成本 %s + %s×风险 %s",
                position.getAvgCostPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                properties.getRiskReward().stripTrailingZeros().toPlainString(),
                risk.setScale(PRICE_SCALE, RoundingMode.HALF_UP).toPlainString()));

        applyBuyMode(plan, atr, lastClose, cash);
        applyAddPositionRule(plan, lastClose);
        applySellStrategy(plan);
        return plan;
    }

    private void applyBuyMode(TradePlan plan, BigDecimal atr, BigDecimal lastClose, CashContext cash) {
        BigDecimal atrRatio = plan.getAtrRatio();
        BigDecimal[] ratios;
        if (atrRatio.compareTo(properties.getHighVolatilityThreshold()) >= 0) {
            ratios = new BigDecimal[]{new BigDecimal("0.50"), new BigDecimal("0.30"), new BigDecimal("0.20")};
            plan.setBuyMode(TradePlan.BUY_MODE_BATCHED);
            plan.setBuyBasis(String.format("ATR 占现价 %s%%，高波动，建议分 %d 批买入",
                    percent(atrRatio), ratios.length));
        } else if (atrRatio.compareTo(properties.getMidVolatilityThreshold()) >= 0) {
            ratios = new BigDecimal[]{new BigDecimal("0.60"), new BigDecimal("0.40")};
            plan.setBuyMode(TradePlan.BUY_MODE_BATCHED);
            plan.setBuyBasis(String.format("ATR 占现价 %s%%，中波动，建议分 %d 批买入",
                    percent(atrRatio), ratios.length));
        } else {
            ratios = new BigDecimal[]{};
            plan.setBuyMode(TradePlan.BUY_MODE_LUMP_SUM);
            plan.setBuyBasis(String.format("ATR 占现价 %s%%，低波动，建议一次性买入", percent(atrRatio)));
        }

        BigDecimal support = plan.getSupport20d();
        List<TradePlan.TradePlanBatch> batches = new ArrayList<>();
        for (int i = 0; i < ratios.length; i++) {
            BigDecimal price = i == 0 ? lastClose
                    : lastClose.subtract(atr.multiply(new BigDecimal("0.5").multiply(BigDecimal.valueOf(i))));
            if (support != null && price.compareTo(support) < 0) {
                price = support;
            }
            price = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal amount = null;
            if (cash != null && TradePlan.CASH_BASIS_REMAINING.equals(cash.cashBasis())
                    && cash.availableCash() != null && cash.availableCash().compareTo(BigDecimal.ZERO) > 0) {
                amount = cash.availableCash().multiply(ratios[i]).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            }
            batches.add(new TradePlan.TradePlanBatch(ratios[i], price, amount));
        }
        plan.setBuyBatches(batches);
    }

    private void applyAddPositionRule(TradePlan plan, BigDecimal lastClose) {
        BigDecimal support = plan.getSupport20d();
        if (support == null) {
            plan.setAddPositionRule(new TradePlan.AddPositionRule(null, ADD_POSITION_RATIO,
                    "支撑位数据缺失，暂不给出补仓价位，补仓比例不超过当前持仓市值 30%"));
            return;
        }
        if (lastClose.compareTo(support) < 0) {
            plan.setAddPositionRule(new TradePlan.AddPositionRule(support, ADD_POSITION_RATIO,
                    String.format("已跌破支撑 %s，暂不补仓，等待重新站上支撑", support.toPlainString())));
            return;
        }
        plan.setAddPositionRule(new TradePlan.AddPositionRule(support, ADD_POSITION_RATIO,
                String.format("回落至支撑 %s 且收盘不破止损位 %s 时补仓，比例不超过当前持仓市值 30%%",
                        support.toPlainString(), plan.getStopLossPrice().toPlainString())));
    }

    private void applySellStrategy(TradePlan plan) {
        String takeProfitAction = String.format("到达止盈价 %s 减仓 50%%", plan.getTakeProfitPrice().toPlainString());
        String stopLossAction = String.format("跌破止损价 %s 全部清仓", plan.getStopLossPrice().toPlainString());
        String resistanceNote = null;
        BigDecimal resistance = plan.getResistance20d();
        BigDecimal lastClose = plan.getLastClose();
        if (resistance != null && lastClose != null && lastClose.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal distance = resistance.subtract(lastClose).divide(lastClose, RATIO_SCALE, RoundingMode.HALF_UP);
            if (distance.compareTo(NEAR_RESISTANCE_RATE) <= 0) {
                resistanceNote = String.format("接近近20日阻力 %s，注意分批减仓", resistance.toPlainString());
            }
        }
        plan.setSellStrategy(new TradePlan.SellStrategy(takeProfitAction, stopLossAction, resistanceNote));
    }

    private void applyDegraded(TradePlan plan, Position position, String reason) {
        BigDecimal avgCost = position.getAvgCostPrice() == null ? BigDecimal.ZERO : position.getAvgCostPrice();
        plan.setStopLossPrice(avgCost.multiply(BigDecimal.ONE.subtract(properties.getDegradedStopLossRate()))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        plan.setTakeProfitPrice(avgCost.multiply(BigDecimal.ONE.add(properties.getDegradedTakeProfitRate()))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        plan.setStopBasis("历史数据不足，降级为固定百分比止损");
        plan.setTargetBasis("历史数据不足，降级为固定百分比止盈");
        plan.setBuyMode(TradePlan.BUY_MODE_LUMP_SUM);
        plan.setBuyBatches(new ArrayList<>());
        plan.setBuyBasis("历史数据不足，建议一次性买入");
        plan.setDegraded(true);
        plan.setDegradedReason(reason);
    }

    private String lastKlineDate(List<StockDailyKline> ascKlines) {
        if (ascKlines == null || ascKlines.isEmpty()) {
            return null;
        }
        return ascKlines.get(ascKlines.size() - 1).getTradeDate();
    }

    private String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(PRICE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
