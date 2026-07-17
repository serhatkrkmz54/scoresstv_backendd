package com.scorestv.football.live;

import com.scorestv.football.sync.dto.FixtureApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Maç "başladı/bitti" bildirimlerini canlı tick'ten BAĞIMSIZ ve ASENKRON
 * OUTBOX'a yazar (gerçek FCM gönderimini worker yapar).
 *
 * <p><b>Neden ayrı + async?</b> Canlı tick'in asıl işi skoru çekip WebSocket'e
 * yaymak — hız-kritik. Bu sınıf {@code @Async} olduğundan tick onu fire-and-forget
 * çağırıp hemen devam eder; bildirim işi (claim + outbox insert) ayrı thread'de
 * koşar, canlı-skor akışını ASLA sektmez. Gönderim ise {@link
 * com.scorestv.mobile.notify.NotificationOutboxWorker} tarafından backoff'lu
 * retry ile garantili yapılır.
 *
 * <p><b>Doğruluk:</b> Tam-bir-kez {@link FixtureNotifyGate} (atomik claim +
 * outbox enqueue, tek tx) ile sağlanır — async olması bozmaz.
 */
@Component
public class MatchStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(MatchStatusNotifier.class);

    /** Maçın başladığını gösteren canlı statüler. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE");
    /** Bitiş statüleri — "bitti" bildirimi tetikler. */
    private static final Set<String> FINAL_STATUSES = Set.of("FT", "AET", "PEN");

    /**
     * "Başladı" yalnız maçın ilk bu kadar DAKİKASINDA (elapsed — takvim saati
     * değil) tetiklenir. Geç görülen maça "başladı" atılmaz.
     */
    private static final int KICKOFF_MAX_ELAPSED = 5;
    /**
     * "İkinci yarı başladı" yalnız 2H'nin ilk birkaç dakikasında tetiklenir
     * (elapsed 45'ten başlar; 50'ye kadar = ilk ~5 dk). Böylece tüm ikinci yarı
     * boyunca her tick'te boş yere değerlendirme yapılmaz.
     */
    private static final int SECONDHALF_MAX_ELAPSED = 50;
    /** elapsed null geldiğinde (canlıya yeni geçmiş an) duvar-saati fallback'i. */
    private static final Duration KICKOFF_WALL_FALLBACK = Duration.ofMinutes(10);
    /** "Bitti" yalnız kickoff bu kadar zaman öncesinden yeni ise (eski maça atma). */
    private static final Duration FINAL_RECENCY = Duration.ofHours(4);

    private final FixtureNotifyGate notifyGate;

    public MatchStatusNotifier(FixtureNotifyGate notifyGate) {
        this.notifyGate = notifyGate;
    }

    /**
     * Canlı tick'ten fire-and-forget çağrılır. Tüm canlı/stuck item'ları
     * değerlendirir; recency koşulu sağlanırsa {@link FixtureNotifyGate} ile
     * atomik claim + outbox enqueue yapar (tam-bir-kez). {@code @Async} → tick
     * thread'ini BEKLETMEZ.
     *
     * @param items o tick'te işlenen API fixture DTO'ları (plain data — thread'ler
     *              arası güvenli)
     */
    // ÖNEMLİ: notifyExecutor (CallerRunsPolicy) — varsayılan @Async havuzu
    // lazy-sync/bot ile paylaşımlı + DiscardPolicy olduğundan yük altında
    // status-geçiş bildirimlerini SESSİZCE düşürüyordu (goller gelir, başladı/
    // İY/2Y/bitti gelmezdi). notifyExecutor asla düşürmez (gol yoluyla eşit).
    @Async("notifyExecutor")
    public void notifyStatusTransitions(List<FixtureApiDto> items) {
        if (items == null || items.isEmpty()) return;
        final Instant now = Instant.now();
        for (FixtureApiDto item : items) {
            final FixtureApiDto.Fixture f = item.fixture();
            if (f == null || f.id() == null || f.status() == null) continue;
            final String status = f.status().shortCode();
            if (status == null) continue;
            final Long id = f.id();
            try {
                if (FINAL_STATUSES.contains(status)) {
                    // "Bitti" — yalnız yeni biten maçlar (eski/arşiv maçlara atma).
                    final Instant ko = parseKickoff(f.date());
                    final boolean recent = ko == null
                            || Duration.between(ko, now).compareTo(FINAL_RECENCY) <= 0;
                    if (recent) {
                        notifyGate.enqueueFinalIfClaimed(id);
                    }
                } else if ("HT".equals(status)) {
                    // "İlk yarı bitti" (devre arası) — dedup_key tek bildirim sağlar.
                    notifyGate.enqueueHalftime(id);
                } else if ("2H".equals(status)) {
                    // "İkinci yarı başladı" — yalnız 2H'nin ilk birkaç dakikasında.
                    final Integer elapsed = f.status().elapsed();
                    if (elapsed == null || elapsed <= SECONDHALF_MAX_ELAPSED) {
                        notifyGate.enqueueSecondHalf(id);
                    }
                } else if (LIVE_STATUSES.contains(status)) {
                    // "Başladı" — yalnız YENİ başlayanlar (ilk 5 maç dakikası).
                    final Integer elapsed = f.status().elapsed();
                    final boolean recentlyStarted;
                    if (elapsed != null) {
                        recentlyStarted = elapsed <= KICKOFF_MAX_ELAPSED;
                    } else {
                        final Instant ko = parseKickoff(f.date());
                        recentlyStarted = ko == null
                                || Duration.between(ko, now).compareTo(KICKOFF_WALL_FALLBACK) <= 0;
                    }
                    if (recentlyStarted) {
                        notifyGate.enqueueKickoffIfClaimed(id);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Durum bildirim enqueue hata fixtureId={}: {}", id, ex.getMessage());
            }
        }
    }

    private static Instant parseKickoff(String date) {
        if (date == null) return null;
        try {
            return OffsetDateTime.parse(date).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
