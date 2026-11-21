package com.xian.stockai.service;

import com.xian.stockai.dao.MonitorEventRepository;
import com.xian.stockai.dao.ResearchReportRepository;
import com.xian.stockai.dao.ReviewLevelRepository;
import com.xian.stockai.po.MonitorEvent;
import com.xian.stockai.po.ResearchReport;
import com.xian.stockai.po.ReviewLevel;
import com.xian.stockai.vo.ReportBrief;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author xian
 * @description 报告库 + 复核线 + 监测日历。三个东西共用一个入口，
 * 因为它们的写入方都是 OpenClaw 的同一次分析：一份报告带出价位、时间点、事件。
 * <p>
 * 这里只负责存与查，不做行情比对、不发提醒。价格是否到了由页面拿现价自己算距离。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchService {

    private static final int DEFAULT_REPORT_LIMIT = 30;
    private static final int DEFAULT_EVENT_LOOKBACK_DAYS = 90;
    private static final int DEFAULT_EVENT_LOOKAHEAD_DAYS = 365;
    private static final int SUMMARY_MAX_LENGTH = 300;
    private static final int SUMMARY_LINE_LENGTH = 120;
    private static final List<String> META_PREFIXES = List.of(
            "数据日", "报告日期", "统计区间", "价格口径", "更新时间", "生成时间", "来源", "免责");

    private static final Set<String> EVENT_STATUSES =
            Set.of(MonitorEvent.STATUS_PENDING, MonitorEvent.STATUS_DONE, MonitorEvent.STATUS_SKIPPED);

    private final ResearchReportRepository reportRepository;
    private final ReviewLevelRepository reviewLevelRepository;
    private final MonitorEventRepository monitorEventRepository;

    /* ---------------- 报告 ---------------- */

    public Result<ResearchReport> saveReport(String userId, ResearchReport request) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            return Result.fail("报告标题不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            return Result.fail("报告正文不能为空（Markdown）");
        }
        LocalDateTime now = LocalDateTime.now();
        ResearchReport target;
        if (StringUtils.hasText(request.getId())) {
            Optional<ResearchReport> existing = reportRepository.findById(request.getId());
            if (existing.isEmpty()) {
                return Result.fail("报告不存在: " + request.getId());
            }
            target = existing.get();
            if (!userId.equals(target.getUserId())) {
                return Result.fail("不能修改其他用户的报告");
            }
        } else {
            target = new ResearchReport();
            target.setUserId(userId);
            target.setCreatedAt(now);
        }
        target.setType(StringUtils.hasText(request.getType()) ? request.getType() : ResearchReport.TYPE_PORTFOLIO);
        target.setTitle(request.getTitle().trim());
        target.setSymbol(request.getSymbol());
        target.setReportDate(request.getReportDate() != null ? request.getReportDate() : LocalDate.now());
        target.setPeriodStart(request.getPeriodStart());
        target.setPeriodEnd(request.getPeriodEnd());
        target.setSummary(trimSummary(StringUtils.hasText(request.getSummary())
                ? request.getSummary()
                : deriveSummary(request.getContent())));
        target.setContent(request.getContent());
        target.setRating(request.getRating());
        target.setTags(request.getTags() == null ? new ArrayList<>() : request.getTags());
        target.setSource(StringUtils.hasText(request.getSource()) ? request.getSource() : ResearchReport.SOURCE_MANUAL);
        target.setUpdatedAt(now);
        ResearchReport saved = reportRepository.save(target);
        log.info("保存研究报告: userId={}, id={}, type={}, title={}", userId, saved.getId(), saved.getType(), saved.getTitle());
        return Result.success(saved);
    }

    public Result<List<ReportBrief>> listReports(String userId, String type, Integer limit) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        int max = limit == null || limit <= 0 ? DEFAULT_REPORT_LIMIT : Math.min(limit, 200);
        List<ResearchReport> reports = StringUtils.hasText(type)
                ? reportRepository.findByUserIdAndTypeOrderByReportDateDesc(userId, type)
                : reportRepository.findByUserIdOrderByReportDateDesc(userId);
        List<ReportBrief> briefs = new ArrayList<>();
        for (ResearchReport report : reports) {
            if (briefs.size() >= max) {
                break;
            }
            briefs.add(toBrief(report));
        }
        return Result.success(briefs);
    }

    public Result<ResearchReport> getReport(String userId, String id) {
        if (!StringUtils.hasText(id)) {
            return Result.fail("报告ID不能为空");
        }
        Optional<ResearchReport> report = reportRepository.findById(id);
        if (report.isEmpty()) {
            return Result.fail("报告不存在: " + id);
        }
        if (StringUtils.hasText(userId) && !userId.equals(report.get().getUserId())) {
            return Result.fail("不能查看其他用户的报告");
        }
        return Result.success(report.get());
    }

    public Result<Void> deleteReport(String userId, String id) {
        Optional<ResearchReport> report = reportRepository.findById(id);
        if (report.isEmpty()) {
            return Result.fail("报告不存在: " + id);
        }
        if (!userId.equals(report.get().getUserId())) {
            return Result.fail("不能删除其他用户的报告");
        }
        monitorEventRepository.deleteByReportId(id);
        reportRepository.deleteById(id);
        return Result.success();
    }

    /* ---------------- 复核线 ---------------- */

    public Result<ReviewLevel> saveReviewLevel(String userId, ReviewLevel request) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getSymbol())) {
            return Result.fail("代码不能为空");
        }
        String symbol = request.getSymbol().trim();
        LocalDateTime now = LocalDateTime.now();
        ReviewLevel target = reviewLevelRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseGet(() -> {
                    ReviewLevel created = new ReviewLevel();
                    created.setUserId(userId);
                    created.setSymbol(symbol);
                    created.setCreatedAt(now);
                    return created;
                });
        target.setStockName(StringUtils.hasText(request.getStockName())
                ? request.getStockName() : target.getStockName());
        target.setReportId(StringUtils.hasText(request.getReportId()) ? request.getReportId() : target.getReportId());
        target.setReportDate(request.getReportDate() != null ? request.getReportDate() : target.getReportDate());
        target.setPriceLevels(normalizePriceLevels(request.getPriceLevels()));
        target.setTimeReviews(normalizeTimeReviews(request.getTimeReviews()));
        target.setThesisStops(request.getThesisStops() == null ? new ArrayList<>() : request.getThesisStops());
        target.setNote(request.getNote());
        target.setUpdatedAt(now);
        ReviewLevel saved = reviewLevelRepository.save(target);
        log.info("保存复核线: userId={}, symbol={}, priceLevels={}, timeReviews={}",
                userId, symbol, saved.getPriceLevels().size(), saved.getTimeReviews().size());
        return Result.success(saved);
    }

    public Result<List<ReviewLevel>> listReviewLevels(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        List<ReviewLevel> levels = new ArrayList<>(reviewLevelRepository.findByUserIdOrderByUpdatedAtDesc(userId));
        // 最近一个复核时间点越近的排前面，没有时间点的排最后
        levels.sort(Comparator.comparing(this::nextReviewDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return Result.success(levels);
    }

    public Result<Void> deleteReviewLevel(String userId, String symbol) {
        reviewLevelRepository.deleteByUserIdAndSymbol(userId, symbol);
        return Result.success();
    }

    private LocalDate nextReviewDate(ReviewLevel level) {
        if (level.getTimeReviews() == null || level.getTimeReviews().isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        return level.getTimeReviews().stream()
                .map(ReviewLevel.TimeReview::getDate)
                .filter(date -> date != null && !date.isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private List<ReviewLevel.PriceLevel> normalizePriceLevels(List<ReviewLevel.PriceLevel> levels) {
        List<ReviewLevel.PriceLevel> result = new ArrayList<>();
        if (levels == null) {
            return result;
        }
        for (ReviewLevel.PriceLevel level : levels) {
            if (level == null || level.getPrice() == null) {
                continue;
            }
            if (!StringUtils.hasText(level.getLabel())) {
                level.setLabel(level.getCode());
            }
            result.add(level);
        }
        return result;
    }

    private List<ReviewLevel.TimeReview> normalizeTimeReviews(List<ReviewLevel.TimeReview> reviews) {
        List<ReviewLevel.TimeReview> result = new ArrayList<>();
        if (reviews == null) {
            return result;
        }
        for (ReviewLevel.TimeReview review : reviews) {
            if (review == null || review.getDate() == null) {
                continue;
            }
            result.add(review);
        }
        result.sort(Comparator.comparing(ReviewLevel.TimeReview::getDate));
        return result;
    }

    /* ---------------- 监测日历 ---------------- */

    /**
     * @param reportId 传了就把这份报告名下的事件整体覆盖掉；不传则追加
     */
    public Result<List<MonitorEvent>> saveEvents(String userId, String reportId, List<MonitorEvent> events) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        if (events == null || events.isEmpty()) {
            return Result.fail("事件列表不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        if (StringUtils.hasText(reportId)) {
            monitorEventRepository.deleteByReportId(reportId);
        }
        List<MonitorEvent> toSave = new ArrayList<>();
        for (MonitorEvent event : events) {
            if (event == null || event.getEventDate() == null || !StringUtils.hasText(event.getTitle())) {
                continue;
            }
            event.setId(null);
            event.setUserId(userId);
            event.setReportId(StringUtils.hasText(reportId) ? reportId : event.getReportId());
            event.setImportance(StringUtils.hasText(event.getImportance())
                    ? event.getImportance() : MonitorEvent.IMPORTANCE_MEDIUM);
            event.setStatus(StringUtils.hasText(event.getStatus())
                    ? event.getStatus() : MonitorEvent.STATUS_PENDING);
            if (!StringUtils.hasText(event.getCategory())) {
                event.setCategory(MonitorEvent.CATEGORY_OTHER);
            }
            event.setCreatedAt(now);
            event.setUpdatedAt(now);
            toSave.add(event);
        }
        if (toSave.isEmpty()) {
            return Result.fail("没有可保存的事件（eventDate 与 title 必填）");
        }
        List<MonitorEvent> saved = monitorEventRepository.saveAll(toSave);
        log.info("保存监测事件: userId={}, reportId={}, size={}", userId, reportId, saved.size());
        return Result.success(saved);
    }

    public Result<List<MonitorEvent>> listEvents(String userId, LocalDate from, LocalDate to, String status) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        LocalDate start = from != null ? from : LocalDate.now().minusDays(DEFAULT_EVENT_LOOKBACK_DAYS);
        LocalDate end = to != null ? to : LocalDate.now().plusDays(DEFAULT_EVENT_LOOKAHEAD_DAYS);
        List<MonitorEvent> events = StringUtils.hasText(status)
                ? monitorEventRepository.findByUserIdAndStatusAndEventDateBetweenOrderByEventDateAsc(
                        userId, status, start, end)
                : monitorEventRepository.findByUserIdAndEventDateBetweenOrderByEventDateAsc(userId, start, end);
        return Result.success(events);
    }

    public Result<MonitorEvent> updateEventStatus(String userId, String id, String status) {
        if (!EVENT_STATUSES.contains(status)) {
            return Result.fail("状态只能是 PENDING / DONE / SKIPPED");
        }
        Optional<MonitorEvent> event = monitorEventRepository.findById(id);
        if (event.isEmpty()) {
            return Result.fail("事件不存在: " + id);
        }
        if (!userId.equals(event.get().getUserId())) {
            return Result.fail("不能修改其他用户的事件");
        }
        event.get().setStatus(status);
        event.get().setUpdatedAt(LocalDateTime.now());
        return Result.success(monitorEventRepository.save(event.get()));
    }

    private ReportBrief toBrief(ResearchReport report) {
        ReportBrief brief = new ReportBrief();
        brief.setId(report.getId());
        brief.setUserId(report.getUserId());
        brief.setType(report.getType());
        brief.setTitle(report.getTitle());
        brief.setSymbol(report.getSymbol());
        brief.setReportDate(report.getReportDate());
        brief.setPeriodStart(report.getPeriodStart());
        brief.setPeriodEnd(report.getPeriodEnd());
        brief.setSummary(trimSummary(report.getSummary()));
        brief.setRating(report.getRating());
        brief.setTags(report.getTags());
        brief.setSource(report.getSource());
        brief.setCreatedAt(report.getCreatedAt());
        return brief;
    }

    private String trimSummary(String summary) {
        if (summary == null) {
            return null;
        }
        String text = summary.trim();
        return text.length() <= SUMMARY_MAX_LENGTH ? text : text.substring(0, SUMMARY_MAX_LENGTH) + "…";
    }

    /**
     * 报告开头本身就是总结，调用方（OpenClaw）只传整份 Markdown。
     * 这里跳过标题、引用块、分隔线、表格，取第一段实质正文压成一行，
     * 供列表页展示用；形如"数据日…/价格口径…"的开头说明段会被跳过。
     */
    private String deriveSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        List<String> paragraphs = new ArrayList<>();
        List<Boolean> sectionBreaks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean pendingBreak = false;
        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("#")) {
                // 开头的大标题直接跳过；有正文之后遇到标题说明进入下一节，摘要到此为止
                if (!paragraphs.isEmpty() || current.length() > 0) {
                    flushParagraph(paragraphs, sectionBreaks, current, pendingBreak);
                    pendingBreak = true;
                }
                continue;
            }
            if (line.isEmpty() || line.startsWith("|")) {
                if (current.length() > 0) {
                    flushParagraph(paragraphs, sectionBreaks, current, pendingBreak);
                    pendingBreak = false;
                }
                continue;
            }
            if (line.startsWith(">") || line.matches("^[-*_]{3,}$")) {
                continue;
            }
            String text = stripInlineMarkdown(line);
            if (text.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                appendJoined(current, text);
            } else {
                current.append(text);
            }
        }
        if (current.length() > 0) {
            flushParagraph(paragraphs, sectionBreaks, current, pendingBreak);
        }
        if (paragraphs.isEmpty()) {
            return null;
        }
        int start = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            if (!isMetaParagraph(paragraphs.get(i))) {
                start = i;
                break;
            }
        }
        StringBuilder summary = new StringBuilder();
        for (int i = start; i < paragraphs.size(); i++) {
            if (sectionBreaks.get(i)) {
                break;
            }
            String paragraph = paragraphs.get(i);
            if (isMetaParagraph(paragraph)) {
                continue;
            }
            if (summary.length() > 0) {
                appendJoined(summary, paragraph);
            } else {
                summary.append(paragraph);
            }
            if (summary.length() >= SUMMARY_LINE_LENGTH) {
                break;
            }
        }
        String derived = summary.length() == 0 ? paragraphs.get(0) : summary.toString();
        if (derived.length() <= SUMMARY_LINE_LENGTH) {
            return derived;
        }
        int cut = derived.lastIndexOf('。', SUMMARY_LINE_LENGTH);
        return cut >= 60
                ? derived.substring(0, cut + 1)
                : derived.substring(0, SUMMARY_LINE_LENGTH) + "…";
    }

    private void flushParagraph(List<String> paragraphs, List<Boolean> sectionBreaks,
                                StringBuilder current, boolean sectionBreak) {
        paragraphs.add(current.toString());
        sectionBreaks.add(sectionBreak);
        current.setLength(0);
    }

    /**
     * 中文句号之间不补空格，只有两侧都是 ASCII 词字符时才补，避免"63.89% 3 只"粘在一起。
     */
    private void appendJoined(StringBuilder sb, String text) {
        if (sb.length() > 0 && isAsciiWordChar(sb.charAt(sb.length() - 1)) && isAsciiWordChar(text.charAt(0))) {
            sb.append(' ');
        }
        sb.append(text);
    }

    private boolean isAsciiWordChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '%' || c == ')' || c == ']' || c == '>');
    }

    private boolean isMetaParagraph(String paragraph) {
        for (String prefix : META_PREFIXES) {
            if (paragraph.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String stripInlineMarkdown(String line) {
        String text = line.replaceAll("^[-*+]\\s+", "")
                .replaceAll("^\\d+[.)]\\s+", "")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[*_`]", "");
        return text.trim();
    }
}
