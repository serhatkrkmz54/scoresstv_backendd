package com.scorestv.highlights.dto;

import java.util.List;

/**
 * İstemcilere dönen sade highlight modeli.
 *
 * <p>{@code embeddable}: embedUrl uygulama içinde (iframe/WebView) gömülebilir
 * mi (ücretli plan geo-restrictions ile belirlenir; çağrı yapılamazsa embedUrl
 * varsa iyimser true). {@code blockedCountries}: gömme/izleme engelli ülke
 * kodları (istemci isterse kullanıcı ülkesine göre yedeğe düşebilir).
 */
public record HighlightView(
        Long id,
        String title,
        String url,
        String embedUrl,
        String imgUrl,
        String source,
        String type,
        boolean embeddable,
        List<String> blockedCountries
) {
    public static HighlightView of(HighlightlyHighlightDto d,
                                   boolean embeddable,
                                   List<String> blockedCountries) {
        return new HighlightView(
                d.id(), d.title(), d.url(), d.embedUrl(),
                d.imgUrl(), d.source(), d.type(),
                embeddable,
                blockedCountries == null ? List.of() : blockedCountries);
    }
}
