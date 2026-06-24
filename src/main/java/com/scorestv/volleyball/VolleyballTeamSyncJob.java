package com.scorestv.volleyball;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Voleybol takim kadrosu (lig+sezon) periyodik senkron — gunde bir kez tum
 * ligler icin {@code /teams?league=X&season=Y}. Junction tablo tazelenir.
 * Yalnizca {@code scorestv.volleyball.enabled=true} ise bean olusur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.enabled", havingValue = "true")
public class VolleyballTeamSyncJob {

    private final VolleyballTeamSyncService teamSync;

    public VolleyballTeamSyncJob(VolleyballTeamSyncService teamSync) {
        this.teamSync = teamSync;
    }

    @Scheduled(
            cron = "${scorestv.volleyball.team-sync-cron:0 30 4 * * *}",
            zone = "${scorestv.volleyball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "volleyballTeamSync", lockAtMostFor = "PT30M")
    public void run() {
        teamSync.syncAllCurrentSeasons();
    }
}
