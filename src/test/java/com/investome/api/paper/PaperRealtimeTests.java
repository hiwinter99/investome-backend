package com.investome.api.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.WebSocket;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperRealtimeTests {
    private String row(String symbol, String time, long price, long volume) {
        String[] fields = new String[46]; Arrays.fill(fields, "0");
        fields[0] = symbol; fields[1] = time; fields[2] = Long.toString(price);
        fields[7] = "69000"; fields[8] = "72000"; fields[9] = "68000";
        fields[13] = Long.toString(volume); fields[33] = "20260925";
        return String.join("^", fields);
    }
    @Test void parsesMultipleTradesAndRejectsBrokenPayloads() {
        var trades = KisTradeParser.parse("0|H0STCNT0|002|" + row("005930", "101010", 70000, 100) + "^" + row("000660", "101011", 150000, 200));
        assertEquals(2, trades.size()); assertEquals(70000, trades.get(0).price());
        assertEquals(69000, trades.get(0).open()); assertEquals(72000, trades.get(0).high());
        assertEquals("2026-09-25T10:10:10", trades.get(0).tradedAt());
        assertThrows(IllegalArgumentException.class, () -> KisTradeParser.parse("0|H0STCNT0|002|" + row("005930", "101010", 70000, 100)));
        assertThrows(IllegalArgumentException.class, () -> KisTradeParser.parse("0|H0STCNT0|001|" + row("005930", "101010", -1, 100)));
        assertTrue(KisTradeParser.parse("0|OTHER|001|ignored").isEmpty());
    }
    @Test void acceptsExtended47FieldBatchesWithoutShiftingNextTrade() {
        var trades = KisTradeParser.parse("0|H0STCNT0|002|" + row("005930", "150601", 271000, 100)
                + "^extension^" + row("005930", "150602", 271500, 102) + "^extension");
        assertEquals(2, trades.size());
        assertEquals(271500, trades.get(1).price());
        assertEquals(102, trades.get(1).accumulatedVolume());
    }
    @Test void acknowledgementIsNotTradeAndOldTicksCannotReplaceLatest() {
        var service = new PaperRealtimeService(new ObjectMapper(), mock(PaperUniverseService.class)); var ws = mock(WebSocket.class);
        try {
            ReflectionTestUtils.setField(service, "subscribed", java.util.Set.of("005930"));
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "{\"header\":{\"tr_id\":\"H0STCNT0\"},\"body\":{\"rt_cd\":\"0\"}}");
            assertEquals("WAITING", service.snapshot().state()); assertNull(service.snapshot().trade());
            assertTrue(service.snapshot().stale());
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "0|H0STCNT0|001|" + row("005930", "101010", 70000, 100));
            assertEquals("LIVE", service.snapshot().state()); assertFalse(service.snapshot().stale());
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "0|H0STCNT0|001|" + row("005930", "101009", 68000, 99));
            assertEquals(70000, service.snapshot().trade().price());
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "0|H0STCNT0|001|" + row("000660", "101011", 150000, 200));
            assertEquals("005930", service.snapshot().trade().symbol());
            ReflectionTestUtils.invokeMethod(service, "fail", 0L);
            assertEquals("ERROR", service.snapshot().state()); assertTrue(service.snapshot().stale());
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "0|H0STCNT0|001|" + row("005930", "101012", 71000, 110));
            assertEquals(70000, service.snapshot().trade().price());
        } finally { service.close(); }
    }
    @Test void heartbeatIsPongedWithoutTreatingItAsPrice() {
        var service = new PaperRealtimeService(new ObjectMapper(), mock(PaperUniverseService.class)); var ws = mock(WebSocket.class);
        try {
            ReflectionTestUtils.setField(service, "subscribed", java.util.Set.of("005930"));
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "{\"header\":{\"tr_id\":\"PINGPONG\"}}");
            verify(ws).sendPong(any()); assertNull(service.snapshot().trade());
        } finally { service.close(); }
    }
    @Test void keepsIndependentPricesForSubscribedSymbolsOnly() {
        var service = new PaperRealtimeService(new ObjectMapper(), mock(PaperUniverseService.class));
        try {
            ReflectionTestUtils.setField(service, "subscribed", java.util.Set.of("005930", "000660"));
            var ws = mock(WebSocket.class);
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "0|H0STCNT0|002|" + row("005930", "101010", 70000, 100) + "^" + row("000660", "101011", 150000, 200));
            assertEquals(2, service.feed().trades().size());
            assertEquals(70000, service.feed().trades().get("005930").price());
            assertEquals(150000, service.feed().trades().get("000660").price());
            ReflectionTestUtils.invokeMethod(service, "accept", 0L, ws, "0|H0STCNT0|001|" + row("035420", "101012", 200000, 300));
            assertEquals(2, service.feed().trades().size());
        } finally { service.close(); }
    }
}
