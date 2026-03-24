package com.investome.api.market;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CryptoMarketController {

    private final CryptoMarketService cryptoMarketService;
    private final StockMarketService stockMarketService;

    @GetMapping("/api/crypto-top30")
    public MarketListResponse getCryptoTop30() {
        return new MarketListResponse(cryptoMarketService.getTop30());
    }

    @GetMapping("/api/stock-top30")
    public MarketListResponse getStockTop30(@RequestParam String market) {
        return new MarketListResponse(stockMarketService.getTop30(market));
    }

    @GetMapping("/api/asset-quote")
    public Object getAssetQuote(
            @RequestParam String market,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String coinId
    ) {
        if ("CRYPTO".equalsIgnoreCase(market)) {
            MarketItemResponse item = cryptoMarketService.getCoinQuote(symbol, name, coinId);
            return item != null ? item : emptyResponse();
        }

        if ("KOSPI".equalsIgnoreCase(market) || "NASDAQ".equalsIgnoreCase(market)) {
            MarketItemResponse item = stockMarketService.getQuote(symbol, market);
            return item != null ? item : emptyResponse();
        }

        return emptyResponse();
    }

    private Map<String, Object> emptyResponse() {
        return new HashMap<>();
    }
}