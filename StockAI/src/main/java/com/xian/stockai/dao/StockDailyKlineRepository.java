package com.xian.stockai.dao;

import com.xian.stockai.po.StockDailyKline;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockDailyKlineRepository extends MongoRepository<StockDailyKline, String> {
    List<StockDailyKline> findTop21BySymbolOrderByTradeDateDesc(String symbol);

    List<StockDailyKline> findTop21BySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(String symbol,
                                                                                         String tradeDate);

    List<StockDailyKline> findTop250BySymbolOrderByTradeDateDesc(String symbol);

    List<StockDailyKline> findTop250BySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(String symbol,
                                                                                          String tradeDate);

    boolean existsByTradeDate(String tradeDate);

    long countByTradeDate(String tradeDate);
}
