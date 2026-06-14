package com.scorestv.basketball.detail;

import com.scorestv.basketball.BasketballStandingsSyncService;
import com.scorestv.basketball.BasketballSyncService;
import com.scorestv.basketball.BasketballTeamProfileSyncService;
import com.scorestv.basketball.BasketballTeamRosterSyncService;
import com.scorestv.basketball.BasketballTeamStatisticsSyncService;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Basketbol takim detay sayfasi acilisinda devreye giren lazy sync.
 *
 * <p>Paralel CompletableFuture pattern — futboldaki
 * {@code TeamDetailLazySync} ile uyumlu. Tum sync calismalari async
 * fire-and-forget; caller bekler degil, DB-only servis cevap doner.
 *
 * <p>Freshness gate'leri:
 * <ul>
 *   <li><b>Profile</b> — 7 gun (ulke, founded, venue degismez)
 *   <li><b>Statistics</b> — 6 saat (aktif sezonda yarisma surdugu icin)
 *   <li><b>Standings</b> — 1 saat (lig genelinde tabloyu yenile)
 *   <li><b>Games (roster + fikstur kaynagi)</b> — bilfiil takim icin oyuncu
 *       listesi {@code basketball_players} master tablosundan; ayrica oyuncu
 *       profil sync'ini burada tetiklemiyoruz (cok pahali). Yalnizca lig
 *       seviyesinde top-players cagrisinin yan urunu yeterli.
 * </ul>
 */
@Component
public class BasketballTeamDetailLazySync {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamDetailLazySync.class);

    /** Takim profil sabit verileri — gunde 1 kez yeterli. */
    private static final Duration PROFILE_FRESHNESS = Duration.ofDays(7);

    /** Sezon istatistikleri — aktif sezonda 6 saatte bir. */
    private static final Duration STATS_FRESHNESS = Duration.ofHours(6);

    private final BasketballTeamRepository teamRepo;
    private final BasketballTeamProfileSyncService profileSync;
    private final BasketballTeamStatisticsSyncService statsSync;
    private final BasketballStandingsSyncService standingsSync;
    private final BasketballSyncService gamesSync;
    private final BasketballTeamRosterSyncService rosterSync;

    public BasketballTeamDetailLazySync(BasketballTeamRepository teamRepo,
                                          BasketballTeamProfileSyncService profileSync,
                                          BasketballTeamStatisticsSyncService statsSync,
                                          BasketballStandingsSyncService standingsSync,
                                          BasketballSyncService gamesSync,
                                          BasketballTeamRosterSyncService rosterSync) {
        this.teamRepo = teamRepo;
        this.profileSync = profileSync;
        this.statsSync = statsSync;
        this.standingsSync = standingsSync;
        this.gamesSync = gamesSync;
        this.rosterSync = rosterSync;
    }

    /**
     * Takim detayi acilisinda freshness gate'lerine gore async tazeleme tetikler.
     * Hicbir uzun islem icin beklenmez.
     *
     * @param teamId   takim id
     * @param leagueId secili lig (sezon istatistikleri icin)
     * @param season   secili sezon
     */
    public void refreshIfNeeded(Long teamId, Long leagueId, String season) {
        BasketballTeam team = teamRepo.findById(teamId).orElse(null);
        if (team == null) return;

        // 1) Takim profili — 7 gunde bir taze
        if (isStale(team.getLastProfileSyncedAt(), PROFILE_FRESHNESS)) {
            refreshProfileAsync(teamId);
        }

        // 2) Sezon istatistikleri — lig + sezon biliniyorsa
        if (leagueId != null && season != null && !season.isBlank()
                && isStale(team.getLastStatsSyncedAt(), STATS_FRESHNESS)) {
            refreshStatsAsync(teamId, leagueId, season);
        }

        // 3) Standings (lig tablosu) — kullanici Puan Durumu tab'a tiklayinca
        //    burada yine async tazeleme: 1 saat freshness
        if (leagueId != null && season != null && !season.isBlank()) {
            refreshStandingsAsync(leagueId, season);
        }

        // 4) Roster — kadro listesi /players?team=X&season=Y. Tek API cagri,
        //    ucuz; her ziyarette tetiklemek mantikli (player_season_stats'a
        //    minimal satir insert eder, mevcut satirsa atlar).
        if (leagueId != null && season != null && !season.isBlank()) {
            refreshRosterAsync(teamId, leagueId, season);
        }
    }

    /**
     * Force refresh — pull-to-refresh ile cagrilir. Inline call yok, tum sync'ler
     * paralel fire-and-forget. Caller cache evict + DB'den taze sayfa okur.
     */
    public void forceRefresh(Long teamId, Long leagueId, String season) {
        CompletableFuture.allOf(
                refreshProfileAsync(teamId),
                leagueId != null && season != null
                        ? refreshStatsAsync(teamId, leagueId, season)
                        : CompletableFuture.completedFuture(null),
                leagueId != null && season != null
                        ? refreshStandingsAsync(leagueId, season)
                        : CompletableFuture.completedFuture(null),
                leagueId != null && season != null
                        ? refreshRosterAsync(teamId, leagueId, season)
                        : CompletableFuture.completedFuture(null)
        );
    }

    @Async
    public CompletableFuture<Void> refreshProfileAsync(Long teamId) {
        try {
            profileSync.syncProfile(teamId, true);
        } catch (Exception e) {
            log.warn("Basketbol takim profile async sync hata id={}: {}",
                    teamId, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> refreshStatsAsync(Long teamId, Long leagueId,
                                                       String season) {
        try {
            statsSync.sync(teamId, leagueId, season, true);
            // Stats kaydedildiginde de last_stats_synced_at takimda guncellenmedi —
            // istersek burada teamRepo'dan tek tek ele alabiliriz, ancak entity
            // updatedAt zaten guncellenecek. Burada freshness gate icin
            // istatistik tablosunun last_synced_at'ini referans alacagiz.
        } catch (Exception e) {
            log.warn("Basketbol takim stats async sync hata team={} league={} season={}: {}",
                    teamId, leagueId, season, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> refreshStandingsAsync(Long leagueId, String season) {
        try {
            standingsSync.sync(leagueId, season);
        } catch (Exception e) {
            log.warn("Basketbol standings async sync hata league={} season={}: {}",
                    leagueId, season, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> refreshRosterAsync(Long teamId, Long leagueId,
                                                       String season) {
        try {
            rosterSync.sync(teamId, leagueId, season);
        } catch (Exception e) {
            log.warn("Basketbol roster async sync hata team={} league={} season={}: {}",
                    teamId, leagueId, season, e.toString());
        }
        return CompletableFuture.completedFuture(null);
    }

    private boolean isStale(Instant last, Duration freshness) {
        if (last == null) return true;
        return Instant.now().isAfter(last.plus(freshness));
    }
}
