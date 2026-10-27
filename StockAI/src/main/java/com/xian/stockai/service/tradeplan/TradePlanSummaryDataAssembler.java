package com.xian.stockai.service.tradeplan;

import com.xian.stockai.dao.HotspotResultRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.ScreeningResultRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.dao.StockTradeRepository;
import com.xian.stockai.dao.TradePlanRepository;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.HotspotStock;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.po.TradePlan;
import com.xian.stockai.po.Users;
import com.xian.stockai.vo.TradePlanRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xian
 * @description 交易计划周报数据组装：组合概览、本周买卖、清仓复盘、逐仓计划、行业分布、关注方向
 */
@Component
@Slf4j
public class TradePlanSummaryDataAssembler {

    private static final String UNCLASSIFIED = "未分类";
    private static final int FOCUS_INDUSTRY_LIMIT = 5;
    private static final int FOCUS_STOCK_LIMIT = 5;
    private static final int BOARD_TOP_SIZE = 3;

    private final TradePlanRepository tradePlanRepository;
    private final PositionRepository positionRepository;
    private final StockTradeRepository stockTradeRepository;
    private final StockIndustryConceptRepository industryConceptRepository;
    private final HotspotResultRepository hotspotResultRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final UsersRepository usersRepository;

    public TradePlanSummaryDataAssembler(TradePlanRepository tradePlanRepository,
                                         PositionRepository positionRepository,
                                         StockTradeRepository stockTradeRepository,
                                         StockIndustryConceptRepository industryConceptRepository,
                                         HotspotResultRepository hotspotResultRepository,
                                         ScreeningResultRepository screeningResultRepository,
                                         UsersRepository usersRepository) {
        this.tradePlanRepository = tradePlanRepository;
        this.positionRepository = positionRepository;
        this.stockTradeRepository = stockTradeRepository;
        this.industryConceptRepository = industryConceptRepository;
        this.hotspotResultRepository = hotspotResultRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * 清仓个股复盘数据
     */
    public record ClearedPosition(String symbol, String stockName, BigDecimal sellQuantity,
                                  BigDecimal sellAvgPrice, BigDecimal costBasis, BigDecimal realizedPnl,
                                  BigDecimal realizedRate, String clearedDate, Integer holdDays,
                                  boolean dataMissing) {
    }

    /**
     * 行业分布行
     */
    public record IndustryRow(String industry, int symbolCount, BigDecimal cost,
                              BigDecimal costRatio, List<String> symbols) {
    }

    /**
     * 组装单个用户的周报输入数据。
     */
    public Map<String, Object> assemble(String userId, LocalDate weekStart, LocalDate weekEnd) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();
        List<Position> allPositions = positionRepository.findByUserId(userId);
        List<Position> activePositions = new ArrayList<>();
        for (Position position : allPositions) {
            if (position.getTotalQuantity() != null && position.getTotalQuantity() > 0) {
                activePositions.add(position);
            }
        }
        List<StockTrade> weekTrades = new ArrayList<>();
        for (StockTrade trade : stockTradeRepository.findByUserId(userId)) {
            if (trade.getCreatedAt() != null && !trade.getCreatedAt().isBefore(from)
                    && trade.getCreatedAt().isBefore(to)) {
                weekTrades.add(trade);
            }
        }
        weekTrades.sort(Comparator.comparing(StockTrade::getCreatedAt));

        Map<String, String> industryBySymbol = resolveIndustries(activePositions);
        List<TradePlanRes> plans = new ArrayList<>();
        for (TradePlan plan : tradePlanRepository.findByUserId(userId)) {
            plans.add(TradePlanRes.from(plan));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekStart", weekStart.toString());
        data.put("weekEnd", weekEnd.toString());
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("summary", buildSummary(userId, activePositions, plans));
        data.put("weekTrades", toWeekTradeRows(weekTrades));
        data.put("clearedPositions", buildClearedPositions(allPositions, weekTrades));
        data.put("plans", plans);
        data.put("industryDistribution", industryDistribution(activePositions, industryBySymbol));
        data.put("focusIndustries", buildFocusIndustries());
        data.put("focusStocks", buildFocusStocks());
        return data;
    }

    /**
     * 按历史交易流水回放，计算清仓个股的成本基准与已实现盈亏。
     */
    public ClearedPosition replayClearedPosition(String symbol, String stockName, List<StockTrade> trades) {
        List<StockTrade> sorted = new ArrayList<>(trades == null ? List.of() : trades);
        sorted.sort(Comparator.comparing(StockTrade::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal soldQuantity = BigDecimal.ZERO;
        BigDecimal soldCost = BigDecimal.ZERO;
        BigDecimal soldAmount = BigDecimal.ZERO;
        LocalDateTime firstBuyAt = null;
        LocalDateTime lastSellAt = null;
        boolean missing = false;
        for (StockTrade trade : sorted) {
            if (trade.getQuantity() == null || trade.getQuantity() <= 0) {
                continue;
            }
            BigDecimal qty = BigDecimal.valueOf(trade.getQuantity());
            if ("BUY".equalsIgnoreCase(trade.getSide())) {
                quantity = quantity.add(qty);
                cost = cost.add(actualOrAmount(trade));
                if (firstBuyAt == null) {
                    firstBuyAt = trade.getCreatedAt();
                }
            } else if ("SELL".equalsIgnoreCase(trade.getSide())) {
                if (quantity.compareTo(qty) < 0) {
                    missing = true;
                    break;
                }
                BigDecimal costPerShare = cost.divide(quantity, 6, RoundingMode.HALF_UP);
                BigDecimal proceeds = trade.getAmount() != null ? trade.getAmount()
                        : trade.getPrice().multiply(qty);
                cost = cost.subtract(costPerShare.multiply(qty));
                quantity = quantity.subtract(qty);
                soldQuantity = soldQuantity.add(qty);
                soldCost = soldCost.add(costPerShare.multiply(qty));
                soldAmount = soldAmount.add(proceeds);
                lastSellAt = trade.getCreatedAt();
            }
        }
        if (missing || soldQuantity.compareTo(BigDecimal.ZERO) <= 0
                || soldCost.compareTo(BigDecimal.ZERO) <= 0) {
            return new ClearedPosition(symbol, stockName, soldQuantity, null, null, null, null,
                    lastSellAt == null ? null : lastSellAt.toLocalDate().toString(), null, true);
        }
        BigDecimal costBasis = soldCost.divide(soldQuantity, 2, RoundingMode.HALF_UP);
        BigDecimal sellAvgPrice = soldAmount.divide(soldQuantity, 2, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = soldAmount.subtract(soldCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realizedRate = realizedPnl.divide(soldCost, 4, RoundingMode.HALF_UP);
        Integer holdDays = null;
        if (firstBuyAt != null && lastSellAt != null) {
            holdDays = (int) java.time.Duration.between(firstBuyAt, lastSellAt).toDays();
        }
        return new ClearedPosition(symbol, stockName, soldQuantity, sellAvgPrice, costBasis,
                realizedPnl, realizedRate,
                lastSellAt == null ? null : lastSellAt.toLocalDate().toString(), holdDays, false);
    }

    /**
     * 行业分布（按持仓成本占比降序，缺失行业归入未分类）。
     */
    public List<IndustryRow> industryDistribution(List<Position> positions, Map<String, String> industryBySymbol) {
        Map<String, BigDecimal> costByIndustry = new LinkedHashMap<>();
        Map<String, Integer> countByIndustry = new LinkedHashMap<>();
        Map<String, List<String>> symbolsByIndustry = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Position position : positions) {
            BigDecimal cost = position.getTotalCost() == null ? BigDecimal.ZERO : position.getTotalCost();
            totalCost = totalCost.add(cost);
            String industry = industryBySymbol == null ? null : industryBySymbol.get(position.getSymbol());
            if (industry == null || industry.trim().isEmpty()) {
                industry = UNCLASSIFIED;
            }
            costByIndustry.merge(industry, cost, BigDecimal::add);
            countByIndustry.merge(industry, 1, Integer::sum);
            symbolsByIndustry.computeIfAbsent(industry, key -> new ArrayList<>()).add(position.getSymbol());
        }
        List<IndustryRow> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : costByIndustry.entrySet()) {
            String industry = entry.getKey();
            BigDecimal ratio = totalCost.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : entry.getValue().divide(totalCost, 4, RoundingMode.HALF_UP);
            rows.add(new IndustryRow(industry, countByIndustry.get(industry), entry.getValue(), ratio,
                    symbolsByIndustry.get(industry)));
        }
        rows.sort(Comparator.comparing(IndustryRow::costRatio).reversed());
        return rows;
    }

    private Map<String, Object> buildSummary(String userId, List<Position> activePositions,
                                             List<TradePlanRes> plans) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        int stopLossWarnings = 0;
        int targetReached = 0;
        int degradedCount = 0;
        for (TradePlanRes plan : plans) {
            if (plan.getAvgCost() != null && plan.getQuantity() != null) {
                totalCost = totalCost.add(plan.getAvgCost().multiply(BigDecimal.valueOf(plan.getQuantity())));
            }
            if (plan.getLastClose() != null && plan.getQuantity() != null) {
                totalMarketValue = totalMarketValue
                        .add(plan.getLastClose().multiply(BigDecimal.valueOf(plan.getQuantity())));
            }
            if (plan.getDistToStop() != null && plan.getDistToStop().abs().compareTo(new BigDecimal("0.03")) < 0) {
                stopLossWarnings++;
            }
            if (plan.getDistToTarget() != null && plan.getDistToTarget().compareTo(BigDecimal.ZERO) <= 0) {
                targetReached++;
            }
            if (Boolean.TRUE.equals(plan.getDegraded())) {
                degradedCount++;
            }
        }
        BigDecimal totalFloatPnl = totalMarketValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal floatPnlRate = totalCost.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : totalFloatPnl.divide(totalCost, 4, RoundingMode.HALF_UP);
        BigDecimal cash = null;
        try {
            Users user = usersRepository.findById(userId).orElse(null);
            cash = user == null ? null : user.getCash();
        } catch (Exception e) {
            log.warn("查询用户现金失败: userId={}, error={}", userId, e.getMessage());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("positionCount", activePositions.size());
        summary.put("totalCost", totalCost.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalMarketValue", totalMarketValue.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalFloatPnl", totalFloatPnl);
        summary.put("floatPnlRate", floatPnlRate);
        summary.put("cash", cash);
        summary.put("stopLossWarnings", stopLossWarnings);
        summary.put("targetReached", targetReached);
        summary.put("degradedCount", degradedCount);
        return summary;
    }

    private List<Map<String, Object>> toWeekTradeRows(List<StockTrade> weekTrades) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockTrade trade : weekTrades) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tradeDate", trade.getCreatedAt() == null ? null : trade.getCreatedAt().toLocalDate().toString());
            row.put("side", trade.getSide());
            row.put("symbol", trade.getSymbol());
            row.put("stockName", trade.getStockName());
            row.put("quantity", trade.getQuantity());
            row.put("price", trade.getPrice());
            row.put("actualAmount", trade.getActualAmount());
            row.put("buyMethod", trade.getBuyMethod());
            BigDecimal fees = BigDecimal.ZERO;
            if (trade.getCommission() != null) {
                fees = fees.add(trade.getCommission());
            }
            if (trade.getTransferFee() != null) {
                fees = fees.add(trade.getTransferFee());
            }
            if (trade.getStampTax() != null) {
                fees = fees.add(trade.getStampTax());
            }
            row.put("fees", fees.setScale(2, RoundingMode.HALF_UP));
            rows.add(row);
        }
        return rows;
    }

    private List<ClearedPosition> buildClearedPositions(List<Position> allPositions, List<StockTrade> weekTrades) {
        List<ClearedPosition> cleared = new ArrayList<>();
        Map<String, List<StockTrade>> tradesBySymbol = new LinkedHashMap<>();
        for (StockTrade trade : weekTrades) {
            tradesBySymbol.computeIfAbsent(trade.getSymbol(), key -> new ArrayList<>()).add(trade);
        }
        for (Position position : allPositions) {
            if (position.getTotalQuantity() != null && position.getTotalQuantity() > 0) {
                continue;
            }
            List<StockTrade> symbolTrades = tradesBySymbol.get(position.getSymbol());
            if (symbolTrades == null || symbolTrades.isEmpty()) {
                continue;
            }
            boolean hasSell = symbolTrades.stream().anyMatch(t -> "SELL".equalsIgnoreCase(t.getSide()));
            if (!hasSell) {
                continue;
            }
            List<StockTrade> history = stockTradeRepository.findByUserIdAndSymbol(
                    position.getUserId(), position.getSymbol());
            cleared.add(replayClearedPosition(position.getSymbol(), position.getStockName(), history));
        }
        return cleared;
    }

    private Map<String, String> resolveIndustries(List<Position> positions) {
        Map<String, String> industryBySymbol = new LinkedHashMap<>();
        for (Position position : positions) {
            try {
                StockIndustryConcept concept = industryConceptRepository.findBySymbol(position.getSymbol());
                if (concept != null && concept.getIndustry() != null && !concept.getIndustry().isEmpty()) {
                    industryBySymbol.put(position.getSymbol(), concept.getIndustry());
                }
            } catch (Exception e) {
                log.warn("查询行业失败: symbol={}, error={}", position.getSymbol(), e.getMessage());
            }
        }
        return industryBySymbol;
    }

    private List<Map<String, Object>> buildFocusIndustries() {
        List<Map<String, Object>> rows = new ArrayList<>();
        HotspotResult hotspot = hotspotResultRepository.findTopByOrderByTradeDateDesc();
        if (hotspot == null || hotspot.getIndustries() == null) {
            return rows;
        }
        int limit = Math.min(FOCUS_INDUSTRY_LIMIT, hotspot.getIndustries().size());
        for (HotspotDirection direction : hotspot.getIndustries().subList(0, limit)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", direction.getName());
            row.put("hotScore", direction.getHotScore());
            row.put("hypeLabel", direction.getHypeLabel());
            row.put("hypeBasis", direction.getHypeBasis());
            List<String> stocks = new ArrayList<>();
            if (direction.getTopStocks() != null) {
                for (HotspotStock stock : direction.getTopStocks()) {
                    stocks.add(stock.getName() + "(" + stock.getSymbol() + ")");
                }
            }
            row.put("topStocks", stocks);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildFocusStocks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        ScreeningResult screening = screeningResultRepository.findTopByOrderByTradeDateDesc();
        if (screening == null) {
            return rows;
        }
        appendBoard(rows, "均衡", screening.getBalancedBoard());
        appendBoard(rows, "价值", screening.getValueBoard());
        appendBoard(rows, "成长", screening.getGrowthBoard());
        return rows.size() > FOCUS_STOCK_LIMIT ? new ArrayList<>(rows.subList(0, FOCUS_STOCK_LIMIT)) : rows;
    }

    private void appendBoard(List<Map<String, Object>> rows, String board, List<BoardEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        int limit = Math.min(BOARD_TOP_SIZE, entries.size());
        for (BoardEntry entry : entries.subList(0, limit)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", entry.getSymbol());
            row.put("name", entry.getName());
            row.put("totalScore", entry.getTotalScore());
            row.put("board", board);
            row.put("reason", entry.getReason());
            rows.add(row);
        }
    }

    private BigDecimal actualOrAmount(StockTrade trade) {
        if (trade.getActualAmount() != null) {
            return trade.getActualAmount();
        }
        if (trade.getAmount() != null) {
            return trade.getAmount();
        }
        return trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
    }
}
