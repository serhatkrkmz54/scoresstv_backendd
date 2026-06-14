package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * API-Sports ortak yanit zarfi: {@code { get, results, errors, response: [...] }}.
 *
 * <p><b>{@code errors}:</b> API 200 dondurse de "No data found" gibi mesajlar
 * burada gelir. Bazi liglerde {@code []} (array), bazilarinda {@code {}}
 * (object) olarak donuyor — bu yuzden ham {@link Object} tipi kullaniyoruz
 * (Jackson her formati kabul eder). {@link #hasErrors()} guvenli check.
 *
 * <p>JsonNode kullanilmiyor cunku projenin RedisConfig'inde ozel mapper
 * yapılandırması var ve JsonNode'u abstract type olarak gorup parse'i kiriyor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BasketballApiResponse<T>(
        String get,
        Integer results,
        Object errors,
        List<T> response
) {
    public List<T> responseOrEmpty() {
        return response == null ? List.of() : response;
    }

    /**
     * API 200 dondurse de mesaj iceriyor mu? {@code errors} Object olarak
     * geldigi icin runtime'da tip kontrolu — Collection (array) veya Map
     * (object) varyantini tolere ederiz.
     */
    public boolean hasErrors() {
        if (errors == null) return false;
        if (errors instanceof Collection<?> c) return !c.isEmpty();
        if (errors instanceof Map<?, ?> m) return !m.isEmpty();
        // String veya bilinmeyen tip — ham deger kontrol et
        return !errors.toString().isEmpty()
                && !"[]".equals(errors.toString())
                && !"{}".equals(errors.toString());
    }
}
