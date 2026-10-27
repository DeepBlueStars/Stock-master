package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author xian
 * @description 周报详情请求（用户 + 周期起始日）
 */
@Getter
@Setter
public class TradePlanSummaryDetailReq implements Serializable {
    private String userId;
    private String weekStart;
}
