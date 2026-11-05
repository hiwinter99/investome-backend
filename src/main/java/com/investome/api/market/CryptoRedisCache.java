package com.investome.api.market;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CryptoRedisCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    @Value("${investome.crypto.redis-enabled:false}")
    private boolean enabled;
    @Value("${investome.crypto.cache-key:investome:spring:crypto:top30:v1}")
    private String key;
    public record Lookup(boolean available, List<MarketItemResponse> items) {}

    public Lookup read() {
        if (!enabled) return new Lookup(false, List.of());
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null) return new Lookup(true, List.of());
            List<MarketItemResponse> items = mapper.readValue(raw, new TypeReference<List<MarketItemResponse>>() {});
            if (items == null || items.stream().anyMatch(item -> item == null || item.getSymbol() == null))
                return new Lookup(true, List.of());
            return new Lookup(true, List.copyOf(items));
        } catch (Exception e) {
            return new Lookup(false, List.of());
        }
    }

    public void write(List<MarketItemResponse> items) {
        if (!enabled || items.isEmpty()) return;
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(items), Duration.ofMinutes(5));
        } catch (Exception ignored) {
            // Redis is optional; a successful upstream response remains usable.
        }
    }
}
