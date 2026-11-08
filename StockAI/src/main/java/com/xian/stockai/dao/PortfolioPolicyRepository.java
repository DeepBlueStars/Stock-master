package com.xian.stockai.dao;

import com.xian.stockai.po.PortfolioPolicy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioPolicyRepository extends MongoRepository<PortfolioPolicy, String> {

    Optional<PortfolioPolicy> findByUserId(String userId);
}
