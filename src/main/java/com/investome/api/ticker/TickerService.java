package com.investome.api.ticker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TickerService {

    private static final String TICKER_CACHE_KEY = "investome:ticker-cache";
    private final ObjectMapper objectMapper;

    private volatile long cachedAt = 0L;
    private volatile TickerResponse cachedData = null;

    private static final long CACHE_MILLIS = 15_000L;

    public TickerResponse getTicker() {
        long now = System.currentTimeMillis();

        if (cachedData != null && (now - cachedAt) < CACHE_MILLIS) {
            return cachedData;
        }

        try {
            TickerResponse fresh = fetchTicker();
            cachedData = fresh;
            cachedAt = now;
            return fresh;
        } catch (Exception e) {
            if (cachedData != null) {
                return cachedData;
            }
            throw new RuntimeException("ticker fetch failed: " + e.getMessage(), e);
        }
    }

    private TickerResponse fetchTicker() {
        TickerResponse prev = cachedData;

        TickerResponse.CoinTicker bitcoin = prev != null ? prev.getBitcoin() : null;
        TickerResponse.CoinTicker ethereum = prev != null ? prev.getEthereum() : null;
        TickerResponse.CoinTicker ripple = prev != null ? prev.getRipple() : null;

        try {
            CryptoTickerBundle bundle = fetchCryptoTickers();
            if (bundle.bitcoin() != null) bitcoin = bundle.bitcoin();
            if (bundle.ethereum() != null) ethereum = bundle.ethereum();
            if (bundle.ripple() != null) ripple = bundle.ripple();
        } catch (Exception ignored) {
        }

        TickerResponse.IndexTicker kospi = prev != null && prev.getIndexes() != null
                ? prev.getIndexes().getKOSPI()
                : new TickerResponse.IndexTicker(null, null);

        TickerResponse.IndexTicker nasdaq = prev != null && prev.getIndexes() != null
                ? prev.getIndexes().getNASDAQ()
                : new TickerResponse.IndexTicker(null, null);

        TickerResponse.IndexTicker samsungElectronics = prev != null && prev.getStocks() != null
                ? prev.getStocks().getSamsungElectronics()
                : new TickerResponse.IndexTicker(null, null);

        TickerResponse.IndexTicker skHynix = prev != null && prev.getStocks() != null
                ? prev.getStocks().getSkHynix()
                : new TickerResponse.IndexTicker(null, null);

        try {
            YahooQuoteResponse yahoo = fetchJson(
                    "https://query1.finance.yahoo.com/v7/finance/quote?symbols=%5EKS11,%5EIXIC",
                    YahooQuoteResponse.class
            );

            if (yahoo != null &&
                    yahoo.getQuoteResponse() != null &&
                    yahoo.getQuoteResponse().getResult() != null) {

                for (YahooQuoteResponse.QuoteItem item : yahoo.getQuoteResponse().getResult()) {
                    if ("^KS11".equals(item.getSymbol())) {
                        TickerResponse.IndexTicker parsed = new TickerResponse.IndexTicker(
                                item.getRegularMarketPrice(),
                                item.getRegularMarketChangePercent()
                        );
                        if (parsed.getPrice() != null) {
                            kospi = parsed;
                        }
                    } else if ("^IXIC".equals(item.getSymbol())) {
                        TickerResponse.IndexTicker parsed = new TickerResponse.IndexTicker(
                                item.getRegularMarketPrice(),
                                item.getRegularMarketChangePercent()
                        );
                        if (parsed.getPrice() != null) {
                            nasdaq = parsed;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // quote 실패 시 아래 chart fallback 진행
        }

        if (kospi == null || kospi.getPrice() == null) {
            try {
                kospi = fetchYahooChartMeta("^KS11");
            } catch (Exception ignored) {
            }
        }

        if (nasdaq == null || nasdaq.getPrice() == null) {
            try {
                nasdaq = fetchYahooChartMeta("^IXIC");
            } catch (Exception ignored) {
            }
        }

        try {
            YahooQuoteResponse yahooStocks = fetchJson(
                    "https://query1.finance.yahoo.com/v7/finance/quote?symbols=005930.KS,000660.KS",
                    YahooQuoteResponse.class
            );

            if (yahooStocks != null &&
                    yahooStocks.getQuoteResponse() != null &&
                    yahooStocks.getQuoteResponse().getResult() != null) {

                for (YahooQuoteResponse.QuoteItem item : yahooStocks.getQuoteResponse().getResult()) {
                    if ("005930.KS".equals(item.getSymbol())) {
                        TickerResponse.IndexTicker parsed = new TickerResponse.IndexTicker(
                                item.getRegularMarketPrice(),
                                item.getRegularMarketChangePercent()
                        );
                        if (parsed.getPrice() != null) {
                            samsungElectronics = parsed;
                        }
                    } else if ("000660.KS".equals(item.getSymbol())) {
                        TickerResponse.IndexTicker parsed = new TickerResponse.IndexTicker(
                                item.getRegularMarketPrice(),
                                item.getRegularMarketChangePercent()
                        );
                        if (parsed.getPrice() != null) {
                            skHynix = parsed;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // quote 실패 시 chart fallback 사용
        }

        if (samsungElectronics == null || samsungElectronics.getPrice() == null) {
            try {
                samsungElectronics = fetchYahooChartMeta("005930.KS");
            } catch (Exception ignored) {
            }
        }

        if (skHynix == null || skHynix.getPrice() == null) {
            try {
                skHynix = fetchYahooChartMeta("000660.KS");
            } catch (Exception ignored) {
            }
        }

        return new TickerResponse(
                bitcoin,
                ethereum,
                ripple,
                new TickerResponse.Stocks(
                        samsungElectronics != null ? samsungElectronics : new TickerResponse.IndexTicker(null, null),
                        skHynix != null ? skHynix : new TickerResponse.IndexTicker(null, null)
                ),
                new TickerResponse.Indexes(
                        kospi != null ? kospi : new TickerResponse.IndexTicker(null, null),
                        nasdaq != null ? nasdaq : new TickerResponse.IndexTicker(null, null)
                )
        );
    }

    private CryptoTickerBundle fetchCryptoTickers() throws IOException, InterruptedException {
        Exception lastError = null;

        for (int attempt = 0; attempt < 2; attempt += 1) {
            try {
                JsonNode crypto = fetchJsonNode(
                        "https://api.coingecko.com/api/v3/simple/price" +
                                "?ids=bitcoin,ethereum,ripple" +
                                "&vs_currencies=krw" +
                                "&include_24hr_change=true"
                );

                return new CryptoTickerBundle(
                        parseCoin(crypto.get("bitcoin")),
                        parseCoin(crypto.get("ethereum")),
                        parseCoin(crypto.get("ripple"))
                );
            } catch (Exception e) {
                lastError = e;
            }
        }

        try {
            return fetchUpbitTickers();
        } catch (Exception upbitError) {
            if (lastError instanceof IOException io) throw io;
            if (lastError instanceof InterruptedException ie) throw ie;
            if (upbitError instanceof IOException io) throw io;
            if (upbitError instanceof InterruptedException ie) throw ie;
            throw new IOException("crypto ticker fetch failed", upbitError);
        }
    }

    private CryptoTickerBundle fetchUpbitTickers() throws IOException, InterruptedException {
        JsonNode items = fetchJsonNode(
                "https://api.upbit.com/v1/ticker?markets=KRW-BTC,KRW-ETH,KRW-XRP"
        );

        if (!items.isArray()) {
            throw new IOException("Upbit ticker payload is invalid");
        }

        TickerResponse.CoinTicker bitcoin = null;
        TickerResponse.CoinTicker ethereum = null;
        TickerResponse.CoinTicker ripple = null;

        for (JsonNode item : items) {
            String market = item.path("market").asText("");
            Double price = asDouble(item.get("trade_price"));
            Double signedChangeRate = asDouble(item.get("signed_change_rate"));
            Double changePct = signedChangeRate != null ? signedChangeRate * 100.0 : null;
            TickerResponse.CoinTicker ticker = new TickerResponse.CoinTicker(price, changePct);

            switch (market) {
                case "KRW-BTC" -> bitcoin = ticker;
                case "KRW-ETH" -> ethereum = ticker;
                case "KRW-XRP" -> ripple = ticker;
                default -> {
                }
            }
        }

        return new CryptoTickerBundle(bitcoin, ethereum, ripple);
    }

    private TickerResponse.IndexTicker fetchYahooChartMeta(String symbol) throws IOException, InterruptedException {
        JsonNode json = fetchJsonNode(
                "https://query1.finance.yahoo.com/v8/finance/chart/" + encodeSymbol(symbol) +
                        "?interval=1d&range=5d&includePrePost=false"
        );

        JsonNode meta = json.path("chart").path("result").path(0).path("meta");
        if (meta.isMissingNode() || meta.isNull()) {
            throw new IOException("No chart meta for " + symbol);
        }

        Double price = asDouble(meta.get("regularMarketPrice"));
        Double prevClose = asDouble(meta.get("previousClose"));

        if (prevClose == null) {
            prevClose = asDouble(meta.get("chartPreviousClose"));
        }

        Double changePct = null;
        if (price != null && prevClose != null && prevClose != 0) {
            changePct = ((price - prevClose) / prevClose) * 100.0;
        }

        return new TickerResponse.IndexTicker(price, changePct);
    }

    private String encodeSymbol(String symbol) {
        return symbol.replace("^", "%5E");
    }

    private TickerResponse.CoinTicker parseCoin(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        Double krw = asDouble(node.get("krw"));
        Double change = asDouble(node.get("krw_24h_change"));

        return new TickerResponse.CoinTicker(krw, change);
    }

    private Double asDouble(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    private JsonNode fetchJsonNode(String url) throws IOException, InterruptedException {
        String body = fetchText(url);
        return objectMapper.readTree(body);
    }

    private <T> T fetchJson(String url, Class<T> type) throws IOException, InterruptedException {
        String body = fetchText(url);
        return objectMapper.readValue(body, type);
    }

    private String fetchText(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Investome Spring)")
                .header("Accept", "application/json,text/plain,*/*")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("upstream failed: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }

    private record CryptoTickerBundle(
            TickerResponse.CoinTicker bitcoin,
            TickerResponse.CoinTicker ethereum,
            TickerResponse.CoinTicker ripple
    ) {
    }
}
