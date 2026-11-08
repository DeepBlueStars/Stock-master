package com.xian.stockai.service;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.MarketQuotes;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.vo.MonitorSaveRequest;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

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

    private MonitorService monitorService;

    @BeforeEach
    void setUp() {
        monitorService = new MonitorService(
                positionMonitorRepository, positionRepository, marketQuotesRepository,
                etfDailyQuotesRepository, List.of(notificationSender));
    }

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

    private MarketQuotes stockQuote(String close) {
        MarketQuotes q = new MarketQuotes();
        q.setCode(SYMBOL);
        q.setClose(new BigDecimal(close));
        return q;
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

    @Test
    void priceTakeProfitTriggersWhenCloseAboveTarget() {
        PositionMonitor rule = priceTpRule();
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_TAKE_PROFIT, rule.getLastTriggerDirection());
        verify(positionMonitorRepository).save(rule);
    }

    @Test
    void rateTakeProfitTriggersWhenRateAboveTarget() {
        PositionMonitor rule = priceTpRule();
        rule.setTakeProfitType(MonitorService.TYPE_RATE);
        rule.setTakeProfitValue(new BigDecimal("0.10"));
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("11.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_TAKE_PROFIT, rule.getLastTriggerDirection());
    }

    @Test
    void priceStopLossTriggersWhenCloseBelowTarget() {
        PositionMonitor rule = priceTpRule();
        rule.setTakeProfitType(null);
        rule.setTakeProfitValue(null);
        rule.setStopLossType(MonitorService.TYPE_PRICE);
        rule.setStopLossValue(new BigDecimal("9.00"));
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("8.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_STOP_LOSS, rule.getLastTriggerDirection());
    }

    @Test
    void rateStopLossTriggersWhenRateBelowTarget() {
        PositionMonitor rule = priceTpRule();
        rule.setTakeProfitType(null);
        rule.setTakeProfitValue(null);
        rule.setStopLossType(MonitorService.TYPE_RATE);
        rule.setStopLossValue(new BigDecimal("-0.08"));
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("9.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_STOP_LOSS, rule.getLastTriggerDirection());
    }

    @Test
    void sameDirectionDoesNotDuplicateAndResetsAfterReturnToSafeZone() {
        PositionMonitor rule = priceTpRule();
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        monitorService.checkRule(rule);
        monitorService.checkRule(rule);
        verify(notificationSender, times(1)).send(any());

        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("14.00"));
        monitorService.checkRule(rule);
        assertEquals(MonitorService.DIR_NONE, rule.getLastTriggerDirection());

        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        monitorService.checkRule(rule);
        verify(notificationSender, times(2)).send(any());
    }

    @Test
    void etfUsesEtfDailyQuotesPriceSource() {
        PositionMonitor rule = priceTpRule();
        rule.setSymbol("510300");
        rule.setTakeProfitValue(new BigDecimal("4.20"));
        EtfDailyQuotes etf = new EtfDailyQuotes();
        etf.setSymbol("510300");
        etf.setClose(new BigDecimal("4.50"));
        when(etfDailyQuotesRepository.findTopBySymbolOrderByTradeDateDescUpdatedAtDesc("510300")).thenReturn(etf);
        when(positionRepository.findByUserIdAndSymbol(USER_ID, "510300")).thenReturn(position(new BigDecimal("4.00")));

        monitorService.checkRule(rule);

        verify(notificationSender).send(any());
        assertEquals(MonitorService.DIR_TAKE_PROFIT, rule.getLastTriggerDirection());
    }

    @Test
    void notifyInvokesAllSenders() {
        NotificationSender sender2 = mock(NotificationSender.class);
        MonitorService multiService = new MonitorService(
                positionMonitorRepository, positionRepository, marketQuotesRepository,
                etfDailyQuotesRepository, List.of(notificationSender, sender2));
        PositionMonitor rule = priceTpRule();
        when(marketQuotesRepository.findByCode(SYMBOL)).thenReturn(stockQuote("16.00"));
        when(positionRepository.findByUserIdAndSymbol(USER_ID, SYMBOL)).thenReturn(position(new BigDecimal("10.00")));

        multiService.checkRule(rule);

        verify(notificationSender).send(any());
        verify(sender2).send(any());
    }
}