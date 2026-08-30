package com.xian.stockai.controller;

import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.service.MarketRegimeService;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/market-regime")
public class MarketRegimeController {
    private final MarketRegimeService marketRegimeService;

    @PostMapping("/evaluate")
    public Result<MarketRegimeRecord> evaluate() {
        return marketRegimeService.evaluateAndSave();
    }

    @PostMapping("/latest")
    public Result<MarketRegimeRecord> latest() {
        return marketRegimeService.latest();
    }

    @PostMapping("/history")
    public Result<List<MarketRegimeRecord>> history(@RequestParam(defaultValue = "30") int limit) {
        return marketRegimeService.history(limit);
    }
}
