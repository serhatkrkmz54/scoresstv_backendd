package com.scorestv.comments;

import com.scorestv.comments.dto.CommentCreateRequest;
import com.scorestv.comments.dto.CommentPageResponse;
import com.scorestv.comments.dto.CommentView;
import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mac yorum sistemi public endpoint'leri.
 *
 * <p>GET endpoint'leri herkes (anonymous + auth) cagirabilir; anonymous icin
 * {@code likedByMe} ve {@code isMine} her zaman false. POST/DELETE auth
 * gerektirir (JWT) — SecurityConfig'te kural tanimlanmistir.
 */
@RestController
@RequestMapping("/api/v1/comments")
public class PublicCommentController {

    private static final int DEFAULT_PAGE_SIZE = 30;

    private final CommentService service;

    public PublicCommentController(CommentService service) {
        this.service = service;
    }

    /**
     * Bir macin yorumlari — sayfali + sort'lu (reply'lar her top-level'in
     * altinda nested).
     *
     * @param sort "newest" (default) veya "popular" (en cok begenilen once)
     */
    @GetMapping("/fixtures/{fixtureId}")
    public CommentPageResponse listFootball(
            @PathVariable Long fixtureId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return doList("FOOTBALL", fixtureId, page, size, sort, currentUser);
    }

    /** Bir basketbol macinin yorumlari. */
    @GetMapping("/basketball/{gameId}")
    public CommentPageResponse listBasketball(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return doList("BASKETBALL", gameId, page, size, sort, currentUser);
    }

    /** Bir voleybol macinin yorumlari. */
    @GetMapping("/volleyball/{gameId}")
    public CommentPageResponse listVolleyball(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return doList("VOLLEYBALL", gameId, page, size, sort, currentUser);
    }

    /** Bir haberin yorumlari (segment="NEWS", matchId=haber id). Yorum sistemi
     *  sport-agnostik; "NEWS" serbest string olarak saklanir, maca FK yok. */
    @GetMapping("/news/{newsId}")
    public CommentPageResponse listNews(
            @PathVariable Long newsId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return doList("NEWS", newsId, page, size, sort, currentUser);
    }

    private CommentPageResponse doList(String sport, Long matchId, int page, int size,
                                       String sort, CurrentUser currentUser) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, DEFAULT_PAGE_SIZE * 3));
        Long userId = currentUser != null ? currentUser.id() : null;
        CommentService.SortMode mode = "popular".equalsIgnoreCase(sort)
                ? CommentService.SortMode.POPULAR
                : CommentService.SortMode.NEWEST;
        return service.list(matchId, sport, userId, safePage, safeSize, mode);
    }

    /** Yeni futbol yorumu (auth gerekli). */
    @PostMapping("/fixtures/{fixtureId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView createFootball(
            @PathVariable Long fixtureId,
            @Valid @RequestBody CommentCreateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return service.create(fixtureId, "FOOTBALL", currentUser.id(), req);
    }

    /** Yeni basketbol yorumu (auth gerekli). */
    @PostMapping("/basketball/{gameId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView createBasketball(
            @PathVariable Long gameId,
            @Valid @RequestBody CommentCreateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return service.create(gameId, "BASKETBALL", currentUser.id(), req);
    }

    /** Yeni voleybol yorumu (auth gerekli). */
    @PostMapping("/volleyball/{gameId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView createVolleyball(
            @PathVariable Long gameId,
            @Valid @RequestBody CommentCreateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return service.create(gameId, "VOLLEYBALL", currentUser.id(), req);
    }

    /** Yeni haber yorumu (auth gerekli). matchId=haber id, sport="NEWS". */
    @PostMapping("/news/{newsId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView createNews(
            @PathVariable Long newsId,
            @Valid @RequestBody CommentCreateRequest req,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return service.create(newsId, "NEWS", currentUser.id(), req);
    }

    /** Yorum sil (sahibi veya admin, auth gerekli). */
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long commentId,
                       @AuthenticationPrincipal CurrentUser currentUser) {
        service.delete(commentId, currentUser.id(), currentUser.role());
    }

    /** Begeni toggle — varsa kaldir, yoksa ekle (auth gerekli). */
    @PostMapping("/{commentId}/like")
    public CommentService.ToggleResult toggleLike(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return service.toggleLike(commentId, currentUser.id());
    }
}
