package com.xian.stockai.service;

import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.StockBasicInfoRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockFundamentalsRepository;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.StockBasicInfo;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.po.StockFundamentals;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
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

    private static final int MOMENTUM_BARS = 21;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal VALUATION_PE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal VALUATION_PB_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal GROWTH_REVENUE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal GROWTH_PROFIT_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal QUALITY_ROE_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal QUALITY_GM_WEIGHT = new BigDecimal("0.4");

    private final IndexConstituentsRepository indexConstituentsRepository;
    private final StockFundamentalsRepository stockFundamentalsRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final StockBasicInfoRepository stockBasicInfoRepository;

    public ScreeningService(IndexConstituentsRepository indexConstituentsRepository,
                            StockFundamentalsRepository stockFundamentalsRepository,
                            StockDailyKlineRepository stockDailyKlineRepository,
                            StockBasicInfoRepository stockBasicInfoRepository) {
        this.indexConstituentsRepository = indexConstituentsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.stockBasicInfoRepository = stockBasicInfoRepository;
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
                int pePct = (f.getPe() != null && f.getPe().compareTo(ZERO) > 0)
                        ? percentileAsc(peValues, f.getPe()) : 0;
                int pbPct = (f.getPb() != null && f.getPb().compareTo(ZERO) > 0)
                        ? percentileAsc(pbValues, f.getPb()) : 0;
                f.setPePct(pePct);
                f.setValuation(roundToInt(
                        VALUATION_PE_WEIGHT.multiply(BigDecimal.valueOf(pePct))
                                .add(VALUATION_PB_WEIGHT.multiply(BigDecimal.valueOf(pbPct)))
                                .doubleValue()));

                int revPct = f.getRevenueYoy() == null ? 0 : percentileDesc(revYoyValues, f.getRevenueYoy());
                int proPct = f.getProfitYoy() == null ? 0 : percentileDesc(profitYoyValues, f.getProfitYoy());
                f.setGrowth(roundToInt(
                        GROWTH_REVENUE_WEIGHT.multiply(BigDecimal.valueOf(revPct))
                                .add(GROWTH_PROFIT_WEIGHT.multiply(BigDecimal.valueOf(proPct)))
                                .doubleValue()));

                int roePct = f.getRoe() == null ? 0 : percentileDesc(roeValues, f.getRoe());
                int gmPct = f.getGrossMargin() == null ? 0 : percentileDesc(gmValues, f.getGrossMargin());
                f.setRoePct(roePct);
                f.setQuality(roundToInt(
                        QUALITY_ROE_WEIGHT.multiply(BigDecimal.valueOf(roePct))
                                .add(QUALITY_GM_WEIGHT.multiply(BigDecimal.valueOf(gmPct)))
                                .doubleValue()));
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
