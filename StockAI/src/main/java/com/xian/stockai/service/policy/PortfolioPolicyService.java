package com.xian.stockai.service.policy;

import com.xian.stockai.dao.MarketRegimeRecordRepository;
import com.xian.stockai.dao.PortfolioPolicyRepository;
import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.po.PortfolioPolicy;
import com.xian.stockai.service.StockService;
import com.xian.stockai.vo.AssetCategoryPositionRes;
import com.xian.stockai.vo.PortfolioSummaryRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把「市场状态」翻译成「各类资产该占多少」，再和实际持仓比一遍。
 * 只做提示，不改任何持仓；调仓仍由人决定。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioPolicyService {

    public static final String REGIME_BULL = "BULL";
    public static final String REGIME_SIDEWAYS = "SIDEWAYS";
    public static final String REGIME_BEAR = "BEAR";
    public static final String REGIME_RISK = "RISK";

    private static final String[] CATEGORIES = {"股票", "基金", "债券", "黄金", "现金"};

    private final PortfolioPolicyRepository policyRepository;
    private final MarketRegimeRecordRepository regimeRepository;
    private final StockService stockService;

    /** 内置默认矩阵：权益总仓位 BULL 85% / SIDEWAYS 65% / BEAR 45% / RISK 30% */
    static Map<String, Map<String, BigDecimal>> defaultMatrix() {
        Map<String, Map<String, BigDecimal>> matrix = new LinkedHashMap<>();
        matrix.put(REGIME_BULL, weights(58, 27, 8, 2, 5));
        matrix.put(REGIME_SIDEWAYS, weights(45, 20, 15, 5, 15));
        matrix.put(REGIME_BEAR, weights(30, 15, 20, 10, 25));
        matrix.put(REGIME_RISK, weights(20, 10, 25, 10, 35));
        return matrix;
    }

    private static Map<String, BigDecimal> weights(int stock, int fund, int bond, int gold, int cash) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("股票", BigDecimal.valueOf(stock));
        map.put("基金", BigDecimal.valueOf(fund));
        map.put("债券", BigDecimal.valueOf(bond));
        map.put("黄金", BigDecimal.valueOf(gold));
        map.put("现金", BigDecimal.valueOf(cash));
        return map;
    }

    /**
     * 市场状态需要连续 confirmDays 天一致才生效，否则沿用上一档。
     * 判定器本身有滞后，单日翻转就换参数会让规则反复打脸。
     */
    public Resolved resolve(String userId) {
        List<MarketRegimeRecord> recent = regimeRepository.findAllByOrderByDateDesc(PageRequest.of(0, 10));
        PortfolioPolicy policy = policyRepository.findByUserId(userId).orElse(null);
        int confirmDays = policy == null || policy.getConfirmDays() == null ? 3 : policy.getConfirmDays();
        BigDecimal band = policy == null || policy.getBand() == null ? new BigDecimal("5") : policy.getBand();

        String latest = recent.isEmpty() ? REGIME_SIDEWAYS : normalizeRegime(recent.get(0).getRegime());
        String latestDate = recent.isEmpty() ? null : recent.get(0).getDate();
        boolean confirmed = recent.size() >= confirmDays && isFresh(latestDate);
        for (int i = 0; i < Math.min(confirmDays, recent.size()); i++) {
            if (!latest.equals(normalizeRegime(recent.get(i).getRegime()))) {
                confirmed = false;
                break;
            }
        }

        Map<String, Map<String, BigDecimal>> matrix = defaultMatrix();
        if (policy != null && policy.getMatrix() != null && !policy.getMatrix().isEmpty()) {
            matrix = toMap(policy.getMatrix());
        }
        // 未确认时退回震荡档：既不激进也不清仓
        String effective = confirmed ? latest : REGIME_SIDEWAYS;
        Map<String, BigDecimal> targets = matrix.getOrDefault(effective, matrix.get(REGIME_SIDEWAYS));

        return new Resolved(latest, effective, confirmed, confirmDays, band, latestDate, targets);
    }

    public PortfolioSummaryRes summary(String userId) {
        Resolved resolved = resolve(userId);
        Map<String, BigDecimal> actual = actualAmounts(userId);

        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : actual.values()) {
            total = total.add(amount);
        }

        // 类别轴以用户左侧配置为准：getAssetCategoryPosition 只返回配置里的类别
        List<String> categories = new ArrayList<>(actual.keySet());
        if (categories.isEmpty()) {
            categories = Arrays.asList(CATEGORIES);
        }
        Map<String, BigDecimal> targets = rollUp(resolved.targets(), categories);

        List<PortfolioSummaryRes.Row> rows = new ArrayList<>();
        List<PortfolioSummaryRes.Breach> breaches = new ArrayList<>();
        for (String category : categories) {
            BigDecimal target = targets.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal amount = actual.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal actualPct = total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            BigDecimal deviation = actualPct.subtract(target).setScale(1, RoundingMode.HALF_UP);
            String status = "IN";
            if (deviation.compareTo(resolved.band()) > 0) {
                status = "OVER";
            } else if (deviation.compareTo(resolved.band().negate()) < 0) {
                status = "UNDER";
            }
            if (!"IN".equals(status)) {
                String text = String.format("%s %s%s（目标 %s%%，带宽 ±%s）",
                        category,
                        "OVER".equals(status) ? "超配" : "低配",
                        deviation.abs().toPlainString() + "pp",
                        target.stripTrailingZeros().toPlainString(),
                        resolved.band().stripTrailingZeros().toPlainString());
                breaches.add(new PortfolioSummaryRes.Breach(category, status, deviation, target, text));
            }
            rows.add(new PortfolioSummaryRes.Row(
                    category, target, amount, actualPct, deviation, status));
        }
        breaches.sort((a, b) -> b.deviation().abs().compareTo(a.deviation().abs()));

        return new PortfolioSummaryRes(
                resolved.latestRegime(),
                resolved.effectiveRegime(),
                resolved.confirmed(),
                resolved.confirmDays(),
                resolved.band(),
                resolved.latestDate(),
                total.setScale(2, RoundingMode.HALF_UP),
                rows,
                breaches);
    }

    /**
     * 把内置矩阵的 5 个标准类别折算到用户实际配置的类别上。
     * 配置里没有单独的「基金」桶时并入「股票」（同为权益仓）；
     * 其它缺失类别按已有目标比例放大，保证折算后合计仍是 100。
     */
    static Map<String, BigDecimal> rollUp(Map<String, BigDecimal> canonical, List<String> categories) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String category : categories) {
            result.put(category, BigDecimal.ZERO);
        }

        BigDecimal unassigned = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : canonical.entrySet()) {
            String category = entry.getKey();
            BigDecimal weight = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            if (result.containsKey(category)) {
                result.merge(category, weight, BigDecimal::add);
            } else if ("基金".equals(category) && result.containsKey("股票")) {
                result.merge("股票", weight, BigDecimal::add);
            } else {
                unassigned = unassigned.add(weight);
            }
        }
        if (unassigned.compareTo(BigDecimal.ZERO) > 0) {
            scaleToHundred(result, unassigned);
        }
        return result;
    }

    /** 把缺失类别的权重按现有比例摊回去，四舍五入的差额补到最后一项 */
    private static void scaleToHundred(Map<String, BigDecimal> targets, BigDecimal extra) {
        BigDecimal sum = targets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal factor = sum.compareTo(BigDecimal.ZERO) > 0
                ? sum.add(extra).divide(sum, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String last = null;
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : targets.entrySet()) {
            BigDecimal scaled = factor.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().multiply(factor).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(targets.size()), 1, RoundingMode.HALF_UP);
            entry.setValue(scaled);
            total = total.add(scaled);
            last = entry.getKey();
        }
        if (last != null) {
            targets.put(last, targets.get(last).add(BigDecimal.valueOf(100).subtract(total)));
        }
    }

    /** 复用总览那套分类口径，保证两处数字一致 */
    private Map<String, BigDecimal> actualAmounts(String userId) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        List<AssetCategoryPositionRes> list = stockService.getAssetCategoryPosition(userId).getData();
        if (list == null) {
            return result;
        }
        for (AssetCategoryPositionRes item : list) {
            result.merge(item.getCategoryName(),
                    item.getPositionAmount() == null ? BigDecimal.ZERO : item.getPositionAmount(),
                    BigDecimal::add);
        }
        return result;
    }

    private static Map<String, Map<String, BigDecimal>> toMap(List<PortfolioPolicy.RegimeWeights> matrix) {        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        for (PortfolioPolicy.RegimeWeights item : matrix) {
            if (item == null || item.getRegime() == null) {
                continue;
            }
            Map<String, BigDecimal> map = new LinkedHashMap<>();
            if (item.getTargets() != null) {
                for (PortfolioPolicy.CategoryTarget t : item.getTargets()) {
                    if (t != null && t.getCategory() != null && t.getTarget() != null) {
                        map.put(t.getCategory(), t.getTarget());
                    }
                }
            }
            result.put(item.getRegime(), map);
        }
        return result;
    }

    static String normalizeRegime(String regime) {
        if (regime == null) {
            return REGIME_SIDEWAYS;
        }
        String upper = regime.trim().toUpperCase();
        switch (upper) {
            case REGIME_BULL:
            case REGIME_SIDEWAYS:
            case REGIME_BEAR:
            case REGIME_RISK:
                return upper;
            default:
                return REGIME_SIDEWAYS;
        }
    }

    /** 解析结果：latest=最新一期原文，effective=实际生效档，targets=生效目标比例 */
    /** 判定器停了超过 STALE_DAYS 天就不算数，宁可退回震荡档也不要拿旧结论指挥仓位 */
    static final int STALE_DAYS = 5;

    static boolean isFresh(String date) {
        if (date == null || date.isBlank()) {
            return false;
        }
        try {
            LocalDate day = LocalDate.parse(date.trim());
            return !day.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(STALE_DAYS));
        } catch (Exception e) {
            return false;
        }
    }

    public record Resolved(String latestRegime,
                           String effectiveRegime,
                           boolean confirmed,
                           int confirmDays,
                           BigDecimal band,
                           String latestDate,
                           Map<String, BigDecimal> targets) {
    }
}
