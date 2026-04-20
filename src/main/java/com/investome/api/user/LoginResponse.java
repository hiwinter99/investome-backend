package com.investome.api.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresAt;
    private Long id;
    private String email;
    private String nickname;
    private String role;
}
