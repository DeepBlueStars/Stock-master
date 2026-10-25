package com.xian.stockai.service;

import com.xian.stockai.config.ScreeningProperties;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.ScreeningResultRepository;
import com.xian.stockai.dao.ScreeningScoreRecordRepository;
import com.xian.stockai.dao.StockBasicInfoRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockFundamentalsRepository;
import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.ScreeningScoreRecord;
import com.xian.stockai.po.StockBasicInfo;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockFundamentals;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScreeningService {

    public static final String STYLE_VALUE = "value";
    public static final String STYLE_GROWTH = "growth";
    public static final String STYLE_BALANCED = "balanced";

    public static final String RATING_HIGH = "HIGH";
    public static final String RATING_MEDIUM = "MEDIUM";
    public static final String RATING_LOW = "LOW";
    public static final String RATING_UNKNOWN = "UNKNOWN";

    private static final int MOMENTUM_BARS = 21;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal VALUATION_PE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal VALUATION_PB_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal GROWTH_REVENUE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal GROWTH_PROFIT_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal QUALITY_ROE_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal QUALITY_GM_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal LONG_TERM_QUALITY_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal LONG_TERM_GROWTH_WEIGHT = new BigDecimal("0.4");

    private static final int TOP_N = 20;

    private final IndexConstituentsRepository indexConstituentsRepository;
    private final StockFundamentalsRepository stockFundamentalsRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final StockBasicInfoRepository stockBasicInfoRepository;
    private final ScreeningProperties screeningProperties;
    private final ScreeningScoreRecordRepository screeningScoreRecordRepository;
    private final ScreeningResultRepository screeningResultRepository;

    public ScreeningService(IndexConstituentsRepository indexConstituentsRepository,
                            StockFundamentalsRepository stockFundamentalsRepository,
                            StockDailyKlineRepository stockDailyKlineRepository,
                            StockBasicInfoRepository stockBasicInfoRepository,
                            ScreeningProperties screeningProperties,
                            ScreeningScoreRecordRepository screeningScoreRecordRepository,
                            ScreeningResultRepository screeningResultRepository) {
        this.indexConstituentsRepository = indexConstituentsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.stockBasicInfoRepository = stockBasicInfoRepository;
        this.screeningProperties = screeningProperties;
        this.screeningScoreRecordRepository = screeningScoreRecordRepository;
        this.screeningResultRepository = screeningResultRepository;
    }

    /**
     * 正向百分位：值越大得分越高，0~100 整数。n<=1 返回 50。
     */
    public static int percentileDesc(List<BigDecimal> values, BigDecimal target) {
        if (values == null || values.size() <= 1) {
            return 50;
        }
        long less = values.stream().filter(v -> v.compareTo(target) < 0).count();
        return roundToInt(less * 100.0 / (values.size() - 1));
    }

    /**
     * 反向百分位：值越小得分越高，0~100 整数。n<=1 返回 50。
     */
    public static int percentileAsc(List<BigDecimal> values, BigDecimal target) {
        if (values == null || values.size() <= 1) {
            return 50;
        }
        long greater = values.stream().filter(v -> v.compareTo(target) > 0).count();
        return roundToInt(greater * 100.0 / (values.size() - 1));
    }

    private static int roundToInt(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /**
     * 读取优质池 + 财务 + K线，计算四因子（横截面百分位，仅由当日数据决定，可复现）。
     */
    public List<StockFactor> calculateFactors(String tradeDate) {
        List<IndexConstituents> pool = indexConstituentsRepository.findAll();
        if (pool == null || pool.isEmpty()) {
            log.warn("优质池成分股为空，跳过因子计算");
            return new ArrayList<>();
        }
        Map<String, String> nameMap = stockBasicInfoRepository.findAll().stream()
                .filter(b -> b.getSymbol() != null && b.getName() != null)
                .collect(Collectors.toMap(StockBasicInfo::getSymbol, StockBasicInfo::getName, (a, b) -> a));

        LocalDate oneYearAgo = LocalDate.parse(tradeDate).minusYears(1);
        List<StockFactor> factors = new ArrayList<>();
        // 百分位输入集合
        List<BigDecimal> peValues = new ArrayList<>();
        List<BigDecimal> pbValues = new ArrayList<>();
        List<BigDecimal> roeValues = new ArrayList<>();
        List<BigDecimal> gmValues = new ArrayList<>();
        List<BigDecimal> revYoyValues = new ArrayList<>();
        List<BigDecimal> profitYoyValues = new ArrayList<>();
        List<BigDecimal> momentumReturns = new ArrayList<>();

        for (IndexConstituents c : pool) {
            String symbol = c.getSymbol();
            if (symbol == null || factors.stream().anyMatch(f -> symbol.equals(f.getSymbol()))) {
                continue; // 池内去重
            }
            StockFactor f = new StockFactor();
            f.setSymbol(symbol);
            f.setName(nameMap.getOrDefault(symbol, symbol));

            StockFundamentals fund = stockFundamentalsRepository.findTopBySymbolOrderByReportDateDesc(symbol);
            boolean fundUsable = fund != null && fund.getReportDate() != null
                    && !LocalDate.parse(fund.getReportDate()).isBefore(oneYearAgo);
            if (fundUsable) {
                f.setDataDate(fund.getReportDate());
                f.setPe(fund.getPe());
                f.setPb(fund.getPb());
                f.setRoe(fund.getRoe());
                f.setGrossMargin(fund.getGrossMargin());
                f.setRevenueYoy(fund.getRevenueYoy());
                f.setProfitYoy(fund.getProfitYoy());
                if (fund.getPe() != null && fund.getPe().compareTo(ZERO) > 0) {
                    peValues.add(fund.getPe());
                }
                if (fund.getPb() != null && fund.getPb().compareTo(ZERO) > 0) {
                    pbValues.add(fund.getPb());
                }
                if (fund.getRoe() != null) {
                    roeValues.add(fund.getRoe());
                }
                if (fund.getGrossMargin() != null) {
                    gmValues.add(fund.getGrossMargin());
                }
                if (fund.getRevenueYoy() != null) {
                    revYoyValues.add(fund.getRevenueYoy());
                }
                if (fund.getProfitYoy() != null) {
                    profitYoyValues.add(fund.getProfitYoy());
                }
            }

            List<StockDailyKline> klines =
                    stockDailyKlineRepository.findTop21BySymbolOrderByTradeDateDesc(symbol);
            if (klines != null && klines.size() >= MOMENTUM_BARS
                    && klines.get(0).getClose() != null && klines.get(MOMENTUM_BARS - 1).getClose() != null
                    && klines.get(MOMENTUM_BARS - 1).getClose().compareTo(ZERO) > 0) {
                BigDecimal latest = klines.get(0).getClose();
                BigDecimal oldest = klines.get(MOMENTUM_BARS - 1).getClose();
                f.setMomentumReturn(latest.subtract(oldest).divide(oldest, 6, RoundingMode.HALF_UP));
                momentumReturns.add(f.getMomentumReturn());
            }
            factors.add(f);
        }

        for (StockFactor f : factors) {
            boolean fundUsable = f.getDataDate() != null;
            if (fundUsable) {
                Integer pePct = (f.getPe() != null && f.getPe().compareTo(ZERO) > 0)
                        ? percentileAsc(peValues, f.getPe()) : null;
                Integer pbPct = (f.getPb() != null && f.getPb().compareTo(ZERO) > 0)
                        ? percentileAsc(pbValues, f.getPb()) : null;
                boolean pePresent = f.getPe() != null;
                boolean pbPresent = f.getPb() != null;
                if (pePresent || pbPresent) {
                    // PE ≤ 0 子项得 0 分；缺失子项不参与，剩余子项按 100% 权重
                    int peScore = pePct == null ? 0 : pePct;
                    int pbScore = pbPct == null ? 0 : pbPct;
                    int valuation;
                    if (pePresent && pbPresent) {
                        valuation = roundToInt(
                                VALUATION_PE_WEIGHT.multiply(BigDecimal.valueOf(peScore))
                                        .add(VALUATION_PB_WEIGHT.multiply(BigDecimal.valueOf(pbScore)))
                                        .doubleValue());
                    } else {
                        valuation = pePresent ? peScore : pbScore;
                    }
                    f.setValuation(valuation);
                }
                f.setPePct(pePct);

                Integer revPct = f.getRevenueYoy() == null ? null : percentileDesc(revYoyValues, f.getRevenueYoy());
                Integer proPct = f.getProfitYoy() == null ? null : percentileDesc(profitYoyValues, f.getProfitYoy());
                if (revPct != null || proPct != null) {
                    int growth = (revPct != null && proPct != null)
                            ? roundToInt(GROWTH_REVENUE_WEIGHT.multiply(BigDecimal.valueOf(revPct))
                                    .add(GROWTH_PROFIT_WEIGHT.multiply(BigDecimal.valueOf(proPct)))
                                    .doubleValue())
                            : (revPct != null ? revPct : proPct);
                    f.setGrowth(growth);
                }

                Integer roePct = f.getRoe() == null ? null : percentileDesc(roeValues, f.getRoe());
                Integer gmPct = f.getGrossMargin() == null ? null : percentileDesc(gmValues, f.getGrossMargin());
                f.setRoePct(roePct);
                if (roePct != null || gmPct != null) {
                    int quality = (roePct != null && gmPct != null)
                            ? roundToInt(QUALITY_ROE_WEIGHT.multiply(BigDecimal.valueOf(roePct))
                                    .add(QUALITY_GM_WEIGHT.multiply(BigDecimal.valueOf(gmPct)))
                                    .doubleValue())
                            : (roePct != null ? roePct : gmPct);
                    f.setQuality(quality);
                }
            }
            if (f.getMomentumReturn() != null) {
                f.setMomentumPct(percentileDesc(momentumReturns, f.getMomentumReturn()));
                f.setMomentum(f.getMomentumPct());
            }

            int missing = countMissing(f);
            f.setDataIncomplete(missing >= 2);
        }
        return factors;
    }

    /**
     * 生成指定风格的 Top 20 候选榜：data_incomplete 不入榜；缺失因子按剩余权重重归一化。
     */
    public List<BoardEntry> buildBoard(String style, List<StockFactor> factors) {
        Map<String, BigDecimal> weights = screeningProperties.weightsOf(style);
        List<BoardEntry> entries = new ArrayList<>();
        for (StockFactor f : factors) {
            if (Boolean.TRUE.equals(f.getDataIncomplete())) {
                continue;
            }
            BigDecimal total = weightedTotal(weights, f);
            if (total == null) {
                continue;
            }
            BoardEntry e = new BoardEntry();
            e.setSymbol(f.getSymbol());
            e.setName(f.getName());
            e.setTotalScore(total);
            e.setValuation(f.getValuation());
            e.setGrowth(f.getGrowth());
            e.setQuality(f.getQuality());
            e.setMomentum(f.getMomentum());
            e.setLongTermRating(f.getLongTermRating());
            e.setReason(buildReason(f));
            entries.add(e);
        }
        entries.sort(Comparator.comparing(BoardEntry::getTotalScore).reversed()
                .thenComparing(BoardEntry::getSymbol));
        return entries.size() > TOP_N ? new ArrayList<>(entries.subList(0, TOP_N)) : entries;
    }

    private BigDecimal weightedTotal(Map<String, BigDecimal> weights, StockFactor f) {
        BigDecimal sum = ZERO;
        BigDecimal weightSum = ZERO;
        if (f.getValuation() != null) {
            sum = sum.add(weights.get("valuation").multiply(BigDecimal.valueOf(f.getValuation())));
            weightSum = weightSum.add(weights.get("valuation"));
        }
        if (f.getGrowth() != null) {
            sum = sum.add(weights.get("growth").multiply(BigDecimal.valueOf(f.getGrowth())));
            weightSum = weightSum.add(weights.get("growth"));
        }
        if (f.getQuality() != null) {
            sum = sum.add(weights.get("quality").multiply(BigDecimal.valueOf(f.getQuality())));
            weightSum = weightSum.add(weights.get("quality"));
        }
        if (f.getMomentum() != null) {
            sum = sum.add(weights.get("momentum").multiply(BigDecimal.valueOf(f.getMomentum())));
            weightSum = weightSum.add(weights.get("momentum"));
        }
        if (weightSum.compareTo(ZERO) <= 0) {
            return null;
        }
        return sum.divide(weightSum, 2, RoundingMode.HALF_UP);
    }

    /**
     * 模板化入选理由（纯量化文本，不用 AI，保证可复现）。
     */
    public static String buildReason(StockFactor f) {
        List<String> parts = new ArrayList<>();
        if (f.getRoePct() != null) {
            parts.add("ROE 池内前 " + (100 - f.getRoePct()) + "%");
        }
        if (f.getPePct() != null) {
            parts.add("PE 低于 " + f.getPePct() + "% 成分股");
        }
        if (f.getMomentumPct() != null) {
            parts.add("20 日涨幅池内前 " + (100 - f.getMomentumPct()) + "%");
        }
        return String.join("，", parts);
    }

    /**
     * 长期价值评级：质量 60% + 成长 40%，任一缺失用另一方 100%，均缺失 → UNKNOWN。
     */
    public static String rateLongTerm(StockFactor f) {
        Integer q = f.getQuality();
        Integer g = f.getGrowth();
        if (q == null && g == null) {
            return RATING_UNKNOWN;
        }
        BigDecimal score;
        if (q == null) {
            score = BigDecimal.valueOf(g);
        } else if (g == null) {
            score = BigDecimal.valueOf(q);
        } else {
            score = BigDecimal.valueOf(q).multiply(LONG_TERM_QUALITY_WEIGHT)
                    .add(BigDecimal.valueOf(g).multiply(LONG_TERM_GROWTH_WEIGHT));
        }
        if (score.compareTo(new BigDecimal("65")) >= 0) {
            return RATING_HIGH;
        }
        if (score.compareTo(new BigDecimal("45")) >= 0) {
            return RATING_MEDIUM;
        }
        return RATING_LOW;
    }

    /**
     * 计算因子 + 填充长期评级 + 全量快照 upsert 入库（screening_scores）。
     */
    public List<StockFactor> calculateAndSaveSnapshot(String tradeDate) {
        List<StockFactor> factors = calculateFactors(tradeDate);
        for (StockFactor f : factors) {
            f.setLongTermRating(rateLongTerm(f));
        }
        ScreeningScoreRecord record = screeningScoreRecordRepository.findByTradeDate(tradeDate);
        if (record == null) {
            record = new ScreeningScoreRecord();
        }
        record.setTradeDate(tradeDate);
        record.setGeneratedAt(LocalDateTime.now());
        record.setStocks(factors);
        screeningScoreRecordRepository.save(record);
        log.info("选股快照已入库: tradeDate={}, 股票数={}", tradeDate, factors.size());
        return factors;
    }

    public Result<ScreeningResult> latest() {
        return Result.successIfNotNull(screeningResultRepository.findTopByOrderByTradeDateDesc(), "暂无选股结果");
    }

    public Result<List<ScreeningResult>> history(int page, int size) {
        return Result.success(screeningResultRepository
                .findAllByOrderByTradeDateDesc(PageRequest.of(page - 1, size)));
    }

    private static int countMissing(StockFactor f) {
        int missing = 0;
        if (f.getValuation() == null) {
            missing++;
        }
        if (f.getGrowth() == null) {
            missing++;
        }
        if (f.getQuality() == null) {
            missing++;
        }
        if (f.getMomentum() == null) {
            missing++;
        }
        return missing;
    }
}
