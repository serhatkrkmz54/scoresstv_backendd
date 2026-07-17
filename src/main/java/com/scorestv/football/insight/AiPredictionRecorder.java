package com.scorestv.football.insight;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * AI tahminlerini MAÇTAN ÖNCE kaydeder ve maç bitince notlar. Aylık/yıllık
 * isabet istatistiklerinin veri kaynağı budur. Kayıt maçtan önce yapıldığı
 * için sonuç sızıntısı (leakage) olmaz; backfill ise (geçmiş) yaklaşıktır.
 */
@Service
public class AiPredictionRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionRecorder.class);

    /** Kayıt penceresi: önümüzdeki bu süre içinde başlayacak maçlar kaydedilir. */
    private static final Duration RECORD_WINDOW = Duration.ofHours(36);
    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN");

    private final FixtureRepository fixtureRepo;
    private final MatchInsightService insightService;
    private final AiPredictionRepository repo;

    public AiPredictionRecorder(FixtureRepository fixtureRepo,
                                MatchInsightService insightService,
                                AiPredictionRepository repo) {
        this.fixtureRepo = fixtureRepo;
        this.insightService = insightService;
        this.repo = repo;
    }

    /** Yaklaşan (covered) maçların tahminini bir kez kaydet (varsa atla). */
    @Transactional
    public int recordUpcoming() {
        final Instant now = Instant.now();
        final List<Fixture> fixtures =
                fixtureRepo.findUpcomingCoveredFixtures(now, now.plus(RECORD_WINDOW));
        int n = 0;
        for (Fixture f : fixtures) {
            if (repo.existsById(f.getId())) continue;
            final MatchInsightResponse ins = safeInsight(f);
            if (ins == null || !ins.available()) continue;
            repo.save(snapshot(f, ins));
            n++;
        }
        if (n > 0) log.info("AI tahmin kaydı: {} yeni maç", n);
        return n;
    }

    /** Bitmiş ama notlanmamış tahminleri notla. */
    @Transactional
    public int gradeFinished() {
        int n = 0;
        for (AiPrediction p : repo.findByGradedFalse()) {
            final Fixture f = fixtureRepo.findById(p.getFixtureId()).orElse(null);
            if (f == null) continue;
            if (f.getStatusShort() == null || !FINISHED.contains(f.getStatusShort())) continue;
            if (f.getHomeGoals() == null || f.getAwayGoals() == null) continue;
            grade(p, f.getHomeGoals(), f.getAwayGoals());
            repo.save(p);
            n++;
        }
        if (n > 0) log.info("AI tahmin notlama: {} maç", n);
        return n;
    }

    /**
     * Geçmiş (son {@code days} gün) bitmiş covered maçları YAKLAŞIK olarak
     * doldur — model şu anki ratinglerle hesaplandığından hafif sapmalıdır
     * (ilk istatistiklerin hemen görünmesi için). İdempotent.
     */
    @Transactional
    public int backfill(int days) {
        final Instant now = Instant.now();
        final int clamped = Math.max(1, Math.min(days, 365));
        final List<Fixture> fixtures = fixtureRepo.findRecentlyFinishedCoveredFixtures(
                now.minus(Duration.ofDays(clamped)), now);
        int n = 0;
        for (Fixture f : fixtures) {
            if (repo.existsById(f.getId())) continue;
            if (f.getHomeGoals() == null || f.getAwayGoals() == null) continue;
            final MatchInsightResponse ins = safeInsight(f);
            if (ins == null || !ins.available()) continue;
            final AiPrediction p = snapshot(f, ins);
            grade(p, f.getHomeGoals(), f.getAwayGoals());
            repo.save(p);
            n++;
        }
        log.info("AI tahmin backfill ({} gün): {} maç", clamped, n);
        return n;
    }

    // --------------------------------------------------------------- yardımcılar
    private MatchInsightResponse safeInsight(Fixture f) {
        try {
            return insightService.forFixture(f, false);
        } catch (RuntimeException ex) {
            log.debug("AI insight hesaplanamadı fixture={}: {}", f.getId(), ex.toString());
            return null;
        }
    }

    private AiPrediction snapshot(Fixture f, MatchInsightResponse ins) {
        final AiPrediction p = new AiPrediction();
        p.setFixtureId(f.getId());
        p.setLeagueId(f.getLeague() != null ? f.getLeague().getId() : null);
        p.setKickoffAt(f.getKickoffAt());
        p.setFavorite(ins.favorite());
        p.setHomeWinPct(ins.homeWinPct());
        p.setDrawPct(ins.drawPct());
        p.setAwayWinPct(ins.awayWinPct());
        p.setOver25Pct(ins.over25Pct());
        p.setBttsYesPct(ins.bttsYesPct());
        p.setExpHome(ins.expectedGoalsHome());
        p.setExpAway(ins.expectedGoalsAway());
        p.setExpectedScore(ins.expectedScore());
        p.setConfidence(ins.confidence());
        p.setPickResult(ins.favorite()); // null olabilir → notlanmaz
        p.setPickOu(ins.over25Pct() != null && ins.over25Pct() >= 50 ? "OVER" : "UNDER");
        p.setPickBtts(ins.bttsYesPct() != null && ins.bttsYesPct() >= 50 ? "YES" : "NO");
        p.setGraded(false);
        return p;
    }

    private void grade(AiPrediction p, int h, int a) {
        p.setActualHome(h);
        p.setActualAway(a);
        final String outcome = h > a ? "HOME" : (h < a ? "AWAY" : "DRAW");
        p.setResultHit(p.getPickResult() != null && p.getPickResult().equals(outcome));
        final boolean over = (h + a) >= 3; // 2.5 üst = 3+ gol
        p.setOuHit("OVER".equals(p.getPickOu()) == over);
        final boolean btts = h > 0 && a > 0;
        p.setBttsHit("YES".equals(p.getPickBtts()) == btts);
        p.setExactHit(p.getExpectedScore() != null
                && p.getExpectedScore().equals(h + "-" + a));
        p.setGraded(true);
        p.setGradedAt(Instant.now());
    }
}
