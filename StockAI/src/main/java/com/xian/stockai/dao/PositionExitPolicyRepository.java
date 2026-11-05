package com.xian.stockai.dao;

import com.xian.stockai.po.PositionExitPolicy;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PositionExitPolicyRepository extends MongoRepository<PositionExitPolicy, String> {
    Optional<PositionExitPolicy> findByUserIdAndSymbol(String userId, String symbol);

    List<PositionExitPolicy> findByUserId(String userId);

    List<PositionExitPolicy> findByEnabledTrue();
}
