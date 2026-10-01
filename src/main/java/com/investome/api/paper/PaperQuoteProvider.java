package com.investome.api.paper;

import com.investome.api.exception.BadRequestException;
import com.investome.api.exception.ApiException;
import com.investome.api.market.StockMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaperQuoteProvider {
    private final StockMarketService market;
    private final PaperStockCatalog catalog;
    public record Quote(String symbol, String name, long price, String source, Instant fetchedAt) {}

    public List<Quote> getQuotes() {
        return List.of(quoteOf("005930"), quoteOf("000660"));
    }

    public Quote quoteOf(String symbol) {
        String name = catalog.require(symbol).name();
        try {
            long price = market.getCurrentKoreanPrice(symbol);
            if (price <= 0) throw new IllegalStateException("Invalid quote");
            return new Quote(symbol, name, price, "KIS_KRX", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QUOTE_UNAVAILABLE",
                "현재가를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
