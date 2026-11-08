package com.xian.stockai.service.exit;

import com.xian.stockai.dao.ExitEvalRunRepository;
import com.xian.stockai.dao.ExitSignalRepository;
import com.xian.stockai.dao.PositionExitPolicyRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.ExitEvalRun;
import com.xian.stockai.po.ExitSignal;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionExitPolicy;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.service.market.KlineProvider;
import com.xian.stockai.vo.ExitOverviewRes;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xian
 * @description 「今日决策」的数据装配。只读：不跑判定、不建策略、不写库。
 * <p>
 * 这里算出来的止损价、止盈价，口径必须和 {@link ExitEvaluationService} 判定时用的一致，
 * 否则会出现「页面说还没到、信号却已经触发」这种自相矛盾。所以硬止损档位直接复用
 * {@link ExitEvaluationService#resolveHardStopRate}，不在这里另写一套。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExitOverviewService {

    /**
     * 止损价离现价不到这个比例，就算「接近止损」，页面上标黄提醒
     */
    static final BigDecimal NEAR_STOP_RATIO = new BigDecimal("0.05");

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final PositionRepository positionRepository;
    private final PositionExitPolicyRepository policyRepository;
    private final ExitSignalRepository signalRepository;
    private final ExitEvalRunRepository runRepository;
    private final KlineProvider klineProvider;
    private final ExitProperties properties;
    private final ExitEvaluationService exitEvaluationService;

    public Result<ExitOverviewRes> overview(String userId) {
        if (userId == null || userId.isBlank()) {
            return Result.fail("用户ID不能为空");
        }
        ExitOverviewRes res = new ExitOverviewRes();
        List<Position> positions = positionRepository.findByUserId(userId);
        if (positions == null) {
            positions = List.of();
        }
        Map<String, PositionExitPolicy> policyBySymbol = new HashMap<>();
        List<PositionExitPolicy> policies = policyRepository.findByUserId(userId);
        if (policies != null) {
            for (PositionExitPolicy policy : policies) {
                policyBySymbol.put(policy.getSymbol(), policy);
            }
        }
        List<ExitSignal> triggered = signalRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, ExitSignal.STATUS_TRIGGERED);
        if (triggered == null) {
            triggered = List.of();
        }

        for (Position position : positions) {
            if (position.getTotalQuantity() == null || position.getTotalQuantity() <= 0) {
                continue;
            }
            ExitOverviewRes.Holding holding = buildHolding(position, policyBySymbol.get(position.getSymbol()), res);
            if (holding != null) {
                res.getHoldings().add(holding);
            }
        }
        res.setSignals(mapSignals(triggered));
        applyStatuses(res.getHoldings(), triggered);
        res.setHandledToday(countHandledToday(userId));
        fillRunInfo(userId, res);
        return Result.success(res);
    }

    private ExitOverviewRes.Holding buildHolding(Position position, PositionExitPolicy policy,
                                                 ExitOverviewRes res) {
        String symbol = position.getSymbol();
        List<StockDailyKline> klines = klineProvider.ascending(symbol);
        ExitOverviewRes.Holding holding = new ExitOverviewRes.Holding();
        holding.setSymbol(symbol);
        holding.setName(position.getStockName());
        holding.setQuantity(position.getTotalQuantity());
        holding.setCost(position.getAvgCostPrice());
        holding.setStatus("ok");
        if (policy != null) {
            holding.setEnabled(policy.getEnabled());
        }
        if (klines == null || klines.isEmpty()) {
            holding.setStatus("nodata");
            holding.setNote("无日线数据");
            holding.setReturnPct(returnPct(null, position.getAvgCostPrice()));
            return holding;
        }
        StockDailyKline last = klines.get(klines.size() - 1);
        trackTradeDate(res, last.getTradeDate());
        BigDecimal close = last.getClose();
        BigDecimal cost = position.getAvgCostPrice();
        holding.setPrice(close);
        holding.setReturnPct(returnPct(close, cost));

        BigDecimal stop = resolveStopLine(position, policy, klines, last, close, cost);
        holding.setStop(stop);
        holding.setTakeProfit(resolveTakeProfit(policy, cost));
        holding.setDistToStop(distance(stop, close));
        holding.setDistToTakeProfit(distance(holding.getTakeProfit(), close));
        if (Boolean.FALSE.equals(policy == null ? null : policy.getEnabled())) {
            holding.setStatus("ok");
            holding.setNote("卖出纪律已停用");
        }
        return holding;
    }

    /**
     * 止损线取「硬止损价」与「已武装的移动止盈价」里更高的那个——两者都是「跌到这里就该走」。
     */
    BigDecimal resolveStopLine(Position position, PositionExitPolicy policy,
                               List<StockDailyKline> klines, StockDailyKline last,
                               BigDecimal close, BigDecimal cost) {
        BigDecimal hardStop = null;
        BigDecimal rate = exitEvaluationService.resolveHardStopRate(position, policy, klines, last);
        if (cost != null && rate != null) {
            hardStop = cost.multiply(BigDecimal.ONE.subtract(rate)).setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal trailFloor = policy == null ? null : policy.getTrailFloorPrice();
        boolean armed = policy != null && Boolean.TRUE.equals(policy.getTrailArmed());
        if (armed && trailFloor != null && (hardStop == null || trailFloor.compareTo(hardStop) > 0)) {
            return trailFloor;
        }
        return hardStop;
    }

    /**
     * 下一档还没打到的止盈价：一档没打给一档价，一档打完给二档价，两档都打完返回 null。
     */
    BigDecimal resolveTakeProfit(PositionExitPolicy policy, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        boolean tp1Done = policy != null && Boolean.TRUE.equals(policy.getTp1Done());
        boolean tp2Done = policy != null && Boolean.TRUE.equals(policy.getTp2Done());
        if (tp2Done) {
            return null;
        }
        BigDecimal rate = tp1Done ? properties.getTakeProfit2Return() : properties.getTakeProfit1Return();
        return cost.multiply(BigDecimal.ONE.add(rate)).setScale(4, RoundingMode.HALF_UP);
    }

    private void applyStatuses(List<ExitOverviewRes.Holding> holdings, List<ExitSignal> triggered) {
        for (ExitOverviewRes.Holding holding : holdings) {
            if (holding.getPrice() == null) {
                continue;
            }
            boolean takeProfitSignal = false;
            boolean stopSignal = false;
            for (ExitSignal signal : triggered) {
                if (!holding.getSymbol().equals(signal.getSymbol())) {
                    continue;
                }
                if (isTakeProfit(signal.getCode())) {
                    takeProfitSignal = true;
                } else {
                    stopSignal = true;
                }
            }
            if (takeProfitSignal) {
                holding.setStatus("tp1");
            } else if (stopSignal || nearStop(holding.getDistToStop())) {
                holding.setStatus("warn");
            } else {
                holding.setStatus("ok");
            }
        }
    }

    /**
     * 现价离止损价不到 5%（或已经跌穿）就算接近止损。
     * distToStop 是「止损价 / 现价 − 1」，平时是负值：−20% 表示还要再跌 20% 才碰到。
     */
    private boolean nearStop(BigDecimal distToStop) {
        return distToStop != null && distToStop.compareTo(NEAR_STOP_RATIO.negate()) >= 0;
    }

    private List<ExitOverviewRes.Signal> mapSignals(List<ExitSignal> triggered) {
        List<ExitOverviewRes.Signal> list = new ArrayList<>();
        for (ExitSignal signal : triggered) {
            ExitOverviewRes.Signal item = new ExitOverviewRes.Signal();
            item.setId(signal.getId());
            item.setSymbol(signal.getSymbol());
            item.setName(signal.getStockName());
            item.setLevel(signal.getLevel());
            item.setCode(signal.getCode());
            item.setTag(tagOf(signal.getCode()));
            item.setTone(isTakeProfit(signal.getCode()) ? "up" : "down");
            item.setTriggerPrice(signal.getTriggerPrice());
            item.setClose(signal.getClose());
            item.setReturnPct(signal.getReturnPct());
            item.setAction(signal.getAction());
            item.setInvalidate(signal.getInvalidate());
            item.setReason(signal.getReason());
            item.setStatus(signal.getStatus());
            list.add(item);
        }
        return list;
    }

    /**
     * 页面顶部的「数据截至」取所有持仓里最新的一根日线，比运行记录更贴近实际看到的价格。
     */
    private void trackTradeDate(ExitOverviewRes res, String tradeDate) {
        if (tradeDate == null) {
            return;
        }
        if (res.getTradeDate() == null || tradeDate.compareTo(res.getTradeDate()) > 0) {
            res.setTradeDate(tradeDate);
        }
    }

    private int countHandledToday(String userId) {
        LocalDateTime startOfDay = LocalDate.now(ZONE_SHANGHAI).atStartOfDay();
        List<ExitSignal> acked = signalRepository.findByUserIdAndStatusAndAckedAtAfter(
                userId, ExitSignal.STATUS_ACKED, startOfDay);
        return acked == null ? 0 : acked.size();
    }

    private void fillRunInfo(String userId, ExitOverviewRes res) {
        List<ExitEvalRun> runs = runRepository.findTop20ByUserIdOrderByStartedAtDesc(userId);
        if (runs == null || runs.isEmpty()) {
            return;
        }
        ExitEvalRun latest = runs.get(0);
        res.setEvaluatedAt(latest.getStartedAt());
        res.setEvaluated(latest.getEvaluated());
        res.setTriggered(latest.getTriggered());
        res.setSkipped(latest.getSkipped());
        if (res.getTradeDate() == null) {
            res.setTradeDate(latest.getTradeDate());
        }
    }

    private static boolean isTakeProfit(String code) {
        return code != null && code.startsWith("TAKE_PROFIT");
    }

    private static String tagOf(String code) {
        if (code == null) {
            return "卖出提示";
        }
        switch (code) {
            case "HARD_STOP":
                return "硬止损";
            case "TRAILING_STOP":
                return "移动止盈";
            case "TAKE_PROFIT_1":
                return "止盈一档";
            case "TAKE_PROFIT_2":
                return "止盈二档";
            default:
                return code;
        }
    }

    private static BigDecimal distance(BigDecimal target, BigDecimal price) {
        if (target == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return target.divide(price, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal returnPct(BigDecimal close, BigDecimal cost) {
        if (close == null || cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return close.divide(cost, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
