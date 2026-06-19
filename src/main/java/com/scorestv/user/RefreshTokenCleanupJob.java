package com.scorestv.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Suresi dolmus refresh token'lari periyodik olarak siler; boylece
 * refresh_tokens tablosu sinirsiz buyumez.
 *
 * Not: iptal edilmis (revoked) ama suresi dolmamis token'lar SILINMEZ -
 * reuse detection bunlara ihtiyac duyar (calinan bir token tekrar gelirse
 * yakalanabilsin diye). Sureleri dolunca bu temizlikte silinirler.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** Her gun saat 03:00'te suresi dolmus refresh token'lari siler. */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "PT15M")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Temizlik: {} adet suresi dolmus refresh token silindi.", deleted);
        }
    }
}
