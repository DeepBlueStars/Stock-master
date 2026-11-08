package com.xian.stockai.service.exit;

import com.xian.stockai.dao.ExitEvalRunRepository;
import com.xian.stockai.dao.ExitSignalRepository;
import com.xian.stockai.dao.PositionExitPolicyRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.StockTradeRepository;
import com.xian.stockai.po.ExitEvalRun;
import com.xian.stockai.po.ExitSignal;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionExitPolicy;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.service.market.KlineProvider;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.service.tradeplan.IndicatorCalculator;
import com.xian.stockai.vo.ExitOverviewRes;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExitOverviewServiceTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private StockTradeRepository stockTradeRepository;
    @Mock
    private PositionExitPolicyRepository policyRepository;
    @Mock
    private ExitSignalRepository signalRepository;
    @Mock
    private ExitEvalRunRepository runRepository;
    @Mock
    private KlineProvider klineProvider;
    @Mock
    private IndicatorCalculator indicatorCalculator;

    private ExitProperties properties;
    private ExitOverviewService service;

    @BeforeEach
    void setUp() {
        properties = new ExitProperties();
        ExitEvaluationService evaluation = new ExitEvaluationService(positionRepository, stockTradeRepository,
                policyRepository, signalRepository, runRepository, klineProvider, properties,
                indicatorCalculator, new ArrayList<NotificationSender>());
        service = new ExitOverviewService(positionRepository, policyRepository, signalRepository,
                runRepository, klineProvider, properties, evaluation);
        when(positionRepository.findByUserId(anyString())).thenReturn(new ArrayList<>());
        when(policyRepository.findByUserId(anyString())).thenReturn(new ArrayList<>());
        when(signalRepository.findByUserIdAndStatusOrderByCreatedAtDesc(anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(signalRepository.findByUserIdAndStatusAndAckedAtAfter(anyString(), anyString(), any()))
                .thenReturn(new ArrayList<>());
        when(runRepository.findTop20ByUserIdOrderByStartedAtDesc(anyString())).thenReturn(new ArrayList<>());
        when(klineProvider.ascending(anyString())).thenReturn(new ArrayList<>());
    }

    private static Position position(String symbol, int quantity, String avgCost) {
        Position position = new Position();
        position.setUserId("u1");
        position.setSymbol(symbol);
        position.setStockName("测试标的");
        position.setTotalQuantity(quantity);
        position.setAvgCostPrice(new BigDecimal(avgCost));
        return position;
    }

    private static List<StockDailyKline> klines(String symbol, String close) {
        return flatKlines(symbol, close, 30);
    }

    /**
     * 收盘价一字不动的日线：把波动率钉在 0，专门用来测分档
     */
    private static List<StockDailyKline> flatKlines(String symbol, String close, int count) {
        List<StockDailyKline> list = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            StockDailyKline kline = new StockDailyKline();
            kline.setSymbol(symbol);
            kline.setTradeDate(start.plusDays(i).toString());
            kline.setClose(new BigDecimal(close));
            list.add(kline);
        }
        return list;
    }

    private static PositionExitPolicy policy(String symbol, String hardStopRate) {
        PositionExitPolicy policy = new PositionExitPolicy();
        policy.setUserId("u1");
        policy.setSymbol(symbol);
        policy.setEnabled(true);
        policy.setEtf(false);
        policy.setHardStopRate(new BigDecimal(hardStopRate));
        policy.setTrailArmed(false);
        policy.setTp1Done(false);
        policy.setTp2Done(false);
        return policy;
    }

    private static ExitSignal signal(String symbol, String code, String triggerPrice) {
        ExitSignal signal = new ExitSignal();
        signal.setId("s-" + code);
        signal.setUserId("u1");
        signal.setSymbol(symbol);
        signal.setStockName("测试标的");
        signal.setLevel("L1");
        signal.setCode(code);
        signal.setStatus(ExitSignal.STATUS_TRIGGERED);
        signal.setTriggerPrice(new BigDecimal(triggerPrice));
        signal.setClose(new BigDecimal("90"));
        signal.setReturnPct(new BigDecimal("-0.1"));
        signal.setAction("全部卖出");
        return signal;
    }

    @Test
    void overviewBuildsHoldingWithStopAndNextTakeProfit() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "90"));

        Result<ExitOverviewRes> result = service.overview("u1");

        assertTrue(result.checkSuccess());
        ExitOverviewRes res = result.getData();
        assertEquals(1, res.getHoldings().size());
        ExitOverviewRes.Holding holding = res.getHoldings().get(0);
        assertEquals("600519", holding.getSymbol());
        assertEquals(0, new BigDecimal("75.0000").compareTo(holding.getStop()));
        assertEquals(0, new BigDecimal("130.0000").compareTo(holding.getTakeProfit()));
        assertEquals(0, new BigDecimal("-0.1").compareTo(holding.getReturnPct()));
        assertEquals("2026-01-30", res.getTradeDate());
        // 现价 90、止损 75：还有 16.7% 才碰到，不算警戒
        assertEquals("ok", holding.getStatus());
    }

    @Test
    void overviewUsesTrailFloorWhenTrailArmedAndHigherThanHardStop() {
        PositionExitPolicy policy = policy("600519", "0.25");
        policy.setTrailArmed(true);
        policy.setTrailFloorPrice(new BigDecimal("120.0000"));
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(policyRepository.findByUserId("u1")).thenReturn(List.of(policy));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "130"));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        assertEquals(0, new BigDecimal("120.0000").compareTo(holding.getStop()));
    }

    @Test
    void overviewUsesSecondTakeProfitAfterFirstDone() {
        PositionExitPolicy policy = policy("600519", "0.25");
        policy.setTp1Done(true);
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(policyRepository.findByUserId("u1")).thenReturn(List.of(policy));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "140"));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        assertEquals(0, new BigDecimal("160.0000").compareTo(holding.getTakeProfit()));
    }

    @Test
    void overviewReturnsNullTakeProfitWhenBothTiersDone() {
        PositionExitPolicy policy = policy("600519", "0.25");
        policy.setTp1Done(true);
        policy.setTp2Done(true);
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(policyRepository.findByUserId("u1")).thenReturn(List.of(policy));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "170"));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        assertNull(holding.getTakeProfit());
    }

    @Test
    void overviewMarksWarnWhenPriceNearStop() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "78"));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        assertEquals("warn", holding.getStatus());
    }

    @Test
    void overviewMarksTp1WhenTakeProfitSignalPending() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "200"));
        when(signalRepository.findByUserIdAndStatusOrderByCreatedAtDesc("u1", ExitSignal.STATUS_TRIGGERED))
                .thenReturn(List.of(signal("600519", "TAKE_PROFIT_1", "130")));

        ExitOverviewRes res = service.overview("u1").getData();

        assertEquals("tp1", res.getHoldings().get(0).getStatus());
        assertEquals(1, res.getSignals().size());
        ExitOverviewRes.Signal mapped = res.getSignals().get(0);
        assertEquals("止盈一档", mapped.getTag());
        assertEquals("up", mapped.getTone());
    }

    @Test
    void overviewMapsStopSignalToDownTone() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "90"));
        when(signalRepository.findByUserIdAndStatusOrderByCreatedAtDesc("u1", ExitSignal.STATUS_TRIGGERED))
                .thenReturn(List.of(signal("600519", "HARD_STOP", "75")));

        ExitOverviewRes res = service.overview("u1").getData();

        assertEquals("warn", res.getHoldings().get(0).getStatus());
        assertEquals("硬止损", res.getSignals().get(0).getTag());
        assertEquals("down", res.getSignals().get(0).getTone());
    }

    @Test
    void overviewMarksHoldingWithoutKlinesAsNoData() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        assertEquals("nodata", holding.getStatus());
        assertEquals("无日线数据", holding.getNote());
    }

    @Test
    void overviewSkipsZeroQuantityPositions() {
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 0, "100")));

        assertTrue(service.overview("u1").getData().getHoldings().isEmpty());
    }

    @Test
    void overviewRejectsBlankUser() {
        assertFalse(service.overview(" ").checkSuccess());
    }

    @Test
    void overviewCountsHandledTodayAndRunInfo() {
        ExitEvalRun run = new ExitEvalRun();
        run.setStartedAt(LocalDateTime.of(2026, 5, 8, 17, 30));
        run.setEvaluated(3);
        run.setTriggered(1);
        run.setSkipped(2);
        run.setTradeDate("2026-05-08");
        when(runRepository.findTop20ByUserIdOrderByStartedAtDesc("u1")).thenReturn(List.of(run));
        when(signalRepository.findByUserIdAndStatusAndAckedAtAfter(eq("u1"), eq(ExitSignal.STATUS_ACKED), any()))
                .thenReturn(List.of(signal("600519", "HARD_STOP", "75")));

        ExitOverviewRes res = service.overview("u1").getData();

        assertEquals(1, res.getHandledToday());
        assertEquals(3, res.getEvaluated());
        assertEquals(1, res.getTriggered());
        assertEquals("2026-05-08", res.getTradeDate());
    }

    @Test
    void etfHardStopFallsIntoLowVolatilityTier() {
        Position etf = position("515180", 600, "1.00");
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(etf));
        when(klineProvider.ascending("515180")).thenReturn(flatKlines("515180", "1.20", 70));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        // 波动率为 0（价格不动）→ 第一档 −15%
        assertEquals(0, new BigDecimal("0.85").compareTo(holding.getStop()));
    }

    @Test
    void etfHardStopFallsBackToWidestTierWithoutEnoughHistory() {
        Position etf = position("515180", 600, "1.00");
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(etf));
        when(klineProvider.ascending("515180")).thenReturn(klines("515180", "1.20"));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        // 样本不足 61 根，波动率算不出来 → 按最宽松的 −25%
        assertEquals(0, new BigDecimal("0.75").compareTo(holding.getStop()));
    }

    @Test
    void policyDisablingDoesNotHideHolding() {
        PositionExitPolicy policy = policy("600519", "0.25");
        policy.setEnabled(false);
        when(positionRepository.findByUserId("u1")).thenReturn(List.of(position("600519", 100, "100")));
        when(policyRepository.findByUserId("u1")).thenReturn(List.of(policy));
        when(klineProvider.ascending("600519")).thenReturn(klines("600519", "90"));

        ExitOverviewRes.Holding holding = service.overview("u1").getData().getHoldings().get(0);

        assertEquals(Boolean.FALSE, holding.getEnabled());
        assertEquals("卖出纪律已停用", holding.getNote());
    }
}
