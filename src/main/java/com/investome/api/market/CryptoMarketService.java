package com.investome.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CryptoMarketService {

    private final MarketHttpClient marketHttpClient;
    private final CryptoRedisCache redisCache;

    private volatile long cachedAt = 0L;
    private volatile List<MarketItemResponse> cachedItems = List.of();

    private static final long CACHE_MILLIS = 60_000L;
    private static final List<CoinSeed> FALLBACK_TOP30 = List.of(
            new CoinSeed("bitcoin", "Bitcoin", "BTC"),
            new CoinSeed("ethereum", "Ethereum", "ETH"),
            new CoinSeed("tether", "Tether", "USDT"),
            new CoinSeed("ripple", "XRP", "XRP"),
            new CoinSeed("binancecoin", "BNB", "BNB"),
            new CoinSeed("solana", "Solana", "SOL"),
            new CoinSeed("usd-coin", "USDC", "USDC"),
            new CoinSeed("staked-ether", "Lido Staked Ether", "STETH"),
            new CoinSeed("dogecoin", "Dogecoin", "DOGE"),
            new CoinSeed("tron", "TRON", "TRX"),
            new CoinSeed("cardano", "Cardano", "ADA"),
            new CoinSeed("wrapped-bitcoin", "Wrapped Bitcoin", "WBTC"),
            new CoinSeed("hyperliquid", "Hyperliquid", "HYPE"),
            new CoinSeed("chainlink", "Chainlink", "LINK"),
            new CoinSeed("wrapped-steth", "Wrapped stETH", "WSTETH"),
            new CoinSeed("sui", "Sui", "SUI"),
            new CoinSeed("stellar", "Stellar", "XLM"),
            new CoinSeed("avalanche-2", "Avalanche", "AVAX"),
            new CoinSeed("shiba-inu", "Shiba Inu", "SHIB"),
            new CoinSeed("bitcoin-cash", "Bitcoin Cash", "BCH"),
            new CoinSeed("leo-token", "LEO Token", "LEO"),
            new CoinSeed("toncoin", "Toncoin", "TON"),
            new CoinSeed("litecoin", "Litecoin", "LTC"),
            new CoinSeed("uniswap", "Uniswap", "UNI"),
            new CoinSeed("polkadot", "Polkadot", "DOT"),
            new CoinSeed("monero", "Monero", "XMR"),
            new CoinSeed("near", "NEAR Protocol", "NEAR"),
            new CoinSeed("pepe", "Pepe", "PEPE"),
            new CoinSeed("aptos", "Aptos", "APT"),
            new CoinSeed("bittensor", "Bittensor", "TAO")
    );

    public synchronized List<MarketItemResponse> getTop30() {
        long now = System.currentTimeMillis();

        CryptoRedisCache.Lookup lookup = redisCache.read();
        if (!lookup.items().isEmpty()) {
            cachedItems = lookup.items();
            cachedAt = now;
            return cachedItems;
        }

        if (!lookup.available() && !cachedItems.isEmpty() && now - cachedAt < CACHE_MILLIS) {
            return cachedItems;
        }

        try {
            List<MarketItemResponse> fresh = fetchTop30();
            cachedItems = fresh;
            cachedAt = now;
            redisCache.write(fresh);
            return fresh;
        } catch (Exception e) {
            if (!cachedItems.isEmpty()) {
                return cachedItems;
            }
            return List.of();
        }
    }

    private List<MarketItemResponse> fetchTop30() throws Exception {
        try {
            List<MarketItemResponse> primary = fetchTop30FromMarkets();
            if (!primary.isEmpty()) {
                return primary;
            }
        } catch (Exception ignored) {
        }

        List<MarketItemResponse> fallback = fetchTop30FromSimplePrice();
        if (!fallback.isEmpty()) {
            return fallback;
        }

        throw new IllegalStateException("Unable to load crypto top30 from primary or fallback source");
    }

    private List<MarketItemResponse> fetchTop30FromMarkets() throws Exception {
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
            result.add(buildCoinItem(
                    coin.path("market_cap_rank").isNumber() ? coin.path("market_cap_rank").asInt() : rank,
                    coin.path("name").asText("-"),
                    coin.path("symbol").asText("-").toUpperCase(Locale.ROOT),
                    coin.path("id").asText(""),
                    coin.path("image").asText(""),
                    coin.path("market_cap").isNumber() ? coin.path("market_cap").asLong() : null,
                    coin.path("current_price").isNumber() ? coin.path("current_price").asDouble() : null,
                    coin.path("price_change_percentage_24h").isNumber()
                            ? coin.path("price_change_percentage_24h").asDouble()
                            : null
            ));
            rank++;
        }

        return result;
    }

    private List<MarketItemResponse> fetchTop30FromSimplePrice() throws Exception {
        String ids = FALLBACK_TOP30.stream()
                .map(seed -> seed.coinId)
                .collect(Collectors.joining(","));

        String url = "https://api.coingecko.com/api/v3/simple/price"
                + "?ids=" + URLEncoder.encode(ids, StandardCharsets.UTF_8)
                + "&vs_currencies=krw"
                + "&include_24hr_change=true"
                + "&include_market_cap=true";

        JsonNode json = marketHttpClient.fetchJsonNode(url);
        List<MarketItemResponse> result = new ArrayList<>();

        for (CoinSeed seed : FALLBACK_TOP30) {
            JsonNode coin = json.path(seed.coinId);
            Double price = marketHttpClient.toDouble(coin.path("krw"));
            if (price == null) {
                continue;
            }

            result.add(buildCoinItem(
                    null,
                    seed.name,
                    seed.symbol,
                    seed.coinId,
                    "",
                    marketHttpClient.toLong(coin.path("krw_market_cap")),
                    price,
                    marketHttpClient.toDouble(coin.path("krw_24h_change"))
            ));
        }

        result.sort(Comparator
                .comparing(MarketItemResponse::getCapKRW, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MarketItemResponse::getSymbol, Comparator.nullsLast(String::compareTo)));

        List<MarketItemResponse> ranked = new ArrayList<>();
        int rank = 1;
        for (MarketItemResponse item : result) {
            item.setRank(rank++);
            ranked.add(item);
        }
        return ranked;
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
            if (items.isArray() && !items.isEmpty()) {
                JsonNode coin = items.get(0);

                return buildCoinItem(
                        null,
                        coin.path("name").asText(name != null ? name : "-"),
                        coin.path("symbol").asText(symbol != null ? symbol : "-").toUpperCase(Locale.ROOT),
                        coin.path("id").asText(id),
                        coin.path("image").asText(""),
                        coin.path("market_cap").isNumber() ? coin.path("market_cap").asLong() : null,
                        coin.path("current_price").isNumber() ? coin.path("current_price").asDouble() : null,
                        coin.path("price_change_percentage_24h").isNumber()
                                ? coin.path("price_change_percentage_24h").asDouble()
                                : null
                );
            }

            return fetchCoinQuoteFallback(id, symbol, name);
        } catch (Exception e) {
            String id = (coinId == null || coinId.isBlank()) ? resolveCoinId(symbol, name) : coinId;
            return fetchCoinQuoteFallback(id, symbol, name);
        }
    }

    private String resolveCoinId(String symbol, String name) {
        CoinSeed fallbackSeed = findFallbackSeed(symbol, name, null);
        if (fallbackSeed != null) {
            return fallbackSeed.coinId;
        }

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

    private MarketItemResponse fetchCoinQuoteFallback(String coinId, String symbol, String name) {
        try {
            if (coinId == null || coinId.isBlank()) {
                return null;
            }

            String url = "https://api.coingecko.com/api/v3/simple/price"
                    + "?ids=" + URLEncoder.encode(coinId, StandardCharsets.UTF_8)
                    + "&vs_currencies=krw"
                    + "&include_24hr_change=true"
                    + "&include_market_cap=true";

            JsonNode json = marketHttpClient.fetchJsonNode(url);
            JsonNode coin = json.path(coinId);
            Double price = marketHttpClient.toDouble(coin.path("krw"));
            if (price == null) {
                return null;
            }

            CoinSeed seed = findFallbackSeed(symbol, name, coinId);
            String resolvedName = seed != null ? seed.name : (name != null && !name.isBlank() ? name : symbol);
            String resolvedSymbol = seed != null ? seed.symbol : (symbol != null && !symbol.isBlank() ? symbol : coinId);

            return buildCoinItem(
                    null,
                    resolvedName != null ? resolvedName : "-",
                    resolvedSymbol != null ? resolvedSymbol.toUpperCase(Locale.ROOT) : "-",
                    coinId,
                    "",
                    marketHttpClient.toLong(coin.path("krw_market_cap")),
                    price,
                    marketHttpClient.toDouble(coin.path("krw_24h_change"))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private CoinSeed findFallbackSeed(String symbol, String name, String coinId) {
        String normalizedSymbol = normalize(symbol);
        String normalizedName = normalize(name);
        String normalizedCoinId = normalize(coinId);

        for (CoinSeed seed : FALLBACK_TOP30) {
            if (!normalizedCoinId.isBlank() && normalize(seed.coinId).equals(normalizedCoinId)) {
                return seed;
            }
            if (!normalizedSymbol.isBlank() && normalize(seed.symbol).equals(normalizedSymbol)) {
                return seed;
            }
            if (!normalizedName.isBlank() && normalize(seed.name).equals(normalizedName)) {
                return seed;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private MarketItemResponse buildCoinItem(
            Integer rank,
            String name,
            String symbol,
            String coinId,
            String iconUrl,
            Long capKRW,
            Double priceKRW,
            Double changePct
    ) {
        return MarketItemResponse.builder()
                .rank(rank)
                .market("CRYPTO")
                .name(name)
                .displayNameEN(name)
                .symbol(symbol)
                .iconUrl(iconUrl)
                .coinId(coinId)
                .capKRW(capKRW)
                .priceKRW(priceKRW)
                .changePct(changePct)
                .build();
    }

    private static final class CoinSeed {
        private final String coinId;
        private final String name;
        private final String symbol;

        private CoinSeed(String coinId, String name, String symbol) {
            this.coinId = coinId;
            this.name = name;
            this.symbol = symbol;
        }
    }
}
