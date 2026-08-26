package com.xian.stockai.controller;

import com.xian.stockai.service.ConfigService;
import com.xian.stockai.vo.ConfigRequest;
import com.xian.stockai.vo.ConfigUpdateRequest;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/config")
@Slf4j
public class ConfigController {
    private final ConfigService configService;

    /**
     * 保存用户资产配置
     * @param configRequest 请求参数
     * @return Result
     */
    @PostMapping("/save")
    public Result<Void> saveConfig(@RequestBody ConfigRequest configRequest) {
        return configService.saveConfig(configRequest.getUserId(), configRequest.getConfig());
    }
    
    /**
     * 获取当前用户使用的配置
     * @param configRequest 请求参数
     * @return Result
     */
    @PostMapping("/get")
    public Result<?> getConfig(@RequestBody ConfigRequest configRequest) {
        return configService.getConfig(configRequest.getUserId());
    }
    
    /**
     * 更新配置
     * @param updateRequest 请求参数
     * @return Result
     */
    @PostMapping("/update")
    public Result<Void> updateConfig(@RequestBody ConfigUpdateRequest updateRequest) {
        return configService.updateConfig(updateRequest.getConfigId(), updateRequest.getConfig());
    }
    
    /**
     * 获取用户的所有配置
     * @param configRequest 请求参数
     * @return Result
     */
    @PostMapping("/list")
    public Result<?> listConfigs(@RequestBody ConfigRequest configRequest) {
        return configService.listConfigs(configRequest.getUserId());
    }
    
    /**
     * 根据ID获取配置
     * @param configRequest 请求参数，包含configId
     * @return Result
     */
    @PostMapping("/getById")
    public Result<?> getConfigById(@RequestBody ConfigUpdateRequest configRequest) {
        return configService.getConfigById(configRequest.getConfigId());
    }
    
    /**
     * 更新用户当前使用的配置
     * @param configRequest 请求参数
     * @return Result
     */
    @PostMapping("/updateCurrent")
    public Result<Void> updateCurrentConfig(@RequestBody ConfigUpdateRequest configRequest) {
        return configService.updateCurrentConfig(configRequest.getUserId(), configRequest.getConfigId());
    }
    
    /**
     * 获取用户个人信息
     * @param configRequest 请求参数
     * @return Result
     */
    @PostMapping("/userInfo")
    public Result<?> getUserInfo(@RequestBody ConfigRequest configRequest) {
        return configService.getUserInfo(configRequest.getUserId());
    }
    
    /**
     * 修改用户现金
     * @param request 请求参数
     * @return Result
     */
    @PostMapping("/updateCash")
    public Result<Void> updateCash(@RequestBody UpdateCashRequest request) {
        return configService.updateCash(request.getUserId(), request.getCash());
    }
    
    /**
     * 修改现金请求参数
     */
    static class UpdateCashRequest {
        private String userId;
        private BigDecimal cash;
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public BigDecimal getCash() {
            return cash;
        }
        
        public void setCash(BigDecimal cash) {
            this.cash = cash;
        }
    }
}