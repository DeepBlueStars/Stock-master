package com.xian.stockai.dao;

import com.xian.stockai.po.TotalReturn;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TotalReturnRepository extends MongoRepository<TotalReturn, String> {
    List<TotalReturn> findByCreatedAt(LocalDateTime createdAt);

    List<TotalReturn> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    List<TotalReturn> findByUserId(String userId);

    TotalReturn findByUserIdAndCreatedAt(String userId, LocalDateTime createdAt);

    TotalReturn findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
