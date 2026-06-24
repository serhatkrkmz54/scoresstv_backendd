package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballTeamSeasonStat;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Voleybol takimi sezon istatistikleri senkronu —
 * {@code /teams/statistics?team=X&league=Y&season=Z}.
 *
 * <p>Freshness gate: 6 saat. Daha ayrintili karari LazySync verir.
 */
@Service
public class VolleyballTeamStatisticsSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballTeamStatisticsSyncService.class);

    private static final Duration FRESHNESS_ACTIVE = Duration.ofHours(6);

    private final VolleyballApiClient client;
    private final VolleyballTeamSeasonStatUpserter upserter;
    private final VolleyballTeamSeasonStatRepository statRepo;

    public VolleyballTeamStatisticsSyncService(VolleyballApiClient client,
                                               VolleyballTeamSeasonStatUpserter upserter,
                                               VolleyballTeamSeasonStatRepository statRepo) {
        this.client = client;
        this.upserter = upserter;
        this.statRepo = statRepo;
    }

    @Transactional
    public Optional<VolleyballTeamSeasonStat> sync(long teamId, long leagueId,
                                                   String season, boolean force) {
        var existing = statRepo
                .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season)
                .orElse(null);

        if (!force && existing != null && existing.getLastSyncedAt() != null) {
            Instant cutoff = Instant.now().minus(FRESHNESS_ACTIVE);
            if (existing.getLastSyncedAt().isAfter(cutoff)) {
                log.debug("Voleybol team stats freshness OK team={} league={} season={}",
                        teamId, leagueId, season);
                return Optional.of(existing);
            }
        }

        Optional<VbTeamStatisticsDto> resp;
        try {
            resp = client.fetchTeamStatistics(teamId, leagueId, season);
        } catch (Exception e) {
            log.warn("Voleybol team statistics fetch hatasi team={} league={} season={}: {}",
                    teamId, leagueId, season, e.toString());
            return Optional.ofNullable(existing);
        }
        if (resp.isEmpty()) {
            log.debug("Voleybol team statistics bos yanit team={} league={} season={}",
                    teamId, leagueId, season);
            return Optional.ofNullable(existing);
        }

        VolleyballTeamSeasonStat saved = upserter.upsertFromDto(
                teamId, leagueId, season, resp.get());
        return Optional.ofNullable(saved);
    }

    public Optional<VolleyballTeamSeasonStat> sync(long teamId, long leagueId, String season) {
        return sync(teamId, leagueId, season, false);
    }
}
