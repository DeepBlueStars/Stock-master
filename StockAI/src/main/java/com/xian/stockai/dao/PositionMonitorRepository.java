package com.xian.stockai.dao;

import com.xian.stockai.po.PositionMonitor;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PositionMonitorRepository extends MongoRepository<PositionMonitor, String> {
    PositionMonitor findByUserIdAndSymbol(String userId, String symbol);

    List<PositionMonitor> findByUserId(String userId);

    List<PositionMonitor> findByEnabledTrue();
}