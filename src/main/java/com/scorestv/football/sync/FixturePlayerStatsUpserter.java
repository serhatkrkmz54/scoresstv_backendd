package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixturePlayerStat;
import com.scorestv.football.domain.FixturePlayerStatRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.PlayerStatApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Oyuncu maç istatistiklerini DB'ye <b>replace</b> stratejisiyle yazar:
 * o maçın TÜM oyuncu satırları silinir, gelen tam set yazılır.
 *
 * <p>API nested objeleri (games/shots/goals/...) flat sütunlara açılır.
 * {@link PlayerStatApiDto.Statistics} array'i API'da daima 1 elemanlıdır;
 * ilk eleman alınır.
 */
@Service
public class FixturePlayerStatsUpserter {

    private final FixturePlayerStatRepository repository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerUpserter playerUpserter;

    public FixturePlayerStatsUpserter(FixturePlayerStatRepository repository,
                                      FixtureRepository fixtureRepository,
                                      TeamRepository teamRepository,
                                      PlayerUpserter playerUpserter) {
        this.repository = repository;
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.playerUpserter = playerUpserter;
    }

    @Transactional
    public int replace(Long fixtureId, List<PlayerStatApiDto> items) {
        Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
        repository.deleteByFixtureId(fixtureId);

        if (items == null || items.isEmpty()) {
            return 0;
        }
        // In-memory dedup — uq_fixture_player_stats(fixture_id, player_id) unique;
        // API ayni player_id'yi (ozellikle null/0 -> coach decision) birden fazla
        // dondurebilir, ilk goreni yaz, geri kalanlari atla.
        Set<Long> seenPlayerIds = new HashSet<>();
        int written = 0;
        for (PlayerStatApiDto item : items) {
            if (item == null || item.team() == null || item.team().id() == null
                    || item.players() == null) {
                continue;
            }
            Team teamRef = teamRepository.getReferenceById(item.team().id());
            for (PlayerStatApiDto.PlayerEntry entry : item.players()) {
                if (entry == null || entry.player() == null) {
                    continue;
                }
                Long playerId = entry.player().id();
                // API-Football bazen player_id=null/0 ile placeholder satir gonderir
                // ("coach decision" gibi) — bunlar gercek oyuncu degil, atla.
                if (playerId == null || playerId == 0L) {
                    continue;
                }
                // Ayni maca ayni player iki kez gelirse skip (dup-key korumasi).
                if (!seenPlayerIds.add(playerId)) {
                    continue;
                }
                FixturePlayerStat ps = build(fixtureRef, teamRef, entry);
                repository.save(ps);
                playerUpserter.upsert(
                        playerId, entry.player().name(), entry.player().photo());
                written++;
            }
        }
        return written;
    }

    private FixturePlayerStat build(Fixture fixtureRef,
                                    Team teamRef,
                                    PlayerStatApiDto.PlayerEntry entry) {
        FixturePlayerStat ps = new FixturePlayerStat();
        ps.setFixture(fixtureRef);
        ps.setTeam(teamRef);
        ps.setPlayerId(entry.player().id());
        ps.setPlayerName(entry.player().name());
        ps.setPlayerPhoto(entry.player().photo());

        // statistics array'i hep 1 elemanlı — ilki al; yoksa varlık alanları null kalır.
        PlayerStatApiDto.Statistics stats = (entry.statistics() != null
                && !entry.statistics().isEmpty())
                ? entry.statistics().get(0)
                : null;
        if (stats == null) {
            return ps;
        }
        applyGames(ps, stats.games());
        ps.setOffsides(stats.offsides());
        applyShots(ps, stats.shots());
        applyGoals(ps, stats.goals());
        applyPasses(ps, stats.passes());
        applyTackles(ps, stats.tackles());
        applyDuels(ps, stats.duels());
        applyDribbles(ps, stats.dribbles());
        applyFouls(ps, stats.fouls());
        applyCards(ps, stats.cards());
        applyPenalty(ps, stats.penalty());
        return ps;
    }

    private static void applyGames(FixturePlayerStat ps, PlayerStatApiDto.Games g) {
        if (g == null) return;
        ps.setMinutes(g.minutes());
        ps.setJerseyNumber(g.number());
        ps.setPosition(g.position());
        ps.setRating(g.rating());
        ps.setCaptain(g.captain());
        ps.setSubstitute(g.substitute());
    }

    private static void applyShots(FixturePlayerStat ps, PlayerStatApiDto.Shots s) {
        if (s == null) return;
        ps.setShotsTotal(s.total());
        ps.setShotsOn(s.on());
    }

    private static void applyGoals(FixturePlayerStat ps, PlayerStatApiDto.Goals g) {
        if (g == null) return;
        ps.setGoalsTotal(g.total());
        ps.setGoalsConceded(g.conceded());
        ps.setGoalsAssists(g.assists());
        ps.setGoalsSaves(g.saves());
    }

    private static void applyPasses(FixturePlayerStat ps, PlayerStatApiDto.Passes p) {
        if (p == null) return;
        ps.setPassesTotal(p.total());
        ps.setPassesKey(p.key());
        ps.setPassesAccuracy(p.accuracy());
    }

    private static void applyTackles(FixturePlayerStat ps, PlayerStatApiDto.Tackles t) {
        if (t == null) return;
        ps.setTacklesTotal(t.total());
        ps.setTacklesBlocks(t.blocks());
        ps.setTacklesInterceptions(t.interceptions());
    }

    private static void applyDuels(FixturePlayerStat ps, PlayerStatApiDto.Duels d) {
        if (d == null) return;
        ps.setDuelsTotal(d.total());
        ps.setDuelsWon(d.won());
    }

    private static void applyDribbles(FixturePlayerStat ps, PlayerStatApiDto.Dribbles d) {
        if (d == null) return;
        ps.setDribblesAttempts(d.attempts());
        ps.setDribblesSuccess(d.success());
        ps.setDribblesPast(d.past());
    }

    private static void applyFouls(FixturePlayerStat ps, PlayerStatApiDto.Fouls f) {
        if (f == null) return;
        ps.setFoulsDrawn(f.drawn());
        ps.setFoulsCommitted(f.committed());
    }

    private static void applyCards(FixturePlayerStat ps, PlayerStatApiDto.Cards c) {
        if (c == null) return;
        ps.setCardsYellow(c.yellow());
        ps.setCardsRed(c.red());
    }

    /** API'da "commited" (typo) → DB'de "committed" (doğru yazım). */
    private static void applyPenalty(FixturePlayerStat ps, PlayerStatApiDto.Penalty p) {
        if (p == null) return;
        ps.setPenaltyWon(p.won());
        ps.setPenaltyCommitted(p.commited());
        ps.setPenaltyScored(p.scored());
        ps.setPenaltyMissed(p.missed());
        ps.setPenaltySaved(p.saved());
    }
}
