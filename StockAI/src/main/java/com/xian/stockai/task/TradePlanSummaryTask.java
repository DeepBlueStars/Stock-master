package com.xian.stockai.task;

import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.service.tradeplan.TradePlanSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author xian
 * @description 交易计划周报定时任务：每周六 08:00 为每个持仓用户生成周报
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradePlanSummaryTask {

    private final PositionRepository positionRepository;
    private final TradePlanSummaryService tradePlanSummaryService;

    @Scheduled(cron = "${trade-plan.summary-cron:0 0 8 ? * SAT}")
    public int generateAll() {
        List<Position> positions = positionRepository.findByTotalQuantityGreaterThan(0);
        Set<String> userIds = new LinkedHashSet<>();
        for (Position position : positions) {
            if (position.getUserId() != null && !position.getUserId().isEmpty()) {
                userIds.add(position.getUserId());
            }
        }
        int failed = 0;
        log.info("开始生成交易计划周报，用户数 {}", userIds.size());
        for (String userId : userIds) {
            try {
                tradePlanSummaryService.generateWeekly(userId);
            } catch (Exception e) {
                failed++;
                log.error("生成交易计划周报失败: userId={}", userId, e);
            }
        }
        log.info("交易计划周报生成完成：用户 {} 个，失败 {} 个", userIds.size(), failed);
        return failed;
    }
}
