package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author xian
 * @description 勾选某一批「已买入」的请求
 */
@Getter
@Setter
public class TradePlanBatchStateReq implements Serializable {
    private String userId;
    private String symbol;
    private Integer index;
    private Boolean bought;
}
