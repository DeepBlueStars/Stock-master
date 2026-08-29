package com.xian.stockai.controller;

import com.xian.stockai.po.PositionMonitor;
import com.xian.stockai.service.MonitorService;
import com.xian.stockai.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/monitor")
@Slf4j
public class MonitorController {
    private final MonitorService monitorService;

    @PostMapping("/save")
    public Result<PositionMonitor> save(@RequestBody MonitorSaveRequest req) {
        return monitorService.save(req);
    }

    @PostMapping("/list")
    public Result<List<PositionMonitor>> list(@RequestBody UserRequest req) {
        return monitorService.list(req.getUserId());
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody MonitorDeleteRequest req) {
        return monitorService.delete(req.getUserId(), req.getSymbol());
    }

    @PostMapping("/enable")
    public Result<Void> enable(@RequestBody MonitorEnableRequest req) {
        return monitorService.enable(req.getUserId(), req.getSymbol(), Boolean.TRUE.equals(req.getEnabled()));
    }
}