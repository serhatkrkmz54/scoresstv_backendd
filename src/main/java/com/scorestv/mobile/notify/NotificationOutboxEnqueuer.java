package com.scorestv.mobile.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.mobile.notify.NotificationMessageBuilder.Localized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Bildirim OUTBOX'ına idempotent satır yazar. Mesaj (TR+EN title/body + data)
 * ENQUEUE anında render edilip dondurulur; gerçek FCM gönderimini {@link
 * NotificationOutboxWorker} backoff'lu retry ile (alıcıyı locale'e göre ayırarak)
 * yapar.
 *
 * <p><b>İdempotency:</b> {@code dedup_key} aynı bildirim için benzersizdir.
 * <b>Collapse:</b> {@code collapseKey} aynı OS bildirimini yerinde günceller;
 * {@code silent=true} sessiz güncelleme (iki fazlı: isimsiz→isimli).
 */
@Component
public class NotificationOutboxEnqueuer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationOutboxEnqueuer.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationOutboxRepository repository;

    public NotificationOutboxEnqueuer(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    /** Basit gönderim (collapse yok, sesli). */
    @Transactional
    public void enqueue(String kind, String notifType, Long fixtureId, Long teamId,
                        Localized msg, Map<String, String> data, String dedupKey) {
        enqueue(kind, notifType, fixtureId, teamId, msg, data, dedupKey, null, false);
    }

    /**
     * TR+EN mesajı, collapse/silent ile kuyruğa ekler (zaten varsa atlar).
     *
     * @param msg         iki dilli metin (TR+EN)
     * @param collapseKey OS bildirim slotu (Android tag / APNs collapse-id); null olabilir
     * @param silent      true → sessiz güncelleme
     */
    @Transactional
    public void enqueue(String kind, String notifType, Long fixtureId, Long teamId,
                        Localized msg, Map<String, String> data, String dedupKey,
                        String collapseKey, boolean silent) {
        if (repository.existsByDedupKey(dedupKey)) {
            return; // zaten kuyrukta — tekrar ekleme
        }
        NotificationOutbox row = new NotificationOutbox();
        row.setKind(kind);
        row.setNotifType(notifType);
        row.setFixtureId(fixtureId);
        row.setTeamId(teamId);
        row.setTitle(msg.titleTr());
        row.setBody(msg.bodyTr());
        row.setTitleEn(msg.titleEn());
        row.setBodyEn(msg.bodyEn());
        row.setDataJson(toJson(data));
        row.setDedupKey(dedupKey);
        row.setCollapseKey(collapseKey);
        row.setSilent(silent);
        row.setStatus(NotificationOutbox.STATUS_PENDING);
        try {
            repository.save(row);
        } catch (DataIntegrityViolationException dup) {
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
