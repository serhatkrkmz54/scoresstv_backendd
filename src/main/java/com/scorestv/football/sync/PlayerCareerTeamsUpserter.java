package com.scorestv.football.sync;

import com.scorestv.football.domain.PlayerCareerTeam;
import com.scorestv.football.domain.PlayerCareerTeamRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.PlayerCareerTeamApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Oyuncu kariyer takimlari REPLACE pattern ile yazilir. Bir oyuncunun tum
 * eski kayitlari silinir, gelen tam set yazilir.
 */
@Service
public class PlayerCareerTeamsUpserter {

    private static final Logger log = LoggerFactory.getLogger(PlayerCareerTeamsUpserter.class);

    private final PlayerCareerTeamRepository repository;
    private final TeamRepository teamRepository;

    public PlayerCareerTeamsUpserter(PlayerCareerTeamRepository repository,
                                     TeamRepository teamRepository) {
        this.repository = repository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public int upsert(Long playerId, List<PlayerCareerTeamApiDto> items) {
        if (playerId == null) return 0;
        repository.deleteByPlayerId(playerId);
        if (items == null || items.isEmpty()) return 0;
        int written = 0;
        for (PlayerCareerTeamApiDto dto : items) {
            if (dto == null || dto.team() == null || dto.team().id() == null) continue;
            Team team = teamRepository.findById(dto.team().id()).orElse(null);
            if (team == null) {
                // Team master'da yok — referans veremiyoruz. Atlanir.
                // DailyTeamRefreshJob ileride dolduracak; bir sonraki player
                // sync'inde bu satir yazilacak.
                log.debug("Career team atlandi (team DB'de yok): playerId={} teamId={}",
                        playerId, dto.team().id());
                continue;
            }
            PlayerCareerTeam ct = new PlayerCareerTeam();
            ct.setPlayerId(playerId);
            ct.setTeam(team);
            ct.setSeasons(dto.seasons() != null ? dto.seasons() : List.of());
            repository.save(ct);
            written++;
        }
        return written;
    }
}
