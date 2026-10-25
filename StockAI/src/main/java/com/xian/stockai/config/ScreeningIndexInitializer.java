package com.xian.stockai.config;

import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.ScreeningScoreRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * @author xian
 * @description 启动时确保选股相关集合的 trade_date 唯一索引存在（幂等，失败不阻断启动）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScreeningIndexInitializer implements ApplicationRunner {

    private static final String TRADE_DATE_FIELD = "trade_date";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureUniqueTradeDateIndex(ScreeningScoreRecord.class);
        ensureUniqueTradeDateIndex(ScreeningResult.class);
        ensureUniqueTradeDateIndex(HotspotResult.class);
    }

    private void ensureUniqueTradeDateIndex(Class<?> documentType) {
        try {
            mongoTemplate.indexOps(documentType)
                    .ensureIndex(new Index().on(TRADE_DATE_FIELD, Sort.Direction.ASC).unique());
            log.info("trade_date 唯一索引已确保: {}", documentType.getSimpleName());
        } catch (Exception e) {
            log.error("trade_date 唯一索引创建失败: {} - {}", documentType.getSimpleName(), e.getMessage(), e);
        }
    }
}
