package com.xian.stockai.dao;

import com.xian.stockai.po.UserCategoryConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserCategoryConfigRepository extends MongoRepository<UserCategoryConfig, String> {
    UserCategoryConfig findByUserId(String userId);
}