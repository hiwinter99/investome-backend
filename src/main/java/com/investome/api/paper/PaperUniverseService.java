package com.investome.api.paper;

import com.investome.api.market.StockMarketService;
import com.investome.api.exception.ApiException;
import com.investome.api.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/** Persist the first successful KST daily ranking; restarts do not change the trading universe. */
@Service @RequiredArgsConstructor
public class PaperUniverseService {
    private final StockMarketService market;
    private final PaperStockCatalog catalog;
    private final PaperUniverseRepository repository;
    private final TransactionTemplate transaction;
    @jakarta.annotation.PostConstruct
    void restoreCatalog() {
        repository.findAll().forEach(e -> catalog.registerKospi(e.getSymbol(), e.getName()));
    }
    public synchronized List<PaperStockCatalog.Stock> stocks() {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var saved = repository.findBySnapshotDayOrderByPositionAsc(day);
        if (saved.isEmpty()) {
            try {
                var ranking = market.getFreshKospiTop30();
                var symbols = new LinkedHashSet<String>();
                var entries = new ArrayList<PaperUniverseEntry>();
                for (var item : ranking) {
                    var stock = catalog.registerKospi(item.getSymbol(), item.getName());
                    if (!stock.market().equals("KOSPI") || !symbols.add(stock.symbol())) continue;
                    entries.add(new PaperUniverseEntry(day, stock.symbol(), stock.name(), entries.size()));
                }
                if (entries.size() != 30) throw new IllegalStateException("Incomplete ranking");
                saved = transaction.execute(status -> repository.saveAllAndFlush(entries));
            } catch (Exception e) {
                // Another server may have persisted the same date concurrently.
                saved = repository.findBySnapshotDayOrderByPositionAsc(day);
                if (saved.size() != 30) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TOP30_UNAVAILABLE", "오늘의 TOP30을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
            }
        }
        return saved.stream().map(e -> catalog.registerKospi(e.getSymbol(), e.getName())).toList();
    }
    public void requireBuyable(String symbol) {
        if (stocks().stream().noneMatch(s -> s.symbol().equals(symbol)))
            throw new BadRequestException("신규 매수는 오늘의 코스피 TOP30 종목만 가능합니다.");
    }
}
