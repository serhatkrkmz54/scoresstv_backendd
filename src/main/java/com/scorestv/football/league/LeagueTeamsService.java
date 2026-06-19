package com.scorestv.football.league;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamLeagueSeasonRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.sync.ReferenceSyncService;
import com.scorestv.football.sync.TeamSyncService;
import com.scorestv.football.web.dto.LeagueTeamView;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lig icin lightweight takim listesi — onboarding (favori takim secimi)
 * gibi ekranlar icin.
 *
 * <h3>4-katmanli kaynak hiyerarsisi</h3>
 *
 * Sirayla denenir, ilki dolu cevap verirse o doner.
 *
 * <ol>
 *   <li><b>Junction tablosu</b> ({@code team_league_seasons}) — en kapsamli
 *       ve en hizli kaynak. /teams API'sinden yazilir; sezon basinda fikstur
 *       olmasa bile resmi kadro burada vardir.</li>
 *   <li><b>Standings</b> — junction yoksa ama lig standings sync edilmisse.
 *       Lig tipi yarismalar icin guvenilir (kupalarda standings olmayabilir).</li>
 *   <li><b>Fixtures UNION</b> — son care; en az bir maci olan takimlar.
 *       Sezon basinda eksik kalir, partial sync sonrasi eksik gozukur.</li>
 *   <li><b>Async /teams sync</b> — <b>junction'da hic kayit yoksa</b>
 *       background'da {@link TeamSyncService#syncLeague} tetiklenir. 5 dk
 *       debounce. Sync tamamlaninca cache evict edilir → kullanici tekrar
 *       acinca tam liste alir.</li>
 * </ol>
 *
 * <h3>Async tetikleme karari</h3>
 *
 * Kaynak {@link #loadCached} hangi katmandan veri donderirse dondersin,
 * <b>junction'da kayit yoksa</b> async sync mutlaka tetiklenir. Boylece
 * fixtures'tan partial bir liste (orn. lige 6 takim oynayacak ama henuz
 * 3'unun fiksturu var) gelirse de eksik kalmaz; bir sonraki acista resmi
 * /teams sonucu cache'lenir.
 */
@Service
public class LeagueTeamsService {

    private static final Logger log = LoggerFactory.getLogger(LeagueTeamsService.class);

    /**
     * Background /teams sync icin per (leagueId, season) debounce.
     * 30sn — async sync genelde 1-3sn surdugu icin yeterli; kullanici geri
     * dondugunde junction zaten dolu olur. Daha uzun debounce sync hata
     * verirse kullaniciyi 5dk boyunca eksik liste ile birakir.
     */
    private static final Duration TEAMS_SYNC_DEBOUNCE = Duration.ofSeconds(30);
    private final Map<String, Instant> _lastTeamsSync = new ConcurrentHashMap<>();

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final FixtureRepository fixtureRepository;
    private final StandingRepository standingRepository;
    private final TeamLeagueSeasonRepository membershipRepository;
    private final TeamSyncService teamSyncService;
    private final ReferenceSyncService referenceSyncService;
    private final MinioStorageService storage;
    private final CacheManager cacheManager;
    private final LeagueTeamsService self;

    public LeagueTeamsService(LeagueRepository leagueRepository,
                              SeasonRepository seasonRepository,
                              FixtureRepository fixtureRepository,
                              StandingRepository standingRepository,
                              TeamLeagueSeasonRepository membershipRepository,
                              TeamSyncService teamSyncService,
                              ReferenceSyncService referenceSyncService,
                              MinioStorageService storage,
                              CacheManager cacheManager,
                              @org.springframework.context.annotation.Lazy LeagueTeamsService self) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.fixtureRepository = fixtureRepository;
        this.standingRepository = standingRepository;
        this.membershipRepository = membershipRepository;
        this.teamSyncService = teamSyncService;
        this.referenceSyncService = referenceSyncService;
        this.storage = storage;
        this.cacheManager = cacheManager;
        this.self = self;
    }

    /**
     * Verilen lig + sezon icin takim listesi — HIZLI yol.
     *
     * <p>4-katmanli kaynak hiyerarsisini calistirir. <b>Junction'da hic kayit
     * YOKSA</b> kaynak ne olursa olsun fire-and-forget /teams sync tetikler;
     * partial/eksik fixtures sonucu cache'lense bile bir sonraki istek dolu
     * liste alir.
     *
     * <p>Junction bos → cache invalidate edilir, boylelikle olasi partial
     * (fixtures'tan dolu) sonuc her acista yeniden okunur ve cache'i kirletmez.
     *
     * @param leagueId   lig id (slug'tan cikarilmis)
     * @param season     null verilirse ligin current sezonu
     * @param turkish    Turkce ad varsa onu kullan
     */
    public List<LeagueTeamView> getTeams(Long leagueId, Integer season, boolean turkish) {
        Integer effectiveSeason = resolveSeasonOrNull(leagueId, season);
        boolean junctionPopulated = effectiveSeason != null
                && membershipRepository.existsByIdLeagueIdAndIdSeason(leagueId, effectiveSeason);

        if (!junctionPopulated && effectiveSeason != null) {
            // Junction yoksa cache'te partial (fixtures fallback) sonuc olabilir
            // — anlik invalidate et ki her acista taze okuma yapilsin. Junction
            // dolduktan sonraki ilk acisla otomatik dolu sonuc cache'lenir.
            evictCacheForLeagueSeason(leagueId, effectiveSeason);
        }

        List<LeagueTeamView> teams = loadCached(leagueId, season, turkish);

        if (!junctionPopulated) {
            self.ensureTeamsAsync(leagueId, season);
        }
        return teams;
    }

    /**
     * Background /teams sync — request'i bekletmez. {@link TeamSyncService#syncLeague}
     * API-Football'dan resmi kadroyu ceker; upserter junction tablosuna yazar.
     * Tamamlaninca lig+sezon icin cache evict — kullanici tekrar acinca tam liste.
     */
    @Async
    public void ensureTeamsAsync(Long leagueId, Integer season) {
        if (leagueId == null) return;

        // Debounce — leagueId bazli (asagidaki syncOne current_season'i
        // degistirebildigi icin sezon degil leagueId anahtar). Kisa pencere;
        // sync 1-3sn surer, kullanici geri dondugunde junction dolu olur.
        String key = leagueId.toString();
        Instant last = _lastTeamsSync.get(key);
        if (last != null && last.isAfter(Instant.now().minus(TEAMS_SYNC_DEBOUNCE))) {
            log.debug("ensureTeamsAsync: debounce'a takildi leagueId={}", leagueId);
            return;
        }
        _lastTeamsSync.put(key, Instant.now());

        try {
            League league = leagueRepository.findById(leagueId).orElse(null);
            if (league == null) {
                log.warn("ensureTeamsAsync: lig bulunamadi leagueId={}", leagueId);
                return;
            }

            // SUREKLI-GUNCEL: explicit sezon istenmediyse ONCE lig metadata'sini
            // tazele — current_season, API'nin current=true sezonuna guncellenir.
            // Boylece stale current_season (sezon devri ya da covered-disi lig)
            // onboarding'i bos birakmaz; takimlar HER ZAMAN guncel sezona gelir.
            if (season == null) {
                try {
                    referenceSyncService.syncOne(leagueId);
                    league = leagueRepository.findById(leagueId).orElse(league);
                } catch (RuntimeException ex) {
                    log.warn("ensureTeamsAsync: lig metadata refresh hata leagueId={}: {}",
                            leagueId, ex.getMessage());
                }
            }

            Integer effective = resolveSeason(league, season);
            if (effective == null) {
                log.warn("ensureTeamsAsync: sezon cozumlenemedi leagueId={} (currentSeason ve "
                        + "seasons tablosu bos) — /teams cagrilamayacak", leagueId);
                return;
            }
            log.info("ensureTeamsAsync: /teams cagriliyor leagueId={} ({}) season={}",
                    leagueId, league.getName(), effective);
            int n = teamSyncService.syncLeague(leagueId, effective);
            if (n == 0) {
                // API'den 0 sonuc — guncel sezon henuz kapsanmiyor (sezon basi) olabilir.
                log.warn("ensureTeamsAsync: /teams API'den 0 takim dondu leagueId={} ({}) season={} "
                        + "— olasi sebep: API'de bu sezon henuz kapsanmiyor (sezon basi)",
                        leagueId, league.getName(), effective);
            } else {
                log.info("ensureTeamsAsync: junction'a {} takim yazildi leagueId={} ({}) season={}",
                        n, leagueId, league.getName(), effective);
            }
            // Cache evict — basarisiz olsa bile yap (partial sonuc cache'i kirletmesin).
            evictCacheForLeagueSeason(leagueId, effective);
        } catch (RuntimeException ex) {
            log.warn("Teams async sync hatasi (leagueId={}): {}", leagueId, ex.getMessage(), ex);
        }
    }

    /**
     * Verilen lig+sezon icin tum cache varyantlarini (tr/en + current/explicit)
     * temizler. {@code @CacheEvict} tek key destekledigi icin manuel evict.
     */
    private void evictCacheForLeagueSeason(Long leagueId, Integer season) {
        Cache cache = cacheManager.getCache(FootballCacheNames.STATIC);
        if (cache == null) return;
        // Cache key format: 'league-teams-' + leagueId + '-' + (season|'cur') + '-' + (tr|en)
        // Hem explicit season hem 'cur' (current resolve) varyantlarini sil.
        for (String lang : new String[]{"tr", "en"}) {
            cache.evict("league-teams-" + leagueId + "-" + season + "-" + lang);
            cache.evict("league-teams-" + leagueId + "-cur-" + lang);
        }
    }

    @Cacheable(value = FootballCacheNames.STATIC,
            key = "'league-teams-' + #leagueId + '-' "
                + "+ (#season == null ? 'cur' : #season) "
                + "+ '-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public List<LeagueTeamView> loadCached(Long leagueId, Integer season, boolean turkish) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi."));

        Integer effectiveSeason = resolveSeason(league, season);
        if (effectiveSeason == null) return List.of();

        // 1. Junction tablosu — en kapsamli kaynak.
        List<Team> teams = membershipRepository
                .findTeamsByLeagueAndSeason(leagueId, effectiveSeason);

        // 2. Standings fallback — League tipi yarismalarda guvenilir.
        if (teams.isEmpty()) {
            teams = standingRepository
                    .findDistinctTeamsByLeagueAndSeason(leagueId, effectiveSeason);
            if (!teams.isEmpty()) {
                log.debug("Takim listesi standings'ten dolduruldu: leagueId={} season={} count={}",
                        leagueId, effectiveSeason, teams.size());
            }
        }

        // 3. Fixtures UNION fallback — son care.
        if (teams.isEmpty()) {
            teams = fixtureRepository
                    .findDistinctTeamsByLeagueAndSeason(leagueId, effectiveSeason);
            if (!teams.isEmpty()) {
                log.debug("Takim listesi fixtures'tan dolduruldu: leagueId={} season={} count={}",
                        leagueId, effectiveSeason, teams.size());
            }
        }

        return teams.stream().map(t -> toView(t, league, turkish)).toList();
    }

    private LeagueTeamView toView(Team t, League league, boolean turkish) {
        String displayName = displayName(t, turkish);
        return new LeagueTeamView(
                t.getId(),
                t.getName(),
                t.getNameTr() != null && !t.getNameTr().isBlank() ? t.getNameTr() : t.getName(),
                deriveShortCode(t, displayName),
                SlugUtil.teamSlug(displayName, t.getId()),
                t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : null,
                null, // primaryColor — backend'de yok, mobile id-hash ile uretir
                t.getCountry(),
                league.getCountryCode());
    }

    /** "BJK", "FB" gibi 2-4 harflik kisaltma. API'den code yoksa name'den uretir. */
    private static String deriveShortCode(Team t, String displayName) {
        if (t.getCode() != null && !t.getCode().isBlank()) return t.getCode();
        // Kelime sayisina gore: tek kelime → ilk 3, coklu → her kelimenin ilk harfi
        String[] words = displayName.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].length() >= 3
                    ? words[0].substring(0, 3).toUpperCase(Locale.ROOT)
                    : words[0].toUpperCase(Locale.ROOT);
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() >= 3) break;
            if (w.isEmpty()) continue;
            sb.append(w.charAt(0));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    /** {@link #getTeams} icin: junction lookup'tan once season'i cozumle. */
    private Integer resolveSeasonOrNull(Long leagueId, Integer requested) {
        if (requested != null) return requested;
        return leagueRepository.findById(leagueId)
                .map(l -> resolveSeason(l, null))
                .orElse(null);
    }

    private Integer resolveSeason(League league, Integer requested) {
        if (requested != null) return requested;
        if (league.getCurrentSeason() != null) return league.getCurrentSeason();
        return seasonRepository.findByLeagueIdOrderByYearDesc(league.getId())
                .stream().findFirst().map(Season::getYear).orElse(null);
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) return tr;
        }
        return entity.getName();
    }
}
