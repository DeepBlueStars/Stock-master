package com.xian.stockai.task;

import com.xian.stockai.service.MarketRegimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketRegimeTaskTest {

    @Mock
    private MarketRegimeService marketRegimeService;

    @Test
    void evaluateDailyDelegatesToService() {
        MarketRegimeTask task = new MarketRegimeTask(marketRegimeService);

        task.evaluateDaily();

        verify(marketRegimeService).evaluateAndSave();
    }

    @Test
    void evaluateDailyRunsAfterIndexSync() throws Exception {
        Method method = MarketRegimeTask.class.getMethod("evaluateDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        // 16:30 必须晚于 etf-service 的指数同步（15:30），否则判定吃到的是旧日线
        assertEquals("${market-regime.cron:0 30 16 ? * MON-FRI}", scheduled.cron());
    }
}
