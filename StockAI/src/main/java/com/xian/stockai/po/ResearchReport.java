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
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 研究/体检报告库。报告正文由外部 AI（OpenClaw）分析后通过 MCP 写入，
 * 这里只做存储、检索和展示，不在系统内重新生成组合结论。
 * <p>
 * 目前只关心组合报告（TYPE_PORTFOLIO），行业/个股报告先留字段不展开。
 */
@Document(collection = "research_report")
@CompoundIndex(name = "idx_report_user_type_date", def = "{'user_id': 1, 'type': 1, 'report_date': -1}")
@Getter
@Setter
public class ResearchReport {

    /**
     * 组合体检报告
     */
    public static final String TYPE_PORTFOLIO = "PORTFOLIO";
    /**
     * 行业报告
     */
    public static final String TYPE_INDUSTRY = "INDUSTRY";
    /**
     * 个股报告
     */
    public static final String TYPE_STOCK = "STOCK";

    /**
     * 由外部 AI 分析后写入
     */
    public static final String SOURCE_OPENCLAW = "OPENCLAW";
    /**
     * 系统内生成
     */
    public static final String SOURCE_SYSTEM = "SYSTEM";
    /**
     * 手工录入
     */
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    private String id;

    @Field(name = "user_id")
    @Indexed
    private String userId;

    /**
     * REPORT 类型：PORTFOLIO / INDUSTRY / STOCK
     */
    @Field(name = "type")
    private String type;

    @Field(name = "title")
    private String title;

    /**
     * 个股报告对应的代码，组合报告为空
     */
    @Field(name = "symbol")
    private String symbol;

    /**
     * 报告的业务日期（体检日），周报去重按这个字段判断
     */
    @Field(name = "report_date")
    @Indexed
    private LocalDate reportDate;

    @Field(name = "period_start")
    private LocalDate periodStart;

    @Field(name = "period_end")
    private LocalDate periodEnd;

    /**
     * 一段话结论，列表页与摘要卡片用
     */
    @Field(name = "summary")
    private String summary;

    /**
     * 报告正文，Markdown
     */
    @Field(name = "content")
    private String content;

    /**
     * 报告给出的评级，如 B+
     */
    @Field(name = "rating")
    private String rating;

    @Field(name = "tags")
    private List<String> tags = new ArrayList<>();

    @Field(name = "source")
    private String source;

    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
