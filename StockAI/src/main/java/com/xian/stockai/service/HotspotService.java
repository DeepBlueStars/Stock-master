package com.xian.stockai.service;

import com.xian.stockai.dao.HotspotResultRepository;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.ScreeningScoreRecordRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.HotspotStock;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.ScreeningScoreRecord;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class HotspotService {

    private static final int MIN_DIRECTION_SIZE = 3;
    private static final int TOP_N = 10;
    private static final int REPRESENTATIVE_COUNT = 3;
    private static final int FIVE_DAY_WINDOW = 5;
    private static final BigDecimal LIMIT_UP_THRESHOLD = new BigDecimal("0.099");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal CHANGE_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal AMOUNT_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal LIMIT_UP_WEIGHT = new BigDecimal("0.2");
    private static final BigDecimal FIVE_DAY_HEAT_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal DAILY_HEAT_WEIGHT = new BigDecimal("0.4");

    public static final String HYPE_SUPPORTED = "SUPPORTED";
    public static final String HYPE_NEUTRAL = "NEUTRAL";
    public static final String HYPE_RISK = "HYPE_RISK";

    private final IndexConstituentsRepository indexConstituentsRepository;
    private final StockIndustryConceptRepository stockIndustryConceptRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final ScreeningScoreRecordRepository screeningScoreRecordRepository;
    private final HotspotResultRepository hotspotResultRepository;
    private final MongoTemplate mongoTemplate;

    /** 成分股量价统计（内存中间对象）。 */
    private static class MemberStats {
        String symbol;
        String name;
        Integer quality;
        BigDecimal dailyChange;
        BigDecimal fiveDayChange;
        BigDecimal amount;
        BigDecimal avgAmount5;
        int limitUpDays5;

        BigDecimal heatContribution() {
            return dailyChange.add(fiveDayChange);
        }
    }

    /** 方向聚合中间对象。 */
    private static class DirectionAgg {
        String name;
        List<MemberStats> members = new ArrayList<>();

        BigDecimal dailyAvgChange() {
            return avg(members, m -> m.dailyChange);
        }

        BigDecimal fiveDayAvgChange() {
            return avg(members, m -> m.fiveDayChange);
        }

        private BigDecimal avg(List<MemberStats> list, Function<MemberStats, BigDecimal> getter) {
            BigDecimal sum = ZERO;
            for (MemberStats m : list) {
                sum = sum.add(getter.apply(m));
            }
            return list.isEmpty() ? ZERO
                    : sum.divide(BigDecimal.valueOf(list.size()), 6, RoundingMode.HALF_UP);
        }
    }

    /**
     * 聚合行业/概念量价 → 热度得分 → Top10 → 入库（upsert）。无当日快照返回 null。
     */
    public HotspotResult buildAndSave(String tradeDate) {
        HotspotResult result = build(tradeDate);
        if (result == null) {
            return null;
        }
        result.setGeneratedAt(LocalDateTime.now());
        Document document = new Document();
        mongoTemplate.getConverter().write(result, document);
        document.remove("_id");
        Update update = new Update();
        document.forEach(update::set);
        mongoTemplate.upsert(Query.query(Criteria.where("trade_date").is(tradeDate)), update,
                HotspotResult.class);
        return result;
    }

    public HotspotResult build(String tradeDate) {
        ScreeningScoreRecord snapshot = screeningScoreRecordRepository.findByTradeDate(tradeDate);
        if (snapshot == null || snapshot.getStocks() == null || snapshot.getStocks().isEmpty()) {
            log.warn("当日 {} 无选股快照，跳过热点识别", tradeDate);
            return null;
        }
        Map<String, Integer> qualityBySymbol = new HashMap<>();
        Map<String, String> nameBySymbol = new HashMap<>();
        for (StockFactor f : snapshot.getStocks()) {
            qualityBySymbol.put(f.getSymbol(), f.getQuality());
            nameBySymbol.put(f.getSymbol(), f.getName());
        }

        List<IndexConstituents> pool = indexConstituentsRepository.findAll();
        Map<String, MemberStats> statsBySymbol = new LinkedHashMap<>();
        for (IndexConstituents c : pool) {
            String symbol = c.getSymbol();
            if (symbol == null || statsBySymbol.containsKey(symbol)) {
                continue;
            }
            // 以评分日为上界取窗口：降序 get(0) 最新，5 日窗口需要 6 个收盘点；
            // 最新一根须为评分日，否则跳过（避免把陈旧涨跌当成当日涨跌）
            List<StockDailyKline> klines = stockDailyKlineRepository
                    .findTop21BySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(symbol, tradeDate);
            if (klines == null || klines.size() < FIVE_DAY_WINDOW + 1
                    || !tradeDate.equals(klines.get(0).getTradeDate())) {
                continue;
            }
            BigDecimal latest = klines.get(0).getClose();
            BigDecimal prev = klines.get(1).getClose();
            BigDecimal fiveAgo = klines.get(FIVE_DAY_WINDOW).getClose();
            if (latest == null || prev == null || fiveAgo == null
                    || prev.compareTo(ZERO) <= 0 || fiveAgo.compareTo(ZERO) <= 0) {
                continue;
            }
            MemberStats m = new MemberStats();
            m.symbol = symbol;
            m.name = nameBySymbol.getOrDefault(symbol, symbol);
            m.quality = qualityBySymbol.get(symbol);
            m.dailyChange = latest.subtract(prev).divide(prev, 6, RoundingMode.HALF_UP);
            m.fiveDayChange = latest.subtract(fiveAgo).divide(fiveAgo, 6, RoundingMode.HALF_UP);
            m.amount = klines.get(0).getAmount() == null ? ZERO : klines.get(0).getAmount();
            BigDecimal amountSum = ZERO;
            for (int i = 0; i < FIVE_DAY_WINDOW; i++) {
                amountSum = amountSum.add(klines.get(i).getAmount() == null ? ZERO : klines.get(i).getAmount());
            }
            m.avgAmount5 = amountSum.divide(BigDecimal.valueOf(FIVE_DAY_WINDOW), 6, RoundingMode.HALF_UP);
            m.limitUpDays5 = countLimitUpDays(klines);
            statsBySymbol.put(symbol, m);
        }

        BigDecimal poolDailyAmount = ZERO;
        BigDecimal poolFiveDayAmount = ZERO;
        for (MemberStats m : statsBySymbol.values()) {
            poolDailyAmount = poolDailyAmount.add(m.amount);
            poolFiveDayAmount = poolFiveDayAmount.add(m.avgAmount5);
        }

        Map<String, DirectionAgg> byIndustry = new LinkedHashMap<>();
        Map<String, DirectionAgg> byConcept = new LinkedHashMap<>();
        for (MemberStats m : statsBySymbol.values()) {
            StockIndustryConcept ic = stockIndustryConceptRepository.findBySymbol(m.symbol);
            if (ic == null) {
                continue;
            }
            if (ic.getIndustry() != null && !ic.getIndustry().isBlank()) {
                byIndustry.computeIfAbsent(ic.getIndustry(), k -> new DirectionAgg()).members.add(m);
            }
            if (ic.getConcepts() != null) {
                for (String concept : ic.getConcepts()) {
                    if (concept != null && !concept.isBlank()) {
                        byConcept.computeIfAbsent(concept, k -> new DirectionAgg()).members.add(m);
                    }
                }
            }
        }

        byIndustry.values().removeIf(a -> a.members.size() < MIN_DIRECTION_SIZE);
        byConcept.values().removeIf(a -> a.members.size() < MIN_DIRECTION_SIZE);

        HotspotResult result = new HotspotResult();
        result.setTradeDate(tradeDate);
        result.setIndustries(toDirections(byIndustry, poolDailyAmount, poolFiveDayAmount));
        result.setConcepts(toDirections(byConcept, poolDailyAmount, poolFiveDayAmount));
        log.info("热点识别完成: tradeDate={}, 行业 {} 个, 概念 {} 个",
                tradeDate, result.getIndustries().size(), result.getConcepts().size());
        return result;
    }

    private int countLimitUpDays(List<StockDailyKline> klines) {
        int days = 0;
        for (int i = 0; i < FIVE_DAY_WINDOW; i++) {
            BigDecimal close = klines.get(i).getClose();
            BigDecimal base = klines.get(i + 1).getClose();
            if (close != null && base != null && base.compareTo(ZERO) > 0) {
                BigDecimal change = close.subtract(base).divide(base, 6, RoundingMode.HALF_UP);
                if (change.compareTo(LIMIT_UP_THRESHOLD) >= 0) {
                    days++;
                }
            }
        }
        return days;
    }

    private List<HotspotDirection> toDirections(Map<String, DirectionAgg> aggs,
                                                 BigDecimal poolDailyAmount,
                                                 BigDecimal poolFiveDayAmount) {
        List<HotspotDirection> directions = new ArrayList<>();
        if (aggs.isEmpty()) {
            return directions;
        }
        // 第一遍：聚合原始指标 + 代表股（raw 与 aggList 按索引平行对应）
        List<BigDecimal> dailyChanges = new ArrayList<>();
        List<BigDecimal> dailyRatios = new ArrayList<>();
        List<BigDecimal> limitUps = new ArrayList<>();
        List<BigDecimal> fiveDayChanges = new ArrayList<>();
        List<BigDecimal> fiveDayRatios = new ArrayList<>();
        List<BigDecimal> fiveDayLimitUps = new ArrayList<>();
        List<HotspotDirection> raw = new ArrayList<>();
        List<DirectionAgg> aggList = new ArrayList<>();
        for (Map.Entry<String, DirectionAgg> entry : aggs.entrySet()) {
            DirectionAgg agg = entry.getValue();
            HotspotDirection d = new HotspotDirection();
            d.setName(entry.getKey());
            d.setStockCount(agg.members.size());
            d.setDailyAvgChange(agg.dailyAvgChange());
            d.setFiveDayAvgChange(agg.fiveDayAvgChange());
            BigDecimal dailySum = ZERO;
            BigDecimal fiveDaySum = ZERO;
            int limitUpCount = 0;
            int fiveDayLimitUpCount = 0;
            for (MemberStats m : agg.members) {
                dailySum = dailySum.add(m.amount);
                fiveDaySum = fiveDaySum.add(m.avgAmount5);
                if (m.dailyChange.compareTo(LIMIT_UP_THRESHOLD) >= 0) {
                    limitUpCount++;
                }
                if (m.limitUpDays5 > 0) {
                    fiveDayLimitUpCount++;
                }
            }
            d.setLimitUpCount(limitUpCount);
            d.setFiveDayLimitUpCount(fiveDayLimitUpCount);
            d.setDailyAmountRatio(poolDailyAmount.compareTo(ZERO) > 0
                    ? dailySum.divide(poolDailyAmount, 6, RoundingMode.HALF_UP) : ZERO);
            d.setFiveDayAmountRatio(poolFiveDayAmount.compareTo(ZERO) > 0
                    ? fiveDaySum.divide(poolFiveDayAmount, 6, RoundingMode.HALF_UP) : ZERO);
            d.setTopStocks(topRepresentatives(agg));
            raw.add(d);
            aggList.add(agg);
            dailyChanges.add(d.getDailyAvgChange());
            dailyRatios.add(d.getDailyAmountRatio());
            limitUps.add(BigDecimal.valueOf(limitUpCount));
            fiveDayChanges.add(d.getFiveDayAvgChange());
            fiveDayRatios.add(d.getFiveDayAmountRatio());
            fiveDayLimitUps.add(BigDecimal.valueOf(fiveDayLimitUpCount));
        }
        // 第二遍：跨方向百分位 → 热度得分（Task 6 在 setHotScore 之后追加炒作标注）
        for (int i = 0; i < raw.size(); i++) {
            HotspotDirection d = raw.get(i);
            int dailyChangePct = ScreeningService.percentileDesc(dailyChanges, d.getDailyAvgChange());
            int dailyRatioPct = ScreeningService.percentileDesc(dailyRatios, d.getDailyAmountRatio());
            int limitUpPct = ScreeningService.percentileDesc(limitUps, BigDecimal.valueOf(d.getLimitUpCount()));
            BigDecimal dailyHeat = CHANGE_WEIGHT.multiply(BigDecimal.valueOf(dailyChangePct))
                    .add(AMOUNT_WEIGHT.multiply(BigDecimal.valueOf(dailyRatioPct)))
                    .add(LIMIT_UP_WEIGHT.multiply(BigDecimal.valueOf(limitUpPct)));
            int fiveChangePct = ScreeningService.percentileDesc(fiveDayChanges, d.getFiveDayAvgChange());
            int fiveRatioPct = ScreeningService.percentileDesc(fiveDayRatios, d.getFiveDayAmountRatio());
            int fiveLimitUpPct = ScreeningService.percentileDesc(fiveDayLimitUps,
                    BigDecimal.valueOf(d.getFiveDayLimitUpCount()));
            BigDecimal fiveHeat = CHANGE_WEIGHT.multiply(BigDecimal.valueOf(fiveChangePct))
                    .add(AMOUNT_WEIGHT.multiply(BigDecimal.valueOf(fiveRatioPct)))
                    .add(LIMIT_UP_WEIGHT.multiply(BigDecimal.valueOf(fiveLimitUpPct)));
            d.setHotScore(FIVE_DAY_HEAT_WEIGHT.multiply(fiveHeat)
                    .add(DAILY_HEAT_WEIGHT.multiply(dailyHeat))
                    .setScale(0, RoundingMode.HALF_UP).intValueExact());
            List<Integer> qualities = new ArrayList<>();
            for (MemberStats m : aggList.get(i).members) {
                qualities.add(m.quality);
            }
            BigDecimal median = medianQuality(qualities);
            d.setHypeLabel(hypeLabel(qualities, d.getHotScore()));
            d.setHypeBasis(median == null
                    ? "无质量分数据，热度得分 " + d.getHotScore()
                    : buildHypeBasis(median, d.getHotScore()));
        }
        raw.sort(Comparator.comparing(HotspotDirection::getHotScore).reversed()
                .thenComparing(HotspotDirection::getName));
        return raw.size() > TOP_N ? new ArrayList<>(raw.subList(0, TOP_N)) : raw;
    }

    /**
     * 炒作风险标注：质量中位数 <40 且热度 ≥60 → HYPE_RISK；≥60 → SUPPORTED；其余 NEUTRAL。
     */
    static String hypeLabel(List<Integer> qualities, int hotScore) {
        BigDecimal median = medianQuality(qualities);
        if (median == null) {
            return HYPE_NEUTRAL;
        }
        if (median.compareTo(new BigDecimal("40")) < 0 && hotScore >= 60) {
            return HYPE_RISK;
        }
        if (median.compareTo(new BigDecimal("60")) >= 0) {
            return HYPE_SUPPORTED;
        }
        return HYPE_NEUTRAL;
    }

    /** 质量分中位数（null 排除；偶数个取中间两数平均；无有效值返回 null）。 */
    static BigDecimal medianQuality(List<Integer> qualities) {
        List<Integer> valid = qualities.stream().filter(Objects::nonNull).sorted().toList();
        if (valid.isEmpty()) {
            return null;
        }
        int mid = valid.size() / 2;
        if (valid.size() % 2 == 1) {
            return BigDecimal.valueOf(valid.get(mid));
        }
        return BigDecimal.valueOf(valid.get(mid - 1)).add(BigDecimal.valueOf(valid.get(mid)))
                .divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
    }

    static String buildHypeBasis(BigDecimal median, int hotScore) {
        return "质量中位数 " + median + "，热度得分 " + hotScore;
    }

    /** 代表股：方向内按 单日涨幅+5日涨幅 之和降序取 3 只。 */
    private List<HotspotStock> topRepresentatives(DirectionAgg agg) {
        List<MemberStats> sorted = new ArrayList<>(agg.members);
        sorted.sort(Comparator.comparing(MemberStats::heatContribution).reversed()
                .thenComparing(m -> m.symbol));
        List<HotspotStock> topStocks = new ArrayList<>();
        for (int i = 0; i < Math.min(REPRESENTATIVE_COUNT, sorted.size()); i++) {
            MemberStats m = sorted.get(i);
            HotspotStock hs = new HotspotStock();
            hs.setSymbol(m.symbol);
            hs.setName(m.name);
            hs.setDailyChange(m.dailyChange);
            hs.setFiveDayChange(m.fiveDayChange);
            topStocks.add(hs);
        }
        return topStocks;
    }

    public Result<HotspotResult> latest() {
        return Result.successIfNotNull(hotspotResultRepository.findTopByOrderByTradeDateDesc(), "暂无热点结果");
    }
}
