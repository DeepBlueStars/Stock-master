package com.xian.stockai.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.MarketQuotes;
import com.xian.stockai.po.Position;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class PositionRes {
    private String symbol;
    private String stockName;
    private BigDecimal price;
    private String reportId;
    private String recommendation;
    private BigDecimal pctChg;
    private Integer totalQuantity;
    private BigDecimal avgCostPrice;
    private BigDecimal totalCost;
    private BigDecimal totalValue;
    private BigDecimal totalReturn;
    private BigDecimal totalReturnPct;
    @JSONField(format="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    /**
     * 股票类型
     */
    private String type;

    public static PositionRes fromPosition(Position position, MarketQuotes marketQuotes) {
        PositionRes res = new PositionRes()
                .setSymbol(position.getSymbol())
                .setStockName(position.getStockName())
                .setPrice(marketQuotes.getClose())
                .setPctChg(marketQuotes.getPctChg() != null ? marketQuotes.getPctChg().setScale(2, RoundingMode.HALF_UP) : null)
                .setTotalQuantity(position.getTotalQuantity())
                .setAvgCostPrice(position.getAvgCostPrice())
                .setTotalCost(position.getTotalCost())
                .setUpdatedAt(marketQuotes.getUpdatedAt())
                .setType(position.getType());
        
        if (position.getTotalQuantity() > 0 && marketQuotes.getClose() != null) {
            res.setTotalValue(new BigDecimal(position.getTotalQuantity()).multiply(marketQuotes.getClose()));
        }
        if (position.getTotalCost() != null && res.getTotalValue() != null) {
            res.setTotalReturn(res.getTotalValue().subtract(position.getTotalCost()));
            res.setTotalReturnPct(res.getTotalReturn().divide(position.getTotalCost(), 4, RoundingMode.HALF_UP));
        }
        
        return res;
    }

    public static PositionRes fromPosition(Position position, EtfDailyQuotes etfDailyQuotes) {
        PositionRes res = new PositionRes()
                .setSymbol(position.getSymbol())
                .setStockName(position.getStockName())
                .setPrice(etfDailyQuotes.getClose())
                .setPctChg(etfDailyQuotes.getPctChange() != null ? etfDailyQuotes.getPctChange().setScale(2, RoundingMode.HALF_UP) : null)
                .setTotalQuantity(position.getTotalQuantity())
                .setAvgCostPrice(position.getAvgCostPrice())
                .setTotalCost(position.getTotalCost())
                .setUpdatedAt(etfDailyQuotes.getUpdatedAt())
                .setType(position.getType());
        
        if (position.getTotalQuantity() > 0 && etfDailyQuotes.getClose() != null) {
            res.setTotalValue(new BigDecimal(position.getTotalQuantity()).multiply(etfDailyQuotes.getClose()));
        }
        if (position.getTotalCost() != null && res.getTotalValue() != null) {
            res.setTotalReturn(res.getTotalValue().subtract(position.getTotalCost()));
            res.setTotalReturnPct(res.getTotalReturn().divide(position.getTotalCost(), 4, RoundingMode.HALF_UP));
        }
        
        return res;
    }
}
