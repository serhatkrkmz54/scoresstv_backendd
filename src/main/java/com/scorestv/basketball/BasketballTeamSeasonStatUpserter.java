package com.scorestv.basketball;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.basketball.domain.BasketballTeamSeasonStat;
import com.scorestv.basketball.domain.BasketballTeamSeasonStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link BkTeamStatisticsDto} -> {@link BasketballTeamSeasonStat} upsert.
 *
 * <p>API yanitindaki {@code games.played.all}, {@code games.wins.all.total},
 * percentage'leri ayikla; {@code points.for/against} ortalamalarini
 * BigDecimal'a cevir; ev/deplasman breakdown'i JSONB serialize.
 *
 * <p>"En uzun seri" verisi API'de yok — null birakilir; istersek games
 * tablosundan hesaplayabiliriz (FT macin sirali aliriz).
 */
@Component
public class BasketballTeamSeasonStatUpserter {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamSeasonStatUpserter.class);

    /** Yerel JSON mapper — RedisConfig DI cakismasini engellemek icin static. */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final BasketballTeamRepository teamRepo;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballTeamSeasonStatRepository statRepo;

    public BasketballTeamSeasonStatUpserter(BasketballTeamRepository teamRepo,
                                             BasketballLeagueRepository leagueRepo,
                                             BasketballTeamSeasonStatRepository statRepo) {
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.statRepo = statRepo;
    }

    /**
     * DTO'dan upsert. Eksik takim/lig satirinda erken cikis (FK self-heal
     * cagiranin gorevi).
     */
    public BasketballTeamSeasonStat upsertFromDto(
            long teamId, long leagueId, String season,
            BkTeamStatisticsDto dto) {

        if (dto == null || dto.games() == null) {
            log.debug("Team stats DTO bos team={} league={} season={}",
                    teamId, leagueId, season);
            return null;
        }

        BasketballTeam team = teamRepo.findById(teamId).orElse(null);
        BasketballLeague league = leagueRepo.findById(leagueId).orElse(null);
        if (team == null || league == null) {
            log.warn("Team stats upsert: team={} veya league={} bulunamadi (skip)",
                    teamId, leagueId);
            return null;
        }

        BasketballTeamSeasonStat row = statRepo
                .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season)
                .orElseGet(BasketballTeamSeasonStat::new);
        row.setTeam(team);
        row.setLeague(league);
        row.setSeason(season);

        // Games (toplam) bloku
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

        // Points bloku
        var points = dto.points();
        if (points != null) {
            var pfor = points.forPoints();
            if (pfor != null) {
                if (pfor.total() != null) {
                    row.setPointsForTotal(pfor.total().all());
                }
                if (pfor.average() != null) {
                    row.setPointsForAvg(parseBd(pfor.average().all()));
                }
            }
            var pagainst = points.against();
            if (pagainst != null) {
                if (pagainst.total() != null) {
                    row.setPointsAgainstTotal(pagainst.total().all());
                }
                if (pagainst.average() != null) {
                    row.setPointsAgainstAvg(parseBd(pagainst.average().all()));
                }
            }
        }

        // Streak verisi API'de yok — null birakilir
        row.setLongestWinStreak(null);
        row.setLongestLoseStreak(null);
        row.setForm(null); // form games tablosundan hesaplanir

        // Ev/Deplasman breakdown JSONB
        row.setHomeAwayJson(buildHomeAwayJson(games, points));

        return statRepo.save(row);
    }

    /** Win/lose ve points'in ev/deplasman bolumlerini tek JSONB nesnesinde topla. */
    private String buildHomeAwayJson(
            BkTeamStatisticsDto.Games games, BkTeamStatisticsDto.Points points) {
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
                if (points != null) {
                    if (points.forPoints() != null && points.forPoints().average() != null) {
                        var a = points.forPoints().average();
                        b.put("pointsForAvg", asNumber(
                                "home".equals(side) ? a.home() : a.away()));
                    }
                    if (points.against() != null && points.against().average() != null) {
                        var a = points.against().average();
                        b.put("pointsAgainstAvg", asNumber(
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

    /** API string'i "0.500" -> BigDecimal; sayi gelmise de calisir. */
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
