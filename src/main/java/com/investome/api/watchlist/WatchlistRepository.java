package com.investome.api.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserIdOrderByAddedAtDesc(Long userId);

    Optional<WatchlistItem> findByUserIdAndMarketAndSymbol(Long userId, String market, String symbol);

    void deleteByUserIdAndMarketAndSymbol(Long userId, String market, String symbol);
}