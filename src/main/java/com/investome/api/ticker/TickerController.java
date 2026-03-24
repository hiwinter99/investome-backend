package com.investome.api.ticker;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TickerController {

    private final TickerService tickerService;

    @GetMapping("/api/ticker")
    public TickerResponse getTicker() {
        return tickerService.getTicker();
    }
}