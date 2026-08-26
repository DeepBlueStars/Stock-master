package com.xian.stockai.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @author xian
 * @description 资产配置分类持仓金额响应
 */
@Data
@Accessors(chain = true)
public class AssetCategoryPositionRes {
    /**
     * 分类名称
     */
    private String categoryName;
    
    /**
     * 持仓金额
     */
    private BigDecimal positionAmount;
    
    /**
     * 分类类型
     */
    private String type;
    
    /**
     * 颜色
     */
    private String color;
}