package com.investome.api.board;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BoardCommentResponse {
    private Long id;
    private Long authorId;
    private String author;
    private String content;
    private LocalDateTime createdAt;
    private boolean mine;
}