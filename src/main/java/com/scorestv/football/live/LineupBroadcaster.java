package com.scorestv.football.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Bir maçın kadrosu ilk kez API'ye düştüğü an WebSocket üzerinden
 * "kadrolar açıklandı" bildirimi yapar.
 *
 * <p>Topic: {@code /topic/fixtures/{fixtureId}/lineups}. Mesaj küçük bir
 * bildirim (full kadro verisi değil) — frontend görünce
 * {@code GET /api/v1/fixtures/{slug}} ile detay yanıtını yeniler ve
 * artık dolu olan {@code lineups} alanını render eder.
 */
@Component
public class LineupBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LineupBroadcaster.class);

    private static final String LINEUPS_TOPIC = "/topic/fixtures/%d/lineups";

    private final SimpMessagingTemplate messagingTemplate;

    public LineupBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * "Kadrolar açıklandı" bildirimi — frontend'e refetch tetikleyici küçük
     * payload. Tam kadro verisi WS üzerinden taşınmaz; frontend HTTP'den çeker.
     */
    public void broadcastAnnounced(Long fixtureId) {
        LineupsAnnouncedMessage message = new LineupsAnnouncedMessage(
                "lineups.announced", fixtureId, Instant.now());
        messagingTemplate.convertAndSend(
                String.format(LINEUPS_TOPIC, fixtureId), message);
        log.info("Kadro açıklandı yayını: fixtureId={}", fixtureId);
    }

    /**
     * WebSocket payload — typed record (Map yerine) çünkü Spring'in
     * {@code convertAndSend(String, Map)} overload'u {@code convertAndSend(Object, Map headers)}
     * ile çakışır ("ambiguous reference" derleme hatası). Tipli payload bunu önler.
     *
     * @param type        Mesaj tipi sabit: "lineups.announced"
     * @param fixtureId   Hangi maç için bildirim
     * @param announcedAt Bildirimin atıldığı an (frontend "X dk önce" göstergesi için)
     */
    public record LineupsAnnouncedMessage(
            String type,
            Long fixtureId,
            Instant announcedAt
    ) {
    }
}
