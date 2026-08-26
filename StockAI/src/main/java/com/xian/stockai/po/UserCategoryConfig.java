package com.xian.stockai.po;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @author xian
 * @description 用户资产配置
 */
@Document(collection = "user_categroy_config")
public class UserCategoryConfig {
    @Id
    private String id;

    @Field(name = "userId")
    private String userId;

    @Field(name = "config")
    private String config;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }
}