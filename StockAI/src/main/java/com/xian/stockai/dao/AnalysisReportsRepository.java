package com.xian.stockai.dao;

import com.xian.stockai.po.AnalysisReports;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface AnalysisReportsRepository extends MongoRepository<AnalysisReports, String> {
    @Query(value = "{ 'stock_symbol': ?0, 'status': 'completed' }", sort = "{ 'createdAt': -1 }")
    List<AnalysisReports> findBySymbolAndStatusCompletedOrderByCreatedAtDesc(String symbol);
}
