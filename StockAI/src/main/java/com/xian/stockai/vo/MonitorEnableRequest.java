package com.xian.stockai.vo;

import lombok.Data;

@Data
public class MonitorEnableRequest {
    private String userId;
    private String symbol;
    private Boolean enabled;
}