package com.xian.stockai.service.tradeplan;

import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.dao.TradePlanRepository;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.po.TradePlan;
import com.xian.stockai.service.MonitorService;
import com.xian.stockai.vo.MonitorLinkRes;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradePlanLinkMonitorTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private StockIndustryConceptRepository stockIndustryConceptRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private TradePlanRepository tradePlanRepository;
    @Mock
    private PositionMonitorRepository positionMonitorRepository;
    @Mock
    private MonitorService monitorService;
    @Mock
    private TradePlanCalculator tradePlanCalculator;

    private TradePlanService service;

    @BeforeEach
    void setUp() {
        service = new TradePlanService(positionRepository, stockDailyKlineRepository,
                stockIndustryConceptRepository, usersRepository, tradePlanRepository,
                positionMonitorRepository, monitorService, tradePlanCalculator);
    }

    private TradePlan plan() {
        TradePlan plan = new TradePlan();
        plan.setUserId("u1");
        plan.setSymbol("600519");
        plan.setStockName("贵州茅台");
        plan.setStopLossPrice(new BigDecimal("6.00"));
        plan.setTakeProfitPrice(new BigDecimal("18.00"));
        return plan;
    }

    @Test
    void overwritesExistingRuleAndKeepsEnabledState() {
        TradePlan plan = plan();
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(plan));
        PositionMonitor existing = new PositionMonitor();
        existing.setId("m1");
        existing.setUserId("u1");
        existing.setSymbol("600519");
        existing.setEnabled(false);
        existing.setTakeProfitType(MonitorService.TYPE_PRICE);
        existing.setTakeProfitValue(new BigDecimal("20.00"));
        existing.setStopLossType(MonitorService.TYPE_PRICE);
        existing.setStopLossValue(new BigDecimal("7.00"));
        when(positionMonitorRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(existing);

        Result<MonitorLinkRes> result = service.linkMonitor("u1", "600519");

        assertTrue(result.checkSuccess());
        assertEquals(0, new BigDecimal("6.00").compareTo(result.getData().getAfter().getStopLossValue()));
        assertEquals(0, new BigDecimal("18.00").compareTo(result.getData().getAfter().getTakeProfitValue()));
        assertEquals(0, new BigDecimal("20.00").compareTo(result.getData().getBefore().getTakeProfitValue()));
        assertFalse(result.getData().getAfter().getEnabled());
        verify(positionMonitorRepository).save(existing);
        assertTrue(Boolean.TRUE.equals(plan.getMonitorLinked()));
        verify(tradePlanRepository).save(plan);
    }

    @Test
    void createsAndEnablesRuleWhenAbsent() {
        TradePlan plan = plan();
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(plan));
        when(positionMonitorRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(null);

        Result<MonitorLinkRes> result = service.linkMonitor("u1", "600519");

        assertTrue(result.checkSuccess());
        assertTrue(result.getData().getAfter().getEnabled());
        assertEquals(MonitorService.TYPE_PRICE, result.getData().getAfter().getStopLossType());
        assertEquals(0, new BigDecimal("6.00").compareTo(result.getData().getAfter().getStopLossValue()));
        assertTrue(Boolean.TRUE.equals(plan.getMonitorLinked()));
        verify(positionMonitorRepository).save(any(PositionMonitor.class));
    }

    @Test
    void failsWhenPlanMissing() {
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.empty());

        Result<MonitorLinkRes> result = service.linkMonitor("u1", "600519");

        assertFalse(result.checkSuccess());
        verify(positionMonitorRepository, never()).save(any());
    }
}
