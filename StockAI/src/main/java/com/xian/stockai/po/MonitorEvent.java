package com.xian.stockai.po;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author xian
 * @description 监测日历上的一个事件：某个日子要去看某个数、某条公告、某个会议。
 * <p>
 * 报告「八、监测清单」里的时间点由 OpenClaw 通过 MCP 写入，页面按日历展示；
 * 只做展示与勾选，不主动推送。重要性用 importance 排序，别让所有事件长得一样重。
 */
@Document(collection = "monitor_event")
@CompoundIndex(name = "idx_event_user_date", def = "{'user_id': 1, 'event_date': 1}")
@Getter
@Setter
public class MonitorEvent {

    public static final String IMPORTANCE_HIGH = "HIGH";
    public static final String IMPORTANCE_MEDIUM = "MEDIUM";
    public static final String IMPORTANCE_LOW = "LOW";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public static final String CATEGORY_MACRO = "宏观数据";
    public static final String CATEGORY_POLICY = "政策会议";
    public static final String CATEGORY_EARNINGS = "财报预告";
    public static final String CATEGORY_MNA = "并购重组";
    public static final String CATEGORY_DIVIDEND = "分红回购";
    public static final String CATEGORY_INDEX = "指数调整";
    public static final String CATEGORY_CONSUMPTION = "消费数据";
    /**
     * 个股关注档案里的监测项，由 save_focus_stock 自动同步
     */
    public static final String CATEGORY_STOCK = "个股监测";
    public static final String CATEGORY_OTHER = "其他";

    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    /**
     * 来源报告，按报告整体覆盖时用
     */
    @Field(name = "report_id")
    @Indexed
    private String reportId;

    @Field(name = "symbol")
    private String symbol;

    @Field(name = "event_date")
    private LocalDate eventDate;

    /**
     * 事件窗口的结束日，单日事件为空
     */
    @Field(name = "end_date")
    private LocalDate endDate;

    @Field(name = "title")
    private String title;

    /**
     * 宏观数据 / 政策会议 / 财报预告 / 并购重组 / 分红回购 / 指数调整 / 消费数据 / 其他
     */
    @Field(name = "category")
    private String category;

    /**
     * 到点要看什么、看哪个数
     */
    @Field(name = "detail")
    private String detail;

    /**
     * 看到什么算什么、触发什么动作
     */
    @Field(name = "action")
    private String action;

    /**
     * HIGH / MEDIUM / LOW
     */
    @Field(name = "importance")
    private String importance;

    /**
     * PENDING / DONE / SKIPPED
     */
    @Field(name = "status")
    private String status;

    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
