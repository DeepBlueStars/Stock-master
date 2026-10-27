package com.xian.stockai.po;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradePlanPoTest {

    @Test
    void planMapsToTradePlansCollection() {
        Document doc = TradePlan.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("trade_plans", doc.collection());
    }

    @Test
    void summaryMapsToExpectedCollection() {
        Document doc = TradePlanSummary.class.getAnnotation(Document.class);
        assertNotNull(doc);
        assertEquals("trade_plan_summary", doc.collection());
    }

    @Test
    void planExposesCoreFields() {
        TradePlan plan = new TradePlan();
        plan.setUserId("u1");
        plan.setSymbol("600519");
        plan.setStockName("贵州茅台");
        plan.setQuantity(100);
        plan.setAvgCost(new java.math.BigDecimal("10.00"));
        plan.setLastClose(new java.math.BigDecimal("12.00"));
        plan.setBuyMode("BATCHED");

        assertEquals("u1", plan.getUserId());
        assertEquals("600519", plan.getSymbol());
        assertEquals("贵州茅台", plan.getStockName());
        assertEquals(100, plan.getQuantity());
        assertEquals(0, new java.math.BigDecimal("10.00").compareTo(plan.getAvgCost()));
        assertEquals(0, new java.math.BigDecimal("12.00").compareTo(plan.getLastClose()));
        assertEquals("BATCHED", plan.getBuyMode());
    }
}
