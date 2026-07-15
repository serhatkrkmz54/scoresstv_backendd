package com.scorestv.game;

import com.scorestv.football.domain.FixturePlayerStat;
import com.scorestv.football.domain.FixturePlayerStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bir yarışmayı çözer: her düelloyu gerçek maç istatistiğiyle (dönem penceresi)
 * hesaplar, kazananı belirler; sonra kullanıcı-merkezli geçişte zorluk çarpanı +
 * seri (streak) ile Scores Coin dağıtır. İdempotent (RESOLVED ise tekrar çalışmaz).
 *
 * <p>Puanlama: taban 100 coin × zorluk çarpanı (az seçileni tutturmak → daha çok,
 * en fazla 3×) × seri faktörü (üst üste doğruda her adım +%5, en fazla +%50).
 * Berabere/VOID = push (coin yok, seri bozulmaz).
 */
@Service
public class GameResolutionService {

    private static final Logger log = LoggerFactory.getLogger(GameResolutionService.class);

    private static final int BASE_REWARD = 100;
    private static final double MAX_MULT = 3.0;
    private static final double MIN_SHARE = 1.0 / MAX_MULT;

    private final GameCompetitionRepository competitionRepo;
    private final GameDuelRepository duelRepo;
    private final GamePickRepository pickRepo;
    private final UserGameStatRepository statRepo;
    private final ScoresCoinService coinService;
    private final FixturePlayerStatRepository fpsRepo;
    private final DuelValueResolver resolver;

    public GameResolutionService(GameCompetitionRepository competitionRepo,
                                 GameDuelRepository duelRepo,
                                 GamePickRepository pickRepo,
                                 UserGameStatRepository statRepo,
                                 ScoresCoinService coinService,
                                 FixturePlayerStatRepository fpsRepo,
                                 DuelValueResolver resolver) {
        this.competitionRepo = competitionRepo;
        this.duelRepo = duelRepo;
        this.pickRepo = pickRepo;
        this.statRepo = statRepo;
        this.coinService = coinService;
        this.fpsRepo = fpsRepo;
        this.resolver = resolver;
    }

    @Transactional
    public void resolveCompetition(Long competitionId) {
        final GameCompetition comp = competitionRepo.findById(competitionId).orElse(null);
        if (comp == null || comp.getStatus() == GameStatus.RESOLVED) return;

        final Instant start = comp.getStartAt();
        final Instant end = comp.getEndAt();
        final List<GameDuel> duels = duelRepo.findByCompetitionIdOrderBySortOrderAsc(competitionId);

        // 1) Her düelloyu çöz (değer + kazanan).
        for (GameDuel d : duels) {
            if (d.getStatus() == DuelStatus.RESOLVED || d.getStatus() == DuelStatus.VOID) continue;
            resolveOneDuel(d, start, end);
        }
        duelRepo.saveAll(duels);

        // 2) Zorluk çarpanları (düello başına, kazanan tarafın seçilme oranından).
        final List<GamePick> allPicks = pickRepo.findByCompetitionId(competitionId);
        final Map<Long, GameDuel> duelById = new HashMap<>();
        for (GameDuel d : duels) duelById.put(d.getId(), d);
        final Map<Long, int[]> counts = new HashMap<>(); // duelId -> [aCount, bCount]
        for (GamePick p : allPicks) {
            final int[] c = counts.computeIfAbsent(p.getDuelId(), k -> new int[2]);
            if ("A".equals(p.getPick())) c[0]++;
            else if ("B".equals(p.getPick())) c[1]++;
        }
        final Map<Long, Double> multById = new HashMap<>();
        for (GameDuel d : duels) multById.put(d.getId(), difficultyMultiplier(d, counts.get(d.getId())));

        // 3) Kullanıcı-merkezli geçiş: her kullanıcının tahminleri sortOrder sırasıyla
        //    → seri (streak) tutarlı, coin dağıtılır.
        final Map<Long, List<GamePick>> byUser =
                allPicks.stream().collect(Collectors.groupingBy(GamePick::getUserId));
        for (Map.Entry<Long, List<GamePick>> e : byUser.entrySet()) {
            resolveUser(e.getKey(), e.getValue(), duelById, multById);
        }

        comp.setStatus(GameStatus.RESOLVED);
        comp.setResolvedAt(Instant.now());
        competitionRepo.save(comp);
        log.info("Oyun yarismasi cozuldu: id={} duels={} picks={}",
                competitionId, duels.size(), allPicks.size());
    }

    private void resolveOneDuel(GameDuel d, Instant start, Instant end) {
        final List<FixturePlayerStat> statsA = fpsRepo.findPlayerStatsInWindow(d.getPlayerAId(), start, end);
        final List<FixturePlayerStat> statsB = fpsRepo.findPlayerStatsInWindow(d.getPlayerBId(), start, end);
        final BigDecimal va = resolver.value(d.getMetric(), statsA);
        final BigDecimal vb = resolver.value(d.getMetric(), statsB);
        d.setValueA(va);
        d.setValueB(vb);
        d.setResolvedAt(Instant.now());

        if (va == null || vb == null) {
            // Biri hiç oynamadı → adil değil, iptal (push).
            d.setStatus(DuelStatus.VOID);
            d.setWinner("VOID");
            return;
        }
        final int cmp = va.compareTo(vb);
        final String winner;
        if (cmp == 0) {
            winner = "DRAW";
        } else {
            final boolean aWins = (d.getDirection() == DuelDirection.HIGHER) ? cmp > 0 : cmp < 0;
            winner = aWins ? "A" : "B";
        }
        d.setWinner(winner);
        d.setStatus(DuelStatus.RESOLVED);
    }

    private double difficultyMultiplier(GameDuel d, int[] counts) {
        final String w = d.getWinner();
        if (counts == null || (!"A".equals(w) && !"B".equals(w))) return 1.0;
        final int total = counts[0] + counts[1];
        if (total == 0) return 1.0;
        final int winnerPicks = "A".equals(w) ? counts[0] : counts[1];
        if (winnerPicks == 0) return MAX_MULT;
        final double share = (double) winnerPicks / total;
        final double m = 1.0 / Math.max(share, MIN_SHARE);
        return Math.min(MAX_MULT, Math.max(1.0, m));
    }

    private void resolveUser(Long userId, List<GamePick> picks,
                             Map<Long, GameDuel> duelById, Map<Long, Double> multById) {
        picks.sort(Comparator.comparingInt(p -> {
            final GameDuel d = duelById.get(p.getDuelId());
            return d == null ? 0 : d.getSortOrder();
        }));
        final UserGameStat stat = coinService.getOrCreate(userId);
        long balance = stat.getCoinBalance();
        long lifetime = stat.getLifetimeCoins();
        int streak = stat.getCurrentStreak();
        int best = stat.getBestStreak();
        int totalPicks = stat.getTotalPicks();
        int correctPicks = stat.getCorrectPicks();

        for (GamePick p : picks) {
            if (p.getCorrect() != null) continue; // zaten çözülmüş (idempotent)
            final GameDuel d = duelById.get(p.getDuelId());
            if (d == null) continue;
            if (d.getStatus() == DuelStatus.VOID || "DRAW".equals(d.getWinner())) {
                continue; // push: coin yok, seri bozulmaz, correct null kalır
            }
            final boolean correct = p.getPick().equals(d.getWinner());
            totalPicks++;
            if (correct) {
                streak++;
                if (streak > best) best = streak;
                correctPicks++;
                final double mult = multById.getOrDefault(d.getId(), 1.0);
                final double streakFactor = 1.0 + Math.min(Math.max(streak - 1, 0), 10) * 0.05;
                final int reward = (int) Math.round(BASE_REWARD * mult * streakFactor);
                balance += reward;
                lifetime += reward;
                p.setCoinsAwarded(reward);
                coinService.appendLedger(userId, reward, balance, "PICK_WIN", "DUEL", d.getId());
            } else {
                streak = 0;
                p.setCoinsAwarded(0);
            }
            p.setCorrect(correct);
        }
        pickRepo.saveAll(picks);
        stat.setCoinBalance(balance);
        stat.setLifetimeCoins(lifetime);
        stat.setCurrentStreak(streak);
        stat.setBestStreak(best);
        stat.setTotalPicks(totalPicks);
        stat.setCorrectPicks(correctPicks);
        statRepo.save(stat);
    }
}
