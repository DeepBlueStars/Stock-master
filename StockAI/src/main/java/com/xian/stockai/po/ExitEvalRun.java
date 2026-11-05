package com.xian.stockai.po;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 一次每日判定的执行记录，用于审计「今天到底算没算、为什么没算」。
 */
@Document(collection = "exit_eval_runs")
@Getter
@Setter
public class ExitEvalRun {

    @Id
    private String id;

    @Field(name = "user_id")
    private String userId;

    /**
     * 触发方式：TASK（定时）/ MANUAL（接口）/ MCP
     */
    @Field(name = "trigger_source")
    private String triggerSource;

    @Field(name = "trade_date")
    private String tradeDate;

    @Field(name = "started_at")
    private LocalDateTime startedAt;

    @Field(name = "finished_at")
    private LocalDateTime finishedAt;

    @Field(name = "evaluated")
    private Integer evaluated;

    @Field(name = "triggered")
    private Integer triggered;

    @Field(name = "skipped")
    private Integer skipped;

    /**
     * 跳过的原因，如「数据停在 2026-10-30，非最新交易日」
     */
    @Field(name = "skipped_reasons")
    private List<String> skippedReasons = new ArrayList<>();

    @Field(name = "errors")
    private List<String> errors = new ArrayList<>();
}
