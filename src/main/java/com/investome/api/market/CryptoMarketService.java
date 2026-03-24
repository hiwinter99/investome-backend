package com.investome.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CryptoMarketService {

    private final MarketHttpClient marketHttpClient;

    private volatile long cachedAt = 0L;
    private volatile List<MarketItemResponse> cachedItems = List.of();

    private static final long CACHE_MILLIS = 60_000L;

    public List<MarketItemResponse> getTop30() {
        long now = System.currentTimeMillis();

        if (!cachedItems.isEmpty() && now - cachedAt < CACHE_MILLIS) {
            return cachedItems;
        }

        try {
            List<MarketItemResponse> fresh = fetchTop30();
            cachedItems = fresh;
            cachedAt = now;
            return fresh;
        } catch (Exception e) {
            if (!cachedItems.isEmpty()) {
                return cachedItems;
            }
            return List.of();
        }
    }

    private List<MarketItemResponse> fetchTop30() throws Exception {
        String url = "https://api.coingecko.com/api/v3/coins/markets"
                + "?vs_currency=krw"
                + "&order=market_cap_desc"
                + "&per_page=30"
                + "&page=1"
                + "&sparkline=false"
                + "&price_change_percentage=24h";

        JsonNode items = marketHttpClient.fetchJsonNode(url);

        List<MarketItemResponse> result = new ArrayList<>();
        if (!items.isArray()) {
            return result;
        }

        int rank = 1;
        for (JsonNode coin : items) {
            result.add(MarketItemResponse.builder()
                    .rank(coin.path("market_cap_rank").isNumber() ? coin.path("market_cap_rank").asInt() : rank)
                    .market("CRYPTO")
                    .name(coin.path("name").asText("-"))
                    .displayNameEN(coin.path("name").asText(""))
                    .symbol(coin.path("symbol").asText("-").toUpperCase())
                    .iconUrl(coin.path("image").asText(""))
                    .coinId(coin.path("id").asText(""))
                    .capKRW(coin.path("market_cap").isNumber() ? coin.path("market_cap").asLong() : null)
                    .priceKRW(coin.path("current_price").isNumber() ? coin.path("current_price").asDouble() : null)
                    .changePct(coin.path("price_change_percentage_24h").isNumber()
                            ? coin.path("price_change_percentage_24h").asDouble()
                            : null)
                    .build());
            rank++;
        }

        return result;
    }

    public MarketItemResponse getCoinQuote(String symbol, String name, String coinId) {
        try {
            String id = (coinId == null || coinId.isBlank()) ? resolveCoinId(symbol, name) : coinId;
            if (id == null || id.isBlank()) {
                return null;
            }

            String url = "https://api.coingecko.com/api/v3/coins/markets"
                    + "?vs_currency=krw"
                    + "&ids=" + URLEncoder.encode(id, StandardCharsets.UTF_8)
                    + "&sparkline=false"
                    + "&price_change_percentage=24h";

            JsonNode items = marketHttpClient.fetchJsonNode(url);
            if (!items.isArray() || items.isEmpty()) {
                return null;
            }

            JsonNode coin = items.get(0);

            return MarketItemResponse.builder()
                    .market("CRYPTO")
                    .name(coin.path("name").asText(name != null ? name : "-"))
                    .displayNameEN(coin.path("name").asText(""))
                    .symbol(coin.path("symbol").asText(symbol != null ? symbol : "-").toUpperCase())
                    .iconUrl(coin.path("image").asText(""))
                    .coinId(coin.path("id").asText(id))
                    .capKRW(coin.path("market_cap").isNumber() ? coin.path("market_cap").asLong() : null)
                    .priceKRW(coin.path("current_price").isNumber() ? coin.path("current_price").asDouble() : null)
                    .changePct(coin.path("price_change_percentage_24h").isNumber()
                            ? coin.path("price_change_percentage_24h").asDouble()
                            : null)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCoinId(String symbol, String name) {
        try {
            String query = symbol != null && !symbol.isBlank() ? symbol : name;
            if (query == null || query.isBlank()) return null;

            String url = "https://api.coingecko.com/api/v3/search?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            JsonNode json = marketHttpClient.fetchJsonNode(url);
            JsonNode coins = json.path("coins");
            if (!coins.isArray() || coins.isEmpty()) {
                return null;
            }

            String sym = symbol == null ? "" : symbol.trim().toLowerCase();
            String nm = name == null ? "" : name.trim().toLowerCase();

            for (JsonNode coin : coins) {
                String cSym = coin.path("symbol").asText("").toLowerCase();
                String cName = coin.path("name").asText("").toLowerCase();
                if (!sym.isBlank() && cSym.equals(sym) && !nm.isBlank() && cName.equals(nm)) {
                    return coin.path("id").asText();
                }
            }

            for (JsonNode coin : coins) {
                String cSym = coin.path("symbol").asText("").toLowerCase();
                if (!sym.isBlank() && cSym.equals(sym)) {
                    return coin.path("id").asText();
                }
            }

            return coins.get(0).path("id").asText();
        } catch (Exception e) {
            return null;
        }
    }
}