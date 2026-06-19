package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Covered basketbol ligler icin gunluk inline refresh — futboldaki
 * {@code DailyLeagueRefreshJob} patternine paralel.
 *
 * <p><b>Neden gerekli:</b> Lazy sync sadece kullanici lig sayfasini actiginda
 * tetiklenir. Popüler olmayan covered ligler icin (orn. Avrupa alt-ligler)
 * kullanici nadir gelir; sayfa acildiginda taze veri gosterebilmek icin
 * background'da gunluk tazeleme.
 *
 * <p><b>Akis:</b>
 * <ol>
 *   <li>{@code /leagues?id=X} — lig info + sezonlar + coverage tazele.
 *   <li>Current sezon icin top players + master tablo + sezon stat
 *       (3 kategori atom replace, sayfali /players cagrisi).
 *   <li>Detay sayfasi Redis cache evict (TR + EN varyantlari) — bir sonraki
 *       kullanici cagrisi taze veriyi alir.
 * </ol>
 *
 * <p><b>Kapsam:</b> Yalniz {@code league.covered = true} olan basketbol
 * ligler. Bayrak admin tarafindan secilir; ~10-15 lig icin gecerli.
 *
 * <p><b>Kota:</b> Lig basina ~20 cagri (1 info + ~19 sayfali /players).
 * 15 lig × 20 = 300 cagri/gun (kotanin %0.4'u — trivial).
 *
 * <p>Cron varsayilan {@code 0 30 6 * * *} (sabah 06:30 — Avrupa lig gece
 * maclari oturmus halde, NBA gunluk maclari da sabah saatinde tamamlanmis).
 * Bean yalniz {@code scorestv.basketball.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class DailyBasketballLeagueRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(DailyBasketballLeagueRefreshJob.class);

    private final BasketballLeagueRepository leagueRepo;
    private final BasketballLeaguesSyncService leaguesSyncService;
    private final BasketballTopPlayersSyncService topPlayersSyncService;
    private final CacheManager cacheManager;

    public DailyBasketballLeagueRefreshJob(
            BasketballLeagueRepository leagueRepo,
            BasketballLeaguesSyncService leaguesSyncService,
            BasketballTopPlayersSyncService topPlayersSyncService,
            CacheManager cacheManager) {
        this.leagueRepo = leagueRepo;
        this.leaguesSyncService = leaguesSyncService;
        this.topPlayersSyncService = topPlayersSyncService;
        this.cacheManager = cacheManager;
    }

    @Scheduled(
            cron = "${scorestv.basketball.league-refresh-cron:0 30 6 * * *}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "dailyBasketballLeagueRefresh", lockAtMostFor = "PT30M")
    public void run() {
        List<BasketballLeague> covered = leagueRepo.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.info("DailyBasketballLeagueRefreshJob: covered lig yok, atlandi.");
            return;
        }

        int processed = 0;
        int infoFailed = 0;
        int topPlayersWritten = 0;
        int topPlayersFailed = 0;
        int cacheEvicted = 0;

        for (BasketballLeague league : covered) {
            // 1) Lig info tazele
            try {
                if (leaguesSyncService.syncLeagueInfo(league.getId()) == null) {
                    infoFailed++;
                    continue;  // info olmadan top players sync etmenin anlami yok
                }
            } catch (RuntimeException ex) {
                infoFailed++;
                log.warn("Basketbol lig info refresh hata: leagueId={} — {}",
                        league.getId(), ex.getMessage());
                continue;
            }

            // 2) Current sezon top players sync (3 kategori birlikte)
            String season = league.getCurrentSeason();
            if (season == null || season.isBlank()) continue;

            try {
                int written = topPlayersSyncService.syncLeagueSeason(
                        league.getId(), season);
                topPlayersWritten += written;
                processed++;
            } catch (RuntimeException ex) {
                topPlayersFailed++;
                log.warn("Basketbol top players refresh hata: leagueId={} season={} — {}",
                        league.getId(), season, ex.getMessage());
            }

            // 3) Detay sayfasi cache evict (TR + EN)
            if (evictDetailCache(league, season)) cacheEvicted++;
        }

        log.info("DailyBasketballLeagueRefreshJob bitti: {} lig islendi, "
                        + "{} info hatasi, {} oyuncu islendi (toplam), "
                        + "{} top-player hatasi, {} cache evict.",
                processed, infoFailed, topPlayersWritten, topPlayersFailed, cacheEvicted);
    }

    /**
     * Lig detay sayfasi cache'inin TR + EN + current sezon varyantlarini evict
     * eder. Bir sonraki kullanici cagrisi cache miss ile taze veriyi getirir.
     */
    private boolean evictDetailCache(BasketballLeague league, String season) {
        var cache = cacheManager.getCache("basketballLeagueDetail");
        if (cache == null) return false;
        String slug = league.getSlug();
        if (slug == null || slug.isBlank()) return false;
        // Hem secili sezon hem "current" placeholder anahtarlari
        cache.evict(slug + ":" + season + ":tr");
        cache.evict(slug + ":" + season + ":en");
        cache.evict(slug + ":current:tr");
        cache.evict(slug + ":current:en");
        return true;
    }
}
