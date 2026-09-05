package com.xian.stockai.dao;

import com.xian.stockai.po.ScreeningScoreRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScreeningScoreRecordRepository extends MongoRepository<ScreeningScoreRecord, String> {
    ScreeningScoreRecord findByTradeDate(String tradeDate);

    ScreeningScoreRecord findTopByOrderByTradeDateDesc();
}
