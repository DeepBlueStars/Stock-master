package com.xian.stockai.dao;

import com.xian.stockai.po.StockDailyKline;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockDailyKlineRepository extends MongoRepository<StockDailyKline, String> {
    List<StockDailyKline> findTop21BySymbolOrderByTradeDateDesc(String symbol);

    boolean existsByTradeDate(String tradeDate);
}
