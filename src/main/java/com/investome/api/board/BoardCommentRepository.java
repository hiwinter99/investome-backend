package com.investome.api.board;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {
    void deleteAllByPost_Id(Long postId);
}