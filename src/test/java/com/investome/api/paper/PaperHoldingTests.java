package com.investome.api.paper;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PaperHoldingTests {
    private PaperHolding holding() {
        return new PaperHolding(new PaperAccount(1L), "005930", 2, new BigDecimal("60000"));
    }

    @Test
    void firstPurchasePreservesSymbolAndInitialHolding() {
        PaperHolding h = holding();
        assertEquals("005930", h.getSymbol());
        assertEquals(2, h.getQuantity());
        assertEquals(new BigDecimal("60000.0000"), h.getAveragePrice());
        assertEquals(10_000_000L, h.getAccount().getCashBalance());
    }

    @Test
    void additionalPurchaseUsesWeightedAverage() {
        PaperHolding h = holding();
        h.buyMore(3, new BigDecimal("70000"));
        assertEquals(5, h.getQuantity());
        assertEquals(new BigDecimal("66000.0000"), h.getAveragePrice());
    }

    @Test
    void repeatingDecimalIsRoundedToFourPlaces() {
        PaperHolding h = new PaperHolding(new PaperAccount(1L), "005930", 1, new BigDecimal("1"));
        h.buyMore(2, new BigDecimal("2"));
        assertEquals(new BigDecimal("1.6667"), h.getAveragePrice());
    }

    @Test
    void invalidPurchasesDoNotChangeExistingHolding() {
        PaperHolding h = holding();
        assertThrows(IllegalArgumentException.class, () -> h.buyMore(0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> h.buyMore(-1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> h.buyMore(1, null));
        assertThrows(IllegalArgumentException.class, () -> h.buyMore(1, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> h.buyMore(1, new BigDecimal("-1")));
        assertEquals(2, h.getQuantity());
        assertEquals(new BigDecimal("60000.0000"), h.getAveragePrice());
    }

    @Test
    void quantityOverflowDoesNotChangeHolding() {
        PaperHolding h = new PaperHolding(new PaperAccount(1L), "005930", Integer.MAX_VALUE, BigDecimal.ONE);
        assertThrows(ArithmeticException.class, () -> h.buyMore(1, BigDecimal.ONE));
        assertEquals(Integer.MAX_VALUE, h.getQuantity());
        assertEquals(new BigDecimal("1.0000"), h.getAveragePrice());
    }

    @Test
    void firstPurchaseRejectsMissingOrInvalidData() {
        PaperAccount a = new PaperAccount(1L);
        assertThrows(IllegalArgumentException.class, () -> new PaperHolding(null, "005930", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new PaperHolding(a, " ", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new PaperHolding(a, "005930", 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new PaperHolding(a, "005930", 1, BigDecimal.ZERO));
    }
}