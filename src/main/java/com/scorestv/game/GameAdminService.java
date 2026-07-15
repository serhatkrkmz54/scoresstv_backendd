package com.scorestv.game;

import com.scorestv.common.ApiException;
import com.scorestv.game.GameDtos.CreateCompetitionRequest;
import com.scorestv.game.GameDtos.CreateDuelRequest;
import com.scorestv.game.GameDtos.PlayerRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Oyun — ADMIN tarafı: yarışma + düello oluşturma/yönetme. */
@Service
public class GameAdminService {

    private final GameCompetitionRepository competitionRepo;
    private final GameDuelRepository duelRepo;

    public GameAdminService(GameCompetitionRepository competitionRepo,
                            GameDuelRepository duelRepo) {
        this.competitionRepo = competitionRepo;
        this.duelRepo = duelRepo;
    }

    @Transactional(readOnly = true)
    public List<GameCompetition> listCompetitions() {
        return competitionRepo.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "startAt"));
    }

    @Transactional
    public GameCompetition createCompetition(CreateCompetitionRequest req) {
        if (!req.endAt().isAfter(req.startAt())) {
            throw ApiException.badRequest("Bitiş, başlangıçtan sonra olmalı.");
        }
        final GameCompetition c = new GameCompetition();
        c.setScope(req.scope());
        c.setTitle(req.title());
        c.setSeason(req.season());
        c.setLeagueId(req.leagueId());
        c.setStartAt(req.startAt());
        c.setEndAt(req.endAt());
        c.setLockAt(req.lockAt());
        c.setStatus(GameStatus.DRAFT);
        return competitionRepo.save(c);
    }

    @Transactional
    public GameDuel addDuel(Long competitionId, CreateDuelRequest req) {
        final GameCompetition comp = competitionRepo.findById(competitionId)
                .orElseThrow(() -> ApiException.notFound("Yarışma bulunamadı."));
        if (req.playerA().id().equals(req.playerB().id())) {
            throw ApiException.badRequest("İki oyuncu farklı olmalı.");
        }
        final GameDuel d = new GameDuel();
        d.setCompetitionId(comp.getId());
        d.setPosition(req.position());
        d.setMetric(req.metric());
        d.setDirection(req.direction() != null ? req.direction() : defaultDirection(req.metric()));
        d.setLeagueId(comp.getLeagueId());
        d.setSortOrder(req.sortOrder() != null ? req.sortOrder() : nextSortOrder(comp.getId()));
        applyPlayerA(d, req.playerA());
        applyPlayerB(d, req.playerB());
        d.setStatus(DuelStatus.OPEN);
        return duelRepo.save(d);
    }

    @Transactional
    public void updateStatus(Long competitionId, GameStatus status) {
        final GameCompetition comp = competitionRepo.findById(competitionId)
                .orElseThrow(() -> ApiException.notFound("Yarışma bulunamadı."));
        if (status == GameStatus.RESOLVED) {
            throw ApiException.badRequest("RESOLVED elle atanamaz (çözümleme job'u yapar).");
        }
        comp.setStatus(status);
        competitionRepo.save(comp);
    }

    @Transactional
    public void deleteDuel(Long duelId) {
        duelRepo.deleteById(duelId);
    }

    @Transactional
    public void deleteCompetition(Long competitionId) {
        // duel + pick FK ON DELETE CASCADE ile temizlenir.
        competitionRepo.deleteById(competitionId);
    }

    // ---- yardımcılar ----

    private int nextSortOrder(Long competitionId) {
        return duelRepo.findByCompetitionIdOrderBySortOrderAsc(competitionId).size();
    }

    private static DuelDirection defaultDirection(DuelMetric metric) {
        return switch (metric) {
            case CARDS, FOULS -> DuelDirection.LOWER;
            default -> DuelDirection.HIGHER;
        };
    }

    private static void applyPlayerA(GameDuel d, PlayerRef p) {
        d.setPlayerAId(p.id());
        d.setPlayerAName(p.name());
        d.setPlayerAPhoto(p.photo());
        d.setPlayerATeam(p.team());
        d.setPlayerATeamLogo(p.teamLogo());
    }

    private static void applyPlayerB(GameDuel d, PlayerRef p) {
        d.setPlayerBId(p.id());
        d.setPlayerBName(p.name());
        d.setPlayerBPhoto(p.photo());
        d.setPlayerBTeam(p.team());
        d.setPlayerBTeamLogo(p.teamLogo());
    }
}
