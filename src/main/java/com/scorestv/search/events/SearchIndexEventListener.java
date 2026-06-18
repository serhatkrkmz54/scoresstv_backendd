package com.scorestv.search.events;

import com.scorestv.search.indexer.SearchIndexerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link EntityIndexedEvent} dinleyicisi — async + AFTER_COMMIT.
 *
 * <p><b>Akis:</b>
 * <ol>
 *   <li>TeamUpserter.upsert() → repo.save(team) → publishEvent(TeamIndexed(team))</li>
 *   <li>Spring tx commit eder</li>
 *   <li>Bu listener AFTER_COMMIT'te calisir + @Async ile ayri thread'e duser</li>
 *   <li>SearchIndexerService.indexTeam(team) → ES'e tek-doc yazar</li>
 * </ol>
 *
 * <p>ES'e yazma basarisiz olsa bile (network, ES down, ...) DB transaction
 * COMMIT olmustur. SearchIndexerService.indexXxx() metodlari try/catch ile
 * sarili — hata log'lanir, akis kesilmez. Ek olarak admin reindex endpoint
 * her zaman manuel tazeleme yapabilir.
 *
 * <p>scorestv.elasticsearch.enabled=false ise bu bean hic yuklenmez,
 * event'ler bos firilatilir (publisher'a hata gelmez, yakalayan yok).
 */
@Component
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SearchIndexEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(SearchIndexEventListener.class);

    private final SearchIndexerService indexer;

    public SearchIndexEventListener(SearchIndexerService indexer) {
        this.indexer = indexer;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamIndexed(EntityIndexedEvent.TeamIndexed e) {
        indexer.indexTeam(e.team());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeagueIndexed(EntityIndexedEvent.LeagueIndexed e) {
        indexer.indexLeague(e.league());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerIndexed(EntityIndexedEvent.PlayerIndexed e) {
        indexer.indexPlayer(e.player());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCountryIndexed(EntityIndexedEvent.CountryIndexed e) {
        indexer.indexCountry(e.country());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFixtureIndexed(EntityIndexedEvent.FixtureIndexed e) {
        indexer.indexFixture(e.fixture());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCoachIndexed(EntityIndexedEvent.CoachIndexed e) {
        indexer.indexCoach(e.coach());
    }
}
