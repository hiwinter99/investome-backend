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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    @GetMapping
    public Map<String, Object> getNews(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(required = false) String q
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            String keyword = resolveKeyword(category, q);
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
                result.put("items", List.of());
                result.put("error", "news_stream_null");
                return result;
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

            StringBuilder xml = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                xml.append(line);
            }
            br.close();
            conn.disconnect();

            List<Map<String, String>> items = parseRss(xml.toString());

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

    private String resolveKeyword(String category, String q) {
        if (q != null && !q.trim().isEmpty()) {
            return q.trim();
        }

        return switch (category == null ? "all" : category.trim().toLowerCase()) {
            case "crypto" -> "가상자산 OR 비트코인 OR 이더리움 OR 코인";
            case "domestic", "korea", "kr", "stocks" -> "국내증시 OR 코스피 OR 코스닥 OR 한국증시";
            case "global", "overseas", "us", "world" -> "해외증시 OR 미국증시 OR 뉴욕증시 OR 나스닥 OR 다우 OR S&P500";
            case "all" -> "경제 OR 증시 OR 코인";
            default -> "경제 OR 증시 OR 코인";
        };
    }

    private List<Map<String, String>> parseRss(String xml) {
        List<Map<String, String>> result = new ArrayList<>();

        String[] chunks = xml.split("<item>");
        for (int i = 1; i < chunks.length; i++) {
            String item = chunks[i];

            String title = cleanText(extract(item, "title"));
            String link = decodeGoogleNewsLink(cleanText(extract(item, "link")));
            String pubDate = cleanText(extract(item, "pubDate"));
            String description = cleanText(extract(item, "description"));
            String source = cleanText(extract(item, "source"));

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

            if (s == -1 || e == -1) return "";

            return text.substring(s + start.length(), e);
        } catch (Exception e) {
            return "";
        }
    }

    private String cleanText(String value) {
        if (value == null) return "";
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

    private String decodeGoogleNewsLink(String link) {
        try {
            if (link == null || link.isBlank()) return "";
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