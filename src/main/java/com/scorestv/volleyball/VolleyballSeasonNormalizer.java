package com.scorestv.volleyball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voleybol sezon formatlari ligden lige farkli olabilir:
 * <ul>
 *   <li>Cogu lig: tek yil {@code "2024"}.</li>
 *   <li>Bazi Avrupa kis ligleri: {@code "2024-2025"}.</li>
 * </ul>
 *
 * <p>Bu sinif lig'in {@code seasonsJson}'undaki gercek sezon listesini parse
 * edip kullanicinin verdiginin DAHA YAKIN eslesmesini bulur.
 */
public final class VolleyballSeasonNormalizer {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballSeasonNormalizer.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Pattern TRAILING_YEAR = Pattern.compile("(\\d{4})$");

    private VolleyballSeasonNormalizer() {}

    /**
     * Verilen input sezonu, lig'in {@code seasonsJson} icindeki gercek format'a
     * uyarlayarak doner. Eslesme bulamazsa input olduugu gibi doner.
     */
    public static String normalize(String input, String seasonsJson) {
        if (input == null || input.isBlank()) return input;
        List<VbLeagueDto.Season> seasons = parseSeasons(seasonsJson);
        if (seasons.isEmpty()) return input;

        // 1) Direkt eslesme
        for (var s : seasons) {
            if (s != null && input.equals(s.seasonAsString())) return input;
        }

        // 2) Trailing 4-digit yil bazli eslesme
        String inputYear = extractTrailingYear(input);
        if (inputYear == null) return input;
        for (var s : seasons) {
            if (s == null || s.seasonAsString() == null) continue;
            String sYear = extractTrailingYear(s.seasonAsString());
            if (inputYear.equals(sYear)) {
                if (!input.equals(s.seasonAsString())) {
                    log.debug("Voleybol sezon normalize: '{}' -> '{}'", input, s.seasonAsString());
                }
                return s.seasonAsString();
            }
        }

        return input;
    }

    private static List<VbLeagueDto.Season> parseSeasons(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Voleybol seasonsJson parse hatasi: {}", e.getMessage());
            return List.of();
        }
    }

    private static String extractTrailingYear(String s) {
        if (s == null) return null;
        Matcher m = TRAILING_YEAR.matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
