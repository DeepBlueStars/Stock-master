package com.xian.stockai.dao;

import com.xian.stockai.po.StockIndustryConcept;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockIndustryConceptRepository extends MongoRepository<StockIndustryConcept, String> {
    StockIndustryConcept findBySymbol(String symbol);
}
