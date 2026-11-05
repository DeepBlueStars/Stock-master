package com.xian.stockai.dao;

import com.xian.stockai.po.ExitEvalRun;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ExitEvalRunRepository extends MongoRepository<ExitEvalRun, String> {
    List<ExitEvalRun> findTop20ByOrderByStartedAtDesc();

    List<ExitEvalRun> findTop20ByUserIdOrderByStartedAtDesc(String userId);
}
