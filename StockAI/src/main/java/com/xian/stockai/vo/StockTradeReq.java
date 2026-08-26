package com.xian.stockai.vo;

import lombok.Data;

/**
 * @author xian
 * @description 交易记录列表请求参数
 */
@Data
public class StockTradeReq {
    private String code;
    private String userId;
}
