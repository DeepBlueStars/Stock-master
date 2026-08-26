package com.xian.stockai.vo;

import lombok.Data;

@Data
public class ConfigUpdateRequest {
    private String userId;
    private String configId;
    private String config;
}