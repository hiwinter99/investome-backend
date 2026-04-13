package com.investome.api.watchlist;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WatchlistResponse {
    private Long id;
    private String market;
    private String symbol;
    private String name;
    private String displayNameEN;
    private String iconUrl;
    private String coinId;
    private String addedAt;
}