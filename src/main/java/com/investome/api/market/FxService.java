package com.investome.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class FxService {

    private static final long CACHE_MILLIS = Duration.ofMinutes(30).toMillis();

    private final MarketHttpClient httpClient;

    private volatile long cachedAt = 0L;
    private volatile FxRateResponse cachedResponse = new FxRateResponse("", null, null, null);

    public FxRateResponse getFxRates() {
        long now = System.currentTimeMillis();

        if (cachedResponse.getUSDKRW() != null && now - cachedAt < CACHE_MILLIS) {
            return cachedResponse;
        }

        try {
            JsonNode json = httpClient.fetchJsonNode("https://open.er-api.com/v6/latest/USD");
            JsonNode rates = json.path("rates");

            Double usdKrw = httpClient.toDouble(rates.path("KRW"));
            Double usdJpy = httpClient.toDouble(rates.path("JPY"));
            Double usdEur = httpClient.toDouble(rates.path("EUR"));

            FxRateResponse fresh = new FxRateResponse(
                    json.path("time_last_update_utc").asText(""),
                    usdKrw,
                    divide(usdKrw, usdJpy),
                    divide(usdKrw, usdEur)
            );

            cachedResponse = fresh;
            cachedAt = now;
            return fresh;
        } catch (Exception e) {
            return cachedResponse;
        }
    }

    private Double divide(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || denominator == 0) {
            return null;
        }
        return Math.round((numerator / denominator) * 100.0) / 100.0;
    }
}
