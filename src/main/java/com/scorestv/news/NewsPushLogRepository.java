package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** {@link NewsPushLog} — haber push outbox + idempotency kaydi. */
public interface NewsPushLogRepository extends JpaRepository<NewsPushLog, Long> {

    /** Bu haber daha once push edildi mi? (idempotency guard). */
    boolean existsByArticleId(Long articleId);

    Optional<NewsPushLog> findByArticleId(Long articleId);

    /**
     * Outbox'a ATOMIK ekle — yayinlama transaction'inin PARCASI olarak calisir
     * (rollback'te satir da gider, yanlis bildirim kalmaz). UNIQUE(article_id)
     * ile ikinci tetik (cift tiklama, tekrar yayinla) sessizce yutulur;
     * ON CONFLICT sayesinde transaction da zehirlenmez.
     *
     * @return 1 = eklendi, 0 = zaten kayitli (pending veya gonderilmis)
     */
    @Modifying
    @Query(value = """
            INSERT INTO news_push_log
                (article_id, target, recipient_count, status, attempts, next_attempt_at, sent_at)
            VALUES (:articleId, :target, 0, 'PENDING', 0, now(), now())
            ON CONFLICT (article_id) DO NOTHING
            """, nativeQuery = true)
    int enqueue(@Param("articleId") Long articleId, @Param("target") String target);

    /**
     * Gonderim icin ATOMIK claim + lease: yalniz vakti gelmis PENDING (veya
     * lease'i dolmus SENDING — crash kurtarma) satiri kapilir; deneme sayaci
     * artar, lease suresince baska worker/thread alamaz.
     *
     * @return 1 = kapildi (gonderim bu cagriranin), 0 = baskasinin/sirasi degil
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NewsPushLog l
               SET l.status = 'SENDING', l.attempts = l.attempts + 1, l.nextAttemptAt = :lease
             WHERE l.id = :id
               AND l.status IN ('PENDING', 'SENDING')
               AND l.nextAttemptAt <= :now
            """)
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("lease") Instant lease);

    /** Vakti gelmis outbox satirlari (PENDING + lease'i dolmus SENDING). */
    List<NewsPushLog> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(
            Collection<String> statuses, Instant now);
}
