package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;

/** {@link NewsPushLog} — haber push idempotency kaydi. */
public interface NewsPushLogRepository extends JpaRepository<NewsPushLog, Long> {

    /** Bu haber daha once push edildi mi? (idempotency guard). */
    boolean existsByArticleId(Long articleId);
}
