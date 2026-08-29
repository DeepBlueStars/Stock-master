package com.xian.stockai.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonitorSaveRequest {
    private String userId;
    private String symbol;
    private String takeProfitType;
    private BigDecimal takeProfitValue;
    private String stopLossType;
    private BigDecimal stopLossValue;
}