package com.xian.stockai.mcp;

import com.xian.stockai.po.Position;
import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.po.StockBasicInfo;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.service.MonitorService;
import com.xian.stockai.service.StockService;
import com.xian.stockai.vo.PositionRes;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StockAI 的 MCP 工具，供外部 AI 客户端通过 MCP 协议直接操作持仓。
 */
@Component
@Slf4j
public class StockAiMcpTools {

    private final StockService stockService;
    private final MonitorService monitorService;
    private final String defaultUserId;

    public StockAiMcpTools(StockService stockService,
                           MonitorService monitorService,
                           @Value("${stockai.mcp.default-user-id:}") String defaultUserId) {
        this.stockService = stockService;
        this.monitorService = monitorService;
        this.defaultUserId = defaultUserId;
    }

    @Tool(name = "buy_position", description = "买入股票或 ETF。自动计算佣金、过户费并更新持仓与交易记录。")
    public Result<TradeExecutionResult> buyPosition(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票或 ETF 代码，如 600000、510300") String symbol,
            @ToolParam(description = "买入数量（股）") Integer quantity,
            @ToolParam(description = "买入价格（元）") BigDecimal price,
            @ToolParam(required = false, description = "股票名称，不填则自动查询") String stockName,
            @ToolParam(required = false, description = "资产分类名称，不填则沿用已有持仓分类或按代码推断") String type,
            @ToolParam(required = false, description = "买入方式：现金 或其他，默认 现金") String buyMethod) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        if (symbol == null || symbol.isBlank()) {
            return Result.fail("股票代码不能为空");
        }
        if (quantity == null || quantity <= 0) {
            return Result.fail("买入数量必须大于0");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail("买入价格必须大于0");
        }

        String name = stockName;
        Result<StockBasicInfo> basicInfo = stockService.getStockBasicInfo(symbol);
        if (basicInfo.checkSuccess() && basicInfo.getData() != null) {
            if (name == null || name.isBlank()) {
                name = basicInfo.getData().getName();
            }
        } else if (name == null || name.isBlank()) {
            return Result.fail(symbol + "不存在");
        }

        StockTrade trade = new StockTrade();
        trade.setUserId(uid);
        trade.setSymbol(symbol);
        trade.setStockName(name);
        trade.setSide("买入");
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setTradeTime(LocalDateTime.now());
        trade.setType(resolveType(uid, symbol, type));
        trade.setBuyMethod(buyMethod == null || buyMethod.isBlank() ? "现金" : buyMethod);

        Result<Void> result = stockService.buy(trade);
        if (!result.checkSuccess()) {
            return Result.fail(result.getMessage());
        }
        return Result.success(result.getMessage(), toExecutionResult(trade, result.getMessage()));
    }

    @Tool(name = "sell_position", description = "卖出股票或 ETF。自动计算佣金、过户费、印花税并更新持仓与交易记录。")
    public Result<TradeExecutionResult> sellPosition(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票或 ETF 代码，如 600000、510300") String symbol,
            @ToolParam(description = "卖出数量（股）") Integer quantity,
            @ToolParam(description = "卖出价格（元）") BigDecimal price,
            @ToolParam(required = false, description = "股票名称，不填则从持仓中获取") String stockName) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        if (symbol == null || symbol.isBlank()) {
            return Result.fail("股票代码不能为空");
        }
        if (quantity == null || quantity <= 0) {
            return Result.fail("卖出数量必须大于0");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail("卖出价格必须大于0");
        }

        Position position = stockService.getPosition(uid, symbol);
        if (position == null) {
            return Result.fail(symbol + "持仓不存在");
        }
        String name = stockName == null || stockName.isBlank() ? position.getStockName() : stockName;

        StockTrade trade = new StockTrade();
        trade.setUserId(uid);
        trade.setSymbol(symbol);
        trade.setStockName(name);
        trade.setSide("卖出");
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setTradeTime(LocalDateTime.now());

        Result<Void> result = stockService.sell(trade);
        if (!result.checkSuccess()) {
            return Result.fail(result.getMessage());
        }
        return Result.success(result.getMessage(), toExecutionResult(trade, result.getMessage()));
    }

    @Tool(name = "list_positions", description = "查询当前持仓列表，包含现价、成本、盈亏，以及每只持仓预设的止盈/止损价格与触发状态。")
    public Result<List<PositionSnapshot>> listPositions(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }

        Result<List<PositionRes>> positionsResult = stockService.getPositionList(uid);
        if (!positionsResult.checkSuccess()) {
            return Result.fail(positionsResult.getMessage());
        }

        Map<String, PositionMonitor> monitorMap = new HashMap<>();
        Result<List<PositionMonitor>> monitorResult = monitorService.list(uid);
        if (monitorResult.checkSuccess() && monitorResult.getData() != null) {
            for (PositionMonitor monitor : monitorResult.getData()) {
                monitorMap.put(monitor.getSymbol(), monitor);
            }
        }

        List<PositionSnapshot> snapshots = new ArrayList<>();
        for (PositionRes position : positionsResult.getData()) {
            PositionMonitor monitor = monitorMap.get(position.getSymbol());
            snapshots.add(new PositionSnapshot(
                    position.getSymbol(),
                    position.getStockName(),
                    position.getType(),
                    position.getTotalQuantity(),
                    position.getAvgCostPrice(),
                    position.getTotalCost(),
                    position.getPrice(),
                    position.getTotalValue(),
                    position.getTotalReturn(),
                    position.getTotalReturnPct(),
                    monitor == null ? null : monitor.getTakeProfitType(),
                    monitor == null ? null : monitor.getTakeProfitValue(),
                    triggerPrice(monitor == null ? null : monitor.getTakeProfitType(),
                            monitor == null ? null : monitor.getTakeProfitValue(), position.getAvgCostPrice()),
                    monitor == null ? null : monitor.getStopLossType(),
                    monitor == null ? null : monitor.getStopLossValue(),
                    triggerPrice(monitor == null ? null : monitor.getStopLossType(),
                            monitor == null ? null : monitor.getStopLossValue(), position.getAvgCostPrice()),
                    monitor == null ? null : monitor.getEnabled(),
                    monitor == null ? null : monitor.getLastTriggerDirection()));
        }
        return Result.success(snapshots);
    }

    private String resolveUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return defaultUserId == null || defaultUserId.isBlank() ? null : defaultUserId;
    }

    private String resolveType(String userId, String symbol, String type) {
        if (type != null && !type.isBlank()) {
            return type;
        }
        Position position = stockService.getPosition(userId, symbol);
        if (position != null && position.getType() != null && !position.getType().isBlank()) {
            return position.getType();
        }
        // 买入页的类型来自资产配置，没有「基金/ETF」这类选项；默认按股票落库
        return "股票";
    }

    /**
     * 将止盈/止损阈值换算为触发价格：价格类型直接返回，比例类型按持仓均价换算。
     */
    private BigDecimal triggerPrice(String monitorType, BigDecimal value, BigDecimal avgCostPrice) {
        if (monitorType == null || value == null) {
            return null;
        }
        if (MonitorService.TYPE_PRICE.equals(monitorType)) {
            return value;
        }
        if (MonitorService.TYPE_RATE.equals(monitorType) && avgCostPrice != null) {
            return avgCostPrice.multiply(BigDecimal.ONE.add(value)).setScale(3, RoundingMode.HALF_UP);
        }
        return null;
    }

    private TradeExecutionResult toExecutionResult(StockTrade trade, String message) {
        return new TradeExecutionResult(
                message,
                trade.getSide(),
                trade.getSymbol(),
                trade.getStockName(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getAmount(),
                trade.getCommission(),
                trade.getTransferFee(),
                trade.getStampTax(),
                trade.getActualAmount());
    }

    /**
     * 交易执行结果
     */
    public record TradeExecutionResult(
            String message,
            String side,
            String symbol,
            String stockName,
            Integer quantity,
            BigDecimal price,
            BigDecimal amount,
            BigDecimal commission,
            BigDecimal transferFee,
            BigDecimal stampTax,
            BigDecimal actualAmount) {
    }

    /**
     * 持仓快照（含止盈止损设置）
     */
    public record PositionSnapshot(
            String symbol,
            String stockName,
            String type,
            Integer quantity,
            BigDecimal avgCostPrice,
            BigDecimal totalCost,
            BigDecimal currentPrice,
            BigDecimal totalValue,
            BigDecimal totalReturn,
            BigDecimal totalReturnPct,
            String takeProfitType,
            BigDecimal takeProfitValue,
            BigDecimal takeProfitPrice,
            String stopLossType,
            BigDecimal stopLossValue,
            BigDecimal stopLossPrice,
            Boolean monitorEnabled,
            String lastTriggerDirection) {
    }
}
