package com.scorestv.football.team;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachCareer;
import com.scorestv.football.domain.CoachCareerRepository;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.domain.CoachTrophy;
import com.scorestv.football.domain.CoachTrophyRepository;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerSeasonStat;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.PlayerSidelined;
import com.scorestv.football.domain.PlayerSidelinedRepository;
import com.scorestv.football.domain.Standing;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TeamSquad;
import com.scorestv.football.domain.TeamSquadRepository;
import com.scorestv.football.domain.TeamStatistics;
import com.scorestv.football.domain.TeamStatisticsRepository;
import com.scorestv.football.domain.Transfer;
import com.scorestv.football.domain.TransferRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.seo.TeamDetailSeoBuilder;
import com.scorestv.football.web.PlayerPhotoResolver;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.football.web.dto.TeamDetailResponse;
import com.scorestv.football.web.dto.TeamDetailResponse.BirthInfo;
import com.scorestv.football.web.dto.TeamDetailResponse.CareerEntry;
import com.scorestv.football.web.dto.TeamDetailResponse.CoachInfo;
import com.scorestv.football.web.dto.TeamDetailResponse.CountryInfo;
import com.scorestv.football.web.dto.TeamDetailResponse.SeasonOption;
import com.scorestv.football.web.dto.TeamDetailResponse.PlayerSeasonStatView;
import com.scorestv.football.web.dto.TeamDetailResponse.SidelinedRow;
import com.scorestv.football.web.dto.TeamDetailResponse.SquadGroup;
import com.scorestv.football.web.dto.TeamDetailResponse.SquadPlayer;
import com.scorestv.football.web.dto.TeamDetailResponse.StandingsPosition;
import com.scorestv.football.web.dto.TeamDetailResponse.StatisticsByLeague;
import com.scorestv.football.web.dto.TeamDetailResponse.TransferRow;
import com.scorestv.football.web.dto.TeamDetailResponse.TrophyEntry;
import com.scorestv.football.web.dto.TeamDetailResponse.VenueInfo;
import com.scorestv.football.web.dto.TeamSeoResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Takim detay sayfasinin yanitini ({@link TeamDetailResponse}) uretir.
 *
 * <p>{@link com.scorestv.football.league.LeagueDetailService} ile ayni desen:
 * <ul>
 *   <li>{@code getById} (cache yok, tx yok) → lazy sync ensure + self proxy cagrisi</li>
 *   <li>{@code loadCachedResponse} (Cacheable + readOnly tx) — gercek DB okumasi</li>
 *   <li>{@code @Lazy TeamDetailService self} ile proxy advice'lari devreye girer</li>
 * </ul>
 *
 * <p>Cache TTL: LIVE (15sn). Squad/transfers gunluk yenilenir; statistics
 * sezon icinde canli oldugundan kisa cache sezonu yansitir.
 */
@Service
public class TeamDetailService {

    private static final int RECENT_FIXTURES_LIMIT = 20;
    private static final int UPCOMING_FIXTURES_LIMIT = 10;
    private static final int TRANSFERS_LIMIT = 30;

    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final CountryRepository countryRepository;
    private final TeamSquadRepository squadRepository;
    private final TransferRepository transferRepository;
    private final TeamStatisticsRepository statsRepository;
    private final StandingRepository standingRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerSidelinedRepository sidelinedRepository;
    private final PlayerSeasonStatRepository playerStatRepository;
    private final CoachRepository coachRepository;
    private final CoachCareerRepository coachCareerRepository;
    private final CoachTrophyRepository coachTrophyRepository;

    private final TeamDetailLazySync lazySync;
    private final TeamDetailSeoBuilder seoBuilder;
    private final FootballMessages messages;
    private final MinioStorageService storage;
    private final PlayerPhotoResolver photoResolver;

    private final TeamDetailService self;

    public TeamDetailService(TeamRepository teamRepository,
                             LeagueRepository leagueRepository,
                             CountryRepository countryRepository,
                             TeamSquadRepository squadRepository,
                             TransferRepository transferRepository,
                             TeamStatisticsRepository statsRepository,
                             StandingRepository standingRepository,
                             FixtureRepository fixtureRepository,
                             PlayerSidelinedRepository sidelinedRepository,
                             PlayerSeasonStatRepository playerStatRepository,
                             CoachRepository coachRepository,
                             CoachCareerRepository coachCareerRepository,
                             CoachTrophyRepository coachTrophyRepository,
                             TeamDetailLazySync lazySync,
                             TeamDetailSeoBuilder seoBuilder,
                             FootballMessages messages,
                             MinioStorageService storage,
                             PlayerPhotoResolver photoResolver,
                             @Lazy TeamDetailService self) {
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.squadRepository = squadRepository;
        this.transferRepository = transferRepository;
        this.statsRepository = statsRepository;
        this.standingRepository = standingRepository;
        this.fixtureRepository = fixtureRepository;
        this.sidelinedRepository = sidelinedRepository;
        this.playerStatRepository = playerStatRepository;
        this.coachRepository = coachRepository;
        this.coachCareerRepository = coachCareerRepository;
        this.coachTrophyRepository = coachTrophyRepository;
        this.lazySync = lazySync;
        this.seoBuilder = seoBuilder;
        this.messages = messages;
        this.storage = storage;
        this.photoResolver = photoResolver;
        this.self = self;
    }

    /**
     * Takim detay endpoint'inin public girisi.
     *
     * @param teamId          takim id
     * @param requestedSeason istenen sezon (null → takimin son sezonu)
     * @param turkish         "tr" → Turkce; aksi halde EN
     */
    public TeamDetailResponse getById(Long teamId, Integer requestedSeason, boolean turkish) {
        // Takimin GERCEK current sezonu — kullanicinin sectigi ile karistirma.
        // Once DB'de fixtures'i bulunan en son sezon, yoksa /leagues?team=X
        // sonucu (cache + opsiyonel API call).
        Integer teamCurrentSeason = resolveTeamCurrentSeason(teamId);
        lazySync.ensureFor(teamId, requestedSeason, teamCurrentSeason);
        return self.loadCachedResponse(teamId, requestedSeason, turkish);
    }

    /**
     * Takimin gercek current sezonunu cozer. Once DB'deki en son fixture
     * sezonu; yoksa lazy sync'in {@code /leagues?team=X} sonucu (cache veya
     * API call). Kullanicinin {@code ?season=} ile istedigi yil DEGIL.
     */
    private Integer resolveTeamCurrentSeason(Long teamId) {
        List<Integer> dbSeasons = fixtureRepository.findSeasonYearsByTeam(teamId);
        if (!dbSeasons.isEmpty()) {
            return dbSeasons.get(0);
        }
        return lazySync.getOrDiscoverCurrentSeason(teamId);
    }

    /** Cache'li okuma — yalniz {@link #getById}'den self proxy ile cagrilir.
     *
     * <p>{@code unless}: lazy sync henuz tamamlanmamissa cevap cache'lenmez —
     * ana moduller (statistics + squad + fixtures) HEPSI bossa "thin" sayilir.
     * Aksi halde yarisi-dolu cevap 15sn boyunca takili kalir; kullanici
     * stale data gorur.
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'team-' + #teamId + '-' + (#season == null ? 'cur' : #season) + '-' + (#turkish ? 'tr' : 'en')",
            unless = "#result == null || ("
                + "#result.statistics().isEmpty() "
                + "&& #result.squad().isEmpty() "
                + "&& #result.recentFixtures().isEmpty() "
                + "&& #result.upcomingFixtures().isEmpty())")
    @Transactional(readOnly = true)
    public TeamDetailResponse loadCachedResponse(Long teamId, Integer season, boolean turkish) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi."));

        // DB'de fixtures'i bulunan sezonlar (gercek senkron edilenler)
        List<Integer> dbSeasonYears = fixtureRepository.findSeasonYearsByTeam(teamId);
        // /leagues?team=X cache'inden gelen TUM tarihi sezonlar (dropdown icin)
        List<Integer> discoveredYears = lazySync.getDiscoveredSeasons(teamId);
        // Birlesim — kullanici DB'de fixtures yokken de eski sezonlari secebilir
        java.util.Set<Integer> union = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        union.addAll(dbSeasonYears);
        union.addAll(discoveredYears);
        List<Integer> seasonYears = new ArrayList<>(union);
        Integer selectedSeason = season != null
                ? season
                : (seasonYears.isEmpty() ? null : seasonYears.get(0));

        Country country = (team.getCountry() != null)
                ? countryRepository.findByName(team.getCountry()).orElse(null)
                : null;

        String displayName = displayName(team, turkish);
        String slug = SlugUtil.teamSlug(displayName, team.getId());

        List<SeasonOption> seasons = seasonYears.stream()
                .map(y -> new SeasonOption(y, null, null,
                        selectedSeason != null && selectedSeason.equals(y)))
                .toList();

        List<StatisticsByLeague> statistics = loadStatistics(teamId, selectedSeason, turkish);
        List<SquadGroup> squad = loadSquad(teamId, selectedSeason, turkish);
        List<FixtureSummary> recent = loadRecentFixtures(teamId, turkish);
        List<FixtureSummary> upcoming = loadUpcomingFixtures(teamId, turkish);
        List<StandingsPosition> standings = loadStandingsPositions(teamId, selectedSeason, turkish);
        List<TransferRow> transfers = loadTransfers(teamId, turkish);
        CoachInfo currentCoach = loadCurrentCoach(teamId, turkish);
        List<SidelinedRow> sidelined = loadSidelinedActive(squad, turkish);
        List<PlayerSeasonStatView> playerStats = loadPlayerSeasonStats(
                teamId, selectedSeason, squad, turkish);

        TeamSeoResponse seo = seoBuilder.build(
                team, country, selectedSeason, displayName, turkish ? "tr" : "en");

        return new TeamDetailResponse(
                team.getId(),
                slug,
                displayName,
                team.getName(),
                team.getLogoKey() != null ? storage.publicUrl(team.getLogoKey()) : null,
                team.getFounded(),
                team.isNational(),
                team.getCode(),
                toCountryInfo(country, team, turkish),
                toVenueInfo(team.getVenue(), turkish),
                selectedSeason,
                seasons,
                statistics,
                squad,
                recent,
                upcoming,
                standings,
                transfers,
                currentCoach,
                sidelined,
                playerStats,
                seo);
    }

    /**
     * Mevcut bas antrenor — CoachesSyncService.syncByTeam tarafindan dogru
     * coach'a {@code current_team_id = teamId} yazilmis olur. Burada o coach'u
     * cekip kariyer (bu takimdaki donemleri) + kupalarla doldururuz.
     */
    private CoachInfo loadCurrentCoach(Long teamId, boolean turkish) {
        Coach coach = coachRepository.findByCurrentTeamId(teamId).orElse(null);
        if (coach == null) return null;
        List<CoachCareer> careerWithTeam =
                coachCareerRepository.findByCoachIdAndTeamId(coach.getId(), teamId);
        List<CoachTrophy> trophies =
                coachTrophyRepository.findByCoachIdOrderBySeason(coach.getId());

        List<CareerEntry> careerEntries = careerWithTeam.stream()
                .map(c -> new CareerEntry(c.getStartDate(), c.getEndDate()))
                .toList();
        List<TrophyEntry> trophyEntries = trophies.stream()
                .map(t -> {
                    String leagueText = resolveLeagueText(
                            t.getLeague(), t.getCountry(), turkish);
                    String countryText = resolveCountryText(t.getCountry(), turkish);
                    return new TrophyEntry(
                            t.getLeague(), leagueText,
                            t.getCountry(), countryText,
                            t.getSeason(),
                            t.getPlace(), messages.trophyPlace(t.getPlace(), turkish));
                })
                .toList();

        BirthInfo birth = null;
        if (coach.getBirthDate() != null || coach.getBirthPlace() != null
                || coach.getBirthCountry() != null) {
            birth = new BirthInfo(coach.getBirthDate(), coach.getBirthPlace(),
                    coach.getBirthCountry());
        }
        String photo = coach.getPhotoKey() != null
                ? storage.publicUrl(coach.getPhotoKey())
                : coach.getPhotoUrl();
        return new CoachInfo(
                coach.getId(),
                coach.getName(),
                coach.getFirstname(),
                coach.getLastname(),
                coach.getAge(),
                coach.getNationality(),
                photo,
                birth,
                careerEntries,
                trophyEntries);
    }

    /**
     * Oyuncu sezonluk istatistikleri — DB'den (player, league) bazinda satir
     * satir cikartilir. Foto'lar PlayerPhotoResolver ile cozulur (kadrodan
     * gelmiyorsa Player master'dan). Pozisyon stats JSONB icinden gelir.
     */
    private List<PlayerSeasonStatView> loadPlayerSeasonStats(
            Long teamId, Integer season, List<SquadGroup> squad, boolean turkish) {
        if (season == null) return List.of();
        List<PlayerSeasonStat> rows = playerStatRepository.findByTeamIdAndSeason(teamId, season);
        if (rows.isEmpty()) return List.of();

        // Foto haritalama: squad'tan zaten cozulmus foto'lari yakala, eksikler
        // icin PlayerPhotoResolver ile master'dan al.
        Map<Long, String> squadPhotos = new HashMap<>();
        Map<Long, String> squadNames = new HashMap<>();
        if (squad != null) {
            for (SquadGroup g : squad) {
                for (SquadPlayer p : g.players()) {
                    if (p.playerId() != null) {
                        squadPhotos.put(p.playerId(), p.photo());
                        squadNames.put(p.playerId(), p.name());
                    }
                }
            }
        }
        Set<Long> playerIds = new HashSet<>();
        for (PlayerSeasonStat s : rows) playerIds.add(s.getPlayerId());
        Map<Long, Player> photoMap = photoResolver.loadMap(playerIds);

        List<PlayerSeasonStatView> out = new ArrayList<>(rows.size());
        for (PlayerSeasonStat s : rows) {
            League lg = s.getLeague();
            String lname = displayName(lg, turkish);
            // Pozisyon: stats_json icindeki games.position
            String rawPosition = null;
            Map<String, Object> stats = s.getStatsJson();
            if (stats != null && stats.get("games") instanceof Map<?, ?> gamesMap) {
                Object pos = gamesMap.get("position");
                if (pos instanceof String posStr) rawPosition = posStr;
            }
            // Oyuncu adi: master'dan; yoksa squad'tan; yoksa player_id
            Player master = photoMap.get(s.getPlayerId());
            String playerName = master != null ? master.getName()
                    : squadNames.getOrDefault(s.getPlayerId(), "Player#" + s.getPlayerId());
            String photo = photoResolver.photoUrl(
                    photoMap, s.getPlayerId(), squadPhotos.get(s.getPlayerId()));

            out.add(new PlayerSeasonStatView(
                    s.getPlayerId(),
                    playerName,
                    photo,
                    lg.getId(),
                    lname,
                    lg.getLogoKey() != null ? storage.publicUrl(lg.getLogoKey()) : null,
                    SlugUtil.leagueSlug(lname, lg.getId()),
                    rawPosition,
                    rawPosition == null ? null : messages.playerPosition(rawPosition, turkish),
                    stats));
        }
        return out;
    }


    private CountryInfo toCountryInfo(Country country, Team team, boolean turkish) {
        if (country == null && (team.getCountry() == null || team.getCountry().isBlank())) {
            return null;
        }
        String name = (turkish && country != null && country.getNameTr() != null
                && !country.getNameTr().isBlank())
                ? country.getNameTr()
                : (country != null ? country.getName() : team.getCountry());
        String code = country != null ? country.getCode() : null;
        String flag = (country != null && country.getFlagKey() != null)
                ? storage.publicUrl(country.getFlagKey())
                : null;
        return new CountryInfo(name, code, flag);
    }

    private VenueInfo toVenueInfo(Venue venue, boolean turkish) {
        if (venue == null) return null;
        String name = (turkish && venue.getNameTr() != null && !venue.getNameTr().isBlank())
                ? venue.getNameTr()
                : venue.getName();
        return new VenueInfo(
                venue.getId(),
                name,
                venue.getCity(),
                venue.getAddress(),
                venue.getCapacity(),
                messages.surface(venue.getSurface(), turkish),
                venue.getImageKey() != null ? storage.publicUrl(venue.getImageKey()) : null);
    }

    /** Statistics — secili sezonda takimin oynadigi tum ligler icin JSONB passthrough. */
    private List<StatisticsByLeague> loadStatistics(Long teamId, Integer season, boolean turkish) {
        if (season == null) return List.of();
        List<TeamStatistics> rows = statsRepository.findByTeamIdAndSeasonWithLeague(teamId, season);
        if (rows.isEmpty()) return List.of();
        List<StatisticsByLeague> out = new ArrayList<>(rows.size());
        for (TeamStatistics row : rows) {
            League lg = row.getLeague();
            String lname = displayName(lg, turkish);
            out.add(new StatisticsByLeague(
                    lg.getId(),
                    lname,
                    lg.getLogoKey() != null ? storage.publicUrl(lg.getLogoKey()) : null,
                    SlugUtil.leagueSlug(lname, lg.getId()),
                    row.getStatsJson()));
        }
        return out;
    }

    /** Kadro — pozisyon bazli grupli + foto resolved. */
    private List<SquadGroup> loadSquad(Long teamId, Integer season, boolean turkish) {
        if (season == null) return List.of();
        List<TeamSquad> rows = squadRepository.findByTeamIdAndSeason(teamId, season);
        if (rows.isEmpty()) return List.of();

        Set<Long> playerIds = new HashSet<>();
        for (TeamSquad s : rows) if (s.getPlayerId() != null) playerIds.add(s.getPlayerId());
        Map<Long, Player> photos = photoResolver.loadMap(playerIds);

        Map<String, List<SquadPlayer>> byPos = new LinkedHashMap<>();
        // Tutarli sira icin onerilen pozisyon ordering
        for (String p : new String[] {"Goalkeeper", "Defender", "Midfielder", "Attacker"}) {
            byPos.put(p, new ArrayList<>());
        }
        for (TeamSquad s : rows) {
            String pos = s.getPosition() == null ? "Other" : s.getPosition();
            byPos.computeIfAbsent(pos, k -> new ArrayList<>())
                    .add(new SquadPlayer(
                            s.getPlayerId(),
                            s.getPlayerName(),
                            s.getPlayerAge(),
                            s.getJerseyNumber(),
                            pos,
                            photoResolver.photoUrl(photos, s.getPlayerId(), null)));
        }
        List<SquadGroup> out = new ArrayList<>(byPos.size());
        for (Map.Entry<String, List<SquadPlayer>> e : byPos.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            // Numara bazli sirala (numarasiz son)
            e.getValue().sort(Comparator.comparing(
                    SquadPlayer::number, Comparator.nullsLast(Comparator.naturalOrder())));
            out.add(new SquadGroup(
                    e.getKey(),
                    messages.playerPosition(e.getKey(), turkish),
                    e.getValue()));
        }
        return out;
    }

    private List<FixtureSummary> loadRecentFixtures(Long teamId, boolean turkish) {
        List<Fixture> rows = fixtureRepository.findRecentByTeam(
                teamId, PageRequest.of(0, RECENT_FIXTURES_LIMIT));
        return rows.stream().map(f -> toFixtureSummary(f, turkish)).toList();
    }

    private List<FixtureSummary> loadUpcomingFixtures(Long teamId, boolean turkish) {
        List<Fixture> rows = fixtureRepository.findUpcomingByTeam(
                teamId, PageRequest.of(0, UPCOMING_FIXTURES_LIMIT));
        return rows.stream().map(f -> toFixtureSummary(f, turkish)).toList();
    }

    private FixtureSummary toFixtureSummary(Fixture f, boolean turkish) {
        Team home = f.getHomeTeam();
        Team away = f.getAwayTeam();
        String homeName = displayName(home, turkish);
        String awayName = displayName(away, turkish);
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
                        home.getId(), homeName,
                        home.getLogoKey() != null ? storage.publicUrl(home.getLogoKey()) : null,
                        SlugUtil.teamSlug(homeName, home.getId())),
                new FixtureSummary.Team(
                        away.getId(), awayName,
                        away.getLogoKey() != null ? storage.publicUrl(away.getLogoKey()) : null,
                        SlugUtil.teamSlug(awayName, away.getId())),
                new FixtureSummary.Score(f.getHomeGoals(), f.getAwayGoals()),
                toVenueSummary(f, turkish));
    }

    /**
     * Fikstur ozetinde venue: FK Venue varsa (name_tr destekli), yoksa
     * inline {@code venueName/venueCity} fallback (UEFA CL gibi venue id'siz
     * maclar icin). Ikisi de yoksa null.
     */
    private FixtureSummary.Venue toVenueSummary(Fixture f, boolean turkish) {
        Venue v = f.getVenue();
        if (v != null) {
            return new FixtureSummary.Venue(displayName(v, turkish), v.getCity());
        }
        if (f.getVenueName() != null && !f.getVenueName().isBlank()) {
            return new FixtureSummary.Venue(f.getVenueName(), f.getVenueCity());
        }
        return null;
    }

    /** Standings position — takimin yer aldigi her ligde tek satir. */
    private List<StandingsPosition> loadStandingsPositions(Long teamId, Integer season, boolean turkish) {
        if (season == null) return List.of();
        List<Standing> rows = standingRepository.findByTeamIdAndSeason(teamId, season);
        if (rows.isEmpty()) return List.of();
        // Her lig icin "multi-group mu?" sorgusunu cache'le (tek lig + tek
        // satir geldiginden her satirda yeniden sorma).
        Map<Long, Boolean> multiGroupByLeague = new HashMap<>();
        for (Standing s : rows) {
            Long lid = s.getLeague().getId();
            multiGroupByLeague.computeIfAbsent(lid, k ->
                    standingRepository.countDistinctGroupNamesByLeagueAndSeason(k, season) > 1);
        }

        List<StandingsPosition> out = new ArrayList<>(rows.size());
        for (Standing s : rows) {
            League lg = s.getLeague();
            String lname = displayName(lg, turkish);
            // Tek-grup ligde (ulusal lig — "Super Lig " gibi) groupName UI'a
            // anlamsiz, suppress. Cok grupluda korunur (CL grup asamasi vb.).
            boolean multiGroup = multiGroupByLeague.getOrDefault(lg.getId(), false);
            String rawGroup = (multiGroup && s.getGroupName() != null)
                    ? s.getGroupName().trim()
                    : null;
            String groupTranslated = rawGroup == null
                    ? null
                    : messages.standingGroupName(rawGroup, turkish);
            out.add(new StandingsPosition(
                    lg.getId(),
                    lname,
                    lg.getLogoKey() != null ? storage.publicUrl(lg.getLogoKey()) : null,
                    SlugUtil.leagueSlug(lname, lg.getId()),
                    rawGroup,
                    groupTranslated,
                    s.getRank(),
                    s.getPoints(),
                    s.getGoalsDiff(),
                    s.getPlayed(),
                    s.getWin(),
                    s.getDraw(),
                    s.getLose(),
                    s.getForm(),
                    s.getDescription(),
                    messages.standingDescription(s.getDescription(), turkish)));
        }
        return out;
    }

    /** Transferler — son N hareket, in/out belirleyip karsi takimi cikar. */
    private List<TransferRow> loadTransfers(Long teamId, boolean turkish) {
        List<Transfer> rows = transferRepository.findByTeam(
                teamId, PageRequest.of(0, TRANSFERS_LIMIT));
        if (rows.isEmpty()) return List.of();

        // Karsi takim slug uretmek icin (yalnizca ham name varsa name'den slug uret).
        // DB'de karsi takim entity yoksa logo URL'i ham olur (logoUrl); slug yine de
        // teamId varsa olusturulabilir.
        List<TransferRow> out = new ArrayList<>(rows.size());
        for (Transfer t : rows) {
            String direction;
            Long counterId;
            String counterName;
            String counterLogo;
            if (Objects.equals(t.getInTeamId(), teamId)) {
                direction = "in";
                counterId = t.getOutTeamId();
                counterName = t.getOutTeamName();
                counterLogo = t.getOutTeamLogo();
            } else {
                direction = "out";
                counterId = t.getInTeamId();
                counterName = t.getInTeamName();
                counterLogo = t.getInTeamLogo();
            }
            String counterSlug = (counterId != null && counterName != null)
                    ? SlugUtil.teamSlug(counterName, counterId)
                    : null;
            out.add(new TransferRow(
                    t.getTransferDate(),
                    direction,
                    t.getTransferType(),
                    messages.transferType(t.getTransferType(), turkish),
                    t.getPlayerId(),
                    t.getPlayerName(),
                    counterId,
                    counterName,
                    counterLogo,
                    counterSlug));
        }
        return out;
    }

    /** Aktif sakatlar — kadrodaki oyunculardan {@code end_date >= today} olanlar. */
    private List<SidelinedRow> loadSidelinedActive(List<SquadGroup> squad, boolean turkish) {
        if (squad == null || squad.isEmpty()) return List.of();
        // Oyuncu id'leri kadrodan cikar
        Set<Long> playerIds = new HashSet<>();
        Map<Long, SquadPlayer> playerById = new HashMap<>();
        for (SquadGroup g : squad) {
            for (SquadPlayer p : g.players()) {
                if (p.playerId() != null) {
                    playerIds.add(p.playerId());
                    playerById.put(p.playerId(), p);
                }
            }
        }
        if (playerIds.isEmpty()) return List.of();
        List<PlayerSidelined> rows = sidelinedRepository.findActiveForPlayers(
                playerIds, LocalDate.now());
        if (rows.isEmpty()) return List.of();
        List<SidelinedRow> out = new ArrayList<>(rows.size());
        for (PlayerSidelined s : rows) {
            SquadPlayer p = playerById.get(s.getPlayerId());
            String pName = p != null ? p.name() : null;
            String pPhoto = p != null ? p.photo() : null;
            // NOT: /sidelined endpoint'inde "type" alani aslinda sakatlik
            // SEBEBI (orn. "Knee Injury") — injuries'taki type (Missing
            // Fixture / Questionable) degil. Bu yuzden injuryReason() ile
            // cevirir; akilli parser bilinmeyen kombinasyonlari da TR'ye
            // ceker ("Knee Injury" → "Diz Sakatligi").
            out.add(new SidelinedRow(
                    s.getPlayerId(),
                    pName,
                    pPhoto,
                    s.getType(),
                    messages.injuryReason(s.getType(), turkish),
                    s.getStartDate(),
                    s.getEndDate()));
        }
        return out;
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

    /**
     * Trophy.league (ham "Bundesliga", "Cup", "UEFA Champions League") icin
     * cevirili karsiligi cozer. DB'de (name, country) ile match varsa name_tr,
     * yoksa kaynak metin. Bilinmeyen lig adi (API hala "Cup" gibi belirsiz
     * isimler veriyor) kaynak metin doner — frontend bozulmaz.
     */
    private String resolveLeagueText(String leagueName, String countryName, boolean turkish) {
        if (leagueName == null || leagueName.isBlank()) return leagueName;
        if (!turkish) return leagueName;
        if (countryName == null) return leagueName;
        return leagueRepository.findFirstByNameIgnoreCaseAndCountryNameIgnoreCase(
                        leagueName, countryName)
                .map(lg -> lg.getNameTr() != null && !lg.getNameTr().isBlank()
                        ? lg.getNameTr() : lg.getName())
                .orElse(leagueName);
    }

    /**
     * Trophy.country (ham "Turkey", "Germany") icin cevirili karsiligi cozer.
     * countries tablosunda name match → name_tr; yoksa kaynak metin.
     */
    private String resolveCountryText(String countryName, boolean turkish) {
        if (countryName == null || countryName.isBlank()) return countryName;
        if (!turkish) return countryName;
        return countryRepository.findByName(countryName)
                .map(c -> c.getNameTr() != null && !c.getNameTr().isBlank()
                        ? c.getNameTr() : c.getName())
                .orElse(countryName);
    }
}
