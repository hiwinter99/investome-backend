package com.investome.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketHttpClient {

    private final ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String fetchText(String url) throws IOException, InterruptedException {
        return fetchText(url, Map.of());
    }

    public String fetchText(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Investome Spring)")
                .header("Accept", "application/json,text/plain,*/*")
                .GET();

        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    builder.header(k, v);
                }
            });
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Upstream failed: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }

    public JsonNode fetchJsonNode(String url) throws IOException, InterruptedException {
        return objectMapper.readTree(fetchText(url));
    }

    public JsonNode fetchJsonNode(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return objectMapper.readTree(fetchText(url, headers));
    }

    public double fetchUsdKrwOrDefault() {
        try {
            JsonNode json = fetchJsonNode("https://api.frankfurter.app/latest?from=USD&to=KRW");
            JsonNode rate = json.path("rates").path("KRW");
            if (rate.isNumber()) {
                return rate.asDouble();
            }
        } catch (Exception ignored) {
        }
        return 1350.0;
    }

    public Double toDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.asDouble();
        try {
            double d = Double.parseDouble(node.asText().replace(",", "").trim());
            return Double.isFinite(d) ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Long toLong(JsonNode node) {
        Double d = toDouble(node);
        return d != null ? Math.round(d) : null;
    }
}