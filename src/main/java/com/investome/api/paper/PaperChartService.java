package com.investome.api.paper;

import com.investome.api.market.StockMarketService;
import com.investome.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PaperChartService {
    private final PaperStockCatalog catalog;
    private final StockMarketService market;
    public record Chart(String symbol, String source, Instant fetchedAt, List<StockMarketService.DailyCandle> candles) {}
    private final ConcurrentHashMap<String, Chart> cache = new ConcurrentHashMap<>();
    public Chart get(String symbol) {
        catalog.require(symbol);
        Chart cached = cache.get(symbol);
        if (cached != null && cached.fetchedAt().isAfter(Instant.now().minusSeconds(60))) return cached;
        try {
            Chart next = new Chart(symbol, "KIS_KRX_ADJUSTED", Instant.now(), market.getKoreanDailyChart(symbol));
            cache.put(symbol, next);
            return next;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHART_UNAVAILABLE", "차트를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
