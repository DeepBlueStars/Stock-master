package com.xian.stockai.po;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组合策略：把「市场状态」映射成「各类资产的目标比例」。
 * 一个用户一份，矩阵可改；没有记录时用内置默认矩阵。
 */
@Data
@Document(collection = "portfolio_policy")
public class PortfolioPolicy {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** 每类资产允许的偏离带宽（百分点），默认 5 */
    private BigDecimal band;

    /** 市场状态切换需要连续确认的天数，默认 3 */
    private Integer confirmDays;

    private List<RegimeWeights> matrix;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class RegimeWeights {
        /** BULL / SIDEWAYS / BEAR / RISK */
        private String regime;
        /** 各类资产目标比例，合计应等于 100 */
        private List<CategoryTarget> targets;
    }

    @Data
    public static class CategoryTarget {
        private String category;
        private BigDecimal target;
    }
}
