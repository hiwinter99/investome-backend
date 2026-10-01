package com.investome.api.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investome.api.exception.BadRequestException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class PaperStockCatalog {
    public record Stock(String symbol, String name, String market) {}
    private final Map<String, Stock> stocks;
    public PaperStockCatalog(ObjectMapper mapper) throws IOException {
        Map<String, Stock> loaded = new TreeMap<>();
        try (var input = new ClassPathResource("paper-stocks.json").getInputStream()) {
            for (var row : mapper.readTree(input)) {
                String symbol = row.path("symbol").asText();
                String market = row.path("market").asText();
                if (symbol.matches("[0-9]{6}") && Set.of("KOSPI", "KOSDAQ").contains(market))
                    loaded.put(symbol, new Stock(symbol, row.path("name").asText().replaceFirst("보통주$", "").replace("(주)", "").trim(), market));
            }
        }
        stocks = new java.util.concurrent.ConcurrentHashMap<>(loaded);
    }
    public List<Stock> all() { return List.copyOf(stocks.values()); }
    public Stock registerKospi(String symbol, String name) {
        if (symbol == null || !symbol.matches("[0-9]{6}") || name == null || name.isBlank())
            throw new IllegalArgumentException("Invalid ranking symbol");
        return stocks.computeIfAbsent(symbol, code -> new Stock(code, name, "KOSPI"));
    }
    public Stock require(String symbol) {
        Stock stock = stocks.get(symbol);
        if (stock == null) throw new BadRequestException("지원 종목 목록에서 종목을 선택해 주세요.");
        return stock;
    }
}
