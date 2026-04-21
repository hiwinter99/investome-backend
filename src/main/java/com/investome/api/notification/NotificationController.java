package com.investome.api.notification;

import com.investome.api.config.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public NotificationListResponse getNotifications(HttpServletRequest request) {
        return notificationService.getNotifications(extractUserId(request));
    }

    @PostMapping("/{notificationId}/read")
    public NotificationListResponse markRead(@PathVariable Long notificationId, HttpServletRequest request) {
        return notificationService.markRead(extractUserId(request), notificationId);
    }

    @PostMapping("/read-all")
    public NotificationListResponse markAllRead(HttpServletRequest request) {
        return notificationService.markAllRead(extractUserId(request));
    }

    private Long extractUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다.");
        }

        try {
            return jwtTokenProvider.getUserId(auth.substring(7));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }
}
