package com.scorestv.news;

import com.scorestv.search.index.ArticleDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Ilgili haberler" (related) icin Elasticsearch araması.
 *
 * <p><b>ES-kosullu:</b> {@code scorestv.elasticsearch.enabled=false} ise bean
 * yuklenmez; {@link NewsService} buna opsiyonel baglanir ve DB fallback'ine
 * duser.
 *
 * <p><b>Sorgu:</b> raw JSON {@link StringQuery} (co.elastic typed DSL yerine —
 * daha az kirilgan, versiyon-bagimsiz). bool: ayni dil (filter) + kendisi haric
 * (must_not) + paylasilan bagli varliklar (should: teams/leagues/fixtures terms)
 * + baslik/ozet more_like_this (should). Paylasan varlik + benzer metin uste
 * cikar. {@code minimum_should_match: 1}.
 */
@Service
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class NewsRelatedSearcher {

    private static final Logger log = LoggerFactory.getLogger(NewsRelatedSearcher.class);

    private final ElasticsearchOperations ops;

    public NewsRelatedSearcher(ElasticsearchOperations ops) {
        this.ops = ops;
    }

    /**
     * Verilen kaynak dokumana benzer (ayni dil, kendisi haric) haber id'lerini
     * alaka sirasiyla doner. ES hatasi olursa bos liste (cagiran DB fallback'ine
     * dusebilir).
     *
     * @param source kaynak haber dokumani
     * @param limit  en fazla sonuc
     */
    public List<Long> findRelatedIds(ArticleDoc source, int limit) {
        if (source == null) return List.of();
        try {
            String json = buildQueryJson(source);
            StringQuery query = new StringQuery(json);
            query.setPageable(PageRequest.of(0, Math.max(1, limit)));
            SearchHits<ArticleDoc> hits = ops.search(query, ArticleDoc.class,
                    IndexCoordinates.of("scorestv_news"));
            List<Long> ids = new ArrayList<>();
            hits.forEach(h -> ids.add(h.getContent().getId()));
            return ids;
        } catch (Exception e) {
            log.warn("ES related haber araması basarisiz id={}: {}",
                    source.getId(), e.getMessage());
            return List.of();
        }
    }

    /** bool query JSON'unu kurar (yalniz "query" govdesi — StringQuery bunu bekler). */
    private String buildQueryJson(ArticleDoc s) {
        List<String> should = new ArrayList<>();
        appendTerms(should, "teams", s.getTeams());
        appendTerms(should, "leagues", s.getLeagues());
        appendTerms(should, "fixtures", s.getFixtures());

        String likeText = ((s.getTitle() != null ? s.getTitle() : "") + " "
                + (s.getSummary() != null ? s.getSummary() : "")).trim();
        if (!likeText.isBlank()) {
            should.add("{\"more_like_this\":{\"fields\":[\"title\",\"summary\"],"
                    + "\"like\":\"" + esc(likeText) + "\","
                    + "\"min_term_freq\":1,\"min_doc_freq\":1,\"max_query_terms\":25}}");
        }

        StringBuilder filter = new StringBuilder();
        if (s.getLang() != null) {
            filter.append("{\"term\":{\"lang\":\"").append(esc(s.getLang())).append("\"}}");
        }

        StringBuilder b = new StringBuilder();
        b.append("{\"bool\":{");
        b.append("\"must_not\":[{\"term\":{\"id\":\"").append(s.getId()).append("\"}}]");
        if (filter.length() > 0) {
            b.append(",\"filter\":[").append(filter).append("]");
        }
        b.append(",\"should\":[").append(String.join(",", should)).append("]");
        b.append(",\"minimum_should_match\":1");
        b.append("}}");
        return b.toString();
    }

    private void appendTerms(List<String> should, String field, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        String vals = ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        if (!vals.isEmpty()) {
            should.add("{\"terms\":{\"" + field + "\":[" + vals + "]}}");
        }
    }

    /** JSON string kacisi — like metnindeki tirnak/ters-slash/kontrol karakterleri. */
    private static String esc(String v) {
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append(' ');
                case '\r' -> sb.append(' ');
                case '\t' -> sb.append(' ');
                default -> {
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
