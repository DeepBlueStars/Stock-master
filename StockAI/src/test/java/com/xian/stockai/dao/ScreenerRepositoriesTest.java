package com.xian.stockai.dao;

import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFundamentals;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.po.IndexConstituents;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenerRepositoriesTest {

    @Test
    void allRepositoriesExtendMongoRepository() {
        assertTrue(MongoRepository.class.isAssignableFrom(StockFundamentalsRepository.class));
        assertTrue(MongoRepository.class.isAssignableFrom(StockIndustryConceptRepository.class));
        assertTrue(MongoRepository.class.isAssignableFrom(StockDailyKlineRepository.class));
        assertTrue(MongoRepository.class.isAssignableFrom(IndexConstituentsRepository.class));
    }

    @Test
    void stockFundamentalsRepositoryDeclaresFindTopBySymbol() throws NoSuchMethodException {
        Method method = StockFundamentalsRepository.class
                .getMethod("findTopBySymbolOrderByReportDateDesc", String.class);
        assertEquals(StockFundamentals.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test
    void stockIndustryConceptRepositoryDeclaresFindBySymbol() throws NoSuchMethodException {
        Method method = StockIndustryConceptRepository.class.getMethod("findBySymbol", String.class);
        assertEquals(StockIndustryConcept.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void stockDailyKlineRepositoryDeclaresFindTop21AndExists() throws NoSuchMethodException {
        Method findTop21 = StockDailyKlineRepository.class
                .getMethod("findTop21BySymbolOrderByTradeDateDesc", String.class);
        assertEquals(List.class, findTop21.getReturnType());
        assertEquals(1, findTop21.getParameterCount());

        Method exists = StockDailyKlineRepository.class.getMethod("existsByTradeDate", String.class);
        assertEquals(boolean.class, exists.getReturnType());
        assertEquals(1, exists.getParameterCount());
        assertEquals(String.class, exists.getParameterTypes()[0]);
    }
}
