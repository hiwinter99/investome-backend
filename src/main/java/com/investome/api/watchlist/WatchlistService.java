package com.investome.api.watchlist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;

    public List<WatchlistResponse> getList(Long userId) {
        return watchlistRepository.findByUserIdOrderByAddedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<WatchlistResponse> add(Long userId, WatchlistRequest request) {
        String market = normalize(request.getMarket());
        String symbol = normalize(request.getSymbol());

        watchlistRepository.findByUserIdAndMarketAndSymbol(userId, market, symbol)
                .orElseGet(() -> {
                    WatchlistItem item = new WatchlistItem();
                    item.setUserId(userId);
                    item.setMarket(market);
                    item.setSymbol(symbol);
                    item.setName(defaultString(request.getName(), symbol));
                    item.setDisplayNameEN(defaultString(request.getDisplayNameEN(), request.getName()));
                    item.setIconUrl(defaultString(request.getIconUrl(), ""));
                    item.setCoinId(defaultString(request.getCoinId(), ""));
                    return watchlistRepository.save(item);
                });

        return getList(userId);
    }

    public List<WatchlistResponse> remove(Long userId, WatchlistRequest request) {
        String market = normalize(request.getMarket());
        String symbol = normalize(request.getSymbol());

        watchlistRepository.deleteByUserIdAndMarketAndSymbol(userId, market, symbol);
        return getList(userId);
    }

    private WatchlistResponse toResponse(WatchlistItem item) {
        return new WatchlistResponse(
                item.getId(),
                item.getMarket(),
                item.getSymbol(),
                item.getName(),
                item.getDisplayNameEN(),
                item.getIconUrl(),
                item.getCoinId(),
                item.getAddedAt() != null ? item.getAddedAt().toString() : null
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String defaultString(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value.trim();
    }
}