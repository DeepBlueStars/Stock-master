package com.xian.stockai.dao;

import com.xian.stockai.po.UserUseConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserUseConfigDao extends MongoRepository<UserUseConfig, String> {
    UserUseConfig findByUserId(String userId);
}