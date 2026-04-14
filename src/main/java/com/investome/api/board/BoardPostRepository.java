package com.investome.api.board;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    @Override
    @EntityGraph(attributePaths = "comments")
    List<BoardPost> findAll();

    @Query("""
            select new com.investome.api.board.BoardPostListRow(
                p.id,
                p.category,
                p.title,
                p.content,
                p.authorId,
                p.authorNickname,
                case when p.imageData is not null and p.imageData <> '' then true else false end,
                p.views,
                p.likes,
                count(c.id),
                p.createdAt,
                p.updatedAt
            )
            from BoardPost p
            left join p.comments c
            group by p.id, p.category, p.title, p.content, p.authorId, p.authorNickname,
                     p.imageData, p.views, p.likes, p.createdAt, p.updatedAt
            order by p.id desc
            """)
    List<BoardPostListRow> findPostListRows();

    @EntityGraph(attributePaths = "comments")
    Optional<BoardPost> findWithCommentsById(Long id);
}
