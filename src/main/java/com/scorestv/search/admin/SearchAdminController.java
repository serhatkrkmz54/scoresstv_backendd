package com.scorestv.search.admin;

import com.scorestv.search.indexer.SearchIndexerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Elasticsearch admin endpoint'leri.
 *
 * <p><code>POST /api/v1/admin/search/reindex/{type}</code> — type:
 * <ul>
 *   <li><b>all</b>          — tum tipler (async, fire-and-forget)</li>
 *   <li><b>teams</b>        — sadece takimlar (sync)</li>
 *   <li><b>leagues</b>      — sadece ligler (sync)</li>
 *   <li><b>players</b>      — sadece oyuncular (sync)</li>
 *   <li><b>countries</b>    — sadece ulkeler (sync)</li>
 *   <li><b>fixtures</b>     — sadece fiksturler (sync, en uzun surer)</li>
 * </ul>
 *
 * <p><b>scorestv.elasticsearch.enabled=false</b> ise bu controller yuklenmez —
 * endpoint 404 doner (gerekli; admin yanlislikla bos cluster'a yazmayalim).
 */
@RestController
@RequestMapping("/api/v1/admin/search")
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SearchAdminController {

    private static final Logger log = LoggerFactory.getLogger(SearchAdminController.class);

    private final SearchIndexerService indexer;

    public SearchAdminController(SearchIndexerService indexer) {
        this.indexer = indexer;
    }

    @PostMapping("/reindex/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> reindex(@PathVariable String type) {
        log.info("Admin reindex talebi: type={}", type);
        switch (type.toLowerCase()) {
            case "all" -> {
                indexer.reindexAllAsync();
                return Map.of(
                        "status", "started_async",
                        "type", "all",
                        "message", "Tum tipler asenkron olarak reindex ediliyor. Backend log'larini takip et.");
            }
            case "countries" -> {
                long n = indexer.reindexCountries();
                return Map.of("type", "countries", "indexed", n);
            }
            case "leagues" -> {
                long n = indexer.reindexLeagues();
                return Map.of("type", "leagues", "indexed", n);
            }
            case "teams" -> {
                long n = indexer.reindexTeams();
                return Map.of("type", "teams", "indexed", n);
            }
            case "players" -> {
                long n = indexer.reindexPlayers();
                return Map.of("type", "players", "indexed", n);
            }
            case "fixtures" -> {
                long n = indexer.reindexFixtures();
                return Map.of("type", "fixtures", "indexed", n);
            }
            default -> {
                return Map.of("status", "error",
                        "message", "Bilinmeyen type: " + type);
            }
        }
    }

    /**
     * Tum indeksleri DROP + CREATE + REINDEX yapar (analyzer/mapping degisikligi
     * sonrasi gerekli). Async — request hemen doner, ES log'larindan ilerleme
     * izlenir. KULLANIM ICIN UYARI: tum indeksler ~30-60sn arama icin
     * kullanilamaz hale gelir; bos cevap doner.
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> rebuildAll() {
        log.info("Admin REBUILD (drop+create+reindex) talebi");
        indexer.rebuildAllAsync();
        return Map.of(
                "status", "started_async",
                "action", "rebuild",
                "message", "Tum indeksler silindi+yeniden yaratiliyor+reindex. ES log'lari takip et. ~30-60sn arama bos doner.");
    }
}
