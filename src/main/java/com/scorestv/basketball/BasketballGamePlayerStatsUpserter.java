package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGamePlayerStat;
import com.scorestv.basketball.domain.BasketballGamePlayerStatRepository;
import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mac basina OYUNCU istatistikleri upsert — REPLACE strategy.
 *
 * <p>Game basina N satir (her takimdan starters + bench kadrosu). Sync
 * sirasinda game_id'ye gore eski satirlar silinip yeniden insert.
 *
 * <p>{@code player} FK garantili olsun diye {@link BasketballPlayerUpserter}
 * cagrilir. {@code team} self-heal mantigi takim stats upserter ile ayni.
 *
 * <p>In-memory dedup: ayni player API'den iki kez gelirse (defansif) sadece
 * ilki yazilir.
 */
@Component
public class BasketballGamePlayerStatsUpserter {

    private final BasketballGamePlayerStatRepository statRepo;
    private final BasketballTeamRepository teamRepo;
    private final BasketballPlayerUpserter playerUpserter;

    public BasketballGamePlayerStatsUpserter(BasketballGamePlayerStatRepository statRepo,
                                              BasketballTeamRepository teamRepo,
                                              BasketballPlayerUpserter playerUpserter) {
        this.statRepo = statRepo;
        this.teamRepo = teamRepo;
        this.playerUpserter = playerUpserter;
    }

    @Transactional
    public int replaceAll(BasketballGame game, List<BkGamePlayerStatDto> dtos) {
        if (game == null || dtos == null) return 0;
        statRepo.deleteByGameId(game.getId());

        int written = 0;
        Set<Long> seenPlayers = new HashSet<>();
        for (BkGamePlayerStatDto dto : dtos) {
            if (dto == null || dto.player() == null || dto.player().id() == null) continue;
            if (!seenPlayers.add(dto.player().id())) continue;

            // Team self-heal.
            BasketballTeam team = null;
            if (dto.team() != null && dto.team().id() != null) {
                final BkGamePlayerStatDto.TeamRef tref = dto.team();
                team = teamRepo.findById(tref.id()).orElseGet(() -> {
                    BasketballTeam t = new BasketballTeam();
                    t.setId(tref.id());
                    t.setName(tref.name() != null ? tref.name()
                            : ("Takim #" + tref.id()));
                    t.setLogo(tref.logo());
                    return teamRepo.save(t);
                });
            }
            if (team == null) continue;   // Team yoksa atla — FK NOT NULL

            // Player ensure — current team olarak set edilir.
            BasketballPlayer player = playerUpserter.ensure(
                    dto.player().id(), dto.player().name(), team);
            if (player == null) continue;

            BasketballGamePlayerStat s = new BasketballGamePlayerStat();
            s.setGame(game);
            s.setTeam(team);
            s.setPlayer(player);
            s.setPlayerName(dto.player().name());
            s.setType(dto.type());
            s.setMinutes(dto.minutes());

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
            s.setPoints(dto.points());
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
