package com.investome.api.market;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FxController {

    private final FxService fxService;

    @GetMapping("/api/fx")
    public FxRateResponse getFxRates() {
        return fxService.getFxRates();
    }
}
