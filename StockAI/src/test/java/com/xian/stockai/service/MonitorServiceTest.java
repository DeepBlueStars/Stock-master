package com.xian.stockai.service;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.vo.MonitorSaveRequest;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private PositionMonitorRepository positionMonitorRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private MarketQuotesRepository marketQuotesRepository;
    @Mock
    private EtfDailyQuotesRepository etfDailyQuotesRepository;
    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private MonitorService monitorService;

    private static final String USER_ID = "u1";
    private static final String SYMBOL = "000001";

    private PositionMonitor priceTpRule() {
        PositionMonitor rule = new PositionMonitor();
        rule.setId("r1");
        rule.setUserId(USER_ID);
        rule.setSymbol(SYMBOL);
        rule.setStockName("平安银行");
        rule.setTakeProfitType(MonitorService.TYPE_PRICE);
        rule.setTakeProfitValue(new BigDecimal("15.00"));
        rule.setLastTriggerDirection(MonitorService.DIR_NONE);
        return rule;
    }

    private Position position(BigDecimal avgCostPrice) {
        Position p = new Position();
        p.setUserId(USER_ID);
        p.setSymbol(SYMBOL);
        p.setStockName("平安银行");
        p.setType("科技");
        p.setAvgCostPrice(avgCostPrice);
        return p;
    }

    @Test
    void saveCreatesEnabledRuleWithNoneDirection() {
        MonitorSaveRequest req = new MonitorSaveRequest();
        req.setUserId(USER_ID);
        req.setSymbol(SYMBOL);
        req.setTakeProfitType(MonitorService.TYPE_PRICE);
        req.setTakeProfitValue(new BigDecimal("15.00"));

        when(positionMonitorRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(null);
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        Result<PositionMonitor> result = monitorService.save(req);

        assertTrue(result.checkSuccess());
        assertTrue(result.getData().getEnabled());
        assertEquals(MonitorService.DIR_NONE, result.getData().getLastTriggerDirection());
        assertEquals("平安银行", result.getData().getStockName());
        verify(positionMonitorRepository).save(any(PositionMonitor.class));
    }

    @Test
    void saveRejectsRuleWithoutThresholds() {
        MonitorSaveRequest req = new MonitorSaveRequest();
        req.setUserId(USER_ID);
        req.setSymbol(SYMBOL);

        Result<PositionMonitor> result = monitorService.save(req);

        assertFalse(result.checkSuccess());
        verify(positionMonitorRepository, never()).save(any());
    }

    @Test
    void enableUpdatesRule() {
        PositionMonitor existing = priceTpRule();
        existing.setEnabled(true);
        when(positionMonitorRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(existing);

        Result<Void> result = monitorService.enable(USER_ID, SYMBOL, false);

        assertTrue(result.checkSuccess());
        assertFalse(existing.getEnabled());
        verify(positionMonitorRepository).save(existing);
    }

    @Test
    void deleteRemovesRule() {
        PositionMonitor existing = priceTpRule();
        when(positionMonitorRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(existing);

        Result<Void> result = monitorService.delete(USER_ID, SYMBOL);

        assertTrue(result.checkSuccess());
        verify(positionMonitorRepository).delete(existing);
    }
}