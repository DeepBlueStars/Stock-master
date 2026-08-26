package com.xian.stockai.dao;

import com.xian.stockai.po.EtfBasicInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EtfBasicInfoRepository extends MongoRepository<EtfBasicInfo, String> {
    EtfBasicInfo findBySymbol(String symbol);
}