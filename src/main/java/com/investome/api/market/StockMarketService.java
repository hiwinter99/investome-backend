package com.investome.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class StockMarketService {

    private final MarketHttpClient http;

    private static final long RANK_TTL_MS = Duration.ofHours(6).toMillis();
    private static final long PRICE_TTL_MS = Duration.ofSeconds(20).toMillis();
    private static final long FX_TTL_MS = Duration.ofSeconds(20).toMillis();
    private static final long KIS_TOKEN_TTL_MS = Duration.ofHours(23).toMillis();

    @Value("${KIS_APP_KEY:}")
    private String kisAppKey;

    @Value("${KIS_APP_SECRET:}")
    private String kisAppSecret;

    @Value("${KIS_BASE_URL:https://openapi.koreainvestment.com:9443}")
    private String kisBaseUrl;

    @Value("${KIS_MARKET_CAP_TR_ID:FHPST01740000}")
    private String kisMarketCapTrId;

    private final Map<String, CacheBucket> cache = new ConcurrentHashMap<>();

    private volatile String kisAccessToken = "";
    private volatile long kisAccessTokenAt = 0L;

    private static final List<StockBase> NASDAQ_FIXED_UNIVERSE = List.of(
            new StockBase("AAPL", "애플", "Apple Inc.", "apple.com"),
            new StockBase("MSFT", "마이크로소프트", "Microsoft Corporation", "microsoft.com"),
            new StockBase("NVDA", "엔비디아", "NVIDIA Corporation", "nvidia.com"),
            new StockBase("AMZN", "아마존", "Amazon.com, Inc.", "amazon.com"),
            new StockBase("GOOGL", "알파벳A", "Alphabet Inc. Class A", "google.com"),
            new StockBase("GOOG", "알파벳C", "Alphabet Inc. Class C", "google.com"),
            new StockBase("META", "메타", "Meta Platforms, Inc.", "meta.com"),
            new StockBase("AVGO", "브로드컴", "Broadcom Inc.", "broadcom.com"),
            new StockBase("TSLA", "테슬라", "Tesla, Inc.", "tesla.com"),
            new StockBase("COST", "코스트코", "Costco Wholesale Corporation", "costco.com"),
            new StockBase("NFLX", "넷플릭스", "Netflix, Inc.", "netflix.com"),
            new StockBase("ASML", "ASML", "ASML Holding N.V.", "asml.com"),
            new StockBase("TMUS", "T-모바일", "T-Mobile US, Inc.", "t-mobile.com"),
            new StockBase("CSCO", "시스코", "Cisco Systems, Inc.", "cisco.com"),
            new StockBase("AMD", "AMD", "Advanced Micro Devices, Inc.", "amd.com"),
            new StockBase("PEP", "펩시코", "PepsiCo, Inc.", "pepsico.com"),
            new StockBase("LIN", "린데", "Linde plc", "linde.com"),
            new StockBase("INTU", "인튜이트", "Intuit Inc.", "intuit.com"),
            new StockBase("QCOM", "퀄컴", "QUALCOMM Incorporated", "qualcomm.com"),
            new StockBase("AMGN", "암젠", "Amgen Inc.", "amgen.com"),
            new StockBase("TXN", "텍사스인스트루먼트", "Texas Instruments Incorporated", "ti.com"),
            new StockBase("INTC", "인텔", "Intel Corporation", "intel.com"),
            new StockBase("HON", "허니웰", "Honeywell International Inc.", "honeywell.com"),
            new StockBase("AMAT", "어플라이드머티어리얼즈", "Applied Materials, Inc.", "appliedmaterials.com"),
            new StockBase("BKNG", "부킹홀딩스", "Booking Holdings Inc.", "bookingholdings.com"),
            new StockBase("ISRG", "인튜이티브서지컬", "Intuitive Surgical, Inc.", "intuitive.com"),
            new StockBase("ADBE", "어도비", "Adobe Inc.", "adobe.com"),
            new StockBase("MU", "마이크론", "Micron Technology, Inc.", "micron.com"),
            new StockBase("ADP", "ADP", "Automatic Data Processing, Inc.", "adp.com"),
            new StockBase("LRCX", "램리서치", "Lam Research Corporation", "lamresearch.com"),
            new StockBase("KLAC", "KLA", "KLA Corporation", "kla.com"),
            new StockBase("PANW", "팔로알토네트웍스", "Palo Alto Networks, Inc.", "paloaltonetworks.com"),
            new StockBase("SNPS", "시놉시스", "Synopsys, Inc.", "synopsys.com"),
            new StockBase("CDNS", "케이던스", "Cadence Design Systems, Inc.", "cadence.com"),
            new StockBase("MRVL", "마벨", "Marvell Technology, Inc.", "marvell.com"),
            new StockBase("PLTR", "팔란티어", "Palantir Technologies Inc.", "palantir.com"),
            new StockBase("ADI", "아나로그디바이스", "Analog Devices, Inc.", "analog.com"),
            new StockBase("CMCSA", "컴캐스트", "Comcast Corporation", "corporate.comcast.com"),
            new StockBase("GILD", "길리어드", "Gilead Sciences, Inc.", "gilead.com"),
            new StockBase("ABNB", "에어비앤비", "Airbnb, Inc.", "airbnb.com"),
            new StockBase("PDD", "핀둬둬", "PDD Holdings Inc.", "pddholdings.com"),
            new StockBase("MELI", "메르카도리브레", "MercadoLibre, Inc.", "mercadolibre.com"),
            new StockBase("ORCL", "오라클", "Oracle Corporation", "oracle.com")
    );

    public List<MarketItemResponse> getTop30(String market) {
        String normalizedMarket = String.valueOf(market).toUpperCase(Locale.ROOT);
        if (!normalizedMarket.equals("KOSPI") && !normalizedMarket.equals("NASDAQ")) {
            return List.of();
        }

        try {
            return ensurePriceSnapshot(normalizedMarket);
        } catch (Exception e) {
            CacheBucket bucket = getBucket(normalizedMarket);
            if (!bucket.pricedItems.isEmpty()) return bucket.pricedItems;
            return List.of();
        }
    }

    public MarketItemResponse getQuote(String symbol, String market) {
        String normalizedMarket = String.valueOf(market).toUpperCase(Locale.ROOT);
        try {
            if ("KOSPI".equals(normalizedMarket)) {
                List<MarketItemResponse> items = ensurePriceSnapshot("KOSPI");
                String target = normalizeSymbol(symbol);
                return items.stream()
                        .filter(it -> normalizeSymbol(it.getSymbol()).equals(target))
                        .findFirst()
                        .orElse(null);
            }

            if ("NASDAQ".equals(normalizedMarket)) {
                CacheBucket bucket = getBucket("NASDAQ");
                double usdKrw = getUsdKrwCached(bucket);
                JsonNode quote = fetchYahooQuoteBatch(List.of(String.valueOf(symbol).toUpperCase(Locale.ROOT))).stream()
                        .findFirst()
                        .orElse(null);

                if (quote == null) return null;

                Double marketCapUsd = firstDouble(
                        http.toDouble(quote.path("marketCap")),
                        http.toDouble(quote.path("regularMarketCap"))
                );

                Double priceUsd = firstDouble(
                        http.toDouble(quote.path("regularMarketPrice")),
                        http.toDouble(quote.path("postMarketPrice")),
                        http.toDouble(quote.path("preMarketPrice")),
                        http.toDouble(quote.path("regularMarketPreviousClose"))
                );

                Double changePct = firstDouble(
                        http.toDouble(quote.path("regularMarketChangePercent")),
                        http.toDouble(quote.path("postMarketChangePercent")),
                        http.toDouble(quote.path("preMarketChangePercent"))
                );

                String rawSymbol = String.valueOf(symbol).toUpperCase(Locale.ROOT);

                return MarketItemResponse.builder()
                        .market("NASDAQ")
                        .name(nonBlank(quote.path("shortName").asText(""), rawSymbol))
                        .displayNameEN(nonBlank(quote.path("longName").asText(""), quote.path("shortName").asText(""), rawSymbol))
                        .symbol(rawSymbol)
                        .iconUrl(buildLogo(pickStockDomain(rawSymbol)))
                        .coinId("")
                        .capKRW(marketCapUsd != null ? Math.round(marketCapUsd * usdKrw) : null)
                        .priceKRW(priceUsd != null ? round2(priceUsd * usdKrw) : null)
                        .changePct(changePct)
                        .build();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<MarketItemResponse> ensurePriceSnapshot(String market) throws Exception {
        CacheBucket bucket = getBucket(market);
        long now = System.currentTimeMillis();

        if (!bucket.pricedItems.isEmpty() && now - bucket.priceAt < PRICE_TTL_MS) {
            return bucket.pricedItems;
        }

        List<MarketItemResponse> rankedItems = ensureRankSnapshot(market);
        List<MarketItemResponse> pricedItems = buildPriceSnapshot(rankedItems, market);

        if (pricedItems.isEmpty()) {
            throw new IllegalStateException(market + " price snapshot build failed");
        }

        bucket.pricedItems = pricedItems;
        bucket.priceAt = now;
        return pricedItems;
    }

    private List<MarketItemResponse> ensureRankSnapshot(String market) throws Exception {
        CacheBucket bucket = getBucket(market);
        long now = System.currentTimeMillis();

        if (!bucket.rankedItems.isEmpty() && now - bucket.rankAt < RANK_TTL_MS) {
            return bucket.rankedItems;
        }

        List<MarketItemResponse> rankedItems =
                "KOSPI".equals(market) ? buildKospiRankSnapshot() : buildNasdaqRankSnapshot();

        if (rankedItems.isEmpty()) {
            throw new IllegalStateException(market + " rank snapshot build failed");
        }

        bucket.rankedItems = rankedItems;
        bucket.rankAt = now;
        return rankedItems;
    }

    private List<MarketItemResponse> buildPriceSnapshot(List<MarketItemResponse> rankedItems, String market) throws Exception {
        if ("KOSPI".equals(market)) {
            return rankedItems.stream().map(item -> MarketItemResponse.builder()
                    .rank(item.getRank())
                    .market("KOSPI")
                    .name(item.getName())
                    .displayNameEN(item.getDisplayNameEN())
                    .symbol(item.getSymbol())
                    .iconUrl(item.getIconUrl())
                    .coinId("")
                    .capKRW(item.getCapKRW())
                    .priceKRW(item.getPriceKRW())
                    .changePct(item.getChangePct())
                    .build()).toList();
        }

        CacheBucket bucket = getBucket("NASDAQ");
        double usdKrw = getUsdKrwCached(bucket);
        List<JsonNode> quoteRows = fetchYahooQuoteBatch(
                rankedItems.stream().map(MarketItemResponse::getSymbol).toList()
        );

        Map<String, JsonNode> quoteMap = new HashMap<>();
        for (JsonNode row : quoteRows) {
            quoteMap.put(row.path("symbol").asText("").toUpperCase(Locale.ROOT), row);
        }

        List<MarketItemResponse> result = new ArrayList<>();
        for (MarketItemResponse item : rankedItems) {
            JsonNode quote = quoteMap.getOrDefault(item.getSymbol().toUpperCase(Locale.ROOT), null);

            Double marketCapUsd = quote == null ? null : firstDouble(
                    http.toDouble(quote.path("marketCap")),
                    http.toDouble(quote.path("regularMarketCap"))
            );

            Double priceUsd = quote == null ? null : firstDouble(
                    http.toDouble(quote.path("regularMarketPrice")),
                    http.toDouble(quote.path("postMarketPrice")),
                    http.toDouble(quote.path("preMarketPrice")),
                    http.toDouble(quote.path("regularMarketPreviousClose"))
            );

            Double changePct = quote == null ? item.getChangePct() : firstDouble(
                    http.toDouble(quote.path("regularMarketChangePercent")),
                    http.toDouble(quote.path("postMarketChangePercent")),
                    http.toDouble(quote.path("preMarketChangePercent")),
                    item.getChangePct()
            );

            result.add(MarketItemResponse.builder()
                    .rank(item.getRank())
                    .market("NASDAQ")
                    .name(quote == null ? item.getName() : nonBlank(quote.path("shortName").asText(""), item.getName()))
                    .displayNameEN(quote == null ? item.getDisplayNameEN()
                            : nonBlank(quote.path("longName").asText(""), quote.path("shortName").asText(""), item.getDisplayNameEN()))
                    .symbol(item.getSymbol())
                    .iconUrl(nonBlank(item.getIconUrl(), buildLogo(pickStockDomain(item.getSymbol()))))
                    .coinId("")
                    .capKRW(marketCapUsd != null ? Math.round(marketCapUsd * usdKrw) : item.getCapKRW())
                    .priceKRW(priceUsd != null ? round2(priceUsd * usdKrw) : item.getPriceKRW())
                    .changePct(changePct)
                    .build());
        }

        return result;
    }

    private List<MarketItemResponse> buildKospiRankSnapshot() throws Exception {
        Map<String, KospiMaster> masterMap = getKospiMasterMap();
        List<JsonNode> rows = fetchKisMarketCapTop30();

        List<MarketItemResponse> normalized = new ArrayList<>();
        int index = 0;
        for (JsonNode row : rows) {
            MarketItemResponse item = normalizeKisMarketCapRow(row, index++, masterMap);
            if (item != null && item.getSymbol() != null && item.getCapKRW() != null) {
                normalized.add(item);
            }
        }

        normalized.sort(Comparator.comparingInt(it -> it.getRank() != null ? it.getRank() : 9999));

        List<MarketItemResponse> top = normalized.stream().limit(30).toList();
        List<MarketItemResponse> result = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            MarketItemResponse item = top.get(i);
            item.setRank(i + 1);
            item.setMarket("KOSPI");
            result.add(item);
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("KOSPI top30 normalization failed");
        }

        return result;
    }

    private List<MarketItemResponse> buildNasdaqRankSnapshot() throws Exception {
        CacheBucket bucket = getBucket("NASDAQ");
        double usdKrw = getUsdKrwCached(bucket);

        List<JsonNode> quoteRows = fetchYahooQuoteBatch(
                NASDAQ_FIXED_UNIVERSE.stream().map(StockBase::symbol).toList()
        );

        Map<String, JsonNode> quoteMap = new HashMap<>();
        for (JsonNode row : quoteRows) {
            quoteMap.put(row.path("symbol").asText("").toUpperCase(Locale.ROOT), row);
        }

        List<MarketItemResponse> normalized = new ArrayList<>();
        for (StockBase item : NASDAQ_FIXED_UNIVERSE) {
            JsonNode quote = quoteMap.getOrDefault(item.symbol().toUpperCase(Locale.ROOT), null);

            Double marketCapUsd = quote == null ? null : firstDouble(
                    http.toDouble(quote.path("marketCap")),
                    http.toDouble(quote.path("regularMarketCap"))
            );

            Double priceUsd = quote == null ? null : firstDouble(
                    http.toDouble(quote.path("regularMarketPrice")),
                    http.toDouble(quote.path("postMarketPrice")),
                    http.toDouble(quote.path("preMarketPrice")),
                    http.toDouble(quote.path("regularMarketPreviousClose"))
            );

            Double changePct = quote == null ? null : firstDouble(
                    http.toDouble(quote.path("regularMarketChangePercent")),
                    http.toDouble(quote.path("postMarketChangePercent")),
                    http.toDouble(quote.path("preMarketChangePercent"))
            );

            normalized.add(MarketItemResponse.builder()
                    .rank(0)
                    .market("NASDAQ")
                    .name(quote == null ? item.name() : nonBlank(quote.path("shortName").asText(""), item.name()))
                    .displayNameEN(quote == null ? item.displayNameEN()
                            : nonBlank(quote.path("longName").asText(""), quote.path("shortName").asText(""), item.displayNameEN()))
                    .symbol(item.symbol())
                    .iconUrl(buildLogo(nonBlank(item.domain(), pickStockDomain(item.symbol()))))
                    .coinId("")
                    .capKRW(marketCapUsd != null ? Math.round(marketCapUsd * usdKrw) : null)
                    .priceKRW(priceUsd != null ? round2(priceUsd * usdKrw) : null)
                    .changePct(changePct)
                    .build());
        }

        normalized.removeIf(it -> it.getSymbol() == null || it.getCapKRW() == null);
        normalized.sort((a, b) -> Long.compare(
                b.getCapKRW() != null ? b.getCapKRW() : 0L,
                a.getCapKRW() != null ? a.getCapKRW() : 0L
        ));

        List<MarketItemResponse> top = normalized.stream().limit(30).toList();
        for (int i = 0; i < top.size(); i++) {
            top.get(i).setRank(i + 1);
        }

        if (top.isEmpty()) {
            throw new IllegalStateException("NASDAQ top30 normalization failed");
        }

        return top;
    }

    private List<JsonNode> fetchYahooQuoteBatch(List<String> symbols) throws Exception {
        String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" +
                URLEncoder.encode(String.join(",", symbols), StandardCharsets.UTF_8);
        JsonNode json = http.fetchJsonNode(url);
        JsonNode result = json.path("quoteResponse").path("result");
        List<JsonNode> rows = new ArrayList<>();
        if (result.isArray()) {
            result.forEach(rows::add);
        }
        return rows;
    }

    private List<JsonNode> fetchKisMarketCapTop30() throws Exception {
        String token = getKisAccessToken();

        String url = kisBaseUrl + "/uapi/domestic-stock/v1/ranking/market-cap" +
                "?fid_cond_mrkt_div_code=J" +
                "&fid_cond_scr_div_code=20174" +
                "&fid_div_cls_code=0" +
                "&fid_input_iscd=0000" +
                "&fid_trgt_cls_code=0" +
                "&fid_trgt_exls_cls_code=0" +
                "&fid_input_price_1=" +
                "&fid_input_price_2=" +
                "&fid_vol_cnt=";

        JsonNode json = http.fetchJsonNode(url, Map.of(
                "Content-Type", "application/json",
                "authorization", "Bearer " + token,
                "appkey", kisAppKey,
                "appsecret", kisAppSecret,
                "tr_id", kisMarketCapTrId
        ));

        JsonNode output = json.path("output");
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

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(kisBaseUrl + "/oauth2/tokenP"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Investome Spring)")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(20))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("KIS token failed: " + response.statusCode() + " " + response.body());
        }

        JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        String token = json.path("access_token").asText("").trim();
        if (token.isBlank()) {
            throw new IllegalStateException("KIS access token not returned");
        }

        kisAccessToken = token;
        kisAccessTokenAt = now;
        return token;
    }

    private MarketItemResponse normalizeKisMarketCapRow(JsonNode row, int index, Map<String, KospiMaster> masterMap) {
        String symbol = normalizeSymbol(firstNonEmpty(row,
                "mksc_shrn_iscd", "stck_shrn_iscd", "shrn_iscd", "isu_cd", "iscd", "stck_shrn_iscd1"
        ));
        if (symbol.isBlank()) return null;

        KospiMaster master = masterMap.get(symbol);

        String rawKorName = firstNonEmpty(row, "hts_kor_isnm", "prdt_name", "stck_name");
        String rawPrice = firstNonEmpty(row, "stck_prpr", "cur_prc", "bstp_nmix_prpr");
        String rawPct = firstNonEmpty(row, "prdy_ctrt", "flu_rt", "chg_rate");
        String rawCap = firstNonEmpty(row, "stck_avls", "data_valx", "mkt_cap");
        String rawRank = firstNonEmpty(row, "data_rank", "rank");

        String name = master != null ? master.name() : normalizeCompanyName(rawKorName);
        if (name.isBlank()) name = symbol;

        String displayNameEN = master != null ? master.displayNameEN() : name;

        Integer rank = toInteger(rawRank);
        if (rank == null) rank = index + 1;

        return MarketItemResponse.builder()
                .rank(rank)
                .market("KOSPI")
                .name(name)
                .displayNameEN(displayNameEN)
                .symbol(symbol)
                .iconUrl(buildLogo(pickStockDomain(symbol)))
                .coinId("")
                .capKRW(toLong(rawCap))
                .priceKRW(toDouble(rawPrice))
                .changePct(toDouble(rawPct))
                .build();
    }

    private Map<String, KospiMaster> getKospiMasterMap() throws IOException {
        List<KospiMaster> items = loadKospiMaster();
        Map<String, KospiMaster> map = new HashMap<>();
        for (KospiMaster item : items) {
            map.put(item.symbol(), item);
        }
        return map;
    }

    private List<KospiMaster> loadKospiMaster() throws IOException {
        Path path1 = Path.of("tmp", "kospi.csv");
        Path path2 = Path.of("..", "tmp", "kospi.csv");

        Path filePath = Files.exists(path1) ? path1 : path2;
        if (!Files.exists(filePath)) {
            throw new IOException("tmp/kospi.csv not found");
        }

        String text = Files.readString(filePath, Charset.forName("MS949"));
        List<Map<String, String>> rows = parseCsv(text);

        Map<String, KospiMaster> overrides = Map.ofEntries(
                Map.entry("005930", new KospiMaster("005930", "삼성전자", "Samsung Electronics")),
                Map.entry("005935", new KospiMaster("005935", "삼성전자우", "Samsung Electronics Pref")),
                Map.entry("000660", new KospiMaster("000660", "SK하이닉스", "SK hynix")),
                Map.entry("373220", new KospiMaster("373220", "LG에너지솔루션", "LG Energy Solution")),
                Map.entry("207940", new KospiMaster("207940", "삼성바이오로직스", "Samsung Biologics")),
                Map.entry("005380", new KospiMaster("005380", "현대차", "Hyundai Motor")),
                Map.entry("068270", new KospiMaster("068270", "셀트리온", "Celltrion")),
                Map.entry("000270", new KospiMaster("000270", "기아", "Kia")),
                Map.entry("105560", new KospiMaster("105560", "KB금융", "KB Financial Group")),
                Map.entry("035420", new KospiMaster("035420", "NAVER", "NAVER")),
                Map.entry("055550", new KospiMaster("055550", "신한지주", "Shinhan Financial Group")),
                Map.entry("005490", new KospiMaster("005490", "POSCO홀딩스", "POSCO Holdings")),
                Map.entry("006400", new KospiMaster("006400", "삼성SDI", "Samsung SDI")),
                Map.entry("035720", new KospiMaster("035720", "카카오", "Kakao")),
                Map.entry("051910", new KospiMaster("051910", "LG화학", "LG Chem")),
                Map.entry("028260", new KospiMaster("028260", "삼성물산", "Samsung C&T")),
                Map.entry("086790", new KospiMaster("086790", "하나금융지주", "Hana Financial Group")),
                Map.entry("015760", new KospiMaster("015760", "한국전력", "KEPCO")),
                Map.entry("329180", new KospiMaster("329180", "HD현대중공업", "HD Hyundai Heavy Industries")),
                Map.entry("138040", new KospiMaster("138040", "메리츠금융지주", "Meritz Financial Group")),
                Map.entry("032830", new KospiMaster("032830", "삼성생명", "Samsung Life")),
                Map.entry("259960", new KospiMaster("259960", "크래프톤", "Krafton")),
                Map.entry("033780", new KospiMaster("033780", "KT&G", "KT&G")),
                Map.entry("011200", new KospiMaster("011200", "HMM", "HMM")),
                Map.entry("316140", new KospiMaster("316140", "우리금융지주", "Woori Financial Group")),
                Map.entry("034020", new KospiMaster("034020", "두산에너빌리티", "Doosan Enerbility")),
                Map.entry("003490", new KospiMaster("003490", "대한항공", "Korean Air")),
                Map.entry("066570", new KospiMaster("066570", "LG전자", "LG Electronics")),
                Map.entry("003670", new KospiMaster("003670", "포스코퓨처엠", "POSCO Future M")),
                Map.entry("009150", new KospiMaster("009150", "삼성전기", "Samsung Electro-Mechanics")),
                Map.entry("012450", new KospiMaster("012450", "한화에어로스페이스", "Hanwha Aerospace"))
        );

        List<KospiMaster> items = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String symbol = normalizeSymbol(getByNormalizedKeys(row, "단축코드", "종목코드", "표준단축코드"));
            if (symbol.isBlank()) continue;

            String rawName = getByNormalizedKeys(row, "한글종목명", "종목명", "한글 종목명");
            String rawEnglish = getByNormalizedKeys(row, "영문종목명", "영문명", "영문 종목명");
            String stockType = getByNormalizedKeys(row, "주식종류");

            if (rawName.isBlank()) continue;
            if (!stockType.isBlank() && !stockType.contains("보통주")) continue;

            KospiMaster override = overrides.get(symbol);
            items.add(new KospiMaster(
                    symbol,
                    override != null ? override.name() : normalizeCompanyName(rawName),
                    override != null ? override.displayNameEN() : (!rawEnglish.isBlank() ? rawEnglish.trim() : normalizeCompanyName(rawName))
            ));
        }

        return items;
    }

    private List<Map<String, String>> parseCsv(String text) {
        List<String> lines = Arrays.stream(text.replace("\uFEFF", "").split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (lines.size() < 2) return List.of();

        List<String> headers = splitCsvLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            List<String> cols = splitCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < cols.size() ? cols.get(j) : "");
            }
            rows.add(row);
        }

        return rows;
    }

    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';

            if (ch == '"') {
                if (inQuotes && next == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == ',' && !inQuotes) {
                result.add(unquote(current.toString().trim()));
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        result.add(unquote(current.toString().trim()));
        return result;
    }

    private String unquote(String value) {
        if (value == null) return "";
        return value.replaceFirst("^\"(.*)\"$", "$1").trim();
    }

    private String getByNormalizedKeys(Map<String, String> row, String... candidates) {
        Map<String, String> normalizedMap = new HashMap<>();
        for (Map.Entry<String, String> e : row.entrySet()) {
            normalizedMap.put(normalizeHeader(e.getKey()), e.getValue());
        }

        for (String key : candidates) {
            String hit = normalizedMap.get(normalizeHeader(key));
            if (hit != null && !hit.trim().isBlank()) return hit.trim();
        }
        return "";
    }

    private String normalizeHeader(String value) {
        return String.valueOf(value)
                .replace("\uFEFF", "")
                .replaceAll("\\s+", "")
                .replaceAll("[()_\\-./]", "")
                .trim()
                .toLowerCase(Locale.ROOT);
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
        if (onlyDigits.isBlank()) return String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        return String.format("%06d", Integer.parseInt(onlyDigits));
    }

    private String normalizeCompanyName(String name) {
        return String.valueOf(name == null ? "" : name)
                .replaceAll("\\s+", "")
                .replaceAll("보통주$", "")
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
        if (domain == null || domain.isBlank()) return "";
        return "https://www.google.com/s2/favicons?sz=128&domain_url=" +
                URLEncoder.encode(domain, StandardCharsets.UTF_8);
    }

    private CacheBucket getBucket(String market) {
        return cache.computeIfAbsent(market, k -> new CacheBucket());
    }

    private double getUsdKrwCached(CacheBucket bucket) {
        long now = System.currentTimeMillis();
        if (bucket.usdKrw != null && now - bucket.fxAt < FX_TTL_MS) {
            return bucket.usdKrw;
        }
        double next = http.fetchUsdKrwOrDefault();
        bucket.usdKrw = next;
        bucket.fxAt = now;
        return next;
    }

    @SafeVarargs
    private final <T> T firstDouble(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String nonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private Double toDouble(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(String value) {
        Double d = toDouble(value);
        return d != null ? Math.round(d) : null;
    }

    private Integer toInteger(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double round2(Double value) {
        if (value == null) return null;
        return Math.round(value * 100.0) / 100.0;
    }

    private static class CacheBucket {
        long rankAt = 0L;
        long priceAt = 0L;
        long fxAt = 0L;
        Double usdKrw = 1350.0;
        List<MarketItemResponse> rankedItems = new ArrayList<>();
        List<MarketItemResponse> pricedItems = new ArrayList<>();
    }

    private record StockBase(String symbol, String name, String displayNameEN, String domain) {}
    private record KospiMaster(String symbol, String name, String displayNameEN) {}
}