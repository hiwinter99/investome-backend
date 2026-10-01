package com.investome.api.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.*;

/** One shared KIS connection; authenticated browsers receive bounded SSE snapshots. */
@Service
public class PaperRealtimeService {
    private final ObjectMapper mapper;
    private final PaperUniverseService universe;
    private final Set<SseEmitter> viewers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService broadcaster = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, KisTradeParser.Trade> trades = new HashMap<>();
    private Set<String> subscribed = Set.of();
    private final Set<String> acknowledged = new HashSet<>();
    private LocalDate subscriptionDay;
    private List<PaperStockCatalog.Stock> activeStocks = List.of();
    private Instant lastFrameAt = Instant.now();
    private long revision, sentRevision = -1;
    private Instant publishedAt = Instant.EPOCH;
    private Instant retryAt = Instant.EPOCH;
    private int retryAttempts;
    public record Feed(String state, Map<String, KisTradeParser.Trade> trades, Set<String> subscribed, Instant serverTime, List<PaperStockCatalog.Stock> stocks) {}
    public synchronized Feed feed() { return new Feed(state, Map.copyOf(trades), Set.copyOf(acknowledged), Instant.now(), activeStocks); }
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(60_000L);
        viewers.add(emitter);
        emitter.onCompletion(() -> viewers.remove(emitter));
        emitter.onTimeout(() -> { viewers.remove(emitter); emitter.complete(); });
        emitter.onError(e -> viewers.remove(emitter));
        start();
        try { emitter.send(SseEmitter.event().name("quotes").data(feed())); }
        catch (Exception e) { viewers.remove(emitter); emitter.complete(); }
        return emitter;
    }
    private void publish() {
        try {
            if (viewers.isEmpty()) return;
            Feed next;
            synchronized (this) {
                viewedAt = Instant.now();
                if ((state.equals("ERROR") && viewedAt.isAfter(retryAt)) || state.equals("DISCONNECTED")) start();
                if (subscriptionDay != null && !subscriptionDay.equals(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
                    disconnect(); start();
                }
                if (sentRevision == revision && publishedAt.isAfter(Instant.now().minusSeconds(10))) return;
                sentRevision = revision; publishedAt = Instant.now(); next = feed();
            }
            for (SseEmitter emitter : viewers) {
                try { emitter.send(SseEmitter.event().name("quotes").data(next)); }
                catch (Exception e) { viewers.remove(emitter); emitter.complete(); }
            }
        } catch (Exception ignored) { /* keep heartbeat scheduler alive */ }
    }
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "paper-realtime"); t.setDaemon(true); return t;
    });
    @Value("${KIS_APP_KEY:}") private String appKey;
    @Value("${KIS_APP_SECRET:}") private String appSecret;
    @Value("${KIS_BASE_URL:https://openapi.koreainvestment.com:9443}") private String baseUrl;
    @Value("${KIS_WS_URL:ws://ops.koreainvestment.com:21000}") private String wsUrl;
    private WebSocket socket;
    private long generation;
    private Instant viewedAt = Instant.now();
    private String state = "DISCONNECTED";
    private KisTradeParser.Trade latest;
    public record Snapshot(String symbol, String state, KisTradeParser.Trade trade, boolean stale) {}

    public PaperRealtimeService(ObjectMapper mapper, PaperUniverseService universe) {
        this.mapper = mapper; this.universe = universe;
        broadcaster.scheduleWithFixedDelay(this::publish, 250, 250, TimeUnit.MILLISECONDS);
        worker.scheduleWithFixedDelay(this::expire, 30, 30, TimeUnit.SECONDS);
    }
    public synchronized Snapshot snapshot() {
        viewedAt = Instant.now();
        boolean stale = latest == null || !state.equals("LIVE") || latest.receivedAt().isBefore(Instant.now().minusSeconds(15));
        return new Snapshot(latest == null ? "" : latest.symbol(), state, latest, stale);
    }
    public synchronized Snapshot start() {
        viewedAt = Instant.now();
        if (state.equals("CONNECTING") || state.equals("SUBSCRIBING") || state.equals("WAITING") || state.equals("LIVE")) return snapshot();
        if (socket != null) { socket.abort(); socket = null; }
        long run = ++generation;
        latest = null; trades.clear(); acknowledged.clear(); state = "CONNECTING"; revision++;
        worker.execute(() -> connect(run));
        return snapshot();
    }
    private void connect(long run) {
        try {
            var codes = new LinkedHashSet<String>();
            var ranking = universe.stocks();
            for (var stock : ranking) codes.add(stock.symbol());
            synchronized (this) {
                if (run != generation) return;
                subscribed = codes; activeStocks = ranking; subscriptionDay = LocalDate.now(ZoneId.of("Asia/Seoul"));
            }
            if (appKey.isBlank() || appSecret.isBlank()) throw new IllegalStateException("Missing credentials");
            String body = mapper.writeValueAsString(Map.of("grant_type", "client_credentials", "appkey", appKey, "secretkey", appSecret));
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/oauth2/Approval"))
                    .header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Approval rejected");
            String approval = mapper.readTree(response.body()).path("approval_key").asText();
            if (approval.isBlank()) throw new IllegalStateException("Missing approval");
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                private final StringBuilder fragments = new StringBuilder();
                @Override public void onOpen(WebSocket ws) {
                    synchronized (PaperRealtimeService.this) {
                        if (run != generation) { ws.abort(); return; }
                        socket = ws; state = "SUBSCRIBING"; lastFrameAt = Instant.now();
                    }
                    worker.execute(() -> {
                        try {
                            for (String code : codes) {
                                synchronized (PaperRealtimeService.this) { if (run != generation) return; }
                                String subscribe = mapper.writeValueAsString(Map.of("header", Map.of("approval_key", approval,
                                    "custtype", "P", "tr_type", "1", "content-type", "utf-8"),
                                    "body", Map.of("input", Map.of("tr_id", "H0STCNT0", "tr_key", code))));
                                ws.sendText(subscribe, true).get(5, TimeUnit.SECONDS);
                                Thread.sleep(100);
                            }
                        } catch (Exception e) { fail(run); }
                    });
                    ws.request(1);
                }
                @Override public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
                    try {
                        fragments.append(text);
                        if (fragments.length() > 1_000_000) throw new IllegalArgumentException("Frame too large");
                        if (last) { String message = fragments.toString(); fragments.setLength(0); accept(run, ws, message); }
                    } catch (Exception e) { fail(run); }
                    ws.request(1); return null;
                }
                @Override public void onError(WebSocket ws, Throwable error) { fail(run); }
                @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { fail(run); return null; }
            }).get(12, TimeUnit.SECONDS);
            worker.schedule(() -> {
                synchronized (PaperRealtimeService.this) {
                    if (generation == run && acknowledged.size() < codes.size()) fail(run);
                }
            }, 15, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            fail(run);
        }
    }
    private synchronized void accept(long run, WebSocket ws, String message) throws Exception {
        if (run != generation) return;
        lastFrameAt = Instant.now();
        if (message.startsWith("{")) {
            var json = mapper.readTree(message);
            String tr = json.path("header").path("tr_id").asText();
            if (tr.equals("PINGPONG")) { ws.sendPong(java.nio.ByteBuffer.wrap(message.getBytes(java.nio.charset.StandardCharsets.UTF_8))); return; }
            if (tr.equals("H0STCNT0")) {
                if (!json.path("body").path("rt_cd").asText().equals("0")) { fail(run); return; }
                String code = json.path("header").path("tr_key").asText();
                if (subscribed.contains(code)) acknowledged.add(code);
                if (!state.equals("LIVE")) state = "WAITING";
                if (acknowledged.size() == subscribed.size()) retryAttempts = 0;
                revision++;
            }
            return;
        }
        for (var trade : KisTradeParser.parse(message)) {
            if (!subscribed.contains(trade.symbol())) continue;
            var latest = trades.get(trade.symbol());
            if (latest != null && (trade.tradedAt().compareTo(latest.tradedAt()) < 0
                    || (trade.tradedAt().equals(latest.tradedAt()) && trade.accumulatedVolume() <= latest.accumulatedVolume()))) continue;
            trades.put(trade.symbol(), trade); this.latest = trade; state = "LIVE"; revision++;
        }
    }
    private synchronized void fail(long run) {
        if (run != generation) return;
        generation++; state = "ERROR"; revision++;
        retryAt = Instant.now().plusSeconds(Math.min(60, 5L << Math.min(retryAttempts++, 4)));
        if (socket != null) { socket.abort(); socket = null; }
    }
    private synchronized void expire() {
        if (socket != null && lastFrameAt.isBefore(Instant.now().minusSeconds(120))) fail(generation);
        if (viewers.isEmpty() && viewedAt.isBefore(Instant.now().minusSeconds(90))) disconnect();
    }
    private synchronized void disconnect() {
        generation++; state = "DISCONNECTED"; latest = null; trades.clear(); acknowledged.clear(); subscriptionDay = null; revision++;
        if (socket != null) { socket.abort(); socket = null; }
    }
    @PreDestroy public void close() { disconnect(); worker.shutdownNow(); broadcaster.shutdownNow(); viewers.forEach(SseEmitter::complete); viewers.clear(); }
}
