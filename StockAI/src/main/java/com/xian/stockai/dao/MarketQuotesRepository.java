package com.xian.stockai.dao;

import com.xian.stockai.po.MarketQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MarketQuotesRepository extends MongoRepository<MarketQuotes, String> {
    // 根据code查询
    MarketQuotes findByCode(String code);
}
