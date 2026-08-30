package com.xian.stockai.dao;

import com.xian.stockai.po.IndexDailyQuotes;
import com.xian.stockai.po.IndexValuation;
import com.xian.stockai.po.MarketRegimeRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketRegimeRepositoriesTest {

    @Test
    void indexDailyQuotesRepositoryExtendsMongoRepository() {
        assertTrue(MongoRepository.class.isAssignableFrom(IndexDailyQuotesRepository.class));
    }

    @Test
    void indexDailyQuotesRepositoryDeclaresFindTop250() throws NoSuchMethodException {
        Method method = IndexDailyQuotesRepository.class
                .getMethod("findTop250ByIndexCodeOrderByTradeDateDesc", String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test
    void indexValuationRepositoryDeclaresFindTopByIndexCode() throws NoSuchMethodException {
        Method method = IndexValuationRepository.class
                .getMethod("findTopByIndexCodeOrderByTradeDateDesc", String.class);
        assertEquals(IndexValuation.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test
    void marketRegimeRecordRepositoryDeclaresFindTopByDate() throws NoSuchMethodException {
        Method method = MarketRegimeRecordRepository.class.getMethod("findTopByOrderByDateDesc");
        assertEquals(MarketRegimeRecord.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    void marketRegimeRecordRepositoryDeclaresFindByDate() throws NoSuchMethodException {
        Method method = MarketRegimeRecordRepository.class.getMethod("findByDate", String.class);
        assertEquals(MarketRegimeRecord.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(String.class, method.getParameterTypes()[0]);
    }

    @Test
    void marketRegimeRecordRepositoryDeclaresFindAllByDateDesc() throws NoSuchMethodException {
        Method method = MarketRegimeRecordRepository.class
                .getMethod("findAllByOrderByDateDesc", Pageable.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(Pageable.class, method.getParameterTypes()[0]);
    }
}
