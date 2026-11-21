package com.xian.stockai.mcp;

import com.alibaba.fastjson2.JSON;
import com.xian.stockai.po.MonitorEvent;
import com.xian.stockai.po.ResearchReport;
import com.xian.stockai.po.ReviewLevel;
import com.xian.stockai.service.ResearchService;
import com.xian.stockai.vo.ReportBrief;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 报告库 / 复核线 / 监测日历的 MCP 工具，供 OpenClaw 把分析结果写进系统。
 * <p>
 * 约定：一次分析 = 一份报告 + 每只持仓的复核线 + 一张监测日历。
 * 报告正文用 Markdown；价位与事件用 JSON 数组字符串传，字段名见各 ToolParam 说明。
 * 这里只落库，不做行情比对、不发提醒。
 */
@Component
@Slf4j
public class ResearchMcpTools {

    private final ResearchService researchService;
    private final String defaultUserId;

    public ResearchMcpTools(ResearchService researchService,
                            @Value("${stockai.mcp.default-user-id:}") String defaultUserId) {
        this.researchService = researchService;
        this.defaultUserId = defaultUserId;
    }

    @Tool(name = "save_research_report",
            description = "保存一份研究报告（Markdown 正文，一次性传整份）。组合体检报告 type 填 PORTFOLIO，个股填 STOCK，行业填 INDUSTRY。"
                    + "个股报告建议传 summary（1-2 句投资结论），否则系统会截取正文第一段，可能取到「信息丰富度」而不是结论。"
                    + "reportDate 是报告的业务日期（yyyy-MM-dd），周报去重按它判断，组合报告请务必传当天日期。")
    public Result<ResearchReport> saveResearchReport(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "报告类型：PORTFOLIO / INDUSTRY / STOCK") String type,
            @ToolParam(description = "报告标题") String title,
            @ToolParam(description = "报告正文，Markdown 全文（开头即总结）") String content,
            @ToolParam(required = false, description = "报告业务日期 yyyy-MM-dd，不填取今天") String reportDate,
            @ToolParam(required = false, description = "统计区间开始日 yyyy-MM-dd") String periodStart,
            @ToolParam(required = false, description = "统计区间结束日 yyyy-MM-dd") String periodEnd,
            @ToolParam(required = false, description = "评级，如 B+") String rating,
            @ToolParam(required = false, description = "标签，逗号分隔") String tagsCsv,
            @ToolParam(required = false, description = "个股代码，组合/行业报告留空") String symbol,
            @ToolParam(required = false, description = "列表摘要，1-2 句结论；个股报告建议传，留空则从正文推导") String summary) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        ResearchReport request = new ResearchReport();
        request.setType(type);
        request.setTitle(title);
        request.setContent(content);
        request.setSummary(summary);
        request.setRating(rating);
        request.setSymbol(symbol);
        request.setSource(ResearchReport.SOURCE_OPENCLAW);
        request.setReportDate(parseDate(reportDate));
        request.setPeriodStart(parseDate(periodStart));
        request.setPeriodEnd(parseDate(periodEnd));
        if (StringUtils.hasText(tagsCsv)) {
            List<String> tags = new ArrayList<>();
            for (String tag : tagsCsv.split("[,，]")) {
                if (StringUtils.hasText(tag)) {
                    tags.add(tag.trim());
                }
            }
            request.setTags(tags);
        }
        return researchService.saveReport(uid, request);
    }

    @Tool(name = "list_research_reports",
            description = "查询已保存的报告列表（不含正文）。type 可填 PORTFOLIO / INDUSTRY / STOCK，留空返回全部。")
    public Result<List<ReportBrief>> listResearchReports(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(required = false, description = "报告类型，留空返回全部") String type,
            @ToolParam(required = false, description = "最多返回几条，默认 30") Integer limit) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return researchService.listReports(uid, type, limit);
    }

    @Tool(name = "get_research_report",
            description = "按 ID 读取一份报告的 Markdown 全文，写复核线前先用它确认结论。")
    public Result<ResearchReport> getResearchReport(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "报告ID") String reportId) {
        return researchService.getReport(resolveUserId(userId), reportId);
    }

    @Tool(name = "save_review_levels",
            description = "保存/覆盖一只持仓的复核线：止盈止损价位、需要复核的时间点、论文止损条件。"
                    + "同一代码重复调用会整体覆盖，所以每次请传完整的清单。"
                    + "priceLevelsJson 示例：[{\"code\":\"TAKE_PROFIT_1\",\"label\":\"止盈一档\",\"price\":160,"
                    + "\"action\":\"清仓\",\"note\":\"23.5 倍 2026E\"}]；code 取值 TAKE_PROFIT_1 / TAKE_PROFIT_2 / "
                    + "TRAILING / REVIEW / HARD_STOP / REBALANCE。"
                    + "timeReviewsJson 示例：[{\"date\":\"2027-01-15\",\"label\":\"业绩预告\","
                    + "\"question\":\"Q4 营收是否转正？\"}]。")
    public Result<ReviewLevel> saveReviewLevels(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票或 ETF 代码") String symbol,
            @ToolParam(required = false, description = "名称，如 东鹏饮料") String stockName,
            @ToolParam(required = false, description = "来源报告ID") String reportId,
            @ToolParam(description = "价位清单 JSON 数组") String priceLevelsJson,
            @ToolParam(required = false, description = "时间点清单 JSON 数组") String timeReviewsJson,
            @ToolParam(required = false, description = "论文止损条件 JSON 字符串数组，如 [\"分红率<80%\"]") String thesisStopsJson,
            @ToolParam(required = false, description = "备注") String note) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        ReviewLevel request = new ReviewLevel();
        request.setSymbol(symbol);
        request.setStockName(stockName);
        request.setReportId(reportId);
        request.setNote(note);
        if (StringUtils.hasText(reportId)) {
            Result<ResearchReport> report = researchService.getReport(uid, reportId);
            if (report.checkSuccess() && report.getData() != null) {
                request.setReportDate(report.getData().getReportDate());
            }
        }
        try {
            request.setPriceLevels(parsePriceLevels(priceLevelsJson));
            request.setTimeReviews(parseTimeReviews(timeReviewsJson));
            request.setThesisStops(parseStrings(thesisStopsJson));
        } catch (Exception e) {
            return Result.fail("JSON 解析失败: " + e.getMessage());
        }
        return researchService.saveReviewLevel(uid, request);
    }

    @Tool(name = "list_review_levels",
            description = "查询每只持仓当前的复核线（价位、时间点、论文止损条件），写入前先看一眼避免覆盖错。")
    public Result<List<ReviewLevel>> listReviewLevels(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return researchService.listReviewLevels(uid);
    }

    @Tool(name = "delete_review_level",
            description = "删除一只持仓的复核线（清仓后不再跟随时用）。")
    public Result<Void> deleteReviewLevel(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票或 ETF 代码") String symbol) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return researchService.deleteReviewLevel(uid, symbol);
    }

    @Tool(name = "save_monitor_events",
            description = "保存监测日历事件（报告「监测清单」里的时间点）。传了 reportId 会先删除该报告名下旧事件再写入，"
                    + "所以每次传完整清单。eventsJson 示例：[{\"date\":\"2026-12-10\",\"title\":\"11 月 CPI/PPI\","
                    + "\"category\":\"宏观数据\",\"detail\":\"看 PPI 同比\",\"action\":\"PPI>6% 持续则复核东鹏毛利率\","
                    + "\"importance\":\"HIGH\"}]；importance 取 HIGH / MEDIUM / LOW。")
    public Result<List<MonitorEvent>> saveMonitorEvents(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(required = false, description = "来源报告ID，传了就整体覆盖该报告的事件") String reportId,
            @ToolParam(description = "事件 JSON 数组") String eventsJson) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        List<MonitorEvent> events;
        try {
            events = parseEvents(eventsJson);
        } catch (Exception e) {
            return Result.fail("eventsJson 解析失败: " + e.getMessage());
        }
        return researchService.saveEvents(uid, reportId, events);
    }

    @Tool(name = "list_monitor_events",
            description = "查询监测日历事件，按日期升序。不传日期默认前后 90 天 / 365 天。")
    public Result<List<MonitorEvent>> listMonitorEvents(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd") String fromDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd") String toDate) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return researchService.listEvents(uid, parseDate(fromDate), parseDate(toDate), null);
    }

    @Tool(name = "update_monitor_event_status",
            description = "勾选监测事件的处理状态：PENDING / DONE / SKIPPED。")
    public Result<MonitorEvent> updateMonitorEventStatus(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "事件ID") String eventId,
            @ToolParam(description = "状态：PENDING / DONE / SKIPPED") String status) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return researchService.updateEventStatus(uid, eventId, status);
    }

    /* ---------------- 解析 ---------------- */

    private List<ReviewLevel.PriceLevel> parsePriceLevels(String json) {
        List<ReviewLevel.PriceLevel> levels = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return levels;
        }
        List<PriceLevelDto> dtos = JSON.parseArray(json, PriceLevelDto.class);
        for (PriceLevelDto dto : dtos) {
            if (dto == null || dto.price == null) {
                continue;
            }
            ReviewLevel.PriceLevel level = new ReviewLevel.PriceLevel();
            level.setCode(dto.code);
            level.setLabel(dto.label);
            level.setPrice(dto.price);
            level.setAction(dto.action);
            level.setNote(dto.note);
            levels.add(level);
        }
        return levels;
    }

    private List<ReviewLevel.TimeReview> parseTimeReviews(String json) {
        List<ReviewLevel.TimeReview> reviews = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return reviews;
        }
        List<TimeReviewDto> dtos = JSON.parseArray(json, TimeReviewDto.class);
        for (TimeReviewDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.date)) {
                continue;
            }
            LocalDate date = LocalDate.parse(dto.date.trim());
            ReviewLevel.TimeReview review = new ReviewLevel.TimeReview();
            review.setDate(date);
            review.setLabel(dto.label);
            review.setQuestion(dto.question);
            review.setNote(dto.note);
            reviews.add(review);
        }
        return reviews;
    }

    private List<MonitorEvent> parseEvents(String json) {
        List<MonitorEvent> events = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return events;
        }
        List<EventDto> dtos = JSON.parseArray(json, EventDto.class);
        for (EventDto dto : dtos) {
            if (dto == null || !StringUtils.hasText(dto.date) || !StringUtils.hasText(dto.title)) {
                continue;
            }
            MonitorEvent event = new MonitorEvent();
            event.setEventDate(LocalDate.parse(dto.date.trim()));
            event.setEndDate(StringUtils.hasText(dto.endDate) ? LocalDate.parse(dto.endDate.trim()) : null);
            event.setTitle(dto.title);
            event.setCategory(dto.category);
            event.setDetail(dto.detail);
            event.setAction(dto.action);
            event.setImportance(dto.importance);
            event.setSymbol(dto.symbol);
            events.add(event);
        }
        return events;
    }

    private List<String> parseStrings(String json) {
        List<String> values = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return values;
        }
        for (String value : JSON.parseArray(json, String.class)) {
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private LocalDate parseDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : null;
    }

    private String resolveUserId(String userId) {
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        return StringUtils.hasText(defaultUserId) ? defaultUserId : null;
    }

    /* fastjson2 用 public 字段，保持最小反射面 */

    public static class PriceLevelDto {
        public String code;
        public String label;
        public BigDecimal price;
        public String action;
        public String note;
    }

    public static class TimeReviewDto {
        public String date;
        public String label;
        public String question;
        public String note;
    }

    public static class EventDto {
        public String date;
        public String endDate;
        public String title;
        public String category;
        public String detail;
        public String action;
        public String importance;
        public String symbol;
    }
}
