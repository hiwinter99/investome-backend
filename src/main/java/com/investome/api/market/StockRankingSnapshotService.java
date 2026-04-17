package com.investome.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockRankingSnapshotService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long PRICE_TTL_MS = Duration.ofSeconds(20).toMillis();
    private static final long FX_TTL_MS = Duration.ofSeconds(20).toMillis();
    private static final long KIS_TOKEN_TTL_MS = Duration.ofHours(23).toMillis();
    private static final List<NasdaqSnapshotBase> NASDAQ_FIXED_UNIVERSE = List.of(
            new NasdaqSnapshotBase("AAPL", "Apple Inc.", "apple.com"),
            new NasdaqSnapshotBase("MSFT", "Microsoft Corporation", "microsoft.com"),
            new NasdaqSnapshotBase("NVDA", "NVIDIA Corporation", "nvidia.com"),
            new NasdaqSnapshotBase("AMZN", "Amazon.com, Inc.", "amazon.com"),
            new NasdaqSnapshotBase("GOOGL", "Alphabet Inc. Class A", "google.com"),
            new NasdaqSnapshotBase("GOOG", "Alphabet Inc. Class C", "google.com"),
            new NasdaqSnapshotBase("META", "Meta Platforms, Inc.", "meta.com"),
            new NasdaqSnapshotBase("AVGO", "Broadcom Inc.", "broadcom.com"),
            new NasdaqSnapshotBase("TSLA", "Tesla, Inc.", "tesla.com"),
            new NasdaqSnapshotBase("COST", "Costco Wholesale Corporation", "costco.com"),
            new NasdaqSnapshotBase("NFLX", "Netflix, Inc.", "netflix.com"),
            new NasdaqSnapshotBase("ASML", "ASML Holding N.V.", "asml.com"),
            new NasdaqSnapshotBase("TMUS", "T-Mobile US, Inc.", "t-mobile.com"),
            new NasdaqSnapshotBase("CSCO", "Cisco Systems, Inc.", "cisco.com"),
            new NasdaqSnapshotBase("AMD", "Advanced Micro Devices, Inc.", "amd.com"),
            new NasdaqSnapshotBase("PEP", "PepsiCo, Inc.", "pepsico.com"),
            new NasdaqSnapshotBase("LIN", "Linde plc", "linde.com"),
            new NasdaqSnapshotBase("INTU", "Intuit Inc.", "intuit.com"),
            new NasdaqSnapshotBase("QCOM", "QUALCOMM Incorporated", "qualcomm.com"),
            new NasdaqSnapshotBase("AMGN", "Amgen Inc.", "amgen.com"),
            new NasdaqSnapshotBase("TXN", "Texas Instruments Incorporated", "ti.com"),
            new NasdaqSnapshotBase("INTC", "Intel Corporation", "intel.com"),
            new NasdaqSnapshotBase("HON", "Honeywell International Inc.", "honeywell.com"),
            new NasdaqSnapshotBase("AMAT", "Applied Materials, Inc.", "appliedmaterials.com"),
            new NasdaqSnapshotBase("BKNG", "Booking Holdings Inc.", "bookingholdings.com"),
            new NasdaqSnapshotBase("ISRG", "Intuitive Surgical, Inc.", "intuitive.com"),
            new NasdaqSnapshotBase("ADBE", "Adobe Inc.", "adobe.com"),
            new NasdaqSnapshotBase("MU", "Micron Technology, Inc.", "micron.com"),
            new NasdaqSnapshotBase("ADP", "Automatic Data Processing, Inc.", "adp.com"),
            new NasdaqSnapshotBase("LRCX", "Lam Research Corporation", "lamresearch.com")
    );

    private final MarketHttpClient http;
    private final MarketRankSnapshotItemRepository snapshotItemRepository;
    private final ObjectMapper objectMapper;

    @Value("${kis.app-key:}")
    private String kisAppKey;

    @Value("${kis.app-secret:}")
    private String kisAppSecret;

    @Value("${kis.base-url:https://openapi.koreainvestment.com:9443}")
    private String kisBaseUrl;

    @Value("${kis.market-cap-tr-id:FHPST01740000}")
    private String kisMarketCapTrId;

    @Value("${fmp.api-key:}")
    private String fmpApiKey;

    private final Map<String, PriceCacheBucket> priceCache = new ConcurrentHashMap<>();

    private volatile String kisAccessToken = "";
    private volatile long kisAccessTokenAt = 0L;

    @Transactional
    public List<MarketItemResponse> getTop30(String market) {
        String normalizedMarket = normalizeMarket(market);
        if (normalizedMarket == null) {
            return List.of();
        }

        try {
            List<MarketRankSnapshotItem> snapshotItems = ensureTodaySnapshot(normalizedMarket);
            return ensureLivePriceSnapshot(normalizedMarket, snapshotItems);
        } catch (Exception e) {
            log.warn("Failed to build {} top30 from today's snapshot: {}", normalizedMarket, e.getMessage());
            List<MarketRankSnapshotItem> fallbackItems = findLatestSnapshot(normalizedMarket);
            if (!fallbackItems.isEmpty()) {
                try {
                    return ensureLivePriceSnapshot(normalizedMarket, fallbackItems);
                } catch (Exception ignored) {
                    return toMarketItems(fallbackItems);
                }
            }
            return List.of();
        }
    }

    @Transactional
    public void refreshTodaySnapshots() {
        LocalDate today = LocalDate.now(SEOUL);
        refreshSnapshotForDate("KOSPI", today);
        refreshSnapshotForDate("NASDAQ", today);
    }

    private List<MarketRankSnapshotItem> ensureTodaySnapshot(String market) {
        LocalDate today = LocalDate.now(SEOUL);
        List<MarketRankSnapshotItem> todayItems =
                snapshotItemRepository.findByMarketAndSnapshotDateOrderByRankOrderAsc(market, today);

        if (!todayItems.isEmpty()) {
            return todayItems;
        }

        refreshSnapshotForDate(market, today);
        return snapshotItemRepository.findByMarketAndSnapshotDateOrderByRankOrderAsc(market, today);
    }

    @Transactional
    public void refreshSnapshotForDate(String market, LocalDate snapshotDate) {
        String normalizedMarket = normalizeMarket(market);
        if (normalizedMarket == null) {
            return;
        }

        try {
            List<MarketItemResponse> rankedItems =
                    "KOSPI".equals(normalizedMarket)
                            ? buildKospiRankSnapshot()
                            : buildNasdaqRankSnapshot();

            if (rankedItems.isEmpty()) {
                throw new IllegalStateException(normalizedMarket + " rank snapshot build failed");
            }

            snapshotItemRepository.deleteByMarketAndSnapshotDate(normalizedMarket, snapshotDate);
            snapshotItemRepository.saveAll(
                    rankedItems.stream()
                            .map(item -> toSnapshotEntity(item, snapshotDate, normalizedMarket))
                            .toList()
            );

            PriceCacheBucket bucket = getPriceBucket(normalizedMarket);
            bucket.snapshotDate = null;
            bucket.items = new ArrayList<>();
            bucket.at = 0L;
        } catch (Exception e) {
            log.warn("Snapshot refresh failed for {} {}: {}", normalizedMarket, snapshotDate, e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private List<MarketRankSnapshotItem> findLatestSnapshot(String market) {
        LocalDate latestDate = snapshotItemRepository.findLatestSnapshotDate(market, LocalDate.now(SEOUL));
        if (latestDate == null) {
            return List.of();
        }
        return snapshotItemRepository.findByMarketAndSnapshotDateOrderByRankOrderAsc(market, latestDate);
    }

    private List<MarketItemResponse> ensureLivePriceSnapshot(
            String market,
            List<MarketRankSnapshotItem> snapshotItems
    ) throws Exception {
        LocalDate snapshotDate = snapshotItems.stream()
                .findFirst()
                .map(MarketRankSnapshotItem::getSnapshotDate)
                .orElse(LocalDate.now(SEOUL));

        PriceCacheBucket bucket = getPriceBucket(market);
        long now = System.currentTimeMillis();
        if (!bucket.items.isEmpty()
                && snapshotDate.equals(bucket.snapshotDate)
                && now - bucket.at < PRICE_TTL_MS) {
            return bucket.items;
        }

        List<MarketItemResponse> pricedItems =
                "KOSPI".equals(market)
                        ? buildKospiLivePrices(snapshotItems)
                        : buildNasdaqLivePrices(snapshotItems);

        bucket.snapshotDate = snapshotDate;
        bucket.items = pricedItems;
        bucket.at = now;
        return pricedItems;
    }

    private List<MarketItemResponse> buildKospiLivePrices(List<MarketRankSnapshotItem> snapshotItems) throws Exception {
        List<String> yahooSymbols = snapshotItems.stream()
                .map(item -> item.getSymbol() + ".KS")
                .toList();

        Map<String, JsonNode> quoteMap = new HashMap<>();
        try {
            for (JsonNode quote : fetchYahooQuoteBatch(yahooSymbols)) {
                quoteMap.put(quote.path("symbol").asText("").toUpperCase(Locale.ROOT), quote);
            }
        } catch (Exception e) {
            log.warn("Yahoo quote batch failed for KOSPI live prices, falling back to chart meta: {}", e.getMessage());
        }

        List<MarketItemResponse> items = new ArrayList<>();
        for (MarketRankSnapshotItem snapshotItem : snapshotItems) {
            JsonNode quote = quoteMap.getOrDefault((snapshotItem.getSymbol() + ".KS").toUpperCase(Locale.ROOT), null);

            Double price = quote == null ? null : firstNonNull(
                    http.toDouble(quote.path("regularMarketPrice")),
                    http.toDouble(quote.path("regularMarketPreviousClose"))
            );
            Double changePct = quote == null ? null : http.toDouble(quote.path("regularMarketChangePercent"));

            if (price == null) {
                YahooChartMeta fallback = fetchYahooChartMeta(snapshotItem.getSymbol() + ".KS");
                price = fallback.price();
                changePct = changePct != null ? changePct : fallback.changePct();
            }

            items.add(MarketItemResponse.builder()
                    .rank(snapshotItem.getRankOrder())
                    .market("KOSPI")
                    .name(snapshotItem.getName())
                    .displayNameEN(snapshotItem.getDisplayNameEN())
                    .symbol(snapshotItem.getSymbol())
                    .iconUrl(snapshotItem.getIconUrl())
                    .coinId("")
                    .capKRW(snapshotItem.getCapKRW())
                    .priceKRW(price)
                    .changePct(changePct)
                    .build());
        }
        return items;
    }

    private List<MarketItemResponse> buildNasdaqLivePrices(List<MarketRankSnapshotItem> snapshotItems) throws Exception {
        double usdKrw = getUsdKrwCached();

        Map<String, JsonNode> quoteMap = new HashMap<>();
        try {
            for (JsonNode quote : fetchYahooQuoteBatch(
                    snapshotItems.stream().map(MarketRankSnapshotItem::getSymbol).toList()
            )) {
                quoteMap.put(quote.path("symbol").asText("").toUpperCase(Locale.ROOT), quote);
            }
        } catch (Exception e) {
            log.warn("Yahoo quote batch failed for NASDAQ live prices, falling back to chart meta: {}", e.getMessage());
        }

        List<MarketItemResponse> items = new ArrayList<>();
        for (MarketRankSnapshotItem snapshotItem : snapshotItems) {
            JsonNode quote = quoteMap.getOrDefault(snapshotItem.getSymbol().toUpperCase(Locale.ROOT), null);

            Double priceUsd = quote == null ? null : firstNonNull(
                    http.toDouble(quote.path("regularMarketPrice")),
                    http.toDouble(quote.path("postMarketPrice")),
                    http.toDouble(quote.path("preMarketPrice")),
                    http.toDouble(quote.path("regularMarketPreviousClose"))
            );
            Double changePct = quote == null ? null : firstNonNull(
                    http.toDouble(quote.path("regularMarketChangePercent")),
                    http.toDouble(quote.path("postMarketChangePercent")),
                    http.toDouble(quote.path("preMarketChangePercent"))
            );

            if (priceUsd == null) {
                YahooChartMeta fallback = fetchYahooChartMeta(snapshotItem.getSymbol());
                priceUsd = fallback.price();
                changePct = changePct != null ? changePct : fallback.changePct();
            }

            items.add(MarketItemResponse.builder()
                    .rank(snapshotItem.getRankOrder())
                    .market("NASDAQ")
                    .name(snapshotItem.getName())
                    .displayNameEN(snapshotItem.getDisplayNameEN())
                    .symbol(snapshotItem.getSymbol())
                    .iconUrl(snapshotItem.getIconUrl())
                    .coinId("")
                    .capKRW(snapshotItem.getCapKRW())
                    .priceKRW(priceUsd != null ? round2(priceUsd * usdKrw) : null)
                    .changePct(changePct)
                    .build());
        }

        return items;
    }

    private List<MarketItemResponse> buildKospiRankSnapshot() throws Exception {
        Map<String, StockNameOverride> masterMap = getKospiMasterMap();
        List<JsonNode> rows = fetchKisMarketCapTop30();
        List<MarketItemResponse> items = new ArrayList<>();

        int index = 0;
        for (JsonNode row : rows) {
            String symbol = normalizeSymbol(firstNonEmpty(row,
                    "mksc_shrn_iscd", "stck_shrn_iscd", "shrn_iscd", "isu_cd", "iscd", "stck_shrn_iscd1"));
            if (symbol.isBlank()) {
                continue;
            }

            StockNameOverride master = masterMap.get(symbol);
            String rawName = firstNonEmpty(row, "hts_kor_isnm", "prdt_name", "stck_name");
            Long capKRW = toLong(firstNonEmpty(row, "stck_avls", "data_valx", "mkt_cap"));
            Integer rank = toInteger(firstNonEmpty(row, "data_rank", "rank"));

            if (capKRW == null) {
                continue;
            }

            items.add(MarketItemResponse.builder()
                    .rank(rank != null ? rank : index + 1)
                    .market("KOSPI")
                    .name(master != null ? master.name() : normalizeCompanyName(rawName))
                    .displayNameEN(master != null ? master.displayNameEN() : normalizeCompanyName(rawName))
                    .symbol(symbol)
                    .iconUrl(buildLogo(pickStockDomain(symbol)))
                    .coinId("")
                    .capKRW(capKRW)
                    .priceKRW(null)
                    .changePct(null)
                    .build());
            index += 1;
        }

        items.sort(Comparator.comparingInt(item -> item.getRank() != null ? item.getRank() : 9999));
        List<MarketItemResponse> top30 = new ArrayList<>();
        for (int i = 0; i < Math.min(30, items.size()); i += 1) {
            MarketItemResponse item = items.get(i);
            item.setRank(i + 1);
            top30.add(item);
        }
        return top30;
    }

    private List<MarketItemResponse> buildNasdaqRankSnapshot() throws Exception {
        if (fmpApiKey != null && !fmpApiKey.isBlank()) {
            try {
                return buildNasdaqRankSnapshotFromFmp();
            } catch (Exception e) {
                log.warn("FMP NASDAQ screener unavailable, falling back to fixed large-cap universe: {}", e.getMessage());
            }
        }

        return buildNasdaqFixedUniverseSnapshot();
    }

    private List<MarketItemResponse> buildNasdaqRankSnapshotFromFmp() throws Exception {
        double usdKrw = getUsdKrwCached();
        String url = "https://financialmodelingprep.com/stable/company-screener"
                + "?exchange=NASDAQ"
                + "&isEtf=false"
                + "&isFund=false"
                + "&isActivelyTrading=true"
                + "&limit=5000"
                + "&apikey=" + URLEncoder.encode(fmpApiKey, StandardCharsets.UTF_8);

        JsonNode payload = http.fetchJsonNode(url);
        if (!payload.isArray()) {
            throw new IllegalStateException("FMP screener payload is invalid");
        }

        List<MarketItemResponse> items = new ArrayList<>();
        for (JsonNode row : payload) {
            String symbol = row.path("symbol").asText("").trim().toUpperCase(Locale.ROOT);
            if (symbol.isBlank()) {
                continue;
            }

            Double marketCapUsd = firstNonNull(
                    http.toDouble(row.path("marketCap")),
                    http.toDouble(row.path("marketCapUsd")),
                    http.toDouble(row.path("companyMarketCap"))
            );
            if (marketCapUsd == null || marketCapUsd <= 0) {
                continue;
            }

            String rawName = nonBlank(
                    row.path("companyName").asText(""),
                    row.path("name").asText(""),
                    symbol
            );
            String domain = extractDomain(nonBlank(
                    row.path("website").asText(""),
                    row.path("websiteUrl").asText(""),
                    pickStockDomain(symbol)
            ));

            items.add(MarketItemResponse.builder()
                    .rank(0)
                    .market("NASDAQ")
                    .name(rawName)
                    .displayNameEN(rawName)
                    .symbol(symbol)
                    .iconUrl(buildLogo(nonBlank(domain, pickStockDomain(symbol))))
                    .coinId("")
                    .capKRW(Math.round(marketCapUsd * usdKrw))
                    .priceKRW(null)
                    .changePct(null)
                    .build());
        }

        items.sort((a, b) -> Long.compare(
                b.getCapKRW() != null ? b.getCapKRW() : 0L,
                a.getCapKRW() != null ? a.getCapKRW() : 0L
        ));

        List<MarketItemResponse> top30 = new ArrayList<>();
        for (int i = 0; i < Math.min(30, items.size()); i += 1) {
            MarketItemResponse item = items.get(i);
            item.setRank(i + 1);
            top30.add(item);
        }

        if (top30.isEmpty()) {
            throw new IllegalStateException("NASDAQ top30 normalization failed");
        }

        return top30;
    }

    private List<MarketItemResponse> buildNasdaqFixedUniverseSnapshot() {
        List<MarketItemResponse> items = new ArrayList<>();
        for (int i = 0; i < NASDAQ_FIXED_UNIVERSE.size(); i += 1) {
            NasdaqSnapshotBase item = NASDAQ_FIXED_UNIVERSE.get(i);
            items.add(MarketItemResponse.builder()
                    .rank(i + 1)
                    .market("NASDAQ")
                    .name(item.name())
                    .displayNameEN(item.name())
                    .symbol(item.symbol())
                    .iconUrl(buildLogo(item.domain()))
                    .coinId("")
                    .capKRW(null)
                    .priceKRW(null)
                    .changePct(null)
                    .build());
        }
        return items;
    }

    private List<JsonNode> fetchYahooQuoteBatch(List<String> symbols) throws Exception {
        String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols="
                + URLEncoder.encode(String.join(",", symbols), StandardCharsets.UTF_8);
        JsonNode result = http.fetchJsonNode(url).path("quoteResponse").path("result");
        List<JsonNode> rows = new ArrayList<>();
        if (result.isArray()) {
            result.forEach(rows::add);
        }
        return rows;
    }

    private YahooChartMeta fetchYahooChartMeta(String symbol) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/"
                + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "?interval=1d&range=5d&includePrePost=false";

        JsonNode meta = http.fetchJsonNode(url)
                .path("chart")
                .path("result")
                .path(0)
                .path("meta");

        if (meta.isMissingNode() || meta.isNull()) {
            return new YahooChartMeta(null, null);
        }

        Double price = firstNonNull(
                http.toDouble(meta.path("regularMarketPrice")),
                http.toDouble(meta.path("previousClose")),
                http.toDouble(meta.path("chartPreviousClose"))
        );

        Double previousClose = firstNonNull(
                http.toDouble(meta.path("previousClose")),
                http.toDouble(meta.path("chartPreviousClose"))
        );

        Double changePct = null;
        if (price != null && previousClose != null && previousClose != 0) {
            changePct = ((price - previousClose) / previousClose) * 100.0;
        }

        return new YahooChartMeta(price, changePct);
    }

    private List<JsonNode> fetchKisMarketCapTop30() throws Exception {
        String token = getKisAccessToken();
        String url = kisBaseUrl + "/uapi/domestic-stock/v1/ranking/market-cap"
                + "?fid_cond_mrkt_div_code=J"
                + "&fid_cond_scr_div_code=20174"
                + "&fid_div_cls_code=0"
                + "&fid_input_iscd=0000"
                + "&fid_trgt_cls_code=0"
                + "&fid_trgt_exls_cls_code=0"
                + "&fid_input_price_1="
                + "&fid_input_price_2="
                + "&fid_vol_cnt=";

        JsonNode output = http.fetchJsonNode(url, Map.of(
                "Content-Type", "application/json",
                "authorization", "Bearer " + token,
                "appkey", kisAppKey,
                "appsecret", kisAppSecret,
                "tr_id", kisMarketCapTrId
        )).path("output");

        List<JsonNode> rows = new ArrayList<>();
        if (output.isArray()) {
            output.forEach(rows::add);
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException("KIS market-cap output is empty");
        }

        return rows.stream().limit(30).toList();
    }

    private String getKisAccessToken() throws Exception {
        if (kisAppKey == null || kisAppKey.isBlank() || kisAppSecret == null || kisAppSecret.isBlank()) {
            throw new IllegalStateException("KIS credentials are missing");
        }

        long now = System.currentTimeMillis();
        if (!kisAccessToken.isBlank() && now - kisAccessTokenAt < KIS_TOKEN_TTL_MS) {
            return kisAccessToken;
        }

        String body = """
                {
                  "grant_type":"client_credentials",
                  "appkey":"%s",
                  "appsecret":"%s"
                }
                """.formatted(kisAppKey, kisAppSecret);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(kisBaseUrl + "/oauth2/tokenP"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Investome Spring)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("KIS token failed: " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String token = json.path("access_token").asText("").trim();
        if (token.isBlank()) {
            throw new IllegalStateException("KIS access token not returned");
        }

        kisAccessToken = token;
        kisAccessTokenAt = now;
        return token;
    }

    private double getUsdKrwCached() {
        PriceCacheBucket bucket = getPriceBucket("FX");
        long now = System.currentTimeMillis();

        if (bucket.usdKrw != null && now - bucket.fxAt < FX_TTL_MS) {
            return bucket.usdKrw;
        }

        bucket.usdKrw = http.fetchUsdKrwOrDefault();
        bucket.fxAt = now;
        return bucket.usdKrw;
    }

    private MarketRankSnapshotItem toSnapshotEntity(
            MarketItemResponse item,
            LocalDate snapshotDate,
            String market
    ) {
        MarketRankSnapshotItem entity = new MarketRankSnapshotItem();
        entity.setMarket(market);
        entity.setSnapshotDate(snapshotDate);
        entity.setRankOrder(item.getRank());
        entity.setSymbol(item.getSymbol());
        entity.setName(item.getName());
        entity.setDisplayNameEN(item.getDisplayNameEN());
        entity.setIconUrl(item.getIconUrl());
        entity.setCapKRW(item.getCapKRW());
        entity.setSource("KOSPI".equals(market) ? "KIS" : "FMP");
        return entity;
    }

    private List<MarketItemResponse> toMarketItems(List<MarketRankSnapshotItem> snapshotItems) {
        return snapshotItems.stream()
                .map(item -> MarketItemResponse.builder()
                        .rank(item.getRankOrder())
                        .market(item.getMarket())
                        .name(item.getName())
                        .displayNameEN(item.getDisplayNameEN())
                        .symbol(item.getSymbol())
                        .iconUrl(item.getIconUrl())
                        .coinId("")
                        .capKRW(item.getCapKRW())
                        .priceKRW(null)
                        .changePct(null)
                        .build())
                .toList();
    }

    private String normalizeMarket(String market) {
        String normalized = String.valueOf(market).toUpperCase(Locale.ROOT);
        if (!normalized.equals("KOSPI") && !normalized.equals("NASDAQ")) {
            return null;
        }
        return normalized;
    }

    private Map<String, StockNameOverride> getKospiMasterMap() {
        return Map.ofEntries(
                Map.entry("005930", new StockNameOverride("삼성전자", "Samsung Electronics")),
                Map.entry("005935", new StockNameOverride("삼성전자우", "Samsung Electronics Pref")),
                Map.entry("000660", new StockNameOverride("SK하이닉스", "SK hynix")),
                Map.entry("373220", new StockNameOverride("LG에너지솔루션", "LG Energy Solution")),
                Map.entry("207940", new StockNameOverride("삼성바이오로직스", "Samsung Biologics")),
                Map.entry("005380", new StockNameOverride("현대차", "Hyundai Motor")),
                Map.entry("068270", new StockNameOverride("셀트리온", "Celltrion")),
                Map.entry("000270", new StockNameOverride("기아", "Kia")),
                Map.entry("105560", new StockNameOverride("KB금융", "KB Financial Group")),
                Map.entry("035420", new StockNameOverride("NAVER", "NAVER")),
                Map.entry("055550", new StockNameOverride("신한지주", "Shinhan Financial Group")),
                Map.entry("005490", new StockNameOverride("POSCO홀딩스", "POSCO Holdings")),
                Map.entry("006400", new StockNameOverride("삼성SDI", "Samsung SDI")),
                Map.entry("035720", new StockNameOverride("카카오", "Kakao")),
                Map.entry("051910", new StockNameOverride("LG화학", "LG Chem")),
                Map.entry("028260", new StockNameOverride("삼성물산", "Samsung C&T")),
                Map.entry("086790", new StockNameOverride("하나금융지주", "Hana Financial Group")),
                Map.entry("015760", new StockNameOverride("한국전력", "KEPCO")),
                Map.entry("329180", new StockNameOverride("HD현대중공업", "HD Hyundai Heavy Industries")),
                Map.entry("138040", new StockNameOverride("메리츠금융지주", "Meritz Financial Group")),
                Map.entry("032830", new StockNameOverride("삼성생명", "Samsung Life")),
                Map.entry("259960", new StockNameOverride("크래프톤", "Krafton")),
                Map.entry("033780", new StockNameOverride("KT&G", "KT&G")),
                Map.entry("011200", new StockNameOverride("HMM", "HMM")),
                Map.entry("316140", new StockNameOverride("우리금융지주", "Woori Financial Group")),
                Map.entry("034020", new StockNameOverride("두산에너빌리티", "Doosan Enerbility")),
                Map.entry("003490", new StockNameOverride("대한항공", "Korean Air")),
                Map.entry("066570", new StockNameOverride("LG전자", "LG Electronics")),
                Map.entry("003670", new StockNameOverride("포스코퓨처엠", "POSCO Future M")),
                Map.entry("009150", new StockNameOverride("삼성전기", "Samsung Electro-Mechanics")),
                Map.entry("012450", new StockNameOverride("한화에어로스페이스", "Hanwha Aerospace"))
        );
    }

    private String firstNonEmpty(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.path(key);
            if (!value.isMissingNode() && !value.isNull() && !value.asText("").trim().isBlank()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private String normalizeSymbol(String raw) {
        String onlyDigits = String.valueOf(raw == null ? "" : raw).replaceAll("\\D", "");
        if (onlyDigits.isBlank()) {
            return String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        }
        return String.format("%06d", Integer.parseInt(onlyDigits));
    }

    private String normalizeCompanyName(String name) {
        return String.valueOf(name == null ? "" : name)
                .replaceAll("\\s+", "")
                .replaceAll("보통주", "")
                .replaceAll("주식$", "")
                .trim();
    }

    private String pickStockDomain(String symbol) {
        Map<String, String> map = Map.ofEntries(
                Map.entry("005930", "samsung.com"),
                Map.entry("005935", "samsung.com"),
                Map.entry("000660", "skhynix.com"),
                Map.entry("373220", "lgensol.com"),
                Map.entry("207940", "samsungbiologics.com"),
                Map.entry("005380", "hyundai.com"),
                Map.entry("068270", "celltrion.com"),
                Map.entry("000270", "kia.com"),
                Map.entry("105560", "kbfg.com"),
                Map.entry("035420", "navercorp.com"),
                Map.entry("055550", "shinhan.com"),
                Map.entry("005490", "posco-inc.com"),
                Map.entry("006400", "samsungsdi.com"),
                Map.entry("035720", "kakaocorp.com"),
                Map.entry("051910", "lgchem.com"),
                Map.entry("028260", "samsungcnt.com"),
                Map.entry("086790", "hanafn.com"),
                Map.entry("015760", "kepco.co.kr"),
                Map.entry("329180", "hd-hhi.com"),
                Map.entry("138040", "meritzfinancialgroup.com"),
                Map.entry("032830", "samsunglife.com"),
                Map.entry("259960", "krafton.com"),
                Map.entry("033780", "ktng.com"),
                Map.entry("011200", "hmm21.com"),
                Map.entry("316140", "woorifg.com"),
                Map.entry("034020", "doosanenerbility.com"),
                Map.entry("003490", "koreanair.com"),
                Map.entry("066570", "lge.com"),
                Map.entry("003670", "poscofuturem.com"),
                Map.entry("009150", "sem.samsung.com"),
                Map.entry("012450", "hanwhaaerospace.com"),
                Map.entry("AAPL", "apple.com"),
                Map.entry("MSFT", "microsoft.com"),
                Map.entry("NVDA", "nvidia.com"),
                Map.entry("AMZN", "amazon.com"),
                Map.entry("GOOGL", "google.com"),
                Map.entry("GOOG", "google.com"),
                Map.entry("META", "meta.com"),
                Map.entry("AVGO", "broadcom.com"),
                Map.entry("TSLA", "tesla.com"),
                Map.entry("COST", "costco.com"),
                Map.entry("NFLX", "netflix.com"),
                Map.entry("ASML", "asml.com"),
                Map.entry("TMUS", "t-mobile.com"),
                Map.entry("CSCO", "cisco.com"),
                Map.entry("AMD", "amd.com"),
                Map.entry("PEP", "pepsico.com"),
                Map.entry("LIN", "linde.com"),
                Map.entry("INTU", "intuit.com"),
                Map.entry("QCOM", "qualcomm.com"),
                Map.entry("AMGN", "amgen.com"),
                Map.entry("TXN", "ti.com"),
                Map.entry("INTC", "intel.com"),
                Map.entry("HON", "honeywell.com"),
                Map.entry("AMAT", "appliedmaterials.com"),
                Map.entry("BKNG", "bookingholdings.com"),
                Map.entry("ISRG", "intuitive.com"),
                Map.entry("ADBE", "adobe.com"),
                Map.entry("MU", "micron.com"),
                Map.entry("ADP", "adp.com"),
                Map.entry("LRCX", "lamresearch.com"),
                Map.entry("KLAC", "kla.com"),
                Map.entry("PANW", "paloaltonetworks.com"),
                Map.entry("SNPS", "synopsys.com"),
                Map.entry("CDNS", "cadence.com"),
                Map.entry("MRVL", "marvell.com"),
                Map.entry("PLTR", "palantir.com"),
                Map.entry("ADI", "analog.com"),
                Map.entry("CMCSA", "corporate.comcast.com"),
                Map.entry("GILD", "gilead.com"),
                Map.entry("ABNB", "airbnb.com"),
                Map.entry("PDD", "pddholdings.com"),
                Map.entry("MELI", "mercadolibre.com"),
                Map.entry("ORCL", "oracle.com")
        );
        return map.getOrDefault(String.valueOf(symbol).toUpperCase(Locale.ROOT), "");
    }

    private String buildLogo(String domain) {
        if (domain == null || domain.isBlank()) {
            return "";
        }
        return "https://www.google.com/s2/favicons?sz=128&domain_url="
                + URLEncoder.encode(domain, StandardCharsets.UTF_8);
    }

    private String extractDomain(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String normalized = raw.trim()
                .replaceFirst("^https?://", "")
                .replaceFirst("^www\\.", "");
        int slashIndex = normalized.indexOf('/');
        return slashIndex >= 0 ? normalized.substring(0, slashIndex) : normalized;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private Long toLong(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Math.round(Double.parseDouble(value.replace(",", "").trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private PriceCacheBucket getPriceBucket(String market) {
        return priceCache.computeIfAbsent(market, key -> new PriceCacheBucket());
    }

    private static class PriceCacheBucket {
        long at = 0L;
        long fxAt = 0L;
        Double usdKrw = 1350.0;
        LocalDate snapshotDate;
        List<MarketItemResponse> items = new ArrayList<>();
    }

    private record StockNameOverride(String name, String displayNameEN) {
    }

    private record YahooChartMeta(Double price, Double changePct) {
    }

    private record NasdaqSnapshotBase(String symbol, String name, String domain) {
    }
}
