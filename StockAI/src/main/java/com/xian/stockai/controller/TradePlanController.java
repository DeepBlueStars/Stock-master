package com.xian.stockai.controller;

import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.vo.MonitorLinkRes;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.TradePlanRes;
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

    /**
     * 当前持仓的交易计划列表（缺失计划按需补生成）
     */
    @PostMapping("/list")
    public Result<List<TradePlanRes>> list(@RequestBody UserRequest request) {
        return tradePlanService.list(request.getUserId());
    }

    /**
     * 将计划中的止盈止损建议一键写入提醒规则
     */
    @PostMapping("/link-monitor")
    public Result<MonitorLinkRes> linkMonitor(@RequestBody TradePlanSymbolReq req) {
        return tradePlanService.linkMonitor(req.getUserId(), req.getSymbol());
    }
}
