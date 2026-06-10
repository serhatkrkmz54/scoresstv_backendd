package com.scorestv.football.sync;

import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TeamSquad;
import com.scorestv.football.domain.TeamSquadRepository;
import com.scorestv.football.sync.dto.SquadApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Takim kadrosunu DB'ye REPLACE eder: (team, season) icin tum satirlar
 * silinir, gelen tam set yazilir. Her oyuncu ayrica {@link PlayerUpserter}
 * ile master tabloya yazilir (foto MinIO'ya aynalanir).
 */
@Service
public class SquadUpserter {

    private final TeamSquadRepository repository;
    private final TeamRepository teamRepository;
    private final PlayerUpserter playerUpserter;

    public SquadUpserter(TeamSquadRepository repository,
                         TeamRepository teamRepository,
                         PlayerUpserter playerUpserter) {
        this.repository = repository;
        this.teamRepository = teamRepository;
        this.playerUpserter = playerUpserter;
    }

    @Transactional
    public int replace(Long teamId, Integer season, List<SquadApiDto.Player> players) {
        repository.deleteByTeamIdAndSeason(teamId, season);
        if (players == null || players.isEmpty()) {
            return 0;
        }
        Team teamRef = teamRepository.getReferenceById(teamId);
        int written = 0;
        for (SquadApiDto.Player p : players) {
            if (p == null || p.id() == null || p.name() == null) {
                continue;
            }
            TeamSquad squad = new TeamSquad();
            squad.setTeam(teamRef);
            squad.setSeason(season);
            squad.setPlayerId(p.id());
            squad.setPlayerName(p.name());
            squad.setPlayerAge(p.age());
            squad.setPosition(p.position());
            squad.setJerseyNumber(p.number());
            repository.save(squad);
            // Player master tablo upsert (foto mirror tetikleyici)
            playerUpserter.upsert(p.id(), p.name(), p.photo());
            written++;
        }
        return written;
    }
}
