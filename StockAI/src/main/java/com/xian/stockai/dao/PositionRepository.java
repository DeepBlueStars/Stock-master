package com.xian.stockai.dao;

import com.xian.stockai.po.Position;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PositionRepository extends MongoRepository<Position, String> {
    Position findByUserIdAndSymbol(String userId, String symbol);

    List<Position> findByUserId(String userId);

    List<Position> findByTotalQuantityGreaterThan(int i);
}