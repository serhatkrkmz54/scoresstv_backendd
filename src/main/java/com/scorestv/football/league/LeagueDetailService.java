package com.scorestv.football.league;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.LeagueTopPlayer;
import com.scorestv.football.domain.LeagueTopPlayer.Category;
import com.scorestv.football.domain.LeagueTopPlayerRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerSeasonStat;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.domain.Standing;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.seo.LeagueDetailSeoBuilder;
import com.scorestv.football.web.PlayerPhotoResolver;
import com.scorestv.football.web.dto.BracketView;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.football.web.dto.LeagueDetailResponse;
import com.scorestv.football.web.dto.LeagueDetailResponse.CoverageInfo;
import com.scorestv.football.web.dto.LeagueDetailResponse.RoundGroup;
import com.scorestv.football.web.dto.LeagueDetailResponse.SeasonInfo;
import com.scorestv.football.web.dto.LeagueDetailResponse.TopPlayerView;
import com.scorestv.football.web.dto.LeagueDetailResponse.TopRatedPlayer;
import com.scorestv.football.web.dto.LeagueSeoResponse;
import com.scorestv.football.web.dto.StandingRow;
import com.scorestv.football.web.dto.StandingsGroup;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lig detay sayfasinin yanitini ({@link LeagueDetailResponse}) uretir.
 *
 * <p>{@link MatchDetailService} ile ayni desen:
 * <ul>
 *   <li>{@code getById} (cache yok, tx yok) → lazy sync ensure + self proxy cagrisi</li>
 *   <li>{@code loadCachedResponse} (Cacheable + readOnly tx) — gercek DB okumasi</li>
 *   <li>{@code @Lazy MatchDetailService self} ile proxy advice'lari devreye girer</li>
 * </ul>
 *
 * <p>Cache TTL: LIVE (15sn) — lig sayfasi sik tazelenir; canli mac varsa skor
 * gerceklik takip eder. Buyuk veri (380 mac + tum top oyuncular) icin biraz
 * agir ama ratio kabul edilebilir; daha gevsek cache istenirse ayri cache
 * adi tanimlanabilir.
 */
@Service
public class LeagueDetailService {

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final FixtureRepository fixtureRepository;
    private final StandingRepository standingRepository;
    private final LeagueTopPlayerRepository topPlayerRepository;
    private final CountryRepository countryRepository;
    private final TeamRepository teamRepository;
    private final PlayerSeasonStatRepository playerSeasonStatRepository;
    private final LeagueDetailLazySync lazySync;
    private final LeagueDetailSeoBuilder seoBuilder;
    private final BracketBuilder bracketBuilder;
    private final FootballMessages messages;
    private final MinioStorageService storage;
    private final PlayerPhotoResolver photoResolver;
    private final LeagueDetailService self;

    public LeagueDetailService(LeagueRepository leagueRepository,
                               SeasonRepository seasonRepository,
                               FixtureRepository fixtureRepository,
                               StandingRepository standingRepository,
                               LeagueTopPlayerRepository topPlayerRepository,
                               CountryRepository countryRepository,
                               TeamRepository teamRepository,
                               PlayerSeasonStatRepository playerSeasonStatRepository,
                               LeagueDetailLazySync lazySync,
                               LeagueDetailSeoBuilder seoBuilder,
                               BracketBuilder bracketBuilder,
                               FootballMessages messages,
                               MinioStorageService storage,
                               PlayerPhotoResolver photoResolver,
                               @Lazy LeagueDetailService self) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.fixtureRepository = fixtureRepository;
        this.standingRepository = standingRepository;
        this.topPlayerRepository = topPlayerRepository;
        this.countryRepository = countryRepository;
        this.teamRepository = teamRepository;
        this.playerSeasonStatRepository = playerSeasonStatRepository;
        this.lazySync = lazySync;
        this.seoBuilder = seoBuilder;
        this.bracketBuilder = bracketBuilder;
        this.messages = messages;
        this.storage = storage;
        this.photoResolver = photoResolver;
        this.self = self;
    }

    /**
     * Lig detay endpoint'inin public girisi.
     *
     * @param leagueId lig id
     * @param requestedSeason istenen sezon (null → ligin current'i)
     * @param turkish "tr" → Turkce; aksi halde EN
     */
    public LeagueDetailResponse getById(Long leagueId, Integer requestedSeason, boolean turkish) {
        return getById(leagueId, requestedSeason, turkish, false);
    }

    /**
     * Lig detay endpoint'i — forceRefresh destegi ile.
     *
     * <p>{@code forceRefresh=true} → Redis cache evict + lazy sync INLINE
     * (kullanici "Yenile"ye basti, fresh bekliyor; 3-5sn yavas olabilir).
     * Aksi halde lazy sync ASYNC fire-and-forget — cevap DB'de o anda ne
     * varsa onunla DONER. Mobile silent retry zinciri eksik veriyi yakalar.
     *
     * <p>Match detay endpoint'i ile ayni desen.
     */
    public LeagueDetailResponse getById(Long leagueId, Integer requestedSeason,
                                        boolean turkish, boolean forceRefresh) {
        if (forceRefresh) {
            self.evictDetailCache(leagueId, requestedSeason, turkish);
            // INLINE — kullanici bekleyebilir
            lazySync.ensureFor(leagueId, requestedSeason);
        } else {
            // FIRE-AND-FORGET — cevap hemen doner, eksikler arkadan dolar
            lazySync.ensureForAsync(leagueId, requestedSeason);
        }
        return self.loadCachedResponse(leagueId, requestedSeason, turkish);
    }

    /**
     * Detay cache'ini siler — force refresh akisinda kullanilir. @CacheEvict
     * advice'i Spring proxy uzerinden cagrildiginda calisir (self injection).
     */
    @CacheEvict(value = FootballCacheNames.LIVE,
            key = "'league-' + #leagueId + '-' + (#season == null ? 'cur' : #season) + '-' + (#turkish ? 'tr' : 'en')")
    public void evictDetailCache(Long leagueId, Integer season, boolean turkish) {
        // No-op — sadece @CacheEvict advice'i icin
    }

    /** Cache'li okuma — yalniz {@link #getById}'den self proxy ile cagrilir.
     *
     * <p>{@code unless}: standings + rounds + topScorers + topRated HEPSI bos
     * ise cevap cache'lenmez (lazy sync henuz tamamlanmamis sayilir). Mobile
     * silent retry'lerin bir sonraki cagrisi fresh DB'yi okur.
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'league-' + #leagueId + '-' + (#season == null ? 'cur' : #season) + '-' + (#turkish ? 'tr' : 'en')",
            unless = "#result == null || ("
                + "#result.standings().isEmpty() "
                + "&& #result.rounds().isEmpty() "
                + "&& #result.topScorers().isEmpty() "
                + "&& #result.topRatedPlayers().isEmpty())")
    @Transactional(readOnly = true)
    public LeagueDetailResponse loadCachedResponse(Long leagueId, Integer season, boolean turkish) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi."));

        List<Season> allSeasons = seasonRepository.findByLeagueIdOrderByYearDesc(leagueId);
        Integer selectedSeason = resolveSelectedSeason(league, allSeasons, season);

        Country country = (league.getCountryName() != null)
                ? countryRepository.findByName(league.getCountryName()).orElse(null)
                : null;

        Season selected = allSeasons.stream()
                .filter(s -> s.getYear().equals(selectedSeason))
                .findFirst()
                .orElse(null);

        List<StandingsGroup> standings = loadStandings(leagueId, selectedSeason, turkish);
        List<RoundGroup> rounds = loadRounds(leagueId, selectedSeason, turkish);
        List<TopPlayerView> topScorers = loadTopPlayers(leagueId, selectedSeason, Category.SCORERS, turkish);
        List<TopPlayerView> topAssists = loadTopPlayers(leagueId, selectedSeason, Category.ASSISTS, turkish);
        List<TopPlayerView> topYellowCards =
                loadTopPlayers(leagueId, selectedSeason, Category.YELLOW_CARDS, turkish);
        List<TopPlayerView> topRedCards =
                loadTopPlayers(leagueId, selectedSeason, Category.RED_CARDS, turkish);
        List<TopRatedPlayer> topRatedPlayers =
                loadTopRatedPlayers(leagueId, selectedSeason, turkish);
        // Kupa eleme bracket — yalniz Cup tipi liglerde dolu. League ise null.
        BracketView bracket = bracketBuilder.build(
                league.getType(), leagueId, selectedSeason, turkish);

        String displayLeague = displayName(league, turkish);
        String slug = SlugUtil.leagueSlug(displayLeague, league.getId());

        LeagueSeoResponse seo = seoBuilder.build(
                league, country, selectedSeason, turkish ? "tr" : "en");

        return new LeagueDetailResponse(
                league.getId(),
                slug,
                displayLeague,
                messages.leagueType(league.getType(), turkish),
                league.getLogoKey() != null ? storage.publicUrl(league.getLogoKey()) : null,
                toCountryDto(country, league, turkish),
                league.getCurrentSeason(),
                selectedSeason,
                allSeasons.stream().map(LeagueDetailService::toSeasonInfo).toList(),
                toCoverage(selected),
                standings,
                rounds,
                topScorers,
                topAssists,
                topYellowCards,
                topRedCards,
                topRatedPlayers,
                bracket,
                seo);
    }

    /** Rating'e gore top 20 oyuncu (min 5 mac). Standings sayfasi widget'i. */
    private List<TopRatedPlayer> loadTopRatedPlayers(Long leagueId, Integer season,
                                                      boolean turkish) {
        if (season == null) return List.of();
        List<PlayerSeasonStat> rows = playerSeasonStatRepository
                .findTopRatedByLeagueSeason(leagueId, season, 5, 20);
        if (rows.isEmpty()) return List.of();
        // CDN foto'lari tek sorguda cek (N+1 onleme)
        var photoMap = photoResolver.loadMap(
                rows.stream().map(PlayerSeasonStat::getPlayerId)
                        .filter(java.util.Objects::nonNull).toList());

        List<TopRatedPlayer> views = new ArrayList<>(rows.size());
        int rank = 1;
        for (PlayerSeasonStat s : rows) {
            Team team = s.getTeam();
            String teamDisplay = team != null ? displayName(team, turkish) : null;
            // JSONB icindeki games.position + rating cikar
            String rawPosition = null;
            java.math.BigDecimal rating = null;
            java.util.Map<String, Object> stats = s.getStatsJson();
            if (stats != null && stats.get("games") instanceof java.util.Map<?, ?> games) {
                Object pos = games.get("position");
                if (pos instanceof String posStr) rawPosition = posStr;
                Object r = games.get("rating");
                if (r instanceof String rs) {
                    try { rating = new java.math.BigDecimal(rs); } catch (Exception ignored) {}
                } else if (r instanceof Number rn) {
                    rating = java.math.BigDecimal.valueOf(rn.doubleValue());
                }
            }
            Player master = photoMap.get(s.getPlayerId());
            String playerName = master != null ? master.getName()
                    : "Player#" + s.getPlayerId();
            String playerSlug = com.scorestv.common.SlugUtil.playerSlug(
                    master != null ? master.getFirstname() : null,
                    master != null ? master.getLastname() : null,
                    playerName, s.getPlayerId());
            views.add(new TopRatedPlayer(
                    rank++,
                    s.getPlayerId(),
                    playerName,
                    playerSlug,
                    photoResolver.photoUrl(photoMap, s.getPlayerId(), null),
                    master != null ? master.getNationality() : null,
                    master != null ? master.getAge() : null,
                    rawPosition,
                    rawPosition == null ? null : messages.playerPosition(rawPosition, turkish),
                    team != null ? team.getId() : null,
                    teamDisplay,
                    team != null && team.getLogoKey() != null
                            ? storage.publicUrl(team.getLogoKey()) : null,
                    team != null && teamDisplay != null
                            ? com.scorestv.common.SlugUtil.teamSlug(teamDisplay, team.getId())
                            : null,
                    rating,
                    stats));
        }
        return views;
    }

    /**
     * Istek sezonunu netlestirir: param verildiyse onu kullan; aksi halde
     * ligin current'i; o da yoksa en yeni sezon.
     */
    private static Integer resolveSelectedSeason(League league, List<Season> seasons,
                                                  Integer requested) {
        if (requested != null) return requested;
        if (league.getCurrentSeason() != null) return league.getCurrentSeason();
        return seasons.isEmpty() ? null : seasons.get(0).getYear();
    }

    private LeagueDetailResponse.Country toCountryDto(Country country, League league, boolean turkish) {
        String name = displayCountryName(country, league, turkish);
        String code = country != null ? country.getCode() : league.getCountryCode();
        String flag = countryFlagUrl(country);
        if (flag == null) {
            flag = league.getCountryFlagUrl(); // ülke eşleşmezse ligin ham bayrağı
        }
        return new LeagueDetailResponse.Country(name, code, flag);
    }

    private String countryFlagUrl(Country country) {
        if (country == null) {
            return null;
        }
        if (country.getFlagKey() != null) {
            return storage.publicUrl(country.getFlagKey());
        }
        if (country.getFlagUrl() != null && !country.getFlagUrl().isBlank()) {
            return country.getFlagUrl();
        }
        String code = country.getCode();
        if (code != null && code.length() == 2) {
            return "https://flagcdn.com/w160/"
                    + code.toLowerCase(java.util.Locale.ROOT) + ".png";
        }
        return null;
    }

    private static String displayCountryName(Country country, League league, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        if (country != null && country.getName() != null) {
            return country.getName();
        }
        return league.getCountryName();
    }

    private static SeasonInfo toSeasonInfo(Season s) {
        return new SeasonInfo(s.getYear(), s.getStartDate(), s.getEndDate(), s.isCurrent());
    }

    /** Sezona ozgu coverage; sezon yoksa hepsi false. */
    private static CoverageInfo toCoverage(Season s) {
        if (s == null) {
            return new CoverageInfo(false, false, false, false, false, false,
                    false, false, false, false, false, false);
        }
        return new CoverageInfo(
                s.isCoverageStandings(),
                s.isCoverageEvents(),
                s.isCoverageLineups(),
                s.isCoverageStatsFixtures(),
                s.isCoverageStatsPlayers(),
                s.isCoveragePlayers(),
                s.isCoverageTopScorers(),
                s.isCoverageTopAssists(),
                s.isCoverageTopCards(),
                s.isCoverageInjuries(),
                s.isCoveragePredictions(),
                s.isCoverageOdds());
    }

    /** Puan durumu — gruplara bolunmus; MatchDetailService'tekiyle ayni mantik. */
    private List<StandingsGroup> loadStandings(Long leagueId, Integer season, boolean turkish) {
        if (season == null) return List.of();
        List<Standing> rows =
                standingRepository.findByLeagueIdAndSeasonOrderByRankAsc(leagueId, season);
        if (rows.isEmpty()) return List.of();

        Map<String, List<StandingRow>> byGroup = new LinkedHashMap<>();
        for (Standing s : rows) {
            String key = s.getGroupName() == null ? "" : s.getGroupName();
            byGroup.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(toStandingRow(s, turkish));
        }
        List<StandingsGroup> groups = new ArrayList<>(byGroup.size());
        for (Map.Entry<String, List<StandingRow>> e : byGroup.entrySet()) {
            String rawGroup = e.getKey().isEmpty() ? null : e.getKey();
            String translated = rawGroup == null
                    ? null
                    : messages.standingGroupName(rawGroup, turkish);
            e.getValue().sort(Comparator.comparing(
                    StandingRow::rank, Comparator.nullsLast(Comparator.naturalOrder())));
            groups.add(new StandingsGroup(rawGroup, translated, e.getValue()));
        }
        // Gruplari isim sirasinda sirala (Group A, B, C, D...). null gruplar
        // en basa (ulusal lig — tek grup, null).
        groups.sort(Comparator.comparing(
                StandingsGroup::groupName,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return groups;
    }

    private StandingRow toStandingRow(Standing s, boolean turkish) {
        Team t = s.getTeam();
        String displayedName = displayName(t, turkish);
        return new StandingRow(
                s.getRank(),
                t.getId(),
                displayedName,
                t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : null,
                SlugUtil.teamSlug(displayedName, t.getId()),
                s.getPoints(),
                s.getGoalsDiff(),
                s.getForm(),
                s.getDescription(),
                messages.standingDescription(s.getDescription(), turkish),
                s.getPlayed(),
                s.getWin(),
                s.getDraw(),
                s.getLose(),
                s.getGoalsFor(),
                s.getGoalsAgainst());
    }

    /** Round bazli fixture gruplari. Round adi roundText ile cevrilir. */
    private List<RoundGroup> loadRounds(Long leagueId, Integer season, boolean turkish) {
        if (season == null) return List.of();
        List<Fixture> fixtures = fixtureRepository.findByLeagueIdAndSeason(leagueId, season);
        if (fixtures.isEmpty()) return List.of();

        Map<String, List<FixtureSummary>> byRound = new LinkedHashMap<>();
        for (Fixture f : fixtures) {
            String round = f.getRound() == null ? "" : f.getRound();
            byRound.computeIfAbsent(round, k -> new ArrayList<>())
                    .add(toFixtureSummary(f, turkish));
        }
        List<RoundGroup> groups = new ArrayList<>(byRound.size());
        for (Map.Entry<String, List<FixtureSummary>> e : byRound.entrySet()) {
            String raw = e.getKey().isEmpty() ? null : e.getKey();
            String text = raw == null ? null : messages.roundText(raw, turkish);
            groups.add(new RoundGroup(raw, text, e.getValue()));
        }
        return groups;
    }

    private FixtureSummary toFixtureSummary(Fixture f, boolean turkish) {
        Team home = f.getHomeTeam();
        Team away = f.getAwayTeam();
        League lg = f.getLeague();
        String leagueName = lg != null ? displayName(lg, turkish) : null;
        FixtureSummary.LeagueRef leagueRef = lg == null ? null
                : new FixtureSummary.LeagueRef(
                        lg.getId(),
                        leagueName,
                        messages.leagueType(lg.getType(), turkish),
                        lg.getLogoKey() != null ? storage.publicUrl(lg.getLogoKey()) : null,
                        SlugUtil.leagueSlug(leagueName, lg.getId()));
        return new FixtureSummary(
                f.getId(),
                SlugUtil.fixtureSlug(home.getName(), away.getName(), f.getId()),
                leagueRef,
                messages.roundText(f.getRound(), turkish),
                f.getKickoffAt(),
                f.getLastSyncedAt(),
                FixtureSummary.Status.of(
                        f.getStatusShort(),
                        messages.statusText(f.getStatusShort(), f.getStatusLong(), turkish),
                        f.getElapsed(), f.getStatusExtra()),
                new FixtureSummary.Team(
                        home.getId(), displayName(home, turkish),
                        home.getLogoKey() != null ? storage.publicUrl(home.getLogoKey()) : null,
                        SlugUtil.teamSlug(displayName(home, turkish), home.getId())),
                new FixtureSummary.Team(
                        away.getId(), displayName(away, turkish),
                        away.getLogoKey() != null ? storage.publicUrl(away.getLogoKey()) : null,
                        SlugUtil.teamSlug(displayName(away, turkish), away.getId())),
                new FixtureSummary.Score(f.getHomeGoals(), f.getAwayGoals()),
                toVenueSummary(f, turkish),
                // Lig fikstür listesinde kırmızı kart rozeti gösterilmiyor.
                0,
                0);
    }

    /**
     * Fikstur ozetinde venue: FK Venue varsa name_tr destekli, yoksa inline
     * {@code venueName/venueCity} fallback (UEFA CL gibi maclar icin).
     */
    private FixtureSummary.Venue toVenueSummary(Fixture f, boolean turkish) {
        com.scorestv.football.domain.Venue v = f.getVenue();
        if (v != null) {
            return new FixtureSummary.Venue(displayName(v, turkish), v.getCity());
        }
        if (f.getVenueName() != null && !f.getVenueName().isBlank()) {
            return new FixtureSummary.Venue(f.getVenueName(), f.getVenueCity());
        }
        return null;
    }

    /** Top scorers/assists/cards — DB'den rank sirali ceker, DTO'ya cevirir. */
    private List<TopPlayerView> loadTopPlayers(Long leagueId, Integer season, Category category,
                                                boolean turkish) {
        if (season == null) return List.of();
        List<LeagueTopPlayer> rows =
                topPlayerRepository.findByLeagueSeasonCategory(leagueId, season, category);
        if (rows.isEmpty()) return List.of();
        // CDN foto'lari tek sorguda cek (player + team — N+1 onleme)
        var photoMap = photoResolver.loadMap(
                rows.stream().map(LeagueTopPlayer::getPlayerId)
                        .filter(java.util.Objects::nonNull).toList());
        var teamIds = rows.stream().map(LeagueTopPlayer::getTeamId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Team> teamMap = new java.util.HashMap<>();
        for (Team t : teamRepository.findAllById(teamIds)) {
            teamMap.put(t.getId(), t);
        }
        List<TopPlayerView> views = new ArrayList<>(rows.size());
        for (LeagueTopPlayer p : rows) {
            // Team logo: DB'de team varsa logo_key'den MinIO URL; yoksa
            // LeagueTopPlayer satirinda denormalize edilmis API URL'i fallback.
            String teamLogo = p.getTeamLogo();  // varsayilan fallback
            Team team = p.getTeamId() != null ? teamMap.get(p.getTeamId()) : null;
            if (team != null && team.getLogoKey() != null) {
                teamLogo = storage.publicUrl(team.getLogoKey());
            }
            // Takim slug — name_tr varsa Turkish, yoksa kaynak ad
            String teamDisplay = team != null ? displayName(team, turkish) : p.getTeamName();
            String teamSlug = (p.getTeamId() != null && teamDisplay != null)
                    ? SlugUtil.teamSlug(teamDisplay, p.getTeamId())
                    : null;
            views.add(new TopPlayerView(
                    p.getRank(),
                    p.getPlayerId(),
                    p.getPlayerName(),
                    photoResolver.photoUrl(photoMap, p.getPlayerId(), p.getPlayerPhoto()),
                    p.getPlayerNationality(),
                    p.getPlayerAge(),
                    p.getTeamId(),
                    teamDisplay,
                    teamLogo,
                    teamSlug,
                    p.getValuePrimary(),
                    p.getValueSecondary(),
                    p.getAppearances(),
                    p.getMinutes()));
        }
        return views;
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }
}
