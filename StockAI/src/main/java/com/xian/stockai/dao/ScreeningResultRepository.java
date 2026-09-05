package com.xian.stockai.dao;

import com.xian.stockai.po.ScreeningResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ScreeningResultRepository extends MongoRepository<ScreeningResult, String> {
    ScreeningResult findTopByOrderByTradeDateDesc();

    ScreeningResult findByTradeDate(String tradeDate);

    List<ScreeningResult> findAllByOrderByTradeDateDesc(Pageable pageable);
}
