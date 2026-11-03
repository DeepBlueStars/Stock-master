package com.xian.stockai.dao;

import com.xian.stockai.po.EtfDailyQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EtfDailyQuotesRepository extends MongoRepository<EtfDailyQuotes, String> {
    // 根据symbol查询最新记录（按更新时间，兼容历史数据）
    EtfDailyQuotes findTopBySymbolOrderByUpdatedAtDesc(String symbol);

    // 按交易日取最新一条（trade_date 为 "yyyy-MM-dd" 字符串，字典序即时间序）
    EtfDailyQuotes findTopBySymbolOrderByTradeDateDesc(String symbol);

    // 取最近 N 个交易日的日线，按交易日倒序
    List<EtfDailyQuotes> findTop250BySymbolOrderByTradeDateDesc(String symbol);
}
