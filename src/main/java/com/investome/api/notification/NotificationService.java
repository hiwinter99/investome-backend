package com.investome.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investome.api.board.BoardPost;
import com.investome.api.board.BoardPostRepository;
import com.investome.api.market.CryptoMarketService;
import com.investome.api.market.MarketItemResponse;
import com.investome.api.market.StockMarketService;
import com.investome.api.mypage.MyPagePortfolio;
import com.investome.api.mypage.MyPagePortfolioRepository;
import com.investome.api.user.User;
import com.investome.api.user.UserRepository;
import com.investome.api.watchlist.WatchlistItem;
import com.investome.api.watchlist.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BoardPostRepository boardPostRepository;
    private final WatchlistRepository watchlistRepository;
    private final MyPagePortfolioRepository myPagePortfolioRepository;
    private final StockMarketService stockMarketService;
    private final CryptoMarketService cryptoMarketService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId) {
        List<NotificationResponse> items = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();

        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        return new NotificationListResponse(unreadCount, items);
    }

    @Transactional
    public NotificationListResponse markRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .ifPresent(notification -> {
                    if (notification.getReadAt() == null) {
                        notification.setReadAt(LocalDateTime.now(KST));
                    }
                });

        return getNotifications(userId);
    }

    @Transactional
    public NotificationListResponse markAllRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndReadAtIsNull(userId);
        LocalDateTime now = LocalDateTime.now(KST);
        notifications.forEach(notification -> {
            if (notification.getReadAt() == null) {
                notification.setReadAt(now);
            }
        });
        return getNotifications(userId);
    }

    @Transactional
    public void notifyPostComment(Long postAuthorId, Long actorUserId, Long postId, String postTitle, String actorNickname) {
        if (postAuthorId == null || Objects.equals(postAuthorId, actorUserId)) {
            return;
        }

        createNotification(
                postAuthorId,
                "post_comment",
                "내 게시글에 댓글이 달렸어요",
                actorNickname + "님이 \"" + safeTitle(postTitle) + "\"에 댓글을 남겼어요.",
                "/board/" + postId,
                null
        );
    }

    @Transactional
    public void notifyCommentReply(Long parentCommentAuthorId, Long actorUserId, Long postId, String postTitle, String actorNickname) {
        if (parentCommentAuthorId == null || Objects.equals(parentCommentAuthorId, actorUserId)) {
            return;
        }

        createNotification(
                parentCommentAuthorId,
                "comment_reply",
                "내 댓글에 답글이 달렸어요",
                actorNickname + "님이 \"" + safeTitle(postTitle) + "\" 댓글에 답글을 남겼어요.",
                "/board/" + postId,
                null
        );
    }

    @Transactional
    public void notifyNoticePost(Long authorId, Long postId, String postTitle) {
        List<User> users = userRepository.findAll();
        String eventKey = "notice-post:" + postId;

        users.stream()
                .map(User::getId)
                .filter(userId -> !Objects.equals(userId, authorId))
                .forEach(userId -> createNotification(
                        userId,
                        "notice_post",
                        "새 공지글이 등록됐어요",
                        "\"" + safeTitle(postTitle) + "\" 공지글이 올라왔어요.",
                        "/board/" + postId,
                        eventKey + ":" + userId
                ));
    }

    @Transactional
    @Scheduled(fixedDelay = 300_000L, initialDelay = 45_000L)
    public void scanPopularPosts() {
        List<BoardPost> posts = boardPostRepository.findAll().stream()
                .filter(post -> !"notice".equalsIgnoreCase(post.getCategory()))
                .sorted(
                        Comparator.comparingLong((BoardPost post) -> post.getLikes() == null ? 0L : post.getLikes()).reversed()
                                .thenComparingLong(post -> post.getViews() == null ? 0L : post.getViews()).reversed()
                                .thenComparingInt(post -> post.getComments() == null ? 0 : post.getComments().size()).reversed()
                                .thenComparing(BoardPost::getId, Comparator.reverseOrder())
                )
                .limit(5)
                .toList();

        for (BoardPost post : posts) {
            long likes = post.getLikes() == null ? 0L : post.getLikes();
            long views = post.getViews() == null ? 0L : post.getViews();
            int comments = post.getComments() == null ? 0 : post.getComments().size();

            if (likes < 1 && comments < 1 && views < 20) {
                continue;
            }

            createNotification(
                    post.getAuthorId(),
                    "popular_post",
                    "내 게시글이 인기글에 올랐어요",
                    "\"" + safeTitle(post.getTitle()) + "\" 글이 인기글에 진입했어요.",
                    "/board/" + post.getId(),
                    "popular-post:" + post.getId()
            );
        }
    }

    @Transactional
    @Scheduled(fixedDelay = 600_000L, initialDelay = 90_000L)
    public void scanWatchlistVolatility() {
        LocalDate today = LocalDate.now(KST);

        for (WatchlistItem item : watchlistRepository.findAll()) {
            MarketItemResponse quote = resolveQuote(item.getMarket(), item.getSymbol(), item.getName(), item.getCoinId());
            if (quote == null || quote.getChangePct() == null) {
                continue;
            }

            double changePct = quote.getChangePct();
            int bucket = bucketizeSignedPercent(changePct);
            if (bucket == 0) {
                continue;
            }

            String direction = bucket > 0 ? "상승" : "하락";
            int absBucket = Math.abs(bucket);
            String key = "watchlist-volatility:" + item.getUserId() + ":" + item.getMarket() + ":" + item.getSymbol()
                    + ":" + today.format(DAY_KEY) + ":" + bucket;

            createNotification(
                    item.getUserId(),
                    "watchlist_volatility",
                    "관심종목 변동성이 커졌어요",
                    item.getName() + "이(가) 오늘 " + direction + " " + absBucket + "% 구간에 들어왔어요.",
                    "/asset/" + item.getMarket() + "/" + item.getSymbol(),
                    key
            );
        }
    }

    @Transactional
    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void scanPortfolioThresholds() {
        LocalDate today = LocalDate.now(KST);

        for (MyPagePortfolio portfolio : myPagePortfolioRepository.findAll()) {
            Long targetAmount = portfolio.getTargetAmount();
            if (targetAmount == null || targetAmount <= 0) {
                continue;
            }

            double totalValue = calculatePortfolioValue(portfolio);
            if (totalValue <= 0) {
                continue;
            }

            double diffPct = ((totalValue - targetAmount) / targetAmount) * 100.0;
            int bucket = bucketizeSignedPercent(diffPct);

            if (bucket == 0 || Math.abs(bucket) > 50) {
                continue;
            }

            String key = "portfolio-threshold:" + portfolio.getUserId() + ":" + today.format(DAY_KEY) + ":" + bucket;
            String direction = bucket > 0 ? "+" : "";
            String title = bucket > 0 ? "목표 금액을 넘어섰어요" : "목표 금액보다 낮아졌어요";

            createNotification(
                    portfolio.getUserId(),
                    "portfolio_threshold",
                    title,
                    "내 포트폴리오가 목표 금액 대비 " + direction + bucket + "% 구간에 진입했어요.",
                    "/mypage",
                    key
            );
        }
    }

    private MarketItemResponse resolveQuote(String market, String symbol, String name, String coinId) {
        String normalizedMarket = String.valueOf(market).toUpperCase(Locale.ROOT);
        if ("CRYPTO".equals(normalizedMarket)) {
            return cryptoMarketService.getCoinQuote(symbol, name, coinId);
        }
        if ("KOSPI".equals(normalizedMarket) || "NASDAQ".equals(normalizedMarket)) {
            return stockMarketService.getQuote(symbol, normalizedMarket);
        }
        return null;
    }

    private double calculatePortfolioValue(MyPagePortfolio portfolio) {
        try {
            JsonNode holdings = objectMapper.readTree(portfolio.getHoldingsJson());
            if (!holdings.isArray()) {
                return 0;
            }

            double totalValue = 0;
            for (JsonNode item : holdings) {
                String market = item.path("market").asText("");
                String symbol = item.path("symbol").asText("");
                String name = item.path("name").asText(symbol);
                String coinId = item.path("coinId").asText("");
                double amount = item.path("amount").asDouble(0);

                if (amount <= 0 || symbol.isBlank()) {
                    continue;
                }

                MarketItemResponse quote = resolveQuote(market, symbol, name, coinId);
                if (quote == null || quote.getPriceKRW() == null) {
                    continue;
                }

                totalValue += quote.getPriceKRW() * amount;
            }

            return totalValue;
        } catch (Exception e) {
            return 0;
        }
    }

    private int bucketizeSignedPercent(double pct) {
        int absBucket = (int) Math.floor(Math.abs(pct) / 5.0) * 5;
        if (absBucket < 5) {
            return 0;
        }
        return pct >= 0 ? absBucket : -absBucket;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.getReadAt() != null,
                notification.getCreatedAt()
        );
    }

    private void createNotification(Long userId, String type, String title, String message, String link, String eventKey) {
        if (userId == null) {
            return;
        }

        String normalizedKey = eventKey == null || eventKey.isBlank() ? null : eventKey.trim();
        if (normalizedKey != null && notificationRepository.existsByUserIdAndEventKey(userId, normalizedKey)) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        notification.setEventKey(normalizedKey);

        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException ignored) {
            // concurrent scheduler duplicate
        }
    }

    private String safeTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank()) {
            return "게시글";
        }
        return value.length() > 48 ? value.substring(0, 48) + "…" : value;
    }
}
