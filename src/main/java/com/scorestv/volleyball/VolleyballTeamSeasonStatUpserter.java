package com.scorestv.volleyball;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStat;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link VbTeamStatisticsDto} -> {@link VolleyballTeamSeasonStat} upsert.
 *
 * <p>API yanitindaki {@code games.played.all}, {@code games.wins.all.total},
 * percentage'leri ayikla; {@code goals.for/against} (set/sayi) ortalamalarini
 * BigDecimal'a cevir; ev/deplasman breakdown'i JSONB serialize.
 */
@Component
public class VolleyballTeamSeasonStatUpserter {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballTeamSeasonStatUpserter.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final VolleyballTeamRepository teamRepo;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballTeamSeasonStatRepository statRepo;

    public VolleyballTeamSeasonStatUpserter(VolleyballTeamRepository teamRepo,
                                            VolleyballLeagueRepository leagueRepo,
                                            VolleyballTeamSeasonStatRepository statRepo) {
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.statRepo = statRepo;
    }

    public VolleyballTeamSeasonStat upsertFromDto(
            long teamId, long leagueId, String season,
            VbTeamStatisticsDto dto) {

        if (dto == null || dto.games() == null) {
            log.debug("Voleybol team stats DTO bos team={} league={} season={}",
                    teamId, leagueId, season);
            return null;
        }

        VolleyballTeam team = teamRepo.findById(teamId).orElse(null);
        VolleyballLeague league = leagueRepo.findById(leagueId).orElse(null);
        if (team == null || league == null) {
            log.warn("Voleybol team stats upsert: team={} veya league={} bulunamadi (skip)",
                    teamId, leagueId);
            return null;
        }

        VolleyballTeamSeasonStat row = statRepo
                .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season)
                .orElseGet(VolleyballTeamSeasonStat::new);
        row.setTeam(team);
        row.setLeague(league);
        row.setSeason(season);

        var games = dto.games();
        if (games.played() != null) {
            row.setGamesPlayed(games.played().all());
        }
        if (games.wins() != null && games.wins().all() != null) {
            row.setWins(games.wins().all().total());
            row.setWinPercentage(parseBd(games.wins().all().percentage()));
        }
        if (games.loses() != null && games.loses().all() != null) {
            row.setLoses(games.loses().all().total());
        }

        var goals = dto.goals();
        if (goals != null) {
            var gfor = goals.forGoals();
            if (gfor != null) {
                if (gfor.total() != null) {
                    row.setSetsForTotal(gfor.total().all());
                }
                if (gfor.average() != null) {
                    row.setSetsForAvg(parseBd(gfor.average().all()));
                }
            }
            var gagainst = goals.against();
            if (gagainst != null) {
                if (gagainst.total() != null) {
                    row.setSetsAgainstTotal(gagainst.total().all());
                }
                if (gagainst.average() != null) {
                    row.setSetsAgainstAvg(parseBd(gagainst.average().all()));
                }
            }
        }

        row.setForm(null); // form games tablosundan hesaplanir

        row.setHomeAwayJson(buildHomeAwayJson(games, goals));

        return statRepo.save(row);
    }

    private String buildHomeAwayJson(
            VbTeamStatisticsDto.Games games, VbTeamStatisticsDto.Goals goals) {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String side : new String[]{"home", "away"}) {
                Map<String, Object> b = new LinkedHashMap<>();
                if (games != null) {
                    if (games.played() != null) {
                        b.put("played", "home".equals(side)
                                ? games.played().home() : games.played().away());
                    }
                    if (games.wins() != null) {
                        var s = "home".equals(side)
                                ? games.wins().home() : games.wins().away();
                        if (s != null) {
                            b.put("wins", s.total());
                            b.put("winsPct", asNumber(s.percentage()));
                        }
                    }
                    if (games.loses() != null) {
                        var s = "home".equals(side)
                                ? games.loses().home() : games.loses().away();
                        if (s != null) b.put("loses", s.total());
                    }
                }
                if (goals != null) {
                    if (goals.forGoals() != null && goals.forGoals().average() != null) {
                        var a = goals.forGoals().average();
                        b.put("setsForAvg", asNumber(
                                "home".equals(side) ? a.home() : a.away()));
                    }
                    if (goals.against() != null && goals.against().average() != null) {
                        var a = goals.against().average();
                        b.put("setsAgainstAvg", asNumber(
                                "home".equals(side) ? a.home() : a.away()));
                    }
                }
                out.put(side, b);
            }
            return JSON_MAPPER.writeValueAsString(out);
        } catch (Exception e) {
            log.debug("home/away JSON serialize hatasi: {}", e.toString());
            return null;
        }
    }

    private BigDecimal parseBd(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double asNumber(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
