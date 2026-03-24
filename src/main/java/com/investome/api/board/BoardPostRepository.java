package com.investome.api.board;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    @Override
    @EntityGraph(attributePaths = "comments")
    List<BoardPost> findAll();

    @EntityGraph(attributePaths = "comments")
    Optional<BoardPost> findWithCommentsById(Long id);
}