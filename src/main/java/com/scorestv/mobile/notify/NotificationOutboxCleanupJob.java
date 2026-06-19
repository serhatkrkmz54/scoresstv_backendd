package com.scorestv.mobile.notify;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * notification_outbox RETENTION — eski satırları temizler ki tablo sınırsız
 * büyümesin (Faz 3: ölçek dayanıklılığı).
 *
 * <ul>
 *   <li>SENT → gönderilmiş, artık değeri yok; kısa tutulur.</li>
 *   <li>FAILED → hata incelemesi için daha uzun tutulur.</li>
 *   <li>PENDING'e dokunulmaz — worker stale olanları zaten FAILED'a çevirir.</li>
 * </ul>
 *
 * <p>Günde bir çalışır; {@code @SchedulerLock} ile çoklu instance'ta yalnız bir
 * node'da koşar. Silme {@code (status, created_at)} index'iyle hızlıdır (V64).
 */
@Component
public class NotificationOutboxCleanupJob {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationOutboxCleanupJob.class);

    /** Gönderilmiş satır retention'ı. */
    private static final Duration SENT_RETENTION = Duration.ofDays(3);
    /** Başarısız satır retention'ı (daha uzun — debug için). */
    private static final Duration FAILED_RETENTION = Duration.ofDays(14);

    private final NotificationOutboxRepository repository;

    public NotificationOutboxCleanupJob(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${scorestv.notify.outbox-cleanup-cron:0 30 4 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "notificationOutboxCleanup", lockAtMostFor = "PT15M")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        int sent = repository.deleteByStatusAndCreatedAtBefore(
                NotificationOutbox.STATUS_SENT, now.minus(SENT_RETENTION));
        int failed = repository.deleteByStatusAndCreatedAtBefore(
                NotificationOutbox.STATUS_FAILED, now.minus(FAILED_RETENTION));
        if (sent > 0 || failed > 0) {
            log.info("Outbox temizlik: {} SENT (>{}g) + {} FAILED (>{}g) silindi",
                    sent, SENT_RETENTION.toDays(), failed, FAILED_RETENTION.toDays());
        }
    }
}
