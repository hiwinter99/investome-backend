package com.investome.api.board;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class BoardPostResponse {
    private Long id;
    private Long no;
    private String category;
    private String title;
    private String content;
    private Long authorId;
    private String author;
    private String imageData;
    private String imageName;
    private Long views;
    private Long likes;
    private Long commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean mine;
    private boolean likedByMe;
    private List<BoardCommentResponse> comments;
}