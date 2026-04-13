package com.investome.api.mypage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MyPagePortfolioRepository extends JpaRepository<MyPagePortfolio, Long> {
    Optional<MyPagePortfolio> findByUserId(Long userId);
}