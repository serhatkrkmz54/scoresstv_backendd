package com.scorestv.comments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface FixtureCommentLikeRepository
        extends JpaRepository<FixtureCommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    long deleteByCommentIdAndUserId(Long commentId, Long userId);

    /**
     * Birden cok yorumun begeni sayisini bir SQL ile cikar — batch.
     * Donen yapı: comment_id -> count.
     */
    @Query("""
            SELECT l.comment.id, COUNT(l) FROM FixtureCommentLike l
            WHERE l.comment.id IN :commentIds
            GROUP BY l.comment.id
            """)
    List<Object[]> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    /** Helper — yukaridaki raw list'i Map'e cevirir. */
    default Map<Long, Long> countMap(List<Long> commentIds) {
        if (commentIds.isEmpty()) return Map.of();
        final Map<Long, Long> result = new java.util.HashMap<>();
        for (Object[] row : countByCommentIds(commentIds)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }
}
