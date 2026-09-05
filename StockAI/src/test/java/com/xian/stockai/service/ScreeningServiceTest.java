package com.xian.stockai.service;

import com.xian.stockai.config.ScreeningProperties;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.StockBasicInfoRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockFundamentalsRepository;
import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.StockBasicInfo;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockFundamentals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    @Mock
    private IndexConstituentsRepository indexConstituentsRepository;
    @Mock
    private StockFundamentalsRepository stockFundamentalsRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private StockBasicInfoRepository stockBasicInfoRepository;

    private ScreeningService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningService(indexConstituentsRepository, stockFundamentalsRepository,
                stockDailyKlineRepository, stockBasicInfoRepository, new ScreeningProperties());
    }

    private IndexConstituents constituent(String symbol) {
        IndexConstituents c = new IndexConstituents();
        c.setSymbol(symbol);
        return c;
    }

    private StockBasicInfo basic(String symbol, String name) {
        StockBasicInfo b = new StockBasicInfo();
        b.setSymbol(symbol);
        b.setName(name);
        return b;
    }

    private StockFundamentals fund(String reportDate, String pe, String pb, String roe,
                                   String revenueYoy, String profitYoy, String grossMargin) {
        StockFundamentals f = new StockFundamentals();
        f.setReportDate(reportDate);
        f.setPe(new BigDecimal(pe));
        f.setPb(new BigDecimal(pb));
        f.setRoe(new BigDecimal(roe));
        f.setRevenueYoy(new BigDecimal(revenueYoy));
        f.setProfitYoy(new BigDecimal(profitYoy));
        f.setGrossMargin(new BigDecimal(grossMargin));
        return f;
    }

    /** 生成 count 条降序 K 线（index 0 = 最新 close=latestClose，index count-1 = 最旧 close=oldestClose，等差）。 */
    private List<StockDailyKline> descendingKlines(String symbol, int count,
                                                   String oldestClose, String latestClose, String amount) {
        BigDecimal oldest = new BigDecimal(oldestClose);
        BigDecimal latest = new BigDecimal(latestClose);
        BigDecimal step = latest.subtract(oldest)
                .divide(BigDecimal.valueOf(count - 1), 6, RoundingMode.HALF_UP);
        List<StockDailyKline> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StockDailyKline k = new StockDailyKline();
            k.setSymbol(symbol);
            k.setTradeDate(LocalDate.of(2026, 9, 1).minusDays(i).toString());
            k.setClose(latest.subtract(step.multiply(BigDecimal.valueOf(i))));
            k.setAmount(new BigDecimal(amount));
            list.add(k);
        }
        return list;
    }

    private void mockStandardPool() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of(
                constituent("A"), constituent("B"), constituent("C"),
                constituent("D"), constituent("E")));
        when(stockBasicInfoRepository.findAll()).thenReturn(List.of(
                basic("A", "甲公司"), basic("B", "乙公司"), basic("C", "丙公司"),
                basic("D", "丁公司"), basic("E", "戊公司")));
        // A：低 PE 高 ROE；B：高 PE 低 ROE；C：PE 为 -5（≤0）
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("A"))
                .thenReturn(fund("2026-06-30", "8", "1", "25", "30", "40", "50"));
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("B"))
                .thenReturn(fund("2026-06-30", "20", "3", "10", "10", "5", "20"));
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("C"))
                .thenReturn(fund("2026-06-30", "-5", "2", "15", "20", "10", "30"));
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("D"))
                .thenReturn(fund("2026-06-30", "15", "2", "18", "15", "12", "35"));
        // E：财务超 1 年
        when(stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc("E"))
                .thenReturn(fund("2024-01-01", "10", "2", "20", "20", "15", "40"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("A"))
                .thenReturn(descendingKlines("A", 21, "10", "12", "100000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("B"))
                .thenReturn(descendingKlines("B", 21, "10", "10", "100000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("C"))
                .thenReturn(descendingKlines("C", 21, "10", "11", "100000"));
        // D：K 线不足 21 条
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("D"))
                .thenReturn(descendingKlines("D", 10, "10", "11", "100000"));
        when(stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc("E"))
                .thenReturn(descendingKlines("E", 21, "10", "11", "100000"));
    }

    private Map<String, StockFactor> factorsBySymbol(String tradeDate) {
        return service.calculateFactors(tradeDate).stream()
                .collect(Collectors.toMap(StockFactor::getSymbol, Function.identity()));
    }

    @Test
    void highRoeEarnsHigherQualityScore() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        // ROE 有效值 [25,10,15,18]（A/B/C/D）：正向百分位 A=100、B=0、C=33、D=67
        assertEquals(100, map.get("A").getQuality());
        assertEquals(0, map.get("B").getQuality());
        assertEquals(33, map.get("C").getQuality());
    }

    @Test
    void lowPeEarnsHigherValuationScoreAndNonPositivePeScoresZero() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        // PE>0 参与反向百分位 [8,20,15]：A(8)=100、B(20)=0；C 的 PE=-5 ≤0 → PE 子分 0
        assertEquals(100, map.get("A").getValuation());
        assertEquals(0, map.get("B").getValuation());
        // C: PE 子分 0，PB=2 在 [1,3,2,2] 反向百分位=33 → valuation = 0*0.5 + 33*0.5 = 16.5 → 17
        assertEquals(17, map.get("C").getValuation());
        assertEquals(0, map.get("C").getPePct());
    }

    @Test
    void momentumIsNullWhenKlinesInsufficient() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        assertNull(map.get("D").getMomentum());
        assertNull(map.get("D").getMomentumReturn());
        // D 仅缺动量（缺 1 个），不算 dataIncomplete
        assertFalse(Boolean.TRUE.equals(map.get("D").getDataIncomplete()));
    }

    @Test
    void momentumReturnComputedFrom21Bars() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        // A: 最新 12 / 20 日前 10 - 1 = 0.2
        assertEquals(0, new BigDecimal("0.2").compareTo(map.get("A").getMomentumReturn()));
        // 动量百分位：A=0.2 > C/E=0.1 > B=0 → A=100
        assertEquals(100, map.get("A").getMomentum());
    }

    @Test
    void staleFundamentalsYieldNullFactorsAndIncomplete() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        StockFactor e = map.get("E");
        assertNull(e.getValuation());
        assertNull(e.getGrowth());
        assertNull(e.getQuality());
        assertNull(e.getDataDate());
        assertTrue(Boolean.TRUE.equals(e.getDataIncomplete()));
    }

    @Test
    void freshFundamentalsRecordDataDateAndRawValues() {
        mockStandardPool();
        Map<String, StockFactor> map = factorsBySymbol("2026-09-01");
        StockFactor a = map.get("A");
        assertEquals("2026-06-30", a.getDataDate());
        assertEquals(new BigDecimal("8"), a.getPe());
        assertEquals(new BigDecimal("25"), a.getRoe());
        assertEquals(100, a.getRoePct());
        assertFalse(Boolean.TRUE.equals(a.getDataIncomplete()));
    }

    @Test
    void emptyPoolReturnsEmptyList() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of());
        assertTrue(service.calculateFactors("2026-09-01").isEmpty());
    }

    // ==== Task 3: 综合评分与风格榜单 ====

    private StockFactor factor(String symbol, Integer val, Integer gro, Integer qua, Integer mon) {
        StockFactor f = new StockFactor();
        f.setSymbol(symbol);
        f.setName(symbol + "名称");
        f.setValuation(val);
        f.setGrowth(gro);
        f.setQuality(qua);
        f.setMomentum(mon);
        f.setDataIncomplete(false);
        return f;
    }

    @Test
    void buildBoardWeightsAndRanksByTotalScore() {
        List<StockFactor> factors = List.of(
                factor("A", 100, 100, 100, 100),
                factor("B", 60, 80, 70, 90),
                factor("C", null, 60, 80, 70)); // 缺估值 → 重归一化
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_VALUE, factors);
        // value 权重 [0.4,0.2,0.3,0.1]：A=100.00；B=0.4*60+0.2*80+0.3*70+0.1*90=70.00；
        // C 剔除估值后 (0.2*60+0.3*80+0.1*70)/0.6 = 43/0.6 = 71.67
        assertEquals(3, board.size());
        assertEquals("A", board.get(0).getSymbol());
        assertEquals("C", board.get(1).getSymbol());
        assertEquals("B", board.get(2).getSymbol());
        assertEquals(0, new BigDecimal("100.00").compareTo(board.get(0).getTotalScore()));
        assertEquals(0, new BigDecimal("71.67").compareTo(board.get(1).getTotalScore()));
        assertEquals(0, new BigDecimal("70.00").compareTo(board.get(2).getTotalScore()));
    }

    @Test
    void buildBoardGrowthStyleUsesGrowthWeights() {
        List<StockFactor> factors = List.of(
                factor("A", 100, 100, 100, 100),
                factor("B", 60, 80, 70, 90),
                factor("C", null, 60, 80, 70));
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_GROWTH, factors);
        // growth 权重 [0.1,0.4,0.2,0.3]：B=79.00；C=(0.4*60+0.2*80+0.3*70)/0.9=61/0.9=67.78
        assertEquals("A", board.get(0).getSymbol());
        assertEquals("B", board.get(1).getSymbol());
        assertEquals("C", board.get(2).getSymbol());
        assertEquals(0, new BigDecimal("79.00").compareTo(board.get(1).getTotalScore()));
        assertEquals(0, new BigDecimal("67.78").compareTo(board.get(2).getTotalScore()));
    }

    @Test
    void buildBoardExcludesIncompleteFactors() {
        StockFactor incomplete = factor("X", 90, 90, 90, 90);
        incomplete.setDataIncomplete(true);
        List<StockFactor> factors = List.of(incomplete, factor("A", 100, 100, 100, 100));
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_BALANCED, factors);
        assertEquals(1, board.size());
        assertEquals("A", board.get(0).getSymbol());
    }

    @Test
    void buildBoardCapsAtTopTwenty() {
        List<StockFactor> factors = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            factors.add(factor("S" + i, 100 - i, 100 - i, 100 - i, 100 - i));
        }
        List<BoardEntry> board = service.buildBoard(ScreeningService.STYLE_BALANCED, factors);
        assertEquals(20, board.size());
        assertEquals("S0", board.get(0).getSymbol());
        assertEquals("S19", board.get(19).getSymbol());
    }

    @Test
    void buildReasonMatchesDesignTemplate() {
        StockFactor f = factor("A", 100, 100, 100, 100);
        f.setRoePct(85);
        f.setPePct(82);
        f.setMomentumPct(70);
        assertEquals("ROE 池内前 15%，PE 低于 82% 成分股，20 日涨幅池内前 30%",
                ScreeningService.buildReason(f));
    }

    @Test
    void buildReasonSkipsNullParts() {
        StockFactor f = factor("B", 100, 100, 100, 100);
        f.setRoePct(60);
        // pePct、momentumPct 为 null（财务/K线缺失）时省略对应片段
        assertEquals("ROE 池内前 40%", ScreeningService.buildReason(f));
    }

    @Test
    void defaultWeightsMatchDesign() {
        ScreeningProperties props = new ScreeningProperties();
        assertEquals(new BigDecimal("0.40"), props.weightsOf("value").get("valuation"));
        assertEquals(new BigDecimal("0.20"), props.weightsOf("value").get("growth"));
        assertEquals(new BigDecimal("0.30"), props.weightsOf("value").get("quality"));
        assertEquals(new BigDecimal("0.10"), props.weightsOf("value").get("momentum"));
        assertEquals(new BigDecimal("0.40"), props.weightsOf("growth").get("growth"));
        assertEquals(new BigDecimal("0.25"), props.weightsOf("balanced").get("valuation"));
        assertEquals(new BigDecimal("0.30"), props.weightsOf("balanced").get("quality"));
    }
}
