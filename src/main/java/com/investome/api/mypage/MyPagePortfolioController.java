package com.investome.api.mypage;

import com.investome.api.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mypage")
public class MyPagePortfolioController {

    private final MyPagePortfolioService myPagePortfolioService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/portfolio")
    public MyPagePortfolioResponse getPortfolio(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = extractUserId(authorizationHeader);
        MyPagePortfolioResponse response = myPagePortfolioService.getByUserId(userId);

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "마이페이지 포트폴리오가 없습니다.");
        }

        return response;
    }

    @PutMapping("/portfolio")
    public MyPagePortfolioResponse savePortfolio(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody MyPagePortfolioRequest request
    ) {
        Long userId = extractUserId(authorizationHeader);
        return myPagePortfolioService.save(userId, request);
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