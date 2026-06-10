package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Prediction;
import com.scorestv.football.domain.PredictionRepository;
import com.scorestv.football.sync.dto.PredictionApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maç tahminini DB'ye <b>UPSERT</b> stratejisiyle yazar:
 * fixture için kayıt varsa UPDATE, yoksa INSERT.
 *
 * <p>Tek maç → tek tahmin (UNIQUE constraint). Nested API yapısı flat
 * sütunlara açılır.
 */
@Service
public class PredictionsUpserter {

    private final PredictionRepository predictionRepository;
    private final FixtureRepository fixtureRepository;

    public PredictionsUpserter(PredictionRepository predictionRepository,
                               FixtureRepository fixtureRepository) {
        this.predictionRepository = predictionRepository;
        this.fixtureRepository = fixtureRepository;
    }

    @Transactional
    public int upsert(Long fixtureId, PredictionApiDto item) {
        if (item == null || item.predictions() == null) {
            return 0;
        }
        Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
        Prediction entity = predictionRepository.findByFixtureId(fixtureId)
                .orElseGet(() -> {
                    Prediction fresh = new Prediction();
                    fresh.setFixture(fixtureRef);
                    return fresh;
                });

        applyPredictions(entity, item.predictions());
        applyComparison(entity, item.comparison());
        // Zengin teams verisi JSONB passthrough — frontend dogrudan kullanir.
        entity.setTeamsJson(item.teams());
        predictionRepository.save(entity);
        return 1;
    }

    private static void applyPredictions(Prediction e, PredictionApiDto.Predictions p) {
        if (p.winner() != null) {
            e.setWinnerTeamId(p.winner().id());
            e.setWinnerComment(p.winner().comment());
        } else {
            e.setWinnerTeamId(null);
            e.setWinnerComment(null);
        }
        e.setWinOrDraw(p.winOrDraw());
        e.setAdvice(p.advice());
        e.setUnderOver(p.underOver());
        if (p.goals() != null) {
            e.setGoalsHome(p.goals().home());
            e.setGoalsAway(p.goals().away());
        }
        if (p.percent() != null) {
            e.setPercentHome(p.percent().home());
            e.setPercentDraw(p.percent().draw());
            e.setPercentAway(p.percent().away());
        }
    }

    private static void applyComparison(Prediction e, PredictionApiDto.Comparison c) {
        if (c == null) {
            return;
        }
        applyPair(c.form(), e::setComparisonFormHome, e::setComparisonFormAway);
        applyPair(c.att(), e::setComparisonAttHome, e::setComparisonAttAway);
        applyPair(c.def(), e::setComparisonDefHome, e::setComparisonDefAway);
        applyPair(c.poissonDistribution(),
                e::setComparisonPoissonHome, e::setComparisonPoissonAway);
        applyPair(c.h2h(), e::setComparisonH2hHome, e::setComparisonH2hAway);
        applyPair(c.goals(), e::setComparisonGoalsHome, e::setComparisonGoalsAway);
        applyPair(c.total(), e::setComparisonTotalHome, e::setComparisonTotalAway);
    }

    private static void applyPair(PredictionApiDto.Pair pair,
                                  java.util.function.Consumer<String> homeSetter,
                                  java.util.function.Consumer<String> awaySetter) {
        if (pair == null) {
            return;
        }
        homeSetter.accept(pair.home());
        awaySetter.accept(pair.away());
    }
}
