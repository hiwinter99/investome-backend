package com.investome.api.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public String signup(@RequestBody SignupRequest request) {
        userService.signup(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );
        return "회원가입 성공";
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
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
