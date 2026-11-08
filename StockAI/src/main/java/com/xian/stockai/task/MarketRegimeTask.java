package com.xian.stockai.task;

import com.xian.stockai.service.MarketRegimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketRegimeTask {
    private final MarketRegimeService marketRegimeService;

    @Scheduled(cron = "${market-regime.cron:0 30 16 ? * MON-FRI}")
    public void evaluateDaily() {
        log.info("开始市场温度计收盘判定（16:30，等指数日线落库之后）");
        marketRegimeService.evaluateAndSave();
        log.info("市场温度计收盘判定结束");
    }
}
