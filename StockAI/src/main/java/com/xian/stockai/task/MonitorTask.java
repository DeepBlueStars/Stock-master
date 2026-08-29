package com.xian.stockai.task;

import com.xian.stockai.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorTask {
    private final MonitorService monitorService;

    @Scheduled(cron = "${monitor.cron:0 30 15 * * ?}")
    public void checkAll() {
        log.info("开始持仓止盈止损监控判定");
        monitorService.checkAll();
        log.info("持仓止盈止损监控判定结束");
    }
}