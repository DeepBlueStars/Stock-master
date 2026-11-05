package com.xian.stockai.service.exit;

import com.xian.stockai.dao.ExitEvalRunRepository;
import com.xian.stockai.dao.ExitSignalRepository;
import com.xian.stockai.dao.PositionExitPolicyRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.StockTradeRepository;
import com.xian.stockai.po.ExitSignal;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionExitPolicy;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.service.market.KlineProvider;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.service.tradeplan.IndicatorCalculator;
import com.xian.stockai.vo.ExitEvaluationRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExitEvaluationServiceTest {

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
    private ExitEvaluationService service;

    @BeforeEach
    void setUp() {
        properties = new ExitProperties();
        service = new ExitEvaluationService(positionRepository, stockTradeRepository, policyRepository,
                signalRepository, runRepository, klineProvider, properties, indicatorCalculator,
                new ArrayList<NotificationSender>());
        service.setClock(CLOCK_2026_05_08);
        when(policyRepository.findByUserIdAndSymbol(anyString(), anyString())).thenReturn(Optional.empty());
        when(policyRepository.save(any(PositionExitPolicy.class))).thenAnswer(i -> i.getArgument(0));
        when(signalRepository.save(any(ExitSignal.class))).thenAnswer(i -> i.getArgument(0));
        when(signalRepository.findTop20ByUserIdAndSymbolAndCodeOrderByCreatedAtDesc(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(signalRepository.findByUserIdAndSymbolAndStatus(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
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

    private static StockTrade trade(String side, int quantity, String price, int month, int day) {
        StockTrade trade = new StockTrade();
        trade.setUserId("u1");
        trade.setSymbol("600519");
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(new BigDecimal(price));
        trade.setAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        trade.setTradeTime(LocalDateTime.of(2026, month, day, 10, 0));
        return trade;
    }

    private static final Clock CLOCK_2026_05_08 = Clock.fixed(
            LocalDate.of(2026, 5, 8).atTime(16, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    private static List<StockDailyKline> klines(int count, String startClose, String lastClose) {
        List<StockDailyKline> klines = new ArrayList<>();
        BigDecimal base = new BigDecimal(startClose);
        BigDecimal end = new BigDecimal(lastClose);
        for (int i = 0; i < count; i++) {
            StockDailyKline kline = new StockDailyKline();
            kline.setSymbol("600519");
            kline.setTradeDate(String.format("2026-%02d-%02d", 1 + i / 28, 1 + i % 28));
            if (i == count - 1) {
                kline.setClose(end);
            } else {
                kline.setClose(base);
            }
            kline.setHigh(kline.getClose());
            kline.setLow(kline.getClose());
            kline.setOpen(kline.getClose());
            klines.add(kline);
        }
        return klines;
    }

    @Test
    void replayFindsEntryDateMaxQuantityAndLastAddPrice() {
        List<StockTrade> trades = List.of(
                trade("买入", 100, "10", 1, 5),
                trade("买入", 100, "12", 1, 20),
                trade("卖出", 50, "15", 2, 10));

        ExitEvaluationService.Holding holding = service.replay(position("600519", 150, "11.33"), trades, 200);

        assertEquals("2026-01-05", holding.entryDate);
        assertEquals(200, holding.maxQuantity);
        assertNotNull(holding.lastAddTime);
        assertEquals(0, new BigDecimal("10").compareTo(holding.preAddAvgCost));
    }

    @Test
    void replayClearsEntryDateAfterFullExit() {
        List<StockTrade> trades = List.of(
                trade("买入", 100, "10", 1, 5),
                trade("卖出", 100, "12", 2, 10));

        ExitEvaluationService.Holding holding = service.replay(position("600519", 100, "10"), trades, 200);

        assertNull(holding.entryDate);
        assertEquals(0, holding.maxQuantity);
    }

    @Test
    void evaluateOneSkipsWhenKlineIsNotFromToday() {
        ExitEvaluationRes res = new ExitEvaluationRes();
        when(klineProvider.ascending("600519")).thenReturn(klines(120, "100", "70"));
        when(stockTradeRepository.findByUserIdAndSymbol("u1", "600519"))
                .thenReturn(List.of(trade("买入", 700, "100", 1, 5)));
        service.setClock(Clock.fixed(
                LocalDate.of(2026, 5, 11).atTime(17, 30).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai")));

        ExitSignal signal = service.evaluateOne(position("600519", 700, "100"), res);

        assertNull(signal);
        assertEquals(1, res.getSkipped());
        assertTrue(res.getSkippedReasons().get(0).contains("数据未更新，最新 2026-05-08"));
        verify(signalRepository, never()).save(any(ExitSignal.class));
    }

    @Test
    void evaluateOnePersistsHardStopSignal() {
        ExitEvaluationRes res = new ExitEvaluationRes();
        when(klineProvider.ascending("600519")).thenReturn(klines(120, "100", "70"));
        when(stockTradeRepository.findByUserIdAndSymbol("u1", "600519"))
                .thenReturn(List.of(trade("买入", 700, "100", 1, 5)));

        ExitSignal signal = service.evaluateOne(position("600519", 700, "100"), res);

        assertNotNull(signal);
        assertEquals("HARD_STOP", signal.getCode());
        assertEquals("L1", signal.getLevel());
        assertEquals(ExitSignal.STATUS_TRIGGERED, signal.getStatus());
        assertEquals(700, signal.getSuggestQuantity());
        assertEquals("2026-05-08", signal.getTradeDate());
    }

    @Test
    void evaluateOneSkipsWhenTheSameSignalIsStillCooling() {
        ExitEvaluationRes res = new ExitEvaluationRes();
        when(klineProvider.ascending("600519")).thenReturn(klines(120, "100", "70"));
        when(stockTradeRepository.findByUserIdAndSymbol("u1", "600519"))
                .thenReturn(List.of(trade("买入", 700, "100", 1, 5)));
        ExitSignal previous = new ExitSignal();
        previous.setStatus(ExitSignal.STATUS_TRIGGERED);
        previous.setTradeDate("2026-05-27");
        previous.setCode("HARD_STOP");
        when(signalRepository.findTop20ByUserIdAndSymbolAndCodeOrderByCreatedAtDesc("u1", "600519", "HARD_STOP"))
                .thenReturn(List.of(previous));

        assertNull(service.evaluateOne(position("600519", 700, "100"), res));
        assertEquals(1, res.getSkipped());
    }

    @Test
    void evaluateOneSkipsDisabledPolicy() {
        ExitEvaluationRes res = new ExitEvaluationRes();
        when(klineProvider.ascending("600519")).thenReturn(klines(120, "100", "70"));

        PositionExitPolicy disabled = new PositionExitPolicy();
        disabled.setUserId("u1");
        disabled.setSymbol("600519");
        disabled.setEnabled(false);
        when(policyRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(disabled));

        assertNull(service.evaluateOne(position("600519", 700, "100"), res));
        assertEquals(1, res.getSkipped());
        assertTrue(res.getSkippedReasons().get(0).contains("已停用"));
    }

    @Test
    void etfHardStopOnlySwitchesTiersAfterCrossingTheHysteresisBand() {
        // 从 −15% 档：σ 涨到 15.5%，刚越过 15% 边界但没越过 15%+2% 的带宽 → 不动
        assertEquals(0, new BigDecimal("0.15").compareTo(
                service.resolveEtfHardStopRate(new BigDecimal("0.15"), new BigDecimal("0.155"))));
        // 涨到 17.5%：越过带宽 → 换到 −20%
        assertEquals(0, new BigDecimal("0.20").compareTo(
                service.resolveEtfHardStopRate(new BigDecimal("0.15"), new BigDecimal("0.175"))));
        // 从 −25% 档回落到 24.5%：没跌破 25%−2% → 维持 −25%
        assertEquals(0, new BigDecimal("0.25").compareTo(
                service.resolveEtfHardStopRate(new BigDecimal("0.25"), new BigDecimal("0.245"))));
        // 继续回落到 22%：跌破 23% 的带宽 → 换到 −20%
        assertEquals(0, new BigDecimal("0.20").compareTo(
                service.resolveEtfHardStopRate(new BigDecimal("0.25"), new BigDecimal("0.22"))));
        // 首次判定（没有历史档位）直接按 σ 落档
        assertEquals(0, new BigDecimal("0.15").compareTo(
                service.resolveEtfHardStopRate(null, new BigDecimal("0.139"))));
        assertEquals(0, new BigDecimal("0.20").compareTo(
                service.resolveEtfHardStopRate(null, new BigDecimal("0.24"))));
        // σ 算不出来时保留已有档位，不要突然收紧
        assertEquals(0, new BigDecimal("0.20").compareTo(
                service.resolveEtfHardStopRate(new BigDecimal("0.20"), null)));
    }

    @Test
    void evaluateOneResetsTrailingStateAfterFullExitAndRebuy() {
        ExitEvaluationRes res = new ExitEvaluationRes();
        when(klineProvider.ascending("600519")).thenReturn(klines(120, "100", "100"));
        when(stockTradeRepository.findByUserIdAndSymbol("u1", "600519"))
                .thenReturn(List.of(trade("买入", 700, "100", 1, 5)));

        PositionExitPolicy stale = new PositionExitPolicy();
        stale.setUserId("u1");
        stale.setSymbol("600519");
        stale.setEntryDate("2025-06-01");
        stale.setTrailArmed(true);
        stale.setTrailPeakClose(new BigDecimal("200"));
        stale.setTp1Done(true);
        when(policyRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(Optional.of(stale));

        assertNull(service.evaluateOne(position("600519", 700, "100"), res));
        assertEquals(false, stale.getTrailArmed());
        assertEquals(0, new BigDecimal("100").compareTo(stale.getTrailPeakClose()));
        assertEquals(false, stale.getTp1Done());
    }

    @Test
    void evaluateOneSkipsWhenNoKline() {
        ExitEvaluationRes res = new ExitEvaluationRes();
        when(klineProvider.ascending("600519")).thenReturn(new ArrayList<>());

        assertNull(service.evaluateOne(position("600519", 700, "100"), res));
        assertEquals(1, res.getSkipped());
        assertTrue(res.getSkippedReasons().get(0).contains("无日线数据"));
    }

    @Test
    void annualizedVolatilityIsZeroForAFlatSeries() {
        List<StockDailyKline> flat = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            StockDailyKline kline = new StockDailyKline();
            kline.setTradeDate(String.format("2026-%02d-%02d", 1 + i / 28, 1 + i % 28));
            kline.setClose(new BigDecimal("10.00"));
            flat.add(kline);
        }

        BigDecimal sigma = ExitEvaluationService.annualizedVolatility(flat, 60);

        assertNotNull(sigma);
        assertEquals(0, sigma.compareTo(BigDecimal.ZERO));
    }
}
