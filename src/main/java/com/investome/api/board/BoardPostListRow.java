package com.investome.api.board;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class BoardPostListRow {

    private final Long id;
    private final String category;
    private final String title;
    private final String content;
    private final Long authorId;
    private final String authorNickname;
    private final boolean hasImage;
    private final Long views;
    private final Long likes;
    private final Long commentCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public BoardPostListRow(
            Long id,
            String category,
            String title,
            String content,
            Long authorId,
            String authorNickname,
            boolean hasImage,
            Long views,
            Long likes,
            Long commentCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.authorNickname = authorNickname;
        this.hasImage = hasImage;
        this.views = views;
        this.likes = likes;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
