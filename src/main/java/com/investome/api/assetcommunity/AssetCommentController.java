package com.investome.api.assetcommunity;

import com.investome.api.config.JwtTokenProvider;
import com.investome.api.security.ClientRequestResolver;
import com.investome.api.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets/{market}/{symbol}/comments")
public class AssetCommentController {

    private final AssetCommentService assetCommentService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientRequestResolver clientRequestResolver;
    private final RateLimitService rateLimitService;

    @GetMapping
    public List<AssetCommentResponse> getComments(
            @PathVariable String market,
            @PathVariable String symbol,
            HttpServletRequest request
    ) {
        return assetCommentService.getComments(market, symbol, extractUserIdOrNull(request));
    }

    @PostMapping
    public AssetCommentResponse createComment(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestBody AssetCommentCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = extractUserId(httpRequest);
        rateLimitService.checkAssetCommentAllowed(clientRequestResolver.resolveClientIp(httpRequest), userId);
        return assetCommentService.createComment(userId, market, symbol, request);
    }

    @DeleteMapping("/{commentId}")
    public void deleteComment(
            @PathVariable String market,
            @PathVariable String symbol,
            @PathVariable Long commentId,
            HttpServletRequest httpRequest
    ) {
        assetCommentService.deleteComment(extractUserId(httpRequest), market, symbol, commentId);
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

    private Long extractUserIdOrNull(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");

        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }

        try {
            return jwtTokenProvider.getUserId(auth.substring(7));
        } catch (Exception e) {
            return null;
        }
    }
}
