package com.xian.stockai.service;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionMonitorRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.notify.NotificationSender;
import com.xian.stockai.vo.MonitorSaveRequest;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final NotificationSender notificationSender;

    public MonitorService(PositionMonitorRepository positionMonitorRepository,
                          PositionRepository positionRepository,
                          MarketQuotesRepository marketQuotesRepository,
                          EtfDailyQuotesRepository etfDailyQuotesRepository,
                          NotificationSender notificationSender) {
        this.positionMonitorRepository = positionMonitorRepository;
        this.positionRepository = positionRepository;
        this.marketQuotesRepository = marketQuotesRepository;
        this.etfDailyQuotesRepository = etfDailyQuotesRepository;
        this.notificationSender = notificationSender;
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
}