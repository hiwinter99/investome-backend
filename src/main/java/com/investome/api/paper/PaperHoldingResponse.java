package com.investome.api.paper;

import java.math.BigDecimal;

public record PaperHoldingResponse(String symbol, int quantity, BigDecimal averagePrice) {
    public static PaperHoldingResponse from(PaperHolding holding) {
        return new PaperHoldingResponse(holding.getSymbol(), holding.getQuantity(), holding.getAveragePrice());
    }
}