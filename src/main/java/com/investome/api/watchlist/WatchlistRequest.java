package com.investome.api.watchlist;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchlistRequest {
    private String market;
    private String symbol;
    private String name;
    private String displayNameEN;
    private String iconUrl;
    private String coinId;
}