package com.xian.stockai.service.policy;

import com.xian.stockai.dao.MarketRegimeRecordRepository;
import com.xian.stockai.dao.PortfolioPolicyRepository;
import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.service.StockService;
import com.xian.stockai.vo.AssetCategoryPositionRes;
import com.xian.stockai.vo.PortfolioSummaryRes;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class PortfolioPolicyServiceTest {

    private static final String USER = "6928fca14f206ada1b7a9269";

    private PortfolioPolicyRepository policyRepository;
    private MarketRegimeRecordRepository regimeRepository;
    private StockService stockService;
    private PortfolioPolicyService service;

    @BeforeEach
    void setUp() {
        policyRepository = Mockito.mock(PortfolioPolicyRepository.class);
        regimeRepository = Mockito.mock(MarketRegimeRecordRepository.class);
        stockService = Mockito.mock(StockService.class);
        service = new PortfolioPolicyService(policyRepository, regimeRepository, stockService);
        Mockito.when(policyRepository.findByUserId(USER)).thenReturn(Optional.empty());
    }

    private void regimes(String... values) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<MarketRegimeRecord> records = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            MarketRegimeRecord record = new MarketRegimeRecord();
            record.setRegime(values[i]);
            record.setDate(today.minusDays(i).toString());
            records.add(record);
        }
        Mockito.when(regimeRepository.findAllByOrderByDateDesc(any(Pageable.class))).thenReturn(records);
    }

    private void positions(String category, long amount) {
        positions(position(category, amount));
    }

    private void positions(AssetCategoryPositionRes... items) {
        Result<List<AssetCategoryPositionRes>> result = Result.success(Arrays.asList(items));
        Mockito.when(stockService.getAssetCategoryPosition(eq(USER))).thenReturn(result);
    }

    @Test
    void threeSameRegimesSwitchTheMatrix() {
        regimes("BEAR", "BEAR", "BEAR");
        positions("股票", 1000);

        PortfolioPolicyService.Resolved resolved = service.resolve(USER);

        assertTrue(resolved.confirmed());
        assertEquals("BEAR", resolved.effectiveRegime());
        assertEquals(0, new BigDecimal("30").compareTo(resolved.targets().get("股票")));
        assertEquals(0, new BigDecimal("25").compareTo(resolved.targets().get("现金")));
    }

    @Test
    void notEnoughConfirmationFallsBackToSideways() {
        regimes("BEAR", "BULL");

        PortfolioPolicyService.Resolved resolved = service.resolve(USER);

        assertFalse(resolved.confirmed());
        assertEquals("SIDEWAYS", resolved.effectiveRegime());
        assertEquals(0, new BigDecimal("45").compareTo(resolved.targets().get("股票")));
    }

    @Test
    void staleRegimeFallsBackToSideways() {
        // 判定器停了六天以上，旧结论不能再指挥仓位
        LocalDate old = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(9);
        List<MarketRegimeRecord> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MarketRegimeRecord record = new MarketRegimeRecord();
            record.setRegime("BEAR");
            record.setDate(old.minusDays(i).toString());
            records.add(record);
        }
        Mockito.when(regimeRepository.findAllByOrderByDateDesc(any(Pageable.class))).thenReturn(records);

        PortfolioPolicyService.Resolved resolved = service.resolve(USER);

        assertFalse(resolved.confirmed());
        assertEquals("SIDEWAYS", resolved.effectiveRegime());
    }

    @Test
    void overWeightBeyondBandIsReportedAsBreach() {
        regimes("BEAR", "BEAR", "BEAR");
        positions(
                position("股票", 6000), position("债券", 1000),
                position("黄金", 1000), position("现金", 2000));

        PortfolioSummaryRes summary = service.summary(USER);

        PortfolioSummaryRes.Row stock = summary.rows().stream()
                .filter(r -> "股票".equals(r.category())).findFirst().orElseThrow();
        assertEquals("OVER", stock.status());
        assertEquals(0, new BigDecimal("60.0").compareTo(stock.actualPct()));
        // 基金并入股票后 BEAR 目标 45%，股票实际 60%，超配 15pp
        assertTrue(summary.breaches().get(0).text().startsWith("股票 超配"));
    }

    @Test
    void fundTargetIsMergedIntoStockWhenConfigHasNoFundBucket() {
        regimes("BULL", "BULL", "BULL");
        positions(
                position("股票", 0), position("债券", 0),
                position("黄金", 0), position("现金", 0));

        PortfolioSummaryRes summary = service.summary(USER);

        // BULL 档股票 58% + 基金 27%，配置里没有基金桶时合并成 85%
        PortfolioSummaryRes.Row stock = summary.rows().stream()
                .filter(r -> "股票".equals(r.category())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("85").compareTo(stock.target()));
        assertTrue(summary.rows().stream().noneMatch(r -> "基金".equals(r.category())));
    }

    @Test
    void zeroHoldingAgainstATargetIsAnUnderweightBreach() {
        regimes("BEAR", "BEAR", "BEAR");
        positions(
                position("股票", 3000), position("债券", 0),
                position("黄金", 0), position("现金", 7000));

        PortfolioSummaryRes summary = service.summary(USER);

        PortfolioSummaryRes.Row bond = summary.rows().stream()
                .filter(r -> "债券".equals(r.category())).findFirst().orElseThrow();
        assertEquals("UNDER", bond.status());
        assertTrue(summary.breaches().stream().anyMatch(b -> b.text().startsWith("债券 低配")));
    }

    @Test
    void insideBandIsNotABreach() {
        regimes("SIDEWAYS", "SIDEWAYS", "SIDEWAYS");
        positions(
                position("股票", 6500), position("债券", 1500),
                position("黄金", 500), position("现金", 1500));

        PortfolioSummaryRes summary = service.summary(USER);

        PortfolioSummaryRes.Row stock = summary.rows().stream()
                .filter(r -> "股票".equals(r.category())).findFirst().orElseThrow();
        assertEquals("IN", stock.status());
        assertTrue(summary.breaches().stream().noneMatch(b -> "股票".equals(b.category())));
    }

    @Test
    void emptyPortfolioDoesNotDivideByZero() {
        regimes("BULL", "BULL", "BULL");
        Mockito.when(stockService.getAssetCategoryPosition(eq(USER)))
                .thenReturn(Result.success(Collections.emptyList()));

        PortfolioSummaryRes summary = service.summary(USER);

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalAmount()));
        assertTrue(summary.rows().stream().allMatch(r -> BigDecimal.ZERO.compareTo(r.actualPct()) == 0));
    }

    @Test
    void everyRegimeWeightsSumToHundred() {
        PortfolioPolicyService.defaultMatrix().forEach((regime, weights) -> {
            BigDecimal sum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal("100").compareTo(sum), regime + " 合计应为 100");
        });
    }

    private static AssetCategoryPositionRes position(String category, long amount) {
        AssetCategoryPositionRes res = new AssetCategoryPositionRes();
        res.setCategoryName(category);
        res.setPositionAmount(BigDecimal.valueOf(amount));
        res.setType(category);
        return res;
    }
}
