package com.xian.stockai.controller;

import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.service.tradeplan.TradePlanSummaryService;
import com.xian.stockai.vo.MonitorLinkRes;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.TradePlanBatchStateReq;
import com.xian.stockai.vo.TradePlanRes;
import com.xian.stockai.vo.TradePlanSummaryBriefRes;
import com.xian.stockai.vo.TradePlanSummaryDetailReq;
import com.xian.stockai.vo.TradePlanSummaryRes;
import com.xian.stockai.vo.TradePlanSymbolReq;
import com.xian.stockai.vo.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author xian
 * @description 交易计划接口
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/trade-plan")
@Slf4j
public class TradePlanController {

    private final TradePlanService tradePlanService;
    private final TradePlanSummaryService tradePlanSummaryService;

    /**
     * 当前持仓的交易计划列表（缺失计划按需补生成）
     */
    @PostMapping("/list")
    public Result<List<TradePlanRes>> list(@RequestBody UserRequest request) {
        return tradePlanService.list(request.getUserId());
    }

    /**
     * 勾选 / 取消某一批「已买入」
     */
    @PostMapping("/batch/state")
    public Result<TradePlanRes> markBatch(@RequestBody TradePlanBatchStateReq req) {
        return tradePlanService.markBatch(req.getUserId(), req.getSymbol(), req.getIndex(), req.getBought());
    }

    /**
     * 将计划中的止盈止损建议一键写入提醒规则
     */
    @PostMapping("/link-monitor")
    public Result<MonitorLinkRes> linkMonitor(@RequestBody TradePlanSymbolReq req) {
        return tradePlanService.linkMonitor(req.getUserId(), req.getSymbol());
    }

    /**
     * 最新一期周报
     */
    @PostMapping("/summary/latest")
    public Result<TradePlanSummaryRes> latestSummary(@RequestBody UserRequest request) {
        return tradePlanSummaryService.latest(request.getUserId());
    }

    /**
     * 最近 12 期周报列表
     */
    @PostMapping("/summary/list")
    public Result<List<TradePlanSummaryBriefRes>> summaryHistory(@RequestBody UserRequest request) {
        return tradePlanSummaryService.history(request.getUserId());
    }

    /**
     * 指定周期周报全文
     */
    @PostMapping("/summary/detail")
    public Result<TradePlanSummaryRes> summaryDetail(@RequestBody TradePlanSummaryDetailReq req) {
        return tradePlanSummaryService.detail(req.getUserId(), req.getWeekStart());
    }

    /**
     * 手动生成当期周报（用于验证与失败补偿）
     */
    @PostMapping("/summary/generate")
    public Result<TradePlanSummaryRes> generateSummary(@RequestBody UserRequest request) {
        return Result.success(TradePlanSummaryRes.from(
                tradePlanSummaryService.generateWeekly(request.getUserId())));
    }
}
