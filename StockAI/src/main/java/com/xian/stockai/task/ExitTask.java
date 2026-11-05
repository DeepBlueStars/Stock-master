package com.xian.stockai.task;

import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.service.exit.ExitEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 每个交易日 17:30 跑卖出纪律判定。
 * <p>
 * 之所以不用原来的 15:30：个股日线要等 etf-service 16:00~16:20 的任务落库，
 * 15:30 判定拿到的是昨天的收盘价，止损止盈会晚一天。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExitTask {

    private final ExitEvaluationService exitEvaluationService;
    private final PositionRepository positionRepository;

    @Scheduled(cron = "${exit.cron:0 30 17 ? * MON-FRI}")
    public void checkAll() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.info("卖出纪律判定：{} 是周末，跳过", today);
            return;
        }
        List<String> userIds = distinctUserIds();
        if (userIds.isEmpty()) {
            log.info("卖出纪律判定：没有有效持仓用户");
            return;
        }
        log.info("开始卖出纪律判定，用户数 {}", userIds.size());
        for (String userId : userIds) {
            try {
                exitEvaluationService.evaluate(userId, "TASK");
            } catch (Exception e) {
                log.error("卖出纪律判定失败: userId={}", userId, e);
            }
        }
        log.info("卖出纪律判定结束");
    }

    private List<String> distinctUserIds() {
        List<String> userIds = new ArrayList<>();
        List<Position> positions = positionRepository.findByTotalQuantityGreaterThan(0);
        if (positions == null) {
            return userIds;
        }
        for (Position position : positions) {
            if (position.getUserId() != null && !userIds.contains(position.getUserId())) {
                userIds.add(position.getUserId());
            }
        }
        return userIds;
    }
}
