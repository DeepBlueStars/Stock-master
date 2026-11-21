package com.xian.stockai.dao;

import com.xian.stockai.po.ResearchReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface ResearchReportRepository extends MongoRepository<ResearchReport, String> {

    List<ResearchReport> findByUserIdOrderByReportDateDesc(String userId);

    List<ResearchReport> findByUserIdAndTypeOrderByReportDateDesc(String userId, String type);

    /**
     * 本周是否已经有外部写入的组合报告，用来决定系统周报要不要生成
     */
    boolean existsByUserIdAndTypeAndReportDateGreaterThanEqual(String userId, String type, LocalDate date);

    List<ResearchReport> deleteByUserId(String userId);
}
