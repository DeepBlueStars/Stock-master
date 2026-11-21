package com.xian.stockai.dao;

import com.xian.stockai.po.UserFavorites;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserFavoritesRepository extends MongoRepository<UserFavorites, String> {

    Optional<UserFavorites> findByUserId(String userId);
}
