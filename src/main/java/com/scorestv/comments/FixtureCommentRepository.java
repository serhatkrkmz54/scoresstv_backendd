package com.scorestv.comments;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FixtureCommentRepository extends JpaRepository<FixtureComment, Long> {

    /**
     * Bir macin top-level (parent_id IS NULL) yorumlari — en yeni once.
     * Replies ayri query ile alinir.
     */
    @Query("""
            SELECT c FROM FixtureComment c
            JOIN FETCH c.user u
            WHERE c.matchId = :matchId
              AND c.sport = :sport
              AND c.deleted = false
              AND c.parent IS NULL
            ORDER BY c.createdAt DESC
            """)
    Slice<FixtureComment> findTopLevelNewest(
            @Param("matchId") Long matchId,
            @Param("sport") String sport,
            Pageable pageable);

    /**
     * Top-level yorumlari begeni sayisina gore (en cok begenilen once); tie
     * durumunda en yeni once.
     */
    @Query("""
            SELECT c FROM FixtureComment c
            JOIN FETCH c.user u
            LEFT JOIN FixtureCommentLike l ON l.comment.id = c.id
            WHERE c.matchId = :matchId
              AND c.sport = :sport
              AND c.deleted = false
              AND c.parent IS NULL
            GROUP BY c.id, u.id
            ORDER BY COUNT(l) DESC, c.createdAt DESC
            """)
    Slice<FixtureComment> findTopLevelPopular(
            @Param("matchId") Long matchId,
            @Param("sport") String sport,
            Pageable pageable);

    /**
     * Verilen parent id'lerin altindaki tum yanitlari getir — en eski once
     * (klasik thread sirasi, conversational akis).
     */
    @Query("""
            SELECT c FROM FixtureComment c
            JOIN FETCH c.user u
            WHERE c.parent.id IN :parentIds
              AND c.deleted = false
            ORDER BY c.parent.id, c.createdAt ASC
            """)
    List<FixtureComment> findRepliesByParentIds(
            @Param("parentIds") List<Long> parentIds);

    /**
     * Macin toplam aktif yorum sayisi (top-level + replies).
     * Tab badge gostergesi icin.
     */
    long countByMatchIdAndSportAndDeletedFalse(Long matchId, String sport);

    /**
     * Verilen yorum id listesinin her birine kullanicinin begeni atmis olup
     * olmadigini doner — batch (N+1 onlemek icin). Donen liste sadece
     * begenilmis comment id'lerini icerir.
     */
    @Query("""
            SELECT l.comment.id FROM FixtureCommentLike l
            WHERE l.user.id = :userId AND l.comment.id IN :commentIds
            """)
    List<Long> findLikedCommentIds(@Param("userId") Long userId,
                                   @Param("commentIds") List<Long> commentIds);
}
