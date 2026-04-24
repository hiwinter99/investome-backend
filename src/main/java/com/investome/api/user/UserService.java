package com.investome.api.user;

import com.investome.api.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public User signup(String email, String password, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = normalizeNickname(nickname);

        userRepository.findByNormalizedEmail(normalizedEmail).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL");
        });

        userRepository.findByNickname(normalizedNickname).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME");
        });

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(normalizedNickname);

        return userRepository.save(user);
    }

    public LoginResponse login(String email, String password, boolean keepLogin) {
        String normalizedEmail = normalizeEmail(email);

        User user = userRepository.findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EMAIL_NOT_FOUND"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        return issueLoginTokens(user, keepLogin);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            refreshTokenRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        User user = userRepository.findById(saved.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        refreshTokenRepository.delete(saved);
        return issueLoginTokens(user, saved.isKeepLogin());
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    private LoginResponse issueLoginTokens(User user, boolean keepLogin) {
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), keepLogin);
        long accessTokenExpiresAt = System.currentTimeMillis() + jwtTokenProvider.getAccessTokenExpirationMs();

        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setToken(refreshToken);
        entity.setKeepLogin(keepLogin);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMs(keepLogin) / 1000));
        refreshTokenRepository.save(entity);

        return new LoginResponse(
                accessToken,
                refreshToken,
                accessTokenExpiresAt,
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String nickname) {
        return nickname == null ? "" : nickname.trim();
    }
}
