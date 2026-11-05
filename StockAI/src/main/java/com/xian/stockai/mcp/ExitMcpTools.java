package com.xian.stockai.mcp;

import com.xian.stockai.po.ExitEvalRun;
import com.xian.stockai.po.ExitSignal;
import com.xian.stockai.po.PositionExitPolicy;
import com.xian.stockai.service.exit.ExitEvaluationService;
import com.xian.stockai.vo.ExitEvaluationRes;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author xian
 * @description 卖出纪律的 MCP 工具，供外部 AI 客户端（OpenClaw 等）读取信号、跑判定、确认信号。
 * <p>
 * 与 {@link StockAiMcpTools} 一样支持「不传 userId 用服务端默认用户」，
 * 这样助手侧不用每次带上下文。
 */
@Component
@Slf4j
public class ExitMcpTools {

    private final ExitEvaluationService exitEvaluationService;
    private final String defaultUserId;

    public ExitMcpTools(ExitEvaluationService exitEvaluationService,
                        @Value("${stockai.mcp.default-user-id:}") String defaultUserId) {
        this.exitEvaluationService = exitEvaluationService;
        this.defaultUserId = defaultUserId;
    }

    @Tool(name = "evaluate_exit_rules",
            description = "立即按卖出纪律（L1 硬止损、L2 移动止盈、L4 分批止盈）判定全部持仓，返回今天新产生的卖出信号。")
    public Result<ExitEvaluationRes> evaluateExitRules(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return exitEvaluationService.evaluate(uid, "MCP");
    }

    @Tool(name = "list_exit_signals",
            description = "查询卖出信号列表。status 可填 TRIGGERED（待处理）/ ACKED（已处理）/ RECOVERED（已失效），留空返回全部。")
    public Result<List<ExitSignal>> listExitSignals(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(required = false, description = "信号状态，留空返回全部") String status) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return exitEvaluationService.listSignals(uid, status);
    }

    @Tool(name = "ack_exit_signal",
            description = "确认某条卖出信号已处理（例如已经卖出，或决定按纪律继续持有）。")
    public Result<ExitSignal> ackExitSignal(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "信号ID") String signalId,
            @ToolParam(required = false, description = "处理备注，如「已卖出」「继续持有」") String note) {
        return exitEvaluationService.ack(resolveUserId(userId), signalId, note);
    }

    @Tool(name = "list_exit_policies", description = "查询每只持仓的卖出纪律参数与当前状态（是否已武装移动止盈、已减到第几档）。")
    public Result<List<PositionExitPolicy>> listExitPolicies(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return exitEvaluationService.listPolicies(uid);
    }

    @Tool(name = "update_exit_policy",
            description = "修改某只持仓的卖出纪律参数（硬止损比例、移动止盈武装阈值与回撤宽度、止盈档位），只传要改的项。")
    public Result<PositionExitPolicy> updateExitPolicy(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票或 ETF 代码") String symbol,
            @ToolParam(required = false, description = "硬止损比例，如 0.25 表示跌到成本的 75% 清仓") BigDecimal hardStopRate,
            @ToolParam(required = false, description = "移动止盈武装阈值，如 0.30") BigDecimal trailingArmReturn,
            @ToolParam(required = false, description = "移动止盈回撤宽度，如 0.15") BigDecimal trailingRate,
            @ToolParam(required = false, description = "保本线浮盈，如 0.10") BigDecimal breakevenReturn,
            @ToolParam(required = false, description = "第一档止盈浮盈，如 0.30") BigDecimal takeProfit1Return,
            @ToolParam(required = false, description = "第二档止盈浮盈，如 0.60") BigDecimal takeProfit2Return,
            @ToolParam(required = false, description = "是否启用该持仓的卖出纪律") Boolean enabled) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        PositionExitPolicy request = new PositionExitPolicy();
        request.setSymbol(symbol);
        request.setHardStopRate(hardStopRate);
        request.setTrailingArmReturn(trailingArmReturn);
        request.setTrailingRate(trailingRate);
        request.setBreakevenReturn(breakevenReturn);
        request.setTakeProfit1Return(takeProfit1Return);
        request.setTakeProfit2Return(takeProfit2Return);
        request.setEnabled(enabled);
        return exitEvaluationService.savePolicy(uid, request);
    }

    @Tool(name = "list_exit_runs", description = "查询最近的卖出纪律判定执行记录，用于确认今天有没有真的跑过。")
    public Result<List<ExitEvalRun>> listExitRuns(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId) {
        return exitEvaluationService.listRuns(resolveUserId(userId));
    }

    private String resolveUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return defaultUserId == null || defaultUserId.isBlank() ? null : defaultUserId;
    }
}
