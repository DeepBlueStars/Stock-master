package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ScreeningHistoryRequest {
    private Integer page;
    private Integer size;
}
