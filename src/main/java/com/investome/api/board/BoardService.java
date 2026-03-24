package com.investome.api.board;

import com.investome.api.user.User;
import com.investome.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardPostRepository boardPostRepository;
    private final BoardCommentRepository boardCommentRepository;
    private final BoardPostLikeRepository boardPostLikeRepository;
    private final UserRepository userRepository;

    public List<BoardPostResponse> getPosts(Long currentUserId) {
        List<BoardPost> posts = boardPostRepository.findAll();
        AtomicLong seq = new AtomicLong(posts.size());

        return posts.stream()
                .sorted(Comparator.comparing(BoardPost::getId).reversed())
                .map(post -> toResponse(post, seq.getAndDecrement(), currentUserId))
                .toList();
    }

    public BoardPostResponse getPost(Long postId, boolean increaseView, Long currentUserId) {
        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        if (increaseView) {
            post.setViews((post.getViews() == null ? 0L : post.getViews()) + 1);
            boardPostRepository.save(post);
        }

        BoardPost refreshed = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        return toResponse(refreshed, refreshed.getId(), currentUserId);
    }

    public BoardPostResponse createPost(Long userId, BoardCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요.");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }

        BoardPost post = new BoardPost();
        post.setAuthorId(user.getId());
        post.setAuthorNickname(user.getNickname());
        post.setCategory(request.getCategory() == null || request.getCategory().isBlank() ? "free" : request.getCategory());
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setImageData(blankToNull(request.getImageData()));
        post.setImageName(blankToNull(request.getImageName()));

        BoardPost saved = boardPostRepository.save(post);
        return toResponse(saved, saved.getId(), userId);
    }

    public BoardPostResponse updatePost(Long userId, Long postId, BoardCreateRequest request) {
        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        if (!post.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글만 수정할 수 있습니다.");
        }

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요.");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }

        post.setCategory(request.getCategory() == null || request.getCategory().isBlank() ? "free" : request.getCategory());
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setImageData(blankToNull(request.getImageData()));
        post.setImageName(blankToNull(request.getImageName()));

        BoardPost saved = boardPostRepository.save(post);
        return toResponse(saved, saved.getId(), userId);
    }

    public void deletePost(Long userId, Long postId) {
        BoardPost post = boardPostRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        if (!post.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글만 삭제할 수 있습니다.");
        }

        boardPostRepository.delete(post);
    }

    public BoardPostResponse addComment(Long userId, Long postId, BoardCommentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }

        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        BoardComment comment = new BoardComment();
        comment.setPost(post);
        comment.setAuthorId(user.getId());
        comment.setAuthorNickname(user.getNickname());
        comment.setContent(request.getContent().trim());

        boardCommentRepository.save(comment);

        BoardPost refreshed = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        return toResponse(refreshed, refreshed.getId(), userId);
    }

    public BoardPostResponse deleteComment(Long userId, Long postId, Long commentId) {
        BoardComment comment = boardCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글이 없습니다."));

        if (!comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        if (!comment.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.");
        }

        boardCommentRepository.delete(comment);

        BoardPost refreshed = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        return toResponse(refreshed, refreshed.getId(), userId);
    }

    public BoardPostResponse toggleLike(Long userId, Long postId) {
        BoardPost post = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        var existing = boardPostLikeRepository.findByPostIdAndUserId(postId, userId);

        if (existing.isPresent()) {
            boardPostLikeRepository.delete(existing.get());
        } else {
            BoardPostLike like = new BoardPostLike();
            like.setPost(post);
            like.setUserId(userId);
            boardPostLikeRepository.save(like);
        }

        post.setLikes(boardPostLikeRepository.countByPostId(postId));
        boardPostRepository.save(post);

        BoardPost refreshed = boardPostRepository.findWithCommentsById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        return toResponse(refreshed, refreshed.getId(), userId);
    }

    private BoardPostResponse toResponse(BoardPost post, Long no, Long currentUserId) {
        boolean mine = currentUserId != null && currentUserId.equals(post.getAuthorId());
        boolean likedByMe = currentUserId != null &&
                boardPostLikeRepository.findByPostIdAndUserId(post.getId(), currentUserId).isPresent();

        List<BoardCommentResponse> commentResponses = post.getComments().stream()
                .map(c -> new BoardCommentResponse(
                        c.getId(),
                        c.getAuthorId(),
                        c.getAuthorNickname(),
                        c.getContent(),
                        c.getCreatedAt(),
                        currentUserId != null && currentUserId.equals(c.getAuthorId())
                ))
                .toList();

        return new BoardPostResponse(
                post.getId(),
                no,
                post.getCategory(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorNickname(),
                post.getImageData(),
                post.getImageName(),
                post.getViews() == null ? 0L : post.getViews(),
                boardPostLikeRepository.countByPostId(post.getId()),
                (long) commentResponses.size(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                mine,
                likedByMe,
                commentResponses
        );
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}