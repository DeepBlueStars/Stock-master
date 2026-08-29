package com.xian.stockai.vo;

import lombok.Data;

import java.util.List;

@Data
public class NotificationReadRequest {
    private String userId;
    private List<String> ids;
}