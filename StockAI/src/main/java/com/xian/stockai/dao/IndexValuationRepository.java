package com.xian.stockai.dao;

import com.xian.stockai.po.IndexValuation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IndexValuationRepository extends MongoRepository<IndexValuation, String> {
    IndexValuation findTopByIndexCodeOrderByTradeDateDesc(String indexCode);
}
