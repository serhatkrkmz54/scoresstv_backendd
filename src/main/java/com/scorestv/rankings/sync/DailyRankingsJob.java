package com.scorestv.rankings.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Gunluk ranking tazeleme — 03:00 Europe/Istanbul.
 *
 * <p>FIFA + UEFA Kulup + UEFA Milli — 3 ayri sync. Sirayla cagrilir.
 * Bir tanesi basarisiz olursa digerlerini etkilemez (try/catch).
 *
 * <p>Hedef sezon UEFA icin: futbol sezonu Agustos baslar, takvim yili
 * Haziran sonuna kadar surer. Bu yuzden Temmuz'dan sonra "yeni sezon"
 * hesabi: {@code July+ → year+1}, {@code Jan-June → year}.
 *
 * <p>Bean yalniz {@code scorestv.rankings.sync-enabled=true} (default: true)
 * ile aktif.
 */
@Component
@ConditionalOnProperty(
        name = "scorestv.rankings.sync-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DailyRankingsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyRankingsJob.class);

    private final FifaRankingSyncService fifaSync;
    private final UefaClubRankingSyncService uefaClubSync;
    private final UefaCountryRankingSyncService uefaCountrySync;

    public DailyRankingsJob(FifaRankingSyncService fifaSync,
                             UefaClubRankingSyncService uefaClubSync,
                             UefaCountryRankingSyncService uefaCountrySync) {
        this.fifaSync = fifaSync;
        this.uefaClubSync = uefaClubSync;
        this.uefaCountrySync = uefaCountrySync;
        log.info("DailyRankingsJob aktif — 03:00 her gun FIFA + UEFA tazeleyecek");
    }

    @Scheduled(
            cron = "${scorestv.rankings.daily-cron:0 0 3 * * *}",
            zone = "${scorestv.rankings.timezone:Europe/Istanbul}")
    public void run() {
        log.info("Daily rankings sync basliyor...");
        int fifa = safeRun("FIFA", fifaSync::sync);
        Integer season = currentTargetSeasonYear();
        int uefaClub = safeRun("UEFA Club",
                () -> uefaClubSync.sync(season));
        int uefaCountry = safeRun("UEFA Country",
                () -> uefaCountrySync.sync(season));
        log.info("Daily rankings sync tamamlandi: FIFA={}, UEFA Club={}, UEFA Country={}",
                fifa, uefaClub, uefaCountry);
    }

    /** Hedef sezon — yariminci yil hesabi (Tem+ → year+1, aksi halde year). */
    public static Integer currentTargetSeasonYear() {
        LocalDate today = LocalDate.now();
        return today.getMonthValue() >= 7 ? today.getYear() + 1 : today.getYear();
    }

    /** Tek bir sync cagrisini try/catch ile sarar — biri patlarsa digerleri devam. */
    private static int safeRun(String label, java.util.concurrent.Callable<Integer> call) {
        try {
            return call.call();
        } catch (Exception ex) {
            log.error("{} ranking sync basarisiz: {}", label, ex.getMessage());
            return -1;
        }
    }
}
