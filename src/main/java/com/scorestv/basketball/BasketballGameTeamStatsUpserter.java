package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameTeamStat;
import com.scorestv.basketball.domain.BasketballGameTeamStatRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mac basina TAKIM istatistikleri upsert — REPLACE strategy.
 *
 * <p>Game basina 2 satir (home + away). Sync sirasinda game_id'ye gore
 * onceki satirlar silinip yeniden insert edilir.
 */
@Component
public class BasketballGameTeamStatsUpserter {

    private final BasketballGameTeamStatRepository statRepo;
    private final BasketballTeamRepository teamRepo;

    public BasketballGameTeamStatsUpserter(BasketballGameTeamStatRepository statRepo,
                                           BasketballTeamRepository teamRepo) {
        this.statRepo = statRepo;
        this.teamRepo = teamRepo;
    }

    /**
     * Bir mac icin tum takim istatistiklerini replace eder.
     *
     * @param game hedef mac (zaten DB'de)
     * @param dtos API'den gelen 2 satir (home + away)
     * @return yazilan satir sayisi
     */
    @Transactional
    public int replaceAll(BasketballGame game, List<BkGameTeamStatDto> dtos) {
        if (game == null || dtos == null) return 0;
        statRepo.deleteByGameId(game.getId());

        int written = 0;
        for (BkGameTeamStatDto dto : dtos) {
            if (dto == null || dto.team() == null || dto.team().id() == null) continue;

            BasketballTeam team = teamRepo.findById(dto.team().id()).orElseGet(() -> {
                // Self-heal — takim eksikse minimal yaz.
                BasketballTeam t = new BasketballTeam();
                t.setId(dto.team().id());
                t.setName(dto.team().name() != null ? dto.team().name()
                        : ("Takim #" + dto.team().id()));
                t.setLogo(dto.team().logo());
                return teamRepo.save(t);
            });

            BasketballGameTeamStat s = new BasketballGameTeamStat();
            s.setGame(game);
            s.setTeam(team);

            if (dto.fieldGoals() != null) {
                s.setFgTotal(dto.fieldGoals().total());
                s.setFgAttempts(dto.fieldGoals().attempts());
                s.setFgPercentage(dto.fieldGoals().percentage());
            }
            if (dto.threepointGoals() != null) {
                s.setTpTotal(dto.threepointGoals().total());
                s.setTpAttempts(dto.threepointGoals().attempts());
                s.setTpPercentage(dto.threepointGoals().percentage());
            }
            if (dto.freethrowsGoals() != null) {
                s.setFtTotal(dto.freethrowsGoals().total());
                s.setFtAttempts(dto.freethrowsGoals().attempts());
                s.setFtPercentage(dto.freethrowsGoals().percentage());
            }
            if (dto.rebounds() != null) {
                s.setReboundsTotal(dto.rebounds().total());
                s.setReboundsOffence(dto.rebounds().offence());
                s.setReboundsDefense(dto.rebounds().defense());
            }
            s.setAssists(dto.assists());
            s.setSteals(dto.steals());
            s.setBlocks(dto.blocks());
            s.setTurnovers(dto.turnovers());
            s.setPersonalFouls(dto.personalFouls());

            statRepo.save(s);
            written++;
        }
        return written;
    }
}
