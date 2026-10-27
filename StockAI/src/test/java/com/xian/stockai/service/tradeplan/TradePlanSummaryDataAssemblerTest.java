package com.xian.stockai.service.tradeplan;

import com.xian.stockai.dao.HotspotResultRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.ScreeningResultRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.dao.StockTradeRepository;
import com.xian.stockai.dao.TradePlanRepository;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.StockTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TradePlanSummaryDataAssemblerTest {

    @Mock
    private TradePlanRepository tradePlanRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private StockTradeRepository stockTradeRepository;
    @Mock
    private StockIndustryConceptRepository industryConceptRepository;
    @Mock
    private HotspotResultRepository hotspotResultRepository;
    @Mock
    private ScreeningResultRepository screeningResultRepository;
    @Mock
    private UsersRepository usersRepository;

    private TradePlanSummaryDataAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new TradePlanSummaryDataAssembler(tradePlanRepository, positionRepository,
                stockTradeRepository, industryConceptRepository, hotspotResultRepository,
                screeningResultRepository, usersRepository);
    }

    private StockTrade trade(String side, int quantity, String price, String actualAmount, int day) {
        StockTrade t = new StockTrade();
        t.setSide(side);
        t.setQuantity(quantity);
        t.setPrice(new BigDecimal(price));
        t.setAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        t.setActualAmount(new BigDecimal(actualAmount));
        t.setCreatedAt(LocalDateTime.of(2026, 10, day, 10, 0));
        t.setSymbol("600519");
        t.setStockName("贵州茅台");
        return t;
    }

    @Test
    void replayComputesRealizedPnlForClearedPosition() {
        List<StockTrade> trades = new ArrayList<>();
        trades.add(trade("BUY", 100, "10.00", "1000.00", 5));
        trades.add(trade("BUY", 100, "12.00", "1200.00", 6));
        trades.add(trade("SELL", 200, "15.00", "3000.00", 9));

        TradePlanSummaryDataAssembler.ClearedPosition cleared =
                assembler.replayClearedPosition("600519", "贵州茅台", trades);

        assertEquals(0, new BigDecimal("11.00").compareTo(cleared.costBasis()));
        assertEquals(0, new BigDecimal("800.00").compareTo(cleared.realizedPnl()));
        assertEquals(0, new BigDecimal("200").compareTo(cleared.sellQuantity()));
        assertTrue(!cleared.dataMissing());
    }

    @Test
    void replayMarksMissingCostWhenHistoryIncomplete() {
        List<StockTrade> trades = new ArrayList<>();
        trades.add(trade("SELL", 100, "15.00", "1492.50", 9));

        TradePlanSummaryDataAssembler.ClearedPosition cleared =
                assembler.replayClearedPosition("600519", "贵州茅台", trades);

        assertTrue(cleared.dataMissing());
        assertNull(cleared.costBasis());
        assertNull(cleared.realizedPnl());
    }

    @Test
    void industryDistributionSortsByCostRatioAndCountsUnclassified() {
        List<Position> positions = new ArrayList<>();
        positions.add(position("600519", "1000.00"));
        positions.add(position("000858", "1000.00"));
        positions.add(position("300750", "500.00"));
        Map<String, String> industryBySymbol = new LinkedHashMap<>();
        industryBySymbol.put("600519", "白酒");
        industryBySymbol.put("000858", "白酒");

        List<TradePlanSummaryDataAssembler.IndustryRow> rows =
                assembler.industryDistribution(positions, industryBySymbol);

        assertEquals("白酒", rows.get(0).industry());
        assertEquals(2, rows.get(0).symbolCount());
        assertEquals(0, new BigDecimal("0.8000").compareTo(rows.get(0).costRatio()));
        assertEquals("未分类", rows.get(1).industry());
        assertEquals(1, rows.get(1).symbolCount());
    }

    private Position position(String symbol, String totalCost) {
        Position p = new Position();
        p.setUserId("u1");
        p.setSymbol(symbol);
        p.setTotalQuantity(100);
        p.setAvgCostPrice(new BigDecimal("10.00"));
        p.setTotalCost(new BigDecimal(totalCost));
        return p;
    }
}
