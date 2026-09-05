package com.xian.stockai.dao;

import com.xian.stockai.po.StockFundamentals;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockFundamentalsRepository extends MongoRepository<StockFundamentals, String> {
    StockFundamentals findTopBySymbolOrderByReportDateDesc(String symbol);
}
