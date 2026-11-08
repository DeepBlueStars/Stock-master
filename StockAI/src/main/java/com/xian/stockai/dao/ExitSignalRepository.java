package com.xian.stockai.dao;

import com.xian.stockai.po.ExitSignal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExitSignalRepository extends MongoRepository<ExitSignal, String> {
    List<ExitSignal> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ExitSignal> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

    List<ExitSignal> findTop20ByUserIdAndSymbolAndCodeOrderByCreatedAtDesc(String userId, String symbol, String code);

    List<ExitSignal> findByUserIdAndSymbolAndStatus(String userId, String symbol, String status);

    long countByUserIdAndStatus(String userId, String status);

    /**
     * 某天之后确认过的信号，用于「今天已处理 N 条」
     */
    List<ExitSignal> findByUserIdAndStatusAndAckedAtAfter(String userId, String status, LocalDateTime after);
}
