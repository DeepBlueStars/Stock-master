package com.xian.stockai.controller;

import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/hotspot")
public class HotspotController {
    private final HotspotService hotspotService;

    @PostMapping("/latest")
    public Result<HotspotResult> latest() {
        return hotspotService.latest();
    }
}
