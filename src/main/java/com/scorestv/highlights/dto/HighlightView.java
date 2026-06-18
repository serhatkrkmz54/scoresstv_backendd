package com.scorestv.highlights.dto;

/**
 * İstemcilere dönen sade highlight modeli.
 *
 * <p>{@code embeddable}: bu highlight'ın embedUrl'i, isteği yapan kullanıcının
 * ÜLKESİNDE uygulama içinde (iframe/WebView) oynatılabilir mi — backend
 * geo-restrictions (allowed/blocked) + kullanıcı ülkesi ile hesaplar. false ise
 * istemci küçük-resim/"tarayıcıda aç" yedeğine düşer.
 */
public record HighlightView(
        Long id,
        String title,
        String url,
        String embedUrl,
        String imgUrl,
        String source,
        String type,
        boolean embeddable
) {
    public static HighlightView of(HighlightlyHighlightDto d, boolean embeddable) {
        return of(d, d.embedUrl(), embeddable);
    }

    /** embedUrl'i dışarıdan (örn. normalize edilmiş YouTube embed) verir. */
    public static HighlightView of(HighlightlyHighlightDto d, String embedUrl,
                                   boolean embeddable) {
        return new HighlightView(
                d.id(), d.title(), d.url(), embedUrl,
                d.imgUrl(), d.source(), d.type(), embeddable);
    }
}
