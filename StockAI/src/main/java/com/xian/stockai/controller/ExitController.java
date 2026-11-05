package com.xian.stockai.controller;

import com.xian.stockai.po.ExitEvalRun;
import com.xian.stockai.po.ExitSignal;
import com.xian.stockai.po.PositionExitPolicy;
import com.xian.stockai.service.exit.ExitEvaluationService;
import com.xian.stockai.vo.ExitEvaluationRes;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @author xian
 * @description 卖出纪律接口：跑判定、看信号、确认信号、改策略。
 */
@RestController
@RequestMapping("/exit")
@RequiredArgsConstructor
public class ExitController {

    private final ExitEvaluationService exitEvaluationService;

    /**
     * 立刻跑一次判定（不传 symbol 则跑全部持仓）
     */
    @PostMapping("/evaluate")
    public Result<ExitEvaluationRes> evaluate(@RequestParam String userId) {
        return exitEvaluationService.evaluate(userId, "MANUAL");
    }

    /**
     * 信号列表，status 可选：TRIGGERED / ACKED / RECOVERED
     */
    @GetMapping("/signals")
    public Result<List<ExitSignal>> signals(@RequestParam String userId,
                                            @RequestParam(required = false) String status) {
        return exitEvaluationService.listSignals(userId, status);
    }

    /**
     * 确认（已处理）某条信号
     */
    @PostMapping("/signals/{id}/ack")
    public Result<ExitSignal> ack(@PathVariable String id,
                                  @RequestBody(required = false) Map<String, String> body,
                                  @RequestParam(required = false) String userId) {
        String note = body == null ? null : body.get("note");
        return exitEvaluationService.ack(userId, id, note);
    }

    @GetMapping("/policies")
    public Result<List<PositionExitPolicy>> policies(@RequestParam String userId) {
        return exitEvaluationService.listPolicies(userId);
    }

    @PostMapping("/policies")
    public Result<PositionExitPolicy> savePolicy(@RequestParam String userId,
                                                 @RequestBody PositionExitPolicy request) {
        return exitEvaluationService.savePolicy(userId, request);
    }

    @GetMapping("/runs")
    public Result<List<ExitEvalRun>> runs(@RequestParam(required = false) String userId) {
        return exitEvaluationService.listRuns(userId);
    }
}
