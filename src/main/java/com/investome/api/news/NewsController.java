package com.investome.api.news;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    @GetMapping
    public Map<String, Object> getNews(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String q
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            String normalizedCategory = normalizeCategory(category);
            List<String> queries = resolveQueries(normalizedCategory, topic, q);
            Map<String, Map<String, String>> merged = new LinkedHashMap<>();

            for (String query : queries) {
                List<Map<String, String>> fetched = fetchNewsItems(query);
                for (Map<String, String> item : fetched) {
                    String dedupeKey = buildDedupeKey(item);
                    merged.putIfAbsent(dedupeKey, item);
                }
            }

            List<Map<String, String>> items = new ArrayList<>(merged.values());
            items = filterRelevant(items, normalizedCategory, topic, q);
            items.sort(Comparator.comparingLong(this::parsePubDateMillis).reversed());

            if (items.size() > limit) {
                items = items.subList(0, limit);
            }

            result.put("items", items);
            result.put("count", items.size());
            return result;
        } catch (Exception e) {
            result.put("items", List.of());
            result.put("error", e.getClass().getSimpleName());
            result.put("message", e.getMessage());
            return result;
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "all";
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> resolveQueries(String category, String topic, String q) {
        if (q != null && !q.trim().isEmpty()) {
            return List.of(q.trim());
        }

        if (topic != null && !topic.trim().isEmpty()) {
            return List.of(topic.trim());
        }

        return switch (category) {
            case "crypto" -> List.of("\"비트코인\" OR \"이더리움\" OR \"가상자산\" OR \"암호화폐\"");
            case "domestic", "korea", "kr", "stocks" -> List.of(
                    "\"코스피\" OR \"코스닥\" OR \"국내증시\" OR \"한국 증시\" OR \"원달러 환율\""
            );
            case "global", "overseas", "us", "world" -> List.of(
                    "\"미국 증시\" OR 나스닥 OR \"S&P 500\" OR 다우존스 OR 연준"
            );
            case "all" -> List.of(
                    "\"코스피\" OR \"코스닥\" OR \"국내증시\" OR \"한국 증시\" OR \"원달러 환율\"",
                    "\"미국 증시\" OR 나스닥 OR \"S&P 500\" OR 다우존스 OR 연준",
                    "\"비트코인\" OR \"이더리움\" OR \"가상자산\" OR \"암호화폐\"",
                    "\"원달러 환율\" OR \"국제유가\" OR 인플레이션 OR 기준금리"
            );
            default -> List.of("\"경제\" OR \"증시\" OR \"비트코인\" OR \"환율\"");
        };
    }

    private List<Map<String, String>> fetchNewsItems(String keyword) throws Exception {
        String rssUrl = "https://news.google.com/rss/search?q="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&hl=ko&gl=KR&ceid=KR:ko";

        HttpURLConnection conn = (HttpURLConnection) new URL(rssUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8");

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (stream == null) {
            return List.of();
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder xml = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            xml.append(line);
        }
        br.close();
        conn.disconnect();

        return parseRss(xml.toString());
    }

    private List<Map<String, String>> filterRelevant(
            List<Map<String, String>> items,
            String category,
            String topic,
            String q
    ) {
        if ((q != null && !q.trim().isEmpty()) || (topic != null && !topic.trim().isEmpty())) {
            return items;
        }

        List<Map<String, String>> filtered = new ArrayList<>();
        for (Map<String, String> item : items) {
            String text = buildSearchText(item);
            if (!isNoise(text) && matchesCategory(text, category)) {
                filtered.add(item);
            }
        }

        return filtered;
    }

    private String buildSearchText(Map<String, String> item) {
        return String.join(" ",
                item.getOrDefault("title", ""),
                item.getOrDefault("description", ""),
                item.getOrDefault("source", "")
        ).toLowerCase(Locale.ROOT);
    }

    private boolean isNoise(String text) {
        return text.contains("pakistan stock market")
                || text.contains("taiwan stock market")
                || text.contains("uae stock market")
                || text.contains("vanguard total stock market etf")
                || text.contains("tradersunion")
                || text.contains("epaselect");
    }

    private boolean matchesCategory(String text, String category) {
        return switch (category) {
            case "crypto" -> containsAny(text,
                    "비트코인", "이더리움", "가상자산", "암호화폐",
                    "bitcoin", "ethereum", "crypto", "cryptocurrency"
            );
            case "domestic", "korea", "kr", "stocks" -> containsAny(text,
                    "코스피", "코스닥", "국내증시", "한국 증시", "원달러", "환율",
                    "kospi", "kosdaq", "korean stock", "korea stock", "domestic market"
            );
            case "global", "overseas", "us", "world" -> containsAny(text,
                    "미국 증시", "나스닥", "다우", "다우존스", "연준", "s&p 500",
                    "nasdaq", "dow jones", "wall street", "federal reserve"
            );
            case "all" -> containsAny(text,
                    "코스피", "코스닥", "국내증시", "한국 증시", "원달러", "환율",
                    "미국 증시", "나스닥", "다우", "s&p 500", "연준",
                    "비트코인", "이더리움", "가상자산", "암호화폐",
                    "kospi", "kosdaq", "nasdaq", "dow jones",
                    "bitcoin", "ethereum", "crypto", "exchange rate", "inflation", "oil"
            );
            default -> true;
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String buildDedupeKey(Map<String, String> item) {
        String link = item.getOrDefault("link", "").trim();
        if (!link.isBlank()) {
            return link;
        }
        return item.getOrDefault("title", "").trim().toLowerCase(Locale.ROOT);
    }

    private long parsePubDateMillis(Map<String, String> item) {
        try {
            return ZonedDateTime.parse(
                    item.getOrDefault("pubDate", ""),
                    DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Map<String, String>> parseRss(String xml) {
        List<Map<String, String>> result = new ArrayList<>();

        String[] chunks = xml.split("<item>");
        for (int i = 1; i < chunks.length; i++) {
            String item = chunks[i];

            String source = cleanText(extract(item, "source"));
            String title = cleanTitle(cleanText(extract(item, "title")), source);
            String link = decodeGoogleNewsLink(cleanText(extract(item, "link")));
            String pubDate = cleanText(extract(item, "pubDate"));
            String description = cleanText(extract(item, "description"));

            Map<String, String> map = new HashMap<>();
            map.put("title", title);
            map.put("link", link);
            map.put("pubDate", pubDate);
            map.put("description", description);
            map.put("source", source);

            result.add(map);
        }

        return result;
    }

    private String extract(String text, String tag) {
        try {
            String start = "<" + tag + ">";
            String end = "</" + tag + ">";

            int s = text.indexOf(start);
            int e = text.indexOf(end);

            if (s == -1 || e == -1) {
                return "";
            }

            return text.substring(s + start.length(), e);
        } catch (Exception e) {
            return "";
        }
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .replaceAll("<[^>]*>", "")
                .trim();
    }

    private String cleanTitle(String title, String source) {
        if (title == null) {
            return "";
        }

        String normalized = title.trim();
        if (source == null || source.isBlank()) {
            return normalized;
        }

        String suffix = " - " + source.trim();
        if (normalized.endsWith(suffix)) {
            return normalized.substring(0, normalized.length() - suffix.length()).trim();
        }

        return normalized;
    }

    private String decodeGoogleNewsLink(String link) {
        try {
            if (link == null || link.isBlank()) {
                return "";
            }
            String decoded = URLDecoder.decode(link, StandardCharsets.UTF_8);
            int idx = decoded.indexOf("url=");
            if (idx >= 0) {
                String real = decoded.substring(idx + 4);
                int amp = real.indexOf('&');
                if (amp >= 0) {
                    real = real.substring(0, amp);
                }
                return real;
            }
            return decoded;
        } catch (Exception e) {
            return link == null ? "" : link;
        }
    }
}
