package com.xian.stockai.controller;

import com.xian.stockai.po.StockBasicInfo;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.po.TotalReturn;
import com.xian.stockai.service.StockService;
import com.xian.stockai.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/stock")
@Slf4j
public class StockController {
    private final StockService stockService;

    // 买入接口
    @PostMapping("/buy")
    public Result<Void> buy(@RequestBody StockTrade stockTrade) {
        return stockService.buy(stockTrade);
    }

    // 卖出接口
    @PostMapping("/sell")
    public Result<Void> sell(@RequestBody StockTrade stockTrade) {
        return stockService.sell(stockTrade);
    }

    @PostMapping("/list")
    public Result<List<PositionRes>> list(@RequestBody UserRequest request) {
        return stockService.getPositionList(request.getUserId());
    }

    @PostMapping("/basicPosition")
    public Result<BasicPositionRes> basicPosition(@RequestBody UserRequest request) {
        return stockService.getBasicPositionList(request.getUserId());
    }
    
    @PostMapping("/listTrade")
    public Result<List<StockTrade>> listTrade(@RequestBody StockTradeReq req) {
        return stockService.getTradeList(req);
    }

    @PostMapping("/getStock")
    public Result<StockBasicInfo> getStock(@RequestBody StockReq req) {
        return stockService.getStockBasicInfo(req.getSymbol());
    }

    @GetMapping("/calculateTotalReturn")
    @Scheduled(cron = "0 0 23 * * ?")
    public void calculateTotalReturn() {
        log.info("开始计算总收益");
        stockService.calculateTotalReturn();
        log.info("结束计算总收益");
    }

    @PostMapping("/listTotalReturn")
    public Result<List<TotalReturn>> listTotalReturn(@RequestBody ReturnReq req) {
        return stockService.listTotalReturn(req);
    }
    
    @PostMapping("/assetCategoryPosition")
    public Result<List<AssetCategoryPositionRes>> assetCategoryPosition(@RequestBody UserRequest request) {
        return stockService.getAssetCategoryPosition(request.getUserId());
    }
}