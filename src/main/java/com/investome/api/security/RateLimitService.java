package com.investome.api.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final int SIGNUP_LIMIT_PER_HOUR = 5;
    private static final int LOGIN_LIMIT_PER_IP = 20;
    private static final int LOGIN_LIMIT_PER_EMAIL = 10;
    private static final int BOARD_POST_LIMIT_PER_USER = 5;
    private static final int BOARD_POST_LIMIT_PER_IP = 8;
    private static final int BOARD_COMMENT_LIMIT_PER_USER = 12;
    private static final int BOARD_COMMENT_LIMIT_PER_IP = 20;
    private static final int ASSET_COMMENT_LIMIT_PER_USER = 5;
    private static final int ASSET_COMMENT_LIMIT_PER_IP = 10;

    private static final Duration SIGNUP_WINDOW = Duration.ofHours(1);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);
    private static final Duration BOARD_POST_WINDOW = Duration.ofMinutes(30);
    private static final Duration BOARD_COMMENT_WINDOW = Duration.ofMinutes(10);
    private static final Duration ASSET_COMMENT_WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public void checkSignupAllowed(String clientIp) {
        boolean allowed = recordAttempt("signup:ip:" + safeKey(clientIp), SIGNUP_LIMIT_PER_HOUR, SIGNUP_WINDOW);
        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "회원가입 시도가 너무 많아요. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public void checkLoginAllowed(String clientIp, String email) {
        boolean ipAllowed = recordAttempt("login:ip:" + safeKey(clientIp), LOGIN_LIMIT_PER_IP, LOGIN_WINDOW);
        boolean emailAllowed = recordAttempt("login:email:" + safeKey(email), LOGIN_LIMIT_PER_EMAIL, LOGIN_WINDOW);

        if (!ipAllowed || !emailAllowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 너무 많아요. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public void checkBoardPostAllowed(String clientIp, Long userId) {
        boolean ipAllowed = recordAttempt("board-post:ip:" + safeKey(clientIp), BOARD_POST_LIMIT_PER_IP, BOARD_POST_WINDOW);
        boolean userAllowed = recordAttempt("board-post:user:" + safeKey(String.valueOf(userId)), BOARD_POST_LIMIT_PER_USER, BOARD_POST_WINDOW);

        if (!ipAllowed || !userAllowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "게시글 작성이 너무 많아요. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public void checkBoardCommentAllowed(String clientIp, Long userId) {
        boolean ipAllowed = recordAttempt("board-comment:ip:" + safeKey(clientIp), BOARD_COMMENT_LIMIT_PER_IP, BOARD_COMMENT_WINDOW);
        boolean userAllowed = recordAttempt("board-comment:user:" + safeKey(String.valueOf(userId)), BOARD_COMMENT_LIMIT_PER_USER, BOARD_COMMENT_WINDOW);

        if (!ipAllowed || !userAllowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "댓글 작성이 너무 많아요. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public void checkAssetCommentAllowed(String clientIp, Long userId) {
        boolean ipAllowed = recordAttempt("asset-comment:ip:" + safeKey(clientIp), ASSET_COMMENT_LIMIT_PER_IP, ASSET_COMMENT_WINDOW);
        boolean userAllowed = recordAttempt("asset-comment:user:" + safeKey(String.valueOf(userId)), ASSET_COMMENT_LIMIT_PER_USER, ASSET_COMMENT_WINDOW);

        if (!ipAllowed || !userAllowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "의견 등록이 너무 많아요. 잠시 후 다시 시도해주세요."
            );
        }
    }

    private boolean recordAttempt(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();

        Deque<Long> bucket = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < cutoff) {
                bucket.pollFirst();
            }

            if (bucket.size() >= limit) {
                return false;
            }

            bucket.addLast(now);
            return true;
        }
    }

    private String safeKey(String raw) {
        return raw == null || raw.isBlank() ? "unknown" : raw.trim().toLowerCase();
    }
}
