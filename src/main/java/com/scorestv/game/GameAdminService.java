package com.scorestv.game;

import com.scorestv.common.ApiException;
import com.scorestv.game.GameDtos.CreateCompetitionRequest;
import com.scorestv.game.GameDtos.CreateDuelRequest;
import com.scorestv.game.GameDtos.PlayerRef;
import com.scorestv.game.GameDtos.AdminUserCoinView;
import com.scorestv.game.GameDtos.AdminGrantResult;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Oyun — ADMIN tarafı: yarışma + düello oluşturma/yönetme. */
@Service
public class GameAdminService {

    private final GameCompetitionRepository competitionRepo;
    private final GameDuelRepository duelRepo;
    private final UserRepository userRepo;
    private final ScoresCoinService scoresCoinService;
    private final UserGameStatRepository statRepo;

    public GameAdminService(GameCompetitionRepository competitionRepo,
                            GameDuelRepository duelRepo,
                            UserRepository userRepo,
                            ScoresCoinService scoresCoinService,
                            UserGameStatRepository statRepo) {
        this.competitionRepo = competitionRepo;
        this.duelRepo = duelRepo;
        this.userRepo = userRepo;
        this.scoresCoinService = scoresCoinService;
        this.statRepo = statRepo;
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

    // ---- Admin: Scores Coin yönetimi ----

    /** E-posta veya görünen ada göre üye arama (bakiye dahil, en fazla 20). */
    @Transactional(readOnly = true)
    public List<AdminUserCoinView> searchUsers(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return userRepo.searchByEmailOrName(q.trim(), PageRequest.of(0, 20))
                .stream()
                .map(u -> {
                    UserGameStat st = statRepo.findById(u.getId()).orElse(null);
                    long bal = st != null ? st.getCoinBalance() : 0L;
                    long life = st != null ? st.getLifetimeCoins() : 0L;
                    return new AdminUserCoinView(u.getId(), u.getEmail(),
                            u.getDisplayName(), bal, life);
                })
                .toList();
    }

    /** Belirli üyeye coin ekle/çıkar (delta + / -). Bakiye eksiye düşemez. */
    @Transactional
    public AdminGrantResult grantCoins(Long userId, Integer delta, String reason) {
        User user = userRepo.findById(userId).orElseThrow(
                () -> ApiException.notFound("Kullanıcı bulunamadı."));
        if (delta == null || delta == 0) {
            throw ApiException.badRequest("Miktar sıfır olamaz.");
        }
        UserGameStat st = scoresCoinService.getOrCreate(user.getId());
        if (st.getCoinBalance() + delta < 0) {
            throw ApiException.badRequest("Bakiye eksiye düşemez.");
        }
        String r = (reason == null || reason.isBlank()) ? "ADMIN_GRANT" : reason.trim();
        scoresCoinService.grant(user.getId(), delta, r, "ADMIN", null);
        UserGameStat updated = scoresCoinService.getOrCreate(user.getId());
        return new AdminGrantResult(user.getId(), updated.getCoinBalance(),
                updated.getLifetimeCoins());
    }

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
