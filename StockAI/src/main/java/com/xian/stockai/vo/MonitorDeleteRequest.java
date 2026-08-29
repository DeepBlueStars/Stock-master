package com.xian.stockai.vo;

import lombok.Data;

@Data
public class MonitorDeleteRequest {
    private String userId;
    private String symbol;
}