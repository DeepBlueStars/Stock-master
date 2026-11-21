package com.xian.stockai.dao;

import com.xian.stockai.po.MonitorEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonitorEventRepository extends MongoRepository<MonitorEvent, String> {

    List<MonitorEvent> findByUserIdOrderByEventDateAsc(String userId);

    List<MonitorEvent> findByUserIdAndEventDateBetweenOrderByEventDateAsc(String userId, LocalDate from, LocalDate to);

    List<MonitorEvent> findByUserIdAndStatusAndEventDateBetweenOrderByEventDateAsc(
            String userId, String status, LocalDate from, LocalDate to);

    List<MonitorEvent> findByReportId(String reportId);

    void deleteByReportId(String reportId);

    /**
     * 覆盖个股监测项时只删待处理的，已完成的事件留作复盘
     */
    void deleteByUserIdAndSymbolAndCategoryAndStatus(String userId, String symbol, String category, String status);
}
