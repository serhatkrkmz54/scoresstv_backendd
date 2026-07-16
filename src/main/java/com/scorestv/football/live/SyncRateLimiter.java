package com.scorestv.football.live;

import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Fixture;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canlı sync joblarının "şimdi bu fixture için bu tipi yoklayabilir miyim?"
 * kararını veren akıllı tıkaç. Üç mekanizma:
 *
 * <ol>
 *   <li><b>Per-fixture per-type rate limit:</b> bir maç + sync tipi için son
 *       sync zamanı tutulur; minimum interval geçmemişse skip.</li>
 *   <li><b>Tier farkı:</b> "Covered" (üst tier) liglerin maçları tam cadence,
 *       diğerleri {@code nonCoveredRateMultiplier} kat daha yavaş. Pik
 *       weekend'de kotayı korur.</li>
 *   <li><b>Halftime skip:</b> Status "HT" (devre arası) iken istatistik ve
 *       oyuncu sync'i atlanır — bu kümede zaten değişim yok. Olaylar (events)
 *       devre arasında devam eder çünkü oyuncu değişikliği olabilir.</li>
 * </ol>
 *
 * <p>Bellekte tutulur ({@code ConcurrentHashMap}); restart sonrası temizdir.
 * Canlı fixture listesi her tick'te {@link #evictStale} ile pasif kayıtları
 * (artık canlı olmayan maçların timestamp'lerini) temizler — sızıntı yok.
 */
@Component
public class SyncRateLimiter {

    /** Hangi sync tipleri için ayrı tıkaç tutuluyor. */
    public enum SyncType {
        EVENTS,
        STATISTICS,
        PLAYER_STATS
    }

    /** Devre arasında atlanan tipler — events HT'de devam (oyuncu değişikliği için). */
    private static final Set<SyncType> SKIP_AT_HALFTIME = Set.of(
            SyncType.STATISTICS, SyncType.PLAYER_STATS);

    private final FootballProperties properties;
    private final Map<SyncType, Map<Long, Instant>> lastSync = new EnumMap<>(SyncType.class);
    /** Gol sonrası EVENTS hızlandırma bitiş anı (fixtureId → until). Boost aktifken
     *  o maçın olay senkronu non-covered çarpanını bypass eder → golcü adı hızlı gelir. */
    private final Map<Long, Instant> eventsBoostUntil = new ConcurrentHashMap<>();

    public SyncRateLimiter(FootballProperties properties) {
        this.properties = properties;
        for (SyncType type : SyncType.values()) {
            lastSync.put(type, new ConcurrentHashMap<>());
        }
    }

    /**
     * Bu fixture için bu tip sync şu anda yapılabilir mi?
     *
     * @return true → çağıran sync yapmalı (ve sonra {@link #markSynced})
     */
    public boolean shouldSync(SyncType type, Fixture fixture) {
        if (SKIP_AT_HALFTIME.contains(type)
                && "HT".equals(fixture.getStatusShort())) {
            return false;
        }
        long intervalSec = effectiveIntervalSeconds(type, fixture);
        Instant last = lastSync.get(type).get(fixture.getId());
        if (last == null) {
            return true;
        }
        return Duration.between(last, Instant.now()).getSeconds() >= intervalSec;
    }

    /**
     * Canlı detay BATCH modu açık mı? Açıkken per-fixture LiveEvents/
     * LiveStatistics/LivePlayerStats joblari ve LiveTickerService'in skor-tetikli
     * per-fixture sync'leri devre disi kalir; yerini tek {@code /fixtures?ids=}
     * batch cagrisi ({@code LiveDetailBatchJob}) alir.
     */
    public boolean isLiveBundleEnabled() {
        return properties.sync().liveBundleEnabled();
    }

    /** Başarılı sync sonrası çağrılır. */
    public void markSynced(SyncType type, Long fixtureId) {
        lastSync.get(type).put(fixtureId, Instant.now());
    }

    /**
     * Gol saptandığında çağrılır — o maçın EVENTS senkronunu belirtilen süre
     * boyunca hızlandırır (non-covered çarpanını bypass eder, covered gibi
     * ~15sn cadence). Golcü adının (faz-2 sessiz güncelleme) 60sn yerine ~15sn'de
     * düşmesini sağlar; süre bitince normale döner (kota korunur).
     */
    public void boostEvents(Long fixtureId, Duration window) {
        if (fixtureId == null || window == null) return;
        eventsBoostUntil.put(fixtureId, Instant.now().plus(window));
    }

    /** Bu maç şu an gol-sonrası EVENTS boost penceresinde mi? */
    private boolean isEventsBoosted(Long fixtureId) {
        Instant until = eventsBoostUntil.get(fixtureId);
        return until != null && Instant.now().isBefore(until);
    }

    /**
     * Artık canlı olmayan maçların kayıtlarını temizler (her tick'in sonunda
     * çağrılması önerilir). Aksi halde harita zamanla büyür.
     */
    public void evictStale(Set<Long> currentLiveFixtureIds) {
        for (Map<Long, Instant> typeMap : lastSync.values()) {
            typeMap.keySet().retainAll(currentLiveFixtureIds);
        }
        eventsBoostUntil.keySet().retainAll(currentLiveFixtureIds);
    }

    /**
     * Bu tip + bu fixture için yürürlükteki sync aralığı (saniye).
     * Covered = baseline; non-covered = baseline × multiplier.
     */
    private long effectiveIntervalSeconds(SyncType type, Fixture fixture) {
        long baseline = baseIntervalSeconds(type);
        // Gol sonrası EVENTS boost: o maç geçici olarak covered gibi (çarpansız)
        // yoklanır → golcü adı ~15sn'de yakalanır, non-covered'da 60sn beklenmez.
        if (type == SyncType.EVENTS && isEventsBoosted(fixture.getId())) {
            return baseline;
        }
        boolean covered = fixture.getLeague() != null && fixture.getLeague().isCovered();
        if (covered) {
            return baseline;
        }
        return baseline * Math.max(1, properties.sync().nonCoveredRateMultiplier());
    }

    /** Covered tier için temel aralık — config'ten okur. */
    private long baseIntervalSeconds(SyncType type) {
        FootballProperties.Sync sync = properties.sync();
        return switch (type) {
            case EVENTS -> 15L;   // baseline: LiveEventsJob @Scheduled aralığıyla (15sn) uyumlu
            case STATISTICS -> sync.liveStatisticsIntervalSeconds();
            case PLAYER_STATS -> sync.livePlayerStatsIntervalSeconds();
        };
    }
}
