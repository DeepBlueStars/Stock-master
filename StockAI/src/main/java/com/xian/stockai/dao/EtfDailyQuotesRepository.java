package com.xian.stockai.dao;

import com.xian.stockai.po.EtfDailyQuotes;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EtfDailyQuotesRepository extends MongoRepository<EtfDailyQuotes, String> {
    // 根据symbol查询最新记录（按更新时间，兼容历史数据）
    EtfDailyQuotes findTopBySymbolOrderByUpdatedAtDesc(String symbol);

    /**
     * 取最新一条日线。
     * <p>
     * 不能只按 updated_at 排：回填任务一次写入整段历史，同一次写入的所有行 updated_at 完全相同，
     * 排序退化成随机，可能拿到中间某一天的旧价格。先按 trade_date（"yyyy-MM-dd"，字典序即时间序）
     * 再按 updated_at 兜底。
     * <p>
     * 注意必须用 findTop 前缀（LIMIT 1）。用 @Query 只给 sort 不给 limit 时，Spring Data 会拿
     * 全部匹配结果去调 oneValue()，多条就抛 IncorrectResultSizeDataAccessException。
     */
    EtfDailyQuotes findTopBySymbolOrderByTradeDateDescUpdatedAtDesc(String symbol);

    // 按交易日取最新一条（trade_date 为 "yyyy-MM-dd" 字符串，字典序即时间序）
    EtfDailyQuotes findTopBySymbolOrderByTradeDateDesc(String symbol);

    // 取最近 N 个交易日的日线，按交易日倒序
    List<EtfDailyQuotes> findTop250BySymbolOrderByTradeDateDesc(String symbol);
}
