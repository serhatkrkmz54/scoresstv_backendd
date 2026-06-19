package com.scorestv.mobile.notify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.mobile.fcm.FcmMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Bildirim OUTBOX worker'ı — GARANTİLİ teslim.
 *
 * <p>Periyodik olarak gönderilmeyi bekleyen (PENDING) ve zamanı gelmiş satırları
 * alır, FCM'e gönderir:
 * <ul>
 *   <li>Başarı → {@code SENT}</li>
 *   <li>Sert FCM hatası → {@code attempts++} + üstel backoff ile tekrar dene
 *       ({@value #MAX_ATTEMPTS} denemeden sonra {@code FAILED})</li>
 *   <li>Çok eski (stale) satır → {@code FAILED} (geç/alakasız "başladı/bitti"
 *       göndermemek için)</li>
 * </ul>
 *
 * <p>Canlı tick'ten TAMAMEN ayrı çalışır; bildirim teslimi skor akışını
 * etkilemez. @Scheduled fixedDelay olduğundan kendisiyle çakışmaz (önceki tur
 * bitmeden yenisi başlamaz).
 */
@Component
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    private static final int BATCH = 50;
    private static final int MAX_ATTEMPTS = 6;
    private static final int MAX_ERR_LEN = 500;
    /** Bu kadar eski PENDING satır artık gönderilmez (geç bildirim olmasın). */
    private static final Duration EXPIRE = Duration.ofMinutes(20);

    /** Basit JSON→Map için yeterli; Spring bean'ine bağımlılık yok. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationOutboxRepository repository;
    private final NotificationDispatcherService dispatcher;
    private final FcmMessagingService fcm;

    public NotificationOutboxWorker(NotificationOutboxRepository repository,
                                    NotificationDispatcherService dispatcher,
                                    FcmMessagingService fcm) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.fcm = fcm;
    }

    @Scheduled(fixedDelayString = "${scorestv.notify.outbox-interval-ms:5000}")
    public void process() {
        if (!fcm.isEnabled()) {
            return; // FCM kapali — satirlar PENDING bekler (ya da EXPIRE olur).
        }
        List<NotificationOutbox> due =
                repository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        NotificationOutbox.STATUS_PENDING, Instant.now(),
                        PageRequest.of(0, BATCH));
        if (due.isEmpty()) return;
        for (NotificationOutbox row : due) {
            processOne(row);
        }
    }

    /** Tek satırı işler — her durumda repo'ya kendi kaydını yazar (idempotent). */
    private void processOne(NotificationOutbox row) {
        // Stale → gönderme, FAILED işaretle.
        if (row.getCreatedAt() != null
                && Duration.between(row.getCreatedAt(), Instant.now()).compareTo(EXPIRE) > 0) {
            row.setStatus(NotificationOutbox.STATUS_FAILED);
            row.setLastError("expired (>" + EXPIRE.toMinutes() + "dk bekledi)");
            repository.save(row);
            return;
        }
        try {
            dispatcher.sendOutboxRow(
                    row.getFixtureId(), row.getTeamId(), row.getNotifType(),
                    row.getTitle(), row.getBody(), parseData(row.getDataJson()));
            row.setStatus(NotificationOutbox.STATUS_SENT);
            repository.save(row);
        } catch (Exception ex) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(clip(ex.getMessage()));
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(NotificationOutbox.STATUS_FAILED);
            } else {
                // Üstel backoff: 4, 8, 16, 32, 64 sn ...
                long backoffSec = (long) Math.pow(2, row.getAttempts()) * 2L;
                row.setNextAttemptAt(Instant.now().plusSeconds(backoffSec));
            }
            repository.save(row);
            log.warn("Outbox gönderim hata id={} kind={} fixtureId={} attempt={}/{}: {}",
                    row.getId(), row.getKind(), row.getFixtureId(),
                    row.getAttempts(), MAX_ATTEMPTS, ex.getMessage());
        }
    }

    private Map<String, String> parseData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Outbox data_json parse hatası: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String clip(String s) {
        if (s == null) return null;
        return s.length() > MAX_ERR_LEN ? s.substring(0, MAX_ERR_LEN) : s;
    }
}
