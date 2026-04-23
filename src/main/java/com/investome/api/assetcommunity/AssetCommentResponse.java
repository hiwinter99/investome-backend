package com.investome.api.assetcommunity;

import java.time.LocalDateTime;

public record AssetCommentResponse(
        Long id,
        Long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt,
        boolean mine
) {
}
