package com.xian.stockai.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 基础信息、持仓信息
 */

@Data
public class BasicPositionRes {
    // 总收益
    private BigDecimal totalProfit;
    // 总收益率
    private BigDecimal totalProfitRate;
    // 总资产
    private BigDecimal totalValue;
    // 总成本
    private BigDecimal totalCost;
    // 持仓股票数量
    private Integer positionCount;
    // 交易次数
    private Integer tradeCount;
}
