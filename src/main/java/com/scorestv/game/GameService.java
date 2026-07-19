package com.scorestv.game;

import com.scorestv.common.ApiException;
import com.scorestv.game.GameDtos.CompetitionView;
import com.scorestv.game.GameDtos.DuelView;
import com.scorestv.game.GameDtos.LeaderboardEntry;
import com.scorestv.game.GameDtos.LedgerEntry;
import com.scorestv.game.GameDtos.PlayerView;
import com.scorestv.game.GameDtos.WalletView;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Oyun (Scores Coin) — kullanıcıya dönük okuma + tahmin. */
@Service
public class GameService {

    private final GameCompetitionRepository competitionRepo;
    private final GameDuelRepository duelRepo;
    private final GamePickRepository pickRepo;
    private final UserGameStatRepository statRepo;
    private final ScoresCoinService coinService;
    private final UserRepository userRepo;

    public GameService(GameCompetitionRepository competitionRepo,
                       GameDuelRepository duelRepo,
                       GamePickRepository pickRepo,
                       UserGameStatRepository statRepo,
                       ScoresCoinService coinService,
                       UserRepository userRepo) {
        this.competitionRepo = competitionRepo;
        this.duelRepo = duelRepo;
        this.pickRepo = pickRepo;
        this.statRepo = statRepo;
        this.coinService = coinService;
        this.userRepo = userRepo;
    }

    /** Verilen kapsamın aktif (OPEN) yarışması + düellolar; yoksa null. */
    @Transactional(readOnly = true)
    public CompetitionView getActive(GameScope scope, Long userId) {
        return competitionRepo.findFirstByScopeAndStatusOrderByStartAtDesc(scope, GameStatus.OPEN)
                .map(c -> toView(c, userId))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    /** Tüm oynanabilir yarışmalar (OPEN + LOCKED) — çoklu yarışma gösterimi. */
    public List<CompetitionView> activeList(Long userId) {
        return competitionRepo.findByStatusInOrderByLockAtAsc(
                        java.util.List.of(GameStatus.OPEN, GameStatus.LOCKED))
                .stream()
                .map(c -> toView(c, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    /** Tek yarışma detayı. DRAFT (henüz yayınlanmamış) yarışmalar herkese açık
     * uçtan gizlenir (ID tahminiyle önizleme sızıntısını önler) → 404. */
    public CompetitionView getCompetition(Long id, Long userId) {
        final GameCompetition comp = competitionRepo.findById(id)
                .filter(c -> c.getStatus() != GameStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Yarışma bulunamadı."));
        return toView(comp, userId);
    }

    /** Admin görünümü — DRAFT dahil her durumdaki yarışmayı getirir (panel detay). */
    public CompetitionView getCompetitionForAdmin(Long id) {
        final GameCompetition comp = competitionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Yarışma bulunamadı."));
        return toView(comp, null);
    }

    private CompetitionView toView(GameCompetition comp, Long userId) {
        final List<GameDuel> duels = duelRepo.findByCompetitionIdOrderBySortOrderAsc(comp.getId());

        final Map<Long, long[]> counts = new HashMap<>();
        for (DuelPickCount pc : pickRepo.pickCounts(comp.getId())) {
            final long[] c = counts.computeIfAbsent(pc.getDuelId(), k -> new long[2]);
            if ("A".equals(pc.getPick())) c[0] = pc.getCnt();
            else if ("B".equals(pc.getPick())) c[1] = pc.getCnt();
        }

        final Map<Long, String> myPicks = new HashMap<>();
        if (userId != null) {
            for (GamePick p : pickRepo.findByCompetitionIdAndUserId(comp.getId(), userId)) {
                myPicks.put(p.getDuelId(), p.getPick());
            }
        }

        final boolean locked = comp.getStatus() != GameStatus.OPEN
                || Instant.now().isAfter(comp.getLockAt());

        final List<DuelView> duelViews = duels.stream()
                .map(d -> {
                    final long[] c = counts.getOrDefault(d.getId(), new long[2]);
                    return new DuelView(
                            d.getId(), d.getPosition(), d.getMetric(), d.getDirection(),
                            d.getStatus(), d.getWinner(), d.getValueA(), d.getValueB(),
                            new PlayerView(d.getPlayerAId(), d.getPlayerAName(),
                                    d.getPlayerAPhoto(), d.getPlayerATeam(), d.getPlayerATeamLogo()),
                            new PlayerView(d.getPlayerBId(), d.getPlayerBName(),
                                    d.getPlayerBPhoto(), d.getPlayerBTeam(), d.getPlayerBTeamLogo()),
                            myPicks.get(d.getId()), c[0], c[1]);
                })
                .toList();

        return new CompetitionView(comp.getId(), comp.getScope(), comp.getTitle(),
                comp.getTitleEn(),
                comp.getStatus(), comp.getStartAt(), comp.getEndAt(), comp.getLockAt(),
                locked, duelViews);
    }

    /** Tahmin gönder/güncelle (giriş zorunlu). Kilit + durum kontrolü. */
    @Transactional
    public void submitPick(Long userId, Long duelId, String pickRaw) {
        final String pick = pickRaw == null ? "" : pickRaw.trim().toUpperCase();
        if (!"A".equals(pick) && !"B".equals(pick)) {
            throw ApiException.badRequest("Geçersiz seçim (A/B).");
        }
        final GameDuel duel = duelRepo.findById(duelId)
                .orElseThrow(() -> ApiException.notFound("Düello bulunamadı."));
        final GameCompetition comp = competitionRepo.findById(duel.getCompetitionId())
                .orElseThrow(() -> ApiException.notFound("Yarışma bulunamadı."));
        if (comp.getStatus() != GameStatus.OPEN) {
            throw ApiException.badRequest("Yarışma tahmine kapalı.");
        }
        if (Instant.now().isAfter(comp.getLockAt())) {
            throw ApiException.badRequest("Tahmin süresi doldu.");
        }
        if (duel.getStatus() != DuelStatus.OPEN) {
            throw ApiException.badRequest("Bu düello tahmine kapalı.");
        }
        final GamePick existing = pickRepo.findByDuelIdAndUserId(duelId, userId).orElse(null);
        if (existing != null) {
            existing.setPick(pick);
            pickRepo.save(existing);
        } else {
            final GamePick gp = new GamePick();
            gp.setCompetitionId(comp.getId());
            gp.setDuelId(duelId);
            gp.setUserId(userId);
            gp.setPick(pick);
            try {
                pickRepo.save(gp);
            } catch (DataIntegrityViolationException race) {
                // Nadir yarış: aynı kullanıcı aynı düelloya EŞZAMANLI 2 istek
                // gönderdi; diğer istek tahmini önce yazdı (UNIQUE ihlali).
                // Postgres bu noktada tx'i abort eder → AYNI tx'te tekrar
                // find/save DENENEMEZ (25P02). Temiz bir hata döndür; istemci
                // tekrar deneyince mevcut tahmin bulunup güncellenir.
                throw ApiException.badRequest(
                        "Tahmin aynı anda gönderildi, lütfen tekrar deneyin.");
            }
        }
        coinService.getOrCreate(userId); // profil satırı garanti
    }

    /**
     * Kullanıcının bu düellodaki tahminini kaldırır (hiçbir oyuncuya vermemek
     * için). Yalnız yarışma OPEN + kilit saatinden önce + düello OPEN iken.
     * İdempotent: tahmin yoksa sessizce geçer. Tahmin sayıları GamePick
     * satırlarından türetildiği için silme, dağılımı otomatik günceller.
     */
    @Transactional
    public void removePick(Long userId, Long duelId) {
        final GameDuel duel = duelRepo.findById(duelId)
                .orElseThrow(() -> ApiException.notFound("Düello bulunamadı."));
        final GameCompetition comp = competitionRepo.findById(duel.getCompetitionId())
                .orElseThrow(() -> ApiException.notFound("Yarışma bulunamadı."));
        if (comp.getStatus() != GameStatus.OPEN) {
            throw ApiException.badRequest("Yarışma tahmine kapalı.");
        }
        if (Instant.now().isAfter(comp.getLockAt())) {
            throw ApiException.badRequest("Tahmin süresi doldu.");
        }
        if (duel.getStatus() != DuelStatus.OPEN) {
            throw ApiException.badRequest("Bu düello tahmine kapalı.");
        }
        pickRepo.findByDuelIdAndUserId(duelId, userId).ifPresent(pickRepo::delete);
    }

    /** Genel (all-time) sıralama. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> globalLeaderboard(int limit) {
        // Yalnız oynayanlar (totalPicks > 0) — herkes hoşgeldin bonusuyla coin
        // sahibi; liderlik gerçekten tahmin yapanlar üzerinden kurulur.
        final List<UserGameStat> top =
                statRepo.findByTotalPicksGreaterThanOrderByLifetimeCoinsDesc(
                        0, PageRequest.of(0, clampLimit(limit)));
        final Map<Long, String> names = displayNames(
                top.stream().map(UserGameStat::getUserId).collect(Collectors.toSet()));
        final List<LeaderboardEntry> out = new ArrayList<>();
        int rank = 1;
        for (UserGameStat s : top) {
            out.add(new LeaderboardEntry(rank++, s.getUserId(),
                    names.getOrDefault(s.getUserId(), "Kullanıcı"),
                    s.getLifetimeCoins(), s.getCorrectPicks(), s.getTotalPicks()));
        }
        return out;
    }

    /** Bir yarışmanın sıralaması (o dönemde kazanılan coin). */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> competitionLeaderboard(Long competitionId, int limit) {
        final List<CompetitionLeaderboardRow> rows =
                pickRepo.leaderboard(competitionId, PageRequest.of(0, clampLimit(limit)));
        final Map<Long, String> names = displayNames(
                rows.stream().map(CompetitionLeaderboardRow::getUserId).collect(Collectors.toSet()));
        final List<LeaderboardEntry> out = new ArrayList<>();
        int rank = 1;
        for (CompetitionLeaderboardRow r : rows) {
            out.add(new LeaderboardEntry(rank++, r.getUserId(),
                    names.getOrDefault(r.getUserId(), "Kullanıcı"),
                    r.getCoins() == null ? 0 : r.getCoins(),
                    r.getCorrectCount() == null ? 0 : r.getCorrectCount(),
                    r.getTotal() == null ? 0 : r.getTotal()));
        }
        return out;
    }

    @Transactional
    public WalletView wallet(Long userId) {
        final UserGameStat s = coinService.getOrCreate(userId);
        final List<LedgerEntry> hist = coinService.history(userId, 0, 50).stream()
                .map(l -> new LedgerEntry(l.getDelta(), l.getBalanceAfter(), l.getReason(),
                        l.getRefType(), l.getRefId(), l.getCreatedAt()))
                .toList();
        return new WalletView(s.getCoinBalance(), s.getLifetimeCoins(),
                s.getTotalPicks(), s.getCorrectPicks(),
                s.getCurrentStreak(), s.getBestStreak(),
                !s.isWelcomeShown(), hist);
    }

    /** Hoşgeldin kutlamasını "gösterildi" işaretle (ömür boyu 1 kez). */
    @Transactional
    public void markWelcomeShown(Long userId) {
        final UserGameStat s = coinService.getOrCreate(userId);
        if (!s.isWelcomeShown()) {
            s.setWelcomeShown(true);
            statRepo.save(s);
        }
    }

    private Map<Long, String> displayNames(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        final Map<Long, String> map = new HashMap<>();
        for (User u : userRepo.findAllById(userIds)) {
            map.put(u.getId(), u.getDisplayName() != null ? u.getDisplayName() : u.getEmail());
        }
        return map;
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
