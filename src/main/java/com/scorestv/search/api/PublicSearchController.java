package com.scorestv.search.api;

import com.scorestv.search.service.SearchResponse;
import com.scorestv.search.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public arama endpoint'i — mobile/web istemcilerin tek-cagri arama
 * yapabilmesi icin.
 *
 * <p><b>Ornek istek:</b>
 * <pre>
 *   GET /api/v1/search?q=galat&types=team,fixture
 *   GET /api/v1/search?q=fener           (tipler bos = hepsi)
 * </pre>
 *
 * <p><b>Auth:</b> public — kullanici login olmadan da arayabilir.
 */
@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PublicSearchController {

    private final SearchService searchService;

    public PublicSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(value = "types", required = false) String types) {

        Set<String> typeSet = (types == null || types.isBlank())
                ? Set.of()
                : Arrays.stream(types.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());

        return searchService.search(query, typeSet);
    }
}
