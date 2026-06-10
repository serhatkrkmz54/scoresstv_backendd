package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureLineup;
import com.scorestv.football.domain.FixtureLineupPlayer;
import com.scorestv.football.domain.FixtureLineupPlayerRepository;
import com.scorestv.football.domain.FixtureLineupRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.LineupApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Kadro verisini DB'ye yazar.
 *
 * <p>Strateji takım kadrosu (fixture_lineups) için <b>UPSERT</b>: mevcut satır
 * varsa UPDATE (formation/coach/colors), yoksa INSERT (announced_at = now()).
 * Bu sayede {@code announced_at} ilk insert'te belirlenir, sonraki sync'ler
 * dokunmaz — frontend "kadro 2 saat önce açıklandı" göstergesi geçerli kalır.
 *
 * <p>Oyuncular (fixture_lineup_players) için <b>REPLACE</b>: o kadronun tüm
 * oyuncuları silinir, gelenler tam set olarak yazılır (insertion order
 * korunur). Bu, son-dakika 11 değişikliklerini doğal yansıtır.
 */
@Service
public class FixtureLineupsUpserter {

    private final FixtureLineupRepository lineupRepository;
    private final FixtureLineupPlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerUpserter playerUpserter;

    public FixtureLineupsUpserter(FixtureLineupRepository lineupRepository,
                                  FixtureLineupPlayerRepository playerRepository,
                                  FixtureRepository fixtureRepository,
                                  TeamRepository teamRepository,
                                  PlayerUpserter playerUpserter) {
        this.lineupRepository = lineupRepository;
        this.playerRepository = playerRepository;
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.playerUpserter = playerUpserter;
    }

    /**
     * Bir maç için tüm gelen kadroları upsert eder.
     *
     * @return yazılan takım kadrosu sayısı (0, 1 veya 2)
     */
    @Transactional
    public int upsert(Long fixtureId, List<LineupApiDto> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
        int processed = 0;
        for (LineupApiDto item : items) {
            if (item == null || item.team() == null || item.team().id() == null) {
                continue;
            }
            upsertOne(fixtureId, fixtureRef, item);
            processed++;
        }
        return processed;
    }

    private void upsertOne(Long fixtureId, Fixture fixtureRef, LineupApiDto item) {
        Long teamId = item.team().id();
        Team teamRef = teamRepository.getReferenceById(teamId);

        FixtureLineup lineup = lineupRepository
                .findByFixtureIdAndTeamId(fixtureId, teamId)
                .orElseGet(() -> {
                    FixtureLineup fresh = new FixtureLineup();
                    fresh.setFixture(fixtureRef);
                    fresh.setTeam(teamRef);
                    fresh.setAnnouncedAt(Instant.now());
                    return fresh;
                });

        // announced_at JPA'da updatable=false; UPDATE'lerde DOKUNULMAZ.
        lineup.setFormation(item.formation());
        applyCoach(lineup, item.coach());
        applyColors(lineup, item.team().colors());
        lineup = lineupRepository.save(lineup);

        // Oyuncuları sil + yeniden yaz (replace).
        playerRepository.deleteByLineupId(lineup.getId());
        int order = 0;
        order = insertPlayers(lineup, item.startXi(), false, order);
        insertPlayers(lineup, item.substitutes(), true, order);
    }

    private int insertPlayers(FixtureLineup lineup,
                              List<LineupApiDto.PlayerWrap> wraps,
                              boolean substitute,
                              int startOrder) {
        if (wraps == null || wraps.isEmpty()) {
            return startOrder;
        }
        int order = startOrder;
        for (LineupApiDto.PlayerWrap wrap : wraps) {
            if (wrap == null || wrap.player() == null) {
                continue;
            }
            LineupApiDto.Player p = wrap.player();
            FixtureLineupPlayer entity = new FixtureLineupPlayer();
            entity.setLineup(lineup);
            entity.setPlayerId(p.id());
            entity.setPlayerName(p.name());
            // Lineups'da foto gelmez ama master tabloya en azindan ad ekle
            // (sonra injury/topplayer/stats syncleri foto'yu doldurur)
            playerUpserter.upsert(p.id(), p.name(), null);
            entity.setJerseyNumber(p.number());
            entity.setPosition(p.pos());
            entity.setGrid(p.grid());
            entity.setSubstitute(substitute);
            entity.setSortOrder(order++);
            playerRepository.save(entity);
        }
        return order;
    }

    private static void applyCoach(FixtureLineup lineup, LineupApiDto.Coach coach) {
        if (coach == null) {
            lineup.setCoachId(null);
            lineup.setCoachName(null);
            lineup.setCoachPhoto(null);
            return;
        }
        lineup.setCoachId(coach.id());
        lineup.setCoachName(coach.name());
        lineup.setCoachPhoto(coach.photo());
    }

    private static void applyColors(FixtureLineup lineup, LineupApiDto.Colors colors) {
        if (colors == null) {
            return;
        }
        if (colors.player() != null) {
            lineup.setPlayerColorPrimary(colors.player().primary());
            lineup.setPlayerColorNumber(colors.player().number());
            lineup.setPlayerColorBorder(colors.player().border());
        }
        if (colors.goalkeeper() != null) {
            lineup.setGkColorPrimary(colors.goalkeeper().primary());
            lineup.setGkColorNumber(colors.goalkeeper().number());
            lineup.setGkColorBorder(colors.goalkeeper().border());
        }
    }
}
