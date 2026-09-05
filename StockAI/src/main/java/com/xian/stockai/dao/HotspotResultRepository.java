package com.xian.stockai.dao;

import com.xian.stockai.po.HotspotResult;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HotspotResultRepository extends MongoRepository<HotspotResult, String> {
    HotspotResult findByTradeDate(String tradeDate);

    HotspotResult findTopByOrderByTradeDateDesc();
}
