package com.xian.stockai.service;

import com.xian.stockai.dao.IndexDailyQuotesRepository;
import com.xian.stockai.dao.IndexValuationRepository;
import com.xian.stockai.dao.MarketRegimeRecordRepository;
import com.xian.stockai.po.IndexDailyQuotes;
import com.xian.stockai.po.IndexValuation;
import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.po.Notification;
import com.xian.stockai.po.RegimeIndicators;
import com.xian.stockai.service.notify.FeishuNotificationSender;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class MarketRegimeService {

    private static final int MA250_WINDOW = 250;
    private static final int MAX_HISTORY_LIMIT = 200;

    private final IndexDailyQuotesRepository indexDailyQuotesRepository;
    private final IndexValuationRepository indexValuationRepository;
    private final MarketRegimeRecordRepository marketRegimeRecordRepository;
    private final ChatClient chatClient;
    private final ObjectProvider<FeishuNotificationSender> feishuProvider;
    private final String benchmarkIndex;
    private final BigDecimal volatilityHigh;
    private final BigDecimal valuationHigh;

    public MarketRegimeService(
            IndexDailyQuotesRepository indexDailyQuotesRepository,
            IndexValuationRepository indexValuationRepository,
            MarketRegimeRecordRepository marketRegimeRecordRepository,
            ChatClient chatClient,
            ObjectProvider<FeishuNotificationSender> feishuProvider,
            @Value("${market-regime.benchmark-index:sh000300}") String benchmarkIndex,
            @Value("${market-regime.thresholds.volatility-high:0.30}") BigDecimal volatilityHigh,
            @Value("${market-regime.thresholds.valuation-high:0.80}") BigDecimal valuationHigh) {
        this.indexDailyQuotesRepository = indexDailyQuotesRepository;
        this.indexValuationRepository = indexValuationRepository;
        this.marketRegimeRecordRepository = marketRegimeRecordRepository;
        this.chatClient = chatClient;
        this.feishuProvider = feishuProvider;
        this.benchmarkIndex = benchmarkIndex;
        this.volatilityHigh = volatilityHigh;
        this.valuationHigh = valuationHigh;
    }

    public RegimeIndicators calculateIndicators(String indexCode) {
        List<IndexDailyQuotes> recent = indexDailyQuotesRepository
                .findTop250ByIndexCodeOrderByTradeDateDesc(indexCode);
        if (recent == null || recent.size() < MA250_WINDOW) {
            log.warn("基准指数 {} 日线不足 {} 条，无法判定", indexCode, MA250_WINDOW);
            return null;
        }
        List<IndexDailyQuotes> asc = new ArrayList<>(recent);
        Collections.reverse(asc);

        RegimeIndicators ind = new RegimeIndicators();
        ind.setBenchmarkIndex(indexCode);
        ind.setMa20(ma(asc, 20));
        ind.setMa60(ma(asc, 60));
        ind.setMa250(ma(asc, MA250_WINDOW));
        ind.setLatestClose(asc.get(asc.size() - 1).getClose());
        ind.setVolumeRatio(volumeRatio(asc));
        ind.setVolatility(volatility(asc));

        IndexValuation val = indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc(indexCode);
        if (val != null) {
            ind.setPePercentile(val.getPePercentile());
            ind.setPbPercentile(val.getPbPercentile());
        } else {
            ind.setPePercentile(BigDecimal.ZERO);
            ind.setPbPercentile(BigDecimal.ZERO);
        }
        return ind;
    }

    public String buildFallbackReport(String regime, String positionAdvice, RegimeIndicators ind) {
        return String.format(
                "[量化结论] 市场状态：%s；仓位建议：%s。基准指数 %s，MA20=%.2f，量比=%.2f，波动率=%.2f%%，PE分位=%.2f，PB分位=%.2f。",
                regime, positionAdvice, ind.getBenchmarkIndex(), ind.getMa20(),
                ind.getVolumeRatio(), ind.getVolatility().multiply(BigDecimal.valueOf(100)),
                ind.getPePercentile(), ind.getPbPercentile());
    }

    public String generateReport(String regime, String positionAdvice, RegimeIndicators ind) {
        String prompt = String.format(
                "你是市场温度计助手。根据以下量化判定结果，用中文输出一段不超过100字的市场日报，"
                        + "必须包含「结论」「依据」「仓位建议」三部分。\n"
                        + "市场状态：%s\n仓位建议：%s\n"
                        + "基准指数：%s\nMA20=%.2f MA60=%.2f MA250=%.2f 最新收盘=%.2f\n"
                        + "量比(5日/60日)=%.2f 20日年化波动率=%.2f%%\nPE分位=%.2f PB分位=%.2f",
                regime, positionAdvice, ind.getBenchmarkIndex(),
                ind.getMa20(), ind.getMa60(), ind.getMa250(), ind.getLatestClose(),
                ind.getVolumeRatio(), ind.getVolatility().multiply(BigDecimal.valueOf(100)),
                ind.getPePercentile(), ind.getPbPercentile());
        return chatClient.prompt().user(prompt).call().content();
    }

    public Result<MarketRegimeRecord> evaluateAndSave() {
        RegimeIndicators ind = calculateIndicators(benchmarkIndex);
        if (ind == null) {
            return Result.fail("基准指数数据不足，无法判定");
        }
        String regime = RegimeRule.determineRegime(ind, volatilityHigh, valuationHigh);
        String advice = RegimeRule.mapPositionAdvice(regime);

        String report;
        try {
            report = generateReport(regime, advice, ind);
        } catch (Exception e) {
            log.error("AI 总结失败，降级为量化结论: {}", e.getMessage());
            report = buildFallbackReport(regime, advice, ind);
        }

        MarketRegimeRecord record = new MarketRegimeRecord();
        String date = LocalDate.now().toString();
        MarketRegimeRecord existing = marketRegimeRecordRepository.findByDate(date);
        if (existing != null) {
            record.setId(existing.getId());
        }
        record.setDate(date);
        record.setRegime(regime);
        record.setPositionAdvice(advice);
        record.setIndicators(ind);
        record.setReport(report);
        record.setCreatedAt(LocalDateTime.now());
        marketRegimeRecordRepository.save(record);
        pushFeishu(record);
        return Result.success(record);
    }

    private void pushFeishu(MarketRegimeRecord record) {
        try {
            FeishuNotificationSender sender = feishuProvider.getIfAvailable();
            if (sender == null) {
                log.info("飞书未启用，跳过市场温度计日报推送");
                return;
            }
            Notification n = new Notification();
            n.setType("MARKET_REGIME");
            n.setContent(record.getReport());
            n.setRead(false);
            n.setCreatedAt(LocalDateTime.now());
            sender.send(n);
        } catch (Exception e) {
            log.error("飞书日报推送失败，不影响记录生成: {}", e.getMessage());
        }
    }

    public Result<MarketRegimeRecord> latest() {
        return Result.successIfNotNull(marketRegimeRecordRepository.findTopByOrderByDateDesc(), "暂无市场状态记录");
    }

    public Result<List<MarketRegimeRecord>> history(int limit) {
        int capped = Math.min(Math.max(1, limit), MAX_HISTORY_LIMIT);
        return Result.success(marketRegimeRecordRepository
                .findAllByOrderByDateDesc(PageRequest.of(0, capped)));
    }

    private BigDecimal ma(List<IndexDailyQuotes> asc, int window) {
        int n = Math.min(window, asc.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = asc.size() - n; i < asc.size(); i++) {
            sum = sum.add(asc.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal avgVolume(List<IndexDailyQuotes> asc, int window) {
        int n = Math.min(window, asc.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = asc.size() - n; i < asc.size(); i++) {
            sum = sum.add(asc.get(i).getVolume());
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal volumeRatio(List<IndexDailyQuotes> asc) {
        BigDecimal avg5 = avgVolume(asc, 5);
        BigDecimal avg60 = avgVolume(asc, 60);
        if (avg60.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return avg5.divide(avg60, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal volatility(List<IndexDailyQuotes> asc) {
        List<BigDecimal> returns = new ArrayList<>();
        int start = Math.max(1, asc.size() - 20);
        for (int i = start; i < asc.size(); i++) {
            BigDecimal prev = asc.get(i - 1).getClose();
            if (prev.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(asc.get(i).getClose().subtract(prev).divide(prev, 6, RoundingMode.HALF_UP));
        }
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            BigDecimal d = r.subtract(mean);
            variance = variance.add(d.multiply(d));
        }
        variance = variance.divide(BigDecimal.valueOf(returns.size() - 1), 8, RoundingMode.HALF_UP);
        BigDecimal std = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        return std.multiply(BigDecimal.valueOf(Math.sqrt(252))).setScale(4, RoundingMode.HALF_UP);
    }
}
