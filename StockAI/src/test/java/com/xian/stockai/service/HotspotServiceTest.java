package com.xian.stockai.service;

import com.xian.stockai.dao.HotspotResultRepository;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.ScreeningScoreRecordRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.ScreeningScoreRecord;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockIndustryConcept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotspotServiceTest {

    @Mock
    private IndexConstituentsRepository indexConstituentsRepository;
    @Mock
    private StockIndustryConceptRepository stockIndustryConceptRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private ScreeningScoreRecordRepository screeningScoreRecordRepository;
    @Mock
    private HotspotResultRepository hotspotResultRepository;

    private HotspotService service;

    @BeforeEach
    void setUp() {
        service = new HotspotService(indexConstituentsRepository, stockIndustryConceptRepository,
                stockDailyKlineRepository, screeningScoreRecordRepository, hotspotResultRepository);
    }

    /** 6 条降序 K 线：最新 close=latest，其余 close=base，amount 恒定，最新一根为 2026-09-01。 */
    private List<StockDailyKline> flatKlines(String symbol, String base, String latest, String amount) {
        return flatKlinesFrom(symbol, "2026-09-01", base, latest, amount);
    }

    /** 6 条降序 K 线，最新一根的 tradeDate 为 latestTradeDate。 */
    private List<StockDailyKline> flatKlinesFrom(String symbol, String latestTradeDate,
                                                 String base, String latest, String amount) {
        LocalDate start = LocalDate.parse(latestTradeDate);
        List<StockDailyKline> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            StockDailyKline k = new StockDailyKline();
            k.setSymbol(symbol);
            k.setTradeDate(start.minusDays(i).toString());
            k.setClose(i == 0 ? new BigDecimal(latest) : new BigDecimal(base));
            k.setAmount(new BigDecimal(amount));
            list.add(k);
        }
        return list;
    }

    /** 以评分日 2026-09-01 为上界查询 K 线的 stub。 */
    private void stubKlines(String symbol, List<StockDailyKline> klines) {
        when(stockDailyKlineRepository
                .findTop21BySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(symbol, "2026-09-01"))
                .thenReturn(klines);
    }

    private StockIndustryConcept industry(String symbol, String industry, List<String> concepts) {
        StockIndustryConcept ic = new StockIndustryConcept();
        ic.setSymbol(symbol);
        ic.setIndustry(industry);
        ic.setConcepts(concepts);
        return ic;
    }

    private StockFactor factor(String symbol, Integer quality) {
        StockFactor f = new StockFactor();
        f.setSymbol(symbol);
        f.setName(symbol + "名称");
        f.setQuality(quality);
        return f;
    }

    private void mockPool() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of(
                constituent("A"), constituent("B"), constituent("C"),
                constituent("D"), constituent("E"),
                constituent("F"), constituent("G"), constituent("H")));
        when(stockIndustryConceptRepository.findBySymbol("A"))
                .thenReturn(industry("A", "白酒", List.of("消费")));
        when(stockIndustryConceptRepository.findBySymbol("B"))
                .thenReturn(industry("B", "白酒", List.of("消费")));
        when(stockIndustryConceptRepository.findBySymbol("C"))
                .thenReturn(industry("C", "白酒", List.of("消费")));
        when(stockIndustryConceptRepository.findBySymbol("D"))
                .thenReturn(industry("D", "银行", List.of("科技")));
        when(stockIndustryConceptRepository.findBySymbol("E"))
                .thenReturn(industry("E", "银行", List.of("科技")));
        when(stockIndustryConceptRepository.findBySymbol("F"))
                .thenReturn(industry("F", "医药", List.of()));
        when(stockIndustryConceptRepository.findBySymbol("G"))
                .thenReturn(industry("G", "医药", List.of()));
        when(stockIndustryConceptRepository.findBySymbol("H"))
                .thenReturn(industry("H", "医药", List.of()));
        // 成交额：A=100000 B=50000 C=30000 D=20000 E=10000 F=150000 G=60000 H=40000（全池 460000）
        stubKlines("A", flatKlines("A", "10", "11", "100000"));   // 涨停 0.1
        stubKlines("B", flatKlines("B", "10", "10.5", "50000"));
        stubKlines("C", flatKlines("C", "10", "10.2", "30000"));
        stubKlines("D", flatKlines("D", "10", "10.1", "20000"));
        stubKlines("E", flatKlines("E", "10", "9.9", "10000"));
        stubKlines("F", flatKlines("F", "10", "12", "150000"));   // 涨停 0.2
        stubKlines("G", flatKlines("G", "10", "10.5", "60000"));
        stubKlines("H", flatKlines("H", "10", "10.1", "40000"));

        ScreeningScoreRecord snapshot = new ScreeningScoreRecord();
        snapshot.setTradeDate("2026-09-01");
        snapshot.setStocks(List.of(
                factor("A", 50), factor("B", 70), factor("C", 90),
                factor("D", 60), factor("E", 30),
                factor("F", 30), factor("G", 35), factor("H", 38)));
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(snapshot);
    }

    private IndexConstituents constituent(String symbol) {
        IndexConstituents c = new IndexConstituents();
        c.setSymbol(symbol);
        return c;
    }

    @Test
    void buildAndSaveAggregatesIndustryAndFiltersSmallDirections() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        assertNotNull(result);
        // 行业维度：白酒(A,B,C)、医药(F,G,H) 保留；银行(D,E) 仅 2 只 → 过滤
        assertEquals(2, result.getIndustries().size());
        // 概念维度：消费(A,B,C) 保留；科技(D,E) 仅 2 只 → 过滤
        assertEquals(1, result.getConcepts().size());
        verify(hotspotResultRepository).save(result);
    }

    @Test
    void buildAndSaveComputesDirectionMetrics() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        HotspotDirection medicine = result.getIndustries().stream()
                .filter(d -> "医药".equals(d.getName())).findFirst().orElseThrow();
        // 医药：dailyAvgChange = (0.2+0.05+0.01)/3 = 0.086667
        assertEquals(0, new BigDecimal("0.086667").compareTo(medicine.getDailyAvgChange()));
        // 医药成交额占比 = 250000/460000 = 0.543478
        assertEquals(0, new BigDecimal("0.543478").compareTo(medicine.getDailyAmountRatio()));
        assertEquals(1, medicine.getLimitUpCount()); // F 涨停
        assertEquals(3, medicine.getStockCount());
        assertEquals(1, medicine.getFiveDayLimitUpCount()); // F 5日内有涨停
    }

    @Test
    void buildAndSaveRanksByHotScore() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        // 行业维度 2 个方向：医药各指标均高于白酒 → 医药热度 80、白酒 0
        HotspotDirection top = result.getIndustries().get(0);
        assertEquals("医药", top.getName());
        assertEquals(80, top.getHotScore());
        assertEquals(0, result.getIndustries().get(1).getHotScore());
    }

    @Test
    void buildAndSavePicksRepresentativeStocks() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        HotspotDirection medicine = result.getIndustries().stream()
                .filter(d -> "医药".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(3, medicine.getTopStocks().size());
        // 按 单日涨幅+5日涨幅 降序：F(0.4) > G(0.1) > H(0.02)
        assertEquals("F", medicine.getTopStocks().get(0).getSymbol());
        assertEquals("G", medicine.getTopStocks().get(1).getSymbol());
        assertEquals("H", medicine.getTopStocks().get(2).getSymbol());
    }

    @Test
    void buildAndSaveUpsertsByTradeDate() {
        mockPool();
        HotspotResult existing = new HotspotResult();
        existing.setId("existing-id");
        when(hotspotResultRepository.findByTradeDate("2026-09-01")).thenReturn(existing);
        HotspotResult result = service.buildAndSave("2026-09-01");
        ArgumentCaptor<HotspotResult> captor = ArgumentCaptor.forClass(HotspotResult.class);
        verify(hotspotResultRepository).save(captor.capture());
        assertEquals("existing-id", captor.getValue().getId());
        assertEquals("2026-09-01", result.getTradeDate());
    }

    @Test
    void buildAndSaveReturnsNullWhenSnapshotMissing() {
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(null);
        assertNull(service.buildAndSave("2026-09-01"));
    }

    // ==== Task 6.2: 最新 K 线须为评分日，否则跳过 ====

    @Test
    void buildSkipsStocksWhoseLatestKlineIsOlderThanTradeDate() {
        when(indexConstituentsRepository.findAll()).thenReturn(List.of(
                constituent("A"), constituent("B"), constituent("C"), constituent("D")));
        when(stockIndustryConceptRepository.findBySymbol(anyString()))
                .thenReturn(industry(null, "白酒", List.of()));
        stubKlines("A", flatKlines("A", "10", "11", "100000"));     // +0.1
        stubKlines("B", flatKlines("B", "10", "10.5", "50000"));    // +0.05
        stubKlines("C", flatKlines("C", "10", "10.2", "30000"));    // +0.02
        // D 最新 K 线早于评分日，若未跳过会把 +0.2 当成当日涨幅
        stubKlines("D", flatKlinesFrom("D", "2026-08-31", "10", "12", "20000"));
        ScreeningScoreRecord snapshot = new ScreeningScoreRecord();
        snapshot.setTradeDate("2026-09-01");
        snapshot.setStocks(List.of(factor("A", 50), factor("B", 50),
                factor("C", 50), factor("D", 50)));
        when(screeningScoreRecordRepository.findByTradeDate("2026-09-01")).thenReturn(snapshot);

        HotspotResult result = service.build("2026-09-01");

        assertEquals(1, result.getIndustries().size());
        HotspotDirection liquor = result.getIndustries().get(0);
        assertEquals(3, liquor.getStockCount());
        // 仅 A/B/C 参与：(0.1 + 0.05 + 0.02) / 3
        assertEquals(0, new BigDecimal("0.056667").compareTo(liquor.getDailyAvgChange()));
        assertTrue(liquor.getTopStocks().stream().noneMatch(s -> "D".equals(s.getSymbol())));
    }

    // ==== Task 6: 炒作风险标注 ====

    @Test
    void hypeLabelRules() {
        // 中位数 ≥60 → SUPPORTED
        assertEquals(HotspotService.HYPE_SUPPORTED, HotspotService.hypeLabel(List.of(50, 70, 90), 80));
        // 中位数 <40 且热度 ≥60 → HYPE_RISK
        assertEquals(HotspotService.HYPE_RISK, HotspotService.hypeLabel(List.of(30, 35, 38), 80));
        // 中位数 <40 但热度 <60 → NEUTRAL
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(30, 35, 38), 50));
        // 中位数 40~60 → NEUTRAL；恰为 40 → NEUTRAL（<40 不成立）
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(45, 55), 70));
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(40), 80));
        // 空数据 → NEUTRAL；恰为 60 → SUPPORTED（≥60 边界）
        assertEquals(HotspotService.HYPE_NEUTRAL, HotspotService.hypeLabel(List.of(), 80));
        assertEquals(HotspotService.HYPE_SUPPORTED, HotspotService.hypeLabel(List.of(60), 80));
        // null 质量分排除：[30, null, 35] → 有效 [30,35] 中位数 32.5 → HYPE_RISK（热度 80）
        assertEquals(HotspotService.HYPE_RISK,
                HotspotService.hypeLabel(java.util.Arrays.asList(30, null, 35), 80));
    }

    @Test
    void medianQualityHandlesOddAndEven() {
        assertEquals(0, new BigDecimal("35").compareTo(HotspotService.medianQuality(List.of(30, 35, 38))));
        assertEquals(0, new BigDecimal("32.5").compareTo(HotspotService.medianQuality(List.of(30, 35))));
        assertNull(HotspotService.medianQuality(List.of()));
        assertNull(HotspotService.medianQuality(java.util.Arrays.asList((Integer) null)));
    }

    @Test
    void hypeBasisTextIncludesMedianAndHotScore() {
        String basis = HotspotService.buildHypeBasis(new BigDecimal("32.5"), 80);
        assertEquals("质量中位数 32.5，热度得分 80", basis);
    }

    @Test
    void buildAnnotatesDirections() {
        mockPool();
        HotspotResult result = service.buildAndSave("2026-09-01");
        // 白酒：质量 [50,70,90] 中位数 70 ≥60 → SUPPORTED
        HotspotDirection liquor = result.getIndustries().stream()
                .filter(d -> "白酒".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(HotspotService.HYPE_SUPPORTED, liquor.getHypeLabel());
        assertEquals("质量中位数 70，热度得分 0", liquor.getHypeBasis());
        // 医药：质量 [30,35,38] 中位数 35 <40 且热度 80 ≥60 → HYPE_RISK
        HotspotDirection medicine = result.getIndustries().stream()
                .filter(d -> "医药".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(HotspotService.HYPE_RISK, medicine.getHypeLabel());
        assertEquals("质量中位数 35，热度得分 80", medicine.getHypeBasis());
        // 概念"消费"（A,B,C）同白酒 → SUPPORTED
        assertEquals(HotspotService.HYPE_SUPPORTED, result.getConcepts().get(0).getHypeLabel());
    }
}
