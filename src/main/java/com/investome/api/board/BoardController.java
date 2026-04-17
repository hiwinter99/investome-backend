package com.investome.api.board;

import com.investome.api.config.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/board")
public class BoardController {

    private final BoardService boardService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/posts")
    public List<BoardPostResponse> getPosts(HttpServletRequest request) {
        return boardService.getPosts(extractUserIdOrNull(request));
    }

    @GetMapping("/posts/{postId}")
    public BoardPostResponse getPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "false") boolean increaseView,
            HttpServletRequest request
    ) {
        return boardService.getPost(postId, increaseView, extractUserIdOrNull(request));
    }

    @PostMapping("/posts")
    public BoardPostResponse createPost(
            @RequestBody BoardCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        return boardService.createPost(extractUserId(httpRequest), request);
    }

    @PutMapping("/posts/{postId}")
    public BoardPostResponse updatePost(
            @PathVariable Long postId,
            @RequestBody BoardCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        return boardService.updatePost(extractUserId(httpRequest), postId, request);
    }

    @DeleteMapping("/posts/{postId}")
    public void deletePost(
            @PathVariable Long postId,
            HttpServletRequest httpRequest
    ) {
        boardService.deletePost(extractUserId(httpRequest), postId);
    }

    @PostMapping("/posts/{postId}/comments")
    public BoardPostResponse addComment(
            @PathVariable Long postId,
            @RequestBody BoardCommentCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        return boardService.addComment(extractUserId(httpRequest), postId, request);
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public BoardPostResponse deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            HttpServletRequest httpRequest
    ) {
        return boardService.deleteComment(extractUserId(httpRequest), postId, commentId);
    }

    @PostMapping("/posts/{postId}/like")
    public BoardPostResponse toggleLike(
            @PathVariable Long postId,
            HttpServletRequest httpRequest
    ) {
        return boardService.toggleLike(extractUserId(httpRequest), postId);
    }

    private Long extractUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");

        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다.");
        }

        try {
            return jwtTokenProvider.getUserId(auth.substring(7));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }

    private Long extractUserIdOrNull(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");

        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }

        try {
            return jwtTokenProvider.getUserId(auth.substring(7));
        } catch (Exception e) {
            return null;
        }
    }
}
