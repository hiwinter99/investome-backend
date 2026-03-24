package com.investome.api.ticker;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class YahooQuoteResponse {
    private QuoteResponse quoteResponse;

    @Getter
    @Setter
    public static class QuoteResponse {
        private List<QuoteItem> result;
    }

    @Getter
    @Setter
    public static class QuoteItem {
        private String symbol;
        private Double regularMarketPrice;
        private Double regularMarketChangePercent;
    }
}