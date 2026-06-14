package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamLeagueSeasonRepository;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Covered basketbol takimlar icin gunluk inline refresh — profil + sezon
 * istatistikleri. Lazy sync sadece kullanici takim sayfasini actiginda
 * tetiklenir; bu job nadir ziyaret edilen takimlari da sicak tutar.
 *
 * <p><b>Akis:</b>
 * <ol>
 *   <li>Takim profil senkronu ({@code /teams?id=X}) — country, founded, venue.
 *   <li>Takim son sezon kombinasyonu icin sezon istatistikleri sync'i
 *       ({@code /teams/statistics?team=X&league=Y&season=Z}).
 *   <li>Detay sayfasi cache evict (TR + EN) — bir sonraki cagri taze veri.
 * </ol>
 *
 * <p><b>Kapsam:</b> Yalniz {@code team.covered = true} olan basketbol
 * takimlar. {@code BasketballTeamDetailService} ilk ziyarette
 * {@code auto-cover on visit} ile bayragi acar.
 *
 * <p><b>Kota:</b> Takim basina ~2 cagri (1 profile + 1 statistics).
 * ~100 covered takim × 2 = 200 cagri/gun (kotanin %0.3'u).
 *
 * <p>Cron varsayilan {@code 0 45 6 * * *} (sabah 06:45 — lig job'undan sonra).
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class DailyBasketballTeamRefreshJob {

    private static final Logger log =
            LoggerFactory.getLogger(DailyBasketballTeamRefreshJob.class);

    private final BasketballTeamRepository teamRepo;
    private final BasketballTeamLeagueSeasonRepository junctionRepo;
    private final BasketballTeamProfileSyncService profileSync;
    private final BasketballTeamStatisticsSyncService statsSync;
    private final CacheManager cacheManager;

    public DailyBasketballTeamRefreshJob(
            BasketballTeamRepository teamRepo,
            BasketballTeamLeagueSeasonRepository junctionRepo,
            BasketballTeamProfileSyncService profileSync,
            BasketballTeamStatisticsSyncService statsSync,
            CacheManager cacheManager) {
        this.teamRepo = teamRepo;
        this.junctionRepo = junctionRepo;
        this.profileSync = profileSync;
        this.statsSync = statsSync;
        this.cacheManager = cacheManager;
    }

    @Scheduled(
            cron = "${scorestv.basketball.team-refresh-cron:0 45 6 * * *}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    public void run() {
        List<BasketballTeam> covered = teamRepo.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.info("DailyBasketballTeamRefreshJob: covered takim yok, atlandi.");
            return;
        }

        int processed = 0;
        int profileFailed = 0;
        int statsRefreshed = 0;
        int cacheEvicted = 0;

        for (BasketballTeam team : covered) {
            // 1) Profile refresh — force=true (job gunluk sicaklik garantor)
            try {
                profileSync.syncProfile(team.getId(), true);
                processed++;
            } catch (Exception ex) {
                profileFailed++;
                log.warn("Basketbol takim profile refresh hata: teamId={} — {}",
                        team.getId(), ex.getMessage());
                continue;
            }

            // 2) En yeni (lig, sezon) ciftini junction'dan al, statistics tazele
            List<Object[]> pairs = junctionRepo.findLeaguesForTeam(team.getId());
            if (pairs.isEmpty()) continue;
            // Junction siralanmamis — basit yaklasim: ilk eslesmeyi sec
            // (gercek production'da ek olarak findLastSyncedAt'a gore secilebilir).
            Object[] arr = pairs.get(0);
            if (arr.length < 2 || arr[0] == null || arr[1] == null) continue;
            Long leagueId = ((Number) arr[0]).longValue();
            String season = arr[1].toString();

            try {
                statsSync.sync(team.getId(), leagueId, season, true);
                statsRefreshed++;
            } catch (Exception ex) {
                log.warn("Basketbol takim stats refresh hata: teamId={} league={} season={} — {}",
                        team.getId(), leagueId, season, ex.getMessage());
            }

            // 3) Detay cache evict (TR + EN)
            if (evictDetailCache(team, leagueId, season)) cacheEvicted++;
        }

        log.info("DailyBasketballTeamRefreshJob bitti: {} takim islendi, "
                        + "{} profile hatasi, {} stats refresh, {} cache evict.",
                processed, profileFailed, statsRefreshed, cacheEvicted);
    }

    /** Detay sayfasi cache evict — TR + EN varyantlari. */
    private boolean evictDetailCache(BasketballTeam team, Long leagueId, String season) {
        var cache = cacheManager.getCache("BASKETBALL_TEAM_DETAIL");
        if (cache == null) return false;
        cache.evict(java.util.Objects.hash(team.getId(), leagueId, season, true));
        cache.evict(java.util.Objects.hash(team.getId(), leagueId, season, false));
        return true;
    }
}
