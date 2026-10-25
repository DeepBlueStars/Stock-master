package com.xian.stockai.task;

import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.service.ScreeningReportService;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScreeningTask {
    private final ScreeningService screeningService;
    private final HotspotService hotspotService;
    private final ScreeningReportService screeningReportService;
    private final MongoTemplate mongoTemplate;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final IndexConstituentsRepository indexConstituentsRepository;

    @Scheduled(cron = "${screening.cron:0 0 18 * * MON-FRI}")
    public void runDaily() {
        log.info("开始选股引擎每日更新");
        runScreening();
        log.info("选股引擎每日更新结束");
    }

    /**
     * 全流程编排：因子快照 → 榜单 → 热点 → AI 报告 → 入库。
     * 任一步骤异常记日志，不阻断已完成步骤产物入库；因子失败则终止。
     */
    public Result<ScreeningResult> runScreening() {
        String tradeDate = LocalDate.now().toString();
        int poolSize = indexConstituentsRepository.findAll().size();
        long coveredCount = stockDailyKlineRepository.countByTradeDate(tradeDate);
        log.info("当日K线覆盖 {}/{}", coveredCount, poolSize);
        if (coveredCount == 0) {
            log.warn("当日 {} 无K线数据，跳过选股任务", tradeDate);
            return Result.fail("当日K线数据未就绪");
        }
        List<StockFactor> factors;
        try {
            factors = screeningService.calculateAndSaveSnapshot(tradeDate);
        } catch (Exception e) {
            log.error("选股因子快照失败: {}", e.getMessage(), e);
            return Result.fail("选股因子计算失败");
        }

        ScreeningResult result = new ScreeningResult();
        result.setTradeDate(tradeDate);
        result.setGeneratedAt(LocalDateTime.now());
        try {
            result.setValueBoard(screeningService.buildBoard(ScreeningService.STYLE_VALUE, factors));
        } catch (Exception e) {
            log.error("风格榜单生成失败({}): {}", ScreeningService.STYLE_VALUE, e.getMessage(), e);
        }
        try {
            result.setGrowthBoard(screeningService.buildBoard(ScreeningService.STYLE_GROWTH, factors));
        } catch (Exception e) {
            log.error("风格榜单生成失败({}): {}", ScreeningService.STYLE_GROWTH, e.getMessage(), e);
        }
        try {
            result.setBalancedBoard(screeningService.buildBoard(ScreeningService.STYLE_BALANCED, factors));
        } catch (Exception e) {
            log.error("风格榜单生成失败({}): {}", ScreeningService.STYLE_BALANCED, e.getMessage(), e);
        }
        HotspotResult hotspot = null;
        try {
            hotspot = hotspotService.buildAndSave(tradeDate);
        } catch (Exception e) {
            log.error("热点识别失败: {}", e.getMessage(), e);
        }
        try {
            screeningReportService.attachReport(result, hotspot, factors);
        } catch (Exception e) {
            log.error("AI综合报告生成失败: {}", e.getMessage(), e);
        }
        try {
            upsertByTradeDate(result, tradeDate);
        } catch (Exception e) {
            log.error("选股结果入库失败: {}", e.getMessage(), e);
            return Result.fail("选股结果入库失败");
        }
        log.info("选股结果已入库: tradeDate={}", tradeDate);
        return Result.success(result);
    }

    private void upsertByTradeDate(ScreeningResult result, String tradeDate) {
        Document document = new Document();
        mongoTemplate.getConverter().write(result, document);
        document.remove("_id");
        Update update = new Update();
        document.forEach((field, value) -> {
            if (value != null) {
                update.set(field, value);
            }
        });
        mongoTemplate.upsert(Query.query(Criteria.where("trade_date").is(tradeDate)), update,
                ScreeningResult.class);
    }
}
