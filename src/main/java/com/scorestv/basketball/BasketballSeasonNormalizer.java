package com.scorestv.basketball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basketbol sezon formatlari ligden lige farkli olabilir:
 * <ul>
 *   <li>NBA, EuroLeague gibi: {@code "2025-2026"} (Avrupa-style)
 *   <li>Bazi Guney Amerika ve takvim-yili liglerinde: {@code "2026"} (tek yil)
 * </ul>
 *
 * <p>Kullanici / mobile tarafi "2025-2026" istese bile bazi liglerde API
 * sadece "2026" kabul eder; tersi de gecerli. Bu sinif lig'in
 * {@code seasonsJson}'undaki gercek sezon listesini parse edip kullanicinin
 * verdiginin DAHA YAKIN eslesmesini bulur — boylece downstream API
 * cagrilari ({@code /standings}, {@code /players}) dogru sezon string'iyle
 * yapilir.
 *
 * <p>Eslesme stratejisi:
 * <ol>
 *   <li>Direkt eslesme: input "2025-2026" varsa ve seasons listesinde
 *       "2025-2026" varsa, oldugu gibi donulur.
 *   <li>Trailing 4-digit yil eslesmesi: input "2026" ise seasons icinde
 *       "2025-2026" varsa donulur (sonu 2026 eslesir). Tersi de calisir.
 *   <li>Eslesme bulamazsa input olduugu gibi donulur (fallback).
 * </ol>
 */
public final class BasketballSeasonNormalizer {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballSeasonNormalizer.class);

    /** Lokal mapper — Spring DI'a bagimli degil ({@code RedisConfig} cakismasin). */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Yil string'inin sonundaki 4 haneli yili yakalayan regex. */
    private static final Pattern TRAILING_YEAR = Pattern.compile("(\\d{4})$");

    private BasketballSeasonNormalizer() {}

    /**
     * Verilen input sezonu, lig'in {@code seasonsJson} icindeki gercek
     * format'a uyarlayarak doner.
     *
     * @param input        kullanicinin verdigi sezon (orn. "2026" veya "2025-2026")
     * @param seasonsJson  lig'in {@code BasketballLeague#getSeasonsJson()}
     * @return normalize edilmis sezon string'i (input formatlanmamissa veya
     *         seasonsJson bossa input olduugu gibi doner)
     */
    public static String normalize(String input, String seasonsJson) {
        if (input == null || input.isBlank()) return input;
        List<BkLeagueDto.Season> seasons = parseSeasons(seasonsJson);
        if (seasons.isEmpty()) return input;

        // 1) Direkt eslesme
        for (var s : seasons) {
            if (s != null && input.equals(s.season())) return input;
        }

        // 2) Trailing 4-digit yil bazli eslesme
        String inputYear = extractTrailingYear(input);
        if (inputYear == null) return input;
        for (var s : seasons) {
            if (s == null || s.season() == null) continue;
            String sYear = extractTrailingYear(s.season());
            if (inputYear.equals(sYear)) {
                if (!input.equals(s.season())) {
                    log.debug("Basketbol sezon normalize: '{}' -> '{}'",
                            input, s.season());
                }
                return s.season();
            }
        }

        // 3) Eslesme yok — input olduugu gibi
        return input;
    }

    /** {@code seasonsJson} string'inden sezon listesini parse eder. */
    private static List<BkLeagueDto.Season> parseSeasons(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Basketbol seasonsJson parse hatasi: {}", e.getMessage());
            return List.of();
        }
    }

    /** "2025-2026" -> "2026", "2026" -> "2026". Yoksa null. */
    private static String extractTrailingYear(String s) {
        if (s == null) return null;
        Matcher m = TRAILING_YEAR.matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
