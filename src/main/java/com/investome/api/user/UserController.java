package com.investome.api.user;

import com.investome.api.security.ClientRequestResolver;
import com.investome.api.security.RateLimitService;
import com.investome.api.security.TurnstileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final ClientRequestResolver clientRequestResolver;
    private final RateLimitService rateLimitService;
    private final TurnstileService turnstileService;

    @PostMapping("/signup")
    public String signup(@RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        String clientIp = clientRequestResolver.resolveClientIp(servletRequest);
        rateLimitService.checkSignupAllowed(clientIp);
        turnstileService.verifyToken(request.getTurnstileToken(), clientIp);

        userService.signup(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );
        return "회원가입 성공";
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = clientRequestResolver.resolveClientIp(servletRequest);
        rateLimitService.checkLoginAllowed(clientIp, request.getEmail());
        turnstileService.verifyToken(request.getTurnstileToken(), clientIp);

        return userService.login(
                request.getEmail(),
                request.getPassword(),
                request.isKeepLogin()
        );
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshTokenRequest request) {
        return userService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public void logout(@RequestBody RefreshTokenRequest request) {
        userService.logout(request.getRefreshToken());
    }
}
