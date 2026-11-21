package com.xian.stockai.mcp;

import com.alibaba.fastjson2.JSON;
import com.xian.stockai.po.ResearchReport;
import com.xian.stockai.po.UserFavorites;
import com.xian.stockai.service.FocusPoolService;
import com.xian.stockai.vo.FocusStockView;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 关注池的 MCP 工具：个股报告 §10 / §12 / §14 的摘要由 OpenClaw 分析后写入关注池。
 * <p>
 * 与 save_research_report 配套使用：先存整份 Markdown 拿到 reportId，再把摘要写进关注池里这只股票的关注档案。
 * 股票不在关注池时自动加入；带 eventDate 的监测项会自动同步到监测日历；这里不判断价格、不改状态。
 */
@Component
@Slf4j
public class FocusPoolMcpTools {

    private final FocusPoolService focusPoolService;
    private final String defaultUserId;

    public FocusPoolMcpTools(FocusPoolService focusPoolService,
                             @Value("${stockai.mcp.default-user-id:}") String defaultUserId) {
        this.focusPoolService = focusPoolService;
        this.defaultUserId = defaultUserId;
    }

    @Tool(name = "save_focus_stock",
            description = "把一只股票写进关注池，并保存它的关注档案（报告「十、买入计划与卖出纪律」「十二、监测清单」"
                    + "「十四、综合决策备忘录」的摘要，不传原文）。股票不在关注池会自动加入；已有档案整体覆盖，状态保持不变。"
                    + "带 eventDate 的监测项会自动同步到监测日历（类别=个股监测），只重置待处理事件，已完成的保留。"
                    + "buyPlanJson 示例：{\"maxBuyPrice\":43,\"maxShares\":200,\"maxWeightPct\":16,\"holdingPeriod\":\"3-5 年\","
                    + "\"rules\":[\"43 元以上不买\"],\"tiers\":[{\"tier\":1,\"label\":\"第一档\",\"priceLow\":42,"
                    + "\"priceHigh\":43,\"shares\":100,\"condition\":\"价格到位 + Q4 增速≥15%\"}]}。"
                    + "sellDisciplineJson 示例：{\"targets\":[{\"priceLow\":65,\"valuation\":\"2028E 35x\",\"action\":\"减仓 1/3\"}],"
                    + "\"invalidation\":[\"创新药连续 2 季 <10%\"],\"stops\":{\"fundamental\":\"触发证伪全部卖出\"},"
                    + "\"addSignals\":[\"≤43 元\"]}。"
                    + "monitorItemsJson 示例：[{\"key\":\"INNOVATION_GROWTH\",\"metric\":\"创新药收入增速\",\"currentValue\":\"+17%\","
                    + "\"threshold\":\"连续 2 季 <10%\",\"action\":\"下调估值\",\"importance\":\"HIGH\",\"cadence\":\"QUARTERLY\","
                    + "\"eventDate\":\"2027-04-09\"}]。"
                    + "decisionMemoJson 示例：{\"dimensions\":[{\"name\":\"生意质量\",\"conclusion\":\"优秀\",\"confidence\":\"HIGH\"}],"
                    + "\"emptyPosition\":\"观察，不买\",\"holder\":\"持有不加\",\"sellSignals\":[\"...\"],"
                    + "\"addSignals\":[\"...\"],\"oneLine\":\"等价格，不等故事。\"}。")
    public Result<UserFavorites.FocusProfile> saveFocusStock(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票代码，如 600276") String symbol,
            @ToolParam(required = false, description = "股票名称，如 恒瑞医药") String stockName,
            @ToolParam(required = false, description = "来源报告ID（save_research_report 的返回值）") String reportId,
            @ToolParam(required = false, description = "报告数据日 yyyy-MM-dd，用报告里的数据日而不是写入日") String reportDate,
            @ToolParam(required = false, description = "评级，如 A") String rating,
            @ToolParam(required = false, description = "状态：WATCHING / TRIGGERED / HOLDING / PAUSED / DROPPED / CLOSED，不填默认 WATCHING") String status,
            @ToolParam(required = false, description = "一句话立场，如「观察，不买入」") String stance,
            @ToolParam(required = false, description = "报告的一句话结论，如「等价格，不等故事」") String oneLine,
            @ToolParam(required = false, description = "一句话生意定义，如「创新药平台型公司」") String coreThesis,
            @ToolParam(required = false, description = "买入计划 JSON") String buyPlanJson,
            @ToolParam(required = false, description = "卖出纪律 JSON") String sellDisciplineJson,
            @ToolParam(required = false, description = "监测清单 JSON 数组") String monitorItemsJson,
            @ToolParam(required = false, description = "决策备忘录 JSON") String decisionMemoJson,
            @ToolParam(required = false, description = "备注，写入档案的状态备注与时间线") String note) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        UserFavorites.FocusProfile request = new UserFavorites.FocusProfile();
        request.setSymbol(symbol);
        request.setStockName(stockName);
        request.setReportId(reportId);
        request.setReportDate(parseDate(reportDate));
        request.setRating(rating);
        request.setStatus(status);
        request.setStance(stance);
        request.setOneLine(oneLine);
        request.setCoreThesis(coreThesis);
        request.setSource(ResearchReport.SOURCE_OPENCLAW);
        request.setStatusNote(note);
        try {
            if (StringUtils.hasText(buyPlanJson)) {
                request.setBuyPlan(JSON.parseObject(buyPlanJson, UserFavorites.BuyPlan.class));
            }
            if (StringUtils.hasText(sellDisciplineJson)) {
                request.setSellDiscipline(JSON.parseObject(sellDisciplineJson, UserFavorites.SellDiscipline.class));
            }
            if (StringUtils.hasText(monitorItemsJson)) {
                List<UserFavorites.MonitorItem> items =
                        JSON.parseArray(monitorItemsJson, UserFavorites.MonitorItem.class);
                request.setMonitorItems(items == null ? new ArrayList<>() : items);
            }
            if (StringUtils.hasText(decisionMemoJson)) {
                request.setDecisionMemo(JSON.parseObject(decisionMemoJson, UserFavorites.DecisionMemo.class));
            }
        } catch (Exception e) {
            return Result.fail("JSON 解析失败: " + e.getMessage());
        }
        return focusPoolService.save(uid, request);
    }

    @Tool(name = "list_focus_stocks",
            description = "列出关注池里有报告档案的股票，带最新收盘价与派生标签（NEAR_BUY / IN_BUY_ZONE / ABOVE_MAX / STALE）。写入前先查，避免覆盖错。")
    public Result<List<FocusStockView>> listFocusStocks(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return focusPoolService.list(uid);
    }

    @Tool(name = "get_focus_stock",
            description = "按代码读取关注池里一只股票的关注档案（含买入计划、卖出纪律、监测清单、决策备忘录）。")
    public Result<FocusStockView> getFocusStock(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票代码") String symbol) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return focusPoolService.get(uid, symbol);
    }

    @Tool(name = "update_focus_stock_status",
            description = "更新关注档案的状态或某一档买入计划的状态，并写入时间线。"
                    + "允许的流转：WATCHING→TRIGGERED/PAUSED/DROPPED；TRIGGERED→HOLDING/WATCHING/PAUSED/DROPPED；"
                    + "HOLDING→CLOSED；PAUSED→WATCHING/DROPPED；DROPPED→WATCHING。"
                    + "暂停或放弃必须传 note 说明原因；价格到点不会自动改状态，必须显式调用。"
                    + "只改档位时 status 留空、传 tier 与 tierStatus（TRIGGERED / DONE / CANCELLED / WAITING）。")
    public Result<UserFavorites.FocusProfile> updateFocusStockStatus(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票代码") String symbol,
            @ToolParam(required = false, description = "新状态：WATCHING / TRIGGERED / HOLDING / PAUSED / DROPPED / CLOSED") String status,
            @ToolParam(required = false, description = "买入档位：1 / 2") Integer tier,
            @ToolParam(required = false, description = "档位状态：WAITING / TRIGGERED / DONE / CANCELLED") String tierStatus,
            @ToolParam(required = false, description = "原因，暂停/放弃必填") String note) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return focusPoolService.updateStatus(uid, symbol, status, tier, tierStatus, note);
    }

    @Tool(name = "delete_focus_stock",
            description = "把一只股票从关注池移除（连带关注档案），并清掉它待处理的个股监测事件；报告原文不删。")
    public Result<Void> deleteFocusStock(
            @ToolParam(required = false, description = "用户ID，不填则使用服务端默认用户") String userId,
            @ToolParam(description = "股票代码") String symbol) {
        String uid = resolveUserId(userId);
        if (uid == null) {
            return Result.fail("用户ID不能为空");
        }
        return focusPoolService.delete(uid, symbol);
    }

    private LocalDate parseDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : null;
    }

    private String resolveUserId(String userId) {
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        return StringUtils.hasText(defaultUserId) ? defaultUserId : null;
    }
}
