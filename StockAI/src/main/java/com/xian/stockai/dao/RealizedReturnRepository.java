package com.xian.stockai.dao;

import com.xian.stockai.po.RealizedReturn;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RealizedReturnRepository extends MongoRepository<RealizedReturn, String> {
    List<RealizedReturn> findByCreatedAt(LocalDateTime createdAt);

    List<RealizedReturn> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    List<RealizedReturn> findByUserId(String userId);

    RealizedReturn findByUserIdAndCreatedAt(String userId, LocalDateTime createdAt);

    RealizedReturn findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
