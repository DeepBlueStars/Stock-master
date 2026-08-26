package com.xian.stockai.service;

import com.xian.stockai.dao.ConfigDao;
import com.xian.stockai.dao.UserUseConfigDao;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.UserCategoryConfig;
import com.xian.stockai.po.UserUseConfig;
import com.xian.stockai.po.Users;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ConfigService {
    private final ConfigDao configDao;
    private final UserUseConfigDao userUseConfigDao;
    private final UsersRepository usersRepository;

    public ConfigService(ConfigDao configDao, UserUseConfigDao userUseConfigDao, UsersRepository usersRepository) {
        this.configDao = configDao;
        this.userUseConfigDao = userUseConfigDao;
        this.usersRepository = usersRepository;
    }

    /**
     * 保存用户资产配置
     * @param userId 用户ID
     * @param config 配置内容
     * @return Result
     */
    public Result<Void> saveConfig(String userId, String config) {
        try {
            // 保存用户配置
            UserCategoryConfig userConfig = new UserCategoryConfig();
            userConfig.setUserId(userId);
            userConfig.setConfig(config);
            UserCategoryConfig savedConfig = configDao.save(userConfig);
            
            // 更新用户当前使用的配置
            UserUseConfig currentConfig = userUseConfigDao.findByUserId(userId);
            if (currentConfig == null) {
                // 新增当前配置
                currentConfig = new UserUseConfig();
                currentConfig.setUserId(userId);
            }
            currentConfig.setConfigId(savedConfig.getId());
            userUseConfigDao.save(currentConfig);
            
            return Result.success("配置保存成功", null);
        } catch (Exception e) {
            log.error("保存配置失败: {}", e.getMessage());
            return Result.fail("保存配置失败");
        }
    }
    
    /**
     * 获取当前用户使用的配置
     * @param userId 用户ID
     * @return Result
     */
    public Result<UserCategoryConfig> getConfig(String userId) {
        try {
            // 获取用户当前使用的配置ID
            UserUseConfig currentConfig = userUseConfigDao.findByUserId(userId);
            if (currentConfig == null) {
                return Result.success("未找到当前使用的配置", null);
            }
            
            // 根据配置ID获取配置内容
            Optional<UserCategoryConfig> configOpt = configDao.findById(currentConfig.getConfigId());
            if (configOpt.isPresent()) {
                return Result.success(configOpt.get());
            } else {
                return Result.success("未找到配置内容", null);
            }
        } catch (Exception e) {
            log.error("获取配置失败: {}", e.getMessage());
            return Result.fail("获取配置失败");
        }
    }
    
    /**
     * 更新配置
     * @param configId 配置ID
     * @param config 配置内容
     * @return Result
     */
    public Result<Void> updateConfig(String configId, String config) {
        try {
            // 验证配置ID是否存在
            Optional<UserCategoryConfig> configOpt = configDao.findById(configId);
            if (configOpt.isEmpty()) {
                return Result.fail("配置不存在");
            }
            
            // 更新配置内容
            UserCategoryConfig userConfig = configOpt.get();
            userConfig.setConfig(config);
            configDao.save(userConfig);
            
            return Result.success("配置更新成功", null);
        } catch (Exception e) {
            log.error("更新配置失败: {}", e.getMessage());
            return Result.fail("更新配置失败");
        }
    }
    
    /**
     * 获取用户的所有配置
     * @param userId 用户ID
     * @return Result
     */
    public Result<List<UserCategoryConfig>> listConfigs(String userId) {
        try {
            List<UserCategoryConfig> configs = configDao.findByUserId(userId);
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取配置列表失败: {}", e.getMessage());
            return Result.fail("获取配置列表失败");
        }
    }
    
    /**
     * 根据ID获取配置
     * @param configId 配置ID
     * @return Result
     */
    public Result<UserCategoryConfig> getConfigById(String configId) {
        try {
            Optional<UserCategoryConfig> configOpt = configDao.findById(configId);
            if (configOpt.isPresent()) {
                return Result.success(configOpt.get());
            } else {
                return Result.fail("配置不存在");
            }
        } catch (Exception e) {
            log.error("获取配置失败: {}", e.getMessage());
            return Result.fail("获取配置失败");
        }
    }
    
    /**
     * 更新用户当前使用的配置
     * @param userId 用户ID
     * @param configId 配置ID
     * @return Result
     */
    public Result<Void> updateCurrentConfig(String userId, String configId) {
        try {
            // 验证配置是否存在
            Optional<UserCategoryConfig> configOpt = configDao.findById(configId);
            if (configOpt.isEmpty()) {
                return Result.fail("配置不存在");
            }
            
            // 更新用户当前使用的配置
            UserUseConfig currentConfig = userUseConfigDao.findByUserId(userId);
            if (currentConfig == null) {
                // 新增当前配置
                currentConfig = new UserUseConfig();
                currentConfig.setUserId(userId);
            }
            currentConfig.setConfigId(configId);
            userUseConfigDao.save(currentConfig);
            
            return Result.success("当前配置更新成功", null);
        } catch (Exception e) {
            log.error("更新当前配置失败: {}", e.getMessage());
            return Result.fail("更新当前配置失败");
        }
    }
    
    /**
     * 获取用户个人信息
     * @param userId 用户ID
     * @return Result
     */
    public Result<Users> getUserInfo(String userId) {
        try {
            Optional<Users> userOpt = usersRepository.findById(userId);
            if (userOpt.isPresent()) {
                Users users = userOpt.get();
                users.setHashedPassword(null);
                return Result.success(users);
            } else {
                return Result.fail("用户不存在");
            }
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage());
            return Result.fail("获取用户信息失败");
        }
    }
    
    /**
     * 修改用户现金
     * @param userId 用户ID
     * @param cash 现金金额
     * @return Result
     */
    public Result<Void> updateCash(String userId, BigDecimal cash) {
        try {
            Optional<Users> userOpt = usersRepository.findById(userId);
            if (userOpt.isPresent()) {
                Users user = userOpt.get();
                user.setCash(cash);
                usersRepository.save(user);
                return Result.success("现金更新成功", null);
            } else {
                return Result.fail("用户不存在");
            }
        } catch (Exception e) {
            log.error("更新现金失败: {}", e.getMessage());
            return Result.fail("更新现金失败");
        }
    }
}