package com.investome.api.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CryptoCacheTests {
    @Test void sharedHitSkipsUpstream() {
        var http = mock(MarketHttpClient.class);
        var redis = mock(CryptoRedisCache.class);
        var items = List.of(MarketItemResponse.builder().symbol("BTC").build());
        when(redis.read()).thenReturn(new CryptoRedisCache.Lookup(true, items));
        assertEquals(items, new CryptoMarketService(http, redis).getTop30());
        verifyNoInteractions(http);
    }

    @Test void missWritesRedisAndOutageUsesMemoryThenStaleData() throws Exception {
        var http = mock(MarketHttpClient.class);
        var redis = mock(CryptoRedisCache.class);
        when(redis.read()).thenReturn(new CryptoRedisCache.Lookup(true, List.of()));
        when(http.fetchJsonNode(anyString())).thenReturn(new ObjectMapper().readTree("""
            [{"id":"bitcoin","symbol":"btc","name":"Bitcoin","current_price":100}]
            """));
        var service = new CryptoMarketService(http, redis);
        var items = service.getTop30();
        assertEquals("BTC", items.get(0).getSymbol());
        verify(redis).write(items);
        when(redis.read()).thenReturn(new CryptoRedisCache.Lookup(false, List.of()));
        assertEquals(items, service.getTop30());
        verify(http, times(1)).fetchJsonNode(anyString());
        ReflectionTestUtils.setField(service, "cachedAt", 0L);
        when(http.fetchJsonNode(anyString())).thenThrow(new java.io.IOException("offline"));
        assertEquals(items, service.getTop30());
    }
}
