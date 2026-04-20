package com.investome.api.user;

import com.investome.api.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public User signup(String email, String password, String nickname) {

        userRepository.findByEmail(email).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "?대? 議댁옱?섎뒗 ?대찓?쇱엯?덈떎.");
        });

        userRepository.findByNickname(nickname).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "?대? 議댁옱?섎뒗 ?됰꽕?꾩엯?덈떎.");
        });

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);

        return userRepository.save(user);
    }

    public LoginResponse login(String email, String password, boolean keepLogin) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "?대찓?쇱씠 議댁옱?섏? ?딆뒿?덈떎."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "鍮꾨?踰덊샇媛 ??몄뒿?덈떎.");
        }

        return issueLoginTokens(user, keepLogin);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "濡쒓렇?몄씠 留뚮즺?섏뿀?댁슂.");
        }

        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "濡쒓렇?몄씠 留뚮즺?섏뿀?댁슂."));

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "濡쒓렇?몄씠 留뚮즺?섏뿀?댁슂.");
        }

        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            refreshTokenRepository.delete(saved);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "濡쒓렇?몄씠 留뚮즺?섏뿀?댁슂.");
        }

        User user = userRepository.findById(saved.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "濡쒓렇?몄씠 留뚮즺?섏뿀?댁슂."));

        refreshTokenRepository.delete(saved);
        return issueLoginTokens(user, saved.isKeepLogin());
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    private LoginResponse issueLoginTokens(User user, boolean keepLogin) {
        refreshTokenRepository.deleteByUserId(user.getId());

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
}
