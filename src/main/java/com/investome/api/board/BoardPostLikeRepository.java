package com.investome.api.board;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardPostLikeRepository extends JpaRepository<BoardPostLike, Long> {
    Optional<BoardPostLike> findByPost_IdAndUserId(Long postId, Long userId);
    long countByPost_Id(Long postId);
    void deleteByPost_IdAndUserId(Long postId, Long userId);
    void deleteAllByPost_Id(Long postId);
}