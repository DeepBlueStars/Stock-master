package com.xian.stockai.dao;

import com.xian.stockai.po.TradePlan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TradePlanRepository extends MongoRepository<TradePlan, String> {
    Optional<TradePlan> findByUserIdAndSymbol(String userId, String symbol);

    List<TradePlan> findByUserId(String userId);

    void deleteByUserIdAndSymbol(String userId, String symbol);
}
