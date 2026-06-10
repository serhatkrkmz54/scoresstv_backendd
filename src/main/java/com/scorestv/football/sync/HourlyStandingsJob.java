package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kapsamlı (covered) liglerin geçerli sezon puan durumunu saatlik tazeleyen iş.
 *
 * <p>API-Football'un {@code /standings} ucu saatte bir güncellenir; bu job
 * birebir uyumludur. Cron varsayılan {@code 0 5 * * * *} (her saatin 5.
 * dakikası — diğer saatlik joblarla yığılma yapmasın).
 *
 * <p>Quota: ~30 covered lig × 24 saat = 720/gün. Trivial.
 * Bean yalnız {@code scorestv.football.sync.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class HourlyStandingsJob {

    private static final Logger log = LoggerFactory.getLogger(HourlyStandingsJob.class);

    private final LeagueRepository leagueRepository;
    private final StandingsSyncService standingsSyncService;

    public HourlyStandingsJob(LeagueRepository leagueRepository,
                              StandingsSyncService standingsSyncService) {
        this.leagueRepository = leagueRepository;
        this.standingsSyncService = standingsSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.standings-cron:0 5 * * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void run() {
        List<League> covered = leagueRepository.findByCoveredTrue();
        if (covered.isEmpty()) {
            return;
        }
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        for (League league : covered) {
            if (league.getCurrentSeason() == null) {
                skipped++;
                continue;
            }
            try {
                standingsSyncService.sync(league.getId(), league.getCurrentSeason());
                succeeded++;
            } catch (ApiException ex) {
                failed++;
                log.warn("Puan durumu sync başarısız (API): leagueId={} season={} — {}",
                        league.getId(), league.getCurrentSeason(), ex.getMessage());
            } catch (RuntimeException ex) {
                failed++;
                log.error("Puan durumu sync beklenmedik hata: leagueId="
                        + league.getId(), ex);
            }
        }
        log.info("Saatlik puan durumu: {} lig işlendi, {} sezon eksik, {} başarısız.",
                succeeded, skipped, failed);
    }
}
