package com.xian.stockai.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author xian
 * @description 报告列表项：不带正文，避免一次拉几十份 Markdown。
 */
@Getter
@Setter
public class ReportBrief {

    private String id;

    private String userId;

    private String type;

    private String title;

    private String symbol;

    private LocalDate reportDate;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String summary;

    private String rating;

    private List<String> tags;

    private String source;

    private LocalDateTime createdAt;
}
