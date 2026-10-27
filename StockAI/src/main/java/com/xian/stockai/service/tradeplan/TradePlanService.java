package com.xian.stockai.service.tradeplan;

import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.dao.StockIndustryConceptRepository;
import com.xian.stockai.dao.TradePlanRepository;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockIndustryConcept;
import com.xian.stockai.po.TradePlan;
import com.xian.stockai.po.Users;
import com.xian.stockai.service.MonitorService;
import com.xian.stockai.vo.TradePlanRes;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author xian
 * @description 交易计划服务：买入后生成/刷新计划，查询持仓计划
 */
@Service
@Slf4j
public class TradePlanService {

    private final PositionRepository positionRepository;
    private final StockDailyKlineRepository stockDailyKlineRepository;
    private final StockIndustryConceptRepository stockIndustryConceptRepository;
    private final UsersRepository usersRepository;
    private final TradePlanRepository tradePlanRepository;
    private final PositionMonitorRepository positionMonitorRepository;
    private final MonitorService monitorService;
    private final TradePlanCalculator tradePlanCalculator;

    public TradePlanService(PositionRepository positionRepository,
                            StockDailyKlineRepository stockDailyKlineRepository,
                            StockIndustryConceptRepository stockIndustryConceptRepository,
                            UsersRepository usersRepository,
                            TradePlanRepository tradePlanRepository,
                            PositionMonitorRepository positionMonitorRepository,
                            MonitorService monitorService,
                            TradePlanCalculator tradePlanCalculator) {
        this.positionRepository = positionRepository;
        this.stockDailyKlineRepository = stockDailyKlineRepository;
        this.stockIndustryConceptRepository = stockIndustryConceptRepository;
        this.usersRepository = usersRepository;
        this.tradePlanRepository = tradePlanRepository;
        this.positionMonitorRepository = positionMonitorRepository;
        this.monitorService = monitorService;
        this.tradePlanCalculator = tradePlanCalculator;
    }

    /**
     * 生成或刷新单股交易计划；重复买入时覆盖为新基准。
     */
    public void refreshPlan(String userId, String symbol) {
        Position position = positionRepository.findByUserIdAndSymbol(userId, symbol);
        if (position == null || position.getTotalQuantity() == null || position.getTotalQuantity() <= 0
                || position.getAvgCostPrice() == null
                || position.getAvgCostPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("交易计划跳过：持仓缺失或成本无效, userId={}, symbol={}", userId, symbol);
            return;
        }
        List<StockDailyKline> ascKlines = ascendingKlines(symbol);
        BigDecimal lastClose = monitorService.getCurrentPrice(symbol);
        String industry = resolveIndustry(symbol);
        TradePlanCalculator.CashContext cash = resolveCash(userId);

        TradePlan plan = tradePlanCalculator.calculate(cash, position, industry, ascKlines, lastClose);
        Optional<TradePlan> existing = tradePlanRepository.findByUserIdAndSymbol(userId, symbol);
        existing.ifPresent(previous -> {
            plan.setId(previous.getId());
            plan.setMonitorLinked(Boolean.TRUE.equals(previous.getMonitorLinked()));
        });
        tradePlanRepository.save(plan);
        log.debug("交易计划已生成: userId={}, symbol={}, degraded={}", userId, symbol, plan.getDegraded());
    }

    /**
     * 当前持仓的交易计划列表；缺失计划的持仓按需幂等补生成。
     */
    public Result<List<TradePlanRes>> list(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Result.fail("用户ID不能为空");
        }
        List<Position> positions = positionRepository.findByUserId(userId);
        List<TradePlan> plans = tradePlanRepository.findByUserId(userId);
        Map<String, TradePlan> planBySymbol = new HashMap<>();
        for (TradePlan plan : plans) {
            planBySymbol.put(plan.getSymbol(), plan);
        }
        List<TradePlanRes> result = new ArrayList<>();
        for (Position position : positions) {
            if (position.getTotalQuantity() == null || position.getTotalQuantity() <= 0) {
                continue;
            }
            TradePlan plan = planBySymbol.get(position.getSymbol());
            if (plan == null) {
                try {
                    refreshPlan(userId, position.getSymbol());
                    plan = tradePlanRepository.findByUserIdAndSymbol(userId, position.getSymbol()).orElse(null);
                } catch (Exception e) {
                    log.error("补生成交易计划失败: userId={}, symbol={}", userId, position.getSymbol(), e);
                }
            }
            if (plan != null) {
                result.add(TradePlanRes.from(plan));
            }
        }
        return Result.success(result);
    }

    private List<StockDailyKline> ascendingKlines(String symbol) {
        List<StockDailyKline> descKlines = stockDailyKlineRepository
                .findTop21BySymbolOrderByTradeDateDesc(symbol);
        if (descKlines == null || descKlines.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockDailyKline> ascKlines = new ArrayList<>(descKlines);
        Collections.reverse(ascKlines);
        return ascKlines;
    }

    private String resolveIndustry(String symbol) {
        try {
            StockIndustryConcept concept = stockIndustryConceptRepository.findBySymbol(symbol);
            return concept == null ? null : concept.getIndustry();
        } catch (Exception e) {
            log.warn("查询行业失败: symbol={}, error={}", symbol, e.getMessage());
            return null;
        }
    }

    private TradePlanCalculator.CashContext resolveCash(String userId) {
        try {
            Users user = usersRepository.findById(userId).orElse(null);
            if (user != null && user.getCash() != null) {
                return new TradePlanCalculator.CashContext(user.getCash(), TradePlan.CASH_BASIS_REMAINING);
            }
        } catch (Exception e) {
            log.warn("查询用户现金失败: userId={}, error={}", userId, e.getMessage());
        }
        return new TradePlanCalculator.CashContext(null, TradePlan.CASH_BASIS_UNKNOWN);
    }
}
