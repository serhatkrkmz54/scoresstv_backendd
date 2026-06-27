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

    /** Başarılı sync sonrası çağrılır. */
    public void markSynced(SyncType type, Long fixtureId) {
        lastSync.get(type).put(fixtureId, Instant.now());
    }

    /**
     * Artık canlı olmayan maçların kayıtlarını temizler (her tick'in sonunda
     * çağrılması önerilir). Aksi halde harita zamanla büyür.
     */
    public void evictStale(Set<Long> currentLiveFixtureIds) {
        for (Map<Long, Instant> typeMap : lastSync.values()) {
            typeMap.keySet().retainAll(currentLiveFixtureIds);
        }
    }

    /**
     * Bu tip + bu fixture için yürürlükteki sync aralığı (saniye).
     * Covered = baseline; non-covered = baseline × multiplier.
     */
    private long effectiveIntervalSeconds(SyncType type, Fixture fixture) {
        long baseline = baseIntervalSeconds(type);
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
