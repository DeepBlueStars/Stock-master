package com.xian.stockai.dao;

import com.xian.stockai.po.EtfDailyQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EtfDailyQuotesRepository extends MongoRepository<EtfDailyQuotes, String> {
    // 根据symbol查询最新记录
    EtfDailyQuotes findTopBySymbolOrderByUpdatedAtDesc(String symbol);
}
