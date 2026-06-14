package com.scorestv.football.queue;

import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.TeamLeagueSeasonRepository;
import com.scorestv.football.domain.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Periyodik otomatik bulk enqueue — admin manuel tetiklemesin diye.
 *
 * <p>Mantik: Worker hep is olmasi icin kuyruga is dokum. Worker ekonomik
 * isler (2sn/is, 30/dk, ~43k/gun). Buradaki cron job'lar SADECE kuyruga
 * EKLER, isi worker yapar. enqueueIfAbsent ile mevcut PENDING'ler tekrar
 * eklenmez — dup korumasi.
 *
 * <p>Plan:
 * <ul>
 *   <li><b>Pazar 02:00:</b> TUM takimlar icin squad sync (5000 is, ~3 saat)</li>
 *   <li><b>Her gun 03:30:</b> Covered takimlar icin transfers sync (~100 is)</li>
 *   <li><b>Her gun 04:00:</b> Covered ligler icin standings sync (~30 is)</li>
 *   <li><b>Her gun 04:30:</b> Covered ligler icin top players (~30 is x 4 kategori)</li>
 * </ul>
 *
 * <p>Quota: Pazar gecesi piklerinde ~5500 cagri (squad bulk) + diger gunler
 * ~1000 cagri (transfers + standings + top players). 75k limit icinde rahat.
 *
 * <p>Worker kota tukenirse otomatik yavaslar (bulk job'lar PENDING'de kalir,
 * ertesi gun devam eder).
 *
 * <p>Bean yalniz {@code scorestv.football.sync.auto-enqueue-enabled=true}
 * ile aktif. Devre disi birakilirsa admin manuel ile devam edilir.
 */
@Component
@ConditionalOnProperty(
        name = "scorestv.football.sync.auto-enqueue-enabled",
        havingValue = "true",
        matchIfMissing = true)  // Varsayilan: aktif
public class AutoEnqueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoEnqueueScheduler.class);

    private final SyncQueueService queueService;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final TeamLeagueSeasonRepository membershipRepository;

    public AutoEnqueueScheduler(SyncQueueService queueService,
                                TeamRepository teamRepository,
                                LeagueRepository leagueRepository,
                                PlayerRepository playerRepository,
                                TeamLeagueSeasonRepository membershipRepository) {
        this.queueService = queueService;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.playerRepository = playerRepository;
        this.membershipRepository = membershipRepository;
        log.info("AutoEnqueueScheduler aktif — periyodik bulk enqueue calisacak");
    }

    /**
     * Haftada bir Pazar 02:00 — TUM takimlarin squad sync'i.
     * 5000 takim → 5000 PENDING job → Worker 2sn/is ile ~3 saat surer.
     * Pazar gecesi dusuk trafik + Pazartesi sabahi yeni transferleri yakalar.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-weekly-squads-cron:0 0 2 * * SUN}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void weeklyAllTeamSquads() {
        Integer season = LocalDate.now().getYear();
        int added = 0;
        long teamCount = teamRepository.count();
        log.info("Weekly auto-enqueue: tum takimlarin squad sync'i basliyor (toplam {} takim)",
                teamCount);
        for (Long teamId : teamRepository.findAll().stream().map(t -> t.getId()).toList()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("teamId", teamId);
            payload.put("season", season);
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_SQUAD_SYNC,
                    payload, SyncQueueService.PRIORITY_BULK)) {
                added++;
            }
        }
        log.info("Weekly auto-enqueue tamamlandi: {} yeni squad job kuyruga eklendi", added);
    }

    /**
     * Her gun 03:30 — covered takimlar icin transfers sync. Yeni transferleri
     * sabaha kadar yakalar (Avrupa pazari, transfer pencerelerinde).
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-daily-transfers-cron:0 30 3 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void dailyCoveredTransfers() {
        int added = 0;
        for (Long teamId : teamRepository.findAll().stream()
                .filter(t -> t.isCovered())
                .map(t -> t.getId())
                .toList()) {
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_TRANSFERS_SYNC,
                    Map.of("teamId", teamId), SyncQueueService.PRIORITY_COVERED)) {
                added++;
            }
        }
        log.info("Daily transfers auto-enqueue: {} covered takim icin transfers job", added);
    }

    /**
     * Her gun 04:00 — covered ligler standings sync. Maclar gece sonuclanir,
     * standings sabaha kadar guncel olur.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-daily-standings-cron:0 0 4 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void dailyCoveredStandings() {
        Integer season = LocalDate.now().getYear();
        int added = 0;
        for (Long leagueId : leagueRepository.findByCoveredTrue().stream()
                .map(l -> l.getId()).toList()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("leagueId", leagueId);
            payload.put("season", season);
            if (queueService.enqueueIfAbsent(SyncJobType.LEAGUE_STANDINGS_SYNC,
                    payload, SyncQueueService.PRIORITY_COVERED)) {
                added++;
            }
        }
        log.info("Daily standings auto-enqueue: {} covered lig icin standings job", added);
    }

    /**
     * Her gun 04:30 — covered ligler top scorers/assists/cards. 4 kategori
     * × N lig = ~120 is. Worker 4dk'da bitirir.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-daily-top-players-cron:0 30 4 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void dailyCoveredTopPlayers() {
        Integer season = LocalDate.now().getYear();
        int added = 0;
        String[] categories = {"SCORERS", "ASSISTS", "YELLOW_CARDS", "RED_CARDS"};
        for (Long leagueId : leagueRepository.findByCoveredTrue().stream()
                .map(l -> l.getId()).toList()) {
            for (String category : categories) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("leagueId", leagueId);
                payload.put("season", season);
                payload.put("category", category);
                if (queueService.enqueueIfAbsent(SyncJobType.LEAGUE_TOP_PLAYERS_SYNC,
                        payload, SyncQueueService.PRIORITY_COVERED)) {
                    added++;
                }
            }
        }
        log.info("Daily top players auto-enqueue: {} job kuyruga eklendi (covered lig × 4 kategori)",
                added);
    }

    /**
     * Her gun 05:00 — covered takimlar icin oyuncu sezon istatistikleri.
     * Maclar tamamlandiktan sonra agg stat'lar guncellenir.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-daily-team-player-stats-cron:0 0 5 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void dailyCoveredTeamPlayerStats() {
        Integer season = LocalDate.now().getYear();
        int added = 0;
        for (Long teamId : teamRepository.findAll().stream()
                .filter(t -> t.isCovered())
                .map(t -> t.getId())
                .toList()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("teamId", teamId);
            payload.put("season", season);
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_PLAYER_STATS_SYNC,
                    payload, SyncQueueService.PRIORITY_COVERED)) {
                added++;
            }
        }
        log.info("Daily team player stats auto-enqueue: {} covered takim", added);
    }

    /**
     * Her gun 02:30 — covered ligler icin {@code /teams?league=X&season=Y}
     * enqueue. Junction tablosunda o lig+sezon icin kayit varsa atlanir
     * (idempotent — boylece bir kez dolduktan sonra her gun tekrar API'yi
     * cagirmayız).
     *
     * <p>Tum ligleri covered yapacak admin akisi: kullanici PUT
     * {@code /coverage/all?covered=true} cagirir → bu cron her gece junction'da
     * eksik olanlari kuyruga atar → worker 2sn/is hizinda yavas yavas isler.
     *
     * <p>Tum DB'deki ligler covered olursa (~1000+): ilk gece worker yaklasik
     * 35dk surede tum eksik /teams cagrilarini bitirir (2sn x 1000 = ~33dk).
     * Kota olarak ~1000 cagri/gun — limit icinde kucuk fraksiyon.
     *
     * <p>Bir sonraki gecelerde junction dolu oldugu icin atlanir; sezon
     * yenilenmedikce hicbir is enqueue edilmez.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-daily-teams-roster-cron:0 30 2 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void dailyCoveredTeamsRoster() {
        Integer season = LocalDate.now().getYear();
        int considered = 0;
        int added = 0;
        int skipped = 0;
        for (League league : leagueRepository.findByCoveredTrue()) {
            considered++;
            // Ligin kendi currentSeason'ina oncelik ver — sezon yili API
            // konvansiyonu (Avrupa: 2025=2025/26, MLS: 2026=2026 takvim).
            Integer effective = league.getCurrentSeason() != null
                    ? league.getCurrentSeason()
                    : season;
            // Junction dolu mu? Doluysa /teams cagrisi gereksiz.
            if (membershipRepository.existsByIdLeagueIdAndIdSeason(
                    league.getId(), effective)) {
                skipped++;
                continue;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("leagueId", league.getId());
            payload.put("season", effective);
            if (queueService.enqueueIfAbsent(SyncJobType.LEAGUE_TEAMS_SYNC,
                    payload, SyncQueueService.PRIORITY_BULK)) {
                added++;
            }
        }
        log.info("Daily teams roster auto-enqueue: {} covered lig kontrol edildi, "
                + "{} yeni LEAGUE_TEAMS_SYNC eklendi, {} junction'da dolu (atlandi)",
                considered, added, skipped);
    }

    /**
     * Her gun 05:30 — covered oyuncular icin profile + sezon stats refresh.
     * Squad/lineup yoluyla DB'ye dusen oyuncular icin tam profil.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-daily-player-profile-cron:0 30 5 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void dailyCoveredPlayerProfiles() {
        Integer season = LocalDate.now().getYear();
        int added = 0;
        for (Long playerId : playerRepository.findByCoveredTrue().stream()
                .map(p -> p.getId()).toList()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("playerId", playerId);
            payload.put("season", season);
            if (queueService.enqueueIfAbsent(SyncJobType.PLAYER_PROFILE_SYNC,
                    payload, SyncQueueService.PRIORITY_COVERED)) {
                added++;
            }
        }
        log.info("Daily player profile auto-enqueue: {} covered oyuncu", added);
    }

    /**
     * <b>Saatlik hidratasyon — eksik isim doldurma.</b>
     *
     * <p>API-Football lineup/squad/playerStat/topscorer endpoint'leri sadece
     * kisa form ("A. Guler") doner; tam isim ("Arda Guler") sadece
     * {@code /players/profiles} ile gelir. PlayerUpserter bu endpoint'lerden
     * gelen oyunculari sadece id+name+photo ile kaydeder — firstname/lastname
     * null kalir.
     *
     * <p>Bu cron her saatin 15. dakikasinda calisir, DB'de
     * firstname null/bos olan ilk 500 oyuncuyu PLAYER_PROFILE_SYNC queue'sune
     * ekler. Worker quota tracker'i ile adaptif yavaslar (saat basina
     * ~150-500 sync, 75k/gun API limiti icinde rahat).
     *
     * <p>covered=true filtre YOK — UI'da gorunen herkesin (kupa final
     * macindaki oyuncu, baska liglerdeki transfer, vb.) tam adi olur.
     *
     * <p>Idempotent: enqueueIfAbsent ayni payload PENDING'deyse atlar.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.auto-enqueue-hourly-player-hydrate-cron:0 15 * * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void hourlyHydrateMissingPlayerNames() {
        Integer season = LocalDate.now().getYear();
        int added = 0;
        var batch = playerRepository.findBatchNeedingProfileHydration();
        for (var player : batch) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("playerId", player.getId());
            payload.put("season", season);
            // PRIORITY_BULK — covered (PRIORITY_COVERED=3) ve admin (DEFAULT=5)
            // ile gercek kullanici tetiklemelerinden sonra islenir. Bu cron
            // arka plan toplu doldurma — diger isleri yavaslatmasin.
            if (queueService.enqueueIfAbsent(SyncJobType.PLAYER_PROFILE_SYNC,
                    payload, SyncQueueService.PRIORITY_BULK)) {
                added++;
            }
        }
        if (added > 0) {
            log.info("Hourly player name hydrate auto-enqueue: {} oyuncu (batch={})",
                    added, batch.size());
        }
    }

    // Basketbol covered ligler gunluk tazeleme:
    //   {@code DailyBasketballLeagueRefreshJob} (futbol patternine paralel)
    //   tarafindan dogrudan inline cagri ile yapilir. Burada queue tabanli
    //   enqueue yok — duplicate'i onlemek icin. Queue tabanli LEAGUE_TEAMS_SYNC
    //   gibi enqueue isleri admin / on-demand cagrilar icin SyncJobExecutor'da
    //   dispatch edilmeye devam eder.
}
