package com.xian.stockai.task;

import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.service.ResearchService;
import com.xian.stockai.service.tradeplan.TradePlanSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author xian
 * @description 交易计划周报定时任务：每周六 18:00 为每个持仓用户生成周报。
 * <p>
 * 外部 AI（OpenClaw）通常周六先做组合体检并通过 MCP 写入报告；本周已有组合报告时
 * 说明外部结论已经落地，系统周报就不再生成，避免同一天两份周报各说各话。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradePlanSummaryTask {

    private final PositionRepository positionRepository;
    private final TradePlanSummaryService tradePlanSummaryService;
    private final ResearchService researchService;

    @Scheduled(cron = "${trade-plan.summary-cron:0 0 18 ? * SAT}")
    public int generateAll() {
        List<Position> positions = positionRepository.findByTotalQuantityGreaterThan(0);
        Set<String> userIds = new LinkedHashSet<>();
        for (Position position : positions) {
            if (position.getUserId() != null && !position.getUserId().isEmpty()) {
                userIds.add(position.getUserId());
            }
        }
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int failed = 0;
        int skipped = 0;
        log.info("开始生成交易计划周报，用户数 {}", userIds.size());
        for (String userId : userIds) {
            if (researchService.hasPortfolioReportSince(userId, weekStart)) {
                skipped++;
                log.info("用户 {} 本周已有外部组合报告，跳过系统周报", userId);
                continue;
            }
            try {
                tradePlanSummaryService.generateWeekly(userId);
            } catch (Exception e) {
                failed++;
                log.error("生成交易计划周报失败: userId={}", userId, e);
            }
        }
        log.info("交易计划周报生成完成：用户 {} 个，跳过 {} 个，失败 {} 个", userIds.size(), skipped, failed);
        return failed;
    }
}
