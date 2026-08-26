package com.xian.stockai.dao;

import com.xian.stockai.po.Users;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsersRepository extends MongoRepository<Users, String> {
}
