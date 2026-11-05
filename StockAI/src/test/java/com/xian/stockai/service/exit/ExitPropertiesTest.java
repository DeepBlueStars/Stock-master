package com.xian.stockai.service.exit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "exit.hard-stop-rate=0.25",
                    "exit.trailing-arm-return=0.30",
                    "exit.trailing-rate=0.15",
                    "exit.breakeven-return=0.10",
                    "exit.take-profit-1-return=0.30",
                    "exit.take-profit-1-fraction=0.3333",
                    "exit.take-profit-2-return=0.60",
                    "exit.add-buffer-trade-days=10",
                    "exit.signal-cooldown-trade-days=5",
                    "exit.notify-enabled=false",
                    "exit.cron=0 30 17 ? * MON-FRI",
                    "exit.etf-hard-stop-tiers[0].max-volatility=0.15",
                    "exit.etf-hard-stop-tiers[0].hard-stop-rate=0.15",
                    "exit.etf-hard-stop-tiers[1].max-volatility=0.25",
                    "exit.etf-hard-stop-tiers[1].hard-stop-rate=0.20",
                    "exit.etf-hard-stop-tiers[2].hard-stop-rate=0.25");

    @Test
    void bindsTiersAndDefaults() {
        runner.run(context -> {
            ExitProperties properties = context.getBean(ExitProperties.class);
            assertEquals(0, new BigDecimal("0.25").compareTo(properties.getHardStopRate()));
            assertEquals(3, properties.getEtfHardStopTiers().size());
            assertEquals(0, new BigDecimal("0.20").compareTo(
                    properties.getEtfHardStopTiers().get(1).getHardStopRate()));
            assertNull(properties.getEtfHardStopTiers().get(2).getMaxVolatility());
            assertEquals(10, properties.getAddBufferTradeDays());
            assertEquals(5, properties.getSignalCooldownTradeDays());
            assertTrue(!properties.isNotifyEnabled());
            assertEquals("0 30 17 ? * MON-FRI", properties.getCron());
        });
    }

    @Configuration
    @EnableConfigurationProperties(ExitProperties.class)
    static class TestConfig {
    }
}
