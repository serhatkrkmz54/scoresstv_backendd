package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballTeamSeasonStat;
import com.scorestv.basketball.domain.BasketballTeamSeasonStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Basketbol takimi sezon istatistikleri senkronu —
 * {@code /teams/statistics?team=X&league=Y&season=Z}.
 *
 * <p>Freshness gate: aktif sezonda 6 saat, biten sezonda 7 gun. Aktif sezon
 * tespiti caller'in sorumlulugu — bu servis sadece bilinen freshness penceresi
 * uygulayabilir; daha ayrintili karari LazySync verir.
 */
@Service
public class BasketballTeamStatisticsSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamStatisticsSyncService.class);

    /** Aktif (oynanan) sezonda stat tazeleme penceresi. */
    private static final Duration FRESHNESS_ACTIVE = Duration.ofHours(6);

    private final BasketballApiClient client;
    private final BasketballTeamSeasonStatUpserter upserter;
    private final BasketballTeamSeasonStatRepository statRepo;

    public BasketballTeamStatisticsSyncService(BasketballApiClient client,
                                                 BasketballTeamSeasonStatUpserter upserter,
                                                 BasketballTeamSeasonStatRepository statRepo) {
        this.client = client;
        this.upserter = upserter;
        this.statRepo = statRepo;
    }

    /**
     * Tek takim + lig + sezon icin stats senkronla. Freshness yoksa veya
     * son senkron 6 saatten eski ise API'ye gider; aksi halde mevcut kayit
     * doner.
     */
    @Transactional
    public Optional<BasketballTeamSeasonStat> sync(long teamId, long leagueId,
                                                     String season,
                                                     boolean force) {
        var existing = statRepo
                .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season)
                .orElse(null);

        if (!force && existing != null && existing.getLastSyncedAt() != null) {
            Instant cutoff = Instant.now().minus(FRESHNESS_ACTIVE);
            if (existing.getLastSyncedAt().isAfter(cutoff)) {
                log.debug("Team stats freshness OK team={} league={} season={} syncedAt={}",
                        teamId, leagueId, season, existing.getLastSyncedAt());
                return Optional.of(existing);
            }
        }

        Optional<BkTeamStatisticsDto> resp;
        try {
            resp = client.fetchTeamStatistics(teamId, leagueId, season);
        } catch (Exception e) {
            log.warn("Team statistics fetch hatasi team={} league={} season={}: {}",
                    teamId, leagueId, season, e.toString());
            return Optional.ofNullable(existing);
        }
        if (resp.isEmpty()) {
            log.debug("Team statistics bos yanit team={} league={} season={}",
                    teamId, leagueId, season);
            return Optional.ofNullable(existing);
        }

        BasketballTeamSeasonStat saved = upserter.upsertFromDto(
                teamId, leagueId, season, resp.get());
        return Optional.ofNullable(saved);
    }

    public Optional<BasketballTeamSeasonStat> sync(long teamId, long leagueId, String season) {
        return sync(teamId, leagueId, season, false);
    }
}
