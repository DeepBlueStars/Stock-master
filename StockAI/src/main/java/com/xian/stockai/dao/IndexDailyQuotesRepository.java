package com.xian.stockai.dao;

import com.xian.stockai.po.IndexDailyQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IndexDailyQuotesRepository extends MongoRepository<IndexDailyQuotes, String> {
    List<IndexDailyQuotes> findTop250ByIndexCodeOrderByTradeDateDesc(String indexCode);
}
