package com.scorestv.basketball;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Basketbol takım kadrosu (lig+sezon) periyodik senkron — günde bir kez tüm
 * covered ligler için API-Basketball {@code /teams?league=X&season=Y} çağrısı.
 * Junction tablo ({@code basketball_team_league_seasons}) tazelenir.
 *
 * <p>Debounce: {@code BasketballTeamSyncService.syncAllCurrentSeasons} son 12
 * saat içinde sync edilmiş ligi atlar — gereksiz API çağrısı yapmaz.
 *
 * <p>Yalnızca {@code scorestv.basketball.enabled=true} ise bean oluşur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballTeamSyncJob {

    private final BasketballTeamSyncService teamSync;

    public BasketballTeamSyncJob(BasketballTeamSyncService teamSync) {
        this.teamSync = teamSync;
    }

    /**
     * Default: her gün saat 04:30 — reference seed (03:00) bittikten sonra.
     * Override: {@code scorestv.basketball.team-sync-cron}.
     */
    @Scheduled(
            cron = "${scorestv.basketball.team-sync-cron:0 30 4 * * *}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    public void run() {
        teamSync.syncAllCurrentSeasons();
    }
}
