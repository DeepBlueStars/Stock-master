package com.xian.stockai.controller;

import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.task.ScreeningTask;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.ScreeningHistoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/screening")
public class ScreeningController {
    private final ScreeningService screeningService;
    private final ScreeningTask screeningTask;

    @PostMapping("/latest")
    public Result<ScreeningResult> latest() {
        return screeningService.latest();
    }

    @PostMapping("/history")
    public Result<List<ScreeningResult>> history(@RequestBody(required = false) ScreeningHistoryRequest req) {
        int page = (req == null || req.getPage() == null || req.getPage() < 1) ? 1 : req.getPage();
        int size = (req == null || req.getSize() == null || req.getSize() < 1) ? 10 : Math.min(req.getSize(), 100);
        return screeningService.history(page, size);
    }

    @PostMapping("/run")
    public Result<ScreeningResult> run() {
        return screeningTask.runScreening();
    }
}
