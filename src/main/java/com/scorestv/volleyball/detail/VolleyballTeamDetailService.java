package com.scorestv.volleyball.detail;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.VolleyballMessages;
import com.scorestv.volleyball.VolleyballTeamProfileSyncService;
import com.scorestv.volleyball.VolleyballTeamStatisticsSyncService;
import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballStanding;
import com.scorestv.volleyball.domain.VolleyballStandingRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStat;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStatRepository;
import com.scorestv.volleyball.web.dto.VolleyballTeamDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Voleybol takim detay sayfasi servisi — basketbol team detail'in voleybol esi,
 * LEANER. Profil + sezon istatistikleri + son/yaklasan maclar + standings satiri.
 */
@Service
public class VolleyballTeamDetailService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballTeamDetailService.class);

    private final VolleyballTeamRepository teamRepo;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballGameRepository gameRepo;
    private final VolleyballStandingRepository standingRepo;
    private final VolleyballTeamSeasonStatRepository statRepo;
    private final VolleyballTeamProfileSyncService profileSync;
    private final VolleyballTeamStatisticsSyncService statsSync;
    private final MinioStorageService storage;
    private final VolleyballMessages messages;

    public VolleyballTeamDetailService(VolleyballTeamRepository teamRepo,
                                       VolleyballLeagueRepository leagueRepo,
                                       VolleyballGameRepository gameRepo,
                                       VolleyballStandingRepository standingRepo,
                                       VolleyballTeamSeasonStatRepository statRepo,
                                       VolleyballTeamProfileSyncService profileSync,
                                       VolleyballTeamStatisticsSyncService statsSync,
                                       MinioStorageService storage,
                                       VolleyballMessages messages) {
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.gameRepo = gameRepo;
        this.standingRepo = standingRepo;
        this.statRepo = statRepo;
        this.profileSync = profileSync;
        this.statsSync = statsSync;
        this.storage = storage;
        this.messages = messages;
    }

    @Transactional
    public VolleyballTeamDetailResponse getBySlug(String slug, String season, boolean turkish) {
        return build(resolveTeamId(slug), season, turkish, false);
    }

    @Transactional
    public VolleyballTeamDetailResponse forceRefresh(String slug, String season, boolean turkish) {
        return build(resolveTeamId(slug), season, turkish, true);
    }

    private Long resolveTeamId(String slug) {
        Long id = SlugUtil.extractGameId(slug); // generic trailing-id extractor
        if (id != null && teamRepo.existsById(id)) return id;
        return teamRepo.findBySlug(slug)
                .map(VolleyballTeam::getId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi"));
    }

    private VolleyballTeamDetailResponse build(Long teamId, String season,
                                               boolean turkish, boolean force) {
        VolleyballTeam team = teamRepo.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi"));

        // Profil lazy sync (freshness gate icinde).
        try {
            profileSync.syncProfile(teamId, force);
        } catch (Exception e) {
            log.debug("Voleybol team profile lazy sync hata id={}: {}", teamId, e.toString());
        }

        // Lig + sezon cifti coz — verilmediyse en guncel oynanan.
        Long leagueId = null;
        String resolvedSeason = season;
        var pairs = gameRepo.findTeamLeagueSeasonPairs(teamId, PageRequest.of(0, 1));
        if (!pairs.isEmpty()) {
            Object[] p = pairs.get(0);
            leagueId = ((Number) p[0]).longValue();
            if (resolvedSeason == null || resolvedSeason.isBlank()) {
                resolvedSeason = (String) p[1];
            }
        }

        // Sezon istatistikleri lazy sync + oku.
        VolleyballTeamDetailResponse.SeasonStats seasonStats = null;
        if (leagueId != null && resolvedSeason != null) {
            try {
                statsSync.sync(teamId, leagueId, resolvedSeason, force);
            } catch (Exception e) {
                log.debug("Voleybol team stats lazy sync hata team={} league={}: {}",
                        teamId, leagueId, e.toString());
            }
            seasonStats = statRepo
                    .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, resolvedSeason)
                    .map(s -> mapStats(s, turkish))
                    .orElse(null);
        }

        List<VolleyballTeamDetailResponse.GameRef> recent = mapGames(
                gameRepo.findRecentByTeam(teamId, leagueId, resolvedSeason,
                        PageRequest.of(0, 10)), turkish);
        List<VolleyballTeamDetailResponse.GameRef> upcoming = mapGames(
                gameRepo.findUpcomingByTeam(teamId, leagueId, resolvedSeason,
                        PageRequest.of(0, 10)), turkish);

        List<VolleyballTeamDetailResponse.StandingRow> standings = new ArrayList<>();
        if (leagueId != null && resolvedSeason != null) {
            for (VolleyballStanding s : standingRepo.findForTeam(leagueId, resolvedSeason, teamId)) {
                standings.add(new VolleyballTeamDetailResponse.StandingRow(
                        s.getPosition(),
                        messages.standingGroupName(s.getGroupName(), turkish),
                        s.getGamesPlayed(),
                        s.getWon(),
                        s.getLost(),
                        s.getSetsFor(),
                        s.getSetsAgainst(),
                        s.getPoints(),
                        s.getForm()));
            }
        }

        String displayName = turkish && team.getNameTr() != null && !team.getNameTr().isBlank()
                ? team.getNameTr() : team.getName();
        String logo = team.getLogoKey() != null ? storage.publicUrl(team.getLogoKey()) : team.getLogo();
        String flag = team.getCountryFlag();

        return new VolleyballTeamDetailResponse(
                team.getId(),
                team.getName(),
                displayName,
                SlugUtil.teamSlug(displayName, team.getId()),
                logo,
                team.getCountryName(),
                flag,
                team.isNational(),
                resolvedSeason,
                team.getUpdatedAt(),
                seasonStats,
                recent,
                upcoming,
                standings);
    }

    private VolleyballTeamDetailResponse.SeasonStats mapStats(
            VolleyballTeamSeasonStat s, boolean turkish) {
        VolleyballLeague league = s.getLeague();
        String leagueName = turkish && league.getNameTr() != null && !league.getNameTr().isBlank()
                ? league.getNameTr() : league.getName();
        return new VolleyballTeamDetailResponse.SeasonStats(
                league.getId(),
                leagueName,
                s.getGamesPlayed(),
                s.getWins(),
                s.getLoses(),
                bdToString(s.getWinPercentage()),
                s.getSetsForTotal(),
                bdToDouble(s.getSetsForAvg()),
                s.getSetsAgainstTotal(),
                bdToDouble(s.getSetsAgainstAvg()),
                s.getForm());
    }

    private List<VolleyballTeamDetailResponse.GameRef> mapGames(
            List<VolleyballGame> games, boolean turkish) {
        List<VolleyballTeamDetailResponse.GameRef> out = new ArrayList<>(games.size());
        for (VolleyballGame g : games) {
            var h = g.getHomeTeam();
            var a = g.getAwayTeam();
            String homeName = displayName(h, turkish);
            String awayName = displayName(a, turkish);
            String slug = SlugUtil.gameSlug(homeName, awayName, g.getId());
            String leagueName = g.getLeague() != null
                    ? (turkish && g.getLeague().getNameTr() != null
                        && !g.getLeague().getNameTr().isBlank()
                        ? g.getLeague().getNameTr() : g.getLeague().getName())
                    : null;
            out.add(new VolleyballTeamDetailResponse.GameRef(
                    g.getId(), slug, g.getStartAt(), g.getStatusShort(), leagueName,
                    new VolleyballTeamDetailResponse.TeamSide(h.getId(), homeName, logoUrl(h)),
                    new VolleyballTeamDetailResponse.TeamSide(a.getId(), awayName, logoUrl(a)),
                    g.getHomeTotal(), g.getAwayTotal()));
        }
        return out;
    }

    private String displayName(VolleyballTeam t, boolean turkish) {
        return turkish && t.getNameTr() != null && !t.getNameTr().isBlank()
                ? t.getNameTr() : t.getName();
    }

    private String logoUrl(VolleyballTeam t) {
        return t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : t.getLogo();
    }

    private static String bdToString(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
