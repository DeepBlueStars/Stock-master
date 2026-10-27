package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author xian
 * @description 用户 + 股票代码请求
 */
@Getter
@Setter
public class TradePlanSymbolReq implements Serializable {
    private String userId;
    private String symbol;
}
