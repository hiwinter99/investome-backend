package com.investome.api.paper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper")
public class PaperTradingController {
    private final PaperTradingService trading;
    private final PaperQuoteProvider quotes;
    private final PaperUniverseService universe;
    private final PaperChartService charts;

    @PostMapping("/orders/buy")
    public PaperOrderResponse buy(@AuthenticationPrincipal Long userId,
                                  @Valid @RequestBody BuyOrderRequest request) {
        return trading.buy(userId, request);
    }

    @PostMapping("/orders/sell")
    public PaperOrderResponse sell(@AuthenticationPrincipal Long userId,
                                   @Valid @RequestBody BuyOrderRequest request) {
        return trading.sell(userId, request);
    }

    @GetMapping("/holdings")
    public List<PaperHoldingResponse> holdings(@AuthenticationPrincipal Long userId) {
        return trading.getHoldings(userId);
    }

    @GetMapping("/orders")
    public List<PaperOrderResponse> orders(@AuthenticationPrincipal Long userId) {
        return trading.getOrders(userId);
    }

    @GetMapping("/quotes")
    public List<PaperQuoteProvider.Quote> quotes() { return quotes.getQuotes(); }
    @GetMapping("/symbols")
    public List<PaperStockCatalog.Stock> symbols() { return universe.stocks(); }

    @GetMapping("/quotes/{symbol}")
    public PaperQuoteProvider.Quote quote(@PathVariable String symbol) { return quotes.quoteOf(symbol); }

    @GetMapping("/charts/{symbol}")
    public PaperChartService.Chart chart(@PathVariable String symbol) { return charts.get(symbol); }
}