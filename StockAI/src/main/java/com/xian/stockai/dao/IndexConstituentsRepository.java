package com.xian.stockai.dao;

import com.xian.stockai.po.IndexConstituents;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IndexConstituentsRepository extends MongoRepository<IndexConstituents, String> {
}
