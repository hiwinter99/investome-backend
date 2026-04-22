package com.investome.api.user;

import com.investome.api.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public User signup(String email, String password, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = normalizeNickname(nickname);

        userRepository.findByNormalizedEmail(normalizedEmail).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입되어 있는 이메일입니다.");
        });

        userRepository.findByNickname(normalizedNickname).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가입된 이메일이 없습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return issueLoginTokens(user, keepLogin);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다.");
        }

        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."));

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다.");
        }

        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            refreshTokenRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다.");
        }

        User user = userRepository.findById(saved.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."));

        refreshTokenRepository.delete(saved);
        return issueLoginTokens(user, saved.isKeepLogin());
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteAllByToken(refreshToken);
    }

    private LoginResponse issueLoginTokens(User user, boolean keepLogin) {
        refreshTokenRepository.deleteAllByUserId(user.getId());

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
