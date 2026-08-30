package com.xian.stockai.dao;

import com.xian.stockai.po.MarketRegimeRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MarketRegimeRecordRepository extends MongoRepository<MarketRegimeRecord, String> {
    MarketRegimeRecord findTopByOrderByDateDesc();

    List<MarketRegimeRecord> findAllByOrderByDateDesc(Pageable pageable);
}
