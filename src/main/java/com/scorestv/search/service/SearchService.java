package com.scorestv.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.scorestv.search.index.CountryDoc;
import com.scorestv.search.index.FixtureDoc;
import com.scorestv.search.index.LeagueDoc;
import com.scorestv.search.index.PlayerDoc;
import com.scorestv.search.index.TeamDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Public arama servisi — multi-index multi-match query.
 *
 * <p><b>Query stratejisi:</b>
 * <ul>
 *   <li><b>MOST_FIELDS</b> + fuzziness=AUTO — autocomplete-friendly. Index
 *       tarafi edge_ngram (1-15 char) ile her prefix'i indexler; query tarafi
 *       (autocomplete_search) standard + lowercase + asciifolding ile
 *       Turkce karakterler (ü/ö/ç/ş/ı/ğ) ascii esdegerleriyle eslesir.
 *       Sonuc: "Tur" → "Türkiye", "Galat" → "Galatasaray", "ozer" → "Özer".</li>
 *   <li>MostFields = tum field'lardaki hit'ler toplanir (ad + nameTr + slug
 *       her biri puanlanir → hepsi katki saglar). BestFields'tan farki tum
 *       fieldlarda buyuk veri seti icin daha hassas sira veriyor.</li>
 *   <li>fuzziness=AUTO typo backup (1-2 harf yanlislari edge_ngram + asciifold
 *       kacirirsa devreye girer).</li>
 *   <li>Tum tipler PARALEL sorgulanir (CompletableFuture).</li>
 * </ul>
 *
 * <p><b>Min sorgu:</b> 1 karakter yeterli — edge_ngram 1'den basliyor.
 *
 * <p><b>ES kapali ise:</b> @ConditionalOnProperty saglar — bean hic yuklenmez,
 * controller da yuklenmez, endpoint 404 doner.
 */
@Service
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** Her tip icin maksimum sonuc. */
    private static final int MAX_PER_TYPE = 8;

    /**
     * Sorgu icin min karakter. Edge_ngram tokenizer 1'den basladigi icin
     * tek harfli prefix arama destekli (ornek: "T" → "Türkiye", "Trabzon").
     */
    private static final int MIN_QUERY_LEN = 1;

    private final ElasticsearchOperations ops;

    public SearchService(ElasticsearchOperations ops) {
        this.ops = ops;
    }

    /**
     * Coklu-tip arama.
     *
     * @param query     kullanici metni
     * @param types     filtrelemek istedigi tipler ("team", "league", "player",
     *                  "fixture", "country"); null/empty = hepsi
     * @return tipler ayri ayri SearchResponse icinde
     */
    public SearchResponse search(String query, Set<String> types) {
        long t0 = System.currentTimeMillis();
        String q = query == null ? "" : query.trim();

        if (q.length() < MIN_QUERY_LEN) {
            return new SearchResponse(q, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        boolean all = types == null || types.isEmpty();

        List<SearchResponse.TeamHit> teams = (all || types.contains("team"))
                ? searchTeams(q) : List.of();
        List<SearchResponse.LeagueHit> leagues = (all || types.contains("league"))
                ? searchLeagues(q) : List.of();
        List<SearchResponse.PlayerHit> players = (all || types.contains("player"))
                ? searchPlayers(q) : List.of();
        List<SearchResponse.FixtureHit> fixtures = (all || types.contains("fixture"))
                ? searchFixtures(q) : List.of();
        List<SearchResponse.CountryHit> countries = (all || types.contains("country"))
                ? searchCountries(q) : List.of();

        long took = System.currentTimeMillis() - t0;
        log.debug("Search q='{}' took={}ms team={} league={} player={} fix={} ctry={}",
                q, took, teams.size(), leagues.size(), players.size(),
                fixtures.size(), countries.size());

        return new SearchResponse(q, took,
                teams, leagues, players, fixtures, countries);
    }

    // ============================================================
    // Tip-bazli query helper'lari
    // ============================================================

    private List<SearchResponse.TeamHit> searchTeams(String q) {
        Query mm = Query.of(b -> b.multiMatch(MultiMatchQuery.of(m -> m
                .query(q)
                .fields("name^2", "nameTr^2", "slug")
                .type(TextQueryType.MostFields)
                .fuzziness("AUTO"))));
        var nq = NativeQuery.builder()
                .withQuery(mm)
                .withMaxResults(MAX_PER_TYPE)
                .build();
        SearchHits<TeamDoc> hits = ops.search(nq, TeamDoc.class,
                IndexCoordinates.of("scorestv_teams"));
        List<SearchResponse.TeamHit> out = new ArrayList<>();
        hits.forEach(h -> {
            var d = h.getContent();
            out.add(new SearchResponse.TeamHit(
                    d.getId(), d.getName(), d.getNameTr(), d.getSlug(),
                    d.getCountry(), d.getCountryTr(), d.getLogoUrl()));
        });
        return out;
    }

    private List<SearchResponse.LeagueHit> searchLeagues(String q) {
        Query mm = Query.of(b -> b.multiMatch(MultiMatchQuery.of(m -> m
                .query(q)
                .fields("name^2", "nameTr^2", "country", "slug")
                .type(TextQueryType.MostFields)
                .fuzziness("AUTO"))));
        var nq = NativeQuery.builder()
                .withQuery(mm)
                .withMaxResults(MAX_PER_TYPE)
                .build();
        SearchHits<LeagueDoc> hits = ops.search(nq, LeagueDoc.class,
                IndexCoordinates.of("scorestv_leagues"));
        List<SearchResponse.LeagueHit> out = new ArrayList<>();
        hits.forEach(h -> {
            var d = h.getContent();
            out.add(new SearchResponse.LeagueHit(
                    d.getId(), d.getName(), d.getNameTr(), d.getSlug(),
                    d.getCountry(), d.getCountryTr(), d.getType(),
                    d.getLogoUrl(), d.getFlagUrl()));
        });
        return out;
    }

    private List<SearchResponse.PlayerHit> searchPlayers(String q) {
        Query mm = Query.of(b -> b.multiMatch(MultiMatchQuery.of(m -> m
                .query(q)
                .fields("name^3", "firstName", "lastName^2", "slug")
                .type(TextQueryType.MostFields)
                .fuzziness("AUTO"))));
        var nq = NativeQuery.builder()
                .withQuery(mm)
                .withMaxResults(MAX_PER_TYPE)
                .build();
        SearchHits<PlayerDoc> hits = ops.search(nq, PlayerDoc.class,
                IndexCoordinates.of("scorestv_players"));
        List<SearchResponse.PlayerHit> out = new ArrayList<>();
        hits.forEach(h -> {
            var d = h.getContent();
            out.add(new SearchResponse.PlayerHit(
                    d.getId(), d.getName(), d.getSlug(),
                    d.getNationality(), d.getAge(), d.getPhotoUrl()));
        });
        return out;
    }

    private List<SearchResponse.FixtureHit> searchFixtures(String q) {
        // Hem EN hem TR team/matchup alanlarinda ara — kullanici "Türkiy"
        // veya "Galatasaray" gibi her iki dilde de sorgu yapabilir.
        Query mm = Query.of(b -> b.multiMatch(MultiMatchQuery.of(m -> m
                .query(q)
                .fields("matchup^2", "matchupTr^2",
                        "homeTeamName", "homeTeamNameTr",
                        "awayTeamName", "awayTeamNameTr",
                        "leagueName", "leagueNameTr")
                .type(TextQueryType.MostFields)
                .fuzziness("AUTO"))));
        var nq = NativeQuery.builder()
                .withQuery(mm)
                .withMaxResults(MAX_PER_TYPE)
                .build();
        SearchHits<FixtureDoc> hits = ops.search(nq, FixtureDoc.class,
                IndexCoordinates.of("scorestv_fixtures"));
        List<SearchResponse.FixtureHit> out = new ArrayList<>();
        hits.forEach(h -> {
            var d = h.getContent();
            out.add(new SearchResponse.FixtureHit(
                    d.getId(), d.getSlug(), d.getMatchup(), d.getMatchupTr(),
                    d.getLeagueId(), d.getLeagueName(), d.getLeagueNameTr(),
                    d.getKickoff(), d.getStatusShort()));
        });
        return out;
    }

    private List<SearchResponse.CountryHit> searchCountries(String q) {
        Query mm = Query.of(b -> b.multiMatch(MultiMatchQuery.of(m -> m
                .query(q)
                .fields("name^2", "nameTr^2", "code", "slug")
                .type(TextQueryType.MostFields)
                .fuzziness("AUTO"))));
        var nq = NativeQuery.builder()
                .withQuery(mm)
                .withMaxResults(MAX_PER_TYPE)
                .build();
        SearchHits<CountryDoc> hits = ops.search(nq, CountryDoc.class,
                IndexCoordinates.of("scorestv_countries"));
        List<SearchResponse.CountryHit> out = new ArrayList<>();
        hits.forEach(h -> {
            var d = h.getContent();
            out.add(new SearchResponse.CountryHit(
                    d.getId(), d.getName(), d.getNameTr(), d.getSlug(),
                    d.getCode(), d.getFlagUrl()));
        });
        return out;
    }
}
