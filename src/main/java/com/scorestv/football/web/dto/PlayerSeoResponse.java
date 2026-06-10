package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Oyuncu detay sayfasi icin tam SEO paketi. {@link TeamSeoResponse} ile ayni
 * yapida — frontend SSR head meta'larini bu yanitan uretir.
 *
 * <p>Hreflang: TR url /oyuncu/{slug}, EN url /player/{slug}.
 */
public record PlayerSeoResponse(
        String title,
        String description,
        String keywords,
        String canonicalUrl,
        String slug,
        String locale,
        OpenGraph openGraph,
        TwitterCard twitter,
        /** {@code application/ld+json} olarak embed edilecek Person JSON-LD. */
        String jsonLd,
        List<Breadcrumb> breadcrumbs,
        List<Hreflang> hreflang
) implements Serializable {

    public record OpenGraph(
            String title,
            String description,
            String type,
            String url,
            String image,
            String siteName,
            String locale
    ) implements Serializable {}

    public record TwitterCard(
            String card,
            String title,
            String description,
            String image
    ) implements Serializable {}

    public record Breadcrumb(
            int position,
            String name,
            String url
    ) implements Serializable {}

    public record Hreflang(
            String lang,
            String href
    ) implements Serializable {}
}
