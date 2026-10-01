package com.investome.api.paper;

import java.time.Instant;

public record PaperOrderResponse(
        Long orderId, String requestId, String symbol, String side, String status,
        int quantity, long price, long totalAmount, long cashBalanceAfter,
        String priceSource, Instant executedAt, Instant quoteFetchedAt
) {
    public static PaperOrderResponse from(PaperOrder order) {
        return new PaperOrderResponse(order.getId(), order.getRequestId(), order.getSymbol(),
                order.getSide().name(), order.getStatus().name(), order.getQuantity(), order.getPrice(),
                order.getTotalAmount(), order.getCashBalanceAfter(), order.getPriceSource(), order.getExecutedAt(), order.getQuoteFetchedAt());
    }
}