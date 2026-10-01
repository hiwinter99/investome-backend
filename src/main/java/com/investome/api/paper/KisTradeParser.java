package com.investome.api.paper;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** KIS H0STCNT0: one payload can contain multiple records (46 fields, or 47 with a trailing extension). */
public final class KisTradeParser {
    private KisTradeParser() {}
    public record Trade(String symbol, long price, long accumulatedVolume, String tradedAt, Instant receivedAt, long open, long high, long low) {}
    public static List<Trade> parse(String message) {
        String[] parts = message.split("\\|", 4);
        if (parts.length != 4 || !parts[0].equals("0") || !parts[1].equals("H0STCNT0")) return List.of();
        int count = Integer.parseInt(parts[2]);
        String[] fields = parts[3].split("\\^", -1);
        if (count < 1 || count > 100 || fields.length % count != 0
                || (fields.length / count != 46 && fields.length / count != 47)) throw new IllegalArgumentException("Invalid trade payload");
        int width = fields.length / count;
        List<Trade> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int offset = i * width;
            String symbol = fields[offset];
            long price = Long.parseLong(fields[offset + 2]);
            long volume = Long.parseLong(fields[offset + 13]);
            if (!symbol.matches("[0-9]{6}") || price <= 0 || volume < 0) throw new IllegalArgumentException("Invalid trade");
            LocalDate day = LocalDate.parse(fields[offset + 33], DateTimeFormatter.BASIC_ISO_DATE);
            LocalTime time = LocalTime.parse(fields[offset + 1], DateTimeFormatter.ofPattern("HHmmss"));
            result.add(new Trade(symbol, price, volume, LocalDateTime.of(day, time).toString(), Instant.now(),
                    Long.parseLong(fields[offset + 7]), Long.parseLong(fields[offset + 8]), Long.parseLong(fields[offset + 9])));
        }
        return result;
    }
}
