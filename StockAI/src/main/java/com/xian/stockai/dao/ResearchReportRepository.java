package com.xian.stockai.dao;

import com.xian.stockai.po.ResearchReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ResearchReportRepository extends MongoRepository<ResearchReport, String> {

    List<ResearchReport> findByUserIdOrderByReportDateDesc(String userId);

    List<ResearchReport> findByUserIdAndTypeOrderByReportDateDesc(String userId, String type);

    List<ResearchReport> deleteByUserId(String userId);
}
