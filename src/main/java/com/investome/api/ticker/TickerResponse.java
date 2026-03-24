package com.investome.api.ticker;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TickerResponse {

    private CoinTicker bitcoin;
    private CoinTicker ethereum;
    private CoinTicker ripple;
    private Indexes indexes;

    @Getter
    @AllArgsConstructor
    public static class CoinTicker {
        private Double krw;

        @JsonProperty("krw_24h_change")
        private Double krw24hChange;
    }

    @Getter
    @AllArgsConstructor
    public static class Indexes {
        private IndexTicker KOSPI;
        private IndexTicker NASDAQ;
    }

    @Getter
    @AllArgsConstructor
    public static class IndexTicker {
        private Double price;
        private Double changePct;
    }
}