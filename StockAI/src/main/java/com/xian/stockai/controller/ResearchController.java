package com.xian.stockai.controller;

import com.xian.stockai.po.MonitorEvent;
import com.xian.stockai.po.ResearchReport;
import com.xian.stockai.po.ReviewLevel;
import com.xian.stockai.service.ResearchService;
import com.xian.stockai.vo.ReportBrief;
import com.xian.stockai.vo.Result;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * @author xian
 * @description 报告库、复核线、监测日历的读写接口。
 * <p>
 * 主要写入方是 OpenClaw（走 MCP）；这里给控制台页面用，也是 MCP 工具的内部实现。
 * 只读行情/日历，不触发买卖，不发通知。
 */
@RestController
@RequestMapping("/research")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchService researchService;

    /* ---------------- 报告 ---------------- */

    @PostMapping("/report/save")
    public Result<ResearchReport> saveReport(@RequestBody ResearchReport request) {
        return researchService.saveReport(request.getUserId(), request);
    }

    @PostMapping("/report/list")
    public Result<List<ReportBrief>> listReports(@RequestBody ReportListReq request) {
        return researchService.listReports(request.getUserId(), request.getType(), request.getLimit());
    }

    @GetMapping("/report/{id}")
    public Result<ResearchReport> getReport(@PathVariable String id,
                                            @RequestParam(required = false) String userId) {
        return researchService.getReport(userId, id);
    }

    @PostMapping("/report/delete")
    public Result<Void> deleteReport(@RequestBody IdReq request) {
        return researchService.deleteReport(request.getUserId(), request.getId());
    }

    /* ---------------- 复核线 ---------------- */

    @PostMapping("/review/save")
    public Result<ReviewLevel> saveReviewLevel(@RequestBody ReviewLevel request) {
        return researchService.saveReviewLevel(request.getUserId(), request);
    }

    @PostMapping("/review/list")
    public Result<List<ReviewLevel>> listReviewLevels(@RequestBody UserReq request) {
        return researchService.listReviewLevels(request.getUserId());
    }

    @PostMapping("/review/delete")
    public Result<Void> deleteReviewLevel(@RequestBody SymbolReq request) {
        return researchService.deleteReviewLevel(request.getUserId(), request.getSymbol());
    }

    /* ---------------- 监测日历 ---------------- */

    @PostMapping("/event/save")
    public Result<List<MonitorEvent>> saveEvents(@RequestBody EventBatchReq request) {
        return researchService.saveEvents(request.getUserId(), request.getReportId(), request.getEvents());
    }

    @PostMapping("/event/list")
    public Result<List<MonitorEvent>> listEvents(@RequestBody EventQueryReq request) {
        return researchService.listEvents(request.getUserId(), request.getFrom(), request.getTo(), request.getStatus());
    }

    @PostMapping("/event/status")
    public Result<MonitorEvent> updateEventStatus(@RequestBody EventStatusReq request) {
        return researchService.updateEventStatus(request.getUserId(), request.getId(), request.getStatus());
    }

    @Getter
    @Setter
    public static class ReportListReq {
        private String userId;
        private String type;
        private Integer limit;
    }

    @Getter
    @Setter
    public static class UserReq {
        private String userId;
    }

    @Getter
    @Setter
    public static class IdReq {
        private String userId;
        private String id;
    }

    @Getter
    @Setter
    public static class SymbolReq {
        private String userId;
        private String symbol;
    }

    @Getter
    @Setter
    public static class EventBatchReq {
        private String userId;
        private String reportId;
        private List<MonitorEvent> events;
    }

    @Getter
    @Setter
    public static class EventQueryReq {
        private String userId;
        private LocalDate from;
        private LocalDate to;
        private String status;
    }

    @Getter
    @Setter
    public static class EventStatusReq {
        private String userId;
        private String id;
        private String status;
    }
}
