package com.scorestv.football.sync;

import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TeamStatistics;
import com.scorestv.football.domain.TeamStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Takim istatistik JSON'unu DB'ye UPSERT eder: (team, league, season) kaydi
 * varsa stats_json'i guncelle; yoksa insert.
 */
@Service
public class TeamStatisticsUpserter {

    private final TeamStatisticsRepository repository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;

    public TeamStatisticsUpserter(TeamStatisticsRepository repository,
                                  TeamRepository teamRepository,
                                  LeagueRepository leagueRepository) {
        this.repository = repository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
    }

    @Transactional
    public int upsert(Long teamId, Long leagueId, Integer season,
                      Map<String, Object> statsJson) {
        if (statsJson == null || statsJson.isEmpty()) {
            return 0;
        }
        Team teamRef = teamRepository.getReferenceById(teamId);
        League leagueRef = leagueRepository.getReferenceById(leagueId);
        TeamStatistics entity = repository
                .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season)
                .orElseGet(() -> {
                    TeamStatistics fresh = new TeamStatistics();
                    fresh.setTeam(teamRef);
                    fresh.setLeague(leagueRef);
                    fresh.setSeason(season);
                    return fresh;
                });
        entity.setStatsJson(statsJson);
        repository.save(entity);
        return 1;
    }
}
