package com.xian.stockai.dao;

import com.xian.stockai.po.UserCategoryConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConfigDao extends MongoRepository<UserCategoryConfig, String> {
    List<UserCategoryConfig> findByUserId(String userId);
}