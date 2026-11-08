package com.xian.stockai.controller;

import com.xian.stockai.service.policy.PortfolioPolicyService;
import com.xian.stockai.vo.PortfolioSummaryRes;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组合策略：目标比例 vs 实际比例。
 */
@RestController
@RequestMapping("/policy")
@RequiredArgsConstructor
@Slf4j
public class PolicyController {

    private final PortfolioPolicyService portfolioPolicyService;

    @PostMapping("/summary")
    public Result<PortfolioSummaryRes> summary(@RequestBody UserRequest request) {
        try {
            return Result.success(portfolioPolicyService.summary(request.getUserId()));
        } catch (Exception e) {
            log.error("获取用户{}组合策略对账失败: {}", request.getUserId(), e.getMessage(), e);
            return Result.fail("获取组合策略对账失败");
        }
    }
}
