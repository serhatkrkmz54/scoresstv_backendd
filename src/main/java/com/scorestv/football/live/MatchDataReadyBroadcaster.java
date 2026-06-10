package com.scorestv.football.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Module-agnostic "data-ready" broadcaster.
 *
 * <p>Bir maç için herhangi bir yan modül (events, lineups, stats, playerStats,
 * h2h, standings, predictions, injuries) lazy-sync ile yeni veri yazıldığında
 * WebSocket üzerinden push gönderir. Mobile/web client'lar tek topic'i
 * dinleyip eksik tab'larını otomatik tazeler — polling gerekmez.
 *
 * <h3>Topic</h3>
 * <pre>/topic/fixtures/{fixtureId}/ready</pre>
 *
 * <h3>Payload</h3>
 * <pre>{ "fixtureId": 1234, "module": "stats", "at": "2026-06-09T..." }</pre>
 *
 * <h3>Tasarım</h3>
 * <ul>
 *   <li><b>Generic:</b> Yeni modüller eklenince broadcaster değişmez —
 *       sadece {@link #publish(Long, String)} çağrısı eklenir.</li>
 *   <li><b>Dil-agnostic:</b> Sinyal niteliği; payload sadece "veri var" der.
 *       Client uygun /api/v1/fixtures/{slug} endpoint'inden lokalize fresh
 *       veri çeker. Topic'in TR/EN ayrımı yok — gereksiz duplicate kaynak.</li>
 *   <li><b>Best-effort:</b> Broadcast hatası iş akışını bloklamaz. Mobile WS
 *       kopuksa zaten reconnect sonrası periyodik safety-net poll bunu
 *       kurtarır.</li>
 *   <li><b>Idempotent:</b> Aynı modül için 2 kez gönderilmesi zararsız —
 *       client her sefer aynı silent refetch'i yapar, state idempotent.</li>
 * </ul>
 *
 * <h3>Modül adları (sözleşme)</h3>
 * Backend ile mobile arasında string sözleşmesi. Yeni eklemeleri her iki
 * tarafa beraber doküman edin:
 * <pre>
 *   events       — fixture events (gol/kart/değişiklik)
 *   lineups      — kadrolar açıklandı/değişti
 *   stats        — match statistics (possession, shots, ...)
 *   playerStats  — bireysel oyuncu maç istatistikleri
 *   h2h          — head-to-head geçmiş maçlar
 *   standings    — puan durumu (lig+sezon bazlı, fixtureId'nin liginin)
 *   predictions  — bahis tahminleri
 *   injuries     — sakatlık/cezalı listesi
 * </pre>
 */
@Component
public class MatchDataReadyBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MatchDataReadyBroadcaster.class);

    private static final String TOPIC_FMT = "/topic/fixtures/%d/ready";

    private final SimpMessagingTemplate messagingTemplate;

    public MatchDataReadyBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Bir modülün lazy-sync ile DB'ye yazıldığını client'lara bildir.
     *
     * <p>Mobile {@code MatchDetailController} bu sinyali aldığında ilgili
     * fixtureId için silent refetch yapar — UI otomatik güncellenir.
     *
     * @param fixtureId etkilenen maç id (null ise no-op)
     * @param module    {@link MatchDataReadyBroadcaster sınıf doc'undaki}
     *                  modül adlarından biri
     */
    public void publish(Long fixtureId, String module) {
        if (fixtureId == null || module == null || module.isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "fixtureId", fixtureId,
                    "module", module,
                    "at", Instant.now().toString()
            );
            // (Object) cast — SimpMessagingTemplate'in convertAndSend(String, Object)
            // ile convertAndSend(Object, Map<String,Object> headers) overload'lari
            // Map<String,Object> argumani icin belirsiz; cast ile payload'a yorumlat.
            messagingTemplate.convertAndSend(
                    String.format(TOPIC_FMT, fixtureId), (Object) payload);
            log.debug("MatchDataReady push: fixtureId={} module={}", fixtureId, module);
        } catch (RuntimeException ex) {
            // Broadcast hatası iş akışını bloklamasın — bir sonraki sync'te
            // yine push atılır veya client polling fallback'i yakalar.
            log.warn("MatchDataReady broadcast hatasi (fixtureId={} module={}): {}",
                    fixtureId, module, ex.getMessage());
        }
    }
}
