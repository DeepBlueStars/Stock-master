package com.xian.stockai.service;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.MarketQuotes;
import com.xian.stockai.po.Notification;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.vo.MonitorSaveRequest;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class MonitorService {
    public static final String DIR_NONE = "NONE";
    public static final String DIR_TAKE_PROFIT = "TAKE_PROFIT";
    public static final String DIR_STOP_LOSS = "STOP_LOSS";
    public static final String TYPE_PRICE = "PRICE";
    public static final String TYPE_RATE = "RATE";

    private final PositionMonitorRepository positionMonitorRepository;
    private final PositionRepository positionRepository;
    private final MarketQuotesRepository marketQuotesRepository;
    private final EtfDailyQuotesRepository etfDailyQuotesRepository;
    private final List<NotificationSender> notificationSenders;

    public MonitorService(PositionMonitorRepository positionMonitorRepository,
                          PositionRepository positionRepository,
                          MarketQuotesRepository marketQuotesRepository,
                          EtfDailyQuotesRepository etfDailyQuotesRepository,
                          List<NotificationSender> notificationSenders) {
        this.positionMonitorRepository = positionMonitorRepository;
        this.positionRepository = positionRepository;
        this.marketQuotesRepository = marketQuotesRepository;
        this.etfDailyQuotesRepository = etfDailyQuotesRepository;
        this.notificationSenders = notificationSenders;
    }

    public Result<PositionMonitor> save(MonitorSaveRequest req) {
        if (req.getUserId() == null || req.getUserId().isEmpty()) {
            return Result.fail("用户ID不能为空");
        }
        if (req.getSymbol() == null || req.getSymbol().isEmpty()) {
            return Result.fail("股票代码不能为空");
        }
        if (req.getTakeProfitType() != null && !req.getTakeProfitType().isEmpty()
                && !isValidType(req.getTakeProfitType())) {
            return Result.fail("止盈类型无效");
        }
        if (req.getStopLossType() != null && !req.getStopLossType().isEmpty()
                && !isValidType(req.getStopLossType())) {
            return Result.fail("止损类型无效");
        }
        boolean hasTakeProfit = req.getTakeProfitType() != null && !req.getTakeProfitType().isEmpty()
                && req.getTakeProfitValue() != null;
        boolean hasStopLoss = req.getStopLossType() != null && !req.getStopLossType().isEmpty()
                && req.getStopLossValue() != null;
        if (!hasTakeProfit && !hasStopLoss) {
            return Result.fail("止盈或止损至少设置一项");
        }

        PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(req.getUserId(), req.getSymbol());
        if (monitor == null) {
            monitor = new PositionMonitor();
            monitor.setUserId(req.getUserId());
            monitor.setSymbol(req.getSymbol());
            monitor.setEnabled(true);
            monitor.setLastTriggerDirection(DIR_NONE);
            monitor.setCreatedAt(LocalDateTime.now());
        }
        monitor.setTakeProfitType(req.getTakeProfitType());
        monitor.setTakeProfitValue(req.getTakeProfitValue());
        monitor.setStopLossType(req.getStopLossType());
        monitor.setStopLossValue(req.getStopLossValue());

        Position position = positionRepository.findByUserIdAndSymbol(req.getUserId(), req.getSymbol());
        if (position != null) {
            monitor.setStockName(position.getStockName());
            monitor.setType(position.getType());
        }
        monitor.setUpdatedAt(LocalDateTime.now());
        positionMonitorRepository.save(monitor);
        return Result.success(monitor);
    }

    public Result<List<PositionMonitor>> list(String userId) {
        return Result.success(positionMonitorRepository.findByUserId(userId));
    }

    public Result<Void> delete(String userId, String symbol) {
        PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(userId, symbol);
        if (monitor == null) {
            return Result.fail("监控规则不存在");
        }
        positionMonitorRepository.delete(monitor);
        return Result.success();
    }

    public Result<Void> enable(String userId, String symbol, boolean enabled) {
        PositionMonitor monitor = positionMonitorRepository.findByUserIdAndSymbol(userId, symbol);
        if (monitor == null) {
            return Result.fail("监控规则不存在");
        }
        monitor.setEnabled(enabled);
        monitor.setUpdatedAt(LocalDateTime.now());
        positionMonitorRepository.save(monitor);
        return Result.success();
    }

    private boolean isValidType(String type) {
        return TYPE_PRICE.equals(type) || TYPE_RATE.equals(type);
    }

    public void checkAll() {
        List<PositionMonitor> rules = positionMonitorRepository.findByEnabledTrue();
        for (PositionMonitor rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                log.error("监控规则{}判定失败: {}", rule.getId(), e.getMessage(), e);
            }
        }
    }

    public void checkRule(PositionMonitor rule) {
        BigDecimal close = getCurrentPrice(rule.getSymbol());
        if (close == null || close.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("规则{}对应标的{}现价缺失，跳过", rule.getId(), rule.getSymbol());
            return;
        }
        BigDecimal returnRate = getReturnRate(rule.getUserId(), rule.getSymbol(), close);

        if (isTakeProfitHit(rule, close, returnRate)) {
            if (!DIR_TAKE_PROFIT.equals(rule.getLastTriggerDirection())) {
                notify(rule, DIR_TAKE_PROFIT, close);
                rule.setLastTriggerDirection(DIR_TAKE_PROFIT);
                rule.setUpdatedAt(LocalDateTime.now());
                positionMonitorRepository.save(rule);
            }
        } else if (isStopLossHit(rule, close, returnRate)) {
            if (!DIR_STOP_LOSS.equals(rule.getLastTriggerDirection())) {
                notify(rule, DIR_STOP_LOSS, close);
                rule.setLastTriggerDirection(DIR_STOP_LOSS);
                rule.setUpdatedAt(LocalDateTime.now());
                positionMonitorRepository.save(rule);
            }
        } else {
            if (!DIR_NONE.equals(rule.getLastTriggerDirection())) {
                rule.setLastTriggerDirection(DIR_NONE);
                rule.setUpdatedAt(LocalDateTime.now());
                positionMonitorRepository.save(rule);
            }
        }
    }

    public BigDecimal getCurrentPrice(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        if (symbol.startsWith("1") || symbol.startsWith("5")) {
            EtfDailyQuotes etf = etfDailyQuotesRepository.findTopBySymbolOrderByTradeDateDescUpdatedAtDesc(symbol);
            return etf == null ? null : etf.getClose();
        }
        MarketQuotes quotes = marketQuotesRepository.findByCode(symbol);
        return quotes == null ? null : quotes.getClose();
    }

    private BigDecimal getReturnRate(String userId, String symbol, BigDecimal close) {
        Position position = positionRepository.findByUserIdAndSymbol(userId, symbol);
        if (position == null || position.getAvgCostPrice() == null
                || position.getAvgCostPrice().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return close.subtract(position.getAvgCostPrice())
                .divide(position.getAvgCostPrice(), 4, RoundingMode.HALF_UP);
    }

    private boolean isTakeProfitHit(PositionMonitor rule, BigDecimal close, BigDecimal returnRate) {
        if (rule.getTakeProfitType() == null || rule.getTakeProfitValue() == null) {
            return false;
        }
        if (TYPE_PRICE.equals(rule.getTakeProfitType())) {
            return close.compareTo(rule.getTakeProfitValue()) >= 0;
        }
        if (TYPE_RATE.equals(rule.getTakeProfitType())) {
            return returnRate != null && returnRate.compareTo(rule.getTakeProfitValue()) >= 0;
        }
        return false;
    }

    private boolean isStopLossHit(PositionMonitor rule, BigDecimal close, BigDecimal returnRate) {
        if (rule.getStopLossType() == null || rule.getStopLossValue() == null) {
            return false;
        }
        if (TYPE_PRICE.equals(rule.getStopLossType())) {
            return close.compareTo(rule.getStopLossValue()) <= 0;
        }
        if (TYPE_RATE.equals(rule.getStopLossType())) {
            return returnRate != null && returnRate.compareTo(rule.getStopLossValue()) <= 0;
        }
        return false;
    }

    private void notify(PositionMonitor rule, String direction, BigDecimal close) {
        Notification notification = new Notification();
        notification.setUserId(rule.getUserId());
        notification.setType(direction);
        notification.setSymbol(rule.getSymbol());
        notification.setStockName(rule.getStockName());
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        String label = DIR_TAKE_PROFIT.equals(direction) ? "止盈" : "止损";
        notification.setContent(String.format("【%s提醒】%s(%s) 最新收盘价 %s 已触发%s",
                label, rule.getStockName(), rule.getSymbol(), close.toPlainString(), label));
        for (NotificationSender sender : notificationSenders) {
            sender.send(notification);
        }
    }
}