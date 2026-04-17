package com.investome.api.board;

import com.investome.api.user.User;
import com.investome.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardPostRepository boardPostRepository;
    private final BoardCommentRepository boardCommentRepository;
    private final BoardPostLikeRepository boardPostLikeRepository;
    private final UserRepository userRepository;

    public List<BoardPostResponse> getPosts(Long currentUserId) {
        List<BoardPostListRow> posts = boardPostRepository.findPostListRows();
        AtomicLong seq = new AtomicLong(posts.size());

        return posts.stream()
                .map(post -> toListResponse(post, seq.getAndDecrement(), currentUserId))
                .toList();
    }

    @Transactional
    public BoardPostResponse getPost(Long postId, boolean increaseView, Long currentUserId) {
        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        if (increaseView) {
            post.setViews((post.getViews() == null ? 0L : post.getViews()) + 1);
        }

        return toResponse(post, post.getId(), currentUserId);
    }

    @Transactional
    public BoardPostResponse createPost(Long userId, BoardCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        BoardPost post = new BoardPost();
        post.setAuthorId(user.getId());
        post.setAuthorNickname(user.getNickname());
        post.setCategory(normalizeCategory(request.getCategory()));
        post.setTitle(normalizeRequired(request.getTitle(), "제목을 입력해주세요."));
        post.setContent(normalizeRequired(request.getContent(), "내용을 입력해주세요."));
        post.setImageData(blankToNull(request.getImageData()));
        post.setImageName(blankToNull(request.getImageName()));

        BoardPost saved = boardPostRepository.save(post);
        return toResponse(saved, saved.getId(), userId);
    }

    @Transactional
    public BoardPostResponse updatePost(Long userId, Long postId, BoardCreateRequest request) {
        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        if (!post.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글만 수정할 수 있습니다.");
        }

        post.setCategory(normalizeCategory(request.getCategory()));
        post.setTitle(normalizeRequired(request.getTitle(), "제목을 입력해주세요."));
        post.setContent(normalizeRequired(request.getContent(), "내용을 입력해주세요."));
        post.setImageData(blankToNull(request.getImageData()));
        post.setImageName(blankToNull(request.getImageName()));

        return toResponse(post, post.getId(), userId);
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        BoardPost post = boardPostRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        if (!post.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글만 삭제할 수 있습니다.");
        }

        boardPostLikeRepository.deleteAllByPost_Id(postId);
        boardCommentRepository.deleteAllByPost_Id(postId);
        boardPostRepository.delete(post);
    }

    @Transactional
    public BoardPostResponse addComment(Long userId, Long postId, BoardCommentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        BoardComment comment = new BoardComment();
        comment.setPost(post);
        comment.setAuthorId(user.getId());
        comment.setAuthorNickname(user.getNickname());
        comment.setContent(normalizeRequired(request.getContent(), "댓글 내용을 입력해주세요."));

        if (request.getParentCommentId() != null) {
            BoardComment parent = post.getComments().stream()
                    .filter(existing -> existing.getId().equals(request.getParentCommentId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글 대상 댓글을 찾을 수 없습니다."));

            comment.setParentComment(parent.getParentComment() != null ? parent.getParentComment() : parent);
        }

        BoardComment saved = boardCommentRepository.save(comment);
        post.getComments().add(saved);

        return toResponse(post, post.getId(), userId);
    }

    @Transactional
    public BoardPostResponse deleteComment(Long userId, Long postId, Long commentId) {
        BoardComment comment = boardCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글이 없습니다."));

        if (!comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        if (!comment.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.");
        }

        if (comment.getParentComment() == null && boardCommentRepository.existsByParentComment_Id(commentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글이 있는 댓글은 삭제할 수 없습니다.");
        }

        BoardPost post = comment.getPost();
        boardCommentRepository.delete(comment);
        post.getComments().removeIf(existing -> existing.getId().equals(commentId));

        return toResponse(post, post.getId(), userId);
    }

    @Transactional
    public BoardPostResponse toggleLike(Long userId, Long postId) {
        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        boardPostLikeRepository.findByPost_IdAndUserId(postId, userId)
                .ifPresentOrElse(
                        boardPostLikeRepository::delete,
                        () -> {
                            BoardPostLike like = new BoardPostLike();
                            like.setPost(post);
                            like.setUserId(userId);
                            boardPostLikeRepository.save(like);
                        }
                );

        post.setLikes(boardPostLikeRepository.countByPost_Id(postId));

        BoardPost refreshed = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        return toResponse(refreshed, refreshed.getId(), userId);
    }

    private BoardPostResponse toResponse(BoardPost post, Long no, Long currentUserId) {
        boolean mine = currentUserId != null && currentUserId.equals(post.getAuthorId());
        boolean likedByMe = currentUserId != null &&
                boardPostLikeRepository.findByPost_IdAndUserId(post.getId(), currentUserId).isPresent();

        List<BoardCommentResponse> commentResponses = sortComments(post.getComments()).stream()
                .map(comment -> {
                    boolean isReply = comment.getParentComment() != null;

                    return new BoardCommentResponse(
                            comment.getId(),
                            comment.getAuthorId(),
                            comment.getAuthorNickname(),
                            comment.getContent(),
                            comment.getCreatedAt(),
                            isReply ? comment.getParentComment().getId() : null,
                            isReply ? 1 : 0,
                            currentUserId != null && currentUserId.equals(comment.getAuthorId())
                    );
                })
                .toList();

        return new BoardPostResponse(
                post.getId(),
                no,
                post.getCategory(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorNickname(),
                hasImage(post.getImageData()),
                post.getImageData(),
                post.getImageName(),
                post.getViews() == null ? 0L : post.getViews(),
                post.getLikes() == null ? 0L : post.getLikes(),
                (long) commentResponses.size(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                mine,
                likedByMe,
                commentResponses
        );
    }

    private BoardPostResponse toListResponse(BoardPostListRow post, Long no, Long currentUserId) {
        boolean mine = currentUserId != null && currentUserId.equals(post.getAuthorId());

        return new BoardPostResponse(
                post.getId(),
                no,
                post.getCategory(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorNickname(),
                post.isHasImage(),
                null,
                null,
                post.getViews() == null ? 0L : post.getViews(),
                post.getLikes() == null ? 0L : post.getLikes(),
                post.getCommentCount() == null ? 0L : post.getCommentCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                mine,
                false,
                List.of()
        );
    }

    private List<BoardComment> sortComments(List<BoardComment> comments) {
        var repliesByParent = comments.stream()
                .filter(comment -> comment.getParentComment() != null)
                .collect(Collectors.groupingBy(comment -> comment.getParentComment().getId()));

        return comments.stream()
                .filter(comment -> comment.getParentComment() == null)
                .sorted(Comparator.comparing(BoardComment::getCreatedAt).thenComparing(BoardComment::getId))
                .flatMap(parent -> {
                    List<BoardComment> replies = repliesByParent.getOrDefault(parent.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(BoardComment::getCreatedAt).thenComparing(BoardComment::getId))
                            .toList();

                    return Stream.concat(Stream.of(parent), replies.stream());
                })
                .toList();
    }

    private boolean hasImage(String imageData) {
        return imageData != null && !imageData.isBlank();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "free";
        }
        return category.trim();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
