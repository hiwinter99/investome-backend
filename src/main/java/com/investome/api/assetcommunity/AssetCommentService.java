package com.investome.api.assetcommunity;

import com.investome.api.user.User;
import com.investome.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AssetCommentService {

    private final AssetCommentRepository assetCommentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AssetCommentResponse> getComments(String market, String symbol, Long currentUserId) {
        String normalizedMarket = normalizeMarket(market);
        String normalizedSymbol = normalizeSymbol(symbol);

        return assetCommentRepository
                .findTop100ByMarketAndSymbolOrderByCreatedAtDescIdDesc(normalizedMarket, normalizedSymbol)
                .stream()
                .map(comment -> new AssetCommentResponse(
                        comment.getId(),
                        comment.getAuthorId(),
                        comment.getAuthorNickname(),
                        comment.getContent(),
                        comment.getCreatedAt(),
                        currentUserId != null && currentUserId.equals(comment.getAuthorId())
                ))
                .toList();
    }

    @Transactional
    public AssetCommentResponse createComment(Long userId, String market, String symbol, AssetCommentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        String normalizedContent = normalizeRequired(request.getContent(), "의견 내용을 입력해주세요.");
        String normalizedAssetName = normalizeRequired(request.getAssetName(), "자산명을 확인할 수 없습니다.");

        AssetComment comment = new AssetComment();
        comment.setMarket(normalizeMarket(market));
        comment.setSymbol(normalizeSymbol(symbol));
        comment.setAssetName(limit(normalizedAssetName, 120));
        comment.setAuthorId(user.getId());
        comment.setAuthorNickname(user.getNickname());
        comment.setContent(limit(normalizedContent, 300));

        AssetComment saved = assetCommentRepository.save(comment);
        return new AssetCommentResponse(
                saved.getId(),
                saved.getAuthorId(),
                saved.getAuthorNickname(),
                saved.getContent(),
                saved.getCreatedAt(),
                true
        );
    }

    private String normalizeMarket(String market) {
        String normalized = normalizeRequired(market, "시장 정보가 올바르지 않습니다.");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        String normalized = normalizeRequired(symbol, "심볼 정보가 올바르지 않습니다.");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        return trimmed;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
