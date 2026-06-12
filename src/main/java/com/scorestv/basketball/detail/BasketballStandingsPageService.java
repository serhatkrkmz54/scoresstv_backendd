package com.scorestv.basketball.detail;

import com.scorestv.basketball.BasketballStandingsSyncService;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballSeason;
import com.scorestv.basketball.domain.BasketballSeasonRepository;
import com.scorestv.basketball.domain.BasketballStanding;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse.LeagueHero;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse.StandingRow;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse.StandingsGroup;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse.TeamRef;
import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Basketbol puan durumu sayfasi servisi — lean lazy sync paterni.
 *
 * <p>Futbol {@code StandingsPageService}'in esi: aci kez detay ekraninda yan
 * modullerle ugrasmaz, sadece standings'i tazeler. 1 saat freshness penceresi
 * (API saatlik) ile.
 *
 * <p>Cache: 5 dakika TTL — kullanici sezon degistirince yeni cache key oluyor.
 */
@Service
public class BasketballStandingsPageService {

    private static final Logger log = LoggerFactory.getLogger(BasketballStandingsPageService.class);

    private static final String CACHE_NAME = "BASKETBALL_STANDINGS_PAGE";
    private static final Duration FRESHNESS = Duration.ofHours(1);

    private final BasketballLeagueRepository leagueRepo;
    private final BasketballStandingRepository standingRepo;
    private final BasketballSeasonRepository seasonRepo;
    private final BasketballGameRepository gameRepo;
    private final BasketballStandingsSyncService syncService;
    private final BasketballStandingsPageService self;

    public BasketballStandingsPageService(BasketballLeagueRepository leagueRepo,
                                           BasketballStandingRepository standingRepo,
                                           BasketballSeasonRepository seasonRepo,
                                           BasketballGameRepository gameRepo,
                                           BasketballStandingsSyncService syncService,
                                           @Lazy BasketballStandingsPageService self) {
        this.leagueRepo = leagueRepo;
        this.standingRepo = standingRepo;
        this.seasonRepo = seasonRepo;
        this.gameRepo = gameRepo;
        this.syncService = syncService;
        this.self = self;
    }

    /**
     * Slug + opsiyonel sezon. Sezon yoksa default = en yeni sezon (basketball_games
     * tablosundan).
     */
    public BasketballStandingsPageResponse getBySlug(String slug, String season, boolean turkish) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId == null) throw new ApiException(404, "Lig bulunamadi");

        BasketballLeague league = leagueRepo.findById(leagueId)
                .orElseThrow(() -> new ApiException(404, "Lig bulunamadi"));

        // Sezon karari
        String resolvedSeason = season != null && !season.isBlank()
                ? season
                : pickDefaultSeason(leagueId);
        if (resolvedSeason == null) {
            // Hic sezon yok — bos sayfa doner
            return emptyPage(league, turkish);
        }

        // Lean lazy sync: standings yoksa veya 1sa eskimisse cek
        ensureFresh(leagueId, resolvedSeason);

        return self.loadCached(leagueId, resolvedSeason, turkish);
    }

    /** Force refresh — cache evict edip taze veri doner. */
    public BasketballStandingsPageResponse forceRefresh(String slug, String season,
                                                         boolean turkish) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId == null) throw new ApiException(404, "Lig bulunamadi");
        BasketballLeague league = leagueRepo.findById(leagueId)
                .orElseThrow(() -> new ApiException(404, "Lig bulunamadi"));
        String resolvedSeason = season != null && !season.isBlank()
                ? season : pickDefaultSeason(leagueId);
        if (resolvedSeason == null) return emptyPage(league, turkish);

        self.evictCache(leagueId, resolvedSeason, turkish);
        syncService.sync(leagueId, resolvedSeason);
        return self.loadCached(leagueId, resolvedSeason, turkish);
    }

    // ================================================================
    // Internal — lazy sync + cached read
    // ================================================================

    private void ensureFresh(long leagueId, String season) {
        long count = standingRepo.countByLeagueIdAndSeason(leagueId, season);
        Instant lastSync = seasonRepo.findByLeagueIdAndSeason(leagueId, season)
                .map(BasketballSeason::getStandingsLastSyncedAt)
                .orElse(null);
        boolean stale = lastSync == null
                || Instant.now().isAfter(lastSync.plus(FRESHNESS));
        if (count == 0 || stale) {
            try {
                syncService.sync(leagueId, season);
                self.evictCache(leagueId, season, true);
                self.evictCache(leagueId, season, false);
            } catch (Exception e) {
                log.warn("Standings lean sync hatasi league={} season={}: {}",
                        leagueId, season, e.toString());
            }
        }
    }

    @Cacheable(
            value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#leagueId, #season, #turkish)"
    )
    @Transactional(readOnly = true)
    public BasketballStandingsPageResponse loadCached(Long leagueId, String season,
                                                       boolean turkish) {
        BasketballLeague league = leagueRepo.findById(leagueId)
                .orElseThrow(() -> new ApiException(404, "Lig bulunamadi"));
        return build(league, season, turkish);
    }

    @CacheEvict(
            value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#leagueId, #season, #turkish)"
    )
    public void evictCache(Long leagueId, String season, boolean turkish) {
        // body bos — evict
    }

    // ================================================================
    // DTO build
    // ================================================================

    private BasketballStandingsPageResponse build(BasketballLeague league, String season,
                                                    boolean turkish) {
        LeagueHero hero = mapHero(league, turkish);
        List<String> seasons = listAvailableSeasons(league.getId());
        List<StandingsGroup> groups = mapGroups(league.getId(), season, turkish);
        Instant lastSync = seasonRepo.findByLeagueIdAndSeason(league.getId(), season)
                .map(BasketballSeason::getStandingsLastSyncedAt)
                .orElse(null);
        return new BasketballStandingsPageResponse(hero, season, seasons, groups, lastSync);
    }

    private LeagueHero mapHero(BasketballLeague l, boolean turkish) {
        String displayName = turkish && l.getNameTr() != null && !l.getNameTr().isBlank()
                ? l.getNameTr() : l.getName();
        return new LeagueHero(
                l.getId(),
                l.getName(),
                displayName,
                l.getType(),
                SlugUtil.leagueSlug(displayName, l.getId()),
                l.getLogo(),
                l.getCountryName(),
                l.getCountryFlag()
        );
    }

    /** Bu ligin tum sezonlari — standings tablosundaki distinct sezonlar (yeni → eski). */
    private List<String> listAvailableSeasons(long leagueId) {
        return standingRepo.findDistinctSeasonsByLeagueId(leagueId);
    }

    /**
     * Default sezon karari (cold-start dahil):
     * 1) standings'te en yeni sezon
     * 2) games tablosunda en yeni sezon (fallback)
     * 3) ikisi de bossa null (frontend "Henuz veri yok" gosterir)
     */
    private String pickDefaultSeason(long leagueId) {
        var fromStandings = standingRepo.findDistinctSeasonsByLeagueId(leagueId);
        if (!fromStandings.isEmpty()) return fromStandings.get(0);
        var fromGames = gameRepo.findDistinctSeasonsByLeagueId(leagueId);
        return fromGames.isEmpty() ? null : fromGames.get(0);
    }

    private List<StandingsGroup> mapGroups(long leagueId, String season, boolean turkish) {
        var rows = standingRepo.findByLeagueAndSeason(leagueId, season);
        if (rows.isEmpty()) return List.of();
        Map<String, List<StandingRow>> byGroup = new TreeMap<>();
        Map<String, String> stageByGroup = new TreeMap<>();
        for (BasketballStanding s : rows) {
            String gname = s.getGroupName() == null ? "" : s.getGroupName();
            byGroup.computeIfAbsent(gname, k -> new ArrayList<>())
                    .add(new StandingRow(
                            s.getPosition(),
                            mapTeamRef(s.getTeam(), turkish),
                            s.getGamesPlayedAll(),
                            s.getWonAll(),
                            s.getLostAll(),
                            s.getWonPercentage(),
                            s.getPointsFor(),
                            s.getPointsAgainst(),
                            (s.getPointsFor() != null && s.getPointsAgainst() != null)
                                    ? (s.getPointsFor() - s.getPointsAgainst()) : null,
                            s.getForm(),
                            s.getDescription()
                    ));
            stageByGroup.putIfAbsent(gname, s.getStage());
        }
        List<StandingsGroup> out = new ArrayList<>(byGroup.size());
        for (Map.Entry<String, List<StandingRow>> e : byGroup.entrySet()) {
            out.add(new StandingsGroup(e.getKey(), stageByGroup.get(e.getKey()), e.getValue()));
        }
        return out;
    }

    private TeamRef mapTeamRef(BasketballTeam t, boolean turkish) {
        if (t == null) return null;
        String displayName = turkish && t.getNameTr() != null && !t.getNameTr().isBlank()
                ? t.getNameTr() : t.getName();
        return new TeamRef(
                t.getId(),
                t.getName(),
                displayName,
                t.getLogo(),
                SlugUtil.teamSlug(displayName, t.getId())
        );
    }

    private BasketballStandingsPageResponse emptyPage(BasketballLeague league, boolean turkish) {
        return new BasketballStandingsPageResponse(
                mapHero(league, turkish),
                null,
                List.of(),
                List.of(),
                null
        );
    }
}
