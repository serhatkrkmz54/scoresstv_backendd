package com.scorestv.comments;

import com.scorestv.comments.dto.CommentCreateRequest;
import com.scorestv.comments.dto.CommentPageResponse;
import com.scorestv.comments.dto.CommentView;
import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.user.Role;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mac yorumlari servis katmani — listele (sortlu + reply'li), olustur (top
 * veya reply), sil, begeni-toggle.
 *
 * <p>Sort modlari:
 * <ul>
 *   <li>{@code newest} — en yeni once (default)</li>
 *   <li>{@code popular} — en cok begenilen once, tie en yeni</li>
 * </ul>
 *
 * <p>Reply (yanit) mantigi:
 * <ul>
 *   <li>Top-level yorumlar parent_id=NULL</li>
 *   <li>Yanitlar parent_id dolu — UI nested gosterir</li>
 *   <li>Maksimum 1 seviye thread — child'a yanit yine ayni parent'i hedefler</li>
 * </ul>
 */
@Service
public class CommentService {

    private final FixtureCommentRepository commentRepository;
    private final FixtureCommentLikeRepository likeRepository;
    private final FixtureRepository fixtureRepository;
    private final UserRepository userRepository;

    public CommentService(FixtureCommentRepository commentRepository,
                          FixtureCommentLikeRepository likeRepository,
                          FixtureRepository fixtureRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.fixtureRepository = fixtureRepository;
        this.userRepository = userRepository;
    }

    public enum SortMode { NEWEST, POPULAR }

    /**
     * Top-level yorumlari ve her birinin reply'larini sayfali getir.
     * Anonim icin currentUserId null verilir.
     */
    @Transactional(readOnly = true)
    public CommentPageResponse list(Long fixtureId, Long currentUserId,
                                    int page, int size, SortMode sort) {
        Slice<FixtureComment> slice = (sort == SortMode.POPULAR)
                ? commentRepository.findTopLevelPopular(
                        fixtureId, PageRequest.of(page, size))
                : commentRepository.findTopLevelNewest(
                        fixtureId, PageRequest.of(page, size, Sort.unsorted()));

        List<FixtureComment> topLevels = slice.getContent();
        if (topLevels.isEmpty()) {
            return new CommentPageResponse(
                    List.of(),
                    commentRepository.countByFixtureIdAndDeletedFalse(fixtureId),
                    false);
        }

        // Reply'lari tek query'de cek — N+1 onlenir.
        List<Long> parentIds = topLevels.stream().map(FixtureComment::getId).toList();
        List<FixtureComment> replies =
                commentRepository.findRepliesByParentIds(parentIds);

        // Tum (top + reply) yorum id'leri — begeni/cevap batch icin.
        List<Long> allIds = new ArrayList<>(parentIds);
        for (FixtureComment r : replies) allIds.add(r.getId());

        Map<Long, Long> likeCounts = likeRepository.countMap(allIds);
        Set<Long> likedByMe = currentUserId == null
                ? Set.of()
                : new HashSet<>(commentRepository.findLikedCommentIds(currentUserId, allIds));

        // Reply'lari parent_id'ye gore grupla (LinkedHashMap → sira korur).
        Map<Long, List<CommentView>> repliesByParent = new LinkedHashMap<>();
        for (FixtureComment r : replies) {
            Long pid = r.getParent().getId();
            repliesByParent.computeIfAbsent(pid, k -> new ArrayList<>())
                    .add(toView(r, currentUserId, likeCounts, likedByMe));
        }

        // Top-level'lari view'a cevir + reply'leri ekle.
        List<CommentView> items = topLevels.stream()
                .map(c -> {
                    CommentView base = toView(c, currentUserId, likeCounts, likedByMe);
                    List<CommentView> rs =
                            repliesByParent.getOrDefault(c.getId(), List.of());
                    return new CommentView(
                            base.id(), base.content(), base.user(), base.createdAt(),
                            base.likeCount(), base.likedByMe(), base.isMine(),
                            null, rs);
                })
                .toList();

        long totalCount = commentRepository.countByFixtureIdAndDeletedFalse(fixtureId);
        return new CommentPageResponse(items, totalCount, slice.hasNext());
    }

    /** Helper — entity → view. Reply listesi caller tarafindan eklenir. */
    private CommentView toView(FixtureComment c, Long currentUserId,
                               Map<Long, Long> likeCounts, Set<Long> likedByMe) {
        User u = c.getUser();
        return new CommentView(
                c.getId(),
                c.getContent(),
                new CommentView.UserRef(
                        u.getId(),
                        u.getDisplayName(),
                        null,  // profile photo henuz desteklenmiyor
                        u.getCountry()),
                c.getCreatedAt(),
                likeCounts.getOrDefault(c.getId(), 0L),
                likedByMe.contains(c.getId()),
                currentUserId != null && currentUserId.equals(u.getId()),
                c.getParent() == null ? null : c.getParent().getId(),
                List.of());
    }

    /**
     * Yeni yorum olustur. parentId dolu ise reply (parent_id field set).
     * Reply'a reply yapilirsa otomatik en ust parent'i hedefler — 1 seviye
     * thread korunur.
     */
    @Transactional
    public CommentView create(Long fixtureId, Long userId,
                              CommentCreateRequest req) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Mac bulunamadi."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanici bulunamadi."));

        FixtureComment c = new FixtureComment();
        c.setFixture(fixture);
        c.setUser(user);
        c.setContent(req.content().trim());

        // Parent çöz — reply'a reply ise top parent'i hedefle (1 seviye limit).
        if (req.parentId() != null) {
            FixtureComment parent = commentRepository.findById(req.parentId())
                    .orElseThrow(() -> ApiException.notFound("Üst yorum bulunamadi."));
            if (parent.isDeleted()) {
                throw ApiException.notFound("Üst yorum silinmis.");
            }
            // Reply'a reply ise topmost parent'i hedef tut
            FixtureComment topParent = parent.getParent() == null
                    ? parent
                    : parent.getParent();
            c.setParent(topParent);
        }

        c = commentRepository.save(c);

        return new CommentView(
                c.getId(),
                c.getContent(),
                new CommentView.UserRef(user.getId(), user.getDisplayName(),
                        null, user.getCountry()),
                c.getCreatedAt(),
                0L,
                false,
                true,
                c.getParent() == null ? null : c.getParent().getId(),
                List.of());
    }

    /** Yorum sil — sahibi veya admin. Soft-delete (deleted=true). */
    @Transactional
    public void delete(Long commentId, Long userId, Role role) {
        FixtureComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("Yorum bulunamadi."));
        boolean isOwner = c.getUser().getId().equals(userId);
        boolean isAdmin = role == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw ApiException.forbidden("Bu yorumu silme yetkin yok.");
        }
        c.setDeleted(true);
        commentRepository.save(c);
    }

    /** Begeni toggle — varsa kaldir, yoksa ekle. */
    @Transactional
    public ToggleResult toggleLike(Long commentId, Long userId) {
        FixtureComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("Yorum bulunamadi."));
        if (c.isDeleted()) {
            throw ApiException.notFound("Yorum silinmis.");
        }
        boolean exists = likeRepository.existsByCommentIdAndUserId(commentId, userId);
        if (exists) {
            likeRepository.deleteByCommentIdAndUserId(commentId, userId);
            long count = likeRepository.countMap(List.of(commentId))
                    .getOrDefault(commentId, 0L);
            return new ToggleResult(false, count);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanici bulunamadi."));
        FixtureCommentLike like = new FixtureCommentLike();
        like.setComment(c);
        like.setUser(user);
        likeRepository.save(like);
        long count = likeRepository.countMap(List.of(commentId))
                .getOrDefault(commentId, 0L);
        return new ToggleResult(true, count);
    }

    public record ToggleResult(boolean liked, long likeCount) {}
}
