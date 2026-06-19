package com.scorestv.mobile.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Bildirim OUTBOX'ına idempotent satır yazar. Mesaj (title/body/data) ENQUEUE
 * anında render edilip dondurulur; gerçek FCM gönderimini {@link
 * NotificationOutboxWorker} backoff'lu retry ile yapar.
 *
 * <p><b>İdempotency:</b> {@code dedup_key} aynı bildirim için benzersizdir;
 * önce {@code existsByDedupKey} ile, ek olarak UNIQUE kısıt + yarış yakalama
 * ile çift satır engellenir.
 */
@Component
public class NotificationOutboxEnqueuer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationOutboxEnqueuer.class);

    /** Basit Map→JSON için yeterli; Spring bean'ine bağımlılık yok. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationOutboxRepository repository;

    public NotificationOutboxEnqueuer(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    /**
     * Bildirimi kuyruğa ekler (zaten varsa atlar).
     *
     * @param kind      KICKOFF|FINAL|GOAL|EVENT
     * @param notifType basladi|bitti|gol|kirmizi|penalti (alıcı tercih tipi)
     * @param fixtureId maç id
     * @param teamId    GOAL/EVENT için ilgili takım; KICKOFF/FINAL için null
     * @param title     render edilmiş başlık
     * @param body      render edilmiş gövde
     * @param data      FCM data payload
     * @param dedupKey  benzersiz idempotency anahtarı
     */
    @Transactional
    public void enqueue(String kind, String notifType, Long fixtureId, Long teamId,
                        String title, String body, Map<String, String> data,
                        String dedupKey) {
        if (repository.existsByDedupKey(dedupKey)) {
            return; // zaten kuyrukta — tekrar ekleme
        }
        NotificationOutbox row = new NotificationOutbox();
        row.setKind(kind);
        row.setNotifType(notifType);
        row.setFixtureId(fixtureId);
        row.setTeamId(teamId);
        row.setTitle(title);
        row.setBody(body);
        row.setDataJson(toJson(data));
        row.setDedupKey(dedupKey);
        row.setStatus(NotificationOutbox.STATUS_PENDING);
        try {
            repository.save(row);
        } catch (DataIntegrityViolationException dup) {
            // Yarış: başka thread aynı dedup_key'i araya yazdı — sorun değil.
            log.debug("Outbox dedup yarışı atlandı: {}", dedupKey);
        }
    }

    private String toJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Outbox data_json serialize hatası: {}", e.getMessage());
            return null;
        }
    }
}
