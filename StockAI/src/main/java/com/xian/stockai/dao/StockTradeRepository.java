package com.xian.stockai.dao;

import com.xian.stockai.po.StockTrade;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockTradeRepository extends MongoRepository<StockTrade, String> {
    List<StockTrade> findByUserId(String userId);
    
    List<StockTrade> findByUserIdAndSymbol(String userId, String symbol);

    Integer countByUserId(String userId);
}