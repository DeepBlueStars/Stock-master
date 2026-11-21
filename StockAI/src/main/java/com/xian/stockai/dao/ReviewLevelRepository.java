package com.xian.stockai.dao;

import com.xian.stockai.po.ReviewLevel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewLevelRepository extends MongoRepository<ReviewLevel, String> {

    List<ReviewLevel> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<ReviewLevel> findByUserIdAndSymbol(String userId, String symbol);

    void deleteByUserIdAndSymbol(String userId, String symbol);
}
