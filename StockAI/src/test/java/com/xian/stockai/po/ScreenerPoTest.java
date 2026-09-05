package com.xian.stockai.po;

import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.HotspotStock;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenerPoTest {

    private static String fieldName(Class<?> clazz, String property) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(property).getAnnotation(Field.class);
        return field == null ? null : field.name();
    }

    @Test
    void stockFundamentalsMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = StockFundamentals.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("stock_fundamentals", doc.collection());
        assertEquals("symbol", fieldName(StockFundamentals.class, "symbol"));
        assertEquals("report_date", fieldName(StockFundamentals.class, "reportDate"));
        assertEquals("pe", fieldName(StockFundamentals.class, "pe"));
        assertEquals("pb", fieldName(StockFundamentals.class, "pb"));
        assertEquals("roe", fieldName(StockFundamentals.class, "roe"));
        assertEquals("revenue_yoy", fieldName(StockFundamentals.class, "revenueYoy"));
        assertEquals("profit_yoy", fieldName(StockFundamentals.class, "profitYoy"));
        assertEquals("gross_margin", fieldName(StockFundamentals.class, "grossMargin"));
    }

    @Test
    void stockFundamentalsExposesGettersAndSetters() {
        StockFundamentals po = new StockFundamentals();
        po.setSymbol("600519");
        po.setReportDate("2026-06-30");
        po.setPe(new BigDecimal("30.5"));
        po.setPb(new BigDecimal("8.2"));
        po.setRoe(new BigDecimal("25.3"));
        po.setRevenueYoy(new BigDecimal("15.1"));
        po.setProfitYoy(new BigDecimal("20.2"));
        po.setGrossMargin(new BigDecimal("91.0"));
        assertEquals("600519", po.getSymbol());
        assertEquals("2026-06-30", po.getReportDate());
        assertEquals(new BigDecimal("30.5"), po.getPe());
        assertEquals(new BigDecimal("8.2"), po.getPb());
        assertEquals(new BigDecimal("25.3"), po.getRoe());
        assertEquals(new BigDecimal("15.1"), po.getRevenueYoy());
        assertEquals(new BigDecimal("20.2"), po.getProfitYoy());
        assertEquals(new BigDecimal("91.0"), po.getGrossMargin());
    }

    @Test
    void stockIndustryConceptMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = StockIndustryConcept.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("stock_industry_concept", doc.collection());
        assertEquals("symbol", fieldName(StockIndustryConcept.class, "symbol"));
        assertEquals("industry", fieldName(StockIndustryConcept.class, "industry"));
        assertEquals("concepts", fieldName(StockIndustryConcept.class, "concepts"));
    }

    @Test
    void stockIndustryConceptExposesGettersAndSetters() {
        StockIndustryConcept po = new StockIndustryConcept();
        po.setSymbol("600519");
        po.setIndustry("白酒");
        po.setConcepts(List.of("消费", "高端制造"));
        assertEquals("600519", po.getSymbol());
        assertEquals("白酒", po.getIndustry());
        assertEquals(2, po.getConcepts().size());
    }

    @Test
    void stockDailyKlineMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = StockDailyKline.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("stock_daily_kline", doc.collection());
        assertEquals("symbol", fieldName(StockDailyKline.class, "symbol"));
        assertEquals("trade_date", fieldName(StockDailyKline.class, "tradeDate"));
        assertEquals("close", fieldName(StockDailyKline.class, "close"));
        assertEquals("amount", fieldName(StockDailyKline.class, "amount"));
    }

    @Test
    void indexConstituentsMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = IndexConstituents.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("index_constituents", doc.collection());
        assertEquals("index_code", fieldName(IndexConstituents.class, "indexCode"));
        assertEquals("index_name", fieldName(IndexConstituents.class, "indexName"));
        assertEquals("symbol", fieldName(IndexConstituents.class, "symbol"));
    }

    @Test
    void screeningScoreRecordMapsCollectionAndFields() throws NoSuchFieldException {
        Document doc = ScreeningScoreRecord.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("screening_scores", doc.collection());
        assertEquals("trade_date", fieldName(ScreeningScoreRecord.class, "tradeDate"));
        assertEquals("generated_at", fieldName(ScreeningScoreRecord.class, "generatedAt"));
        assertEquals("stocks", fieldName(ScreeningScoreRecord.class, "stocks"));
        Indexed indexed = ScreeningScoreRecord.class.getDeclaredField("tradeDate")
                .getAnnotation(Indexed.class);
        assertNotNull(indexed);
        assertTrue(indexed.unique());
    }

    @Test
    void hotspotPosMapCollectionAndFields() throws NoSuchFieldException {
        Document doc = HotspotResult.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("hotspot_results", doc.collection());
        assertEquals("trade_date", fieldName(HotspotResult.class, "tradeDate"));
        assertEquals("generated_at", fieldName(HotspotResult.class, "generatedAt"));
        assertEquals("industries", fieldName(HotspotResult.class, "industries"));
        assertEquals("concepts", fieldName(HotspotResult.class, "concepts"));
        Indexed indexed = HotspotResult.class.getDeclaredField("tradeDate")
                .getAnnotation(Indexed.class);
        assertNotNull(indexed);
        assertTrue(indexed.unique());
        assertEquals("hot_score", fieldName(HotspotDirection.class, "hotScore"));
        assertEquals("daily_avg_change", fieldName(HotspotDirection.class, "dailyAvgChange"));
        assertEquals("daily_amount_ratio", fieldName(HotspotDirection.class, "dailyAmountRatio"));
        assertEquals("limit_up_count", fieldName(HotspotDirection.class, "limitUpCount"));
        assertEquals("five_day_avg_change", fieldName(HotspotDirection.class, "fiveDayAvgChange"));
        assertEquals("five_day_amount_ratio", fieldName(HotspotDirection.class, "fiveDayAmountRatio"));
        assertEquals("five_day_limit_up_count", fieldName(HotspotDirection.class, "fiveDayLimitUpCount"));
        assertEquals("stock_count", fieldName(HotspotDirection.class, "stockCount"));
        assertEquals("hype_label", fieldName(HotspotDirection.class, "hypeLabel"));
        assertEquals("hype_basis", fieldName(HotspotDirection.class, "hypeBasis"));
        assertEquals("top_stocks", fieldName(HotspotDirection.class, "topStocks"));
        assertEquals("daily_change", fieldName(HotspotStock.class, "dailyChange"));
        assertEquals("five_day_change", fieldName(HotspotStock.class, "fiveDayChange"));
    }
}
