package com.scorestv.mobile.broadcast;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.scorestv.mobile.fcm.FcmMessagingService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Genel bildirim (broadcast) worker'i — GARANTILI teslim.
 *
 * <p>Periyodik olarak QUEUED + zamani gelmis satirlari alir ve FCM'e gonderir:
 * <ul>
 *   <li>Basari (en az bir cihaza iletildi) → {@code SENT}</li>
 *   <li>Gecici toplam basarisizlik → {@code attempts++} + ustel backoff; {@value
 *       #MAX_ATTEMPTS} denemeden sonra {@code FAILED}</li>
 *   <li>Cok eski (stale) satir → {@code FAILED}</li>
 * </ul>
 *
 * <p>@SchedulerLock ile cok-instance'ta yalniz bir kopya calisir; fixedDelay
 * oldugundan kendisiyle cakismaz. Outbox worker'i ile ayni desen.
 */
@Component
public class BroadcastNotificationWorker {

    private static final Logger log =
            LoggerFactory.getLogger(BroadcastNotificationWorker.class);

    private static final int BATCH = 20;
    private static final int MAX_ATTEMPTS = 6;
    private static final int MAX_ERR_LEN = 500;
    /** Bu kadar eski QUEUED satir artik gonderilmez (FAILED). */
    private static final Duration EXPIRE = Duration.ofHours(2);

    private final BroadcastNotificationRepository repository;
    private final BroadcastNotificationService service;
    private final FcmMessagingService fcm;

    public BroadcastNotificationWorker(BroadcastNotificationRepository repository,
                                       BroadcastNotificationService service,
                                       FcmMessagingService fcm) {
        this.repository = repository;
        this.service = service;
        this.fcm = fcm;
    }

    @Scheduled(fixedDelayString = "${scorestv.notify.broadcast-interval-ms:5000}")
    @SchedulerLock(name = "broadcastNotificationWorker", lockAtMostFor = "PT2M")
    public void process() {
        if (!fcm.isEnabled()) {
            return; // FCM kapali — satirlar QUEUED bekler (ya da EXPIRE olur).
        }
        List<BroadcastNotification> due =
                repository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        BroadcastNotification.STATUS_QUEUED, Instant.now(),
                        PageRequest.of(0, BATCH));
        for (BroadcastNotification row : due) {
            processOne(row);
        }
    }

    private void processOne(BroadcastNotification row) {
        // Stale → gonderme, FAILED isaretle.
        if (row.getCreatedAt() != null
                && Duration.between(row.getCreatedAt(), Instant.now()).compareTo(EXPIRE) > 0) {
            row.setStatus(BroadcastNotification.STATUS_FAILED);
            row.setLastError("expired (>" + EXPIRE.toHours() + "sa bekledi)");
            repository.save(row);
            return;
        }
        try {
            service.deliver(row);
            row.setStatus(BroadcastNotification.STATUS_SENT);
            row.setLastError(null);
            repository.save(row);
            log.info("Broadcast gonderildi id={} sent={}/{}",
                    row.getId(), row.getSentCount(), row.getRecipientCount());
        } catch (Exception ex) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(clip(ex.getMessage()));
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(BroadcastNotification.STATUS_FAILED);
            } else {
                // Ustel backoff: 4, 8, 16, 32, 64 sn ...
                long backoffSec = (long) Math.pow(2, row.getAttempts()) * 2L;
                row.setNextAttemptAt(Instant.now().plusSeconds(backoffSec));
            }
            repository.save(row);
            log.warn("Broadcast gonderim hata id={} attempt={}/{}: {}",
                    row.getId(), row.getAttempts(), MAX_ATTEMPTS, ex.getMessage());
        }
    }

    private static String clip(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > MAX_ERR_LEN ? s.substring(0, MAX_ERR_LEN) : s;
    }
}
