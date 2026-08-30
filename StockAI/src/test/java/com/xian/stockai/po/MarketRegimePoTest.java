package com.xian.stockai.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MarketRegimePoTest {

    private static String fieldName(Class<?> clazz, String property) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(property).getAnnotation(Field.class);
        return field == null ? null : field.name();
    }

    @Test
    void indexDailyQuotesMapsCollection() {
        Document doc = IndexDailyQuotes.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("index_daily_quotes", doc.collection());
    }

    @Test
    void indexDailyQuotesMapsFields() throws NoSuchFieldException {
        assertEquals("index_code", fieldName(IndexDailyQuotes.class, "indexCode"));
        assertEquals("trade_date", fieldName(IndexDailyQuotes.class, "tradeDate"));
        assertEquals("open", fieldName(IndexDailyQuotes.class, "open"));
        assertEquals("high", fieldName(IndexDailyQuotes.class, "high"));
        assertEquals("low", fieldName(IndexDailyQuotes.class, "low"));
        assertEquals("close", fieldName(IndexDailyQuotes.class, "close"));
        assertEquals("volume", fieldName(IndexDailyQuotes.class, "volume"));
        assertEquals("amount", fieldName(IndexDailyQuotes.class, "amount"));
        assertEquals("data_source", fieldName(IndexDailyQuotes.class, "dataSource"));
    }

    @Test
    void indexDailyQuotesExposesGettersAndSetters() {
        IndexDailyQuotes po = new IndexDailyQuotes();
        po.setIndexCode("000300");
        po.setTradeDate("2026-08-29");
        po.setClose(new BigDecimal("4000.12"));
        po.setVolume(new BigDecimal("123456789"));

        assertEquals("000300", po.getIndexCode());
        assertEquals("2026-08-29", po.getTradeDate());
        assertEquals(new BigDecimal("4000.12"), po.getClose());
        assertEquals(new BigDecimal("123456789"), po.getVolume());
    }

    @Test
    void indexValuationMapsCollection() {
        Document doc = IndexValuation.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("index_valuation", doc.collection());
    }

    @Test
    void indexValuationMapsFields() throws NoSuchFieldException {
        assertEquals("index_code", fieldName(IndexValuation.class, "indexCode"));
        assertEquals("trade_date", fieldName(IndexValuation.class, "tradeDate"));
        assertEquals("pe", fieldName(IndexValuation.class, "pe"));
        assertEquals("pb", fieldName(IndexValuation.class, "pb"));
        assertEquals("pe_percentile", fieldName(IndexValuation.class, "pePercentile"));
        assertEquals("pb_percentile", fieldName(IndexValuation.class, "pbPercentile"));
        assertEquals("data_source", fieldName(IndexValuation.class, "dataSource"));
    }

    @Test
    void indexValuationExposesGettersAndSetters() {
        IndexValuation po = new IndexValuation();
        po.setIndexCode("000300");
        po.setPePercentile(new BigDecimal("0.55"));
        po.setPbPercentile(new BigDecimal("0.40"));

        assertEquals("000300", po.getIndexCode());
        assertEquals(new BigDecimal("0.55"), po.getPePercentile());
        assertEquals(new BigDecimal("0.40"), po.getPbPercentile());
    }

    @Test
    void regimeIndicatorsExposesGettersAndSetters() {
        RegimeIndicators indicators = new RegimeIndicators();
        indicators.setMa20(new BigDecimal("3900.00"));
        indicators.setMa60(new BigDecimal("3800.00"));
        indicators.setMa250(new BigDecimal("3600.00"));
        indicators.setVolumeRatio(new BigDecimal("1.25"));
        indicators.setVolatility(new BigDecimal("0.15"));
        indicators.setLatestClose(new BigDecimal("4000.12"));
        indicators.setPePercentile(new BigDecimal("0.55"));
        indicators.setPbPercentile(new BigDecimal("0.40"));
        indicators.setBenchmarkIndex("000300");

        assertEquals(new BigDecimal("3900.00"), indicators.getMa20());
        assertEquals(new BigDecimal("3800.00"), indicators.getMa60());
        assertEquals(new BigDecimal("3600.00"), indicators.getMa250());
        assertEquals(new BigDecimal("1.25"), indicators.getVolumeRatio());
        assertEquals(new BigDecimal("0.15"), indicators.getVolatility());
        assertEquals(new BigDecimal("4000.12"), indicators.getLatestClose());
        assertEquals(new BigDecimal("0.55"), indicators.getPePercentile());
        assertEquals(new BigDecimal("0.40"), indicators.getPbPercentile());
        assertEquals("000300", indicators.getBenchmarkIndex());
    }

    @Test
    void marketRegimeRecordMapsCollection() {
        Document doc = MarketRegimeRecord.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("market_regime_records", doc.collection());
    }

    @Test
    void marketRegimeRecordMapsFields() throws NoSuchFieldException {
        assertEquals("date", fieldName(MarketRegimeRecord.class, "date"));
        assertEquals("regime", fieldName(MarketRegimeRecord.class, "regime"));
        assertEquals("position_advice", fieldName(MarketRegimeRecord.class, "positionAdvice"));
        assertEquals("indicators", fieldName(MarketRegimeRecord.class, "indicators"));
        assertEquals("report", fieldName(MarketRegimeRecord.class, "report"));
        assertEquals("created_at", fieldName(MarketRegimeRecord.class, "createdAt"));
    }

    @Test
    void marketRegimeRecordDateHasUniqueIndex() throws NoSuchFieldException {
        Indexed indexed = MarketRegimeRecord.class.getDeclaredField("date").getAnnotation(Indexed.class);
        assertNotNull(indexed);
        assertTrue(indexed.unique());
    }

    @Test
    void marketRegimeRecordCreatedAtFormatsDate() throws NoSuchFieldException {
        JSONField jsonField = MarketRegimeRecord.class.getDeclaredField("createdAt").getAnnotation(JSONField.class);
        JsonFormat jsonFormat = MarketRegimeRecord.class.getDeclaredField("createdAt").getAnnotation(JsonFormat.class);

        assertNotNull(jsonField);
        assertEquals("yyyy-MM-dd HH:mm:ss", jsonField.format());
        assertNotNull(jsonFormat);
        assertEquals("yyyy-MM-dd HH:mm:ss", jsonFormat.pattern());
    }

    @Test
    void marketRegimeRecordExposesGettersAndSetters() {
        RegimeIndicators indicators = new RegimeIndicators();
        indicators.setBenchmarkIndex("000300");
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 30, 15, 30, 0);

        MarketRegimeRecord record = new MarketRegimeRecord();
        record.setDate("2026-08-30");
        record.setRegime("BULL");
        record.setPositionAdvice("AGGRESSIVE");
        record.setIndicators(indicators);
        record.setReport("市场处于上行趋势");
        record.setCreatedAt(createdAt);

        assertEquals("2026-08-30", record.getDate());
        assertEquals("BULL", record.getRegime());
        assertEquals("AGGRESSIVE", record.getPositionAdvice());
        assertSame(indicators, record.getIndicators());
        assertEquals("市场处于上行趋势", record.getReport());
        assertEquals(createdAt, record.getCreatedAt());
    }
}
