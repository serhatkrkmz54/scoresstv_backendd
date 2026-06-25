package com.scorestv.basketball.detail;

import com.scorestv.basketball.BasketballLeaguesSyncService;
import com.scorestv.basketball.BasketballStandingsSyncService;
import com.scorestv.basketball.BasketballSyncService;
import com.scorestv.basketball.BasketballTopPlayersSyncService;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballSeason;
import com.scorestv.basketball.domain.BasketballSeasonRepository;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Basketbol lig detay sayfasi acilisinda devreye giren lazy sync.
 *
 * <p>Mantik (futboldaki {@code LeagueDetailLazySync} patternine paralel):
 * <ul>
 *   <li><b>Lig info</b> — {@code lastInfoSyncedAt} 24sa eskiyse asenkron
 *       tazelenir. Sezonlar listesi, ulke, coverage flag'leri update.
 *   <li><b>Standings (current sezon)</b> — yenilik kontrolu
 *       {@code BasketballStandingsSyncService} tarafindan yapilir, oraya
 *       delegate edilir. 1 saat freshness.
 *   <li><b>Top players (current sezon)</b> — {@code lastTopPlayersSyncedAt}
 *       1sa eskiyse async tazelenir. Sayfali /players cagrisi pahali (~20
 *       cagri/lig), kullanici sayfa acinca beklemesin.
 *   <li><b>Fikstur (current sezon)</b> — kullanici "Fikstur" tab'a tiklayinca
 *       on-demand. Initial lazy sync'te yapilmaz.
 *   <li><b>Diger sezonlar</b> — kullanici sezon dropdown'undan secince
 *       tetiklenir (on-demand). Initial sync'te dokunmayiz.
 * </ul>
 *
 * <p>Cagrildigi yer: {@code BasketballLeagueDetailService.getDetail()} en
 * basta. DB-only servis, hicbir senkrondan beklemez (async). Veriler sayfa
 * yukleninceden eskise stale-while-revalidate ile gosterilir, refresh
 * tamamlaninca cache evict + bir sonraki cagri taze veri verir.
 */
@Component
public class BasketballLeagueDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeagueDetailLazySync.class);

    /** Lig info refresh penceresi — gunde bir kere yeterli. */
    private static final Duration LEAGUE_INFO_FRESHNESS = Duration.ofHours(24);

    /** Top players refresh penceresi — saatlik. */
    private static final Duration TOP_PLAYERS_FRESHNESS = Duration.ofHours(1);

    /** Standings refresh penceresi — saatlik (API saatlik tazeler). */
    private static final Duration STANDINGS_FRESHNESS = Duration.ofHours(1);

    private final BasketballLeagueRepository leagueRepo;
    private final BasketballLeaguesSyncService leaguesSyncService;
    private final BasketballTopPlayersSyncService topPlayersSyncService;
    private final BasketballStandingsSyncService standingsSyncService;
    private final BasketballSyncService gamesSyncService;
    private final BasketballStandingRepository standingRepo;
    private final BasketballSeasonRepository seasonRepo;

    public BasketballLeagueDetailLazySync(
            BasketballLeagueRepository leagueRepo,
            BasketballLeaguesSyncService leaguesSyncService,
            BasketballTopPlayersSyncService topPlayersSyncService,
            BasketballStandingsSyncService standingsSyncService,
            BasketballSyncService gamesSyncService,
            BasketballStandingRepository standingRepo,
            BasketballSeasonRepository seasonRepo) {
        this.leagueRepo = leagueRepo;
        this.leaguesSyncService = leaguesSyncService;
        this.topPlayersSyncService = topPlayersSyncService;
        this.standingsSyncService = standingsSyncService;
        this.gamesSyncService = gamesSyncService;
        this.standingRepo = standingRepo;
        this.seasonRepo = seasonRepo;
    }

    /**
     * Lig detay sayfasi acilirken kullanilir. Sync gerekirse async tetikler,
     * hicbir uzun islem icin beklenilmez — caller hemen DB'den okur.
     *
     * @param leagueId lig
     * @param season   secili sezon (caller resolve eder; null gelirse current)
     */
    public void refreshIfNeeded(Long leagueId, String season) {
        BasketballLeague league = leagueRepo.findById(leagueId).orElse(null);
        if (league == null) return;

        // 1) Lig info — sezonlar listesi sayfanin temeli, ozenle tazele
        if (isStale(league.getLastInfoSyncedAt(), LEAGUE_INFO_FRESHNESS)) {
            refreshLeagueInfoAsync(leagueId);
        }

        // Secili sezon yoksa current'a dus
        String effectiveSeason = season != null && !season.isBlank()
                ? season
                : league.getCurrentSeason();
        if (effectiveSeason == null || effectiveSeason.isBlank()) return;

        // 2) Standings — sayfanin ana modulu. Bos veya 1sa eskimisse async
        //    tetikle (her sayfa acilisinda API'yi yormamak icin gate'li).
        if (isStandingsStale(leagueId, effectiveSeason)) {
            refreshStandingsAsync(leagueId, effectiveSeason);
        }

        // 3) Top players — saatlik freshness, current sezon icin
        if (effectiveSeason.equals(league.getCurrentSeason())
                && isStale(league.getLastTopPlayersSyncedAt(), TOP_PLAYERS_FRESHNESS)) {
            refreshTopPlayersAsync(leagueId, effectiveSeason);
        }
    }

    /**
     * Force refresh — pull-to-refresh / admin tetikleme.
     * Inline cagri yok, tum sync'ler async fire-and-forget.
     */
    public void forceRefresh(Long leagueId, String season) {
        refreshLeagueInfoAsync(leagueId);
        if (season != null && !season.isBlank()) {
            refreshStandingsAsync(leagueId, season);
            refreshTopPlayersAsync(leagueId, season);
        }
    }

    @Async
    public CompletableFuture<Void> refreshLeagueInfoAsync(Long leagueId) {
        try {
            leaguesSyncService.syncLeagueInfo(leagueId);
        } catch (Exception e) {
            log.warn("Basketbol lig info async sync hata id={}: {}", leagueId, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> refreshStandingsAsync(Long leagueId, String season) {
        try {
            standingsSyncService.sync(leagueId, season);
        } catch (Exception e) {
            log.warn("Basketbol standings async sync hata id={} season={}: {}",
                    leagueId, season, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> refreshTopPlayersAsync(Long leagueId, String season) {
        try {
            topPlayersSyncService.syncLeagueSeason(leagueId, season);
        } catch (Exception e) {
            log.warn("Basketbol top players async sync hata id={} season={}: {}",
                    leagueId, season, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Standings tazeleme gerekiyor mu? {@code BasketballStandingsPageService
     * .ensureFresh} ile ayni gate: hic satir yoksa ya da son sync 1sa'ten
     * eskiyse true. Boylece her sayfa acilisinda API'yi yormayiz.
     */
    private boolean isStandingsStale(Long leagueId, String season) {
        long count = standingRepo.countByLeagueIdAndSeason(leagueId, season);
        Instant lastSync = seasonRepo.findByLeagueIdAndSeason(leagueId, season)
                .map(BasketballSeason::getStandingsLastSyncedAt)
                .orElse(null);
        boolean stale = lastSync == null
                || Instant.now().isAfter(lastSync.plus(STANDINGS_FRESHNESS));
        return count == 0 || stale;
    }

    private static boolean isStale(Instant lastSyncedAt, Duration freshness) {
        if (lastSyncedAt == null) return true;
        return lastSyncedAt.isBefore(Instant.now().minus(freshness));
    }
}
