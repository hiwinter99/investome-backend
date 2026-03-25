package com.investome.api.watchlist;

import com.investome.api.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public List<WatchlistResponse> getList(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = extractUserId(authorizationHeader);
        return watchlistService.getList(userId);
    }

    @PostMapping
    public List<WatchlistResponse> add(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody WatchlistRequest request
    ) {
        Long userId = extractUserId(authorizationHeader);
        return watchlistService.add(userId, request);
    }

    @DeleteMapping
    public List<WatchlistResponse> remove(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody WatchlistRequest request
    ) {
        Long userId = extractUserId(authorizationHeader);
        return watchlistService.remove(userId, request);
    }

    private Long extractUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "잘못된 인증 헤더입니다.");
        }

        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 비어 있습니다.");
        }

        try {
            return jwtTokenProvider.getUserId(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }
}