package com.xian.stockai.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 组合策略对账结果：目标比例 vs 实际比例，按带宽判定超配/低配。
 */
public record PortfolioSummaryRes(
        String latestRegime,
        String effectiveRegime,
        boolean confirmed,
        int confirmDays,
        BigDecimal band,
        String latestDate,
        BigDecimal totalAmount,
        List<Row> rows,
        List<Breach> breaches) {

    public record Row(
            String category,
            BigDecimal target,
            BigDecimal amount,
            BigDecimal actualPct,
            BigDecimal deviation,
            String status) {
    }

    /** 越界项：status 是 OVER/UNDER，deviation 带正负，text 直接给前端显示 */
    public record Breach(
            String category,
            String status,
            BigDecimal deviation,
            BigDecimal target,
            String text) {
    }
}
