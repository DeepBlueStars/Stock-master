package com.xian.stockai.service.tradeplan;

import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.dao.TradePlanRepository;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.po.TradePlan;
import com.xian.stockai.po.Users;
import com.xian.stockai.service.MonitorService;
import com.xian.stockai.service.market.KlineProvider;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.TradePlanRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradePlanServiceTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private KlineProvider klineProvider;
    @Mock
    private StockIndustryConceptRepository industryConceptRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private TradePlanRepository tradePlanRepository;
    @Mock
    private PositionMonitorRepository positionMonitorRepository;
    @Mock
    private MonitorService monitorService;
    @Mock
    private TradePlanCalculator calculator;

    private TradePlanService service;

    @BeforeEach
    void setUp() {
        service = new TradePlanService(positionRepository, klineProvider, industryConceptRepository,
                usersRepository, tradePlanRepository, positionMonitorRepository, monitorService, calculator);
    }

    private Position position(String symbol, int quantity, String avgCost) {
        Position p = new Position();
        p.setUserId("u1");
        p.setSymbol(symbol);
        p.setStockName("测试股");
        p.setTotalQuantity(quantity);
        p.setAvgCostPrice(new BigDecimal(avgCost));
        p.setTotalCost(new BigDecimal(avgCost).multiply(BigDecimal.valueOf(quantity)));
        return p;
    }

    @Test
    void refreshPlanSavesPlanWithIndustryAndRemainingCash() {
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position("600519", 100, "10.00"));
        when(klineProvider.ascending("600519")).thenReturn(new ArrayList<>());
        when(monitorService.getCurrentPrice("600519")).thenReturn(new BigDecimal("10.00"));
        StockIndustryConcept concept = new StockIndustryConcept();
        concept.setIndustry("白酒");
        when(industryConceptRepository.findBySymbol("600519")).thenReturn(concept);
        Users user = new Users();
        user.setCash(new BigDecimal("10000"));
        when(usersRepository.findById("u1")).thenReturn(Optional.of(user));
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.empty());
        TradePlan generated = new TradePlan();
        generated.setSymbol("600519");
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(generated);

        service.refreshPlan("u1", "600519");

        verify(calculator).calculate(eq(new TradePlanCalculator.CashContext(new BigDecimal("10000"),
                TradePlan.CASH_BASIS_REMAINING)), any(), eq("白酒"), any(), eq(new BigDecimal("10.00")));
        verify(tradePlanRepository).save(generated);
    }

    @Test
    void refreshPlanPreservesMonitorLinkedFlag() {
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position("600519", 100, "10.00"));
        when(klineProvider.ascending("600519")).thenReturn(new ArrayList<>());
        when(monitorService.getCurrentPrice("600519")).thenReturn(new BigDecimal("10.00"));
        TradePlan existing = new TradePlan();
        existing.setId("p1");
        existing.setSymbol("600519");
        existing.setMonitorLinked(true);
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(existing));
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(new TradePlan());

        service.refreshPlan("u1", "600519");

        org.mockito.ArgumentCaptor<TradePlan> captor = org.mockito.ArgumentCaptor.forClass(TradePlan.class);
        verify(tradePlanRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getMonitorLinked()));
        assertEquals("p1", captor.getValue().getId());
    }

    @Test
    void refreshPlanCarriesOverBoughtMarks() {
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position("600519", 100, "10.00"));
        when(klineProvider.ascending("600519")).thenReturn(new ArrayList<>());
        when(monitorService.getCurrentPrice("600519")).thenReturn(new BigDecimal("10.00"));

        TradePlan.TradePlanBatch oldFirst = new TradePlan.TradePlanBatch(
                new BigDecimal("50"), new BigDecimal("10.00"), new BigDecimal("5000"));
        oldFirst.setBought(true);
        oldFirst.setBoughtAt(java.time.LocalDateTime.of(2026, 11, 6, 14, 30));
        TradePlan.TradePlanBatch oldSecond = new TradePlan.TradePlanBatch(
                new BigDecimal("50"), new BigDecimal("9.00"), new BigDecimal("5000"));
        TradePlan existing = new TradePlan();
        existing.setBuyBatches(new ArrayList<>(List.of(oldFirst, oldSecond)));
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(existing));

        TradePlan generated = new TradePlan();
        generated.setBuyBatches(new ArrayList<>(List.of(
                new TradePlan.TradePlanBatch(new BigDecimal("50"), new BigDecimal("10.10"), new BigDecimal("5000")),
                new TradePlan.TradePlanBatch(new BigDecimal("50"), new BigDecimal("9.10"), new BigDecimal("5000")))));
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(generated);

        service.refreshPlan("u1", "600519");

        assertTrue(Boolean.TRUE.equals(generated.getBuyBatches().get(0).getBought()), "第一批的已买入标记应被保留");
        assertEquals(java.time.LocalDateTime.of(2026, 11, 6, 14, 30),
                generated.getBuyBatches().get(0).getBoughtAt());
        assertTrue(!Boolean.TRUE.equals(generated.getBuyBatches().get(1).getBought()), "没买的批次不能被带成已买入");
    }

    @Test
    void markBatchTogglesBoughtFlag() {
        TradePlan plan = new TradePlan();
        plan.setUserId("u1");
        plan.setSymbol("600519");
        plan.setBuyBatches(new ArrayList<>(List.of(
                new TradePlan.TradePlanBatch(new BigDecimal("50"), new BigDecimal("10.00"), new BigDecimal("5000")),
                new TradePlan.TradePlanBatch(new BigDecimal("50"), new BigDecimal("9.00"), new BigDecimal("5000")))));
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(plan));

        Result<TradePlanRes> result = service.markBatch("u1", "600519", 1, true);

        assertTrue(result.checkSuccess());
        assertTrue(Boolean.TRUE.equals(plan.getBuyBatches().get(1).getBought()));
        assertNotNull(plan.getBuyBatches().get(1).getBoughtAt());
        verify(tradePlanRepository).save(plan);

        service.markBatch("u1", "600519", 1, false);
        assertTrue(!Boolean.TRUE.equals(plan.getBuyBatches().get(1).getBought()));
        assertNull(plan.getBuyBatches().get(1).getBoughtAt());
    }

    @Test
    void markBatchRejectsOutOfRangeIndex() {
        TradePlan plan = new TradePlan();
        plan.setBuyBatches(new ArrayList<>(List.of(
                new TradePlan.TradePlanBatch(new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("5000")))));
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(plan));

        Result<TradePlanRes> result = service.markBatch("u1", "600519", 3, true);

        assertTrue(!result.checkSuccess());
        verify(tradePlanRepository, times(0)).save(any());
    }

    @Test
    void markBatchFailsWhenPlanMissing() {
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.empty());

        Result<TradePlanRes> result = service.markBatch("u1", "600519", 0, true);

        assertTrue(!result.checkSuccess());
        assertTrue(result.getMessage().contains("不存在"));
    }

    @Test
    void refreshPlanSkipsWhenPositionAbsent() {
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(null);

        service.refreshPlan("u1", "600519");

        verify(tradePlanRepository, times(0)).save(any());
    }

    @Test
    void listGeneratesMissingPlansIdempotently() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "10.00")));
        when(tradePlanRepository.findByUserId("u1")).thenReturn(new ArrayList<>());
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position("600519", 100, "10.00"));
        when(klineProvider.ascending("600519")).thenReturn(new ArrayList<>());
        when(monitorService.getCurrentPrice("600519")).thenReturn(new BigDecimal("10.00"));
        TradePlan generated = new TradePlan();
        generated.setUserId("u1");
        generated.setSymbol("600519");
        generated.setStockName("测试股");
        when(calculator.calculate(any(), any(), any(), any(), any())).thenReturn(generated);
        when(tradePlanRepository.findByUserIdAndSymbol("u1", "600519"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(generated));

        Result<List<TradePlanRes>> result = service.list("u1");

        assertTrue(result.checkSuccess());
        assertEquals(1, result.getData().size());
        assertEquals("600519", result.getData().get(0).getSymbol());
        assertNotNull(result.getData().get(0).getStockName());
    }
}
