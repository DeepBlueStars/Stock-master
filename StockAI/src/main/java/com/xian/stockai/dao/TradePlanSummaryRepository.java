package com.xian.stockai.dao;

import com.xian.stockai.po.TradePlanSummary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TradePlanSummaryRepository extends MongoRepository<TradePlanSummary, String> {
    Optional<TradePlanSummary> findTopByUserIdOrderByWeekStartDesc(String userId);

    List<TradePlanSummary> findTop12ByUserIdOrderByWeekStartDesc(String userId);

    List<TradePlanSummary> findByUserIdOrderByWeekStartAsc(String userId);

    Optional<TradePlanSummary> findByUserIdAndWeekStart(String userId, String weekStart);

    Optional<TradePlanSummary> findByUserIdAndWeekStartAndIdNot(String userId, String weekStart, String id);
}
