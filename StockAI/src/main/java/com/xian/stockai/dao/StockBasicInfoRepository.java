package com.xian.stockai.dao;

import com.xian.stockai.po.StockBasicInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockBasicInfoRepository extends MongoRepository<StockBasicInfo, String> {
    StockBasicInfo findBySymbol(String symbol);
}
