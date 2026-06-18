package com.scorestv.search.indexer;

import com.scorestv.search.index.CoachDoc;
import com.scorestv.search.index.CountryDoc;
import com.scorestv.search.index.FixtureDoc;
import com.scorestv.search.index.LeagueDoc;
import com.scorestv.search.index.PlayerDoc;
import com.scorestv.search.index.TeamDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Backend acildiginda ES'i kontrol eder; ilgili index BOS ise full reindex
 * baslatir (background). Bu sayede:
 *
 * <ul>
 *   <li>ES'i ilk kez kuran kullanici elle reindex cagirmak zorunda kalmaz</li>
 *   <li>ES verisi bir sekilde silinirse (snapshot temizleme, manuel drop)
 *       sonraki app restart'ta otomatik geri doldurulur</li>
 *   <li>Index DOLU ise hicbir sey yapmaz (her startup'ta yeniden indexleme
 *       pahali olur, FCM/canlay-ticker sync zaten ES'i guncel tutar)</li>
 * </ul>
 *
 * <p><b>Davranis:</b>
 * <ul>
 *   <li>ApplicationReadyEvent dinler — tum bean'ler hazir olduktan sonra</li>
 *   <li>@Async + her tip ayri yontemde — app startup'i bekletmez</li>
 *   <li>ES kapaliysa exception yutulur, log warn, app calismaya devam eder</li>
 *   <li>scorestv.elasticsearch.bootstrap-on-startup=false ile kapatilabilir
 *       (default: true)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SearchIndexBootstrap {

    private static final Logger log =
            LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final SearchIndexerService indexer;
    private final ElasticsearchOperations esOps;

    /** false yaparsan startup'ta otomatik reindex devre disi kalir. */
    @Value("${scorestv.elasticsearch.bootstrap-on-startup:true}")
    private boolean bootstrapEnabled;

    public SearchIndexBootstrap(SearchIndexerService indexer,
                                ElasticsearchOperations esOps) {
        this.indexer = indexer;
        this.esOps = esOps;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!bootstrapEnabled) {
            log.info("ES bootstrap KAPALI (scorestv.elasticsearch.bootstrap-on-startup=false)");
            return;
        }
        log.info("ES bootstrap basliyor — bos index'ler otomatik doldurulacak");

        try {
            // Sirayla: kucuk index'lerden buyuge (ulkeler/ligler hizli, fixtures uzun)
            bootstrapIfEmpty("scorestv_countries", CountryDoc.class,
                    indexer::reindexCountries);
            bootstrapIfEmpty("scorestv_leagues", LeagueDoc.class,
                    indexer::reindexLeagues);
            bootstrapIfEmpty("scorestv_teams", TeamDoc.class,
                    indexer::reindexTeams);
            bootstrapIfEmpty("scorestv_players", PlayerDoc.class,
                    indexer::reindexPlayers);
            bootstrapIfEmpty("scorestv_fixtures", FixtureDoc.class,
                    indexer::reindexFixtures);
            bootstrapIfEmpty("scorestv_coaches", CoachDoc.class,
                    indexer::reindexCoaches);
            log.info("ES bootstrap tamamlandi");
        } catch (Exception e) {
            log.warn("ES bootstrap hata: {} (ES erisilebilir mi? Manual reindex denenebilir)",
                    e.getMessage());
        }
    }

    /**
     * Index bos ise tum DB'yi indexle. Dolu ise hicbir sey yapma.
     * Her tip kendi try/catch icinde — bir tip basarisiz olsa digerleri devam eder.
     */
    private void bootstrapIfEmpty(String indexName, Class<?> docClass,
                                  java.util.function.LongSupplier reindex) {
        try {
            long count = esOps.count(
                    org.springframework.data.elasticsearch.client.elc.NativeQuery
                            .builder().build(),
                    docClass,
                    IndexCoordinates.of(indexName));
            if (count > 0) {
                log.info("ES {} index dolu ({} kayit) — bootstrap atlandi",
                        indexName, count);
                return;
            }
            log.info("ES {} index BOS — otomatik reindex baslatiliyor", indexName);
            long indexed = reindex.getAsLong();
            log.info("ES {} bootstrap tamamlandi: {} kayit indexlendi",
                    indexName, indexed);
        } catch (Exception e) {
            // Index hic yoksa count() exception atar — bu da "bos" demektir.
            log.info("ES {} count basarisiz, muhtemelen index yok — reindex deneniyor: {}",
                    indexName, e.getMessage());
            try {
                long indexed = reindex.getAsLong();
                log.info("ES {} bootstrap tamamlandi: {} kayit indexlendi",
                        indexName, indexed);
            } catch (Exception ex) {
                log.warn("ES {} bootstrap basarisiz: {}", indexName, ex.getMessage());
            }
        }
    }
}
