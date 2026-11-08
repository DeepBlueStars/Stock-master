package com.xian.stockai.service.exit;

import com.xian.stockai.dao.ExitEvalRunRepository;
import com.xian.stockai.dao.ExitSignalRepository;
import com.xian.stockai.dao.PositionExitPolicyRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.StockTradeRepository;
import com.xian.stockai.po.ExitEvalRun;
import com.xian.stockai.po.ExitSignal;
import com.xian.stockai.po.Notification;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionExitPolicy;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.service.market.KlineProvider;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.service.tradeplan.IndicatorCalculator;
import com.xian.stockai.vo.ExitEvaluationRes;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author xian
 * @description 卖出纪律判定：把持仓、日线、交易流水与策略状态拼成 {@link ExitRuleEngine} 的输入，
 * 落库信号、回写状态，并做冷却与加仓缓冲。
 * <p>
 * 设计取舍：这里只「记账 + 提醒」，绝不自动下单。真正的卖出动作由用户在券商端完成，
 * 或由外部助手（MCP）读取信号后决定。
 */
@Service
@Slf4j
public class ExitEvaluationService {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final PositionRepository positionRepository;
    private final StockTradeRepository stockTradeRepository;
    private final PositionExitPolicyRepository policyRepository;
    private final ExitSignalRepository signalRepository;
    private final ExitEvalRunRepository runRepository;
    private final KlineProvider klineProvider;
    private final ExitProperties properties;
    private final IndicatorCalculator indicatorCalculator;
    private final List<NotificationSender> notificationSenders;
    /** 判定用的「今天」，测试可替换 */
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public ExitEvaluationService(PositionRepository positionRepository,
                                 StockTradeRepository stockTradeRepository,
                                 PositionExitPolicyRepository policyRepository,
                                 ExitSignalRepository signalRepository,
                                 ExitEvalRunRepository runRepository,
                                 KlineProvider klineProvider,
                                 ExitProperties properties,
                                 IndicatorCalculator indicatorCalculator,
                                 List<NotificationSender> notificationSenders) {
        this.positionRepository = positionRepository;
        this.stockTradeRepository = stockTradeRepository;
        this.policyRepository = policyRepository;
        this.signalRepository = signalRepository;
        this.runRepository = runRepository;
        this.klineProvider = klineProvider;
        this.properties = properties;
        this.indicatorCalculator = indicatorCalculator;
        this.notificationSenders = notificationSenders;
    }

    /**
     * 对某个用户的全部持仓跑一次判定
     *
     * @param triggerSource TASK / MANUAL / MCP
     */
    public Result<ExitEvaluationRes> evaluate(String userId, String triggerSource) {
        if (userId == null || userId.isBlank()) {
            return Result.fail("用户ID不能为空");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        ExitEvalRun run = new ExitEvalRun();
        run.setUserId(userId);
        run.setTriggerSource(triggerSource);
        run.setStartedAt(startedAt);
        run.setEvaluated(0);
        run.setTriggered(0);
        run.setSkipped(0);

        ExitEvaluationRes res = new ExitEvaluationRes();
        List<Position> positions = positionRepository.findByUserId(userId);
        if (positions == null) {
            positions = List.of();
        }

        for (Position position : positions) {
            if (position.getTotalQuantity() == null || position.getTotalQuantity() <= 0) {
                continue;
            }
            try {
                ExitSignal signal = evaluateOne(position, res);
                run.setEvaluated(run.getEvaluated() + 1);
                if (signal != null) {
                    run.setTriggered(run.getTriggered() + 1);
                    res.getSignals().add(signal);
                }
            } catch (Exception e) {
                log.error("卖出纪律判定失败: user={}, symbol={}", userId, position.getSymbol(), e);
                run.getErrors().add(position.getSymbol() + ": " + e.getMessage());
            }
        }

        run.setTradeDate(res.getTradeDate());
        run.setFinishedAt(LocalDateTime.now());
        run.setSkipped(res.getSkipped());
        run.setSkippedReasons(res.getSkippedReasons());
        ExitEvalRun saved = runRepository.save(run);
        res.setRunId(saved.getId());
        res.setEvaluated(run.getEvaluated());
        res.setTriggered(run.getTriggered());
        return Result.success(res);
    }

    /**
     * 清仓后又重新买回来时，移动止盈的峰值/武装状态不能跨轮次继承，
     * 否则上一轮的高点会让新仓一开盘就处于「已武装」并立刻触发回撤。
     */
    private void resetOnReentry(PositionExitPolicy policy, Holding holding) {
        if (holding.entryDate == null || policy.getEntryDate() == null) {
            return;
        }
        if (holding.entryDate.equals(policy.getEntryDate())) {
            return;
        }
        policy.setTrailArmed(false);
        policy.setTrailPeakClose(null);
        policy.setTrailFloorPrice(null);
        policy.setTp1Done(false);
        policy.setTp2Done(false);
        policy.setOriginalQuantity(null);
        policy.setPreAddAvgCost(null);
        policy.setLastAddTradeTime(null);
    }

    /**
     * 判定单只持仓，返回本次新产生的信号（没有则 null）
     */
    ExitSignal evaluateOne(Position position, ExitEvaluationRes res) {
        String userId = position.getUserId();
        String symbol = position.getSymbol();
        List<StockDailyKline> klines = klineProvider.ascending(symbol);
        if (klines.isEmpty()) {
            res.getSkippedReasons().add(symbol + ": 无日线数据");
            res.setSkipped(res.getSkipped() + 1);
            return null;
        }
        StockDailyKline last = klines.get(klines.size() - 1);
        if (res.getTradeDate() == null || last.getTradeDate() == null
                || last.getTradeDate().compareTo(res.getTradeDate()) > 0) {
            res.setTradeDate(last.getTradeDate());
        }
        // 严格守卫：日线必须更新到当天，否则宁可今天不判，也不拿昨天的收盘价冒充今天
        String today = LocalDate.now(clock).toString();
        if (!today.equals(last.getTradeDate())) {
            res.getSkippedReasons().add(symbol + ": 数据未更新，最新 " + last.getTradeDate());
            res.setSkipped(res.getSkipped() + 1);
            return null;
        }

        PositionExitPolicy policy = loadOrCreatePolicy(userId, position);
        if (Boolean.FALSE.equals(policy.getEnabled())) {
            res.getSkippedReasons().add(symbol + ": 已停用卖出纪律");
            res.setSkipped(res.getSkipped() + 1);
            return null;
        }
        Holding holding = replay(position, stockTradeRepository.findByUserIdAndSymbol(userId, symbol), klines.size());
        resetOnReentry(policy, holding);

        List<StockDailyKline> episode = episodeKlines(klines, holding, policy);
        if (episode.isEmpty()) {
            res.getSkippedReasons().add(symbol + ": 建仓以来无日线");
            res.setSkipped(res.getSkipped() + 1);
            return null;
        }
        BigDecimal peak = episode.stream()
                .map(StockDailyKline::getClose)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(last.getClose());
        if (policy.getTrailPeakClose() == null || peak.compareTo(policy.getTrailPeakClose()) > 0) {
            policy.setTrailPeakClose(peak);
        }

        BigDecimal hardStopRate = resolveHardStopRate(position, policy, klines, last);
        BigDecimal anchorCost = resolveTrailAnchorCost(policy, holding, klines, last);

        ExitRuleEngine.Input input = new ExitRuleEngine.Input(
                position.getAvgCostPrice(),
                last.getClose(),
                policy.getTrailPeakClose(),
                hardStopRate,
                anchorCost,
                position.getTotalQuantity(),
                policy.getOriginalQuantity() == null ? position.getTotalQuantity() : policy.getOriginalQuantity(),
                Boolean.TRUE.equals(policy.getTrailArmed()),
                Boolean.TRUE.equals(policy.getTp1Done()),
                Boolean.TRUE.equals(policy.getTp2Done()),
                params());
        ExitRuleEngine.Result result = ExitRuleEngine.evaluate(input);

        policy.setHardStopRate(hardStopRate);
        policy.setTrailArmed(result.trailArmed());
        policy.setTrailFloorPrice(result.trailFloor());
        policy.setTrailPeakClose(result.peakClose());
        policy.setTp1Done(result.tp1Done());
        policy.setTp2Done(result.tp2Done());
        policy.setUpdatedAt(LocalDateTime.now());
        policyRepository.save(policy);

        if (result.signals().isEmpty()) {
            recoverStale(userId, symbol, klines);
            return null;
        }
        ExitRuleEngine.Signal draft = result.signals().get(0);
        if (inCooldown(userId, symbol, draft.code(), last.getTradeDate(), klines)) {
            res.getSkippedReasons().add(symbol + ": " + draft.code() + " 在冷却期内");
            res.setSkipped(res.getSkipped() + 1);
            return null;
        }

        ExitSignal signal = new ExitSignal();
        signal.setUserId(userId);
        signal.setSymbol(symbol);
        signal.setStockName(position.getStockName());
        signal.setLevel(draft.level());
        signal.setCode(draft.code());
        signal.setStatus(ExitSignal.STATUS_TRIGGERED);
        signal.setTradeDate(last.getTradeDate());
        signal.setClose(last.getClose());
        signal.setAvgCost(position.getAvgCostPrice());
        signal.setReturnPct(returnPct(last.getClose(), position.getAvgCostPrice()));
        signal.setTriggerPrice(draft.triggerPrice());
        signal.setAction(draft.action());
        signal.setSuggestQuantity(draft.suggestQuantity());
        signal.setReason(draft.reason());
        signal.setInvalidate(draft.invalidate());
        signal.setCreatedAt(LocalDateTime.now());
        signal.setUpdatedAt(LocalDateTime.now());
        ExitSignal saved = signalRepository.save(signal);
        notifyIfEnabled(saved);
        return saved;
    }

    private PositionExitPolicy loadOrCreatePolicy(String userId, Position position) {
        PositionExitPolicy policy = policyRepository.findByUserIdAndSymbol(userId, position.getSymbol()).orElse(null);
        if (policy != null) {
            return policy;
        }
        policy = new PositionExitPolicy();
        policy.setUserId(userId);
        policy.setSymbol(position.getSymbol());
        policy.setStockName(position.getStockName());
        policy.setEnabled(true);
        boolean etf = KlineProvider.isEtf(position.getSymbol());
        policy.setEtf(etf);
        if (!etf) {
            // ETF 的硬止损在首次判定时按波动率落档；这里先写死 0.25 会被滞回当成"历史档位"锁住
            policy.setHardStopRate(properties.getHardStopRate());
        }
        policy.setTrailingArmReturn(properties.getTrailingArmReturn());
        policy.setTrailingRate(properties.getTrailingRate());
        policy.setBreakevenReturn(properties.getBreakevenReturn());
        policy.setTakeProfit1Return(properties.getTakeProfit1Return());
        policy.setTakeProfit1Fraction(properties.getTakeProfit1Fraction());
        policy.setTakeProfit2Return(properties.getTakeProfit2Return());
        policy.setTakeProfit2Fraction(properties.getTakeProfit2Fraction());
        policy.setAddBufferTradeDays(properties.getAddBufferTradeDays());
        policy.setTrailArmed(false);
        policy.setTp1Done(false);
        policy.setTp2Done(false);
        policy.setOriginalQuantity(position.getTotalQuantity());
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }

    /**
     * 从交易流水重放出本轮建仓的起始日、最大持仓、最近一次加仓时间与加仓前均价
     */
    Holding replay(Position position, List<StockTrade> trades, int klineCount) {
        Holding holding = new Holding();
        if (trades == null || trades.isEmpty()) {
            holding.entryDate = null;
            holding.maxQuantity = position.getTotalQuantity() == null ? 0 : position.getTotalQuantity();
            return holding;
        }
        List<StockTrade> sorted = new ArrayList<>(trades);
        sorted.sort(Comparator.comparing(StockTrade::getTradeTime, Comparator.nullsLast(Comparator.naturalOrder())));
        int qty = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (StockTrade trade : sorted) {
            Integer q = trade.getQuantity();
            if (q == null || q <= 0) {
                continue;
            }
            boolean buy = "买入".equals(trade.getSide());
            if (buy) {
                if (qty > 0) {
                    BigDecimal avg = cost.divide(BigDecimal.valueOf(qty), 6, RoundingMode.HALF_UP);
                    holding.lastAddTime = trade.getTradeTime();
                    holding.preAddAvgCost = avg;
                } else {
                    holding.entryDate = toDateString(trade.getTradeTime());
                }
                BigDecimal amount = trade.getActualAmount() != null ? trade.getActualAmount() : trade.getAmount();
                if (amount == null && trade.getPrice() != null) {
                    amount = trade.getPrice().multiply(BigDecimal.valueOf(q));
                }
                cost = cost.add(amount == null ? BigDecimal.ZERO : amount);
                qty += q;
                holding.maxQuantity = Math.max(holding.maxQuantity, qty);
            } else {
                if (qty > 0) {
                    BigDecimal avg = cost.divide(BigDecimal.valueOf(qty), 6, RoundingMode.HALF_UP);
                    cost = cost.subtract(avg.multiply(BigDecimal.valueOf(Math.min(q, qty))));
                    qty = Math.max(0, qty - q);
                    if (qty == 0) {
                        holding.entryDate = null;
                        holding.maxQuantity = 0;
                        holding.lastAddTime = null;
                        holding.preAddAvgCost = null;
                    }
                }
            }
        }
        holding.klineCount = klineCount;
        return holding;
    }

    /**
     * 建仓以来的日线切片：起始日为空（流水缺失）时退化为全部历史
     */
    private List<StockDailyKline> episodeKlines(List<StockDailyKline> klines, Holding holding,
                                                PositionExitPolicy policy) {
        String start = holding.entryDate != null ? holding.entryDate : policy.getEntryDate();
        if (start != null) {
            policy.setEntryDate(start);
        }
        List<StockDailyKline> episode = new ArrayList<>();
        for (StockDailyKline kline : klines) {
            if (start == null || kline.getTradeDate() == null || kline.getTradeDate().compareTo(start) >= 0) {
                episode.add(kline);
            }
        }
        return episode;
    }

    /**
     * 加仓缓冲期内，移动止盈仍以加仓前均价为锚；超过缓冲期则用最新均价
     */
    private BigDecimal resolveTrailAnchorCost(PositionExitPolicy policy, Holding holding,
                                              List<StockDailyKline> klines, StockDailyKline last) {
        if (holding.lastAddTime == null || holding.preAddAvgCost == null) {
            return null;
        }
        int bufferDays = policy.getAddBufferTradeDays() == null
                ? properties.getAddBufferTradeDays() : policy.getAddBufferTradeDays();
        String addDate = toDateString(holding.lastAddTime);
        long days = tradingDaysAfter(klines, addDate);
        return days < bufferDays ? holding.preAddAvgCost : null;
    }

    private long tradingDaysAfter(List<StockDailyKline> klines, String date) {
        if (date == null) {
            return Long.MAX_VALUE;
        }
        long count = 0;
        for (StockDailyKline kline : klines) {
            if (kline.getTradeDate() != null && kline.getTradeDate().compareTo(date) > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * ETF 按 60 日年化波动率分档；个股按 ATR/现价是否放大决定是否放宽
     */
    /**
     * ETF 硬止损分档，带滞回：σ 要越过档位边界一个带宽才换档，
     * 避免波动率在边界附近抖动时止损线来回跳。currentRate 为空表示首次判定。
     */
    BigDecimal resolveEtfHardStopRate(BigDecimal currentRate, BigDecimal sigma) {
        List<ExitProperties.EtfHardStopTier> tiers = properties.getEtfHardStopTiers();
        if (tiers == null || tiers.isEmpty()) {
            return properties.getHardStopRate();
        }
        if (sigma == null) {
            return currentRate != null ? currentRate : tiers.get(tiers.size() - 1).getHardStopRate();
        }
        int raw = rawTierIndex(tiers, sigma);
        int current = tierIndexOfRate(tiers, currentRate);
        int target = current < 0 ? raw : keepOrSwitch(tiers, current, raw, sigma);
        return tiers.get(target).getHardStopRate();
    }

    private int rawTierIndex(List<ExitProperties.EtfHardStopTier> tiers, BigDecimal sigma) {
        for (int i = 0; i < tiers.size(); i++) {
            BigDecimal upper = tiers.get(i).getMaxVolatility();
            if (upper == null || sigma.compareTo(upper) <= 0) {
                return i;
            }
        }
        return tiers.size() - 1;
    }

    private int tierIndexOfRate(List<ExitProperties.EtfHardStopTier> tiers, BigDecimal rate) {
        if (rate == null) {
            return -1;
        }
        for (int i = 0; i < tiers.size(); i++) {
            BigDecimal tierRate = tiers.get(i).getHardStopRate();
            if (tierRate != null && tierRate.compareTo(rate) == 0) {
                return i;
            }
        }
        return -1;
    }

    private int keepOrSwitch(List<ExitProperties.EtfHardStopTier> tiers, int current, int raw, BigDecimal sigma) {
        if (raw == current) {
            return current;
        }
        BigDecimal margin = properties.getEtfHardStopHysteresis() == null
                ? BigDecimal.ZERO : properties.getEtfHardStopHysteresis();
        boolean looser = raw > current;
        BigDecimal boundary = looser
                ? tiers.get(raw - 1).getMaxVolatility()
                : tiers.get(current - 1).getMaxVolatility();
        if (boundary == null) {
            return raw;
        }
        boolean crossed = looser
                ? sigma.compareTo(boundary.add(margin)) > 0
                : sigma.compareTo(boundary.subtract(margin)) < 0;
        return crossed ? raw : current;
    }

    /**
     * 当前该用哪一档硬止损。policy 可以为空（还没判定过的持仓），此时按最新波动率现算，
     * 不落库、也不创建策略——「今日决策」页要能看，但不能因为有人刷新页面就写库。
     */
    public BigDecimal resolveHardStopRate(Position position, PositionExitPolicy policy,
                                          List<StockDailyKline> klines, StockDailyKline last) {
        boolean etf = (policy != null && Boolean.TRUE.equals(policy.getEtf()))
                || KlineProvider.isEtf(position.getSymbol());
        if (etf) {
            BigDecimal current = policy == null ? null : policy.getHardStopRate();
            return resolveEtfHardStopRate(current, annualizedVolatility(klines, 60));
        }
        BigDecimal atr = indicatorCalculator.atr(klines, 14);
        BigDecimal close = last.getClose();
        if (atr != null && close != null && close.compareTo(BigDecimal.ZERO) > 0
                && atr.divide(close, 6, RoundingMode.HALF_UP).compareTo(properties.getHardStopRelaxAtrRatio()) > 0) {
            return properties.getHardStopRelaxedRate();
        }
        return properties.getHardStopRate();
    }

    /**
     * 最近 window 根日线的年化波动率，样本不足返回 null
     */
    static BigDecimal annualizedVolatility(List<StockDailyKline> klines, int window) {
        if (klines.size() < window + 1) {
            return null;
        }
        List<StockDailyKline> recent = klines.subList(klines.size() - window - 1, klines.size());
        double[] returns = new double[window];
        for (int i = 1; i <= window; i++) {
            double prev = recent.get(i - 1).getClose().doubleValue();
            double curr = recent.get(i).getClose().doubleValue();
            if (prev <= 0) {
                return null;
            }
            returns[i - 1] = Math.log(curr / prev);
        }
        double mean = 0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.length;
        double variance = 0;
        for (double r : returns) {
            variance += (r - mean) * (r - mean);
        }
        variance /= (returns.length - 1);
        return BigDecimal.valueOf(Math.sqrt(variance) * Math.sqrt(252))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private boolean inCooldown(String userId, String symbol, String code, String tradeDate,
                               List<StockDailyKline> klines) {
        List<ExitSignal> recent = signalRepository.findTop20ByUserIdAndSymbolAndCodeOrderByCreatedAtDesc(
                userId, symbol, code);
        if (recent == null || recent.isEmpty()) {
            return false;
        }
        ExitSignal latest = recent.get(0);
        if (!ExitSignal.STATUS_TRIGGERED.equals(latest.getStatus())) {
            return false;
        }
        String since = latest.getTradeDate();
        if (since == null) {
            return true;
        }
        int cooldown = properties.getSignalCooldownTradeDays();
        return tradingDaysAfter(klines, since) < cooldown;
    }

    /**
     * 条件已消失且超过冷却期的历史信号，自动置为 RECOVERED，避免列表里堆着过期提醒
     */
    private void recoverStale(String userId, String symbol, List<StockDailyKline> klines) {
        List<ExitSignal> triggered = signalRepository.findByUserIdAndSymbolAndStatus(
                userId, symbol, ExitSignal.STATUS_TRIGGERED);
        if (triggered == null || triggered.isEmpty()) {
            return;
        }
        int cooldown = properties.getSignalCooldownTradeDays();
        for (ExitSignal signal : triggered) {
            if (signal.getTradeDate() != null && tradingDaysAfter(klines, signal.getTradeDate()) >= cooldown) {
                signal.setStatus(ExitSignal.STATUS_RECOVERED);
                signal.setUpdatedAt(LocalDateTime.now());
                signalRepository.save(signal);
            }
        }
    }

    private void notifyIfEnabled(ExitSignal signal) {
        if (!properties.isNotifyEnabled() || notificationSenders.isEmpty()) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(signal.getUserId());
        notification.setType("EXIT_SIGNAL");
        notification.setSymbol(signal.getSymbol());
        notification.setStockName(signal.getStockName());
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setContent(String.format("【卖出提醒·%s】%s(%s) %s：%s",
                signal.getLevel(), signal.getStockName(), signal.getSymbol(), signal.getAction(), signal.getReason()));
        for (NotificationSender sender : notificationSenders) {
            try {
                sender.send(notification);
            } catch (Exception e) {
                log.warn("卖出信号通知发送失败: {}", e.getMessage());
            }
        }
    }

    private ExitRuleEngine.Params params() {
        return new ExitRuleEngine.Params(
                properties.getTrailingArmReturn(),
                properties.getTrailingRate(),
                properties.getBreakevenReturn(),
                properties.getTakeProfit1Return(),
                properties.getTakeProfit1Fraction(),
                properties.getTakeProfit2Return(),
                properties.getTakeProfit2Fraction());
    }

    private static BigDecimal returnPct(BigDecimal close, BigDecimal cost) {
        if (close == null || cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return close.divide(cost, 6, RoundingMode.HALF_UP).subtract(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static String toDateString(LocalDateTime time) {
        return time == null ? null : time.toLocalDate().toString();
    }

    static LocalDate parseDate(String date) {
        try {
            return date == null ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public Result<List<ExitSignal>> listSignals(String userId, String status) {
        if (userId == null || userId.isBlank()) {
            return Result.fail("用户ID不能为空");
        }
        if (status == null || status.isBlank()) {
            return Result.success(signalRepository.findByUserIdOrderByCreatedAtDesc(userId));
        }
        return Result.success(signalRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status));
    }

    public Result<ExitSignal> ack(String userId, String signalId, String note) {
        if (signalId == null || signalId.isBlank()) {
            return Result.fail("信号ID不能为空");
        }
        ExitSignal signal = signalRepository.findById(signalId).orElse(null);
        if (signal == null) {
            return Result.fail("信号不存在");
        }
        if (userId != null && !userId.isBlank() && !userId.equals(signal.getUserId())) {
            return Result.fail("信号不属于该用户");
        }
        signal.setStatus(ExitSignal.STATUS_ACKED);
        signal.setAckedAt(LocalDateTime.now());
        signal.setUpdatedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            signal.setNote(note);
        }
        return Result.success(signalRepository.save(signal));
    }

    public Result<List<PositionExitPolicy>> listPolicies(String userId) {
        if (userId == null || userId.isBlank()) {
            return Result.fail("用户ID不能为空");
        }
        return Result.success(policyRepository.findByUserId(userId));
    }

    public Result<List<ExitEvalRun>> listRuns(String userId) {
        if (userId == null || userId.isBlank()) {
            return Result.success(runRepository.findTop20ByOrderByStartedAtDesc());
        }
        return Result.success(runRepository.findTop20ByUserIdOrderByStartedAtDesc(userId));
    }

    /**
     * 新建或修改某只持仓的卖出纪律。只接受策略参数，状态字段（是否武装、已减几档）由判定任务维护。
     */
    public Result<PositionExitPolicy> savePolicy(String userId, PositionExitPolicy request) {
        if (userId == null || userId.isBlank()) {
            return Result.fail("用户ID不能为空");
        }
        if (request == null || request.getSymbol() == null || request.getSymbol().isBlank()) {
            return Result.fail("股票代码不能为空");
        }
        PositionExitPolicy policy = policyRepository.findByUserIdAndSymbol(userId, request.getSymbol())
                .orElseGet(() -> {
                    PositionExitPolicy created = new PositionExitPolicy();
                    created.setUserId(userId);
                    created.setSymbol(request.getSymbol());
                    created.setStockName(request.getStockName());
                    created.setEtf(KlineProvider.isEtf(request.getSymbol()));
                    created.setTrailArmed(false);
                    created.setTp1Done(false);
                    created.setTp2Done(false);
                    created.setCreatedAt(LocalDateTime.now());
                    if (!Boolean.TRUE.equals(created.getEtf())) {
                        created.setHardStopRate(properties.getHardStopRate());
                    }
                    created.setTrailingArmReturn(properties.getTrailingArmReturn());
                    created.setTrailingRate(properties.getTrailingRate());
                    created.setBreakevenReturn(properties.getBreakevenReturn());
                    created.setTakeProfit1Return(properties.getTakeProfit1Return());
                    created.setTakeProfit1Fraction(properties.getTakeProfit1Fraction());
                    created.setTakeProfit2Return(properties.getTakeProfit2Return());
                    created.setTakeProfit2Fraction(properties.getTakeProfit2Fraction());
                    created.setAddBufferTradeDays(properties.getAddBufferTradeDays());
                    return created;
                });
        if (request.getStockName() != null && !request.getStockName().isBlank()) {
            policy.setStockName(request.getStockName());
        }
        if (request.getEnabled() != null) {
            policy.setEnabled(request.getEnabled());
        }
        if (request.getHardStopRate() != null) {
            policy.setHardStopRate(request.getHardStopRate());
        }
        if (request.getTrailingArmReturn() != null) {
            policy.setTrailingArmReturn(request.getTrailingArmReturn());
        }
        if (request.getTrailingRate() != null) {
            policy.setTrailingRate(request.getTrailingRate());
        }
        if (request.getBreakevenReturn() != null) {
            policy.setBreakevenReturn(request.getBreakevenReturn());
        }
        if (request.getTakeProfit1Return() != null) {
            policy.setTakeProfit1Return(request.getTakeProfit1Return());
        }
        if (request.getTakeProfit2Return() != null) {
            policy.setTakeProfit2Return(request.getTakeProfit2Return());
        }
        if (request.getAddBufferTradeDays() != null) {
            policy.setAddBufferTradeDays(request.getAddBufferTradeDays());
        }
        policy.setUpdatedAt(LocalDateTime.now());
        return Result.success(policyRepository.save(policy));
    }

    /**
     * 从交易流水重放出来的持仓状态
     */
    static class Holding {
        String entryDate;
        int maxQuantity;
        LocalDateTime lastAddTime;
        BigDecimal preAddAvgCost;
        int klineCount;
    }
}
